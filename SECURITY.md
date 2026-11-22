# Política de segurança

## Versões suportadas

O projeto está em desenvolvimento ativo (série `0.x`). Apenas a `main` recebe correções.

## Como relatar uma vulnerabilidade

**Não abra issue pública.** Use o relato privado do GitHub:
<https://github.com/VFSVINI/meridian-wealth/security/advisories/new>.

Inclua, se possível: componente afetado, passos para reproduzir, impacto estimado e sugestão de correção.
Respondemos em até 7 dias e combinamos a divulgação após a correção.

## Escopo e princípios

- O MeridianWealth é uma ferramenta **analítica e educacional**: não executa ordens, não guarda credenciais de corretoras e não movimenta dinheiro.
- Chaves de provedores (Finnhub, FRED, brapi) entram apenas por variáveis de ambiente; nunca são registradas em log nem devolvidas pela API.
- A esteira roda CodeQL (`security-extended`) e Dependabot semanalmente.
- Autenticação por JWT com refresh rotativo, senha com BCrypt, Login com Google por ID token, rate limit por usuário e IP real (`X-Forwarded-For` só de proxy confiável), CORS e CSP restritivos: ver [docs/seguranca.md](docs/seguranca.md) e o [ADR 013](docs/adr/013-autenticacao-jwt-google-e-defesas-de-borda.md).
- Segredos (`MW_JWT_SECRET`, `MW_GOOGLE_CLIENT_ID`) entram só por variável de ambiente; nunca são registrados em log nem devolvidos pela API. Senhas e tokens também nunca vão para o log.

## Problemas conhecidos (abertos)

- Sem `MW_PERSISTENCE=true` (execução sem Docker), contas, sessões e carteira ficam em memória e um reinício as apaga; com a persistência ligada (Docker Compose) ficam no PostgreSQL.
- O ID token do Google não leva `nonce` e as travas de tentativa de login valem por instância (dívida 43). O refresh token do navegador vive num cookie `HttpOnly` e nenhum token fica no armazenamento do navegador.
- O contador de rate limit usa o Redis no Compose. A auditoria completa está em [docs/seguranca-checklist.md](docs/seguranca-checklist.md) e o roteiro de incidentes em [docs/resposta-a-incidentes.md](docs/resposta-a-incidentes.md).
