package dev.meridianwealth.domain.glossary;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Glossário didático das métricas do painel. Cada entrada explica "o que é", "como ler" e "quando se preocupar" em
 * português simples e descreve <b>o que o código realmente calcula</b> (inclusive onde difere do conceito clássico).
 * É conteúdo educacional, não recomendação. Ids e referências cruzadas são validados na carga da classe.
 */
public final class Glossary {
    public enum Group {
        TIMING("Sinal e timing"),
        RISK("Risco"),
        TECHNICAL("Indicadores técnicos"),
        CONTEXT("Contexto de mercado"),
        FUNDAMENTALS("Fundamentos"),
        BRAZIL("Juros e inflação do Brasil"),
        PORTFOLIO("Carteira e simulação"),
        DATA("Dados e fontes"),
        REPORTS("Relatórios e composição");

        private final String label;

        Group(String label) { this.label = label; }

        public String label() { return label; }
    }

    public record Entry(String id, Group group, String term, String what, String howToRead, String whenToWorry, List<String> related) {
        public Entry {
            related = List.copyOf(related);
        }
    }

    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    private static void add(String id, Group g, String term, String what, String how, String worry, String... related) {
        if (BY_ID.put(id, new Entry(id, g, term, what, how, worry, List.of(related))) != null) throw new IllegalStateException("Id repetido: " + id);
    }

