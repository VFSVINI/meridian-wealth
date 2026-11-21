# Simulador de impacto pré-ordem e glossário didático

Item #9 do roadmap (Issue #22). Antes de qualquer ordem, mostra o efeito dela na carteira; e explica cada métrica do painel em
português simples.

> Ferramenta analítica e educacional. **A plataforma não executa ordens** e nada aqui é recomendação de investimento.

## Simulador de impacto pré-ordem

`POST /api/impact/pre-order` recebe a carteira (posições em reais), **uma** compra ou venda hipotética e, opcionalmente, o perfil.
A resposta se declara simulação (`simulation: true` e o rótulo "SIMULAÇÃO: não é cotação nem ordem"), traz a carteira antes e
depois e uma lista de achados. Não existe rota que envie ordem.

Posições e ordem se informam por **símbolo do universo do painel** (a volatilidade vem de dado real) ou só pelo **tipo** do ativo
(sem volatilidade). Os valores são os em reais que o usuário digita: **nenhuma cotação é usada**, então a simulação nunca é
confundida com preço.

| Métrica | Definição |
|---|---|
| Maior posição | peso da maior posição, em %, 2 casas (`HALF_EVEN`) |
| HHI | soma dos quadrados dos pesos × 10.000 (0 a 10.000) |
| Posições efetivas | 10.000 / HHI: quantas posições iguais dariam a mesma concentração |
| Volatilidade ponderada | média das volatilidades anualizadas das posições **com dado real**, ponderada pelo peso; limite superior (ignora correlação) |
| Cobertura da volatilidade | fração do valor da carteira com volatilidade |
| Aderência ao perfil | a mesma nota do item #8 (`PortfolioAdherence`), antes e depois |

Achados: maior posição acima de **25%** (limite de atenção do produto, não regra de mercado), mudança de diversificação e de
volatilidade, cobertura parcial do risco, queda ou alta da aderência e compra em classe com meta de 0% no perfil. Mostra o efeito;
**não diz o que comprar ou vender**.

Regras que viram erro 400: venda de ativo que não está na carteira, venda maior que a posição, ativo fora do universo, índice de
referência, valor não positivo, mais de 50 posições, ordem ausente e valor fora de 15 dígitos inteiros e 6 casas decimais
(`MoneyBounds`, a defesa contra `1E5000000` que o item #8 descobriu; agora é compartilhada). Valores em `BigDecimal` (ADR 002);
só a volatilidade entra como `double` do motor e é convertida na fronteira.

**Premissas, ditas na resposta e na tela:** sem custos, corretagem, impostos, spread nem preço de execução; risco de carteira sem
correlação; volatilidade dos últimos 60 períodos.

Os nomes das classes usam "Impact" porque o ArchUnit proíbe `Simulat` em código de produção (dado de mercado sintético só
existe em `src/test`). O recurso não gera dado de mercado: calcula sobre valores do usuário.

## Glossário didático

`GET /api/glossary` e `GET /api/glossary/{id}`: **60 entradas** em 9 grupos, cada uma com "o que é", "como ler" e "quando se
preocupar", mais entradas relacionadas. Cada texto descreve o que **o código calcula**, inclusive onde difere do conceito clássico:

- o **Sharpe** do painel não desconta a taxa livre de risco;
- a **expectativa** e a **exposição sugerida** usam uma "probabilidade" derivada do score (heurística, não frequência medida);
- o **risco geopolítico** é uma contagem de palavras-chave em manchetes;
- a **confiança** mede concordância entre indicadores, não chance de acerto.

Na tela, cada métrica do painel tem um botão "?" que abre a entrada no glossário (diálogo com busca e entradas relacionadas); o botão
"Glossário" do cabeçalho abre a lista completa.

### Como a ligação métrica → glossário é garantida

O `index.html` mapeia rótulos a ids (tabela `GID`). `GlossaryTest` lê essa tabela, confere que **todo id existe** e que **cada
rótulo do painel** (inclusive os rótulos reais das séries do BCB, lidos de `RateSeries`) resolve para uma entrada. Rótulo novo sem
entrada quebra a build.

## Privacidade

Carteira e ordem não são gravadas nem registradas em log.
