package tpf;

import java.time.Instant;
import java.util.Objects;

/**
 * Um registo lógico aceite pelo líder.
 *
 * @param lsn número sequencial local do registo
 * @param instante instante em que o líder atribuiu o LSN
 * @param autor autor da mensagem
 * @param canal canal de origem
 * @param texto conteúdo do registo
 */
public record Registo(long lsn, Instant instante, String autor, String canal, String texto) {
    public Registo {
        if (lsn < 0) {
            throw new IllegalArgumentException("O LSN não pode ser negativo");
        }
        Objects.requireNonNull(instante, "instante");
        requireField("autor", autor);
        requireField("canal", canal);
        Objects.requireNonNull(texto, "texto");
    }

    private static void requireField(String name, String value) {
        if (value == null || value.isBlank() || value.indexOf(';') >= 0) {
            throw new IllegalArgumentException(
                    name + " não pode estar vazio nem conter ';'");
        }
    }
}
