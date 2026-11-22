package dev.meridianwealth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Teste dourado (de caracterização) do motor de timing: congela, para onze cenários determinísticos, TODOS os campos da avaliação (score,
 * sinal, fase, regime, confiança, risco, indicadores, zonas, expectativa e os textos dos fatores). O PIT mostrou que o motor, o núcleo do
 * produto, tinha 85% dos mutantes vivos: os testes antigos só conferiam faixas. Este teste não prova que as regras estão certas (os testes de
 * fórmula e de propriedade fazem isso por outros caminhos); prova que qualquer mudança nelas aparece, e quem a fizer precisa regravar os
 * valores de propósito.
 */
class TimingEngineGoldenTest {
    private static final double TOL = 1e-6;
    private final TimingEngine engine = new TimingEngine();

    private Assessment assess(String scenario) {
        GoldenScenarios.Scenario s = GoldenScenarios.all().get(scenario);
        return engine.assess("G", s.candles(), s.macro(), s.benchmark(), s.perYear());
    }

    @Test void uptrend() {
        Assessment a = assess("uptrend");
        assertThat(a.score()).isEqualTo(-20);
        assertThat(a.signal()).isEqualTo(Signal.TAKE_PROFIT_WINDOW);
        assertThat(a.phase()).isEqualTo(Phase.ESTICADO_REALIZAR);
        assertThat(a.regime()).isEqualTo(Regime.UPTREND);
        assertThat(a.confidence()).isEqualTo(84);
        assertThat(a.risk()).isEqualTo(RiskClass.LOW);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isNull();
        assertThat(a.price()).isCloseTo(155.1732923063, within(TOL));
        assertThat(a.zScore()).isCloseTo(1.2703616985, within(TOL));
        assertThat(a.percentile()).isCloseTo(97.5903614458, within(TOL));
        assertThat(a.rsi()).isCloseTo(70.6688939720, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-0.2656703053, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.0440434432, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.0114994500, within(TOL));
        assertThat(a.sharpe()).isCloseTo(8.0335543035, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0024408262, within(TOL));
        assertThat(a.atr()).isCloseTo(2.1536901784, within(TOL));
        assertThat(a.fairValue()).isCloseTo(150.9080202615, within(TOL));
        assertThat(a.entryLow()).isCloseTo(153.0196021279, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(155.7117148509, within(TOL));
        assertThat(a.invalidation()).isCloseTo(150.8659119495, within(TOL));
        assertThat(a.target1()).isCloseTo(157.3269824847, within(TOL));
        assertThat(a.target2()).isCloseTo(159.4806726631, within(TOL));
        assertThat(a.upside()).isCloseTo(0.0277585163, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0277585163, within(TOL));
        assertThat(a.riskReward()).isCloseTo(1.0000000000, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(-0.0037011355, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relVolume()).isCloseTo(1.1680644804, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("VENDA", "RSI(14) em 71: momentum esticado"),
                tuple("VENDA", "Percentil 98% na janela: perto das máximas do ciclo"),
                tuple("COMPRA", "MACD: pressão vendedora perdendo força (histograma subindo)"),
                tuple("ALERTA", "Tendência de alta ativa: realizar cedo demais pode cortar lucro; score de venda foi atenuado"));
    }

    @Test void downtrend() {
        Assessment a = assess("downtrend");
        assertThat(a.score()).isEqualTo(40);
        assertThat(a.signal()).isEqualTo(Signal.ACCUMULATE);
        assertThat(a.phase()).isEqualTo(Phase.ACUMULACAO);
        assertThat(a.regime()).isEqualTo(Regime.DOWNTREND);
        assertThat(a.confidence()).isEqualTo(98);
        assertThat(a.risk()).isEqualTo(RiskClass.LOW);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isNull();
        assertThat(a.price()).isCloseTo(63.3975317758, within(TOL));
        assertThat(a.zScore()).isCloseTo(-1.6631140324, within(TOL));
        assertThat(a.percentile()).isCloseTo(1.2048192771, within(TOL));
        assertThat(a.rsi()).isCloseTo(8.6091074207, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-0.0649057285, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.0332022466, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.3196105036, within(TOL));
        assertThat(a.sharpe()).isCloseTo(-12.4077511524, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0044578959, within(TOL));
        assertThat(a.atr()).isCloseTo(0.9544809916, within(TOL));
        assertThat(a.fairValue()).isCloseTo(66.2731162785, within(TOL));
        assertThat(a.entryLow()).isCloseTo(62.4430507841, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(63.6361520237, within(TOL));
        assertThat(a.invalidation()).isCloseTo(61.4885697925, within(TOL));
        assertThat(a.target1()).isCloseTo(64.7630198537, within(TOL));
        assertThat(a.target2()).isCloseTo(67.1015809091, within(TOL));
        assertThat(a.upside()).isCloseTo(0.0584257625, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0301109827, within(TOL));
        assertThat(a.riskReward()).isCloseTo(1.9403472494, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(0.0259622892, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.1000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relVolume()).isCloseTo(1.1680644804, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("COMPRA", "Z-Score -1.66: preço 1.7σ abaixo da média de 50 barras (desconto estatístico)"),
                tuple("COMPRA", "RSI(14) em 9: região de sobrevenda"),
                tuple("COMPRA", "Percentil 1% na janela: perto das mínimas do ciclo"),
                tuple("COMPRA", "MACD: pressão vendedora perdendo força (histograma subindo)"),
                tuple("ALERTA", "Tendência de baixa: score de compra foi atenuado (queda pode continuar)"));
    }

    @Test void range() {
        Assessment a = assess("range");
        assertThat(a.score()).isEqualTo(61);
        assertThat(a.signal()).isEqualTo(Signal.BUY_DIP_WINDOW);
        assertThat(a.phase()).isEqualTo(Phase.ACUMULACAO);
        assertThat(a.regime()).isEqualTo(Regime.RANGE);
        assertThat(a.confidence()).isEqualTo(97);
        assertThat(a.risk()).isEqualTo(RiskClass.LOW);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isNull();
        assertThat(a.price()).isCloseTo(95.0075248467, within(TOL));
        assertThat(a.zScore()).isCloseTo(-1.7110037965, within(TOL));
        assertThat(a.percentile()).isCloseTo(1.6064257028, within(TOL));
        assertThat(a.rsi()).isCloseTo(8.9491584290, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-0.1353622029, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.0578184610, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.0951975983, within(TOL));
        assertThat(a.sharpe()).isCloseTo(-0.3556151848, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0049288216, within(TOL));
        assertThat(a.atr()).isCloseTo(1.4329562144, within(TOL));
        assertThat(a.fairValue()).isCloseTo(100.8790377799, within(TOL));
        assertThat(a.entryLow()).isCloseTo(93.5745686323, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(95.3657639003, within(TOL));
        assertThat(a.invalidation()).isCloseTo(92.1416124178, within(TOL));
        assertThat(a.target1()).isCloseTo(97.3675510600, within(TOL));
        assertThat(a.target2()).isCloseTo(101.6378389914, within(TOL));
        assertThat(a.upside()).isCloseTo(0.0697872527, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0301651099, within(TOL));
        assertThat(a.riskReward()).isCloseTo(2.3135089816, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(0.0401347185, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.1000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relVolume()).isCloseTo(1.1680644804, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("COMPRA", "Z-Score -1.71: preço 1.7σ abaixo da média de 50 barras (desconto estatístico)"),
                tuple("COMPRA", "RSI(14) em 9: região de sobrevenda"),
                tuple("COMPRA", "Percentil 2% na janela: perto das mínimas do ciclo"),
                tuple("COMPRA", "MACD: pressão vendedora perdendo força (histograma subindo)"));
    }

    @Test void spike() {
        Assessment a = assess("spike");
        assertThat(a.score()).isEqualTo(-74);
        assertThat(a.signal()).isEqualTo(Signal.SELL_EXIT_SETUP);
        assertThat(a.phase()).isEqualTo(Phase.ESTICADO_REALIZAR);
        assertThat(a.regime()).isEqualTo(Regime.STRESS);
        assertThat(a.confidence()).isEqualTo(64);
        assertThat(a.risk()).isEqualTo(RiskClass.LOW);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isNull();
        assertThat(a.price()).isCloseTo(125.0596449241, within(TOL));
        assertThat(a.zScore()).isCloseTo(4.2914109726, within(TOL));
        assertThat(a.percentile()).isCloseTo(100.0000000000, within(TOL));
        assertThat(a.rsi()).isCloseTo(93.4868729049, within(TOL));
        assertThat(a.macdHist()).isCloseTo(2.7164623196, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.2016986478, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.0392114784, within(TOL));
        assertThat(a.sharpe()).isCloseTo(3.4880077083, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0032805004, within(TOL));
        assertThat(a.atr()).isCloseTo(3.3198966572, within(TOL));
        assertThat(a.fairValue()).isCloseTo(101.4708465182, within(TOL));
        assertThat(a.entryLow()).isCloseTo(121.7397482669, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(125.8896190884, within(TOL));
        assertThat(a.invalidation()).isCloseTo(118.4198516098, within(TOL));
        assertThat(a.target1()).isCloseTo(128.3795415813, within(TOL));
        assertThat(a.target2()).isCloseTo(131.6994382385, within(TOL));
        assertThat(a.upside()).isCloseTo(0.0530930127, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0530930127, within(TOL));
        assertThat(a.riskReward()).isCloseTo(1.0000000000, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(-0.0261925529, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relVolume()).isCloseTo(1.5567488036, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("VENDA", "Z-Score 4.29: preço esticado 4.3σ acima da média de 50 barras"),
                tuple("VENDA", "RSI(14) em 93: momentum esticado"),
                tuple("VENDA", "Percentil 100% na janela: perto das máximas do ciclo"),
                tuple("VENDA", "Preço tocou a banda superior de Bollinger (20, 2σ)"),
                tuple("ALERTA", "Regime de estresse: volatilidade de curto prazo muito acima da normal"));
    }

    @Test void fall() {
        Assessment a = assess("fall");
        assertThat(a.score()).isEqualTo(70);
        assertThat(a.signal()).isEqualTo(Signal.HIGH_RISK_WAIT);
        assertThat(a.phase()).isEqualTo(Phase.CAUTELA_EVITAR);
        assertThat(a.regime()).isEqualTo(Regime.STRESS);
        assertThat(a.confidence()).isEqualTo(63);
        assertThat(a.risk()).isEqualTo(RiskClass.MEDIUM);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isNull();
        assertThat(a.price()).isCloseTo(66.3420431289, within(TOL));
        assertThat(a.zScore()).isCloseTo(-3.6213094893, within(TOL));
        assertThat(a.percentile()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.rsi()).isCloseTo(4.1195358950, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-3.2055444779, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.2795829690, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.3495866884, within(TOL));
        assertThat(a.sharpe()).isCloseTo(-4.2422255737, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0512932944, within(TOL));
        assertThat(a.atr()).isCloseTo(3.5925359685, within(TOL));
        assertThat(a.fairValue()).isCloseTo(96.4917485623, within(TOL));
        assertThat(a.entryLow()).isCloseTo(66.3420431289, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(67.2401771210, within(TOL));
        assertThat(a.invalidation()).isCloseTo(59.1569711919, within(TOL));
        assertThat(a.target1()).isCloseTo(91.1171057699, within(TOL));
        assertThat(a.target2()).isCloseTo(113.4754578762, within(TOL));
        assertThat(a.upside()).isCloseTo(0.7104607052, within(TOL));
        assertThat(a.downside()).isCloseTo(0.1083034468, within(TOL));
        assertThat(a.riskReward()).isCloseTo(6.5599085382, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(0.4921235980, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relVolume()).isCloseTo(1.4833505899, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("COMPRA", "Z-Score -3.62: preço 3.6σ abaixo da média de 50 barras (desconto estatístico)"),
                tuple("COMPRA", "RSI(14) em 4: região de sobrevenda"),
                tuple("COMPRA", "Percentil 0% na janela: perto das mínimas do ciclo"),
                tuple("COMPRA", "Preço tocou a banda inferior de Bollinger (20, 2σ)"),
                tuple("ALERTA", "Faca caindo: queda acelerada com volatilidade 2.7x a normal ou choque macro; aguardar estabilização"));
    }

    @Test void dip() {
        Assessment a = assess("dip");
        assertThat(a.score()).isEqualTo(41);
        assertThat(a.signal()).isEqualTo(Signal.ACCUMULATE);
        assertThat(a.phase()).isEqualTo(Phase.ACUMULACAO);
        assertThat(a.regime()).isEqualTo(Regime.DOWNTREND);
        assertThat(a.confidence()).isEqualTo(97);
        assertThat(a.risk()).isEqualTo(RiskClass.LOW);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isNull();
        assertThat(a.price()).isCloseTo(89.9532861554, within(TOL));
        assertThat(a.zScore()).isCloseTo(-2.1311275410, within(TOL));
        assertThat(a.percentile()).isCloseTo(1.2048192771, within(TOL));
        assertThat(a.rsi()).isCloseTo(14.5748742470, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-0.6645243373, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.0801551508, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.1354049837, within(TOL));
        assertThat(a.sharpe()).isCloseTo(-4.1623545479, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0123900324, within(TOL));
        assertThat(a.atr()).isCloseTo(1.8802274416, within(TOL));
        assertThat(a.fairValue()).isCloseTo(98.5818362265, within(TOL));
        assertThat(a.entryLow()).isCloseTo(88.0730587139, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(90.4233430158, within(TOL));
        assertThat(a.invalidation()).isCloseTo(86.1928312723, within(TOL));
        assertThat(a.target1()).isCloseTo(95.3774081920, within(TOL));
        assertThat(a.target2()).isCloseTo(103.7635476938, within(TOL));
        assertThat(a.upside()).isCloseTo(0.1535270375, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0418045304, within(TOL));
        assertThat(a.riskReward()).isCloseTo(3.6724976014, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(0.0825565679, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.1000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relVolume()).isCloseTo(0.8568757257, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("COMPRA", "Z-Score -2.13: preço 2.1σ abaixo da média de 50 barras (desconto estatístico)"),
                tuple("COMPRA", "RSI(14) em 15: região de sobrevenda"),
                tuple("COMPRA", "Percentil 1% na janela: perto das mínimas do ciclo"),
                tuple("COMPRA", "MACD: pressão vendedora perdendo força (histograma subindo)"),
                tuple("ALERTA", "Tendência de baixa: score de compra foi atenuado (queda pode continuar)"));
    }

    @Test void stressMacro() {
        Assessment a = assess("stressMacro");
        assertThat(a.score()).isEqualTo(52);
        assertThat(a.signal()).isEqualTo(Signal.HIGH_RISK_WAIT);
        assertThat(a.phase()).isEqualTo(Phase.CAUTELA_EVITAR);
        assertThat(a.regime()).isEqualTo(Regime.STRESS);
        assertThat(a.confidence()).isEqualTo(71);
        assertThat(a.risk()).isEqualTo(RiskClass.MEDIUM);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isNull();
        assertThat(a.price()).isCloseTo(95.0075248467, within(TOL));
        assertThat(a.zScore()).isCloseTo(-1.7110037965, within(TOL));
        assertThat(a.percentile()).isCloseTo(1.6064257028, within(TOL));
        assertThat(a.rsi()).isCloseTo(8.9491584290, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-0.1353622029, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.0578184610, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.0951975983, within(TOL));
        assertThat(a.sharpe()).isCloseTo(-0.3556151848, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0049288216, within(TOL));
        assertThat(a.atr()).isCloseTo(1.4329562144, within(TOL));
        assertThat(a.fairValue()).isCloseTo(100.8790377799, within(TOL));
        assertThat(a.entryLow()).isCloseTo(93.5745686323, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(95.3657639003, within(TOL));
        assertThat(a.invalidation()).isCloseTo(92.1416124178, within(TOL));
        assertThat(a.target1()).isCloseTo(97.3675510600, within(TOL));
        assertThat(a.target2()).isCloseTo(101.6378389914, within(TOL));
        assertThat(a.upside()).isCloseTo(0.0697872527, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0301651099, within(TOL));
        assertThat(a.riskReward()).isCloseTo(2.3135089816, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(0.0371361476, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relVolume()).isCloseTo(1.1680644804, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("COMPRA", "Z-Score -1.71: preço 1.7σ abaixo da média de 50 barras (desconto estatístico)"),
                tuple("COMPRA", "RSI(14) em 9: região de sobrevenda"),
                tuple("COMPRA", "Percentil 2% na janela: perto das mínimas do ciclo"),
                tuple("COMPRA", "MACD: pressão vendedora perdendo força (histograma subindo)"),
                tuple("ALERTA", "Faca caindo: queda acelerada com volatilidade 0.3x a normal ou choque macro; aguardar estabilização"),
                tuple("ALERTA", "Risco macro/geopolítico elevado (90/100) reduz o score e a confiança"));
    }

    @Test void knifeMacro() {
        Assessment a = assess("knifeMacro");
        assertThat(a.score()).isEqualTo(60);
        assertThat(a.signal()).isEqualTo(Signal.HIGH_RISK_WAIT);
        assertThat(a.phase()).isEqualTo(Phase.CAUTELA_EVITAR);
        assertThat(a.regime()).isEqualTo(Regime.STRESS);
        assertThat(a.confidence()).isEqualTo(56);
        assertThat(a.risk()).isEqualTo(RiskClass.HIGH);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isNull();
        assertThat(a.price()).isCloseTo(74.9986839942, within(TOL));
        assertThat(a.zScore()).isCloseTo(-3.5982958472, within(TOL));
        assertThat(a.percentile()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.rsi()).isCloseTo(4.5220484699, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-2.2253820499, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.1833227695, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.2647175136, within(TOL));
        assertThat(a.sharpe()).isCloseTo(-4.4748794223, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0280948913, within(TOL));
        assertThat(a.atr()).isCloseTo(2.9452096217, within(TOL));
        assertThat(a.fairValue()).isCloseTo(97.2146962121, within(TOL));
        assertThat(a.entryLow()).isCloseTo(74.9986839942, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(75.7349863996, within(TOL));
        assertThat(a.invalidation()).isCloseTo(69.1082647508, within(TOL));
        assertThat(a.target1()).isCloseTo(92.9244748946, within(TOL));
        assertThat(a.target2()).isCloseTo(108.7869655990, within(TOL));
        assertThat(a.upside()).isCloseTo(0.4505183265, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0785403014, within(TOL));
        assertThat(a.riskReward()).isCloseTo(5.7361420654, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(0.2918007382, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relVolume()).isCloseTo(0.8568757257, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("COMPRA", "Z-Score -3.60: preço 3.6σ abaixo da média de 50 barras (desconto estatístico)"),
                tuple("COMPRA", "RSI(14) em 5: região de sobrevenda"),
                tuple("COMPRA", "Percentil 0% na janela: perto das mínimas do ciclo"),
                tuple("COMPRA", "Preço tocou a banda inferior de Bollinger (20, 2σ)"),
                tuple("ALERTA", "Faca caindo: queda acelerada com volatilidade 2.7x a normal ou choque macro; aguardar estabilização"),
                tuple("ALERTA", "Risco macro/geopolítico elevado (90/100) reduz o score e a confiança"));
    }

    @Test void benchBehind() {
        Assessment a = assess("benchBehind");
        assertThat(a.score()).isEqualTo(-24);
        assertThat(a.signal()).isEqualTo(Signal.TAKE_PROFIT_WINDOW);
        assertThat(a.phase()).isEqualTo(Phase.ESTICADO_REALIZAR);
        assertThat(a.regime()).isEqualTo(Regime.UPTREND);
        assertThat(a.confidence()).isEqualTo(84);
        assertThat(a.risk()).isEqualTo(RiskClass.LOW);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isCloseTo(0.1908947875, within(TOL));
        assertThat(a.price()).isCloseTo(155.1732923063, within(TOL));
        assertThat(a.zScore()).isCloseTo(1.2703616985, within(TOL));
        assertThat(a.percentile()).isCloseTo(97.5903614458, within(TOL));
        assertThat(a.rsi()).isCloseTo(70.6688939720, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-0.2656703053, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.0440434432, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.0114994500, within(TOL));
        assertThat(a.sharpe()).isCloseTo(8.0335543035, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0024408262, within(TOL));
        assertThat(a.atr()).isCloseTo(2.1536901784, within(TOL));
        assertThat(a.fairValue()).isCloseTo(150.9080202615, within(TOL));
        assertThat(a.entryLow()).isCloseTo(153.0196021279, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(155.7117148509, within(TOL));
        assertThat(a.invalidation()).isCloseTo(150.8659119495, within(TOL));
        assertThat(a.target1()).isCloseTo(157.3269824847, within(TOL));
        assertThat(a.target2()).isCloseTo(159.4806726631, within(TOL));
        assertThat(a.upside()).isCloseTo(0.0277585163, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0277585163, within(TOL));
        assertThat(a.riskReward()).isCloseTo(1.0000000000, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(-0.0044413626, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(-0.1061261172, within(TOL));
        assertThat(a.relVolume()).isCloseTo(1.1680644804, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("VENDA", "RSI(14) em 71: momentum esticado"),
                tuple("VENDA", "Percentil 98% na janela: perto das máximas do ciclo"),
                tuple("COMPRA", "MACD: pressão vendedora perdendo força (histograma subindo)"),
                tuple("ALERTA", "Tendência de alta ativa: realizar cedo demais pode cortar lucro; score de venda foi atenuado"),
                tuple("ALERTA", "Força relativa fraca: -10.6 pp frente ao benchmark em 60 barras"));
    }

    @Test void benchAhead() {
        Assessment a = assess("benchAhead");
        assertThat(a.score()).isEqualTo(-18);
        assertThat(a.signal()).isEqualTo(Signal.HOLD_RIDE_TREND);
        assertThat(a.phase()).isEqualTo(Phase.TENDENCIA_ATIVA);
        assertThat(a.regime()).isEqualTo(Regime.UPTREND);
        assertThat(a.confidence()).isEqualTo(84);
        assertThat(a.risk()).isEqualTo(RiskClass.LOW);
        assertThat(a.periodsPerYear()).isEqualTo(252);
        assertThat(a.beta()).isCloseTo(0.1908947875, within(TOL));
        assertThat(a.price()).isCloseTo(155.1732923063, within(TOL));
        assertThat(a.zScore()).isCloseTo(1.2703616985, within(TOL));
        assertThat(a.percentile()).isCloseTo(97.5903614458, within(TOL));
        assertThat(a.rsi()).isCloseTo(70.6688939720, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-0.2656703053, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.0440434432, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.0114994500, within(TOL));
        assertThat(a.sharpe()).isCloseTo(8.0335543035, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0024408262, within(TOL));
        assertThat(a.atr()).isCloseTo(2.1536901784, within(TOL));
        assertThat(a.fairValue()).isCloseTo(150.9080202615, within(TOL));
        assertThat(a.entryLow()).isCloseTo(153.0196021279, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(155.7117148509, within(TOL));
        assertThat(a.invalidation()).isCloseTo(150.8659119495, within(TOL));
        assertThat(a.target1()).isCloseTo(157.3269824847, within(TOL));
        assertThat(a.target2()).isCloseTo(159.4806726631, within(TOL));
        assertThat(a.upside()).isCloseTo(0.0277585163, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0277585163, within(TOL));
        assertThat(a.riskReward()).isCloseTo(1.0000000000, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(-0.0033310220, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0613268589, within(TOL));
        assertThat(a.relVolume()).isCloseTo(1.1680644804, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("VENDA", "RSI(14) em 71: momentum esticado"),
                tuple("VENDA", "Percentil 98% na janela: perto das máximas do ciclo"),
                tuple("COMPRA", "MACD: pressão vendedora perdendo força (histograma subindo)"),
                tuple("ALERTA", "Tendência de alta ativa: realizar cedo demais pode cortar lucro; score de venda foi atenuado"),
                tuple("COMPRA", "Força relativa: +6.1 pp acima do benchmark em 60 barras"));
    }

    @Test void crypto() {
        Assessment a = assess("crypto");
        assertThat(a.score()).isEqualTo(40);
        assertThat(a.signal()).isEqualTo(Signal.ACCUMULATE);
        assertThat(a.phase()).isEqualTo(Phase.ACUMULACAO);
        assertThat(a.regime()).isEqualTo(Regime.DOWNTREND);
        assertThat(a.confidence()).isEqualTo(98);
        assertThat(a.risk()).isEqualTo(RiskClass.LOW);
        assertThat(a.periodsPerYear()).isEqualTo(365);
        assertThat(a.beta()).isNull();
        assertThat(a.price()).isCloseTo(63.3975317758, within(TOL));
        assertThat(a.zScore()).isCloseTo(-1.6631140324, within(TOL));
        assertThat(a.percentile()).isCloseTo(1.2048192771, within(TOL));
        assertThat(a.rsi()).isCloseTo(8.6091074207, within(TOL));
        assertThat(a.macdHist()).isCloseTo(-0.0649057285, within(TOL));
        assertThat(a.annVol()).isCloseTo(0.0399589100, within(TOL));
        assertThat(a.maxDrawdown()).isCloseTo(0.3196105036, within(TOL));
        assertThat(a.sharpe()).isCloseTo(-14.9327308234, within(TOL));
        assertThat(a.var95()).isCloseTo(0.0044578959, within(TOL));
        assertThat(a.atr()).isCloseTo(0.9544809916, within(TOL));
        assertThat(a.fairValue()).isCloseTo(66.2731162785, within(TOL));
        assertThat(a.entryLow()).isCloseTo(62.4430507841, within(TOL));
        assertThat(a.entryHigh()).isCloseTo(63.6361520237, within(TOL));
        assertThat(a.invalidation()).isCloseTo(61.4885697925, within(TOL));
        assertThat(a.target1()).isCloseTo(64.7630198537, within(TOL));
        assertThat(a.target2()).isCloseTo(67.1015809091, within(TOL));
        assertThat(a.upside()).isCloseTo(0.0584257625, within(TOL));
        assertThat(a.downside()).isCloseTo(0.0301109827, within(TOL));
        assertThat(a.riskReward()).isCloseTo(1.9403472494, within(TOL));
        assertThat(a.expectedValue()).isCloseTo(0.0259622892, within(TOL));
        assertThat(a.suggestedExposure()).isCloseTo(0.1000000000, within(TOL));
        assertThat(a.relStrength()).isCloseTo(0.0000000000, within(TOL));
        assertThat(a.relVolume()).isCloseTo(1.1680644804, within(TOL));
        assertThat(a.factors()).extracting(Factor::side, Factor::text).containsExactly(
                tuple("COMPRA", "Z-Score -1.66: preço 1.7σ abaixo da média de 50 barras (desconto estatístico)"),
                tuple("COMPRA", "RSI(14) em 9: região de sobrevenda"),
                tuple("COMPRA", "Percentil 1% na janela: perto das mínimas do ciclo"),
                tuple("COMPRA", "MACD: pressão vendedora perdendo força (histograma subindo)"),
                tuple("ALERTA", "Tendência de baixa: score de compra foi atenuado (queda pode continuar)"));
    }
}
