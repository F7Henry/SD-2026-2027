package raft04;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RaftCheck {
    private static final Pattern EVENT_FILE =
            Pattern.compile("eventos-p(\\d+)-n(\\d+)\\.txt");
    private static final Pattern EVENT_LINE =
            Pattern.compile("(\\d+);([^;]+);p(\\d+)(?:;(.*))?");

    private RaftCheck() { }

    static final class Event {
        final long instant;
        final String type;
        final int partition;
        final int node;
        final String detail;

        Event(long instant, String type, int partition, int node, String detail) {
            this.instant = instant;
            this.type = type;
            this.partition = partition;
            this.node = node;
            this.detail = detail == null ? "" : detail;
        }
    }

    private static final class TermInfo {
        final Set<Integer> candidates = new LinkedHashSet<>();
        final Map<Integer, Set<Integer>> votes = new TreeMap<>();   // candidato -> eleitores
        final List<Integer> leaders = new ArrayList<>();
        long firstCandidate = Long.MAX_VALUE;
        long leaderInstant = -1;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Uso: RaftCheck <N> <pasta> [falhas.txt]");
            return;
        }
        int n = Integer.parseInt(args[0]);
        Path folder = Path.of(args[1]);
        Path failures = args.length == 3 ? Path.of(args[2]) : null;

        Map<String, List<Event>> byNode = readEvents(folder);
        StringBuilder report = new StringBuilder();
        for (int p = 0; p < n; p++) partitionReport(report, p, byNode);
        part2(report, n, folder, failures, byNode);

        Files.writeString(folder.resolve("raft-report.txt"), report.toString());
        System.out.print(report);
    }

    // ---------- parte 1: eleições e segurança, por partição ----------

    private static void partitionReport(StringBuilder report, int p,
                                        Map<String, List<Event>> byNode) {
        List<Event> events = new ArrayList<>();
        for (List<Event> list : byNode.values()) {
            for (Event e : list) if (e.partition == p) events.add(e);
        }
        events.sort(Comparator.comparingLong(e -> e.instant));      // ordenação estável
        report.append("== Partição p").append(p).append(" ==\n");
        if (events.isEmpty()) {
            report.append("(sem eventos)\n\n");
            return;
        }

        Map<Long, TermInfo> terms = new TreeMap<>();
        long start = Long.MAX_VALUE;
        long firstLeaderInstant = -1;
        long firstLeaderTerm = -1;
        int firstLeaderNode = 0;
        for (Event e : events) {
            switch (e.type) {
                case "ARRANQUE":
                    start = Math.min(start, e.instant);
                    break;
                case "CANDIDATO": {
                    long t = num(e.detail, "termo");
                    TermInfo info = terms.computeIfAbsent(t, x -> new TermInfo());
                    info.candidates.add(e.node);
                    info.votes.computeIfAbsent(e.node, x -> new TreeSet<>()).add(e.node);
                    info.firstCandidate = Math.min(info.firstCandidate, e.instant);
                    break;
                }
                case "VOTO": {
                    if (!e.detail.endsWith("concedido")) break;
                    long t = num(e.detail, "termo");
                    String candidate = field(e.detail, "candidato");
                    if (candidate == null) break;
                    int c = Integer.parseInt(candidate.substring(1));
                    TermInfo info = terms.computeIfAbsent(t, x -> new TermInfo());
                    info.votes.computeIfAbsent(c, x -> new TreeSet<>()).add(e.node);
                    break;
                }
                case "LIDER": {
                    long t = num(e.detail, "termo");
                    TermInfo info = terms.computeIfAbsent(t, x -> new TermInfo());
                    info.leaders.add(e.node);
                    if (info.leaderInstant < 0) info.leaderInstant = e.instant;
                    if (firstLeaderInstant < 0) {
                        firstLeaderInstant = e.instant;
                        firstLeaderTerm = t;
                        firstLeaderNode = e.node;
                    }
                    break;
                }
                default:
                    break;
            }
        }

        report.append("Termos:\n");
        int withoutLeader = 0;
        int splitVotes = 0;
        int withoutLeaderBeforeFirst = 0;
        int multiLeader = 0;
        long[] run = {-1, -1, 0};      // termo inicial, termo final, número de termos
        int runCandidate = 0;
        for (Map.Entry<Long, TermInfo> entry : terms.entrySet()) {
            TermInfo info = entry.getValue();
            boolean lonely = info.leaders.isEmpty() && info.candidates.size() == 1;
            if (lonely) {
                int only = info.candidates.iterator().next();
                if (run[2] > 0 && only != runCandidate) flushRun(report, run, runCandidate);
                if (run[2] == 0) run[0] = entry.getKey();
                run[1] = entry.getKey();
                run[2]++;
                runCandidate = only;
                withoutLeader++;
                if (firstLeaderTerm < 0 || entry.getKey() < firstLeaderTerm) {
                    withoutLeaderBeforeFirst++;
                }
                continue;
            }
            flushRun(report, run, runCandidate);
            StringBuilder candidates = new StringBuilder();
            for (int c : info.candidates) {
                if (candidates.length() > 0) candidates.append(", ");
                candidates.append('n').append(c);
            }
            StringBuilder votes = new StringBuilder();
            for (Map.Entry<Integer, Set<Integer>> v : info.votes.entrySet()) {
                if (votes.length() > 0) votes.append(", ");
                votes.append('n').append(v.getKey()).append('=').append(v.getValue().size());
            }
            report.append("  termo ").append(entry.getKey())
                    .append(": candidatos=[").append(candidates)
                    .append("] votos={").append(votes).append("} ");
            if (info.leaders.isEmpty()) {
                withoutLeader++;
                splitVotes++;
                if (firstLeaderTerm < 0 || entry.getKey() < firstLeaderTerm) {
                    withoutLeaderBeforeFirst++;
                }
                report.append("sem líder (voto dividido)\n");
            } else {
                if (info.leaders.size() > 1) multiLeader++;
                report.append("líder=n").append(info.leaders.get(0));
                if (info.firstCandidate != Long.MAX_VALUE) {
                    report.append(" eleicao_ms=")
                            .append(info.leaderInstant - info.firstCandidate);
                }
                if (info.leaders.size() > 1) {
                    report.append(" LIDERES DUPLICADOS=").append(info.leaders);
                }
                report.append('\n');
            }
        }
        flushRun(report, run, runCandidate);

        report.append("Termos sem líder: ").append(withoutLeader)
                .append("; destes, votos divididos (2 ou mais candidatos): ")
                .append(splitVotes).append('\n');
        if (firstLeaderInstant < 0 || start == Long.MAX_VALUE) {
            report.append("Primeiro líder: nenhum\n");
        } else {
            report.append("Primeiro líder: n").append(firstLeaderNode)
                    .append(" no termo ").append(firstLeaderTerm).append(", ")
                    .append(String.format(Locale.ROOT, "%.3f",
                            (firstLeaderInstant - start) / 1000.0))
                    .append(" s após o arranque do primeiro nó; termos sem líder até aí: ")
                    .append(withoutLeaderBeforeFirst).append('\n');
        }

        report.append("Segurança: termos com mais de um LIDER = ").append(multiLeader)
                .append(" (deve ser 0)\n");
        int drops = 0;
        for (Map.Entry<String, List<Event>> entry : byNode.entrySet()) {
            String[] key = entry.getKey().split(":");
            if (Integer.parseInt(key[0]) != p) continue;
            long previous = -1;
            for (Event e : entry.getValue()) {
                long t = termOf(e);
                if (t < 0) continue;
                if (t < previous) {
                    drops++;
                    report.append("  TERMO A DIMINUIR em n").append(key[1]).append(": ")
                            .append(previous).append(" -> ").append(t)
                            .append(" (").append(e.type).append(")\n");
                }
                previous = t;
            }
        }
        report.append("Segurança: vezes em que o termo de um nó diminuiu = ").append(drops)
                .append(" (deve ser 0)\n\n");
    }


    // escreve (e repõe) uma sequência de termos em que um único nó se candidatou sem obter maioria
    private static void flushRun(StringBuilder report, long[] run, int candidate) {
        if (run[2] == 0) return;
        if (run[2] == 1) {
            report.append("  termo ").append(run[0]).append(": só n").append(candidate)
                    .append(" candidato, sem maioria\n");
        } else {
            report.append("  termos ").append(run[0]).append(" a ").append(run[1])
                    .append(": só n").append(candidate).append(" candidato, sem maioria (")
                    .append(run[2]).append(" termos)\n");
        }
        run[0] = -1;
        run[1] = -1;
        run[2] = 0;
    }

    // ================= parte 2 (passo 13b): falhas, perdidas, truncados, coincidência =================

    private static void part2(StringBuilder report, int n, Path folder, Path failures,
                              Map<String, List<Event>> byNode) throws IOException {
        report.append("== Falhas ==\n");
        if (failures == null) {
            report.append("(sem falhas.txt: indisponibilidade não calculada)\n");
        } else {
            unavailability(report, folder, failures);
        }
        report.append('\n');
        report.append("== Leituras perdidas ==\n");
        lostReadings(report, n, folder);
        report.append('\n');
        report.append("== Registos truncados ==\n");
        truncations(report, folder, byNode);
        report.append('\n');
        report.append("== Coincidência final dos registos ==\n");
        finalMatch(report, n, folder);
        report.append('\n');
    }

    // ---------- passo 2a: indisponibilidade de cada falha ----------

    // linhas de hb-p<k>-n<i>.txt como {termo, hb, instante}
    private static List<long[]> readHb(Path folder, int p, int node) throws IOException {
        List<long[]> result = new ArrayList<>();
        Path path = folder.resolve("hb-p" + p + "-n" + node + ".txt");
        if (!Files.exists(path)) return result;
        for (String line : Files.readAllLines(path)) {
            String[] f = line.trim().split(";");
            if (f.length < 3) continue;
            result.add(new long[]{Long.parseLong(f[0]), Long.parseLong(f[1]),
                    Long.parseLong(f[2])});
        }
        return result;
    }

    // linhas de produtor.txt da partição p, como {instante, índice} + texto "sensor;temperatura"
    private static List<String[]> readProducer(Path folder, int p) throws IOException {
        List<String[]> result = new ArrayList<>();
        Path path = folder.resolve("produtor.txt");
        if (!Files.exists(path)) return result;
        for (String line : Files.readAllLines(path)) {
            String[] f = line.trim().split(";");
            if (f.length != 4 || !f[1].equals("p" + p)) continue;
            result.add(new String[]{f[0], f[2] + ";" + f[3]});
        }
        return result;
    }

    private static void unavailability(StringBuilder report, Path folder, Path failures)
            throws IOException {
        double low = (Wire.ELEICAO_MIN_MS - Wire.PERIODO_HB_MS) / 1000.0;
        double high = (Wire.ELEICAO_MAX_MS + Wire.VERIFICACAO_MS) / 1000.0;
        report.append(String.format(Locale.ROOT,
                "Intervalo previsto para a indisponibilidade: ]%.1f s; %.1f s]%n", low, high));
        for (String line : Files.readAllLines(failures)) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] f = line.split(";");
            if (f.length != 3) {
                report.append("  linha inválida em falhas.txt: ").append(line).append('\n');
                continue;
            }
            int p = Integer.parseInt(f[0].trim().substring(1));
            int node = Integer.parseInt(f[1].trim().substring(1));
            long t = Long.parseLong(f[2].trim());
            report.append("Falha de n").append(node).append(" em p").append(p)
                    .append(" (instante ").append(t).append("): ");

            // termo em que o nó falhado era líder: último LOTE antes da falha, recente
            long oldTerm = -1;
            long lastHb = -1;
            for (long[] hb : readHb(folder, p, node)) {
                if (hb[2] <= t && hb[2] > lastHb) { lastHb = hb[2]; oldTerm = hb[0]; }
            }
            if (oldTerm < 0 || t - lastHb > 3 * Wire.PERIODO_HB_MS) {
                report.append("não era líder (sem LOTE recente); sem indisponibilidade\n");
                continue;
            }

            // primeiro LOTE de um líder de termo superior, depois da falha
            long newInstant = Long.MAX_VALUE;
            long newTerm = -1;
            int newNode = 0;
            for (int i = 1; i <= Wire.GRUPO; i++) {
                for (long[] hb : readHb(folder, p, i)) {
                    if (hb[0] > oldTerm && hb[2] > t && hb[2] < newInstant) {
                        newInstant = hb[2];
                        newTerm = hb[0];
                        newNode = i;
                    }
                }
            }
            if (newNode == 0) {
                report.append("termo ").append(oldTerm)
                        .append(", sem novo líder até ao fim do ensaio\n");
                continue;
            }
            double down = (newInstant - t) / 1000.0;
            int sent = 0;
            for (String[] r : readProducer(folder, p)) {
                long instant = Long.parseLong(r[0]);
                if (instant > t && instant <= newInstant) sent++;
            }
            report.append(String.format(Locale.ROOT,
                    "líder do termo %d; novo líder n%d (termo %d) em %d%n"
                            + "  indisponibilidade = %.3f s (%s do intervalo previsto); "
                            + "leituras enviadas nesse intervalo = %d%n",
                    oldTerm, newNode, newTerm, newInstant, down,
                    (down > low && down <= high) ? "dentro" : "FORA", sent));
        }
    }

    // ---------- passo 2b: leituras perdidas ----------

    // registo final da partição: o log do nó com o último registo mais recente
    private static List<String[]> finalLog(Path folder, int p) throws IOException {
        List<String[]> best = new ArrayList<>();
        long bestTerm = -1;
        long bestSeq = -1;
        for (int i = 1; i <= Wire.GRUPO; i++) {
            Path path = folder.resolve("no-p" + p + "-n" + i + ".txt");
            if (!Files.exists(path)) continue;
            List<String[]> log = new ArrayList<>();
            for (String line : Files.readAllLines(path)) {
                if (!line.isBlank()) log.add(line.trim().split(";"));
            }
            if (log.isEmpty()) continue;
            String[] last = log.get(log.size() - 1);
            long seq = Long.parseLong(last[0]);
            long term = Long.parseLong(last[1]);
            if (term > bestTerm || (term == bestTerm && seq > bestSeq)) {
                best = log;
                bestTerm = term;
                bestSeq = seq;
            }
        }
        return best;
    }

    private static void lostReadings(StringBuilder report, int n, Path folder)
            throws IOException {
        if (!Files.exists(folder.resolve("produtor.txt"))) {
            report.append("(sem produtor.txt)\n");
            return;
        }
        long totalLost = 0;
        for (int p = 0; p < n; p++) {
            List<String[]> sent = readProducer(folder, p);
            Map<String, Integer> available = new java.util.HashMap<>();
            for (String[] r : finalLog(folder, p)) {          // sensor;temperatura
                available.merge(r[2] + ";" + r[3], 1, Integer::sum);
            }
            List<String> lost = new ArrayList<>();
            for (String[] r : sent) {
                Integer count = available.get(r[1]);
                if (count != null && count > 0) available.put(r[1], count - 1);
                else lost.add(r[0] + ";" + r[1]);
            }
            totalLost += lost.size();
            report.append("p").append(p).append(": enviadas=").append(sent.size())
                    .append(" perdidas=").append(lost.size()).append('\n');
            for (String l : lost) report.append("  perdida: ").append(l).append('\n');
        }
        report.append("Total de leituras perdidas: ").append(totalLost).append('\n');
    }


    // ---------- passo 3a: eventos TRUNCADO ----------

    private static void truncations(StringBuilder report, Path folder,
                                    Map<String, List<Event>> byNode) throws IOException {
        int events = 0;
        for (Map.Entry<String, List<Event>> entry : byNode.entrySet()) {
            String[] key = entry.getKey().split(":");
            int p = Integer.parseInt(key[0]);
            int node = Integer.parseInt(key[1]);
            for (Event e : entry.getValue()) {
                if (!e.type.equals("TRUNCADO")) continue;
                events++;
                long from = num(e.detail, "de");
                long discarded = num(e.detail, "descartados");
                report.append("n").append(node).append(" em p").append(p)
                        .append(" (instante ").append(e.instant).append("): de seq=")
                        .append(from).append(", descartados=").append(discarded)
                        .append(", termos=").append(field(e.detail, "termos"));
                Path dump = folder.resolve("truncados-p" + p + "-n" + node + ".txt");
                if (!Files.exists(dump)) {
                    report.append("; (sem truncados-p" + p + "-n" + node
                            + ".txt: não sei quantos existem no registo final)\n");
                    continue;
                }
                // registos descartados deste nó a partir de 'from', e o registo final da partição
                Map<String, List<Long>> finalSeqs = new java.util.HashMap<>();
                for (String[] r : finalLog(folder, p)) {
                    finalSeqs.computeIfAbsent(r[2] + ";" + r[3], x -> new ArrayList<>())
                            .add(Long.parseLong(r[0]));
                }
                int found = 0;
                int total = 0;
                StringBuilder detail = new StringBuilder();
                for (String line : Files.readAllLines(dump)) {
                    if (line.isBlank()) continue;
                    String[] r = line.trim().split(";");
                    long seq = Long.parseLong(r[0]);
                    if (seq < from) continue;
                    total++;
                    List<Long> seqs = finalSeqs.get(r[2] + ";" + r[3]);
                    if (seqs != null) {
                        for (long s : seqs) {
                            if (s != seq) {
                                found++;
                                detail.append(" ").append(r[2]).append(' ').append(r[3])
                                        .append(": seq ").append(seq).append(" -> ").append(s);
                                break;
                            }
                        }
                    }
                }
                report.append("; existem no registo final com outro seq: ").append(found)
                        .append(" de ").append(total).append(detail).append('\n');
            }
        }
        if (events == 0) report.append("(nenhum evento TRUNCADO)\n");
    }

    // ---------- passo 3b: coincidência final dos três ficheiros ----------

    private static void finalMatch(StringBuilder report, int n, Path folder)
            throws IOException {
        for (int p = 0; p < n; p++) {
            List<List<String>> logs = new ArrayList<>();
            List<Integer> nodes = new ArrayList<>();
            for (int i = 1; i <= Wire.GRUPO; i++) {
                Path path = folder.resolve("no-p" + p + "-n" + i + ".txt");
                if (!Files.exists(path)) continue;
                List<String> lines = new ArrayList<>();
                for (String line : Files.readAllLines(path)) {
                    if (!line.isBlank()) lines.add(line.trim());
                }
                logs.add(lines);
                nodes.add(i);
            }
            if (logs.isEmpty()) {
                report.append("p").append(p).append(": (sem ficheiros de registos)\n");
                continue;
            }
            int min = Integer.MAX_VALUE;
            for (List<String> l : logs) min = Math.min(min, l.size());
            List<Integer> different = new ArrayList<>();
            for (int idx = 0; idx < min; idx++) {
                String first = logs.get(0).get(idx);
                for (List<String> l : logs) {
                    if (!l.get(idx).equals(first)) { different.add(idx + 1); break; }
                }
            }
            StringBuilder sizes = new StringBuilder();
            for (int j = 0; j < nodes.size(); j++) {
                if (sizes.length() > 0) sizes.append(", ");
                sizes.append('n').append(nodes.get(j)).append('=').append(logs.get(j).size());
            }
            report.append("p").append(p).append(": registos por nó {").append(sizes)
                    .append("}; comparados até ao menor (").append(min)
                    .append("); seq divergentes = ").append(different.size());
            if (!different.isEmpty()) report.append(" ").append(different);
            report.append('\n');
        }
    }

    // ---------- leitura e utilitários ----------

    // eventos de cada nó, por ordem do ficheiro; chave "k:i"
    private static Map<String, List<Event>> readEvents(Path folder) throws IOException {
        Map<String, List<Event>> result = new TreeMap<>();
        try (var paths = Files.list(folder)) {
            for (Path path : (Iterable<Path>) paths.sorted()::iterator) {
                Matcher file = EVENT_FILE.matcher(path.getFileName().toString());
                if (!file.matches()) continue;
                int p = Integer.parseInt(file.group(1));
                int node = Integer.parseInt(file.group(2));
                List<Event> list = new ArrayList<>();
                for (String line : Files.readAllLines(path)) {
                    Matcher m = EVENT_LINE.matcher(line.trim());
                    if (m.matches()) {
                        list.add(new Event(Long.parseLong(m.group(1)), m.group(2),
                                Integer.parseInt(m.group(3)), node, m.group(4)));
                    }
                }
                result.put(p + ":" + node, list);
            }
        }
        return result;
    }

    // termo do nó revelado por um evento, ou -1 se o evento não o indica
    private static long termOf(Event e) {
        switch (e.type) {
            case "ARRANQUE":
            case "CANDIDATO":
            case "VOTO":
            case "LIDER":
            case "NOVO_LIDER":
            case "RECEBIDO":
                return num(e.detail, "termo");
            case "TERMO": {                              // detalhe "antigo->novo"
                int arrow = e.detail.indexOf("->");
                if (arrow < 0) return -1;
                try {
                    return Long.parseLong(e.detail.substring(arrow + 2).trim());
                } catch (NumberFormatException x) {
                    return -1;
                }
            }
            default:
                return -1;
        }
    }

    private static String field(String detail, String name) {
        for (String part : detail.split(";")) {
            if (part.startsWith(name + "=")) return part.substring(name.length() + 1);
        }
        return null;
    }

    private static long num(String detail, String name) {
        String value = field(detail, name);
        if (value == null) return -1;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}