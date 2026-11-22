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

- Contas e sessões ficam em memória até a migração de usuários (item #12): um reinício as apaga.
- O refresh token trafega no corpo da resposta (não em cookie `HttpOnly`) e o ID token do Google não leva `nonce`; a CSP restritiva é a mitigação de XSS.
- As travas de tentativa de login valem por instância; o contador de rate limit usa o Redis no Compose.
