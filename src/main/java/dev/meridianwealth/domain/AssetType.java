package dev.meridianwealth.domain;

import static dev.meridianwealth.domain.AssetCategory.CAMBIO_E_COMMODITIES;
import static dev.meridianwealth.domain.AssetCategory.CRIPTO;
import static dev.meridianwealth.domain.AssetCategory.FUNDOS;
import static dev.meridianwealth.domain.AssetCategory.REFERENCIA;
import static dev.meridianwealth.domain.AssetCategory.RENDA_FIXA_PRIVADA;
import static dev.meridianwealth.domain.AssetCategory.RENDA_FIXA_PUBLICA;
import static dev.meridianwealth.domain.AssetCategory.RENDA_VARIAVEL;
import static dev.meridianwealth.domain.CreditProtection.FGC_ATE_LIMITE;
import static dev.meridianwealth.domain.CreditProtection.NAO_SE_APLICA;
import static dev.meridianwealth.domain.CreditProtection.RISCO_SOBERANO;
import static dev.meridianwealth.domain.CreditProtection.SEM_GARANTIA_FGC;
import static dev.meridianwealth.domain.IncomeKind.CONFORME_REGRAS;
import static dev.meridianwealth.domain.IncomeKind.CUPOM_SEMESTRAL;
import static dev.meridianwealth.domain.IncomeKind.NENHUM;
import static dev.meridianwealth.domain.IncomeKind.NO_VENCIMENTO;
import static dev.meridianwealth.domain.IncomeKind.PROVENTOS;
import static dev.meridianwealth.domain.Indexer.CONFORME_EMISSAO;
import static dev.meridianwealth.domain.Indexer.IPCA_MAIS;
import static dev.meridianwealth.domain.Indexer.PRE_FIXADO;
import static dev.meridianwealth.domain.Indexer.SELIC;
import static dev.meridianwealth.domain.PriceSource.INFORMADO_PELO_USUARIO;
import static dev.meridianwealth.domain.PriceSource.MERCADO;
import static dev.meridianwealth.domain.PriceSource.TABELA_OFICIAL;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Taxonomia multi-ativo da plataforma. Cada tipo declara só atributos definidos pelo próprio produto ou
 * verificados em fonte pública (ver {@code docs/taxonomia.md}); regras de imposto ficam para os itens #13
 * e #25 do roadmap, com a base legal citada.
 *
 * <p>Os cinco títulos do Tesouro Direto usam o nome do produto (conferido nos dados abertos da STN em
 * 2026-11-20) e, em {@link #code()}, a sigla do título público subjacente na nomenclatura do Tesouro
 * Nacional (LFT, LTN, NTN-F, NTN-B Principal e NTN-B).
 */
public enum AssetType {
    ACAO("Ação", "ACAO", RENDA_VARIAVEL, Indexer.NENHUM, PROVENTOS, false, NAO_SE_APLICA, MERCADO),
    ETF("ETF (fundo de índice)", "ETF", RENDA_VARIAVEL, Indexer.NENHUM, CONFORME_REGRAS, false, NAO_SE_APLICA, MERCADO),
    FII("Fundo imobiliário (FII)", "FII", RENDA_VARIAVEL, Indexer.NENHUM, PROVENTOS, false, NAO_SE_APLICA, MERCADO),

    FUNDO_RENDA_FIXA("Fundo de renda fixa", "FUNDO_RF", FUNDOS, Indexer.NENHUM, NENHUM, false, SEM_GARANTIA_FGC, TABELA_OFICIAL),
    FUNDO_MULTIMERCADO("Fundo multimercado", "FUNDO_MM", FUNDOS, Indexer.NENHUM, NENHUM, false, SEM_GARANTIA_FGC, TABELA_OFICIAL),
    FUNDO_ACOES("Fundo de ações", "FUNDO_ACOES", FUNDOS, Indexer.NENHUM, NENHUM, false, SEM_GARANTIA_FGC, TABELA_OFICIAL),

    TESOURO_SELIC("Tesouro Selic", "LFT", RENDA_FIXA_PUBLICA, SELIC, NO_VENCIMENTO, true, RISCO_SOBERANO, TABELA_OFICIAL),
    TESOURO_PREFIXADO("Tesouro Prefixado", "LTN", RENDA_FIXA_PUBLICA, PRE_FIXADO, NO_VENCIMENTO, true, RISCO_SOBERANO, TABELA_OFICIAL),
    TESOURO_PREFIXADO_JUROS("Tesouro Prefixado com Juros Semestrais", "NTN-F", RENDA_FIXA_PUBLICA, PRE_FIXADO,
            CUPOM_SEMESTRAL, true, RISCO_SOBERANO, TABELA_OFICIAL),
    TESOURO_IPCA("Tesouro IPCA+", "NTN-B Principal", RENDA_FIXA_PUBLICA, IPCA_MAIS, NO_VENCIMENTO, true,
            RISCO_SOBERANO, TABELA_OFICIAL),
    TESOURO_IPCA_JUROS("Tesouro IPCA+ com Juros Semestrais", "NTN-B", RENDA_FIXA_PUBLICA, IPCA_MAIS, CUPOM_SEMESTRAL,
            true, RISCO_SOBERANO, TABELA_OFICIAL),

    LCI("Letra de Crédito Imobiliário (LCI)", "LCI", RENDA_FIXA_PRIVADA, CONFORME_EMISSAO, CONFORME_REGRAS, true,
            FGC_ATE_LIMITE, INFORMADO_PELO_USUARIO),
    LCA("Letra de Crédito do Agronegócio (LCA)", "LCA", RENDA_FIXA_PRIVADA, CONFORME_EMISSAO, CONFORME_REGRAS, true,
            FGC_ATE_LIMITE, INFORMADO_PELO_USUARIO),
    CRI("Certificado de Recebíveis Imobiliários (CRI)", "CRI", RENDA_FIXA_PRIVADA, CONFORME_EMISSAO, CONFORME_REGRAS,
            true, SEM_GARANTIA_FGC, INFORMADO_PELO_USUARIO),
    CRA("Certificado de Recebíveis do Agronegócio (CRA)", "CRA", RENDA_FIXA_PRIVADA, CONFORME_EMISSAO, CONFORME_REGRAS,
            true, SEM_GARANTIA_FGC, INFORMADO_PELO_USUARIO),
    NOTA_COMERCIAL("Nota comercial", "NC", RENDA_FIXA_PRIVADA, CONFORME_EMISSAO, CONFORME_REGRAS, true,
            SEM_GARANTIA_FGC, INFORMADO_PELO_USUARIO),
    NOTA_PROMISSORIA("Nota promissória", "NP", RENDA_FIXA_PRIVADA, CONFORME_EMISSAO, CONFORME_REGRAS, true,
            SEM_GARANTIA_FGC, INFORMADO_PELO_USUARIO),

    CRIPTO_ATIVO("Criptoativo", "CRIPTO", CRIPTO, Indexer.NENHUM, NENHUM, false, NAO_SE_APLICA, MERCADO),
    CAMBIO("Câmbio", "CAMBIO", CAMBIO_E_COMMODITIES, Indexer.NENHUM, NENHUM, false, NAO_SE_APLICA, MERCADO),
    COMMODITY("Commodity", "COMMODITY", CAMBIO_E_COMMODITIES, Indexer.NENHUM, NENHUM, false, NAO_SE_APLICA, MERCADO),
    INDICE("Índice de referência", "INDICE", REFERENCIA, Indexer.NENHUM, NENHUM, false, NAO_SE_APLICA, MERCADO);

    private final String label;
    private final String code;
    private final AssetCategory category;
    private final Indexer indexer;
    private final IncomeKind income;
    private final boolean hasMaturity;
    private final CreditProtection protection;
    private final PriceSource priceSource;

    AssetType(String label, String code, AssetCategory category, Indexer indexer, IncomeKind income,
              boolean hasMaturity, CreditProtection protection, PriceSource priceSource) {
        this.label = label;
        this.code = code;
        this.category = category;
        this.indexer = indexer;
        this.income = income;
        this.hasMaturity = hasMaturity;
        this.protection = protection;
        this.priceSource = priceSource;
    }

    public String label() { return label; }

    public String code() { return code; }

    public AssetCategory category() { return category; }

    public Indexer indexer() { return indexer; }

    public IncomeKind income() { return income; }

    /** Tem data de vencimento (títulos de renda fixa); ações, fundos e cripto não têm. */
    public boolean hasMaturity() { return hasMaturity; }

    public CreditProtection protection() { return protection; }

    public PriceSource priceSource() { return priceSource; }

    /** Pode ser comprado numa carteira (índices e taxas servem só de referência). */
    public boolean investable() { return category != REFERENCIA; }

    public static List<AssetType> byCategory(AssetCategory category) {
        return Arrays.stream(values()).filter(t -> t.category == category).toList();
    }

    /** Converte a classe textual usada no cadastro dos instrumentos ("Ação", "FII", "Cripto"...). */
    public static Optional<AssetType> fromInstrumentClass(String assetClass) {
        if (assetClass == null) return Optional.empty();
        return switch (assetClass) {
            case "Ação" -> Optional.of(ACAO);
            case "ETF" -> Optional.of(ETF);
            case "FII" -> Optional.of(FII);
            case "Cripto" -> Optional.of(CRIPTO_ATIVO);
            case "Câmbio" -> Optional.of(CAMBIO);
            case "Commodity" -> Optional.of(COMMODITY);
            case "Índice" -> Optional.of(INDICE);
            default -> Optional.empty();
        };
    }
}
