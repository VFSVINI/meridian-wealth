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
- O roadmap de segurança (JWT com refresh, hash forte de senha, rate limit por usuário e IP com `X-Forwarded-For` de proxies confiáveis, CORS e CSP restritivos) está na Issue do PR #11 e em `docs/PROJETO.md`.

## Problemas conhecidos (abertos)

- O rate limit da API usa `getRemoteAddr()`: atrás de proxy todos os clientes compartilham o mesmo limite (dívida 5, PR #11).
- Ainda não há autenticação: não exponha a instância publicamente com dados pessoais antes do PR #11.
