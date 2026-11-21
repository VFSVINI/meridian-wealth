package dev.meridianwealth.domain.thesis;

import static dev.meridianwealth.domain.thesis.Thesis.Effect.FAVORECIDO_SE_CONFIRMAR;
import static dev.meridianwealth.domain.thesis.Thesis.Effect.PREJUDICADO_SE_CONFIRMAR;

import dev.meridianwealth.domain.AssetType;
import dev.meridianwealth.domain.thesis.Thesis.Sensitivity;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * As cinco teses genéricas. A "inspiração" resume, em uma linha, a abordagem que cada gestora descreve publicamente de si; o
 * resto é conteúdo didático do produto. Nenhuma posição real é atribuída a ninguém, e as teses não são recomendação.
 */
public final class ThesisCatalog {
    public static final String DISCLAIMER = "Teses ilustrativas e genéricas, inspiradas no estilo público de cada gestora. Não representam posições reais "
            + "de nenhuma instituição, não foram revisadas por elas e não são recomendação de investimento.";

    private static final Set<AssetType> TESOURO = EnumSet.of(AssetType.TESOURO_SELIC, AssetType.TESOURO_PREFIXADO,
            AssetType.TESOURO_PREFIXADO_JUROS, AssetType.TESOURO_IPCA, AssetType.TESOURO_IPCA_JUROS);
    private static final Set<AssetType> PREFIXADO_E_IPCA = EnumSet.of(AssetType.TESOURO_PREFIXADO, AssetType.TESOURO_PREFIXADO_JUROS,
            AssetType.TESOURO_IPCA, AssetType.TESOURO_IPCA_JUROS);
    private static final Set<AssetType> ACOES = EnumSet.of(AssetType.ACAO, AssetType.ETF, AssetType.FUNDO_ACOES);
    private static final Set<AssetType> LIQUIDOS = EnumSet.of(AssetType.ACAO, AssetType.ETF, AssetType.CRIPTO_ATIVO, AssetType.CAMBIO);
    private static final Set<AssetType> ILIQUIDOS = EnumSet.of(AssetType.FII, AssetType.CRI, AssetType.CRA, AssetType.NOTA_COMERCIAL,
            AssetType.NOTA_PROMISSORIA);

