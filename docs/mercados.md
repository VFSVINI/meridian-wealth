# Mercados e fontes de dados

Referência operacional das bolsas acompanhadas e das fontes de cotação, com o que foi **observado de verdade** contra as APIs reais. Atualize esta página sempre que um provedor mudar de comportamento.

## Bolsas e calendários

| Bolsa | Região | Fuso | Pregão regular (hora local) | Feriados | Fonte do calendário |
|---|---|---|---|---|---|
| NYSE / Nasdaq | EUA | America/New_York | 09:30–16:00; fecha às 13:00 no dia seguinte ao Thanksgiving e em 24/12 | regras implementadas e **conferidas** | [nyse.com/trade/hours-calendars](https://www.nyse.com/trade/hours-calendars) (lido em 2026-11-20) |
| B3 | Brasil | America/Sao_Paulo | 10:00–17:00; abre às 13:00 na Quarta-feira de Cinzas | regras implementadas e **conferidas** (inclui 20/11, feriado nacional desde 2024) | [b3.com.br — calendário de negociação](https://www.b3.com.br/pt_br/solucoes/plataformas/puma-trading-system/para-participantes-e-traders/calendario-de-negociacao/feriados/) (lido em 2026-11-20) |
| Xetra | Europa | Europe/Berlin | 09:00–17:30 | regras implementadas e **conferidas** (Ascensão, Pentecostes e Corpus Christi são dias de pregão) | [cashmarket.deutsche-boerse.com — trading calendar](https://www.cashmarket.deutsche-boerse.com/cash-en/trading/trading-calendar-and-trading-hours) (lido em 2026-11-20) |
| London Stock Exchange | Europa | Europe/London | 08:00–16:30; meio expediente até 12:30 em 24/12 e 31/12 | regras das bank holidays inglesas; **conferidas** só nas datas que a página publica (a partir de 31/08/2026) | [londonstockexchange.com/equities-trading/business-days](https://www.londonstockexchange.com/equities-trading/business-days) |
| Euronext Amsterdam | Europa | Europe/Amsterdam | 09:00–17:30 | **não verificado** | — |
| Tóquio (JPX) | Ásia | Asia/Tokyo | 09:00–11:30 e 12:30–15:30 | **não verificado** | — |
| Hong Kong (HKEX) | Ásia | Asia/Hong_Kong | 09:30–12:00 e 13:00–16:00 | **não verificado** | — |
| ASX | Oceania | Australia/Sydney | 10:00–16:00 | **não verificado** | — |
| Tadawul | Oriente Médio/África | Asia/Riyadh | 10:00–15:00, de domingo a quinta | **não verificado** | — |
| Johannesburgo (JSE) | Oriente Médio/África | Africa/Johannesburg | 09:00–17:00 | **não verificado** | — |
| Mercado global 24h | Global | UTC | domingo 22:00 a sexta 22:00 (câmbio e futuros) | não se aplica | — |

"Não verificado" significa que o sistema conhece fuso, sessões e fim de semana, mas **não** os feriados: em um feriado local, o pregão pode aparecer como aberto. O painel marca essas bolsas com `*` e a API devolve `holidaysVerified: false`. Os testes em `MarketHoursTest` comparam os calendários conferidos dia a dia com as listas oficiais de 2026.

## Fontes de cotação

Medições de 2026-11-20 (sexta-feira, após o fechamento de NYSE e com a B3 em feriado), a partir do Brasil. Latência HTTP é o tempo total da requisição em três medições; o atraso do dado (tempo entre o negócio e a publicação) só pode ser medido com o pregão aberto e fica registrado como pendente.

| Fonte | Uso no sistema | Latência HTTP medida | Atraso nominal do dado | Observado |
|---|---|---|---|---|
| Yahoo (chart v8) | histórico e cotação de ações, índices, câmbio e futuros em todas as regiões | 0,16–0,36 s | ~15 min (fechamento fora do pregão) | respondeu para os 25 ativos; informa bolsa, moeda (inclusive `GBp` e `ZAc`, em centavos) e `regularMarketTime` |
| brapi.dev | histórico diário da B3 (primeira fonte) | 0,34–0,38 s | barra diária (fechamento) | sem token, respondeu para PETR4, VALE3 e ITUB4 (497 barras em 2 anos). Em feriado, `regularMarketTime` trouxe o horário da consulta, não o do último negócio: por isso não é usado para cotação |
| Frankfurter (BCE) | câmbio, failover da Yahoo | 0,16–0,74 s | referência diária | migrou para `api.frankfurter.dev/v1`; o domínio antigo responde 301 |
| Stooq (CSV) | desligado por padrão | — | atrasado / diário | passou a exigir verificação por JavaScript (captcha); `MW_STOOQ_ENABLED=true` religa |
| Finnhub REST | cotação de ações dos EUA (com chave) | não medido | tempo real segundo a documentação | **não validado**: sem chave neste projeto (HTTP 401 sem chave) |
| Finnhub WebSocket | negócios de ações dos EUA (com chave) | não medido | tempo real segundo a documentação | **não validado** ao vivo; parser e ciclo de vida testados no formato da documentação pública |
| Feed pago (Twelve Data) | adapter plugável, desligado | — | declarado pelo plano (`MW_PAID_FEED_FRESHNESS`) | **não ativado**; o selo nunca é presumido como tempo real |

Tempo real verdadeiro de B3 e de bolsas europeias exige feed pago. O adapter `PaidQuoteFeed` permite ligar um provedor contratado sem mudar o restante do sistema.

## Pendências de validação

- Medir o atraso real (negócio → publicação) da Yahoo e do brapi com o pregão aberto.
- Validar a Finnhub (REST e WebSocket) com uma chave gratuita: confirmar o selo "tempo real" na prática.
- Conferir os calendários das bolsas marcadas como "não verificado" contra as páginas oficiais.
