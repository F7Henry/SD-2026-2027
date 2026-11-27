package rep04;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class PartitionReplica {
    private static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";

    private static final class State {
        final int id;
        final int partition;
        final int n;
        final RecordFile file;
        final TreeMap<Long, SensorRecord> pending = new TreeMap<>();
        final HeartbeatMonitor monitor;
        long last;
        String host;
        int port;

        State(int id, int partition, int n) throws IOException {
            this.id = id;
            this.partition = partition;
            this.n = n;
            file = new RecordFile("replica-" + id + "-p" + partition + ".txt");
            last = file.lastSeq();
            monitor = new HeartbeatMonitor(partition, Wire.PERIODO_HB_MS,
                    Wire.FATOR_TIMEOUT, event -> log(event));
        }

        synchronized void receive(String message) {
            try {
                Batch batch = Batch.decode(message, n);
                monitor.onHeartbeat(batch.inc, batch.hb);
                host = batch.address;
                port = batch.port;
                String range = batch.records.isEmpty() ? "-"
                        : batch.records.get(0).seq + ".."
                        + batch.records.get(batch.records.size() - 1).seq;
                log("RECEBIDO;p" + partition + ";inc=" + batch.inc
                        + ";hb=" + batch.hb + ";ultimoSeq=" + batch.lastSeq
                        + ";n=" + batch.records.size() + ";seq=" + range);
                for (SensorRecord record : batch.records) apply(record);
                if (last < batch.lastSeq) recover(last + 1, batch.lastSeq);
            } catch (Exception e) {
                System.out.println("LOTE rejeitado: " + e.getMessage());
            }
        }

        private void apply(SensorRecord record) throws IOException {
            if (record.seq <= last || pending.containsKey(record.seq)) return;
            if (record.seq > last + 1) {
                pending.put(record.seq, record);
                return;
            }
            writeInOrder(record);
        }

        private void writeInOrder(SensorRecord first) throws IOException {
            file.append(first);
            last = first.seq;
            while (pending.containsKey(last + 1)) {
                SensorRecord next = pending.remove(last + 1);
                file.append(next);
                last = next.seq;
            }
        }

        private void recover(long from, long to) {
            if (host == null || from > to) return;
            log("RECUPERACAO;p" + partition + ";de=" + from + ";ate=" + to);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), Wire.T_TCP_MS);
                socket.setSoTimeout(Wire.T_TCP_MS);
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println("PEDIDO " + from + " " + to);
                String line;
                while ((line = in.readLine()) != null && !"FIM".equals(line)) {
                    if (line.startsWith("ERRO ")) throw new IOException(line);
                    apply(SensorRecord.fromLine(line, n));
                }
            } catch (Exception e) {
                System.out.println("RECUPERACAO FALHOU: " + e.getMessage());
            }
        }

        private void check() {
            monitor.check();
        }

        private void log(String event) {
            String line = System.currentTimeMillis() + ";" + event
                    + System.lineSeparator();
            System.out.print(line);
            try {
                Files.writeString(Path.of("monitor-replica" + id + ".txt"), line,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.out.println("ERRO MONITOR: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Uso: PartitionReplica <id> <p1,p2,...> [api]");
            return;
        }
        int id = Integer.parseInt(args[0]);
        String api = args.length > 2 ? args[2] : DEFAULT_API;
        Map<Integer, State> states = new HashMap<>();
        for (String value : args[1].split(",")) {
            int partition = Integer.parseInt(value.trim());
            states.put(partition, new State(id, partition, 3));
        }
        for (State state : states.values()) {
            Thread thread = new Thread(() -> subscribe(api, state),
                    "replica-p" + state.partition);
            thread.start();
        }
        Thread checker = new Thread(() -> {
            while (true) {
                states.values().forEach(State::check);
                try {
                    Thread.sleep(Wire.VERIFICACAO_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "verificador-heartbeat");
        checker.start();
        Thread.currentThread().join();
    }

    private static void subscribe(String api, State state) {
        while (true) {
            try {
                new IpfsPubSub(api).subscribe(Wire.topicData(state.partition),
                        state::receive);
            } catch (Exception e) {
                System.out.println("SUBSCRICAO p" + state.partition
                        + " INTERROMPIDA: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
