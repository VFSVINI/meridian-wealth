package dev.chronosquant.application;

public record SignalRecord(String symbol, long ts, int score, String signal, String phase, String regime,
                           int confidence, String risk, double price) {}
