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

    public String toLine() {
        return seq + ";" + sensorId + ";" + temperature + ";" + timestamp
                + ";" + lamport + ";" + vector;
    }
}
