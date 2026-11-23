package rep03;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class PartitionReplica {
    private static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";

    private static final class State {
        final int partition;
        final RecordFile file;
        final TreeMap<Long, SensorRecord> pending = new TreeMap<>();
        long last;
        String host;
        int port;

        State(int id, int partition) throws IOException {
            this.partition = partition;
            file = new RecordFile("replica-" + id + "-p" + partition + ".txt");
            last = file.lastSeq();
        }

        synchronized void receive(String message) {
            try {
                String[] parts = Wire.regParts(message);
                host = parts[0];
                port = Integer.parseInt(parts[1]);
                SensorRecord record = SensorRecord.fromLine(parts[2]);
                double loss = Double.parseDouble(System.getProperty("perda", "0"));
                long discard = Long.parseLong(System.getProperty("descartar", "-1"));
                if (Math.random() < loss || (record.seq == discard && !discarded)) {
                    if (record.seq == discard) discarded = true;
                    System.out.println("DESCARTADO (SIMULACAO): " + record.seq);
                    return;
                }
                apply(record);
                if (pending.size() > Wire.LIMITE_RETIDOS) recover();
            } catch (Exception e) {
                System.out.println("REJEITADO: " + message);
            }
        }

        private boolean discarded;

        private void apply(SensorRecord record) throws IOException {
            if (record.seq <= last || pending.containsKey(record.seq)) {
                System.out.println("DUPLICADO: " + record.seq);
                return;
            }
            if (record.seq > last + 1) {
                pending.put(record.seq, record);
                System.out.println("EM ESPERA: " + record.seq);
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

        private void recover() {
            if (host == null || pending.isEmpty()) return;
            long from = last + 1;
            long to = pending.firstKey() - 1;
            if (from > to) return;
            System.out.println("RECUPERACAO: " + from + ".." + to
                    + " pedida a " + host + ":" + port);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), Wire.T_TCP_MS);
                socket.setSoTimeout(Wire.T_TCP_MS);
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println("PEDIDO " + from + " " + to);
                String line;
                boolean finished = false;
                while ((line = in.readLine()) != null) {
                    if ("FIM".equals(line)) {
                        finished = true;
                        break;
                    }
                    if (line.startsWith("ERRO ")) throw new IOException(line);
                    SensorRecord record = SensorRecord.fromLine(line);
                    apply(record);
                    System.out.println("RECUPERADO: " + record.seq);
                }
                if (!finished) throw new IOException("resposta sem FIM");
                while (pending.containsKey(last + 1)) {
                    SensorRecord next = pending.remove(last + 1);
                    file.append(next);
                    last = next.seq;
                }
            } catch (Exception e) {
                System.out.println("RECUPERACAO FALHOU: " + e.getMessage());
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
            states.put(partition, new State(id, partition));
        }
        for (State state : states.values()) {
            Thread thread = new Thread(() -> subscribe(api, state),
                    "replica-p" + state.partition);
            thread.start();
        }
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
                try { Thread.sleep(1000); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
