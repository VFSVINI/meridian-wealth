package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.application.Quote;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.domain.Freshness;

/**
 * Porta plugável para um feed de cotações pago (tempo real de B3, Europa ou EUA conforme o plano).
 *
 * <p>Fica desligada por padrão. Quando ligada ({@code MW_PAID_FEED} e {@code MW_PAID_FEED_KEY}), entra
 * em primeiro lugar na cadeia de cotações. O selo de atraso é declarado pela configuração, porque
 * depende do plano contratado, e nunca é presumido como tempo real.
 */
public interface PaidQuoteFeed extends ProviderStatusSource {
    /** Rótulo exibido como fonte da cotação (ex.: "Twelve Data"). */
    String label();

    boolean enabled();

    /** Selo de atraso do plano contratado. */
    Freshness freshness();

    /** Cotação do ativo ou exceção; {@code symbols} traz os códigos por provedor. */
    Quote quote(String symbol, Symbols symbols);

    /** Se este feed cobre o ativo. */
    boolean covers(Symbols symbols);
}
