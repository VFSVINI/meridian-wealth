package dev.meridianwealth.web;

import dev.meridianwealth.domain.AssetType;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Taxonomia multi-ativo: os tipos de ativo que a plataforma conhece e seus atributos. */
@RestController
@RequestMapping("/api")
public class TaxonomyController {
    /** Um tipo de ativo, com a chave estável do enum e o rótulo em português de cada atributo. */
    public record AssetTypeView(String key, String code, String label, String category, String categoryLabel,
                                String indexer, String indexerLabel, String income, String incomeLabel,
                                boolean hasMaturity, String protection, String protectionLabel, String priceSource,
                                String priceSourceLabel, boolean investable) {
        static AssetTypeView of(AssetType t) {
            return new AssetTypeView(t.name(), t.code(), t.label(), t.category().name(), t.category().label(),
                    t.indexer().name(), t.indexer().label(), t.income().name(), t.income().label(), t.hasMaturity(),
                    t.protection().name(), t.protection().label(), t.priceSource().name(), t.priceSource().label(),
                    t.investable());
        }
    }

    @GetMapping("/taxonomy")
    public List<AssetTypeView> taxonomy() {
        return Arrays.stream(AssetType.values()).map(AssetTypeView::of).toList();
    }
}
