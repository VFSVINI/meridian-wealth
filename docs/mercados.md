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
| Cripto 24/7 | Cripto | UTC | sempre aberto; a barra diária fecha à meia-noite UTC | não se aplica | — |

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

## Cripto (24/7)

BTC, ETH, SOL e XRP. Histórico diário de 2 anos pela Yahoo (`BTC-USD` etc., 731 barras em UTC, a barra de hoje já com o preço atual) e preço ao vivo por WebSocket, com failover automático entre as bolsas ([ADR 005](adr/005-stream-cripto-com-failover.md)). Volatilidade anualizada por **√365**, beta de 90 barras contra o BTC.

| Ordem | Bolsa | Endpoint | Canal | Moeda | Cadência observada | Abertura do handshake |
|---|---|---|---|---|---|---|
| 1 | Binance | `wss://stream.binance.com:9443/stream?streams=<par>@miniTicker` | `miniTicker` (último preço de cada segundo) | USDT | 1 mensagem por par por segundo | 1,85 s |
| 2 | Coinbase Exchange | `wss://ws-feed.exchange.coinbase.com` | `ticker` (a cada negócio) | USD | ~136 ms entre mensagens (2 pares) | 1,70 s |
| 3 | Kraken | `wss://ws.kraken.com/v2` | `ticker` (a cada negócio) | USD | ~405 ms entre mensagens (2 pares) | 1,42 s |

Medido em 2026-11-21 a partir do Brasil, 10 s de captura por bolsa (as mensagens gravadas estão em `src/test/resources/fixtures/crypto`). Nenhuma das três bloqueou o acesso daqui.

Regras do failover (resumo; detalhes no ADR 005):

- HTTP 451 ou 403 no handshake: a bolsa fica isolada por 30 min e o stream passa à próxima.
- Falha de rede: backoff de 1 s a 60 s; 3 falhas seguidas isolam a bolsa por 5 min.
- Conexão sem nenhuma mensagem por 20 s: derrubada e contada como falha.
- A bolsa preferida volta a ser usada sem lacuna: a nova conexão abre antes de a antiga fechar.
- O `/api/status` mostra a bolsa em uso e o estado das outras (conectada, em espera ou isolada, com o motivo e quando volta).

Limites honestos: a Binance cota em USDT e as demais em USD (degrau no preço ao trocar de bolsa); o miniTicker da Binance é o último preço por segundo, não cada negócio; o bloqueio regional real (HTTP 451) ainda **não foi observado**, só simulado nos testes.

## Brasil: juros, inflação e câmbio oficial (BCB)

Selic, CDI, IPCA e PTAX, lidos do Banco Central e exibidos como o BCB publicou, com a data de referência de cada valor (`GET /api/brazil/rates` e o card "Juros e inflação" do painel). Atualização a cada 30 minutos; cada indicador é independente e mantém o último valor bom se a fonte falhar.

| Indicador | Fonte | Unidade | Como o código foi confirmado |
|---|---|---|---|
| Meta Selic | SGS 432 | % a.a. | página oficial "Taxa de juros - Meta Selic definida pelo Copom" no portal de dados abertos do BCB (série diária, % ao ano), lida em 2026-11-20 |
| CDI, taxa diária | SGS 12 | % a.d. | sem página no portal de dados abertos (busca nos 4.261 conjuntos de dados); confirmado por consistência: `(1 + 0,050788%)^252 − 1 = 13,65%`, igual ao SGS 4389 na mesma data |
| CDI anualizado (base 252) | SGS 4389 | % a.a. | idem; coerente com a meta Selic de 13,75% (CDI 0,10 ponto abaixo) |
| IPCA, variação mensal | SGS 433 | % no mês (data = 1º dia do mês de referência) | sem página no portal; os 12 últimos IPCA mensais compostos dão 4,63%, igual ao SGS 13522 |
| IPCA, acumulado em 12 meses | SGS 13522 | % em 12 meses | idem |
| PTAX, dólar (compra e venda) | Olinda `CotacaoDolarPeriodo` | R$ por US$ | `$metadata` oficial do serviço PTAX lista as entidades e os campos (`cotacaoCompra`, `cotacaoVenda`, `dataHoraCotacao`) |

Os conjuntos 11 (Selic diária), 1 (dólar livre, venda), 10813 (dólar livre, compra), 4189 e 4390 (Selic acumulada) existem no portal, mas não são usados. Em 2026-11-19 os valores do SGS 1 e 10813 coincidiram com o PTAX (5,8889 e 5,8883), mas a fonte oficial da PTAX é o Olinda.

### Contrato observado da API (2026-11-20 e 21)

- **Janela de datas:** `dataInicial` e `dataFinal` em `dd/MM/aaaa`. A consulta sem filtro é limitada e a janela máxima é de 10 anos (página oficial do recurso JSON do SGS).
- **Modo "últimos N" (não usado):** a série 432 traz datas futuras (08/12 e 09/12/2026, com a meta vigente repetida) e a 4389 veio em ordem decrescente. O cliente usa janela de datas, descarta data posterior ao corte e ordena.
- **Janela sem dados:** HTTP 404 com `{"erro":{"statusCode":404,"detail":"...Value(s) not found"}}` (um objeto, não uma lista vazia). Data fora do formato: HTTP 400 com `{"erro":"dataInicial invalida..."}`. Série inexistente: HTTP 404 com o texto `A serie nao existe!`.
- **Tipos dos valores:** o SGS devolve o valor como **texto** (`"13.75"`); o PTAX devolve **número decimal com 5 casas** (`5.88890`). Ambos viram `BigDecimal`, sem passar por `double`.
- **PTAX em feriado:** não há cotação no feriado nacional (20/11/2026, Consciência Negra, devolveu lista vazia). O painel mostra o PTAX de 19/11 com essa data.
- **Latência HTTP medida:** SGS 0,26 a 0,80 s (9 séries, uma medição cada); Olinda 0,29 s.

## Pendências de validação

- Medir o atraso real (negócio → publicação) da Yahoo e do brapi com o pregão aberto.
- Validar a Finnhub (REST e WebSocket) com uma chave gratuita: confirmar o selo "tempo real" na prática.
- Conferir os calendários das bolsas marcadas como "não verificado" contra as páginas oficiais.
- Ler a documentação oficial das séries 12, 4389, 433 e 13522, que não têm página no portal de dados abertos (hoje confirmadas por consistência aritmética), e a página de documentação do Olinda, que respondeu como template sem renderizar.
- Validar o failover contra um bloqueio regional real (HTTP 451 da Binance) rodando de um servidor em região bloqueada, por exemplo no deploy do PR #21 do roadmap.
