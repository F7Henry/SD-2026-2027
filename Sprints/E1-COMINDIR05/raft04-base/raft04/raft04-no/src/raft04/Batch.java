package raft04;

import java.util.ArrayList;
import java.util.List;

public final class Batch {
    public final long term;
    public final int leader;
    public final String address;
    public final int port;
    public final long hb;
    public final long lastSeq;
    public final long lastTerm;
    public final List<SensorRecord> records;

    public Batch(long term, int leader, String address, int port, long hb,
                 long lastSeq, long lastTerm, List<SensorRecord> records) {
        this.term = term;
        this.leader = leader;
        this.address = address;
        this.port = port;
        this.hb = hb;
        this.lastSeq = lastSeq;
        this.lastTerm = lastTerm;
        this.records = List.copyOf(records);
    }

    public String encode() {
        StringBuilder result = new StringBuilder();
        result.append("LOTE;").append(term).append(';').append(leader)
                .append(';').append(address).append(';').append(port)
                .append(';').append(hb).append(';').append(lastSeq)
                .append(';').append(lastTerm).append(';').append(records.size());
        for (SensorRecord record : records) {
            result.append('\n').append(record.toLine());
        }
        return result.toString();
    }

    public static Batch decode(String message, int n) {
        String[] lines = message.trim().split("\\R", -1);
        String[] header = lines[0].split(";", 9);
        if (header.length != 9 || !"LOTE".equals(header[0])) {
            throw new IllegalArgumentException("LOTE mal formado");
        }
        int count = Integer.parseInt(header[8]);
        if (count < 0 || lines.length - 1 != count) {
            throw new IllegalArgumentException("numero de registos invalido");
        }
        List<SensorRecord> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            records.add(SensorRecord.fromLine(lines[i + 1], n));
        }
        return new Batch(Long.parseLong(header[1]), Integer.parseInt(header[2]),
                header[3], Integer.parseInt(header[4]), Long.parseLong(header[5]),
                Long.parseLong(header[6]), Long.parseLong(header[7]), records);
    }
}