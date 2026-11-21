package dev.meridianwealth.infrastructure.crypto;

/** Último preço negociado de um par numa bolsa, com o horário do evento na origem (epoch em ms). */
public record CryptoTick(String pair, double price, long timeMillis) {}