    public static final List<Thesis> ALL = List.of(
            new Thesis("equilibrio-de-riscos", "Equilíbrio entre cenários",
                    "Estilo de paridade de risco (Bridgewater): equilibrar o risco entre cenários de crescimento e de inflação.",
                    "Nenhum cenário econômico domina o futuro; por isso o risco deve vir de fontes diferentes, e não de uma só classe.",
                    List.of("Crescimento e inflação podem surpreender para cima ou para baixo, e ninguém acerta qual cenário vem.",
                            "Classes diferentes reagem de formas diferentes a cada cenário.",
                            "Distribuir o risco entre elas reduz a dependência de uma aposta."),
                    List.of(new Sensitivity("rf-publica", "Renda fixa pública", TESOURO, Set.of(), FAVORECIDO_SE_CONFIRMAR,
                                    "Ajuda quando o crescimento desacelera e os juros caem."),
                            new Sensitivity("commodities-ouro", "Commodities e ouro", EnumSet.of(AssetType.COMMODITY), Set.of(), FAVORECIDO_SE_CONFIRMAR,
                                    "Costumam reagir a inflação inesperada."),
                            new Sensitivity("acoes", "Ações", ACOES, Set.of(), FAVORECIDO_SE_CONFIRMAR,
                                    "Costumam reagir bem a crescimento acima do esperado.")),
                    List.of("Em crises fortes, as classes caem juntas (correlação sobe) e a diversificação deixa de proteger.",
                            "Juros e inflação sobem ao mesmo tempo e pressionam renda fixa e ações juntas.",
                            "Uma classe passa a responder por quase todo o risco da carteira.")),

            new Thesis("megatendencias", "Megatendências estruturais",
                    "Estilo de temas de longo prazo (BlackRock): transição energética, tecnologia e demografia.",
                    "Mudanças lentas e duradouras criam vencedores e perdedores ao longo de anos, independentemente do ciclo.",
                    List.of("Tecnologia e digitalização continuam ampliando a participação no PIB.",
                            "A transição energética redistribui investimento entre fontes de energia.",
                            "O envelhecimento da população muda o consumo e a poupança."),
                    List.of(new Sensitivity("tecnologia", "Tecnologia", Set.of(), Set.of("tecnologia"), FAVORECIDO_SE_CONFIRMAR,
                                    "É o tema central de digitalização."),
                            new Sensitivity("energia", "Energia", Set.of(), Set.of("energia"), PREJUDICADO_SE_CONFIRMAR,
                                    "Energia de origem fóssil perde espaço no longo prazo se a transição avançar."),
                            new Sensitivity("mineracao", "Mineração", Set.of(), Set.of("mineracao"), FAVORECIDO_SE_CONFIRMAR,
                                    "Metais entram na transição energética e na infraestrutura digital.")),
                    List.of("Juros altos por muito tempo derrubam o valor presente de lucros distantes, e os temas perdem preço.",
                            "Regulação ou custo de capital freia a adoção de uma tecnologia.",
                            "O tema fica caro demais para o crescimento que entrega.")),

            new Thesis("sistematica-estatistica", "Sinais estatísticos e liquidez",
                    "Estilo quantitativo sistemático (Renaissance): muitos sinais estatísticos pequenos, em ativos líquidos.",
                    "Padrões estatísticos de curto prazo podem render pouco por operação, o que só funciona com liquidez, custo baixo e disciplina.",
                    List.of("Os preços carregam padrões fracos que se repetem estatisticamente.",
                            "O ganho por operação é pequeno e depende de custo baixo.",
                            "O risco é controlado pela diversificação em muitas posições."),
                    List.of(new Sensitivity("liquidos", "Ativos líquidos", LIQUIDOS, Set.of(), FAVORECIDO_SE_CONFIRMAR,
                                    "Entram e saem rápido e com custo baixo."),
                            new Sensitivity("iliquidos", "Ativos de baixa liquidez", ILIQUIDOS, Set.of(), PREJUDICADO_SE_CONFIRMAR,
                                    "Custo e prazo de saída corroem ganhos pequenos.")),
                    List.of("Os padrões desaparecem quando muita gente passa a explorá-los.",
                            "O custo de operar sobe e consome a vantagem.",
                            "Um regime de mercado novo invalida o que o histórico mostrava.")),

            new Thesis("macro-juros-e-cambio", "Macro: juros e câmbio",
                    "Estilo de macro global (Verde/SPX): decisões baseadas em juros, câmbio e ciclo econômico.",
                    "A trajetória dos juros e do câmbio, no Brasil e fora, move os preços de várias classes ao mesmo tempo.",
                    List.of("A trajetória da Selic e da inflação define o retorno da renda fixa e o apetite por risco.",
                            "O dólar reage ao diferencial de juros entre o Brasil e os EUA e ao risco global.",
                            "Choques externos chegam ao Brasil pelo câmbio."),
                    List.of(new Sensitivity("pos-fixado", "Pós-fixados (Selic)", EnumSet.of(AssetType.TESOURO_SELIC), Set.of(), FAVORECIDO_SE_CONFIRMAR,
                                    "Acompanham a Selic: ajudam se os juros ficarem altos."),
                            new Sensitivity("prefixado-ipca", "Prefixados e IPCA+", PREFIXADO_E_IPCA, Set.of(), PREJUDICADO_SE_CONFIRMAR,
                                    "Perdem valor de mercado se os juros futuros subirem."),
                            new Sensitivity("cambio", "Câmbio", EnumSet.of(AssetType.CAMBIO), Set.of(), FAVORECIDO_SE_CONFIRMAR,
                                    "Reage à alta do dólar.")),
                    List.of("O Banco Central muda a trajetória dos juros de forma diferente da esperada.",
                            "O dólar se move contra a posição por um fator que não estava no cenário.",
                            "Um choque fiscal ou político muda a leitura de risco do país.")),

            new Thesis("valor-de-longo-prazo", "Valor de longo prazo",
                    "Estilo de gestão de valor (Dynamo): ações de empresas boas compradas abaixo do valor que se estima, com horizonte longo.",
                    "O preço oscila no curto prazo, mas converge ao valor da empresa no longo prazo se ela gerar caixa.",
                    List.of("O valor da empresa vem do caixa que ela gera ao longo dos anos.",
                            "O mercado erra o preço por pessimismo ou otimismo exagerados.",
                            "Paciência e preço de entrada importam mais que acertar o momento."),
                    List.of(new Sensitivity("acoes-brasil", "Ações de empresas listadas", EnumSet.of(AssetType.ACAO), Set.of(), FAVORECIDO_SE_CONFIRMAR,
                                    "São o ativo que a tese compra: participação em empresas que geram caixa."),
                            new Sensitivity("cripto", "Criptoativos", EnumSet.of(AssetType.CRIPTO_ATIVO), Set.of(), PREJUDICADO_SE_CONFIRMAR,
                                    "Não geram caixa: a tese de valor não tem como avaliá-los.")),
                    List.of("O resultado da empresa piora de forma duradoura e o 'barato' era armadilha.",
                            "O preço fica longe do valor por muito mais tempo que o horizonte do investidor.",
                            "A empresa perde a vantagem competitiva que sustentava o caixa.")));

    private ThesisCatalog() {}

    public static Optional<Thesis> find(String id) { return ALL.stream().filter(t -> t.id().equals(id)).findFirst(); }

    static {
        Set<String> ids = new HashSet<>();
        for (Thesis t : ALL) {
            if (!ids.add(t.id())) throw new IllegalStateException("Tese repetida: " + t.id());
            Set<String> sens = new HashSet<>();
            t.sensitivities().forEach(s -> {
                if (!sens.add(s.id())) throw new IllegalStateException("Sensibilidade repetida em " + t.id() + ": " + s.id());
            });
        }
    }
}
