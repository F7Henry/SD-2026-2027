package tpf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Chunk aberto que acumula registos antes da selagem e cifra.
 *
 * <p>Formato binário: magic {@code TPF1}, número de registos e, para cada
 * registo, LSN, instante em milissegundos UTC e os campos de texto como
 * strings UTF-8 precedidas pelo seu tamanho. O resultado é completado com
 * zeros até um múltiplo de {@link Wire#PAD_BYTES}.</p>
 */
public final class Chunk {
    private static final byte[] MAGIC = "TPF1".getBytes(StandardCharsets.US_ASCII);

    private final List<Registo> registos = new ArrayList<>();
    private Instant primeiroInstante;

    /**
     * Adiciona um registo e indica se o limite de tamanho foi atingido.
     * O chamador deve selar imediatamente se este método devolver true.
     */
    public synchronized boolean adicionar(Registo registo) {
        Objects.requireNonNull(registo, "registo");
        if (registos.isEmpty()) {
            primeiroInstante = Instant.now();
        }
        registos.add(registo);
        return serializarSemPadding().length >= Wire.CHUNK_MAX_BYTES;
    }

    /**
     * Verifica se o chunk ficou aberto durante o tempo máximo configurado.
     */
    public synchronized boolean deveSelarPorTempo(Instant agora) {
        Objects.requireNonNull(agora, "agora");
        return primeiroInstante != null
                && Duration.between(primeiroInstante, agora).toMillis() >= Wire.SELAGEM_MS;
    }

    public synchronized boolean vazio() {
        return registos.isEmpty();
    }

    public synchronized List<Registo> registos() {
        return List.copyOf(registos);
    }

    /**
     * Serializa o chunk e adiciona zeros até ao próximo múltiplo de PAD_BYTES.
     */
    public synchronized byte[] serializar() {
        if (registos.isEmpty()) {
            throw new IllegalStateException("Não é possível selar um chunk vazio");
        }

        byte[] raw = serializarSemPadding();
        int paddedLength = ((raw.length + Wire.PAD_BYTES - 1) / Wire.PAD_BYTES)
                * Wire.PAD_BYTES;
        byte[] padded = new byte[paddedLength];
        System.arraycopy(raw, 0, padded, 0, raw.length);
        return padded;
    }

    /**
     * Reconstrói os registos e ignora os bytes de padding restantes.
     */
    public static List<Registo> desserializar(byte[] padded) {
        Objects.requireNonNull(padded, "padded");
        if (padded.length == 0 || padded.length % Wire.PAD_BYTES != 0) {
            throw new IllegalArgumentException("O chunk não tem tamanho múltiplo de PAD_BYTES");
        }

        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(padded));
            byte[] magic = input.readNBytes(MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IllegalArgumentException("Magic inválida no chunk");
            }

            int count = input.readInt();
            if (count <= 0) {
                throw new IllegalArgumentException("Número de registos inválido: " + count);
            }

            List<Registo> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long lsn = input.readLong();
                Instant instante = Instant.ofEpochMilli(input.readLong());
                String autor = readString(input);
                String canal = readString(input);
                String texto = readString(input);
                result.add(new Registo(lsn, instante, autor, canal, texto));
            }
            return List.copyOf(result);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Chunk truncado", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Chunk inválido", exception);
        }
    }

    private synchronized byte[] serializarSemPadding() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(MAGIC);
            output.writeInt(registos.size());

            for (Registo registo : registos) {
                output.writeLong(registo.lsn());
                output.writeLong(registo.instante().toEpochMilli());
                writeString(output, registo.autor());
                writeString(output, registo.canal());
                writeString(output, registo.texto());
            }

            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível serializar o chunk", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available()) {
            throw new IllegalArgumentException("Tamanho de texto inválido: " + length);
        }
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }
}
