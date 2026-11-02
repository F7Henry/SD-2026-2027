package rep04;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeartbeatCheck {
    private static final Pattern HB_FILE = Pattern.compile("hb-p(\\d+)\\.txt");
    private static final Pattern MONITOR = Pattern.compile(
            "(\\d+);([^;]+);p(\\d+);(.*)");

    private HeartbeatCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Uso: HeartbeatCheck <N> <pasta> [falhas.txt]");
            return;
        }
        int n = Integer.parseInt(args[0]);
        Path folder = Path.of(args[1]);
        Map<Integer, List<Hb>> heartbeats = readHeartbeats(folder);
        StringBuilder report = new StringBuilder();
        for (int p = 0; p < n; p++) {
            List<Hb> values = new ArrayList<>(
                    heartbeats.getOrDefault(p, List.of()));
            values.sort(Comparator.comparingLong(h -> h.instant));
            long totalRecords = values.stream().mapToLong(h -> h.count).sum();
            long empty = values.stream().filter(h -> h.count == 0).count();
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < values.size(); i++) {
                gaps.add(values.get(i).instant - values.get(i - 1).instant);
            }
            report.append("p").append(p).append(": lotes=").append(values.size())
                    .append(" vazios=").append(values.isEmpty() ? 0
                            : (double) empty / values.size())
                    .append(" registos_media=").append(values.isEmpty() ? 0
                            : (double) totalRecords / values.size())
                    .append(" registos_max=").append(values.stream()
                            .mapToLong(h -> h.count).max().orElse(0))
                    .append(" intervalo_medio_ms=").append(average(gaps))
                    .append(" intervalo_max_ms=").append(gaps.stream()
                            .mapToLong(Long::longValue).max().orElse(0)).append('\n');
        }
        List<Event> events = readEvents(folder);
        long suspicions = events.stream().filter(e -> e.type.equals("SUSPEITA")).count();
        long resumes = events.stream().filter(e -> e.type.equals("RETOMA")).count();
        long lost = events.stream().filter(e -> e.type.equals("HB_PERDIDO")).count();
        long recovery = events.stream().filter(e -> e.type.equals("RECUPERACAO")).count();
        report.append("suspeitas=").append(suspicions)
                .append(" falsas_suspeitas=").append(falseSuspicions(events))
                .append(" falhas_reais=").append(suspicions - falseSuspicions(events))
                .append(" HB_PERDIDO=").append(lost)
                .append(" RECUPERACAO=").append(recovery).append('\n');
        report.append("latencia_lote_ms_media_max=")
                .append(batchLatency(folder, events)).append('\n');
        report.append("bytes_registo_lote_medio=")
                .append(bytesPerRecord(heartbeats))
                .append(" bytes_reg_equivalente_medio=")
                .append(regBytes(folder)).append('\n');
        if (args.length == 3) {
            report.append("deteccao_ms_por_falha=").append(detection(folder, Path.of(args[2])))
                    .append('\n');
        }
        Files.writeString(folder.resolve("heartbeat-report.txt"), report.toString());
        System.out.print(report);
    }

    private static Map<Integer, List<Hb>> readHeartbeats(Path folder) throws IOException {
        Map<Integer, List<Hb>> result = new HashMap<>();
        try (var paths = Files.list(folder)) {
            paths.forEach(path -> {
                Matcher matcher = HB_FILE.matcher(path.getFileName().toString());
                if (!matcher.matches()) return;
                int p = Integer.parseInt(matcher.group(1));
                try {
                    for (String line : Files.readAllLines(path)) {
                        String[] f = line.split(";", 6);
                        if (f.length == 6) result.computeIfAbsent(p, k -> new ArrayList<>())
                                .add(new Hb(Long.parseLong(f[2]), Integer.parseInt(f[4]),
                                        Long.parseLong(f[5])));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return result;
    }

    private static List<Event> readEvents(Path folder) throws IOException {
        List<Event> result = new ArrayList<>();
        try (var paths = Files.list(folder)) {
            paths.filter(p -> p.getFileName().toString().startsWith("monitor-"))
                    .forEach(path -> {
                        try {
                            for (String line : Files.readAllLines(path)) {
                                Matcher m = MONITOR.matcher(line);
                                if (m.matches()) result.add(new Event(
                                        Long.parseLong(m.group(1)), m.group(2),
                                        Integer.parseInt(m.group(3)), m.group(4)));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return result;
    }

    private static Map<Integer, Long> detection(Path folder, Path failures) throws IOException {
        Map<Integer, Long> result = new HashMap<>();
        for (String line : Files.readAllLines(failures)) {
            String[] f = line.trim().split(";");
            if (f.length == 2) result.put(Integer.parseInt(f[0].substring(1)),
                    Long.parseLong(f[1]));
        }
        for (Event event : readEvents(folder)) {
            if (event.type.equals("SUSPEITA")) {
                Long failure = result.get(event.partition);
                if (failure != null) result.put(event.partition, event.instant - failure);
            }
        }
        return result;
    }

    private static long falseSuspicions(List<Event> events) {
        events.sort(Comparator.comparingLong(e -> e.instant));
        long result = 0;
        for (int i = 0; i < events.size(); i++) {
            Event current = events.get(i);
            if (!current.type.equals("SUSPEITA")) continue;
            for (int j = i + 1; j < events.size(); j++) {
                Event next = events.get(j);
                if (next.partition != current.partition) continue;
                if (next.type.equals("SUSPEITA")) break;
                if (next.type.equals("RETOMA")) {
                    result++;
                    break;
                }
            }
        }
        return result;
    }

    private static String batchLatency(Path folder, List<Event> events)
            throws IOException {
        Map<String, Long> recordTimes = new HashMap<>();
        try (var paths = Files.list(folder)) {
            paths.filter(p -> p.getFileName().toString().matches("lider-p\\d+\\.txt"))
                    .forEach(path -> {
                        try {
                            int p = Integer.parseInt(path.getFileName().toString()
                                    .replaceAll("\\D+", ""));
                            for (String line : Files.readAllLines(path)) {
                                SensorRecord record = SensorRecord.fromLine(line);
                                long instant = LocalDateTime.parse(line.split(";", 6)[3])
                                        .atZone(ZoneId.systemDefault()).toInstant()
                                        .toEpochMilli();
                                recordTimes.put(p + ":" + record.seq, instant);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        List<Long> latencies = new ArrayList<>();
        for (Event event : events) {
            if (!event.type.equals("RECEBIDO")) continue;
            Matcher matcher = Pattern.compile(".*seq=(\\d+)\\.\\.(\\d+)")
                    .matcher(event.detail);
            if (!matcher.matches()) continue;
            long first = Long.parseLong(matcher.group(1));
            long last = Long.parseLong(matcher.group(2));
            for (long seq = first; seq <= last; seq++) {
                Long source = recordTimes.get(event.partition + ":" + seq);
                if (source != null) latencies.add(event.instant - source);
            }
        }
        long max = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
        return String.format(java.util.Locale.ROOT, "%.2f/%d", average(latencies), max);
    }

    private static String bytesPerRecord(Map<Integer, List<Hb>> heartbeats) {
        long bytes = 0;
        long records = 0;
        for (List<Hb> values : heartbeats.values()) {
            for (Hb heartbeat : values) {
                bytes += heartbeat.bytes;
                records += heartbeat.count;
            }
        }
        return records == 0 ? "0" : String.format(java.util.Locale.ROOT,
                "%.2f", (double) bytes / records);
    }

    private static String regBytes(Path folder) throws IOException {
        long total = 0;
        long count = 0;
        try (var paths = Files.list(folder)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!path.getFileName().toString().matches("lider-p\\d+\\.txt")) continue;
                for (String line : Files.readAllLines(path)) {
                    total += ("REG;127.0.0.1;7000;" + line)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                    count++;
                }
            }
        }
        return count == 0 ? "0" : String.format(java.util.Locale.ROOT,
                "%.2f", (double) total / count);
    }

    private static double average(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static final class Hb {
        final long instant;
        final int count;
        final long bytes;

        Hb(long instant, int count, long bytes) {
            this.instant = instant;
            this.count = count;
            this.bytes = bytes;
        }
    }

    private static final class Event {
        final long instant;
        final String type;
        final int partition;
        final String detail;

        Event(long instant, String type, int partition, String detail) {
            this.instant = instant;
            this.type = type;
            this.partition = partition;
            this.detail = detail;
        }
    }
}
