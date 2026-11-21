# Como contribuir

Obrigado pelo interesse no MeridianWealth. Este guia resume o fluxo de trabalho do projeto.

## Pré-requisitos

- Java 21 (Temurin ou Oracle).
- Nada de Maven instalado: use o wrapper (`./mvnw` no Linux/macOS/Git Bash, `.\mvnw.cmd` no PowerShell).
- Docker Desktop é opcional (Compose com PostgreSQL e Redis e, a partir do PR #20, Testcontainers).

## Fluxo (GitHub Flow)

1. Toda mudança nasce de uma Issue.
2. Crie a branch a partir da `main` atualizada: `feat/issue-<N>-<slug>`, `fix/...`, `refactor/...` ou `docs/...`.
3. Faça commits atômicos e semânticos **em português**, com prefixo convencional:
   `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`, `ci:`, `chore:`, `perf:`.
   Exemplo: `feat: adiciona motor de suitability por perfil`.
4. Rode `./mvnw verify` antes do push. O build roda JUnit 5, JaCoCo (piso de cobertura), Checkstyle e Spotless.
   Para corrigir a formatação automaticamente: `./mvnw spotless:apply`.
5. Abra o PR usando o template, com `Fecha #<N>`, decisões, como testar, riscos e o que ficou sem validar.
6. O PR recebe code review técnico; ajustes entram em commit `fix:` próprio.
7. O merge é feito com **rebase** (`gh pr merge --rebase --delete-branch`). Não usamos squash: o histórico atômico é preservado.

## Regras inegociáveis

- **Dados 100% reais.** Nenhum número sintético no produto. Simuladores só existem em `src/test`. Monte Carlo, stress tests e backtests são projeções e levam o rótulo "projeção".
- **Transparência de fonte.** Toda cotação mostra fonte, horário e atraso.
- **Precisão financeira.** `double` apenas em estatística e sinais; `BigDecimal` em dinheiro, quantidade, preço médio, IR e saldo (ADR 002).
- **Arquitetura hexagonal.** `domain` não conhece frameworks; `application` não conhece infraestrutura (ADR 001).
- **Sem segredos no repositório.** Use variáveis de ambiente e o `.env.example`.
- **Idioma.** Issues, commits, PRs, reviews e documentação em português do Brasil; identificadores Java em inglês.
- **Produto analítico e educacional.** Não executa ordens e não é recomendação financeira.

## Decisões de arquitetura

Decisões estruturais são registradas em `docs/adr/` no formato contexto, decisão, consequências e alternativas. Abra um ADR novo em vez de editar um aceito; se mudar de ideia, marque o antigo como substituído.
