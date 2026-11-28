package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.crypto.FearGreed;
import dev.meridianwealth.domain.crypto.HourlyVolume;
import java.util.List;
import java.util.Optional;

/** Porta de saída do cripto: barras horárias reais de uma bolsa e o índice de medo e ganância; vazio quando nenhuma fonte respondeu (nada é estimado). */
public interface CryptoInsightsPort {
    /** Barras de uma hora e a bolsa que as entregou. */
    record Hourly(String source, List<HourlyVolume.Bar> bars) {}

    Optional<Hourly> hourly(String symbol);

    Optional<FearGreed> fearGreed();
}
