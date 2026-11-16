package raft04;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class RecordFile {
    private final Path path;

    public RecordFile(String filename) {
        path = Path.of(filename);
    }

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

    public synchronized SensorRecord last(int n) throws IOException {
        if (!Files.exists(path)) return null;
        List<String> lines = Files.readAllLines(path);
        return lines.isEmpty() ? null : SensorRecord.fromLine(
                lines.get(lines.size() - 1), n);
    }

    public synchronized SensorRecord last() throws IOException {
        return last(3);
    }

    public synchronized List<SensorRecord> range(long from, long to)
            throws IOException {
        List<SensorRecord> result = new ArrayList<>();
        if (!Files.exists(path)) return result;
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            SensorRecord record = SensorRecord.fromLine(line);
            if (record.seq >= from && record.seq <= to) result.add(record);
        }
        return result;
    }

    // todos os registos do ficheiro, por ordem
    public synchronized List<SensorRecord> all(int n) throws IOException {
        List<SensorRecord> result = new ArrayList<>();
        if (!Files.exists(path)) return result;
        for (String line : Files.readAllLines(path)) {
            if (!line.isBlank()) result.add(SensorRecord.fromLine(line, n));
        }
        return result;
    }

    // reescreve o ficheiro de forma atómica (ficheiro temporário + ATOMIC_MOVE)
    public synchronized void replaceAll(List<SensorRecord> records) throws IOException {
        StringBuilder text = new StringBuilder();
        for (SensorRecord record : records) {
            text.append(record.toLine()).append(System.lineSeparator());
        }
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, text.toString());
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
}