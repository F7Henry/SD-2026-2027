package tpf;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Set;

/**
 * Parâmetros do protocolo de escrita local.
 *
 * <p>Os valores podem ser substituídos com propriedades da JVM, por exemplo:
 * {@code -Dtpf.chunkMaxBytes=32768}.</p>
 */
public final class Wire {
    private static final int MASTER_KEY_BYTES = 32;
    private static final String MASTER_KEY_PROPERTY = "tpf.masterKey";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final int CHUNK_MAX_BYTES = positiveInt("tpf.chunkMaxBytes", 65_536);
    public static final int PAD_BYTES = positiveInt("tpf.padBytes", 4_096);
    public static final long SELAGEM_MS = positiveLong("tpf.selagemMs", 5_000L);

    private Wire() {
    }

    /**
     * Obtém a chave mestra no caminho configurado ou em {@code master.key}.
     *
     * <p>Se o ficheiro ainda não existir, a criação é feita com {@code CREATE_NEW}
     * para evitar substituir uma chave existente por acidente. A escrita é
     * forçada para o disco antes de a chave ser devolvida.</p>
     *
     * @return uma cópia dos 32 bytes da chave mestra
     * @throws IllegalStateException se a chave não puder ser criada ou lida
     */
    public static byte[] loadOrCreateMasterKey() {
        String configuredPath = System.getProperty(MASTER_KEY_PROPERTY, "master.key");
        return loadOrCreateMasterKey(Path.of(configuredPath));
    }

    /**
     * Variante testável que permite indicar diretamente o caminho da chave.
     */
    public static byte[] loadOrCreateMasterKey(Path path) {
        try {
            if (Files.exists(path)) {
                byte[] key = Files.readAllBytes(path);
                if (key.length != MASTER_KEY_BYTES) {
                    throw new IllegalStateException(
                            "A master.key tem de ter exatamente 32 bytes: " + path);
                }
                restrictToOwner(path);
                return key;
            }

            byte[] key = new byte[MASTER_KEY_BYTES];
            RANDOM.nextBytes(key);

            // CREATE_NEW falha se outro processo criar a chave entretanto.
            try (FileChannel channel = FileChannel.open(path,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(key);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            restrictToOwner(path);
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível preparar a master.key: " + path,
                    exception);
        }
    }

    /**
     * Em sistemas POSIX aplica leitura apenas para o dono. Em sistemas sem
     * suporte POSIX, usa a API portátil como melhor aproximação disponível.
     */
    private static void restrictToOwner(Path path) throws IOException {
        try {
            Set<PosixFilePermission> ownerOnly = EnumSet.of(
                    PosixFilePermission.OWNER_READ);
            Files.setPosixFilePermissions(path, ownerOnly);
        } catch (UnsupportedOperationException exception) {
            // Windows não expõe permissões POSIX através desta API.
            path.toFile().setReadable(false, false);
            path.toFile().setReadable(true, true);
            path.toFile().setWritable(false, false);
            path.toFile().setExecutable(false, false);
        }
    }

    private static int positiveInt(String property, int defaultValue) {
        String value = System.getProperty(property);
        if (value == null) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(
                        "A propriedade " + property + " tem de ser positiva: " + value);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "A propriedade " + property + " não é um inteiro válido: " + value,
                    exception);
        }
    }

    private static long positiveLong(String property, long defaultValue) {
        String value = System.getProperty(property);
        if (value == null) {
            return defaultValue;
        }

        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(
                        "A propriedade " + property + " tem de ser positiva: " + value);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "A propriedade " + property + " não é um inteiro válido: " + value,
                    exception);
        }
    }
}
