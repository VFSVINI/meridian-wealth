package dev.meridianwealth.web;

import dev.meridianwealth.application.ThesisService;
import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.thesis.GeoTranslator;
import dev.meridianwealth.domain.thesis.Thesis;
import dev.meridianwealth.domain.thesis.ThesisCatalog;
import dev.meridianwealth.domain.thesis.ThesisReader;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tradutor geopolítico de carteira e teses institucionais genéricas. Só leitura e cálculo: nenhuma posição real de nenhuma
 * gestora é citada, e a carteira vem na requisição, sem ser gravada nem registrada em log.
 */
@RestController
@RequestMapping("/api/thesis")
public class ThesisController {
    static final String DISCLAIMER = ThesisCatalog.DISCLAIMER;
    static final List<String> GEO_ASSUMPTIONS = List.of(
            "A pressão de cada setor vem da contagem de palavras-chave nas manchetes reais do radar macro (fonte de terceiros), não de análise de conteúdo.",
            "Só os setores que o radar mapeia (energia, defesa, financeiro, tecnologia e indústria) têm pressão calculada.",
            "O setor de cada posição vem do universo do painel; posição informada só pelo tipo fica como \"não informado\".",
            "Descreve exposição ao noticiário, não prevê preço nem diz o que fazer.");

    public record SensitivityView(String id, String label, String effect, String rationale) {}

    public record ThesisView(String id, String name, String inspiration, String summary, List<String> premises,
                             List<SensitivityView> sensitivities, List<String> invalidations) {
        static ThesisView of(Thesis t) {
            return new ThesisView(t.id(), t.name(), t.inspiration(), t.summary(), t.premises(),
                    t.sensitivities().stream().map(s -> new SensitivityView(s.id(), s.label(), s.effect().label(), s.rationale())).toList(),
                    t.invalidations());
        }
    }

    public record CatalogView(List<ThesisView> theses, String disclaimer) {}

    public record PositionRequest(String symbol, AssetType type, BigDecimal valueBrl) {}

    public record LensRequest(List<PositionRequest> positions) {}

    public record HeadlineView(String title, String source, BigDecimal pressure) {}

    public record SectorView(String sector, BigDecimal exposurePercent, BigDecimal pressure, BigDecimal contributionPoints,
                             List<HeadlineView> headlines) {}

    public record GeopoliticsView(boolean macroAvailable, BigDecimal totalBrl, BigDecimal classifiedPercent, BigDecimal unclassifiedPercent,
                                  BigDecimal weightedPressure, List<SectorView> sectors, List<String> pressuredWithoutExposure,
                                  List<String> findings, List<String> assumptions) {
        static GeopoliticsView of(GeoTranslator.Translation t) {
            return new GeopoliticsView(t.macroAvailable(), t.totalBrl(), t.classifiedPercent(), t.unclassifiedPercent(), t.weightedPressure(),
                    t.sectors().stream().map(s -> new SectorView(s.sector(), s.exposurePercent(), s.pressure(), s.contributionPoints(),
                            s.headlines().stream().map(h -> new HeadlineView(h.title(), h.source(), h.risk())).toList())).toList(),
                    t.pressuredWithoutExposure(), t.findings(), GEO_ASSUMPTIONS);
        }
    }

    public record SensitivityReadingView(String id, String label, String effect, String rationale, BigDecimal percent, List<String> holdings) {}

    public record ReadingView(ThesisView thesis, List<SensitivityReadingView> sensitivities, BigDecimal favoredPercent, BigDecimal hurtPercent,
                              BigDecimal outsideThesisPercent) {
        static ReadingView of(ThesisReader.Reading r) {
            return new ReadingView(ThesisView.of(r.thesis()),
                    r.sensitivities().stream().map(s -> new SensitivityReadingView(s.id(), s.label(), s.effect(), s.rationale(), s.percent(),
                            s.holdings())).toList(),
                    r.favoredPercent(), r.hurtPercent(), r.outsideThesisPercent());
        }
    }

    public record MacroView(boolean available, String source, Double vix, Double dollarIndex, Double us10yYield, String headline) {}

    public record LensView(boolean illustrative, GeopoliticsView geopolitics, List<ReadingView> readings, MacroView macro, String disclaimer) {}

    private final ThesisService service;

    public ThesisController(ThesisService service) { this.service = service; }

    @GetMapping
    public CatalogView catalog() { return new CatalogView(ThesisCatalog.ALL.stream().map(ThesisView::of).toList(), DISCLAIMER); }

    @PostMapping("/lens")
    public LensView lens(@RequestBody LensRequest request) {
        if (request == null || request.positions() == null) throw new IllegalArgumentException("Informe a carteira");
        ThesisService.Lens lens = service.lens(request.positions().stream().map(p -> {
            if (p == null) throw new IllegalArgumentException("Posição inválida");
            return new ThesisService.Item(p.symbol(), p.type(), p.valueBrl());
        }).toList());
        ThesisService.MacroContext m = lens.macro();
        return new LensView(true, GeopoliticsView.of(lens.geopolitics()), lens.readings().stream().map(ReadingView::of).toList(),
                new MacroView(m.available(), m.source(), m.vix(), m.dollarIndex(), m.us10yYield(), m.headline()), DISCLAIMER);
    }
}
