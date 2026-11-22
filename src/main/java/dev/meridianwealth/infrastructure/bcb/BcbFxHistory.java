package dev.meridianwealth.infrastructure.bcb;

import dev.meridianwealth.application.port.FxHistoryPort;
import java.time.LocalDate;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

/**
 * PTAX (venda) por data, do serviço Olinda do Banco Central. Pega a cotação mais recente da janela de 7 dias que termina na data pedida
 * (cobre fins de semana e feriados) e recusa a que for de data posterior à pedida. Qualquer falha vira "sem dado": nada é estimado.
 */
@Component
public class BcbFxHistory implements FxHistoryPort {
    private static final Logger LOG = Logger.getLogger(BcbFxHistory.class.getName());
    private static final int LOOKBACK_DAYS = 7;
    private final BcbClient client;

    public BcbFxHistory(BcbClient client) { this.client = client; }

    @Override
    public Optional<PtaxQuote> ptaxSellOnOrBefore(LocalDate date) {
        try {
            BcbClient.Ptax p = client.ptaxOnOrBefore(date, LOOKBACK_DAYS);
            LocalDate reference = p.time().toLocalDate();
            if (reference.isAfter(date) || p.sell() == null || p.sell().signum() <= 0) return Optional.empty();
            return Optional.of(new PtaxQuote(p.sell(), reference));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "PTAX de {0} indisponível: {1}", new Object[] {date, e.getMessage()});
            return Optional.empty();
        }
    }
}
