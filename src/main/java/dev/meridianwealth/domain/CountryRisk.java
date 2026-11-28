package dev.meridianwealth.domain;

import java.util.List;

/**
 * Risco de um país segundo as manchetes recentes que o citam (item #51): {@code risk} em [0,1] é a soma do risco das manchetes dividida por duas manchetes fortes,
 * limitada a 1; {@code headlines} diz em quantas manchetes ele se apoia e {@code top} é a de maior risco entre elas. Não é um índice oficial. País que nenhuma manchete cita não aparece aqui.
 */
public record CountryRisk(String iso3, String name, String region, double risk, int headlines, List<String> sectors, NewsItem top) {
    public CountryRisk {
        sectors = sectors == null ? List.of() : List.copyOf(sectors);
    }
}
