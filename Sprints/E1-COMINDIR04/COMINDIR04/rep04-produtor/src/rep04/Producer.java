package rep04;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public class Producer {
    private static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: Producer <N> [api]");
            return;
        }
        int n = Integer.parseInt(args[0]);
        Partitioner partitioner = new Partitioner(n);
        IpfsPubSub pubsub = new IpfsPubSub(args.length > 1 ? args[1] : DEFAULT_API);
        List<HeartbeatMonitor> monitors = new ArrayList<>();
        for (int p = 0; p < n; p++) {
            int partition = p;
            HeartbeatMonitor monitor = new HeartbeatMonitor(p, Wire.PERIODO_HB_MS,
                    Wire.FATOR_TIMEOUT, Producer::log);
            monitors.add(monitor);
            new Thread(() -> subscribe(pubsub, monitor, partition),
                    "produtor-p" + p).start();
        }
        new Thread(() -> {
            while (true) {
                monitors.forEach(HeartbeatMonitor::check);
                try {
                    Thread.sleep(Wire.VERIFICACAO_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "verificador-heartbeat").start();

        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                if (input.equalsIgnoreCase("sair")) return;
                String[] fields = input.split("\\s+");
                if (fields.length >= 1 && fields[0].equalsIgnoreCase("rajada")) {
                    if (fields.length == 3) burst(pubsub, partitioner,
                            Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), false);
                    else System.out.println("Formato: rajada <n> <s>");
                } else if (fields.length >= 1
                        && fields[0].equalsIgnoreCase("rajada-quente")) {
                    if (fields.length == 3) burst(pubsub, partitioner,
                            Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), true);
                    else System.out.println("Formato: rajada-quente <n> <s>");
                } else if (fields.length == 2) {
                    publish(pubsub, partitioner, fields[0],
                            Double.parseDouble(fields[1]), monitors);
                } else {
                    System.out.println("Formato: <sensor> <temperatura>");
                }
            }
        }
    }

    private static void subscribe(IpfsPubSub pubsub, HeartbeatMonitor monitor, int p) {
        while (true) {
            try {
                pubsub.subscribe(Wire.topicData(p), message -> {
                    Batch batch = Batch.decode(message, 3);
                    monitor.onHeartbeat(batch.inc, batch.hb);
                    log("RECEBIDO;p" + p + ";inc=" + batch.inc + ";hb="
                            + batch.hb + ";ultimoSeq=" + batch.lastSeq
                            + ";n=" + batch.records.size());
                });
            } catch (Exception e) {
                System.out.println("SUBSCRICAO p" + p + " INTERROMPIDA: "
                        + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void publish(IpfsPubSub pubsub, Partitioner partitioner,
                                String sensor, double temperature,
                                List<HeartbeatMonitor> monitors)
            throws IOException, InterruptedException {
        int p = partitioner.of(sensor);
        if (monitors.get(p).state() == HeartbeatMonitor.State.SUSPEITO) {
            log("AVISO: líder p" + p + " suspeito; leitura " + sensor
                    + " pode perder-se");
        }
        pubsub.publish(Wire.topicIn(p), Wire.in(sensor, temperature, 0,
                new VectorClock(partitioner.count())));
        System.out.println("Publicado em p" + p + ": " + sensor);
    }

    private static void burst(IpfsPubSub pubsub, Partitioner partitioner,
                              int count, int sensorCount, boolean hot)
            throws IOException, InterruptedException {
        int[] byPartition = new int[partitioner.count()];
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String sensor = hot && ThreadLocalRandom.current().nextInt(100) < 80
                    ? "S01" : "S" + String.format("%02d",
                    ThreadLocalRandom.current().nextInt(2, sensorCount + 1));
            double temperature = ThreadLocalRandom.current().nextDouble(15, 30);
            int p = partitioner.of(sensor);
            pubsub.publish(Wire.topicIn(p), Wire.in(sensor, temperature, 0,
                    new VectorClock(partitioner.count())));
            byPartition[p]++;
        }
        long end = System.currentTimeMillis();
        System.out.println("PRIMEIRA PUBLICACAO: " + start);
        System.out.println("ULTIMA PUBLICACAO: " + end);
        System.out.println("TEMPO DE ENVIO: " + (end - start) + " ms");
        for (int p = 0; p < byPartition.length; p++) {
            System.out.println("p" + p + ": " + byPartition[p]);
        }
    }

    private static void log(String event) {
        String line = System.currentTimeMillis() + ";" + event
                + System.lineSeparator();
        System.out.print(line);
        try {
            Files.writeString(Path.of("monitor-produtor.txt"), line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("ERRO MONITOR: " + e.getMessage());
        }
    }
}
