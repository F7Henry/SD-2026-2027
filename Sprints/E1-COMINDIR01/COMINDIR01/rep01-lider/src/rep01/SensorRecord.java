
package rep01;

import java.time.LocalDateTime;

public class SensorRecord {
    public long seq;
    public String sensorId;
    public double temperature;
    public LocalDateTime timestamp;

    public SensorRecord(long seq, String sensorId,
                        double temperature, LocalDateTime timestamp) {
        this.seq = seq;
        this.sensorId = sensorId;
        this.temperature = temperature;
        this.timestamp = timestamp;
    }

    public String toLine() {
        return seq + ";" + sensorId + ";" + temperature + ";" + timestamp;
    }

    public static SensorRecord fromLine(String line) {
        String[] parts = line.split(";");

        return new SensorRecord(
            Long.parseLong(parts[0]),
            parts[1],
            Double.parseDouble(parts[2]),
            LocalDateTime.parse(parts[3])
        );
    }
}
