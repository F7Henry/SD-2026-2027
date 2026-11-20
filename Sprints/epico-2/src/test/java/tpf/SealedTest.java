package tpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SealedTest {
    @TempDir
    Path directory;

    @Test
    void gravaEAbreBinarioSemTextoEmClaro() throws Exception {
        byte[] key = new byte[32];
        byte[] content = "mensagem privada".getBytes(StandardCharsets.UTF_8);

        ChunkCrypto.SealedData sealed = Sealed.write(directory, key, content);

        assertTrue(Files.exists(directory.resolve(sealed.chunkId() + ".bin")));
        assertTrue(Files.exists(directory.resolve(sealed.chunkId() + ".hdr")));
        assertArrayEquals(content, Sealed.open(directory, sealed.chunkId(), key));
        assertFalse(new String(sealed.ciphertext(), StandardCharsets.UTF_8)
                .contains("mensagem privada"));
    }
}
