# ADR 042 — Cripto 24h: relógio de 24 horas e medo e ganância com fontes públicas

- **Status:** aceito
- **Data:** 2026-11-28
- **Issue:** #106 (item #54 do roadmap)

## Contexto

O protótipo pede um "relógio de 24 horas" (volume por hora do dia) e o índice de medo e ganância. O projeto não tinha dado por hora nem esse índice. A Yahoo, que já serve o histórico diário, **não serve para o volume horário**: medido em 2026-11-28, 85 das 152 barras horárias do Bitcoin vinham com volume zero. Usar essas barras faria o relógio mostrar horas "sem negociação" que nunca existiram.

## Decisão

1. **Barras horárias reais por bolsa, em ordem:** Coinbase, depois Kraken, depois Binance (servidores em nuvem de algumas regiões são barrados em uma delas, como já tratamos no stream). Todas são públicas e sem chave; o volume vira dólares (Coinbase pelo fechamento, Kraken pelo vwap, Binance pelo volume em USDT). A primeira que responde vence e a resposta diz qual foi.
2. **Domínio puro** (`HourlyVolume`): média do volume de cada hora do dia, no horário de Brasília, sobre as últimas 168 barras completas. Volume zero, negativo ou não numérico é dado que falta e não entra na média; hora sem amostra fica nula; a barra da hora corrente (parcial) fica de fora; menos de 24 barras válidas não dão perfil.
3. **Medo e ganância:** índice público da alternative.me (0 a 100), com o rótulo traduzido e a data; valor fora de 0 a 100 é descartado. A fonte é citada na tela.
4. **Serviço com cache de 15 minutos, último dado bom e espera de 2 minutos depois de falha** (mesmo padrão do histórico do Brasil). O que não chegou vem em `unavailable` e a tela diz "não informado"; nada é estimado.
5. **API:** `GET /api/crypto/insights`. Só o Bitcoin é medido, como termômetro do dia do cripto.
6. **Mais oito moedas** no universo (12 no total), para a "galáxia" ter o que mostrar; o volume de 24 h delas já vinha das cotações.

## Consequências

- Duas dependências externas novas, ambas com falha segura: sem elas, os dois cartões dizem que a fonte não respondeu.
- O relógio mostra o ritmo do Bitcoin, não o de cada moeda.
- Fixtures reais das quatro respostas ficaram em `src/test/resources/fixtures/crypto`.
