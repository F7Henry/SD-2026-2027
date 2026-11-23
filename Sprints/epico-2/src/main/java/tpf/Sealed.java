package tpf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

/**
 * Persiste e abre chunks cifrados em {@code <chunkId>.bin} e {@code <chunkId>.hdr}.
 */
public final class Sealed {
    private Sealed() {
    }

    public static ChunkCrypto.SealedData write(Path directory, byte[] masterKey, byte[] padded) {
        Objects.requireNonNull(directory, "directory");
        ChunkCrypto.SealedData sealed = ChunkCrypto.seal(masterKey, padded);
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(sealed.chunkId() + ".bin"), sealed.ciphertext());
            String header = sealed.keyVersion() + ";"
                    + Base64.getEncoder().encodeToString(sealed.salt()) + ";"
                    + Base64.getEncoder().encodeToString(sealed.nonce()) + System.lineSeparator();
            Files.writeString(directory.resolve(sealed.chunkId() + ".hdr"), header,
                    StandardCharsets.UTF_8);
            return sealed;
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível gravar o chunk selado", exception);
        }
    }

    public static byte[] open(Path directory, String chunkId, byte[] masterKey) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(chunkId, "chunkId");
        if (!chunkId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("chunkId inválido");
        }
        try {
            byte[] ciphertext = Files.readAllBytes(directory.resolve(chunkId + ".bin"));
            String[] fields = Files.readString(directory.resolve(chunkId + ".hdr"),
                    StandardCharsets.UTF_8).trim().split(";", -1);
            if (fields.length != 3) {
                throw new IllegalArgumentException("Header inválido");
            }
            ChunkCrypto.SealedData sealed = new ChunkCrypto.SealedData(
                    Integer.parseInt(fields[0]),
                    Base64.getDecoder().decode(fields[1]),
                    Base64.getDecoder().decode(fields[2]),
                    ciphertext,
                    chunkId);
            return ChunkCrypto.open(masterKey, sealed);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Não foi possível abrir o chunk " + chunkId,
                    exception);
        }
    }
}
