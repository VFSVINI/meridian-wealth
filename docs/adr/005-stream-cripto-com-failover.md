# ADR 005 — Stream de cripto com failover e anualização por √365

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #4

## Contexto

Criptoativos negociam 24 horas por dia, 7 dias por semana, e são a única classe em que o tempo real é gratuito e confiável. Três pontos precisavam de decisão:

1. **Fonte do preço.** A Binance é a maior bolsa e publica um stream público, mas **bloqueia por região** (HTTP 451 no handshake), inclusive em provedores de nuvem sediados em países restritos. O deploy gratuito do roadmap (Hugging Face Spaces) pode cair nesse caso. A Coinbase Exchange e a Kraken têm WebSockets públicos sem esse bloqueio.
2. **Calendário e anualização.** O motor tratava toda barra como dia de pregão (252 por ano). Para cripto o ano tem 365 barras, e a barra diária fecha à meia-noite UTC. A regra de "barra nova" herdada (corte fixo de 23 horas) abria uma barra falsa às 23:30 UTC do mesmo dia.
3. **Risco relativo.** Altcoins se movem com o Bitcoin; o beta contra o BTC é a medida de risco mais usada para esse mercado.

## Decisão

1. **Três bolsas em ordem de preferência: Binance, Coinbase Exchange, Kraken.** Cada uma é uma `CryptoVenue` com URL, assinatura e parser próprios, validados contra mensagens reais gravadas.
2. **Failover dirigido por uma máquina de estados (`CryptoStreamManager`, tick de 1 s):**
   - HTTP **451 ou 403** no handshake isola a bolsa por **30 min** e o stream passa à próxima;
   - falha de rede: backoff de 1 s a 60 s, e **3 falhas seguidas** isolam a bolsa por 5 min;
   - conexão aberta **sem nenhuma mensagem por 20 s** é derrubada e conta como falha;
   - quando a bolsa preferida volta a ficar disponível, **a nova conexão abre antes de a antiga fechar**, e mensagens tardias da antiga são descartadas.
3. **Honestidade da fonte:** cada preço sai com o nome da bolsa que o gerou ("Binance WS", "Coinbase WS" ou "Kraken WS"), com selo de tempo real, e o status mostra a bolsa em uso e o motivo de cada isolamento.
4. **O histórico diário continua vindo da Yahoo** (símbolos `BTC-USD` etc., 731 barras em UTC); o stream só atualiza a barra corrente. O stream nunca cria uma série sozinho.
5. **Polling REST suspenso enquanto o stream entrega** (30 s de tolerância), para a fonte exibida não alternar entre o stream e a Yahoo.
6. **Base de anualização por ativo:** `Instrument.periodsPerYear()` devolve 365 para cripto e 252 para os demais; a volatilidade, a volatilidade RMS, o Sharpe e o backtest usam essa base. Barras de séries em UTC abrem à meia-noite UTC, e a regra de barra nova compara o **dia local da bolsa**.
7. **Beta de 90 barras contra o benchmark do ativo** (BTC para as altcoins), com o benchmark alinhado às barras do ativo pela data local, não pelo fim da série.

## Consequências

- **Positivas:** o preço de cripto chega a cada segundo (Binance) ou a cada negócio (Coinbase e Kraken) mesmo se uma bolsa bloquear o servidor; a transição é automática e visível; a volatilidade de cripto não fica subestimada em √(252/365) ≈ 17%.
- **Negativas:**
  - A Binance cota em **USDT** e as outras duas em **USD**; numa troca de bolsa o preço dá um degrau igual à diferença entre as duas cotações (observada na casa de centésimos de ponto percentual). O histórico da Yahoo é em USD.
  - O miniTicker da Binance é o último preço de cada segundo, não cada negócio.
  - Três conexões possíveis aumentam a superfície de manutenção (formatos mudam sem aviso).
- **Riscos:** o caminho do HTTP 451 foi validado só com recusas simuladas no handshake. De onde o teste foi feito (Brasil) nenhuma das três bolsas bloqueou, então o bloqueio real ainda não foi observado.

## Alternativas consideradas

- **Só Coinbase (USD, sem bloqueio):** mais simples, mas o roadmap exige a Binance como fonte principal e a Coinbase negocia menos pares.
- **Polling REST de uma bolsa:** a Yahoo atualiza cripto quase em tempo real, mas o selo é "atrasado" e há limite de cota; o stream entrega o preço no momento do negócio.
- **Histórico pela Binance REST (klines):** responde daqui, mas sofre o mesmo bloqueio regional do stream; a Yahoo dá o mesmo histórico em USD sem esse risco.
