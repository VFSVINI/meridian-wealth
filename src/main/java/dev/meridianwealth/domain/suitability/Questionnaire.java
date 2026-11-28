package dev.meridianwealth.domain.suitability;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Questionário de perfil. As sete perguntas cobrem as três dimensões que o art. 3 da Resolução CVM 30 manda verificar
 * (objetivos, situação financeira e conhecimento), com os itens mínimos dos parágrafos 1 a 3. É uma <b>definição do
 * produto</b>, educacional: pontos, limiares e tetos não vêm de norma e não substituem a análise de um profissional
 * habilitado.
 */
public final class Questionnaire {
    public enum Dimension {
        OBJECTIVES("Objetivos de investimento"),
        FINANCIAL_SITUATION("Situação financeira"),
        KNOWLEDGE("Conhecimento");

        private final String label;

        Dimension(String label) { this.label = label; }

        public String label() { return label; }
    }

    public record Option(String id, String text, int points) {}

    public record Question(String id, Dimension dimension, String legalRef, String text, List<Option> options) {
        public int maxPoints() { return options.stream().mapToInt(Option::points).max().orElse(0); }

        Optional<Option> option(String optionId) { return options.stream().filter(o -> o.id().equals(optionId)).findFirst(); }
    }

    /** Teto de perfil: a resposta {@code optionId} da pergunta {@code questionId} limita o perfil a {@code maxProfile}. */
    public record Cap(String questionId, String optionId, InvestorProfile maxProfile, String reason) {}

    private static final String OBJ = "Resolução CVM 30, art. 3, §1";
    private static final String FIN = "Resolução CVM 30, art. 3, §2";
    private static final String KNOW = "Resolução CVM 30, art. 3, §3";

    public static final List<Question> QUESTIONS = List.of(
            new Question("HORIZON", Dimension.OBJECTIVES, OBJ + ", I", "Por quanto tempo você pretende manter este dinheiro investido?", List.of(
                    new Option("UP_TO_1Y", "Até 1 ano", 0), new Option("1_3Y", "De 1 a 3 anos", 1),
                    new Option("3_5Y", "De 3 a 5 anos", 2), new Option("5_10Y", "De 5 a 10 anos", 3),
                    new Option("OVER_10Y", "Mais de 10 anos", 4))),
            new Question("DRAWDOWN", Dimension.OBJECTIVES, OBJ + ", II", "Se seus investimentos caíssem 20% em 3 meses, o que você faria?", List.of(
                    new Option("SELL_ALL", "Venderia tudo", 0), new Option("SELL_PART", "Venderia uma parte", 1),
                    new Option("HOLD", "Manteria", 3), new Option("BUY_MORE", "Compraria mais", 4))),
            new Question("PURPOSE", Dimension.OBJECTIVES, OBJ + ", III", "Qual é a principal finalidade deste investimento?", List.of(
                    new Option("PRESERVE", "Preservar o patrimônio", 0), new Option("INCOME", "Gerar renda complementar", 1),
                    new Option("LONG_TERM", "Acumular para um projeto de longo prazo ou aposentadoria", 3),
                    new Option("GROW", "Multiplicar o patrimônio, aceitando oscilações", 4))),
            new Question("INCOME", Dimension.FINANCIAL_SITUATION, FIN + ", I", "Como sua renda regular se compara às suas despesas?", List.of(
                    new Option("NOT_ENOUGH", "Não cobre as despesas", 0), new Option("TIGHT", "Cobre sem folga", 1),
                    new Option("COMFORTABLE", "Cobre com folga moderada", 3), new Option("LARGE_SURPLUS", "Sobra uma parte grande", 4))),
            new Question("WEALTH_SHARE", Dimension.FINANCIAL_SITUATION, FIN + ", II", "Que parte do seu patrimônio total este dinheiro representa?", List.of(
                    new Option("OVER_70", "Mais de 70%", 0), new Option("30_70", "Entre 30% e 70%", 2),
                    new Option("UNDER_30", "Menos de 30%", 4))),
            new Question("LIQUIDITY_RESERVE", Dimension.FINANCIAL_SITUATION, FIN + ", III", "Você vai precisar deste dinheiro nos próximos 12 meses, e tem uma reserva de emergência separada dele?", List.of(
                    new Option("NEED_SOON", "Vou precisar dele em até 12 meses", 0), new Option("NO_RESERVE", "Não devo precisar, mas não tenho reserva de emergência", 1),
                    new Option("UNDER_6M", "Não devo precisar e tenho reserva de menos de 6 meses de despesas", 2),
                    new Option("OVER_6M", "Não devo precisar e tenho reserva de 6 meses ou mais", 4))),
            new Question("KNOWLEDGE", Dimension.KNOWLEDGE, KNOW + ", I e II", "Com quais produtos você investe e há quanto tempo?", List.of(
                    new Option("BASIC", "Só poupança e CDB, ou ainda não invisto em produtos de risco", 0),
                    new Option("FIXED_INCOME", "Tesouro Direto e fundos de renda fixa, ou ações há menos de 2 anos", 1),
                    new Option("STOCKS", "Ações e fundos imobiliários, de 2 a 5 anos", 3),
                    new Option("ADVANCED", "Ações e fundos imobiliários há mais de 5 anos, ou também derivativos, criptoativos ou ativos no exterior", 4))));

    /** Tetos de perfil por resposta: situação ou comportamento incompatível com risco alto limita o perfil, qualquer que seja a nota. */
    public static final List<Cap> CAPS = List.of(
            new Cap("HORIZON", "UP_TO_1Y", InvestorProfile.CONSERVATIVE, "Horizonte de até 1 ano não comporta oscilação de renda variável."),
            new Cap("LIQUIDITY_RESERVE", "NEED_SOON", InvestorProfile.CONSERVATIVE, "Você precisa do dinheiro em até 12 meses."),
            new Cap("LIQUIDITY_RESERVE", "NO_RESERVE", InvestorProfile.MODERATE, "Sem reserva de emergência, uma perda força a venda no pior momento."),
            new Cap("DRAWDOWN", "SELL_ALL", InvestorProfile.MODERATE, "Vender tudo numa queda de 20% revela tolerância baixa a perdas."));

    private static final Map<String, Question> BY_ID = QUESTIONS.stream().collect(Collectors.toUnmodifiableMap(Question::id, Function.identity()));

    private Questionnaire() {}

    public static int maxTotalPoints() { return QUESTIONS.stream().mapToInt(Question::maxPoints).sum(); }

    public static Optional<Question> question(String id) { return Optional.ofNullable(BY_ID.get(id)); }
}
