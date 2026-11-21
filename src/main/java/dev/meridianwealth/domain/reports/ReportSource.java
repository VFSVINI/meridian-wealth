package dev.meridianwealth.domain.reports;

import dev.meridianwealth.domain.composition.SourceKind;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/** Fonte dos relatórios: conjunto de dados, tipo, data da consulta e o SHA-256 de cada arquivo lido. */
public record ReportSource(String name, String url, SourceKind kind, LocalDate consultedOn, Map<String, String> fileSha256) {
    public ReportSource {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(consultedOn, "consultedOn");
        if (url == null || !url.startsWith("https://")) throw new IllegalArgumentException("A fonte precisa de url https: " + name);
        fileSha256 = fileSha256 == null ? Map.of() : Map.copyOf(fileSha256);
    }
}
