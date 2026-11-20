package tpf;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Cifra e decifra o conteúdo padded de um chunk.
 */
public final class ChunkCrypto {
    public static final int KEY_VERSION = 1;
    private static final byte[] INFO = "tpf-chunk-v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD_PREFIX = "tpf-v1".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ChunkCrypto() {
    }

    public static SealedData seal(byte[] masterKey, byte[] padded) {
        requireMasterKey(masterKey);
        Objects.requireNonNull(padded, "padded");

        byte[] salt = randomBytes(SALT_BYTES);
        byte[] nonce = randomBytes(NONCE_BYTES);
        byte[] aad = aad(salt, nonce, KEY_VERSION);
        try {
            byte[] dek = hkdf(masterKey, salt, INFO, 32);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(padded);
            return new SealedData(KEY_VERSION, salt, nonce, ciphertext, sha256Hex(ciphertext));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível cifrar o chunk", exception);
        }
    }

    public static byte[] open(byte[] masterKey, SealedData sealed) {
        requireMasterKey(masterKey);
        Objects.requireNonNull(sealed, "sealed");
        if (sealed.keyVersion() != KEY_VERSION) {
            throw new IllegalArgumentException("Versão de chave não suportada: "
                    + sealed.keyVersion());
        }
        String actualId = sha256Hex(sealed.ciphertext());
        if (!MessageDigest.isEqual(actualId.getBytes(StandardCharsets.US_ASCII),
                sealed.chunkId().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Hash do ciphertext não corresponde ao chunkId");
        }

        try {
            byte[] dek = hkdf(masterKey, sealed.salt(), INFO, 32);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"),
                    new GCMParameterSpec(TAG_BITS, sealed.nonce()));
            cipher.updateAAD(aad(sealed.salt(), sealed.nonce(), sealed.keyVersion()));
            return cipher.doFinal(sealed.ciphertext());
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Autenticação GCM falhou", exception);
        }
    }

    static byte[] aad(byte[] salt, byte[] nonce, int keyVersion) {
        return ByteBuffer.allocate(AAD_PREFIX.length + Integer.BYTES + salt.length + nonce.length)
                .put(AAD_PREFIX)
                .putInt(keyVersion)
                .put(salt)
                .put(nonce)
                .array();
    }

    static String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private static byte[] hkdf(byte[] masterKey, byte[] salt, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] pseudorandomKey = mac.doFinal(masterKey);

            mac.init(new SecretKeySpec(pseudorandomKey, "HmacSHA256"));
            mac.update(info);
            mac.update((byte) 0x01);
            return Arrays.copyOf(mac.doFinal(), length);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HKDF-SHA256 indisponível", exception);
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        RANDOM.nextBytes(result);
        return result;
    }

    private static void requireMasterKey(byte[] masterKey) {
        if (masterKey == null || masterKey.length != 32) {
            throw new IllegalArgumentException("A master key tem de ter 32 bytes");
        }
    }

    /**
     * Metadados e ciphertext de um chunk selado.
     */
    public record SealedData(int keyVersion, byte[] salt, byte[] nonce,
                             byte[] ciphertext, String chunkId) {
        public SealedData {
            if (keyVersion < 0 || salt == null || salt.length != SALT_BYTES
                    || nonce == null || nonce.length != NONCE_BYTES
                    || ciphertext == null || chunkId == null || chunkId.isBlank()) {
                throw new IllegalArgumentException("Metadados de chunk inválidos");
            }
            salt = salt.clone();
            nonce = nonce.clone();
            ciphertext = ciphertext.clone();
        }

        @Override
        public byte[] salt() {
            return salt.clone();
        }

        @Override
        public byte[] nonce() {
            return nonce.clone();
        }

        @Override
        public byte[] ciphertext() {
            return ciphertext.clone();
        }
    }
}
