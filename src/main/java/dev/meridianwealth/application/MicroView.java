package dev.meridianwealth.application;

import dev.meridianwealth.domain.Fundamentals;

public record MicroView(String symbol, String sector, String benchmark, double relStrength, double relVolume,
                        Fundamentals fundamentals) {}
