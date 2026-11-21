# ADR 011 — Tradutor geopolítico e teses institucionais genéricas

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #23 (item #10 do roadmap)

## Contexto

Manchetes geopolíticas mexem com a carteira por caminhos que o investidor não vê, e a lente de grandes gestoras ajuda a pensar.
Os riscos do recurso: parecer previsão, parecer informação privilegiada ou atribuir posições reais a instituições que nunca as
divulgaram naquele formato, e dar a uma contagem de palavras-chave um ar de precisão que ela não tem.

## Decisão

1. **Tradutor descritivo sobre dado real:** a pressão de cada setor vem das manchetes reais do radar macro (a mesma contagem de
   palavras-chave do `GeoRiskScorer`) e é cruzada com o peso do setor na carteira informada. A resposta mostra exposição e
   contribuição (peso x pressão), com as manchetes que a causam. Não prevê preço e não diz o que fazer.
2. **Limites declarados na resposta e na tela:** a pressão é contagem de palavras, só cinco setores são medidos, o setor da
   posição vem do universo do painel e posição só por tipo fica "não informado" (fora da pressão média, com a cobertura
   declarada). Sem macro disponível, nada é calculado nem estimado.
3. **Teses genéricas, sem posições:** cinco lentes didáticas (equilíbrio entre cenários, megatendências, sinais estatísticos e
   liquidez, macro de juros e câmbio, valor de longo prazo), cada uma com premissas, grupos sensíveis e o que a invalidaria. A
   "inspiração" é uma linha sobre a abordagem que a gestora descreve de si publicamente; nenhuma posição, retorno ou decisão é
   atribuído a ninguém, e o aviso diz que não foram revisadas pelas gestoras.
4. **Efeito condicional:** cada grupo sensível é "favorecido" ou "prejudicado" **se a premissa se confirmar**; a leitura mostra
   quanto da carteira cai em cada grupo e quanto fica fora da tese. Não há nota nem veredito.
5. **Testes determinísticos:** manchetes fixas por `GeoRiskScorer` e carteiras pequenas, com os números conferidos à mão; um teste
   varre os textos das teses contra frases que sugeririam posição real ou recomendação.
6. **Mesmas defesas de entrada do simulador:** `MoneyBounds`, no máximo 50 posições, índices recusados, ativo fora do universo
   recusado; a carteira não é gravada nem registrada em log.

## Consequências

- **Positivas:** o investidor vê onde o noticiário encosta na carteira e escolhe uma lente com premissas explícitas e condição
  de invalidação; a promessa de dados reais e de não atribuir posições é verificada por teste.
- **Negativas:** a pressão por palavras-chave é grosseira e mapeia poucos setores; a caracterização das gestoras é simplificada
  e não foi validada por elas; as teses não medem o ajuste ao perfil nem à correlação.

## Alternativas rejeitadas

- **Atribuir posições reais (13F, informes) a cada gestora:** exigiria fonte pública datada por gestora e traria o risco de
  parecer cópia de carteira; fica fora do escopo e vedado pelo roadmap.
- **Dar nota de aderência à tese:** transformaria uma lente em recomendação.
- **Análise de sentimento por modelo de linguagem nas manchetes:** o resultado não seria reprodutível nem testável com cenários
  determinísticos.
