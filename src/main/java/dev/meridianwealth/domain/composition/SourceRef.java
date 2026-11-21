package dev.meridianwealth.domain.composition;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Documento ou página pública de onde um dado foi tirado. {@code referenceDate} é a data-base dos dados como a
 * fonte a informa (texto livre: "2026-09-30", "2026-10"; nulo quando a fonte não informa) e {@code consultedOn}
 * o dia em que o sistema a leu. {@code evidenceSha256} é o hash do texto extraído, para detectar mudança.
 */
public record SourceRef(String name, String url, SourceKind kind, String referenceDate, LocalDate consultedOn,
                        String evidenceSha256) {
    public SourceRef {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(consultedOn, "consultedOn");
        URI uri = URI.create(Objects.requireNonNull(url, "url"));
        if (!"https".equals(uri.getScheme())) throw new IllegalArgumentException("A fonte deve ter URL https: " + url);
    }
}
