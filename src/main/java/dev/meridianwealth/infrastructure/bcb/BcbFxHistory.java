package dev.meridianwealth.infrastructure.bcb;

import dev.meridianwealth.application.port.FxHistoryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * PTAX (venda) por data, do serviço Olinda do Banco Central. Pega a cotação mais recente da janela de 7 dias que termina na data pedida
 * (cobre fins de semana e feriados) e recusa a que for de data posterior à pedida. Qualquer falha vira "sem dado": nada é estimado.
 *
 * <p>O PTAX publicado de uma data passada não muda, então ele é guardado em cache por data pedida (até {@value #MAX_CACHED} datas): uma
 * importação de histórico com várias operações por dia consulta o Banco Central uma vez por data. Hoje e o futuro não entram no cache
 * (a cotação do dia sai no meio da tarde e a resolução da data muda), nem as falhas.
 */
@Component
public class BcbFxHistory implements FxHistoryPort {
    static final int MAX_CACHED = 2048;
    private static final Logger LOG = Logger.getLogger(BcbFxHistory.class.getName());
    private static final int LOOKBACK_DAYS = 7;

    private final BcbClient client;
    private final Clock clock;
    private final Map<LocalDate, PtaxQuote> cache = new ConcurrentHashMap<>();

    @Autowired
    public BcbFxHistory(BcbClient client) { this(client, Clock.system(ZoneId.of("America/Sao_Paulo"))); }

    BcbFxHistory(BcbClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public Optional<PtaxQuote> ptaxSellOnOrBefore(LocalDate date) {
        PtaxQuote cached = cache.get(date);
        if (cached != null) return Optional.of(cached);
        try {
            BcbClient.Ptax p = client.ptaxOnOrBefore(date, LOOKBACK_DAYS);
            LocalDate reference = p.time().toLocalDate();
            if (reference.isAfter(date) || p.sell() == null || p.sell().signum() <= 0) return Optional.empty();
            PtaxQuote quote = new PtaxQuote(p.sell(), reference);
            if (date.isBefore(LocalDate.now(clock))) {
                if (cache.size() >= MAX_CACHED) cache.clear();
                cache.put(date, quote);
            }
            return Optional.of(quote);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "PTAX de {0} indisponível: {1}", new Object[] {date, e.getMessage()});
            return Optional.empty();
        }
    }
}
