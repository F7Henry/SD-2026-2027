package raft04;

import java.time.LocalDateTime;

public class SensorRecord {
    public long seq;
    public long term;
    public String sensorId;
    public double temperature;
    public LocalDateTime timestamp;
    public long lamport;
    public VectorClock vector;

    public SensorRecord(long seq, long term, String sensorId, double temperature,
                        LocalDateTime timestamp, long lamport,
                        VectorClock vector) {
        this.seq = seq;
        this.term = term;
        this.sensorId = sensorId;
        this.temperature = temperature;
        this.timestamp = timestamp;
        this.lamport = lamport;
        this.vector = vector.copy();
    }

    public String toLine() {
        return seq + ";" + term + ";" + sensorId + ";" + temperature + ";"
                + timestamp + ";" + lamport + ";" + vector;
    }

    public static SensorRecord fromLine(String line, int n) {
        String[] parts = line.trim().split(";", 7);
        if (parts.length != 7) throw new IllegalArgumentException("Registo mal formado");
        return new SensorRecord(Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                parts[2], Double.parseDouble(parts[3]), LocalDateTime.parse(parts[4]),
                Long.parseLong(parts[5]), VectorClock.parse(parts[6], n));
    }

    public static SensorRecord fromLine(String line) {
        return fromLine(line, 3);
    }
}