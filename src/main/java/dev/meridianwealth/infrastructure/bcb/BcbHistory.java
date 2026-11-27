package dev.meridianwealth.infrastructure.bcb;

import dev.meridianwealth.application.port.BrazilHistoryPort;
import dev.meridianwealth.domain.HistorySeries;
import dev.meridianwealth.domain.RateHistory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Histórico das séries do SGS do Banco Central (item #50). Qualquer falha vira "sem dado": quem chama mantém o último valor bom. */
@Component
public class BcbHistory implements BrazilHistoryPort {
    private static final Logger LOG = Logger.getLogger(BcbHistory.class.getName());
    private final BcbClient client;
    private final Clock clock;

    @Autowired
    public BcbHistory(BcbClient client) { this(client, Clock.system(ZoneId.of("America/Sao_Paulo"))); }

    BcbHistory(BcbClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public Optional<RateHistory> history(HistorySeries series, LocalDate from, LocalDate to) {
        try {
            List<BcbClient.Point> pts = client.series(series.sgsCode(), from, to);
            return Optional.of(new RateHistory(series, pts.stream().map(p -> new RateHistory.Point(p.date(), p.value())).toList(), clock.instant()));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Histórico {0} indisponível: {1}", new Object[] {series.key(), e.getMessage()});
            return Optional.empty();
        }
    }
}
