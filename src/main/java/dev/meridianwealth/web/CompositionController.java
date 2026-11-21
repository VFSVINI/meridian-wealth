package dev.meridianwealth.web;

import dev.meridianwealth.application.CompositionService;
import dev.meridianwealth.application.CompositionService.GeoAsset;
import dev.meridianwealth.application.CompositionUnavailableException;
import dev.meridianwealth.domain.composition.Allocation;
import dev.meridianwealth.domain.composition.Coordinates;
import dev.meridianwealth.domain.composition.FundComposition;
import dev.meridianwealth.domain.composition.FundMetrics;
import dev.meridianwealth.domain.composition.MapLinks;
import dev.meridianwealth.domain.composition.RealAsset;
import dev.meridianwealth.domain.composition.SourceRef;
import dev.meridianwealth.domain.composition.TenantSector;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Raio-X de composição e geolocalização de ativos reais. Só serve dados curados com fonte e data; o que a fonte não
 * publica sai como {@code null} com o motivo, nunca estimado.
 */
@RestController
@RequestMapping("/api/composition")
public class CompositionController {
    static final String ATTRIBUTION = "Coordenadas obtidas por geocodificação do endereço publicado na fonte, com dados © "
            + "colaboradores do OpenStreetMap (licença ODbL). Links do Google Maps apenas abrem o local; nenhuma imagem é copiada.";
    static final String DISCLAIMER = "Ferramenta analítica e educacional. Não é recomendação de investimento.";

    public record SourceView(String name, String url, String kind, String kindLabel, String referenceDate,
                             LocalDate consultedOn, String evidenceSha256) {
        static SourceView of(SourceRef s) {
            return new SourceView(s.name(), s.url(), s.kind().name(), s.kind().label(), s.referenceDate(), s.consultedOn(),
                    s.evidenceSha256());
        }
    }

    public record CoordinatesView(BigDecimal lat, BigDecimal lng, String precision, boolean approximate,
                                  String matchedAddress, String method, String osmRef, LocalDate geocodedOn,
                                  String satelliteUrl, String streetViewUrl) {
        static CoordinatesView of(Coordinates c) {
            return new CoordinatesView(c.lat(), c.lng(), c.precision().name(), c.precision().name().equals("STREET"),
                    c.matchedAddress(), c.method(), c.osmRef(), c.geocodedOn(), MapLinks.satellite(c), MapLinks.streetView(c));
        }
    }

    public record AssetView(String id, String name, String publishedAddress, String city, String uf, String kind,
                            BigDecimal areaM2, String publishedArea, BigDecimal areaSharePercent, BigDecimal vacancyPercent,
                            BigDecimal revenueSharePercent, List<TenantSector> tenantSectors, CoordinatesView coordinates,
                            String noCoordinatesReason) {
        static AssetView of(FundComposition f, RealAsset a, BigDecimal listedTotalM2) {
            return new AssetView(a.id(), a.name(), a.publishedAddress(), a.city(), a.uf(),
                    a.kind() == null ? null : a.kind().name(), a.areaM2(), a.publishedArea(), f.areaSharePercent(a, listedTotalM2),
                    a.vacancyPercent(), a.revenueSharePercent(), a.tenantSectors(),
                    a.coordinates() == null ? null : CoordinatesView.of(a.coordinates()), a.noCoordinatesReason());
        }
    }

    public record CompositionView(String symbol, String fundName, String cnpj, SourceView source, String areaLabel,
                                  Integer declaredAssetCount, int listedAssetCount, int geolocatedCount,
                                  BigDecimal listedAreaM2, FundMetrics metrics, String classification,
                                  List<AssetView> assets, List<Allocation> allocations, Map<String, BigDecimal> totalsBrl,
                                  List<String> notices, String attribution, String disclaimer, long dataAgeDays, int staleAfterDays,
                                  boolean stale) {
        static CompositionView of(FundComposition f, LocalDate today) {
            var age = f.age(today);
            BigDecimal total = f.listedAreaM2();
            return new CompositionView(f.fundSymbol(), f.fundName(), f.cnpj(), SourceView.of(f.source()), f.areaLabel(),
                    f.declaredAssetCount(), f.assets().size(), (int) f.geolocatedCount(), total, f.metrics(),
                    f.classification(), f.assets().stream().map(a -> AssetView.of(f, a, total)).toList(), f.allocations(),
                    f.totalsBrl(), f.notices(), ATTRIBUTION, DISCLAIMER, age.days(), age.staleAfterDays(), age.stale());
        }
    }

    public record GeoPoint(String fundSymbol, String sourceKind, String sourceName, String referenceDate, AssetView asset) {}

    public record GeoView(List<GeoPoint> points, String attribution, String disclaimer) {}

    private final CompositionService service;

    public CompositionController(CompositionService service) { this.service = service; }

    @GetMapping
    public List<CompositionView> all() { return service.all().stream().map(f -> CompositionView.of(f, service.today())).toList(); }

    @GetMapping("/geo")
    public GeoView geo() {
        Map<String, BigDecimal> totals = new java.util.HashMap<>();
        List<GeoPoint> points = service.geolocated().stream()
                .map(g -> point(g, totals.computeIfAbsent(g.fund().fundSymbol(), k -> g.fund().listedAreaM2()))).toList();
        return new GeoView(points, ATTRIBUTION, DISCLAIMER);
    }

    @GetMapping("/{symbol}")
    public CompositionView one(@PathVariable String symbol) {
        return CompositionView.of(service.composition(symbol.toUpperCase(java.util.Locale.ROOT)), service.today());
    }

    private static GeoPoint point(GeoAsset g, BigDecimal listedTotalM2) {
        SourceRef s = g.fund().source();
        return new GeoPoint(g.fund().fundSymbol(), s.kind().name(), s.name(), s.referenceDate(), AssetView.of(g.fund(), g.asset(), listedTotalM2));
    }

    @RestControllerAdvice(assignableTypes = CompositionController.class)
    static class Errors {
        @ExceptionHandler(CompositionUnavailableException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        Map<String, String> unavailable(CompositionUnavailableException e) {
            return Map.of("error", e.getMessage(), "symbol", e.symbol());
        }
    }
}
