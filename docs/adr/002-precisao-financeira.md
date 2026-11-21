# ADR 002 — Precisão financeira: fronteira `double` × `BigDecimal`

- **Status:** aceito
- **Data:** 2026-11-20
- **Issue:** #1

## Contexto

Todo o motor estatístico herdado (`Indicators`, `TimingEngine`, backtest) usa `double`. Isso é adequado para estatística: as entradas já são aproximações de mercado, as fórmulas usam raízes, logaritmos e exponenciais, e o desempenho importa em séries longas.

O roadmap introduz carteira, preço médio ponderado, livro de movimentações, conversão BRL/USD e apuração de IR. Nesses pontos `double` é inaceitável: `0.1 + 0.2 != 0.3`, erros se acumulam em somatórios, e um centavo de diferença num DARF ou num saldo é defeito de negócio.

## Decisão

**`double` apenas em estatística e sinais; `BigDecimal` em tudo que é dinheiro ou quantidade contábil.**

| Uso | Tipo | Exemplos |
|---|---|---|
| Estatística, indicadores, scores, sinais e projeções | `double` | RSI, MACD, volatilidade, Sharpe, VaR, correlação, Monte Carlo |
| Dinheiro e contabilidade | `BigDecimal` | preço de execução, preço médio, quantidade, custos, saldo, valor de mercado, IR, proventos, câmbio aplicado |

Regras de implementação:

1. **Value objects** no domínio: `Money` (valor + moeda ISO 4217), `Quantity` e `Rate`. Nada de `BigDecimal` solto atravessando camadas.
2. **Construção** sempre por `new BigDecimal(String)` ou `BigDecimal.valueOf(...)`; **nunca** `new BigDecimal(double)`.
3. **Comparação** com `compareTo`, nunca `equals` (que diferencia `1.0` de `1.00`).
4. **Escala e arredondamento explícitos:** reais com 2 casas e `RoundingMode.HALF_EVEN` (arredondamento bancário) na apresentação e na liquidação; cálculos intermediários com `MathContext.DECIMAL128`; preço médio e quantidades fracionárias (cripto) com até 8 casas.
5. **Divisão** sempre com escala e `RoundingMode`, evitando `ArithmeticException` em dízimas.
6. **Fronteira única:** a conversão `double` → `BigDecimal` acontece em pontos conhecidos (ex.: uma cotação ao entrar na carteira), via `BigDecimal.valueOf(double)`, cobertos por teste. Resultados estatísticos nunca voltam para o razão contábil.
7. **Persistência e API:** colunas `NUMERIC(p, s)` no PostgreSQL (nunca `REAL`/`DOUBLE PRECISION` para dinheiro); JSON serializa `BigDecimal` como string para não perder precisão no JavaScript.

## Consequências

- **Positivas:** saldos, preços médios e impostos exatos e auditáveis; regras de arredondamento documentadas e testáveis.
- **Negativas:** código mais verboso e mais lento que `double` nas partes contábeis (irrelevante no volume esperado).
- **Riscos:** vazamento de `double` em código monetário. Mitigação: value objects, checklist no template de PR e, a partir do PR #12, regra ArchUnit proibindo `double` em assinaturas públicas dos pacotes de carteira e movimentações.

## Alternativas consideradas

- **`long` em centavos:** rápido e exato, mas sofre com moedas de escalas diferentes, frações de cripto (8 casas) e taxas percentuais; exigiria reimplementar o que `BigDecimal` já oferece.
- **`double` em tudo com arredondamento no fim:** rejeitado; o erro se acumula antes do arredondamento.
- **JSR 354 (Moneta):** API rica, mas dependência pesada para o escopo atual. Reavaliar se surgirem muitas moedas e conversões.
