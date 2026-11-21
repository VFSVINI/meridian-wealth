package dev.meridianwealth.application.port;

import dev.meridianwealth.application.ProviderStatus;

/** Porta de saída: qualquer adaptador de provedor que saiba informar a própria saúde. */
public interface ProviderStatusSource {
    ProviderStatus status();
}
