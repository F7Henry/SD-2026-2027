package rep03;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AlarmProcessor {
    private static final double LIMIAR_ALARME = Wire.LIMIAR_ALARME;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: AlarmProcessor <N> [api]");
            return;
        }
        int n = Integer.parseInt(args[0]);
        String api = args.length > 1 ? args[1]
                : "http://127.0.0.1:5001/api/v0";
        IpfsPubSub pubsub = new IpfsPubSub(api);
        ProcessorState state = new ProcessorState(n, pubsub);
        for (int partition = 0; partition < n; partition++) {
            final int k = partition;
            Thread thread = new Thread(() -> subscribe(pubsub, state, k),
                    "processador-p" + k);
            thread.start();
        }
        Thread.currentThread().join();
    }

    private static void subscribe(IpfsPubSub pubsub, ProcessorState state,
                                  int partition) {
        while (true) {
            try {
                pubsub.subscribe(Wire.topicData(partition),
                        message -> state.receive(partition, message));
            } catch (Exception e) {
                System.out.println("Subscrição p" + partition
                        + " interrompida: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static final class ProcessorState {
        private final int n;
        private final IpfsPubSub pubsub;
        private final Object lock = new Object();
        private final VectorClock vector;
        private long lamport;
        private final List<Set<Long>> seen;

        ProcessorState(int n, IpfsPubSub pubsub) {
            if (n <= 0) throw new IllegalArgumentException("N inválido");
            this.n = n;
            this.pubsub = pubsub;
            this.vector = new VectorClock(n);
            this.seen = new ArrayList<>(n);
            for (int i = 0; i < n; i++) seen.add(new HashSet<>());
        }

        void receive(int partition, String message) {
            try {
                String[] parts = Wire.regParts(message);
                SensorRecord record = SensorRecord.fromLine(parts[2], n);
                synchronized (lock) {
                    if (!seen.get(partition).add(record.seq)) return;
                    lamport = Math.max(lamport, record.lamport);
                    vector.merge(record.vector);
                    if (record.sensorId.startsWith("ALARM-")
                            || record.temperature < LIMIAR_ALARME) return;
                    String alarmSensor = "ALARM-" + record.sensorId;
                    int target = new Partitioner(n).of(alarmSensor);
                    pubsub.publish(Wire.topicIn(target), Wire.in(alarmSensor,
                            record.temperature, lamport, vector.copy()));
                    System.out.println("ALARME: " + record.sensorId + " p"
                            + partition + "#" + record.seq + " → p" + target);
                }
            } catch (Exception e) {
                System.out.println("REG rejeitado: " + e.getMessage());
            }
        }
    }
}
