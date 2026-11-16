package rep02;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

public class PartitionLeader {
    private static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Uso: PartitionLeader <k> <N> [endereço] [api]");
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
        long[] next = {file.lastSeq() + 1};

        Thread input = new Thread(() -> {
            try {
                pubsub.subscribe(Wire.topicIn(k), message -> {
                    try {
                        if (!"IN".equals(Wire.type(message))) throw new IllegalArgumentException();
                        String[] fields = Wire.body(message).split(";", 2);
                        if (fields.length != 2) throw new IllegalArgumentException();
                        String sensor = fields[0];
                        if (partitioner.of(sensor) != k) {
                            System.out.println("PARTIÇÃO ERRADA: " + sensor);
                            return;
                        }
                        double temperature = Double.parseDouble(fields[1]);
                        SensorRecord record;
                        synchronized (lock) {
                            record = new SensorRecord(next[0]++, sensor,
                                    temperature, LocalDateTime.now());
                            file.append(record);
                        }
                        pubsub.publish(Wire.topicData(k),
                                Wire.reg(host, port, record));
                    } catch (Exception e) {
                        System.out.println("REJEITADO: " + message);
                    }
                });
            } catch (Exception e) {
                System.out.println("Subscrição interrompida: " + e.getMessage());
            }
        }, "entrada-p" + k);
        input.start();

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Líder p" + k + " à escuta em " + port);
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
                        System.out.println("RECUPERAÇÃO: " + from + ".." + to
                                + " (" + records.size() + " registos)");
                    } catch (IllegalArgumentException e) {
                        out.println("ERRO " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.out.println("Recuperação interrompida: " + e.getMessage());
                }
            }
        }
    }

    private static long[] parseRequest(String request) {
        if (request == null) throw new IllegalArgumentException("pedido vazio");
        String[] fields = request.trim().split("\\s+");
        if (fields.length != 3 || !"PEDIDO".equals(fields[0])) {
            throw new IllegalArgumentException("pedido inválido");
        }
        long from = Long.parseLong(fields[1]);
        long to = Long.parseLong(fields[2]);
        if (from > to) throw new IllegalArgumentException("intervalo inválido");
        return new long[] {from, to};
    }
}
