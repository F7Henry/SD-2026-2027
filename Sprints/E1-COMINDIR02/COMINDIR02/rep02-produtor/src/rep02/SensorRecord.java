package rep02;

import java.time.LocalDateTime;

public class SensorRecord {
    public long seq;
    public String sensorId;
    public double temperature;
    public LocalDateTime timestamp;

    public SensorRecord(long seq, String sensorId, double temperature,
                        LocalDateTime timestamp) {
        this.seq = seq;
        this.sensorId = sensorId;
        this.temperature = temperature;
        this.timestamp = timestamp;
    }

    public String toLine() {
        return seq + ";" + sensorId + ";" + temperature + ";" + timestamp;
    }
}
