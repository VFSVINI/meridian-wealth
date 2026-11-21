package dev.chronosquant.application;

import dev.chronosquant.domain.Fundamentals;

public record MicroView(String symbol, String sector, String benchmark, double relStrength, double relVolume,
                        Fundamentals fundamentals) {}
