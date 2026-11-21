package dev.meridianwealth.domain;

import java.util.List;

/**
 * Diagnóstico completo de timing para um ativo. Score: -100 (realizar/sair) a +100 (comprar).
 * {@code beta} é o beta de 90 barras contra o benchmark do ativo (nulo sem benchmark ou dados) e
 * {@code periodsPerYear} a base de anualização (365 para cripto, 252 para os demais).
 */
public record Assessment(
        String symbol, double price, int score, Signal signal, Phase phase, Regime regime,
        int confidence, RiskClass risk,
        double zScore, double percentile, double rsi, double macdHist,
        double annVol, double maxDrawdown, double sharpe, double var95, double atr,
        double fairValue, double entryLow, double entryHigh, double invalidation,
        double target1, double target2,
        double upside, double downside, double riskReward, double expectedValue,
        double suggestedExposure, double relStrength, double relVolume, List<Factor> factors,
        Double beta, int periodsPerYear) {}
