package dev.meridianwealth.infrastructure.provider;

import java.time.Instant;

/** Manchete de uma fonte: só o título, o nome da fonte, o link da matéria e o horário informado por ela (nulos quando a fonte não traz). Nunca o texto. */
public record Headline(String title, String source, String link, Instant publishedAt) {
    public Headline(String title, String source) { this(title, source, null, null); }
}
