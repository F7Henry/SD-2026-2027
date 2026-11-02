package rep04;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PartitionLeader {
    private static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Uso: PartitionLeader <k> <N> [endereco] [api]");
            return;
        }
        int k = Integer.parseInt(args[0]);
        Partitioner partitioner = new Partitioner(Integer.parseInt(args[1]));
        String host = args.length > 2 ? args[2] : "127.0.0.1";
        String api = args.length > 3 ? args[3] : DEFAULT_API;
        RecordFile file = new RecordFile("lider-p" + k + ".txt");
        IpfsPubSub pubsub = new IpfsPubSub(api);
        int port = Wire.PORTO_BASE + k;
        Object lock = new Object();
        List<SensorRecord> pending = new ArrayList<>();
        long inc = System.currentTimeMillis();
        long[] heartbeat = {0};
        SensorRecord last = file.last(partitioner.count());
        long[] next = {last == null ? 1 : last.seq + 1};
        long[] lamport = {last == null ? 0 : last.lamport};
        VectorClock[] vector = {last == null
                ? new VectorClock(partitioner.count()) : last.vector.copy()};

        Thread input = new Thread(() -> {
            try {
                pubsub.subscribe(Wire.topicIn(k), message -> {
                    try {
                        if (!"IN".equals(Wire.type(message))) throw new IllegalArgumentException();
                        String[] fields = Wire.body(message).split(";", 4);
                        if (fields.length != 4) throw new IllegalArgumentException();
                        String sensor = fields[0];
                        if (partitioner.of(sensor) != k) {
                            System.out.println("PARTICAO ERRADA: " + sensor);
                            return;
                        }
                        double temperature = Double.parseDouble(fields[1]);
                        long messageLamport = Long.parseLong(fields[2]);
                        VectorClock messageVector = VectorClock.parse(
                                fields[3], partitioner.count());
                        SensorRecord record;
                        synchronized (lock) {
                            lamport[0] = Math.max(lamport[0], messageLamport) + 1;
                            vector[0].merge(messageVector);
                            vector[0].tick(k);
                            long sequence = next[0]++;
                            if (vector[0].get(k) != sequence) {
                                throw new IllegalStateException("V[k] != seq");
                            }
                            record = new SensorRecord(sequence, sensor,
                                    temperature, LocalDateTime.now(),
                                    lamport[0], vector[0].copy());
                            file.append(record);
                            pending.add(record);
                            System.out.println("EVENTO: seq=" + record.seq
                                    + " L=" + record.lamport + " V="
                                    + record.vector);
                        }
                    } catch (Exception e) {
                        System.out.println("REJEITADO: " + message);
                    }
                });
            } catch (Exception e) {
                System.out.println("SUBSCRICAO INTERROMPIDA: " + e.getMessage());
            }
        }, "entrada-p" + k);
        input.start();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            List<SensorRecord> batchRecords;
            long currentSeq;
            long currentHb;
            synchronized (lock) {
                batchRecords = new ArrayList<>(pending);
                pending.clear();
                currentSeq = next[0] - 1;
                currentHb = ++heartbeat[0];
            }
            Batch batch = new Batch(host, port, inc, currentHb, currentSeq,
                    batchRecords);
            String encoded = batch.encode();
            try {
                pubsub.publish(Wire.topicData(k), encoded);
                long now = System.currentTimeMillis();
                String hbLine = inc + ";" + currentHb + ";" + now + ";"
                        + currentSeq + ";" + batchRecords.size() + ";"
                        + encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + System.lineSeparator();
                Files.writeString(Path.of("hb-p" + k + ".txt"), hbLine,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
                System.out.println("HEARTBEAT: hb=" + currentHb + " n="
                        + batchRecords.size() + " ultimoSeq=" + currentSeq);
            } catch (Exception e) {
                System.out.println("ERRO AO PUBLICAR LOTE: " + e.getMessage());
            }
        }, 0, Wire.PERIODO_HB_MS, TimeUnit.MILLISECONDS);

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("LIDER p" + k + " A ESCUTA EM " + port);
            while (true) {
                try (Socket socket = server.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(
                             socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(new OutputStreamWriter(
                             socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    socket.setSoTimeout(Wire.T_TCP_MS);
                    String request = in.readLine();
                    try {
                        long[] interval = parseRequest(request);
                        long from = interval[0];
                        long to;
                        synchronized (lock) {
                            to = Math.min(interval[1], next[0] - 1);
                        }
                        List<SensorRecord> records = file.range(from, to);
                        for (SensorRecord record : records) {
                            out.println(record.toLine());
                        }
                        out.println("FIM");
                        System.out.println("RECUPERACAO: " + from + ".." + to
                                + " (" + records.size() + " registos)");
                    } catch (IllegalArgumentException e) {
                        out.println("ERRO " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.out.println("RECUPERACAO INTERROMPIDA: " + e.getMessage());
                }
            }
        }
    }

    private static long[] parseRequest(String request) {
        if (request == null) throw new IllegalArgumentException("pedido vazio");
        String[] fields = request.trim().split("\\s+");
        if (fields.length != 3 || !"PEDIDO".equals(fields[0])) {
            throw new IllegalArgumentException("pedido invalido");
        }
        long from = Long.parseLong(fields[1]);
        long to = Long.parseLong(fields[2]);
        if (from > to) throw new IllegalArgumentException("intervalo invalido");
        return new long[]{from, to};
    }
}
