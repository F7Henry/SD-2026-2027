package raft04;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AlarmProcessor {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: AlarmProcessor <N> [api]");
            return;
        }
        int n = Integer.parseInt(args[0]);
        IpfsPubSub pubsub = new IpfsPubSub(args.length > 1 ? args[1]
                : "http://127.0.0.1:5001/api/v0");
        ProcessorState state = new ProcessorState(n, pubsub);
        for (int p = 0; p < n; p++) {
            final int partition = p;
            new Thread(() -> subscribe(pubsub, state, partition),
                    "processador-p" + p).start();
        }
        new Thread(state::check, "verificador-heartbeat").start();
        Thread.currentThread().join();
    }

    private static void subscribe(IpfsPubSub pubsub, ProcessorState state, int p) {
        while (true) {
            try {
                pubsub.subscribe(Wire.topicData(p),
                        message -> state.receive(p, message));
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

    private static final class ProcessorState {
        private final int n;
        private final IpfsPubSub pubsub;
        private final Object lock = new Object();
        private final List<Set<String>> seen = new ArrayList<>();   // pares "seq:termo"
        private final List<HeartbeatMonitor> monitors = new ArrayList<>();
        private final long[] maxTerm;                               // maior termo visto por partição
        private long lamport;
        private VectorClock vector;

        ProcessorState(int n, IpfsPubSub pubsub) {
            this.n = n;
            this.pubsub = pubsub;
            this.maxTerm = new long[n];
            vector = new VectorClock(n);
            for (int p = 0; p < n; p++) {
                seen.add(new HashSet<>());
                monitors.add(new HeartbeatMonitor(p, Wire.PERIODO_HB_MS,
                        Wire.FATOR_TIMEOUT, event -> log(event)));
            }
        }

        void receive(int partition, String message) {
            try {
                Batch batch = Batch.decode(message, n);
                synchronized (lock) {
                    if (batch.term < maxTerm[partition]) {
                        log("IGNORADO;p" + partition + ";termo=" + batch.term
                                + ";maximo=" + maxTerm[partition]);
                        return;
                    }
                    maxTerm[partition] = batch.term;
                }
                monitors.get(partition).onHeartbeat(batch.term, batch.leader, batch.hb);
                log("RECEBIDO;p" + partition + ";termo=" + batch.term
                        + ";lider=n" + batch.leader + ";hb=" + batch.hb
                        + ";ultimoSeq=" + batch.lastSeq + ";n=" + batch.records.size());
                for (SensorRecord record : batch.records) process(partition, record);
            } catch (Exception e) {
                System.out.println("LOTE rejeitado: " + e.getMessage());
            }
        }

        private void process(int partition, SensorRecord record) {
            synchronized (lock) {
                if (!seen.get(partition).add(record.seq + ":" + record.term)) return;
                lamport = Math.max(lamport, record.lamport);
                vector.merge(record.vector);
                if (record.sensorId.startsWith("ALARM-")
                        || record.temperature < Wire.LIMIAR_ALARME) return;
                String alarmSensor = "ALARM-" + record.sensorId;
                int target = new Partitioner(n).of(alarmSensor);
                if (monitors.get(target).state() == HeartbeatMonitor.State.SUSPEITO) {
                    log("AVISO: partição p" + target + " sem líder conhecido; alarme "
                            + record.sensorId + " pode perder-se");
                }
                try {
                    pubsub.publish(Wire.topicIn(target), Wire.in(alarmSensor,
                            record.temperature, lamport, vector.copy()));
                    System.out.println("ALARME: " + record.sensorId + " p"
                            + partition + "#" + record.seq + "(termo " + record.term
                            + ") -> p" + target);
                } catch (Exception e) {
                    System.out.println("ALARME NAO PUBLICADO: " + e.getMessage());
                }
            }
        }

        void check() {
            while (true) {
                monitors.forEach(HeartbeatMonitor::check);
                try {
                    Thread.sleep(Wire.VERIFICACAO_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void log(String event) {
            String line = System.currentTimeMillis() + ";" + event
                    + System.lineSeparator();
            System.out.print(line);
            try {
                Files.writeString(Path.of("monitor-processador.txt"), line,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception e) {
                System.out.println("ERRO MONITOR: " + e.getMessage());
            }
        }
    }
}