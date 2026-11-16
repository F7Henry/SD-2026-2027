
package rep01;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public class PubSubLeader {

    private static final String TOPIC = "rep01-temperaturas";
    private static final String DEFAULT_API =
            "http://127.0.0.1:5001/api/v0";

    public static void main(String[] args)
            throws IOException, InterruptedException {

        String api = args.length > 0 ? args[0] : DEFAULT_API;
        IpfsPubSub pubsub = new IpfsPubSub(api);

        RecordFile file = new RecordFile("lider.txt");
        long seq = file.lastSeq();

        try (Scanner scanner = new Scanner(System.in)) {

            System.out.println("Líder IPFS PubSub iniciado.");
            System.out.println("API: " + api);
            System.out.println("Escreve: sensor temperatura");
            System.out.println("Exemplo: sensor01 23.5");
            System.out.println("Escreve 'rajada <n>' para enviar n registos.");
            System.out.println("Escreve 'sair' para terminar.");

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("sair")) {
                    break;
                }

                if (input.startsWith("rajada ")) {
                    String[] parts = input.split("\\s+");

                    if (parts.length != 2) {
                        System.out.println("Formato: rajada <n>");
                        continue;
                    }

                    int n;

                    try {
                        n = Integer.parseInt(parts[1]);
                        if (n <= 0) {
                            System.out.println(
                                    "O número tem de ser positivo.");
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Formato: rajada <n>");
                        continue;
                    }

                    long inicioMs = 0;
                    long fimMs = 0;
                    long inicioNs = System.nanoTime();
                    int enviados = 0;

                    try {
                        for (int i = 0; i < n; i++) {
                            double temperatura =
                                    ThreadLocalRandom.current()
                                            .nextDouble(15.0, 30.000001);

                            SensorRecord record = new SensorRecord(
                                    ++seq,
                                    "SIM",
                                    temperatura,
                                    LocalDateTime.now()
                            );

                            file.append(record);

                            if (i == 0) {
                                inicioMs = System.currentTimeMillis();
                            }

                            pubsub.publish(TOPIC, record.toLine());
                            fimMs = System.currentTimeMillis();
                            enviados++;
                        }
                    } catch (IOException e) {
                        System.out.println(
                                "Erro ao guardar ou publicar: "
                                        + e.getMessage());
                    }

                    long duracaoNs = System.nanoTime() - inicioNs;

                    System.out.println("Registos enviados: " + enviados);
                    System.out.println("Instante do primeiro envio: "
                            + inicioMs + " ms");
                    System.out.println("Instante do último envio: "
                            + fimMs + " ms");
                    System.out.printf("Tempo total: %.2f ms%n",
                            duracaoNs / 1_000_000.0);

                    if (duracaoNs > 0) {
                        System.out.printf("Débito do líder: %.2f registos/s%n",
                                enviados / (duracaoNs / 1_000_000_000.0));
                    }

                    continue;
                }

                String[] parts = input.split("\\s+");

                if (parts.length != 2) {
                    System.out.println(
                            "Formato inválido. Exemplo: sensor01 23.5");
                    continue;
                }

                try {
                    String sensorId = parts[0];
                    double temperature = Double.parseDouble(parts[1]);

                    SensorRecord record = new SensorRecord(
                            ++seq,
                            sensorId,
                            temperature,
                            LocalDateTime.now()
                    );

                    file.append(record);
                    pubsub.publish(TOPIC, record.toLine());

                    System.out.println("Enviado: " + record.toLine());

                } catch (NumberFormatException e) {
                    System.out.println(
                            "A temperatura tem de ser um número.");
                } catch (IOException e) {
                    System.out.println(
                            "Erro ao guardar ou publicar: " + e.getMessage());
                }
            }

            System.out.println("Líder IPFS PubSub terminado.");
        }
    }
}
