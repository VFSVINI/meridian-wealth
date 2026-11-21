# Changelog

Todas as mudanças relevantes do projeto ficam registradas aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o versionamento segue o [SemVer](https://semver.org/lang/pt-BR/).
Cada milestone fecha com uma tag `v0.<N>.0` e uma GitHub Release.

## [Não lançado]

### Adicionado
- Maven Wrapper 3.9.9 com checksum SHA-256 verificado.
- Cobertura JaCoCo com piso de 70% de linhas no `verify`.
- Esteira com CodeQL (`security-extended`), Dependabot (Maven, Actions e Docker) e resumo de cobertura no job.
- Governança: `CONTRIBUTING.md`, `SECURITY.md`, templates de Issue e PR em português e `docs/PROJETO.md` como memória do projeto.
- ADRs 001 (arquitetura hexagonal), 002 (precisão financeira) e 003 (frontend sem etapa de build).

### Alterado
- Rebranding de ChronosQuant (`dev.chronosquant`) para MeridianWealth (`dev.meridianwealth`): classe principal `MeridianWealthApplication`, artefato `meridian-wealth`, imagem Docker, textos da UI e variáveis `CQ_*` → `MW_*`.
- Prefixo de propriedades `chronosquant.*` → `meridianwealth.*`.

### Base herdada
- Importada a base inicial ChronosQuant 0.2.0 (motor quantitativo, provedores com failover, resiliência própria, stream WebSocket/SSE e painel). Primeiro `mvn verify` executado neste PR: 45 testes, todos verdes.
