# ADR 024 — Deploy gratuito no Render com PostgreSQL no Neon

- **Status:** aceito (substitui o [ADR 023](023-deploy-gratuito-hugging-face-e-neon.md))
- **Data:** 2026-11-23
- **Issue:** #58 (correção do item #21 do roadmap)

## Contexto

O ADR 023 escolheu o Hugging Face Spaces sem conferir no site do provedor se o SDK Docker era gratuito. Ao tentar o primeiro deploy, a tela de criação mostrou que **Spaces com Docker exigem plano pago (PRO)**; só os estáticos são gratuitos, e o MeridianWealth é uma aplicação Java com servidor. A premissa estava errada.

Pesquisa de 2026-11-23 sobre planos gratuitos com Docker: o Koyeb passou a exigir cartão (com bloqueio de US$ 29); o Back4App tem 256 MB, pouco para a JVM; o Render oferece Docker, 512 MB e 750 horas por mês, e dorme após 15 minutos sem acesso. As fontes divergem em detalhes (CPU, banda), então os limites são conferidos no site do Render antes do deploy.

Medição local: a aplicação com `-Xmx300m` e coletor serial ocupa cerca de 236 MB depois de algumas requisições, o que cabe em 512 MB.

## Decisão

1. **Aplicação no Render (serviço web Docker, plano gratuito), dados no Neon**, na mesma região (Ohio) para reduzir a latência. O Redis continua de fora (cache em memória), com a mesma exclusão exata das duas classes do Redis do ADR 023 e o teste que sobe a aplicação assim contra PostgreSQL.
2. **A configuração vive no repositório**, em `render.yaml` (Blueprint): Docker, plano gratuito, verificação de saúde em `/actuator/health/liveness`, `autoDeploy: false` (publica quando o dono manda), limites de memória da JVM por `JAVA_TOOL_OPTIONS` (`-Xmx280m`, metaspace e código limitados, coletor serial), `MW_JWT_SECRET` gerado pelo Render e `DB_URL`, `DB_USER` e `DB_PASSWORD` como `sync: false` (o Render os pede no painel; nada sensível no arquivo).
3. **Sem workflow de publicação:** o Render lê o repositório do GitHub e compila o `Dockerfile` da raiz; o workflow do Hugging Face e a pasta `deploy/hf/` saem. O `HEALTHCHECK` do `Dockerfile` passa a usar a variável `PORT`, porque o Render define uma porta própria.
4. **Teste de coerência** (`DeployFilesTest`) trava o blueprint (Docker, plano gratuito, segredos sem valor escrito, memória da JVM dentro do limite) e o runbook.

## Consequências

- O deploy continua dependendo de ações do dono (conta, segredos, criação do serviço). Os pontos que só se provam no ar (faixa do proxy, memória em Monte Carlo, partida lenta com CPU pequena, failover da cripto, banda gratuita) ficam listados no runbook.
- O plano gratuito dorme e a CPU é pequena: a primeira visita depois de uma pausa é lenta.
- Sem a restrição do iframe do Hugging Face, a CSP `frame-ancestors 'none'` permanece como está.
- Plano B se o gratuito não bastar: Oracle Cloud "Always Free" (mais memória, mas exige cartão e cadastro trabalhoso) ou um plano pago de entrada.

## Alternativas descartadas

- **Hugging Face Spaces:** Docker exige plano pago.
- **Koyeb:** passou a exigir cartão.
- **Back4App:** 256 MB de RAM.
- **Fornecedores pouco conhecidos cujas fontes são só o próprio site:** risco de depender de um plano que muda sem aviso.
