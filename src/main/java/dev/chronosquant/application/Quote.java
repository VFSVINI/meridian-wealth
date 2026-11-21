package dev.chronosquant.application;

public record Quote(String symbol, double price, long tsMillis, String source) {}
