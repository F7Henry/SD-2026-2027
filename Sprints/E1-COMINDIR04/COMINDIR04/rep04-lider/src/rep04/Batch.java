package rep04;

import java.util.ArrayList;
import java.util.List;

public final class Batch {
    public final String address;
    public final int port;
    public final long inc;
    public final long hb;
    public final long lastSeq;
    public final List<SensorRecord> records;

    public Batch(String address, int port, long inc, long hb, long lastSeq,
                 List<SensorRecord> records) {
        this.address = address;
        this.port = port;
        this.inc = inc;
        this.hb = hb;
        this.lastSeq = lastSeq;
        this.records = List.copyOf(records);
    }

    public String encode() {
        StringBuilder result = new StringBuilder();
        result.append("LOTE;").append(address).append(';').append(port)
                .append(';').append(inc).append(';').append(hb).append(';')
                .append(lastSeq).append(';').append(records.size());
        for (SensorRecord record : records) {
            result.append('\n').append(record.toLine());
        }
        return result.toString();
    }

    public static Batch decode(String message, int n) {
        String[] lines = message.trim().split("\\R", -1);
        String[] header = lines[0].split(";", 7);
        if (header.length != 7 || !"LOTE".equals(header[0])) {
            throw new IllegalArgumentException("LOTE mal formado");
        }
        int count = Integer.parseInt(header[6]);
        if (count < 0 || lines.length - 1 != count) {
            throw new IllegalArgumentException("numero de registos invalido");
        }
        List<SensorRecord> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            records.add(SensorRecord.fromLine(lines[i + 1], n));
        }
        return new Batch(header[1], Integer.parseInt(header[2]),
                Long.parseLong(header[3]), Long.parseLong(header[4]),
                Long.parseLong(header[5]), records);
    }
}
