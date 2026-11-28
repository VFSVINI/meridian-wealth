# Suitability e carteira modelo

Item #8 do roadmap (Issue #21). Um questionário de 7 perguntas classifica o perfil (CONSERVADOR, MODERADO, ARROJADO ou
AGRESSIVO), cada perfil tem uma carteira modelo com metas por classe de ativo, e uma carteira informada pelo usuário é medida
contra a meta (desvio por classe, nota de aderência e radar).

> Ferramenta analítica e educacional. **O questionário não substitui a análise de um profissional habilitado**, as metas são
> ilustrativas e nada aqui é recomendação de investimento.

## O que é norma e o que é definição do produto

A Resolução CVM 30 (art. 3, lida no PDF oficial em 2026-11-21) manda verificar se o produto é adequado aos **objetivos**, se a
**situação financeira** é compatível e se o cliente tem o **conhecimento** necessário, e lista os itens mínimos de cada
dimensão (§1 a §3). As sete perguntas cobrem esses itens (duas delas juntam dois itens cada, ver ADR 040) e citam o parágrafo de origem. A norma obriga **instituições**; esta
ferramenta não recomenda nem executa ordens e **não alega conformidade** com ela: só usa as dimensões como estrutura.

Tudo o mais é **definição do produto**, sem fonte externa: pontos das respostas, faixas da nota, tetos e metas das carteiras.

## Questionário (todas as perguntas valem o mesmo)

| Pergunta | Dimensão (CVM 30, art. 3) |
|---|---|
| Horizonte do investimento | objetivos, §1, I |
| Reação a uma queda de 20% em 3 meses | objetivos, §1, II (preferência de risco) |
| Finalidade do investimento | objetivos, §1, III |
| Renda regular contra despesas | situação financeira, §2, I |
| Parte do patrimônio que este dinheiro representa | situação financeira, §2, II |
| Necessidade do dinheiro em 12 meses e reserva de emergência (uma pergunta) | situação financeira, §2, III |
| Produtos com que investe e há quanto tempo (uma pergunta) | conhecimento, §3, I e II |

Cada pergunta tem de 3 a 5 opções com 0 a 4 pontos (total máximo 28). `GET /api/suitability/questionnaire` devolve perguntas,
pontos, tetos e faixas, para a regra ser inspecionável.

## Perfil

Nota = pontos × 100 / 28, com 2 casas (`HALF_EVEN`). Faixas: abaixo de 30 **conservador**; de 30 a 54,99 **moderado**; de 55 a
79,99 **arrojado**; 80 ou mais **agressivo**.

**Tetos** limitam o perfil por respostas incompatíveis com risco alto, qualquer que seja a nota; vale o menor perfil e cada teto
aplicado aparece com o motivo:

| Resposta | Teto |
|---|---|
| Horizonte de até 1 ano | CONSERVADOR |
| Precisa do dinheiro em 12 meses | CONSERVADOR |
| Sem reserva de emergência | MODERADO |
| Venderia tudo numa queda de 20% | MODERADO |

Resposta faltando, pergunta desconhecida ou opção inválida é **erro 400**, nunca um palpite.

## Carteira modelo (metas ilustrativas, % por classe)

Classes: `AssetCategory` sem a de referência (índices não se compram).

| Perfil | Renda fixa pública | Renda fixa privada | Fundos | Renda variável | Cripto | Câmbio e commodities |
|---|---|---|---|---|---|---|
| CONSERVADOR | 50 | 30 | 10 | 5 | 0 | 5 |
| MODERADO | 30 | 25 | 15 | 25 | 0 | 5 |
| ARROJADO | 15 | 15 | 15 | 45 | 5 | 5 |
| AGRESSIVO | 5 | 10 | 10 | 60 | 10 | 5 |

A soma é 100 em cada perfil (verificada na carga da classe e em teste) e o risco cresce com o perfil.

## Aderência

`POST /api/suitability/adherence` recebe o perfil e as posições (tipo do ativo da taxonomia e valor em reais). O peso de cada
classe é arredondado **uma vez** (2 casas, `HALF_EVEN`) e o desvio sai dos pesos já arredondados, então os números da tela
fecham. Nota = 100 − metade da soma dos desvios absolutos (100 igual à meta, 0 totalmente disjunta). Tolerância de **±5 pontos
percentuais** por classe (inclusiva). A resposta descreve o desvio: **não diz o que comprar ou vender**.

Posição de índice de referência, valor não positivo, carteira vazia, mais de 50 posições, mapa de respostas maior que o questionário ou valor fora de 15 dígitos inteiros e 6 casas decimais são erro 400. O limite de magnitude existe porque notação científica (`1E5000000`) travava a soma por segundos (medido na revisão).

## Privacidade

Respostas e carteira vêm na requisição, viram um resultado e **não são gravadas nem registradas em log**. Só dado de cadastro e
persistência entram no item #11 em diante, com consentimento.

## API

`GET /api/suitability/questionnaire`, `POST /api/suitability/evaluate`, `GET /api/suitability/model-portfolios`,
`POST /api/suitability/adherence`.
