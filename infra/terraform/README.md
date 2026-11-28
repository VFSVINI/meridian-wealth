# Infraestrutura como código (Terraform), sem aplicar

Este diretório **descreve** o ambiente de produção do MeridianWealth (banco no Neon, serviço web no Render) como código. Ele espelha o [`render.yaml`](../../render.yaml)
e o [runbook de deploy](../../docs/deploy.md). **Nada aqui é aplicado pela CI nem por este projeto**: a CI só roda `terraform fmt -check` e `terraform validate`, que
conferem a sintaxe e o esquema dos provedores sem tocar em conta nenhuma.

## Por que existe

- Documentação executável: o desenho do ambiente (região, plano, variáveis, banco) fica num lugar que se revisa em PR, em vez de só no painel.
- Prova de que o ambiente é reproduzível, sem custo.
- Ponto de partida se um dia o projeto sair do plano gratuito (ver [ADR 045](../../docs/adr/045-infraestrutura-como-codigo-e-comparacao-de-nuvens.md)).

## Se você for aplicar

1. Crie as chaves de API (Render e Neon) e exporte `TF_VAR_render_api_key`, `TF_VAR_neon_api_key`; copie `terraform.tfvars.example` para `terraform.tfvars`.
2. **O ambiente já existe.** Um `apply` direto criaria um segundo serviço e um segundo banco. Importe o que existe antes (`terraform import render_web_service.app <srv-id>`,
   `terraform import neon_project.db <id-do-projeto>`) e rode `terraform plan` até ele mostrar **nenhuma mudança** (ou só as que você quer).
3. O estado (`terraform.tfstate`) **contém a senha do banco e o segredo do JWT**. Ele é ignorado pelo Git; guarde-o num *backend* privado se for usar de verdade.
4. **Não validado:** se a API do Render aceita criar o plano `free` por Terraform (a documentação do provedor lista só os planos pagos). O `render.yaml` cria o serviço gratuito
   pelo Blueprint, que é o caminho comprovado.

## Comandos (local)

```
terraform init -backend=false
terraform fmt -check
terraform validate
```