    static {
        add("score", Group.TIMING, "Score do timing",
                "Uma nota de -100 a +100 que soma componentes explícitos: z-score (até 35 pontos), RSI (20), percentil (20), virada do MACD (10), tendência (5) e força relativa (5), menos até 15 pontos de risco macro.",
                "Positivo quer dizer que o preço está esticado para baixo em relação à própria história (zona de desconto); negativo, esticado para cima; perto de zero, sem extremo. Em tendência de alta, notas negativas são suavizadas, e em tendência de baixa as positivas.",
                "Nota alta de compra não é promessa de alta: um preço pode continuar caindo. Em queda livre com volatilidade explosiva o motor troca o sinal por \"Aguardar\".",
                "signal", "zscore", "rsi", "percentile", "macd", "regime");
        add("signal", Group.TIMING, "Sinal",
                "O rótulo que resume o score: 45 ou mais, Janela de compra; de 20 a 44, Acumular; entre -19 e 19, Manter tendência (se o regime for de alta) ou Aguardar; de -44 a -20, Realizar lucro; -45 ou menos, Reduzir ou sair. Quedas livres viram Aguardar.",
                "Descreve onde o preço está na própria história, não o que vai acontecer. É uma leitura analítica, não uma ordem nem uma recomendação.",
                "Sinais mudam com o preço. Se o rótulo troca toda hora, o ativo está sem direção clara.",
                "score", "phase", "confidence");
        add("phase", Group.TIMING, "Fase",
                "Agrupamento do sinal em quatro fases: Acumulação, Tendência ativa, Esticado (realizar lucro) e Cautela (evitar).",
                "É só outra forma de ler o sinal: acumulação e esticado são extremos; tendência ativa é o preço seguindo o regime; cautela é falta de direção ou risco alto.",
                "Cautela com risco Alto ou Extremo pede atenção redobrada a tamanho de posição.",
                "signal", "regime");
        add("regime", Group.TIMING, "Regime",
                "Estado de mercado do ativo: Tendência de alta (preço acima da média de 50 barras e esta acima da de 100), Tendência de baixa (o inverso), Lateralização ou Estresse (volatilidade dos últimos 5 períodos acima de 1,8 vez a de 60, ou risco macro acima de 0,8).",
                "Ajuda a ler o score: em alta, queda é mais provável de ser correção; em baixa, \"barato\" pode continuar barato.",
                "Estresse indica movimentos bruscos: o risco de perda em um dia é maior que o normal do ativo.",
                "score", "volatility", "geo-risk");
        add("confidence", Group.TIMING, "Confiança",
                "De 0 a 100: começa em 40, soma 15 para cada componente (z-score, RSI, percentil, MACD) que aponta na mesma direção do score, e desconta o risco macro e o estresse.",
                "Mede quanto os indicadores concordam entre si, não a chance de o sinal acertar.",
                "Confiança baixa com score alto significa indicadores divergindo: o sinal é frágil.",
                "score");
        add("risk", Group.RISK, "Classe de risco",
                "Índice de 0 a 1: 50% pela volatilidade anualizada (limitada a 60%), 30% pelo risco geopolítico e macro e 20% pelo VaR de 1 barra (limitado a 4%). Abaixo de 0,25: Baixo; até 0,50: Moderado; até 0,75: Alto; acima: Extremo.",
                "Compara o ativo com uma régua fixa do produto: ele não é comparado com outros ativos nem com o seu perfil.",
                "Risco Alto ou Extremo pede posições menores e horizonte mais longo; confira se combina com o seu perfil.",
                "volatility", "var95", "geo-risk");
        add("suggested-exposure", Group.RISK, "Exposição sugerida",
                "Fração Kelly: 25% do valor de Kelly calculado com a \"probabilidade\" derivada do score e com a relação risco/retorno, limitada a 10%. Vale zero quando o score não é positivo ou há queda livre.",
                "É uma ordem de grandeza analítica de quanto do capital um sinal positivo \"suportaria\" segundo a fórmula, nunca o tamanho de uma posição a comprar.",
                "A probabilidade usada é função do score, não uma frequência medida: trate o número com ceticismo.",
                "expected-value", "risk-reward");
        add("entry-zone", Group.TIMING, "Zona de entrada",
                "Faixa de preço calculada: o piso é o maior entre (preço - 1 ATR) e o menor entre a banda inferior de Bollinger e o preço; o teto é o preço + 0,25 ATR.",
                "Mostra a região próxima do preço atual e da banda inferior onde o motor considera o preço \"descontado\".",
                "Se o preço atual está bem acima da zona, o sinal de compra depende de uma queda que pode não vir.",
                "atr", "bollinger");
        add("invalidation", Group.TIMING, "Invalidação",
                "O preço atual menos 2 ATR.",
                "É o nível em que o cenário do sinal perderia o sentido; abaixo dele o movimento deixa de ser ruído normal.",
                "Quanto mais longe do preço, maior a perda possível se o cenário falhar (veja Downside).",
                "atr", "downside");
        add("targets", Group.TIMING, "Alvos 1 e 2",
                "Alvo 1: o maior entre a banda média de Bollinger e o preço + 1 ATR. Alvo 2: o maior entre a banda superior e o alvo 1 + 1 ATR.",
                "São referências de reversão à média, não previsões de preço.",
                "Alvos distantes do preço dependem de uma recuperação longa; confira o Risco/retorno.",
                "bollinger", "atr", "upside");
        add("zscore", Group.TECHNICAL, "Z-score (50)",
                "Quantos desvios-padrão o preço atual está da média das últimas 50 barras.",
                "Perto de 0: preço na média. Acima de +2 ou abaixo de -2: preço bem esticado para um lado.",
                "Preços podem ficar esticados por muito tempo em tendências fortes; o z-score sozinho não indica reversão.",
                "score", "bollinger");
        add("percentile", Group.TECHNICAL, "Percentil",
                "A fração das últimas barras (até 250) em que o preço foi menor que o atual.",
                "100% significa o maior preço da janela; 0%, o menor. Mostra se o preço está no topo ou no fundo da faixa recente.",
                "Percentil alto em ativo que sobe sempre é normal; use junto com o regime.",
                "score", "regime");
        add("rsi", Group.TECHNICAL, "RSI (14)",
                "Índice de força relativa de Wilder, de 0 a 100, que compara os ganhos e as perdas médios das últimas 14 barras.",
                "Valores abaixo de 30 costumam ser chamados de sobrevendido e acima de 70 de sobrecomprado, mas são convenções populares, não leis.",
                "Em tendências fortes o RSI pode ficar muito tempo em extremos sem reverter.",
                "score");
        add("macd", Group.TECHNICAL, "MACD (histograma)",
                "A diferença entre a linha MACD (média móvel exponencial curta menos longa) e a sua linha de sinal.",
                "Histograma positivo e crescendo indica momento de alta; o motor dá peso à virada: negativo que sobe (+10) ou positivo que cai (-10).",
                "Cruzamentos ocorrem tarde e geram falsos sinais em mercado lateral.",
                "score", "regime");
        add("bollinger", Group.TECHNICAL, "Bandas de Bollinger (20, 2σ)",
                "A média de 20 barras com bandas 2 desvios-padrão acima e abaixo.",
                "Preço colado na banda inferior sugere esticado para baixo, e na superior, para cima. Em teoria cerca de 95% dos preços ficam entre as bandas, mas retornos reais têm caudas pesadas.",
                "Tocar a banda não é sinal de reversão: em tendência o preço pode andar sobre ela.",
                "zscore", "targets");
        add("volatility", Group.RISK, "Volatilidade anualizada",
                "O desvio-padrão dos retornos logarítmicos das últimas 60 barras, multiplicado pela raiz de 252 (365 em cripto, que negocia todo dia).",
                "Quanto maior, mais o preço balança. 20% ao ano é um ativo calmo; 60% ou mais, muito agitado. Não distingue alta de queda.",
                "Volatilidade que dispara de repente costuma acompanhar notícias ou estresse de mercado.",
                "risk", "var95", "max-drawdown");
        add("max-drawdown", Group.RISK, "Max drawdown",
                "A maior queda percentual do pico até o vale nas últimas barras (até 250).",
                "Responde à pergunta: no pior momento recente, quanto teria perdido quem comprou no topo?",
                "Se você não aguentaria uma queda desse tamanho, o ativo provavelmente não combina com seu perfil.",
                "volatility", "var95");
        add("sharpe", Group.RISK, "Sharpe (do painel)",
                "O retorno médio por barra dividido pelo desvio-padrão, multiplicado pela raiz do número de barras por ano, nas últimas 120 barras. Diferente do Sharpe clássico, aqui não se desconta a taxa livre de risco.",
                "Maior é melhor: mais retorno por unidade de risco. Como não desconta a Selic, o valor tende a ficar acima do Sharpe clássico.",
                "Janela curta oscila muito: um Sharpe alto em 120 barras não garante o futuro.",
                "volatility");
        add("var95", Group.RISK, "VaR 95% (1 barra)",
                "Valor em risco histórico: nas últimas 120 barras, a perda que só foi superada nos 5% piores dias.",
                "Em 95% dos dias recentes a perda foi menor que esse valor. Não é a perda máxima.",
                "Os 5% piores dias podem ser bem piores que o VaR; ele não mede a cauda.",
                "volatility", "max-drawdown", "risk");
        add("atr", Group.TECHNICAL, "ATR (14)",
                "A média da amplitude real (máxima menos mínima, ajustada por saltos) das últimas 14 barras, em unidades de preço.",
                "Mostra o tamanho típico de um dia de movimento. O motor usa o ATR para montar a zona de entrada, a invalidação e os alvos.",
                "ATR crescendo rápido indica que os movimentos estão ficando maiores.",
                "entry-zone", "invalidation");
        add("fair-value", Group.TECHNICAL, "Valor justo (SMA50)",
                "A média simples dos últimos 50 fechamentos.",
                "É uma referência estatística de preço \"normal\" recente. Não é valor fundamentalista: não olha lucros nem dívida.",
                "Preço longe da média não significa que o ativo vale mais ou menos, só que se afastou do recente.",
                "zscore");
        add("upside", Group.TIMING, "Upside",
                "A distância do preço atual até o Alvo 2, em porcentagem do preço.",
                "É o ganho potencial \"de manual\" se o preço chegar ao alvo.",
                "Alvo é uma referência, não promessa.",
                "targets", "risk-reward");
        add("downside", Group.TIMING, "Downside",
                "A distância do preço atual até a Invalidação, em porcentagem do preço (2 ATR sobre o preço).",
                "É a perda que o cenário aceita antes de ser considerado errado.",
                "Gaps de abertura podem levar o preço além da invalidação.",
                "invalidation", "risk-reward");
        add("risk-reward", Group.TIMING, "Risco/retorno",
                "Upside dividido por Downside.",
                "Acima de 1, o ganho potencial supera a perda aceita; abaixo de 1, o contrário.",
                "Uma relação alta com alvo irreal não ajuda: confira se o alvo é plausível.",
                "upside", "downside", "expected-value");
        add("expected-value", Group.TIMING, "Expectativa",
                "p × Upside - (1 - p) × Downside, com p = 0,5 + score/300, limitado entre 25% e 75%.",
                "Positivo indica que, segundo a fórmula, o ganho esperado supera a perda esperada.",
                "p vem do score, não de uma frequência medida: é uma heurística do produto, não uma probabilidade estatística.",
                "score", "risk-reward", "suggested-exposure");
        add("relative-strength", Group.CONTEXT, "Força relativa (60)",
                "O retorno do ativo menos o retorno do benchmark nas últimas 60 barras, em pontos percentuais.",
                "Positivo: o ativo andou melhor que o seu benchmark. Negativo: andou pior.",
                "Muita diferença para menos pode indicar problema específico do ativo; para mais, que ele já subiu bastante.",
                "benchmark", "beta");
        add("relative-volume", Group.TECHNICAL, "Volume relativo",
                "O volume da última barra dividido pela média do volume das 20 barras anteriores.",
                "1x é volume normal; acima de 2x, interesse fora do comum.",
                "Volume alto com queda forte costuma indicar venda com pressa.",
                "score");
        add("beta", Group.RISK, "Beta (90)",
                "A covariância dos retornos logarítmicos do ativo com os do benchmark dividida pela variância do benchmark, nas últimas 90 barras.",
                "1: acompanha o benchmark. Acima de 1: oscila mais que ele. Abaixo de 1: oscila menos. Negativo: tende a andar ao contrário.",
                "O beta muda com a janela; um valor isolado de 90 barras é instável.",
                "benchmark", "relative-strength");
        add("benchmark", Group.CONTEXT, "Benchmark",
                "O índice de referência do ativo no cadastro (por exemplo, o Ibovespa para ações brasileiras).",
                "Serve de régua para a força relativa e o beta. FIIs não têm benchmark porque comparar com o Ibovespa não representa o setor.",
                "Um benchmark mal escolhido distorce a comparação.",
                "relative-strength", "beta");
        add("vix", Group.CONTEXT, "VIX",
                "Índice da Cboe que resume a volatilidade esperada do S&P 500 para os próximos 30 dias, extraída dos preços de opções. Conhecido como índice do medo.",
                "Valores baixos indicam calma; subidas fortes indicam busca por proteção. O painel o usa como parte do risco macro.",
                "Picos de VIX costumam coincidir com quedas generalizadas.",
                "geo-risk", "regime");
        add("dollar-index", Group.CONTEXT, "Índice do dólar",
                "Um índice que mede o valor do dólar contra uma cesta de moedas de países desenvolvidos.",
                "Dólar forte tende a pressionar moedas emergentes e commodities; dólar fraco, o contrário.",
                "Movimentos bruscos do índice mexem com ativos de países emergentes como o Brasil.",
                "yield-10y");
        add("yield-10y", Group.CONTEXT, "Juros de 10 anos (EUA)",
                "O rendimento do título do Tesouro americano de 10 anos, referência global de taxa livre de risco.",
                "Juros altos tornam a renda fixa mais atraente e pesam sobre ações e ativos de risco.",
                "Altas rápidas de juros costumam pressionar o mercado de ações.",
                "dollar-index");
        add("geo-risk", Group.CONTEXT, "Risco geopolítico",
                "Um indicador do produto de 0 a 100: as manchetes são pontuadas por palavras-chave (guerra, sanções, embargo etc.) e o resultado vira o risco macro usado no score e na classe de risco.",
                "Mais alto indica mais notícias de tensão. É uma contagem de palavras-chave, não uma análise de inteligência.",
                "Manchetes alarmistas inflam o indicador; ele não diz se o evento chega ao seu ativo.",
                "risk", "regime");
        add("backtest", Group.TIMING, "Backtest",
                "A simulação da regra de sinal sobre o histórico real carregado: entra quando o score chega ao limiar de entrada (e o sinal não é Aguardar) e sai no stop técnico ou quando o score cai ao limiar de saída. É uma projeção.",
                "Compare a Estratégia com Comprar e segurar: se a estratégia não ganha com menos queda, a regra não agrega.",
                "Passado não garante futuro; o teste não inclui custos, impostos nem o efeito da ordem no preço.",
                "win-rate", "max-drawdown");
        add("win-rate", Group.TIMING, "Acerto",
                "A fração das operações do backtest que terminaram com retorno positivo.",
                "Acerto alto com poucos ganhos grandes perdidos e perdas grandes pode ainda dar prejuízo; olhe o retorno total.",
                "Poucas operações tornam o acerto pouco confiável.",
                "backtest");
        add("pe-ratio", Group.FUNDAMENTALS, "P/L",
                "O preço da ação dividido pelo lucro por ação.",
                "Quantos anos de lucro atual o preço \"paga\". Compare com empresas do mesmo setor.",
                "P/L muito baixo pode indicar lucro que não se repete; muito alto, expectativa de crescimento forte.",
                "net-margin");
        add("net-margin", Group.FUNDAMENTALS, "Margem líquida",
                "O lucro líquido dividido pela receita, em porcentagem.",
                "Quanto sobra de lucro para cada R$ 100 vendidos.",
                "Margem caindo com receita estável indica custos pressionando.",
                "pe-ratio", "revenue-growth");
        add("revenue-growth", Group.FUNDAMENTALS, "Crescimento de receita",
                "A variação percentual da receita contra o período de comparação informado pela fonte.",
                "Positivo: a empresa vende mais. Compare com o setor e com a inflação.",
                "Receita que cresce com margem que cai pode não gerar lucro maior.",
                "net-margin");
        add("market-cap", Group.FUNDAMENTALS, "Valor de mercado",
                "O preço da ação vezes o número de ações da empresa.",
                "Mostra o tamanho da empresa na bolsa, em bilhões na moeda da cotação.",
                "Empresas pequenas tendem a oscilar mais e ter menos liquidez.",
                "pe-ratio");
        add("sector", Group.CONTEXT, "Setor",
                "A classificação setorial do ativo no cadastro do produto (por exemplo energia, mineração, financeiro).",
                "Ajuda a ligar notícias e riscos macro aos ativos: um conflito, por exemplo, pesa mais sobre o setor de energia.",
                "Ativos do mesmo setor tendem a reagir juntos às mesmas notícias; uma carteira só de um setor é concentrada.",
                "geo-risk", "benchmark");
        add("selic", Group.BRAZIL, "Meta Selic",
                "A taxa básica de juros definida pelo Copom do Banco Central. É a meta para a taxa Selic.",
                "Juros altos favorecem a renda fixa e encarecem o crédito; juros em queda tendem a ajudar a bolsa e os FIIs.",
                "A tela mostra \"vigente em\" a data da última decisão: a meta vale até a próxima reunião.",
                "cdi", "ipca");
        add("cdi", Group.BRAZIL, "CDI",
                "A taxa média dos empréstimos entre bancos, muito próxima da Selic, usada como referência de rendimento da renda fixa.",
                "\"100% do CDI\" é o rendimento de referência do mercado para aplicações de baixo risco.",
                "Compare um investimento com o CDI líquido de imposto e de taxas.",
                "selic");
        add("ipca", Group.BRAZIL, "IPCA",
                "O índice oficial de inflação do Brasil, medido pelo IBGE; o painel mostra o mês de referência e a variação em 12 meses.",
                "Mede quanto os preços subiram. Rendimento menor que o IPCA perde poder de compra.",
                "Inflação alta pressiona os juros e o custo de vida.",
                "selic");
        add("ptax", Group.BRAZIL, "PTAX",
                "A taxa de câmbio de referência do dólar calculada pelo Banco Central a partir das cotações do dia (compra e venda).",
                "É a taxa usada em contratos e em cálculos oficiais; difere do preço que você paga numa corretora.",
                "O dólar de uma operação real inclui spread e tarifas.",
                "dollar-index");
        add("freshness", Group.DATA, "Selo de frescor",
                "Indica a idade da cotação: tempo real, atrasado (com o atraso informado), fechamento ou gravado. Cada cotação mostra fonte, horário e atraso.",
                "Tempo real é o mais novo; fechamento é o último preço do pregão; gravado é o último valor bom guardado.",
                "Cotação atrasada ou gravada não serve para decisão sensível ao minuto.",
                "stale");
        add("stale", Group.DATA, "Desatualizado",
                "Aviso de que o dado passou do prazo esperado sem atualização.",
                "Indica que a fonte não respondeu a tempo ou o dado curado ficou antigo.",
                "Não use um dado desatualizado como se fosse o atual.",
                "freshness");
        add("concentration", Group.PORTFOLIO, "Concentração (maior posição)",
                "A porcentagem do patrimônio na maior posição da carteira.",
                "Quanto maior, mais o resultado depende de um único ativo.",
                "O produto avisa acima de 25% (limite de atenção do produto, não uma regra de mercado).",
                "hhi", "effective-positions");
        add("hhi", Group.PORTFOLIO, "HHI (concentração)",
                "O índice de Herfindahl-Hirschman: a soma dos quadrados das porcentagens de cada posição, de 0 a 10.000.",
                "10.000 é uma posição só; quanto menor, mais espalhado.",
                "Subir após uma compra significa que a carteira ficou mais concentrada.",
                "concentration", "effective-positions");
        add("effective-positions", Group.PORTFOLIO, "Posições efetivas",
                "10.000 dividido pelo HHI: o número de posições de mesmo tamanho que teriam a mesma concentração.",
                "Uma carteira de 10 posições, uma delas com 80%, tem pouco mais de 1 posição efetiva.",
                "Número efetivo bem menor que o de posições reais indica uma posição dominando.",
                "hhi", "concentration");
        add("weighted-volatility", Group.PORTFOLIO, "Volatilidade ponderada",
                "A média das volatilidades anualizadas das posições com dado real, ponderada pelo peso de cada uma. Ignora a correlação entre elas.",
                "É um limite superior para o risco da carteira: a diversificação real reduz o valor.",
                "Se a cobertura for baixa, o número descreve só parte da carteira.",
                "volatility", "volatility-coverage");
        add("volatility-coverage", Group.PORTFOLIO, "Cobertura da volatilidade",
                "A fração do valor da carteira em posições que têm volatilidade calculada de dado real.",
                "100% significa que todas as posições têm volatilidade; menos, que parte (por exemplo, renda fixa sem cotação) ficou de fora.",
                "Com cobertura baixa, não conclua que a carteira é pouco arriscada.",
                "weighted-volatility");
        add("adherence", Group.PORTFOLIO, "Aderência ao perfil",
                "Nota de 0 a 100 de quão perto a carteira está da carteira modelo do perfil: 100 menos metade da soma dos desvios por classe.",
                "100: igual à meta. Cada classe tem tolerância de 5 pontos percentuais.",
                "Aderência caindo depois de uma ordem simulada significa que ela afasta a carteira do perfil.",
                "concentration");
        add("simulation", Group.PORTFOLIO, "Simulação pré-ordem",
                "O cálculo do efeito de uma compra ou venda hipotética na concentração, no risco e na aderência, sobre valores em reais informados por você.",
                "É uma simulação: não é cotação, não é ordem e a plataforma não executa ordens. Não inclui custos, impostos nem preço de execução.",
                "O resultado real depende do preço no momento da ordem e dos custos.",
                "hhi", "adherence");
        add("variation", Group.REPORTS, "Variação do relatório",
                "(valor atual - base) / |base|, em porcentagem, comparando o relatório com o mesmo período do ano anterior, o exercício anterior, a média de meses anteriores ou o mês anterior, conforme a métrica.",
                "Positivo: melhorou contra a base. Os limiares do veredito são ±5% (±1% no valor patrimonial da cota).",
                "Uma variação grande sobre base pequena exagera; olhe os números.",
                "verdict");
        add("verdict", Group.REPORTS, "Veredito do relatório",
                "POSITIVO, NEUTRO ou NEGATIVO, conforme os sinais das métricas do relatório. Prejuízo nunca é POSITIVO; sem base de comparação, não há veredito.",
                "É uma leitura mecânica dos números publicados, com a regra à vista.",
                "Não separa resultado não recorrente nem prevê preço.",
                "variation");
        add("dividend-yield", Group.REPORTS, "Dividend yield do mês (FII)",
                "O valor do dividend yield do Informe Mensal do fundo, como publicado, sem conversão de unidade.",
                "O veredito o compara com a média dos 3 meses anteriores do próprio fundo.",
                "Um mês acima da média pode ser rendimento extraordinário que não se repete.",
                "nav-per-share", "verdict");
        add("nav-per-share", Group.REPORTS, "Valor patrimonial da cota",
                "O patrimônio líquido do fundo dividido pelo número de cotas, como publicado no Informe Mensal.",
                "Compare com o preço de mercado da cota: preço abaixo do valor patrimonial é um desconto sobre o patrimônio contábil.",
                "Queda do valor patrimonial pode refletir reavaliação dos imóveis ou distribuição acima do resultado.",
                "dividend-yield");
        add("abl", Group.REPORTS, "ABL",
                "Área bruta locável: a área do imóvel que pode ser alugada.",
                "Serve para comparar o tamanho dos imóveis de um fundo. O painel mostra a área publicada pela fonte.",
                "Some só valores de mesma fonte e data; a soma da lista pode ser menor que a ABL total do fundo.",
                "vacancy");
        add("vacancy", Group.REPORTS, "Vacância",
                "A parcela da área (física) ou da receita (financeira) que está desocupada, quando a fonte publica.",
                "Quanto menor, mais ocupado o fundo. Onde a fonte não publica, aparece \"não informado\".",
                "Vacância subindo por vários meses é sinal de alerta para a renda do fundo.",
                "abl");
        add("report-deadline", Group.REPORTS, "Prazo de divulgação",
                "O prazo máximo regulatório da CVM para a entrega de cada relatório (ITR em 45 dias, DFP em 3 meses, informe mensal de FII em 15 dias).",
                "É um limite: a divulgação real pode ser anterior. A data de entrega aparece depois que a fonte a registra.",
                "Atraso na entrega de relatório é um sinal de atenção sobre o emissor.",
                "verdict");

        Set<String> ids = BY_ID.keySet();
        Set<String> missing = new HashSet<>();
        BY_ID.values().forEach(e -> e.related().stream().filter(r -> !ids.contains(r)).forEach(r -> missing.add(e.id() + " -> " + r)));
        if (!missing.isEmpty()) throw new IllegalStateException("Referências cruzadas inexistentes: " + missing);
    }

    private Glossary() {}

    public static List<Entry> all() { return List.copyOf(BY_ID.values()); }

    public static Optional<Entry> find(String id) { return Optional.ofNullable(BY_ID.get(id)); }
}
