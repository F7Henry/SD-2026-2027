
package rep01;

import java.io.IOException;
import java.util.TreeMap;

public class PubSubReplica {

    private static final String TOPIC = "rep01-temperaturas";
    private static final String DEFAULT_API =
            "http://127.0.0.1:5001/api/v0";

    private static class Receiver {

        private final RecordFile file;
        private final TreeMap<Long, SensorRecord> pending =
                new TreeMap<>();
        private long expectedSeq;

        Receiver(RecordFile file) throws IOException {
            this.file = file;
            this.expectedSeq = file.lastSeq() + 1;
        }

        public synchronized void process(String message) {
            try {
                SensorRecord record = SensorRecord.fromLine(message);

                if (record.seq < expectedSeq) {
                    System.out.println(
                            "Duplicado ou antigo ignorado: "
                                    + record.seq);
                    return;
                }

                if (pending.containsKey(record.seq)) {
                    System.out.println(
                            "Duplicado em espera ignorado: "
                                    + record.seq);
                    return;
                }

                pending.put(record.seq, record);

                if (record.seq > expectedSeq) {
                    System.out.println(
                            "Falta o registo " + expectedSeq
                                    + ". Recebido: " + record.seq);
                }

                while (pending.containsKey(expectedSeq)) {
                    SensorRecord next = pending.remove(expectedSeq);
                    file.append(next);

                    System.out.println(
                            "Registo guardado: " + next.toLine());

                    expectedSeq++;
                }

            } catch (IOException e) {
                System.out.println(
                        "Erro ao guardar o registo: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("REJEITADO: " + message);
            }
        }
    }

    public static void main(String[] args)
            throws IOException, InterruptedException {

        String api = args.length >= 1 ? args[0] : DEFAULT_API;

        int replicaId = 1;

        if (args.length >= 2) {
            try {
                replicaId = Integer.parseInt(args[1]);

                if (replicaId <= 0) {
                    System.out.println(
                            "O ID da réplica tem de ser positivo.");
                    return;
                }
            } catch (NumberFormatException e) {
                System.out.println(
                        "O ID da réplica tem de ser um número inteiro.");
                return;
            }
        }

        IpfsPubSub pubsub = new IpfsPubSub(api);

        RecordFile file = new RecordFile(
                "replica-" + replicaId + ".txt");

        Receiver receiver = new Receiver(file);

        System.out.println("Réplica IPFS PubSub iniciada.");
        System.out.println("API: " + api);
        System.out.println("ID da réplica: " + replicaId);
        System.out.println(
                "Próximo número esperado: " + (file.lastSeq() + 1));

        while (true) {
            try {
                pubsub.subscribe(TOPIC, receiver::process);

            } catch (IOException e) {
                System.out.println(
                        "Ligação PubSub interrompida: " + e.getMessage());
                Thread.sleep(2000);
            }
        }
    }
}
