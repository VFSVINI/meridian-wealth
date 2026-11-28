# ADR 039 — Geopolítica: mapa de risco por país a partir das manchetes

- **Status:** aceito
- **Data:** 2026-11-27
- **Issue:** #94 (item #51 do roadmap)

## Contexto

A página Geopolítica mostrava o tradutor e o radar macro em texto. O protótipo Cosmos pede um mapa-múndi com o risco por país, o caminho "evento → expectativa → preço → carteira", quatro termômetros e notícias com link para a matéria. Não existe fonte gratuita e oficial de "risco por país" em tempo real; inventar uma nota violaria o ADR 004.

## Decisão

1. **Risco por país vem das manchetes reais**, as mesmas que já alimentavam o radar (RSS e Finnhub). `GeoRegions` associa cerca de 75 países a termos (nomes, gentílicos e capitais), com fronteira de palavra para evitar "Niger" em "Nigeria" ou o pronome "us". O risco do país é a soma do risco das manchetes que o citam dividida por duas manchetes fortes, limitada a 1 (`GeoRiskScorer.countryRisks`, calculado sobre **todas** as manchetes pontuadas, não só as oito mostradas). O método é publicado na tela e **não é um índice oficial**.
2. **País sem manchete é "não informado"**, nunca risco zero.
3. **Links só http e https** (`safeLink`), abertos com `rel="noopener noreferrer"`; fonte sem link diz isso em vez de inventar um. Mostramos título, fonte e horário; o texto completo fica na origem.
4. **Variação do dia** do VIX e do dólar (DXY) vem do Yahoo (preço atual sobre o último fechamento de um dia anterior em Nova York); sem fechamento anterior, a variação fica "não informada".
5. **Mapa** a partir do Natural Earth 110m (domínio público), reduzido por `scripts/build-countries.py` a 176 países e 163 KB (hash conferido em teste, sem Antártida). A projeção Natural Earth é calculada em JS; nada de biblioteca nova nem de build.
6. A página antiga (tradutor e radar da carteira) continua, recolhida.

## Consequências

- A cobertura depende das manchetes do momento: poucos países aparecem de cada vez, e a tela diz isso.
- Palavras-chave são um método simples e sujeito a erro (ironia, negação); por isso o rótulo "segundo as manchetes" e o link para a matéria.
- Acrescentar um país é uma linha em `GeoRegions`; o teste confere que o código existe no arquivo do mapa.
