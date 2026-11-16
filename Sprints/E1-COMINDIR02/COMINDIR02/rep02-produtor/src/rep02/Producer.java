package rep02;

import java.io.IOException;
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
        System.out.println("Produtor iniciado para N=" + n);
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                if (input.equalsIgnoreCase("sair")) return;
                String[] fields = input.split("\\s+");
                if (fields.length >= 1 && fields[0].equalsIgnoreCase("rajada")) {
                    if (fields.length != 3) {
                        System.out.println("Formato: rajada <n> <s>");
                        continue;
                    }
                    burst(pubsub, partitioner, Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]), false);
                } else if (fields.length >= 1 &&
                        fields[0].equalsIgnoreCase("rajada-quente")) {
                    if (fields.length != 3) {
                        System.out.println("Formato: rajada-quente <n> <s>");
                        continue;
                    }
                    burst(pubsub, partitioner, Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]), true);
                } else if (fields.length == 2) {
                    publish(pubsub, partitioner, fields[0],
                            Double.parseDouble(fields[1]));
                } else {
                    System.out.println("Formato: <sensor> <temperatura>");
                }
            }
        }
    }

    private static void publish(IpfsPubSub pubsub, Partitioner partitioner,
                                String sensor, double temperature)
            throws IOException, InterruptedException {
        int p = partitioner.of(sensor);
        pubsub.publish(Wire.topicIn(p), Wire.in(sensor, temperature));
        System.out.println("Publicado em p" + p + ": " + sensor);
    }

    private static void burst(IpfsPubSub pubsub, Partitioner partitioner,
                              int count, int sensorCount, boolean hot)
            throws IOException, InterruptedException {
        int[] byPartition = new int[partitioner.count()];
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String sensor;
            if (hot && ThreadLocalRandom.current().nextInt(100) < 80) {
                sensor = "S01";
            } else {
                sensor = "S" + String.format("%02d",
                        ThreadLocalRandom.current().nextInt(2, sensorCount + 1));
            }
            double temperature = ThreadLocalRandom.current().nextDouble(15, 30);
            int p = partitioner.of(sensor);
            pubsub.publish(Wire.topicIn(p), Wire.in(sensor, temperature));
            byPartition[p]++;
        }
        long end = System.currentTimeMillis();
        System.out.println("Primeira publicação: " + start);
        System.out.println("Última publicação: " + end);
        System.out.println("Tempo de envio: " + (end - start) + " ms");
        for (int p = 0; p < byPartition.length; p++) {
            System.out.println("p" + p + ": " + byPartition[p]);
        }
    }
}
