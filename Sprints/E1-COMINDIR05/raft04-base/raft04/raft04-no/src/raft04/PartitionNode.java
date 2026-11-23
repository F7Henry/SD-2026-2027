package raft04;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PartitionNode {
    private static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";

    private final int k;                       // partição de dados
    private final int i;                       // nó (1..3)
    private final int n;                       // número de partições (dimensão do vetor)
    private final Partitioner partitioner;
    private final IpfsPubSub pubsub = new IpfsPubSub(DEFAULT_API);
    private final RecordFile file;
    private final RaftState raft;
    private final Path eventsFile;

    // Monitor do nó: protege 'last' e o estado de líder/seguidor.
    // ORDEM DOS LOCKS: monitor do nó -> monitor de RaftState (nunca ao contrário).
    // O log (logLock) é independente e nunca adquire nenhum dos outros.
    private final Object lock = new Object();
    private final Object logLock = new Object();

    private SensorRecord last;                 // último registo do ficheiro (null se vazio)

    // --- estado do líder
    private long seq;
    private long lamport;
    private VectorClock vector;
    private long hb;
    private List<SensorRecord> pending = new ArrayList<>();
    private ScheduledExecutorService scheduler;

    // --- estado do seguidor: último (termo, hb) aplicado, para ignorar lotes atrasados
    private long seenTerm = -1;
    private long seenHb = 0;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Uso: PartitionNode <k> <i> [N]");
            return;
        }
        int k = Integer.parseInt(args[0]);
        int i = Integer.parseInt(args[1]);
        int n = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        new PartitionNode(k, i, n).run();
    }

    public PartitionNode(int k, int i, int n) throws IOException {
        this.k = k;
        this.i = i;
        this.n = n;
        this.partitioner = new Partitioner(n);
        this.file = new RecordFile("no-p" + k + "-n" + i + ".txt");
        this.eventsFile = Path.of("eventos-p" + k + "-n" + i + ".txt");
        this.last = file.last(n);
        this.raft = new RaftState(k, i, Wire.GRUPO, Wire.ELEICAO_MIN_MS,
                Wire.ELEICAO_MAX_MS, Path.of("estado-p" + k + "-n" + i + ".txt"),
                this::event);
    }

    private void run() throws Exception {
        synchronized (lock) {
            event("ARRANQUE;p" + k + ";no=n" + i + ";termo=" + raft.term()
                    + ";ultimoSeq=" + lastSeq() + ";ultimoTermo=" + lastTerm());
        }
        start("recuperacao-p" + k + "-n" + i, this::serveRecovery);
        start("entrada-p" + k, () -> subscribeLoop(Wire.topicIn(k), this::onIn));
        start("dados-p" + k, () -> subscribeLoop(Wire.topicData(k), this::onBatch));
        start("eleicao-p" + k, () -> subscribeLoop(Wire.topicElection(k), this::onElection));
        start("verificacao-p" + k, this::checkLoop);
        Thread.currentThread().join();
    }

    // ---------- utilitários ----------

    private long lastSeq()  { return last == null ? 0 : last.seq; }    // chamar com o lock do nó
    private long lastTerm() { return last == null ? 0 : last.term; }   // chamar com o lock do nó

    private void start(String name, Runnable r) {
        new Thread(r, name).start();
    }

    // evento na consola e em eventos-p<k>-n<i>.txt, precedido do instante
    private void event(String e) {
        String line = System.currentTimeMillis() + ";" + e + System.lineSeparator();
        synchronized (logLock) {
            System.out.print(line);
            try {
                Files.writeString(eventsFile, line,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException x) {
                System.out.println("ERRO EVENTOS: " + x.getMessage());
            }
        }
    }

    // publica nos tópicos de dados/eleição; um nó isolado não publica
    private void publish(String topic, String message) {
        if (Wire.isolated(k, i)) return;
        try {
            pubsub.publish(topic, message);
        } catch (Exception e) {
            event("ERRO;p" + k + ";publicar: " + e.getMessage());
        }
    }

    private void subscribeLoop(String topic, Consumer<String> handler) {
        while (true) {
            try {
                pubsub.subscribe(topic, handler);
            } catch (Exception e) {
                System.out.println("SUBSCRICAO " + topic + " INTERROMPIDA: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // ---------- eleição ----------

    // a cada VERIFICACAO_MS: se o prazo expirou, passa a candidato e pede votos
    private void checkLoop() {
        while (true) {
            VoteRequest request = null;
            synchronized (lock) {
                long t = raft.tick();                 // grava o estado ANTES de publicar
                if (t > 0) request = new VoteRequest(t, i, lastSeq(), lastTerm());
            }
            if (request != null) publish(Wire.topicElection(k), request.encode());
            try {
                Thread.sleep(Wire.VERIFICACAO_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void onElection(String message) {
        if (Wire.isolated(k, i)) return;
        try {
            String type = Wire.type(message);
            if (type.equals("PEDIDO_VOTO")) {
                VoteRequest request = VoteRequest.decode(message);
                if (request.candidate == i) return;   // ignora as próprias mensagens
                RaftState.Vote vote;
                synchronized (lock) {
                    vote = raft.onVoteRequest(request.term, request.candidate,
                            request.lastSeq, request.lastTerm, lastSeq(), lastTerm());
                }
                publish(Wire.topicElection(k), new VoteReply(vote.term, i,
                        request.candidate, vote.granted).encode());
            } else if (type.equals("VOTO")) {
                VoteReply reply = VoteReply.decode(message);
                if (reply.candidate != i || reply.voter == i) return;
                synchronized (lock) {
                    if (raft.onVote(reply.term, reply.voter, reply.candidate, reply.granted)) {
                        becomeLeader();
                    }
                }
            }
        } catch (Exception e) {
            event("ERRO;p" + k + ";eleicao: " + e.getMessage());
        }
    }

    // ---------- recuperação TCP (servida por qualquer nó) ----------

    private void serveRecovery() {
        int port = Wire.nodePort(k, i);
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("NO p" + k + " n" + i + " A ESCUTA EM " + port);
            while (true) {
                try (Socket socket = server.accept()) {
                    if (Wire.isolated(k, i)) continue;     // nó isolado: ligação falha
                    socket.setSoTimeout(Wire.T_TCP_MS);
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    String request = in.readLine();
                    try {
                        long[] interval = parseRequest(request);
                        for (SensorRecord record : file.range(interval[0], interval[1])) {
                            out.println(record.toLine());
                        }
                        out.println("FIM");
                    } catch (IllegalArgumentException e) {
                        out.println("ERRO " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.out.println("RECUPERACAO INTERROMPIDA: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            event("ERRO;p" + k + ";servidor TCP: " + e.getMessage());
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

    // ================= líder =================

    private void onIn(String message) {
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
            VectorClock messageVector = VectorClock.parse(fields[3], n);
            SensorRecord record;
            synchronized (lock) {
                long t = raft.termIfLeader();           // lido e usado dentro da mesma secção
                if (t < 0) {
                    event("IGNORADO;p" + k + ";leitura " + sensor + " (nao sou lider)");
                    return;
                }
                lamport = Math.max(lamport, messageLamport) + 1;
                vector.merge(messageVector);
                vector.tick(k);
                long sequence = ++seq;
                if (vector.get(k) != sequence) throw new IllegalStateException("V[k] != seq");
                record = new SensorRecord(sequence, t, sensor, temperature,
                        LocalDateTime.now(), lamport, vector.copy());
                file.append(record);
                last = record;
                pending.add(record);
            }
            System.out.println("EVENTO: seq=" + record.seq + " termo=" + record.term
                    + " L=" + record.lamport + " V=" + record.vector);
        } catch (Exception e) {
            System.out.println("REJEITADO: " + message);
        }
    }

    private void becomeLeader() {
        synchronized (lock) {
            seq = lastSeq();
            lamport = last == null ? 0 : last.lamport;
            vector = last == null ? new VectorClock(n) : last.vector.copy();
            hb = 0;
            pending = new ArrayList<>();
            if (scheduler != null) scheduler.shutdownNow();
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::enviarLote, 0,
                    Wire.PERIODO_HB_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void enviarLote() {
        try {
            List<SensorRecord> records;
            long t, lastS, lastT, currentHb;
            synchronized (lock) {
                t = raft.termIfLeader();
                if (t < 0) {                 // deixou de ser líder: cancela o agendamento
                    scheduler.shutdown();
                    return;
                }
                records = pending;
                pending = new ArrayList<>();
                lastS = lastSeq();
                lastT = lastTerm();
                currentHb = ++hb;
            }
            if (Wire.isolated(k, i)) return;     // nó isolado não publica
            Batch batch = new Batch(t, i, "127.0.0.1", Wire.nodePort(k, i),
                    currentHb, lastS, lastT, records);
            String encoded = batch.encode();
            pubsub.publish(Wire.topicData(k), encoded);
            String hbLine = t + ";" + currentHb + ";" + System.currentTimeMillis() + ";"
                    + lastS + ";" + lastT + ";" + records.size() + ";"
                    + encoded.getBytes(StandardCharsets.UTF_8).length
                    + System.lineSeparator();
            Files.writeString(Path.of("hb-p" + k + "-n" + i + ".txt"), hbLine,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("HEARTBEAT: termo=" + t + " hb=" + currentHb
                    + " n=" + records.size() + " ultimoSeq=" + lastS);
        } catch (Exception e) {
            event("ERRO;p" + k + ";lote: " + e.getMessage());
        }
    }

    // ================= seguidor =================

    private void onBatch(String message) {
        if (Wire.isolated(k, i)) return;
        try {
            Batch batch = Batch.decode(message, n);
            if (batch.leader == i) return;                 // ignora as próprias mensagens
            synchronized (lock) {
                if (!raft.onBatch(batch.term, batch.leader)) {
                    event("IGNORADO;p" + k + ";termo=" + batch.term
                            + ";lider=n" + batch.leader);
                    return;
                }
                if (batch.term == seenTerm && batch.hb <= seenHb) return;  // atrasado/duplicado
                seenTerm = batch.term;
                seenHb = batch.hb;
                event("RECEBIDO;p" + k + ";termo=" + batch.term + ";lider=n" + batch.leader
                        + ";hb=" + batch.hb + ";ultimoSeq=" + batch.lastSeq
                        + ";n=" + batch.records.size());

                boolean needSync = false;
                List<SensorRecord> mine = file.all(n);
                for (SensorRecord r : batch.records) {
                    long mySeq = lastSeq();
                    if (r.seq <= mySeq) {
                        if (mine.get((int) r.seq - 1).term != r.term) {
                            needSync = true;               // mesmo seq, outro termo: divergência
                            break;
                        }                                  // mesmo termo: duplicado, ignorado
                    } else if (r.seq == mySeq + 1 && (last == null || last.term == r.term)) {
                        file.append(r);                    // continuação normal
                        mine.add(r);
                        last = r;
                    } else {
                        needSync = true;                   // lacuna ou mudança de termo
                        break;
                    }
                }
                if (!needSync) {
                    // fim do lote: compara o último registo com ultimoSeq/ultimoTermo
                    needSync = lastSeq() != batch.lastSeq || lastTerm() != batch.lastTerm;
                }
                if (needSync) synchronize(batch);
            }
        } catch (Exception e) {
            event("ERRO;p" + k + ";lote: " + e.getMessage());
        }
    }

    // Recupera registos em falta e/ou resolve divergências, a partir do registo do líder.
    // Chamar com o lock do nó.
    private void synchronize(Batch batch) throws IOException {
        List<SensorRecord> mine = file.all(n);
        int size = mine.size();
        int blockStart = size == 0 ? 0 : startOfBlock(mine, size - 1);
        List<SensorRecord> got;
        while (true) {
            long from = size == 0 ? 1 : mine.get(blockStart).seq;
            got = fetch(batch, from);
            boolean matches = size == 0 || (!got.isEmpty() && got.get(0).seq == from
                    && got.get(0).term == mine.get(blockStart).term);
            if (matches || blockStart == 0) break;
            blockStart = startOfBlock(mine, blockStart - 1);   // recua um bloco de termo
        }

        // primeiro registo meu que difere do do líder
        int idx = blockStart;
        int g = 0;
        while (idx < size && g < got.size()
                && mine.get(idx).seq == got.get(g).seq
                && mine.get(idx).term == got.get(g).term) {
            idx++;
            g++;
        }

        int discarded = size - idx;
        if (discarded > 0) {
            long fromSeq = mine.get(idx).seq;
            TreeSet<Long> terms = new TreeSet<>();
            for (int j = idx; j < size; j++) terms.add(mine.get(j).term);
            StringBuilder dump = new StringBuilder();
            for (int j = idx; j < size; j++) {
                dump.append(mine.get(j).toLine()).append(System.lineSeparator());
            }
            Files.writeString(Path.of("truncados-p" + k + "-n" + i + ".txt"), dump.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            file.replaceAll(new ArrayList<>(mine.subList(0, idx)));
            last = idx == 0 ? null : mine.get(idx - 1);
            StringBuilder list = new StringBuilder();
            for (long t : terms) {
                if (list.length() > 0) list.append(',');
                list.append(t);
            }
            event("TRUNCADO;p" + k + ";de=" + fromSeq + ";descartados=" + discarded
                    + ";termos=" + list);
        }

        long firstNew = -1;
        long lastNew = -1;
        for (int j = g; j < got.size(); j++) {
            SensorRecord r = got.get(j);
            if (r.seq != lastSeq() + 1) break;
            file.append(r);
            last = r;
            if (firstNew < 0) firstNew = r.seq;
            lastNew = r.seq;
        }
        if (firstNew >= 0 && discarded == 0) {
            event("RECUPERACAO;p" + k + ";de=" + firstNew + ";ate=" + lastNew);
        }
    }

    // índice do primeiro registo do bloco de termo a que pertence list[index]
    private static int startOfBlock(List<SensorRecord> list, int index) {
        while (index > 0 && list.get(index - 1).term == list.get(index).term) index--;
        return index;
    }

    // pede ao líder, por TCP, os registos from..batch.lastSeq (exige a linha FIM)
    private List<SensorRecord> fetch(Batch batch, long from) throws IOException {
        List<SensorRecord> result = new ArrayList<>();
        if (from > batch.lastSeq) return result;
        if (Wire.isolated(k, i)) throw new IOException("no isolado");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(batch.address, batch.port), Wire.T_TCP_MS);
            socket.setSoTimeout(Wire.T_TCP_MS);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println("PEDIDO " + from + " " + batch.lastSeq);
            String line;
            boolean fim = false;
            while ((line = in.readLine()) != null) {
                if (line.equals("FIM")) { fim = true; break; }
                if (line.startsWith("ERRO ")) throw new IOException(line);
                result.add(SensorRecord.fromLine(line, n));
            }
            if (!fim) throw new IOException("resposta sem FIM");
        }
        return result;
    }
}