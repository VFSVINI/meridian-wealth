# Carteira persistida

Item #12 do roadmap. Decisões em [ADR 014](adr/014-carteira-persistida-preco-medio-e-bloqueio-otimista.md). Exige login (rotas sob `/api/me`,
ver [seguranca.md](seguranca.md)). A carteira é um registro seu: não é extrato de corretora, a plataforma não executa ordens e nada
aqui é recomendação de investimento.

## Rotas

| Rota | Corpo | O que faz |
|---|---|---|
| `GET /api/me/portfolio` | | posições valorizadas, câmbio, totais em reais, pesos por classe e notas |
| `POST /api/me/portfolio/positions` | `symbol` **ou** `type` (+ `label`), `quantity`, `averagePrice`, `currency` | abre a posição (201); 409 se já existe |
| `POST /api/me/portfolio/positions/{id}/purchases` | `quantity`, `unitPrice`, `expectedVersion?` | compra: novo preço médio ponderado |
| `POST /api/me/portfolio/positions/{id}/sales` | `quantity`, `expectedVersion?` | venda: reduz a quantidade e mantém o preço médio; zerar fecha |
| `PUT /api/me/portfolio/positions/{id}` | `quantity`, `averagePrice`, `expectedVersion` | substitui quantidade e preço médio (ajuste manual) |
| `DELETE /api/me/portfolio/positions/{id}?expectedVersion=` | | remove a posição |

Erros trazem `error` e `code`: `INVALID_INPUT` 400, `NOT_FOUND` 404 (inclusive posição de outro usuário), `POSITION_EXISTS`,
`VERSION_CONFLICT` e `LIMIT_REACHED` 409 (máximo de 200 posições por carteira).

## Como o preço médio é calculado

- **Compra de `q'` a `p'`** sobre `q` a `pm`: `pm' = (q · pm + q' · p') / (q + q')`, arredondado a 6 casas (HALF_EVEN). Exemplo: 10 a
  R$ 1.000 e mais 10 a R$ 1.100 dá 20 a R$ 1.050.
- **Venda:** a quantidade cai e o preço médio **não muda**. Vender mais do que se tem é recusado; vender tudo fecha a posição.
- Corretagem e taxas só entram se você as somar ao preço informado. Resultado realizado e imposto de renda ficam para o livro de
  movimentações (item #13).
- Quantidade: até 8 casas decimais. Preço: até 6. Até 15 dígitos inteiros.

## Versão e concorrência

Cada posição tem `version`. Uma escrita só vale se a versão que você leu ainda for a atual; senão a resposta é **409
`VERSION_CONFLICT`**: releia a carteira e repita. Mandar `expectedVersion` detecta também uma tela desatualizada. Sem
`expectedVersion` a compra e a venda ainda são protegidas: duas operações simultâneas nunca se sobrescrevem, uma delas recebe o 409.

## Moedas e valorização

- A posição é em **BRL** ou **USD**. Ativo do painel herda a moeda da cotação; ativo cotado em outra moeda (EUR, JPY, GBp...) é
  recusado.
- **Valor de mercado:** preço do provedor na moeda da posição, com `source`, `quoteTime` e selo de frescor (`freshness`, `stale`).
  Sem cotação, `basis` é `COST` e valor de mercado e resultado vêm `null` ("não informado").
- **Reais:** posições em dólar são convertidas pelo **PTAX (venda)** do Banco Central, com `date` e `source`. Sem PTAX, os valores em
  reais vêm `null`.
- **Resultado não realizado** é na moeda da posição. O custo em reais de uma posição em dólar usa o câmbio **de hoje**, não o da
  compra, então não há resultado em reais.
- `byClass` pesa a carteira por classe de ativo sobre o valor atual em reais (posição sem cotação entra pelo custo).

## Persistência

Com `MW_PERSISTENCE=true` (Docker Compose) as contas, as sessões, o perfil de investidor e as posições ficam no PostgreSQL
(`V2__users_and_portfolio.sql`). Sem ela, ficam em memória e somem no reinício. A migração tem restrições que o banco impõe:
quantidade e preço positivos, moeda BRL ou USD, uma posição por usuário e ativo, credencial coerente com o provedor da conta e
exclusão em cascata ao apagar o usuário.

## Limites

- Sem livro de movimentações: o histórico de compras e vendas, o câmbio de cada compra e o imposto chegam no item #13.
- Sem tela: "Minha Carteira" é a etapa 2 do item #15.
