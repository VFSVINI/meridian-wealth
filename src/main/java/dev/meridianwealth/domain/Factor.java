package dev.meridianwealth.domain;

/** Item de explicabilidade. {@code side}: COMPRA, VENDA ou ALERTA. */
public record Factor(String side, String text) {}
