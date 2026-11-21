package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.application.Quote;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Stooq (CSV público): histórico diário e cotação atrasada. Pode exigir captcha/chave em alguns períodos. */
@Component
public class StooqClient implements ProviderStatusSource {
    private final HttpGateway http;
    private final ResilientExecutor executor = ResilientExecutor.withDefaults("Stooq", 3, 1.0);

    private final boolean enabled;

    /**
     * Desligado por padrão: em 2026-11-20 o download CSV passou a exigir verificação por JavaScript
     * (captcha), inviável para um cliente HTTP. Pode ser religado com {@code MW_STOOQ_ENABLED=true}.
     */
    public StooqClient(HttpGateway http, @org.springframework.beans.factory.annotation.Value("${meridianwealth.stooq.enabled:false}") boolean enabled) {
        this.http = http;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    public List<Candle> history(String stooqSymbol) {
        String url = "https://stooq.com/q/d/l/?i=d&s=" + stooqSymbol;
        return executor.callPatiently(() -> parseHistory(http.get(url, Map.of())), ResilientExecutor.BACKGROUND_WAIT);
    }

    public Quote quote(String symbol, String stooqSymbol) {
        String url = "https://stooq.com/q/l/?f=sd2t2ohlcv&h&e=csv&s=" + stooqSymbol;
        return executor.call(() -> parseQuote(symbol, http.get(url, Map.of())));
    }

    static List<Candle> parseHistory(String csv) throws IOException {
        String[] lines = csv.strip().split("\\R");
        if (lines.length < 2 || !lines[0].toLowerCase().startsWith("date")) {
            throw new IOException("Stooq: formato inesperado (sem dados, chave ou verificação JavaScript)");
        }
        List<Candle> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String[] p = lines[i].split(",");
            if (p.length < 5) continue;
            long t = LocalDate.parse(p[0]).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            out.add(new Candle(t, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Double.parseDouble(p[4]), p.length > 5 && !p[5].isBlank() ? Double.parseDouble(p[5]) : 0));
        }
        return out.size() > 520 ? new ArrayList<>(out.subList(out.size() - 520, out.size())) : out;
    }

    static Quote parseQuote(String symbol, String csv) throws IOException {
        String[] lines = csv.strip().split("\\R");
        if (lines.length < 2) throw new IOException("Stooq: cotação vazia");
        String[] p = lines[1].split(",");
        if (p.length < 7 || p[6].contains("N/D")) throw new IOException("Stooq: cotação indisponível");
        LocalTime time = p[2].contains("N/D") ? LocalTime.NOON : LocalTime.parse(p[2]);
        long t = LocalDate.parse(p[1]).atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli();
        return new Quote(symbol, Double.parseDouble(p[6]), t, "Stooq");
    }

    @Override
    public ProviderStatus status() { return executor.stats(enabled); }
}
