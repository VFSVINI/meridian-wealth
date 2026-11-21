package dev.meridianwealth.application;

import dev.meridianwealth.domain.Freshness;

/**
 * Procedência de uma cotação: fonte, horário da cotação na origem, selo de atraso e quando o
 * sistema tentará atualizá-la de novo (0 quando não há atualização agendada).
 */
public record QuoteMeta(String source, long quoteTime, Freshness freshness, long nextUpdateAt) {}
