package tpf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Armazena registos no líder e atribui LSNs sem concorrência.
 *
 * <p>O método {@link #registar(String, String, String)} só devolve depois de
 * forçar a linha para o armazenamento. Assim, o LSN confirmado ao cliente já
 * corresponde a uma escrita durável.</p>
 */
public final class RegistoStore {
    private final Path registosPath;
    private final Path eventosPath;
    private long nextLsn;

    public RegistoStore(Path registosPath, Path eventosPath) {
        this.registosPath = Objects.requireNonNull(registosPath, "registosPath");
        this.eventosPath = Objects.requireNonNull(eventosPath, "eventosPath");
        this.nextLsn = readNextLsn();
    }

    public RegistoStore() {
        this(Path.of("registos.log"), Path.of("eventos-lider.txt"));
    }

    /**
     * Atribui o próximo LSN, persiste o registo e só depois o devolve.
     */
    public synchronized Registo registar(String autor, String canal, String texto) {
        Registo registo = new Registo(nextLsn++, Instant.now(), autor, canal, texto);
        String line = encode(registo) + System.lineSeparator();
        try {
            appendAndForce(registosPath, line);
            appendAndForce(eventosPath, registo.instante() + ";REGISTO;lsn="
                    + registo.lsn() + ",autor=" + registo.autor()
                    + ",canal=" + registo.canal());
            return registo;
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível persistir o registo", exception);
        }
    }

    /**
     * Lê todos os registos persistidos, útil para reconstruir o estado no arranque.
     */
    public synchronized List<Registo> lerTodos() {
        if (!Files.exists(registosPath)) {
            return List.of();
        }

        try {
            List<Registo> result = new ArrayList<>();
            for (String line : Files.readAllLines(registosPath, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    result.add(decode(line));
                }
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler " + registosPath, exception);
        }
    }

    private long readNextLsn() {
        long highest = -1;
        for (Registo registo : lerTodos()) {
            if (registo.lsn() <= highest) {
                throw new IllegalStateException("LSNs repetidos ou fora de ordem em "
                        + registosPath);
            }
            highest = registo.lsn();
        }
        return highest + 1;
    }

    private static String encode(Registo registo) {
        String encodedText = Base64.getEncoder().encodeToString(
                registo.texto().getBytes(StandardCharsets.UTF_8));
        return registo.lsn() + ";" + registo.instante() + ";" + registo.autor()
                + ";" + registo.canal() + ";" + encodedText;
    }

    private static Registo decode(String line) {
        String[] fields = line.split(";", 5);
        if (fields.length != 5) {
            throw new IllegalStateException("Linha inválida em registos.log: " + line);
        }
        try {
            String text = new String(Base64.getDecoder().decode(fields[4]), StandardCharsets.UTF_8);
            return new Registo(Long.parseLong(fields[0]), Instant.parse(fields[1]),
                    fields[2], fields[3], text);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Linha inválida em registos.log: " + line, exception);
        }
    }

    private static void appendAndForce(Path path, String content) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }
}
