package rep03;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PartitionCheck {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Uso: PartitionCheck <N> <pasta>");
            return;
        }
        int n = Integer.parseInt(args[0]);
        Path folder = Path.of(args[1]);
        Partitioner partitioner = new Partitioner(n);
        long total = 0;
        for (int p = 0; p < n; p++) {
            Path leader = folder.resolve("lider-p" + p + ".txt");
            List<String> lines = Files.exists(leader)
                    ? Files.readAllLines(leader) : List.of();
            long expected = 1;
            for (String line : lines) {
                SensorRecord record = SensorRecord.fromLine(line);
                if (record.seq != expected++) System.out.println(
                        "SEQUENCIA INVALIDA p" + p + ": " + record.seq);
                if (partitioner.of(record.sensorId) != p) System.out.println(
                        "PARTICAO INVALIDA: " + record.sensorId);
            }
            total += lines.size();
            System.out.println("p" + p + ": " + lines.size() + " registos");
        }
        System.out.println("Total de registos: " + total);
    }
}
