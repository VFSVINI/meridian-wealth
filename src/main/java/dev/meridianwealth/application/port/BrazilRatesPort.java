package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.BrazilRates;

/** Porta de saída: juros, inflação e câmbio oficial do Brasil, como o Banco Central publicou. */
public interface BrazilRatesPort {
    /** Último retrato conhecido; indicadores que ainda não chegaram vêm ausentes, nunca estimados. */
    BrazilRates current();
}
