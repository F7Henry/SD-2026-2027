package rep03;

import java.time.LocalDateTime;

public class SensorRecord {
    public long seq;
    public String sensorId;
    public double temperature;
    public LocalDateTime timestamp;
    public long lamport;
    public VectorClock vector;

    public SensorRecord(long seq, String sensorId, double temperature,
                        LocalDateTime timestamp, long lamport,
                        VectorClock vector) {
        this.seq = seq;
        this.sensorId = sensorId;
        this.temperature = temperature;
        this.timestamp = timestamp;
        this.lamport = lamport;
        this.vector = vector.copy();
    }

    public SensorRecord(long seq, String sensorId, double temperature,
                        LocalDateTime timestamp) {
        this(seq, sensorId, temperature, timestamp, 0, new VectorClock(3));
    }

    public String toLine() {
        return seq + ";" + sensorId + ";" + temperature + ";" + timestamp
                + ";" + lamport + ";" + vector;
    }

    public static SensorRecord fromLine(String line, int n) {
        String[] parts = line.trim().split(";", 6);
        if (parts.length != 6) throw new IllegalArgumentException("Registo mal formado");
        return new SensorRecord(Long.parseLong(parts[0]), parts[1],
                Double.parseDouble(parts[2]), LocalDateTime.parse(parts[3]),
                Long.parseLong(parts[4]), VectorClock.parse(parts[5], n));
    }

    public static SensorRecord fromLine(String line) {
        return fromLine(line, 3);
    }

}
