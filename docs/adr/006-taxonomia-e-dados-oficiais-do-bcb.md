# ADR 006 — Taxonomia multi-ativo e dados oficiais do BCB

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #5

## Contexto

A carteira (itens #12 e #13) vai misturar ações, FIIs, fundos, títulos públicos, crédito privado e cripto, e as decisões de preço, risco e suitability dependem de saber **o que cada coisa é**. Hoje o cadastro só tem uma classe textual solta. Além disso, a renda fixa brasileira é medida contra indicadores oficiais (Selic, CDI, IPCA) e o câmbio de referência é a PTAX; sem eles, nenhum retorno de renda fixa faz sentido.

Os dois problemas têm o mesmo risco: **afirmar fatos sobre produtos financeiros sem verificação**. Um atributo errado ("LCI é garantida", "esta nota paga cupom") vira decisão de investimento.

## Decisão

1. **Taxonomia fechada em `AssetType`** (22 tipos), com atributos tipados (categoria, indexador, forma de renda, vencimento, proteção ao crédito, origem do preço). **Só entra atributo definido pelo produto ou verificado em fonte pública**; o que não foi verificado fica de fora (liquidez, imposto) ou como "conforme emissão". A página [taxonomia](../taxonomia.md) lista o que foi lido, onde e quando, e o que **não** foi verificado.
2. **O cadastro falha alto:** `Instrument.type()` lança erro diante de uma classe sem tipo, em vez de adivinhar.
3. **Taxa é `Rate`, em `BigDecimal`** (ADR 002): juros compostos com `DECIMAL128`, arredondamento só na apresentação (`HALF_EVEN`). A API devolve os valores como **texto exato**, com a escala que o BCB publicou (o PTAX sai `5.88890`, não `5.8889`).
4. **Indicadores do BCB pelo SGS com janela de datas**, e o PTAX pelo serviço Olinda (`CotacaoDolarPeriodo`, ordenado e limitado a 1). O modo "últimos N" **não é usado**: na série 432 ele traz datas futuras e a ordem não é garantida.
5. **Cada indicador é independente:** o que falhar mantém o último valor bom; um valor mais antigo nunca substitui um mais novo; indicador sem dado aparece como "sem dado", nunca estimado nem copiado de outro.
6. **Defasagem por periodicidade da série:** séries diárias ficam desatualizadas após 5 dias sem dado novo (cobre fim de semana e feriado prolongado), as mensais após 75 dias (o IPCA tem referência no 1º dia do mês e sai no mês seguinte). O "hoje" é o de **Brasília**.
7. **FIIs entram no universo sem benchmark**: o IFIX tem uma única barra na Yahoo, e comparar um FII com o Ibovespa não representa o setor.

## Consequências

- **Positivas:** a taxonomia dá vocabulário comum à carteira, ao suitability e à composição; os atributos têm origem rastreável; os indicadores oficiais aparecem com data, fonte e situação, sem esconder lacunas; valores exatos evitam o erro de ponto flutuante na renda fixa.
- **Negativas:** a taxonomia é menos rica do que um catálogo comercial (sem liquidez, sem imposto); a sigla dos títulos públicos segue a nomenclatura do Tesouro, mas não foi relida em página oficial (o site respondeu 403); quatro séries do BCB (12, 4389, 433 e 13522) não têm página no portal de dados abertos, e sua semântica foi confirmada por recomposição aritmética, não por documentação.
- **Riscos:** o BCB pode mudar o contrato do SGS ou do Olinda sem aviso. Os testes usam respostas reais gravadas, e o estado "desatualizado" torna a falha visível.

## Alternativas consideradas

- **Atributos "completos" a partir de conhecimento geral** (liquidez, IR, IOF): rejeitado, pela razão do contexto. Entram depois com base legal citada.
- **`double` para taxas:** rejeitado pelo ADR 002; o CDI diário composto por 252 dias só reproduz o CDI anualizado do BCB sem erro com `BigDecimal`.
- **SGS 1 e 10813 como PTAX:** na data conferida os valores coincidiram com o PTAX, mas a fonte oficial da PTAX é o serviço Olinda, e é ele que o sistema usa.
- **Modo "últimos N" do SGS:** mais curto, mas traz datas futuras e ordem imprevisível.
