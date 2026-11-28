package dev.meridianwealth.domain;

import java.time.Instant;
import java.util.List;

/**
 * Manchete com risco estimado (0 a 1), sentimento (-1 a 1) e setores afetados. {@code link} é o endereço da matéria na fonte (só http ou https) e
 * {@code publishedAt} o horário que a fonte informou; os dois são nulos quando a fonte não traz. {@code countries} são os códigos (ADM0_A3) que o título cita.
 * Só o título, a fonte, o horário e o link ficam aqui: o texto da matéria nunca é copiado.
 */
public record NewsItem(String title, String source, double risk, double sentiment, List<String> sectors, String link, Instant publishedAt, List<String> countries) {
    public NewsItem {
        sectors = sectors == null ? List.of() : List.copyOf(sectors);
        countries = countries == null ? List.of() : List.copyOf(countries);
    }
}
