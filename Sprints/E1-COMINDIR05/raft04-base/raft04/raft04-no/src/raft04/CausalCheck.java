package raft04;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifica as propriedades causais dos registos produzidos pelos nós.
 *
 * Lê os ficheiros no-p<k>-n<i>.txt de uma pasta. Há dois conjuntos de registos:
 *  - TODOS: união dos registos de todos os nós, sem duplicados por (partição, seq, termo).
 *    Inclui registos que depois foram truncados. Serve para confirmar V[k] = seq
 *    em todos os registos alguma vez atribuídos, através das mudanças de líder.
 *  - FINAL: para cada partição, o registo do nó com o log mais atualizado
 *    (maior termo do último registo; em empate, maior seq). É o registo "oficial",
 *    sem divergências, usado nas verificações de Lamport e de ordem causal.
 */
public final class CausalCheck {
    private static final Pattern FILE = Pattern.compile("no-p(\\d+)-n(\\d+)\\.txt");

    private static final class Event {
        final int partition;
        final SensorRecord record;

        Event(int partition, SensorRecord record) {
            this.partition = partition;
            this.record = record;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Uso: CausalCheck <N> <pasta>");
            return;
        }
        int n = Integer.parseInt(args[0]);
        Path folder = Path.of(args[1]);

        Map<String, List<SensorRecord>> logs = readLogs(folder, n);   // chave "k:i"
        List<Event> all = union(logs);
        List<Event> events = finalLogs(logs, n);

        // V[k] = seq em todos os registos alguma vez atribuídos (inclui truncados)
        int invalid = 0;
        for (Event event : all) {
            if (event.record.vector.get(event.partition) != event.record.seq) {
                invalid++;
                System.out.println("INVALIDO: p" + event.partition
                        + " seq=" + event.record.seq + " termo=" + event.record.term
                        + " V=" + event.record.vector);
            }
        }

        // Lamport estritamente crescente dentro de cada partição (registo final)
        long localLamportViolations = 0;
        Map<Integer, Event> previous = new HashMap<>();
        for (Event event : events) {
            Event prior = previous.put(event.partition, event);
            if (prior != null && event.record.lamport <= prior.record.lamport) {
                localLamportViolations++;
                System.out.println("LAMPORT INVALIDO: p" + event.partition
                        + " L=" + prior.record.lamport + " seguido de L="
                        + event.record.lamport);
            }
        }

        long before = 0;
        long after = 0;
        long concurrent = 0;
        long concurrentLamportDifferent = 0;
        long lamportViolations = 0;
        for (int i = 0; i < events.size(); i++) {
            for (int j = i + 1; j < events.size(); j++) {
                Event a = events.get(i);
                Event b = events.get(j);
                VectorClock.Order order = a.record.vector.compare(b.record.vector);
                if (order == VectorClock.Order.BEFORE) {
                    if (a.partition != b.partition) before++;
                    if (a.record.lamport >= b.record.lamport) lamportViolations++;
                } else if (order == VectorClock.Order.AFTER) {
                    if (a.partition != b.partition) after++;
                    if (b.record.lamport >= a.record.lamport) lamportViolations++;
                } else if (order == VectorClock.Order.CONCURRENT) {
                    if (a.partition != b.partition) {
                        concurrent++;
                        if (a.record.lamport != b.record.lamport) {
                            concurrentLamportDifferent++;
                        }
                    }
                }
            }
        }
        double proportion = concurrent == 0 ? 0
                : (double) concurrentLamportDifferent / concurrent;
        String report = String.format(Locale.ROOT,
                "registos (união, sem duplicados)=%d%nregistos (finais)=%d%n"
                        + "V[k]!=seq=%d%na->b=%d%nb->a=%d%n"
                        + "concorrentes=%d%nconcorrentes com L distinto=%d "
                        + "(%.4f)%nviolações Lamport=%d%n"
                        + "violações Lamport na partição=%d%n",
                all.size(), events.size(), invalid, before, after, concurrent,
                concurrentLamportDifferent, proportion, lamportViolations,
                localLamportViolations);
        System.out.print(report);
        Files.writeString(folder.resolve("causal-report.txt"), report);

        events.sort(Comparator.comparingLong((Event e) -> e.record.lamport)
                .thenComparingInt(e -> e.partition)
                .thenComparingLong(e -> e.record.seq));
        List<String> ordered = new ArrayList<>();
        for (Event event : events) {
            ordered.add(event.partition + ";" + event.record.toLine());
        }
        Files.write(folder.resolve("vista-lamport.txt"), ordered);
    }

    // registos de cada nó, por ordem do ficheiro; chave "k:i"
    private static Map<String, List<SensorRecord>> readLogs(Path folder, int n)
            throws IOException {
        Map<String, List<SensorRecord>> result = new java.util.TreeMap<>();
        try (var files = Files.list(folder)) {
            for (Path path : (Iterable<Path>) files.sorted()::iterator) {
                Matcher matcher = FILE.matcher(path.getFileName().toString());
                if (!matcher.matches()) continue;
                List<SensorRecord> list = new ArrayList<>();
                for (String line : Files.readAllLines(path)) {
                    if (!line.trim().isEmpty()) list.add(SensorRecord.fromLine(line, n));
                }
                result.put(matcher.group(1) + ":" + matcher.group(2), list);
            }
        }
        return result;
    }

    // união sem duplicados por (partição, seq, termo)
    private static List<Event> union(Map<String, List<SensorRecord>> logs) {
        List<Event> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, List<SensorRecord>> entry : logs.entrySet()) {
            int partition = Integer.parseInt(entry.getKey().split(":")[0]);
            for (SensorRecord r : entry.getValue()) {
                if (seen.add(partition + ":" + r.seq + ":" + r.term)) {
                    result.add(new Event(partition, r));
                }
            }
        }
        return result;
    }

    // por partição, o log do nó mais atualizado (maior termo final, depois maior seq)
    private static List<Event> finalLogs(Map<String, List<SensorRecord>> logs, int n) {
        Map<Integer, List<SensorRecord>> best = new java.util.TreeMap<>();
        for (Map.Entry<String, List<SensorRecord>> entry : logs.entrySet()) {
            int partition = Integer.parseInt(entry.getKey().split(":")[0]);
            List<SensorRecord> candidate = entry.getValue();
            List<SensorRecord> current = best.get(partition);
            if (current == null || moreRecent(candidate, current)) {
                best.put(partition, candidate);
            }
        }
        List<Event> result = new ArrayList<>();
        for (Map.Entry<Integer, List<SensorRecord>> entry : best.entrySet()) {
            for (SensorRecord r : entry.getValue()) {
                result.add(new Event(entry.getKey(), r));
            }
        }
        return result;
    }

    private static boolean moreRecent(List<SensorRecord> a, List<SensorRecord> b) {
        if (a.isEmpty()) return false;
        if (b.isEmpty()) return true;
        SensorRecord la = a.get(a.size() - 1);
        SensorRecord lb = b.get(b.size() - 1);
        if (la.term != lb.term) return la.term > lb.term;
        return la.seq > lb.seq;
    }
}