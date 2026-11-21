# Tradutor geopolítico e teses institucionais genéricas

Item #10 do roadmap. Decisões em [ADR 011](adr/011-tradutor-geopolitico-e-teses-genericas.md).

> **Aviso:** as teses são ilustrativas e genéricas, inspiradas no estilo público de cada gestora. Não representam posições reais de
> nenhuma instituição, não foram revisadas por elas e não são recomendação de investimento.

## API

- `GET /api/thesis`: catálogo com as cinco teses (premissas, grupos sensíveis, invalidações) e o aviso.
- `POST /api/thesis/lens`: recebe `{"positions":[{"symbol":"PETR4","valueBrl":"6000"},{"type":"TESOURO_SELIC","valueBrl":"4000"}]}` e devolve a tradução geopolítica, a leitura de cada tese e o contexto macro. Carteira não é gravada nem logada.

## Tradutor geopolítico

1. Cada manchete do radar macro recebe um risco de 0 a 1 por contagem de palavras-chave e aponta setores (energia, defesa, financeiro, tecnologia, indústria).
2. A **pressão** do setor é a soma do risco das manchetes dele dividida por 3, limitada a 1.
3. O **peso** do setor é a soma dos valores das posições do setor sobre o total da carteira.
4. A **contribuição** é peso x pressão, em pontos percentuais. A **pressão média** é a média das pressões ponderada pelo peso, só sobre as posições com setor conhecido.
5. Posições informadas só pelo tipo ficam "não informado" (sem setor) e fora da média; sem dado macro, nada é calculado.

## Teses

| Tese | Inspiração (abordagem que a gestora descreve de si) | Grupos sensíveis |
|---|---|---|
| Equilíbrio entre cenários | paridade de risco (Bridgewater) | renda fixa pública, commodities e ouro, ações |
| Megatendências estruturais | temas de longo prazo (BlackRock) | tecnologia, energia, mineração |
| Sinais estatísticos e liquidez | quantitativo sistemático (Renaissance) | ativos líquidos, ativos de baixa liquidez |
| Macro: juros e câmbio | macro global (Verde/SPX) | pós-fixados, prefixados e IPCA+, câmbio |
| Valor de longo prazo | gestão de valor (Dynamo) | ações de empresas listadas, criptoativos |

A leitura mostra, para a carteira informada, o percentual **favorecido** e **prejudicado** se a premissa se confirmar e o que fica
**fora da tese**. Não há nota nem veredito.

## Limites

- Pressão por palavras-chave em manchetes de terceiros: grosseira, só cinco setores.
- O setor vem do universo do painel; fora dele, "não informado".
- As caracterizações das gestoras são resumos simplificados e não foram validadas por elas; os textos das teses não foram revisados por profissional habilitado.
