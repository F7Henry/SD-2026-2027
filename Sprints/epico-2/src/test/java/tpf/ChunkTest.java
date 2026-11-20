package tpf;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkTest {
    @Test
    void serializaComPaddingEDesserializa() {
        Registo original = new Registo(0, Instant.now(), "ana", "arquivo", "olá");
        Chunk chunk = new Chunk();
        chunk.adicionar(original);

        byte[] serialized = chunk.serializar();
        List<Registo> restored = Chunk.desserializar(serialized);

        assertEquals(0, serialized.length % Wire.PAD_BYTES);
        assertEquals(List.of(original), restored);
    }

    @Test
    void alteraDepoisDoLimiteDeTamanho() {
        String text = "x".repeat(Wire.CHUNK_MAX_BYTES);
        Chunk chunk = new Chunk();

        assertTrue(chunk.adicionar(new Registo(0, Instant.now(), "ana", "arquivo", text)));
    }
}
