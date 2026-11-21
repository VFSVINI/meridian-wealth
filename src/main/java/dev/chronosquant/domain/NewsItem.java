package dev.chronosquant.domain;

import java.util.List;

/** Manchete com risco estimado (0 a 1), sentimento (-1 a 1) e setores afetados. */
public record NewsItem(String title, String source, double risk, double sentiment, List<String> sectors) {}
