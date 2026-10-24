package rep03;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class RecordFile {
    private final Path path;

    public RecordFile(String filename) { path = Path.of(filename); }

    public synchronized void append(SensorRecord record) throws IOException {
        Files.writeString(path, record.toLine() + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized long lastSeq() throws IOException {
        if (!Files.exists(path)) return 0;
        List<String> lines = Files.readAllLines(path);
        return lines.isEmpty() ? 0 : SensorRecord.fromLine(
                lines.get(lines.size() - 1)).seq;
    }

    public synchronized List<SensorRecord> range(long from, long to)
            throws IOException {
        List<SensorRecord> result = new ArrayList<>();
        if (!Files.exists(path)) return result;
        for (String line : Files.readAllLines(path)) {
            SensorRecord record = SensorRecord.fromLine(line);
            if (record.seq >= from && record.seq <= to) result.add(record);
        }
        return result;
    }
}

