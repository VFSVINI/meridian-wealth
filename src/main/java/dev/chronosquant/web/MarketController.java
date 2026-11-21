package dev.chronosquant.web;

import dev.chronosquant.application.BacktestService;
import dev.chronosquant.application.MarketService;
import dev.chronosquant.application.MicroView;
import dev.chronosquant.application.SignalRecord;
import dev.chronosquant.application.TickBatch;
import dev.chronosquant.domain.Assessment;
import dev.chronosquant.domain.Candle;
import dev.chronosquant.domain.Instrument;
import dev.chronosquant.domain.TimingEngine;
import dev.chronosquant.infrastructure.stream.StreamHub;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final StreamHub hub;

    public MarketController(MarketService market, StreamHub hub) {
        this.market = market;
        this.hub = hub;
    }

    @GetMapping("/universe")
    public List<Instrument> universe() { return market.universe(); }

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

    @PostMapping("/settings/flow")
    public Map<String, Object> flow(@RequestParam boolean enabled) {
        market.settings().setFlow(enabled);
        return Map.of("flow", enabled);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() { return hub.subscribe(); }

    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(NoSuchElementException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        Map<String, String> notFound(NoSuchElementException e) { return Map.of("error", "Ativo desconhecido: " + e.getMessage()); }

        @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        Map<String, String> badRequest(RuntimeException e) { return Map.of("error", e.getMessage()); }
    }
}
