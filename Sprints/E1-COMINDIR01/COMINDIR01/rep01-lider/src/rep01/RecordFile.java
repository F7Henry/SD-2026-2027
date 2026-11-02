
package rep01;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class RecordFile {

    private final Path path;

    public RecordFile(String filename) {
        this.path = Path.of(filename);
    }

    public synchronized void append(SensorRecord record)
            throws IOException {

        Files.writeString(
            path,
            record.toLine() + System.lineSeparator(),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
    }

    public synchronized long lastSeq() throws IOException {

        if (!Files.exists(path)) {
            return 0;
        }

        List<String> lines = Files.readAllLines(path);

        if (lines.isEmpty()) {
            return 0;
        }

        String lastLine = lines.get(lines.size() - 1);

        return SensorRecord.fromLine(lastLine).seq;
    }
}
