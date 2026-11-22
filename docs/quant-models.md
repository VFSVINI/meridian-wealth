# Modelos quantitativos

Barras tratadas como diárias (252 por ano).

## Indicadores
- **Z-Score(n=50):** z = (P − SMA) / σ amostral.
- **Percentil:** fração de preços estritamente menores que o último na janela (até 250 barras).
- **RSI(14) de Wilder**, **MACD(12,26,9)**, **Bollinger(20, 2σ)**, **ATR(14)** simples.
- **Volatilidade anualizada:** σ(retornos log, 60 barras) × √252.
- **Choque de volatilidade:** razão entre a raiz da média dos quadrados dos últimos 5 retornos e a das últimas 60 barras (capta quedas direcionais, onde o desvio padrão enganaria).
- **Max drawdown**, **Sharpe** (sem taxa livre de risco), **VaR histórico 95%**.

## Regime
`STRESS` se choque > 1,8 ou risco macro > 0,8; `UPTREND` se P > SMA50 > SMA100; `DOWNTREND` se P < SMA50 < SMA100; senão `RANGE`.

## Score
score = 35·(−z/3) + 20·(50−RSI)/50 + 20·(50−percentil)/50 + MACD(±10) + regime(±5) + força relativa(±5) − 15·riscoMacro, com z limitado a ±3. A força relativa (retorno do ativo menos o do benchmark em 60 barras, SPY ou Euro Stoxx 50) entra como clamp(FR/10%, −1, 1)·5.
Em `UPTREND` o score negativo é multiplicado por 0,7 (evita vender cedo demais); em `DOWNTREND` o positivo é multiplicado por 0,7. Resultado limitado a [-100, 100].

| Score | Sinal / fase |
|---|---|
| ≥ 45 | `BUY_DIP_WINDOW` / Acumulação |
| 20 a 44 | `ACCUMULATE` / Acumulação |
| -19 a 19 | `HOLD_RIDE_TREND` em alta; `HIGH_RISK_WAIT` nos demais regimes |
| -44 a -20 | `TAKE_PROFIT_WINDOW` / Esticado |
| ≤ -45 | `SELL_EXIT_SETUP` / Esticado |

**Faca caindo** (força `HIGH_RISK_WAIT`): z < -1,5 com choque > 1,8 e queda de 5 barras > 3%; ou tendência de baixa com z < -2 e MACD piorando; ou risco macro > 0,85 com z < -1.

## Zonas, assimetria e exposição
- Entrada: de max(P − ATR, banda inferior) a P + 0,25·ATR. Invalidação: P − 2·ATR. Alvos: média de Bollinger (ou P + ATR) e banda superior.
- Upside = (alvo2 − P)/P; downside = (P − invalidação)/P; R/R = upside/downside.
- p(alta) = clamp(0,5 + score/300, 0,25, 0,75); EV = p·upside − (1−p)·downside.
- Exposição: Kelly f* = p − (1−p)/R/R, vezes 0,25, teto de 10%; zero se score ≤ 0 ou faca caindo.
- Confiança: 40 + 15 por componente concordante (Z, RSI, percentil, MACD) − 10·riscoMacro − 20 em estresse.

## Camada macro e geopolítica
- **Risco macro** = 0,5·clamp((VIX−12)/28, 0, 1) + 0,5·risco geopolítico.
- **Risco geopolítico:** cada manchete recebe risco = soma dos pesos das palavras-chave encontradas (guerra, sanções, tarifas, juros, petróleo, ciberataque e outras), limitado a 1, com desconto de 0,6 para palavras de desescalada. Índice = clamp(1,6·média + 0,4·fração de manchetes com risco ≥ 0,5, 0, 1).
- **Impacto por setor:** soma do risco das manchetes de cada setor dividida por 3, limitada a 1. O risco aplicado a um ativo é riscoMacro + 0,25·impacto do setor; o ouro (refúgio) usa 30% do risco macro.
- **Volume relativo:** volume da última barra dividido pela média das 20 anteriores.

## Monte Carlo (item #18)

Projeção patrimonial, **sempre rotulada projeção**, com 5.000 trajetórias mensais: `valor = valor x (1 + retorno do mês) + aporte`. O retorno do mês vem de um de dois modelos:

- **Log-normal** (premissas manuais): `ln(1 + r) = (mu - sigma²/2) dt + sigma sqrt(dt) Z`, com `mu = ln(1 + retorno esperado)`, `dt = 1/12` e `Z` normal padrão (Box-Muller); o retorno esperado composto em 12 meses bate com a premissa.
- **Reamostragem de retornos reais por blocos** (carteira): sorteia uma janela de 21 pregões consecutivos do histórico alinhado por data e compõe o retorno de cada ativo nessa mesma janela, então a correlação do passado é preservada. Pesos fixos pelo valor atual em reais; a parte sem histórico real fica parada.

Saídas: percentis 5, 25, 50, 75 e 95 por mês (interpolação linear entre ordens), histograma de 30 faixas do patrimônio final (até o percentil 99), probabilidade de estar na meta (no fim e no fim de cada ano) e probabilidade de terminar abaixo do investido. O gerador é um `SplittableRandom` com semente explícita: mesma entrada, mesma saída (ADR 020). Limites: valores nominais em reais, sem impostos, custos nem inflação; o passado não garante o futuro; ativos em dólar sem o efeito do câmbio.

