
package rep01;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;

public class MulticastReplica {

    private static final String GROUP = "230.0.0.1";
    private static final int PORT = 6789;

    public static void main(String[] args) throws IOException {

        String nomeFicheiro = args.length > 0
            ? args[0]
            : "registos-replica.txt";

        RecordFile file = new RecordFile(nomeFicheiro);
        long expectedSeq = file.lastSeq() + 1;

        TreeMap<Long, SensorRecord> pending = new TreeMap<>();
        InetAddress group = InetAddress.getByName(GROUP);

        try (MulticastSocket socket = new MulticastSocket(PORT)) {

            socket.joinGroup(group);

            System.out.println("Réplica iniciada.");
            System.out.println("Próximo número esperado: " + expectedSeq);

            while (true) {
                byte[] buffer = new byte[4096];
                DatagramPacket packet =
                    new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);

                String message = new String(
                    packet.getData(),
                    packet.getOffset(),
                    packet.getLength(),
                    StandardCharsets.UTF_8
                );

                try {
                    SensorRecord record = SensorRecord.fromLine(message);

                    if (record.seq < expectedSeq) {
                        System.out.println(
                            "Duplicado ou antigo ignorado: " + record.seq
                        );
                        continue;
                    }

                    if (pending.containsKey(record.seq)) {
                        System.out.println(
                            "Duplicado em espera ignorado: " + record.seq
                        );
                        continue;
                    }

                    pending.put(record.seq, record);

                    if (record.seq > expectedSeq) {
                        System.out.println(
                            "Falta o registo " + expectedSeq
                            + ". Recebido: " + record.seq
                        );
                    }

                    while (pending.containsKey(expectedSeq)) {
                        SensorRecord next = pending.remove(expectedSeq);
                        file.append(next);

                        System.out.println(
                            "Registo guardado: " + next.toLine()
                        );

                        expectedSeq++;
                    }

                } catch (RuntimeException e) {
                    System.out.println("Mensagem inválida ignorada.");
                }
            }
        }
    }
}
