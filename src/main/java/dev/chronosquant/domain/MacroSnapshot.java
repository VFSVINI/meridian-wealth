package dev.chronosquant.domain;

import java.util.List;
import java.util.Map;

/**
 * Radar macro/geopolítico. {@code riskScore} em [0,1] combina VIX e risco geopolítico;
 * {@code sectorImpact} (0 a 1) indica quais setores as manchetes pressionam.
 */
public record MacroSnapshot(double vix, double dollarIndex, double yield10y, double geoRisk,
                            String headline, double riskScore, String source,
                            List<NewsItem> news, Map<String, Double> sectorImpact) {

    public static MacroSnapshot of(double vix, double usd, double y10, double geo, String headline,
                                   String source, List<NewsItem> news, Map<String, Double> sectors) {
        double r = 0.5 * Indicators.clamp((vix - 12) / 28.0, 0, 1) + 0.5 * Indicators.clamp(geo, 0, 1);
        return new MacroSnapshot(vix, usd, y10, geo, headline, r, source, news, sectors);
    }

    /** Risco ajustado ao setor: manchetes do setor elevam o risco; ouro/metais atuam como refúgio. */
    public double riskFor(String sector) {
        if ("refugio".equals(sector)) return Indicators.clamp(riskScore * 0.3, 0, 1);
        return Indicators.clamp(riskScore + 0.25 * sectorImpact.getOrDefault(sector, 0.0), 0, 1);
    }
}
