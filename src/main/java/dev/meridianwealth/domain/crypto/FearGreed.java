package dev.meridianwealth.domain.crypto;

import java.time.Instant;
import java.util.Map;

/** Índice de medo e ganância do cripto (0 = medo extremo, 100 = ganância extrema), como publicado pela fonte; a plataforma só traduz o rótulo. */
public record FearGreed(int value, String classification, String label, Instant updatedAt) {
    private static final Map<String, String> LABELS = Map.of("Extreme Fear", "Medo extremo", "Fear", "Medo", "Neutral", "Neutro", "Greed", "Ganância", "Extreme Greed", "Ganância extrema");

    public FearGreed {
        if (value < 0 || value > 100) throw new IllegalArgumentException("Índice fora de 0 a 100: " + value);
    }

    /** Monta o índice do texto da fonte; rótulo desconhecido volta como a própria classificação original. */
    public static FearGreed of(int value, String classification, long epochSeconds) {
        String c = classification == null ? "" : classification.trim();
        return new FearGreed(value, c, LABELS.getOrDefault(c, c.isEmpty() ? null : c), Instant.ofEpochSecond(epochSeconds));
    }
}
