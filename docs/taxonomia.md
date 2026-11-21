# Taxonomia multi-ativo

Os 22 tipos de ativo que a plataforma conhece (`AssetType`, exposto em `GET /api/taxonomy`). Cada tipo declara só atributos **definidos pelo próprio produto** ou **verificados em fonte pública**. Onde não houve verificação, o atributo não existe (por exemplo, liquidez e imposto ficam de fora).

| Tipo | Sigla | Categoria | Indexador | Renda | Vencimento | Proteção ao crédito | Preço |
|---|---|---|---|---|---|---|---|
| Ação | `ACAO` | Renda variável | sem | proventos | não | não se aplica | mercado |
| ETF | `ETF` | Renda variável | sem | conforme regulamento | não | não se aplica | mercado |
| Fundo imobiliário | `FII` | Renda variável | sem | proventos | não | não se aplica | mercado |
| Fundo de renda fixa | `FUNDO_RF` | Fundos | sem | sem renda periódica | não | sem garantia do FGC | tabela oficial |
| Fundo multimercado | `FUNDO_MM` | Fundos | sem | sem renda periódica | não | sem garantia do FGC | tabela oficial |
| Fundo de ações | `FUNDO_ACOES` | Fundos | sem | sem renda periódica | não | sem garantia do FGC | tabela oficial |
| Tesouro Selic | `LFT` | Renda fixa pública | Selic | tudo no vencimento | sim | risco soberano | tabela oficial |
| Tesouro Prefixado | `LTN` | Renda fixa pública | pré-fixado | tudo no vencimento | sim | risco soberano | tabela oficial |
| Tesouro Prefixado com Juros Semestrais | `NTN-F` | Renda fixa pública | pré-fixado | cupom semestral | sim | risco soberano | tabela oficial |
| Tesouro IPCA+ | `NTN-B Principal` | Renda fixa pública | IPCA + taxa | tudo no vencimento | sim | risco soberano | tabela oficial |
| Tesouro IPCA+ com Juros Semestrais | `NTN-B` | Renda fixa pública | IPCA + taxa | cupom semestral | sim | risco soberano | tabela oficial |
| LCI | `LCI` | Renda fixa privada | definido na emissão | conforme emissão | sim | **garantido pelo FGC até o limite** | informado pelo investidor |
| LCA | `LCA` | Renda fixa privada | definido na emissão | conforme emissão | sim | **garantido pelo FGC até o limite** | informado pelo investidor |
| CRI | `CRI` | Renda fixa privada | definido na emissão | conforme emissão | sim | sem garantia do FGC | informado pelo investidor |
| CRA | `CRA` | Renda fixa privada | definido na emissão | conforme emissão | sim | sem garantia do FGC | informado pelo investidor |
| Nota comercial | `NC` | Renda fixa privada | definido na emissão | conforme emissão | sim | sem garantia do FGC | informado pelo investidor |
| Nota promissória | `NP` | Renda fixa privada | definido na emissão | conforme emissão | sim | sem garantia do FGC | informado pelo investidor |
| Criptoativo | `CRIPTO` | Criptoativos | sem | sem renda periódica | não | não se aplica | mercado |
| Câmbio | `CAMBIO` | Câmbio e commodities | sem | sem renda periódica | não | não se aplica | mercado |
| Commodity | `COMMODITY` | Câmbio e commodities | sem | sem renda periódica | não | não se aplica | mercado |
| Índice de referência | `INDICE` | Referência (não negociável) | sem | sem renda periódica | não | não se aplica | mercado |

"Preço" é a **intenção de projeto**: hoje só "mercado" está implementado. "Tabela oficial" e "informado pelo investidor" entram junto com a carteira (itens #12 e #13 do roadmap). As fontes oficiais previstas existem e foram conferidas em 2026-11-21: os preços e taxas do Tesouro Direto no Tesouro Transparente (STN) e as cotas de fundos no conjunto [Informe Diário](https://dados.cvm.gov.br/dataset/fi-doc-inf_diario) do portal de dados abertos da CVM (arquivos mensais até 2026-11).

## O que foi verificado e onde

| Fato | Fonte | Lido em |
|---|---|---|
| LCI, LCA, CDB, RDB, LCD, LH, LC, conta corrente e poupança são garantidos pelo FGC | [fgc.org.br — Sobre a garantia FGC](https://www.fgc.org.br/sobre-garantia-fgc) | 2026-11-20 |
| Não são garantidos: títulos públicos (Tesouro Direto), fundos de renda fixa, debêntures, CRI, CRA, LIG, LI, LF | mesma página | 2026-11-20 |
| Limite: R$ 250 mil por CPF e por instituição ou conglomerado, e R$ 1 milhão por CPF em quatro anos | mesma página | 2026-11-20 |
| Nomes dos produtos do Tesouro Direto: Tesouro Selic, Prefixado, Prefixado com Juros Semestrais, IPCA+ e IPCA+ com Juros Semestrais | [Tesouro Transparente (STN), preços e taxas do Tesouro Direto](https://www.tesourotransparente.gov.br) (CSV, data-base 2026-11-19) | 2026-11-20 |
| Indexador e cupom dos títulos públicos | decorrem do próprio nome do produto | — |

## O que **não** foi verificado

- **Notas comerciais e notas promissórias** não aparecem nas listas de garantidos nem nas de não garantidos do FGC. São tratadas como "sem garantia do FGC" porque a lista de garantidos é taxativa, mas a página não as cita.
- **A correspondência produto → sigla do título público** (Tesouro Selic = LFT, Prefixado = LTN, Prefixado com Juros Semestrais = NTN-F, IPCA+ = NTN-B Principal, IPCA+ com Juros Semestrais = NTN-B) segue a nomenclatura do Tesouro Nacional, mas **não foi relida em página oficial nesta sessão**: o site do Tesouro Direto respondeu HTTP 403. O CSV da STN traz só os nomes dos produtos.
- O CSV da STN lista também Tesouro Renda+, Educa+ e IGPM+ com Juros Semestrais, que não estão modelados.
- **Imposto** (IR regressivo, isenções, IOF) não faz parte da taxonomia: entra nos itens #13 e #25 com a base legal citada.
- **Forma de renda** de ETF, LCI, LCA, CRI, CRA e notas é "conforme emissão ou regulamento", porque depende do papel.

## Como o cadastro de ativos usa a taxonomia

`Instrument.type()` converte a classe textual do cadastro ("Ação", "FII", "Cripto"...) em `AssetType` e falha na hora diante de uma classe desconhecida. O universo atual tem 33 instrumentos de mercado (ações, ETFs, FIIs, cripto, câmbio, commodities e índices); as demais classes (Tesouro, LCI, LCA, CRI, CRA, notas, fundos) existem na taxonomia e entram em carteira nos próximos itens.
