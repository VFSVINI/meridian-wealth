package dev.meridianwealth.application;

public record Quote(String symbol, double price, long tsMillis, String source) {}
