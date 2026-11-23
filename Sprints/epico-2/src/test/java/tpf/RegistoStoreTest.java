package tpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistoStoreTest {
    @TempDir
    Path directory;

    @Test
    void atribuiLsnsERecuperaNumeracaoDepoisDeReiniciar() {
        Path log = directory.resolve("registos.log");
        Path events = directory.resolve("eventos-lider.txt");
        RegistoStore first = new RegistoStore(log, events);

        Registo one = first.registar("ana", "arquivo", "texto; com separador");
        Registo two = first.registar("rui", "arquivo", "segundo");
        RegistoStore restarted = new RegistoStore(log, events);
        Registo three = restarted.registar("lia", "arquivo", "terceiro");

        assertEquals(0, one.lsn());
        assertEquals(1, two.lsn());
        assertEquals(2, three.lsn());
        assertEquals(3, restarted.lerTodos().size());
        assertEquals("texto; com separador", restarted.lerTodos().get(0).texto());
    }

    @Test
    void gravaTextoCodificadoEEventos() throws Exception {
        Path log = directory.resolve("registos.log");
        Path events = directory.resolve("eventos-lider.txt");
        new RegistoStore(log, events).registar("ana", "arquivo", "segredo");

        String logContent = Files.readString(log, StandardCharsets.UTF_8);
        String eventContent = Files.readString(events, StandardCharsets.UTF_8);
        assertTrue(logContent.contains("c2VncmVkbw=="));
        assertTrue(eventContent.contains(";REGISTO;"));
    }
}
