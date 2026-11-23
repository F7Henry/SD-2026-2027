package tpf;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkCryptoTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void cifraEDecifraOConteudo() {
        byte[] key = randomBytes(32);
        byte[] content = "conteudo secreto".getBytes(StandardCharsets.UTF_8);

        ChunkCrypto.SealedData sealed = ChunkCrypto.seal(key, content);

        assertArrayEquals(content, ChunkCrypto.open(key, sealed));
    }

    @Test
    void rejeitaCiphertextAlterado() {
        byte[] key = randomBytes(32);
        ChunkCrypto.SealedData original = ChunkCrypto.seal(key, new byte[4096]);
        byte[] altered = original.ciphertext();
        altered[0] ^= 1;
        ChunkCrypto.SealedData tampered = new ChunkCrypto.SealedData(
                original.keyVersion(), original.salt(), original.nonce(), altered,
                original.chunkId());

        assertThrows(IllegalArgumentException.class, () -> ChunkCrypto.open(key, tampered));
    }

    @Test
    void rejeitaMetadadosOuChaveDiferentes() {
        byte[] key = randomBytes(32);
        ChunkCrypto.SealedData sealed = ChunkCrypto.seal(key, new byte[4096]);

        byte[] otherSalt = sealed.salt();
        otherSalt[0] ^= 1;
        ChunkCrypto.SealedData changedSalt = new ChunkCrypto.SealedData(
                sealed.keyVersion(), otherSalt, sealed.nonce(), sealed.ciphertext(),
                sealed.chunkId());

        byte[] otherNonce = sealed.nonce();
        otherNonce[0] ^= 1;
        ChunkCrypto.SealedData changedNonce = new ChunkCrypto.SealedData(
                sealed.keyVersion(), sealed.salt(), otherNonce, sealed.ciphertext(),
                sealed.chunkId());

        ChunkCrypto.SealedData changedVersion = new ChunkCrypto.SealedData(
                2, sealed.salt(), sealed.nonce(), sealed.ciphertext(), sealed.chunkId());

        assertThrows(IllegalArgumentException.class, () -> ChunkCrypto.open(key, changedSalt));
        assertThrows(IllegalArgumentException.class, () -> ChunkCrypto.open(key, changedNonce));
        assertThrows(IllegalArgumentException.class, () -> ChunkCrypto.open(key, changedVersion));
        assertThrows(IllegalArgumentException.class,
                () -> ChunkCrypto.open(randomBytes(32), sealed));
        assertThrows(IllegalArgumentException.class,
                () -> ChunkCrypto.open(new byte[31], sealed));
    }

    private static byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        RANDOM.nextBytes(result);
        return result;
    }
}
