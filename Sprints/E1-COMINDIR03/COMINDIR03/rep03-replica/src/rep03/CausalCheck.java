package rep03;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CausalCheck {
    private static final Pattern FILE = Pattern.compile("lider-p(\\d+)\\.txt");

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
        List<Event> events = read(folder, n);
        int invalid = 0;
        for (Event event : events) {
            if (event.record.vector.get(event.partition) != event.record.seq) {
                invalid++;
                System.out.println("INVÁLIDO: p" + event.partition
                        + " seq=" + event.record.seq + " V="
                        + event.record.vector);
            }
        }
        long localLamportViolations = 0;
        Map<Integer, Event> previous = new HashMap<>();
        for (Event event : events) {
            Event prior = previous.put(event.partition, event);
            if (prior != null && event.record.lamport <= prior.record.lamport) {
                localLamportViolations++;
                System.out.println("LAMPORT INVÁLIDO: p" + event.partition
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
        long totalConcurrent = concurrent;
        double proportion = totalConcurrent == 0 ? 0
                : (double) concurrentLamportDifferent / totalConcurrent;
        String report = String.format(Locale.ROOT,
                "registos=%d%nV[k]!=seq=%d%na->b=%d%nb->a=%d%n"
                        + "concorrentes=%d%nconcorrentes com L distinto=%d "
                        + "(%.4f)%nviolações Lamport=%d%n"
                        + "violações Lamport na partição=%d%n",
                events.size(), invalid, before, after, concurrent,
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

    private static List<Event> read(Path folder, int n) throws IOException {
        List<Event> result = new ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                Matcher matcher = FILE.matcher(path.getFileName().toString());
                if (!matcher.matches()) continue;
                int partition = Integer.parseInt(matcher.group(1));
                for (String line : Files.readAllLines(path)) {
                    if (!line.trim().isEmpty()) {
                        result.add(new Event(partition,
                                SensorRecord.fromLine(line, n)));
                    }
                }
            }
        }
        return result;
    }
}
