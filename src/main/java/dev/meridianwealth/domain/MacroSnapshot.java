package dev.meridianwealth.domain;

import java.util.List;
import java.util.Map;

/**
 * Radar macro e geopolítico.
 *
 * <p>Cada indicador é opcional ({@code null} quando nenhum provedor respondeu): o produto nunca
 * preenche lacunas com valores inventados. {@code riskScore} em [0,1] combina apenas os componentes
 * disponíveis (VIX e risco geopolítico); sem nenhum deles vale 0 e {@code available} é falso, ou
 * seja, o motor simplesmente não aplica ajuste macro. {@code sectorImpact} (0 a 1) indica quais
 * setores as manchetes pressionam.
 */
public record MacroSnapshot(Double vix, Double dollarIndex, Double yield10y, Double geoRisk,
                            String headline, double riskScore, boolean available, String source,
                            List<NewsItem> news, Map<String, Double> sectorImpact, List<CountryRisk> countries, Map<String, Double> changes) {

    public MacroSnapshot {
        news = news == null ? List.of() : List.copyOf(news);
        sectorImpact = sectorImpact == null ? Map.of() : Map.copyOf(sectorImpact);
        countries = countries == null ? List.of() : List.copyOf(countries);
        changes = changes == null ? Map.of() : Map.copyOf(changes);
    }

    public static MacroSnapshot of(Double vix, Double usd, Double y10, Double geo, String headline,
                                   String source, List<NewsItem> news, Map<String, Double> sectors) {
        return of(vix, usd, y10, geo, headline, source, news, sectors, List.of(), Map.of());
    }

    /** {@code changes}: variação do dia (fração) por indicador (vix, dxy), só quando a fonte permite; ausente é "não informado". */
    public static MacroSnapshot of(Double vix, Double usd, Double y10, Double geo, String headline,
                                   String source, List<NewsItem> news, Map<String, Double> sectors, List<CountryRisk> countries, Map<String, Double> changes) {
        double sum = 0;
        int parts = 0;
        if (vix != null) {
            sum += Indicators.clamp((vix - 12) / 28.0, 0, 1);
            parts++;
        }
        if (geo != null) {
            sum += Indicators.clamp(geo, 0, 1);
            parts++;
        }
        boolean any = vix != null || usd != null || y10 != null || geo != null;
        return new MacroSnapshot(vix, usd, y10, geo, headline, parts == 0 ? 0 : sum / parts, any, source,
                news, sectors, countries, changes);
    }

    /** Nenhum indicador macro disponível. */
    public static MacroSnapshot unavailable() {
        return of(null, null, null, null, "Sem dado macro em tempo real", "SEM DADOS", List.of(), Map.of());
    }

    /** Risco ajustado ao setor: manchetes do setor elevam o risco; ouro/metais atuam como refúgio. */
    public double riskFor(String sector) {
        if ("refugio".equals(sector)) return Indicators.clamp(riskScore * 0.3, 0, 1);
        return Indicators.clamp(riskScore + 0.25 * sectorImpact.getOrDefault(sector, 0.0), 0, 1);
    }
}
