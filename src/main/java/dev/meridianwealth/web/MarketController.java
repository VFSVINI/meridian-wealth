package dev.meridianwealth.web;

import dev.meridianwealth.application.BacktestService;
import dev.meridianwealth.application.DataUnavailableException;
import dev.meridianwealth.application.MarketService;
import dev.meridianwealth.application.MicroView;
import dev.meridianwealth.application.SignalRecord;
import dev.meridianwealth.application.TickBatch;
import dev.meridianwealth.domain.Assessment;
import dev.meridianwealth.domain.Candle;
import dev.meridianwealth.domain.Exchange;
import dev.meridianwealth.domain.MarketHours;
import dev.meridianwealth.domain.Instrument;
import dev.meridianwealth.domain.TimingEngine;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@Validated
public class MarketController {
    private final MarketService market;
    private final SseTickSink sse;

    public MarketController(MarketService market, SseTickSink sse) {
        this.market = market;
        this.sse = sse;
    }

    @GetMapping("/universe")
    public List<Instrument> universe() { return market.universe(); }

    /** Estado do pregão das bolsas do universo: aberto/fechado, motivo (feriado, fim de semana) e hora local. */
    @GetMapping("/markets")
    public List<MarketHours.SessionStatus> markets() {
        Instant now = Instant.now();
        return market.universe().stream().map(Instrument::exchange).distinct()
                .sorted(java.util.Comparator.comparing(Exchange::ordinal))
                .map(ex -> MarketHours.status(ex, now)).toList();
    }

    @GetMapping("/overview")
    public TickBatch overview() { return market.overview(); }

    @GetMapping("/candles/{symbol}")
    public List<Candle> candles(@PathVariable String symbol, @RequestParam(defaultValue = "300") @Min(1) @Max(1500) int limit) {
        return market.candles(symbol, limit);
    }

    @GetMapping("/assessment/{symbol}")
    public Assessment assessment(@PathVariable String symbol) { return market.assessment(symbol); }

    @GetMapping("/micro/{symbol}")
    public MicroView micro(@PathVariable String symbol) { return market.micro(symbol); }

    @GetMapping("/signals/{symbol}")
    public List<SignalRecord> signals(@PathVariable String symbol, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return market.signals(symbol, limit);
    }

    @GetMapping("/backtest/{symbol}")
    public BacktestService.Result backtest(@PathVariable String symbol,
                                           @RequestParam(defaultValue = "35") @Min(5) @Max(95) int buyScore,
                                           @RequestParam(defaultValue = "30") @Min(5) @Max(95) int sellScore,
                                           @RequestParam(defaultValue = "600") @Min(TimingEngine.MIN_BARS + 30) @Max(1500) int bars) {
        return market.backtest(symbol, buyScore, sellScore, bars);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() { return sse.subscribe(); }

    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(NoSuchElementException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        Map<String, String> notFound(NoSuchElementException e) { return Map.of("error", "Ativo desconhecido"); }

        @ExceptionHandler(DataUnavailableException.class)
        @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
        Map<String, String> unavailable(DataUnavailableException e) {
            return Map.of("error", e.getMessage(), "status", e.status().name(), "symbol", e.symbol());
        }

        @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        Map<String, String> badRequest(RuntimeException e) { return Map.of("error", e.getMessage()); }
    }
}
