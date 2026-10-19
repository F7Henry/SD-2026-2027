
package rep01;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Scanner;

public class MulticastLeader {

    private static final String GROUP = "230.0.0.1";
    private static final int PORT = 6789;

    public static void main(String[] args) throws IOException {

        String nomeFicheiro = args.length > 0
            ? args[0]
            : "registos.txt";

        RecordFile file = new RecordFile(nomeFicheiro);
        long seq = file.lastSeq();

        InetAddress group = InetAddress.getByName(GROUP);

        try (MulticastSocket socket = new MulticastSocket();
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Leader iniciado.");
            System.out.println("Escreve: sensor temperatura");
            System.out.println("Exemplo: sensor01 23.5");
            System.out.println("Escreve 'rajada <n>' para enviar n mensagens.");
            System.out.println("Escreve 'sair' para terminar.");

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("sair")) {
                    break;
                }

                String[] comando = input.split("\\s+");

                if (comando.length == 2
                        && comando[0].equalsIgnoreCase("rajada")) {

                    try {
                        int quantidade = Integer.parseInt(comando[1]);

                        if (quantidade <= 0) {
                            System.out.println(
                                "A quantidade tem de ser positiva."
                            );
                            continue;
                        }

                        long start = System.nanoTime();
                        int sent = 0;

                        for (int i = 0; i < quantidade; i++) {
                            SensorRecord record = new SensorRecord(
                                ++seq,
                                "sensor01",
                                20.0 + (i % 10),
                                LocalDateTime.now()
                            );

                            file.append(record);

                            byte[] data = record.toLine()
                                .getBytes(StandardCharsets.UTF_8);

                            DatagramPacket packet = new DatagramPacket(
                                data, data.length, group, PORT
                            );

                            socket.send(packet);
                            sent++;
                        }

                        long elapsed = System.nanoTime() - start;
                        double elapsedMs = elapsed / 1_000_000.0;
                        double throughput =
                            sent / (elapsed / 1_000_000_000.0);

                        System.out.println(
                            "Rajada enviada: " + sent + " mensagens."
                        );

                        System.out.printf(
                            "Tempo de envio: %.2f ms%n",
                            elapsedMs
                        );

                        System.out.printf(
                            "Débito: %.2f registos/s%n",
                            throughput
                        );

                    } catch (NumberFormatException e) {
                        System.out.println(
                            "Quantidade inválida. Exemplo: rajada 1000"
                        );
                    }

                    continue;
                }

                String[] parts = input.split("\\s+");

                if (parts.length != 2) {
                    System.out.println(
                        "Formato inválido. Exemplo: sensor01 23.5"
                    );
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

                    byte[] data = record.toLine()
                        .getBytes(StandardCharsets.UTF_8);

                    DatagramPacket packet = new DatagramPacket(
                        data, data.length, group, PORT
                    );

                    socket.send(packet);

                    System.out.println("Enviado: " + record.toLine());

                } catch (NumberFormatException e) {
                    System.out.println(
                        "A temperatura tem de ser um número."
                    );
                }
            }

            System.out.println("Leader terminado.");
        }
    }
}
