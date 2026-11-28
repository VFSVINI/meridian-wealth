package dev.meridianwealth.web;

import dev.meridianwealth.application.CryptoInsightsService;
import dev.meridianwealth.domain.crypto.HourlyVolume;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Relógio de 24 horas (volume do Bitcoin por hora do dia, no horário de Brasília) e medo e ganância do cripto. Dado real de fonte pública; o que falta vem em {@code unavailable}. */
@RestController
@RequestMapping("/api/crypto")
public class CryptoController {
    public record HourView(int hour, Double averageUsd, Double relative, int samples) {}

    public record HourlyView(String symbol, String zone, String source, int bars, String from, String to, int peakHour, List<HourView> hours) {}

    public record FearGreedView(int value, String label, String classification, String updatedAt, String source) {}

    public record InsightsView(String asOf, String notice, HourlyView hourly, FearGreedView fearGreed, List<String> unavailable) {}

    private final CryptoInsightsService service;

    public CryptoController(CryptoInsightsService service) { this.service = service; }

    @GetMapping("/insights")
    public InsightsView insights() {
        CryptoInsightsService.View v = service.view();
        HourlyVolume.Profile p = v.hourly();
        HourlyView hourly = p == null ? null : new HourlyView(CryptoInsightsService.SYMBOL, v.zone(), v.hourlySource(), p.bars(), java.time.Instant.ofEpochMilli(p.fromMillis()).toString(),
                java.time.Instant.ofEpochMilli(p.toMillis()).toString(), p.peakHour(), p.hours().stream().map(h -> new HourView(h.hour(), h.averageUsd() == null ? null : Math.round(h.averageUsd()) * 1.0,
                        h.relative() == null ? null : Math.round(h.relative() * 1000) / 1000.0, h.samples())).toList());
        FearGreedView fg = v.fearGreed() == null ? null : new FearGreedView(v.fearGreed().value(), v.fearGreed().label(), v.fearGreed().classification(), v.fearGreed().updatedAt().toString(), "alternative.me");
        return new InsightsView(v.asOf().toString(), "Volume do Bitcoin por hora (média das últimas barras completas, em dólares) e índice de medo e ganância, ambos de fontes públicas. Ferramenta analítica: não é recomendação de investimento.",
                hourly, fg, v.unavailable());
    }
}
