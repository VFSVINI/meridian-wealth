# ADR 045 — Infraestrutura como código sem aplicar e comparação de nuvens

- **Status:** aceito
- **Data:** 2026-11-28
- **Issue:** #113 (item #63 do roadmap, Milestone 10)

## Contexto

O ambiente de produção (Render + Neon, planos gratuitos) está descrito em `render.yaml` e num runbook, mas o banco no Neon e as decisões de região e plano só existem nos painéis. Surgiu a ideia de usar GCP ("todo mundo está falando disso"). O projeto precisa continuar **sem custo** e sem exigir cartão, e nada pode migrar sem decisão do dono.

## Decisão

1. **Terraform em `infra/terraform/` descreve o ambiente atual** (projeto Neon com PostgreSQL 16 em `aws-us-east-2`, serviço web Docker no Render em Ohio, as mesmas variáveis do `render.yaml`), **sem aplicar**. A CI roda só `terraform fmt -check`, `init -backend=false` e `validate`, que conferem sintaxe e esquema dos provedores sem tocar em conta alguma. Um teste confere que o Terraform e o `render.yaml` dizem a mesma coisa (variáveis, região, `autoDeploy` desligado, opções da JVM) e que nenhum segredo está escrito nos arquivos.
2. **Chaves e estado são segredo:** as chaves de API são variáveis `sensitive` sem valor padrão (`TF_VAR_*`); `*.tfstate`, `.terraform/` e `terraform.tfvars` são ignorados pelo Git, porque o estado guarda a senha do banco e o segredo do JWT.
3. **Antes de aplicar, importar:** o ambiente já existe; o README manda importar os recursos (`terraform import`) e só aplicar quando o `plan` não mostrar mudança indevida.
4. **Comparação com a nuvem do Google, só como análise:**

| Critério | Render + Neon (hoje) | GCP Cloud Run + Neon |
|---|---|---|
| Custo no uso do projeto | zero (planos gratuitos) | a camada gratuita do Cloud Run cobre um projeto pequeno, mas **a conta de cobrança com cartão é obrigatória** e o que passar da cota é cobrado |
| Cartão | não exigido (conferir no cadastro) | exigido |
| Partida a frio | o serviço dorme após 15 min e a JVM leva dezenas de segundos | escala a zero; a JVM também parte a frio (mitigável com instância mínima, que **tem custo**) |
| Banco | Neon gratuito (suspende quando ocioso) | Cloud SQL **não tem camada gratuita**; o caminho barato seria manter o Neon |
| Esforço de migração | nenhum | novo serviço, novo domínio de acesso, segredos no Secret Manager, nova rotina de deploy |
| Ganho de produto | n/a | nenhum: o produto roda igual |

   Os números de cota do Cloud Run (da ordem de 2 milhões de requisições e algumas centenas de milhares de segundos de vCPU e de GiB por mês) vieram de páginas de terceiros e **não foram verificados em fonte primária**: confira na página oficial de preços antes de qualquer decisão.
5. **Recomendação:** ficar no Render + Neon. Migrar para o GCP trocaria custo zero por risco de cobrança, sem ganho para o usuário. Como estudo, o Dockerfile e a imagem no GHCR (ADR 044) já bastam para rodar no Cloud Run se um dia for desejado. **Nada migra para o GCP sem decisão do dono.**

## Consequências

- O ambiente passa a ser revisável em PR e reproduzível, sem custo e sem aplicar nada.
- Não validado: se a API do Render aceita criar o plano `free` por Terraform (a documentação do provedor lista só planos pagos); o `render.yaml` continua sendo o caminho comprovado para criar o serviço gratuito.
- Dois provedores de comunidade ou de terceiros (Render e Neon) entram no `required_providers`, fixados numa linha de versão.
