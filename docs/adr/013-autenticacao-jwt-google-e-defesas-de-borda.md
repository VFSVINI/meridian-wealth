# ADR 013 — Autenticação por JWT, Login com Google e defesas de borda

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #25 (item #11 do roadmap)

## Contexto

Até o item #10 a API era toda anônima. O item #11 precisa de contas, de dados que pertencem a um usuário (a carteira do item #12)
e de defesas de borda: rate limit confiável atrás de proxy (dívida 5), CORS e CSP restritivos, modo demonstração e Login com
Google. Riscos: confiar no `X-Forwarded-For` que o cliente escreve, aceitar token de outro emissor, vincular em silêncio uma conta
do Google a uma conta com senha que não é da mesma pessoa, deixar o refresh token ser reusado depois de roubado e travar todo um
NAT por causa de um atacante.

## Decisão

1. **Sem Spring Security.** A autenticação é um filtro pequeno (`AuthenticationFilter`) sobre bibliotecas auditadas: Nimbus JOSE
   (JWT e JWKS) e `spring-security-crypto` (BCrypt). A API é stateless com bearer, sem sessão, sem formulário e sem cookie de
   sessão, então o que o framework completo traria (CSRF, sessão, formulário de login) não se aplica, e a superfície de código
   próprio fica a de um filtro. A regra de que as rotas de usuário ficam sob `/api/me` e só leem o dono do token vem de um teste.
2. **Access token JWT HS256 de 15 minutos** (segredo de pelo menos 256 bits em `MW_JWT_SECRET`; sem ele, um segredo aleatório por
   partida, com aviso, nunca um padrão publicado). A validação aceita só HS256 e confere emissor, audiência, `exp`, `nbf` e o
   formato do `sub`; qualquer falha devolve vazio, sem dizer o motivo.
3. **Refresh token opaco de 256 bits, guardado só como hash SHA-256, com rotação e detecção de reuso.** Cada login abre uma
   "família"; cada refresh consome o token e emite outro na mesma família. Um token já consumido que reaparece revoga a família
   inteira (RFC 6819). A troca é atômica: com pedidos simultâneos para o mesmo token, exatamente um vence (teste de concorrência).
4. **Senha:** BCrypt custo 12; política no estilo NIST 800-63B (mínimo de 12 caracteres, recusa de senhas óbvias, sem regras de
   composição); máximo de 72 **bytes**, porque o BCrypt ignora o resto. E-mail desconhecido paga o mesmo custo de hash que e-mail
   conhecido, e o erro de login não diz se falhou o e-mail ou a senha.
5. **Travas de força bruta em camadas:** 5 falhas por e-mail em 15 minutos (protege cada conta) e 50 por IP (segura varredura de
   várias contas sem derrubar um NAT inteiro por causa de um atacante), além do limite por minuto do filtro de API e de um limite
   menor, por IP, nas rotas `/api/auth/*`.
6. **IP real do cliente (fecha a dívida 5):** `X-Forwarded-For` só vale quando a conexão vem de um proxy listado em
   `MW_TRUSTED_PROXIES` (IPs ou CIDR). A cadeia é lida da direita para a esquerda, pulando proxies confiáveis; o primeiro
   endereço que não é proxy é o cliente. Sem lista (o padrão), o cabeçalho é ignorado por completo. Só literais de IP são aceitos;
   nada é resolvido por DNS.
7. **Rate limit por usuário e por IP:** anônimo por IP (300/min), autenticado por usuário (600/min), demonstração por sessão
   (120/min), `/api/auth/*` por IP (20/min); resposta 429 com `Retry-After`.
8. **Login com Google por ID token:** o backend valida a assinatura RS256 com as chaves públicas do Google (JWKS em cache, com
   nova busca limitada a uma por minuto para `kid` desconhecido), o `iss`, o `aud` igual a `MW_GOOGLE_CLIENT_ID`, o `exp`, o `sub`,
   o e-mail e o `email_verified`, e então emite o **mesmo** JWT interno e refresh token. Só RS256 é aceito (não há confusão de
   algoritmo). O fluxo de ID token não usa o `MW_GOOGLE_CLIENT_SECRET`: a variável fica documentada e reservada, e o código não a
   lê. Sem `MW_GOOGLE_CLIENT_ID` o provedor fica desligado e `GET /api/auth/config` diz `googleLoginEnabled: false`.
9. **Nunca vincular em silêncio:** o mesmo e-mail entre provedores é um conflito explícito (409 com código
   `EMAIL_REGISTERED_WITH_PASSWORD` ou `EMAIL_REGISTERED_WITH_GOOGLE`), nas duas direções. Vincular exigiria provar a posse das duas
   contas e não é feito neste item. Provisionamento automático no primeiro login: e-mail, nome, avatar (só `https`, tamanho
   limitado) e `auth_provider = GOOGLE`; sem perfil de investidor, a resposta traz `needsSuitability: true`.
10. **Modo demo:** `POST /api/auth/demo` cria uma conta efêmera (`DEMO`), sem refresh, de 60 minutos, com limite de 500 sessões
    simultâneas e varredura das expiradas. Os dados de mercado públicos continuam abertos sem token; só `/api/me/**` exige login.
11. **Isolamento entre usuários por construção:** nenhuma rota recebe o dono do dado; o `AuthPrincipal` vem do token validado. O
    `IsolationGuard` é o ponto único de checagem de dono para recursos futuros, e o erro de dono errado é igual ao de "não existe".
12. **CSP restritiva e cabeçalhos:** `default-src 'self'`, sem script nem estilo embutidos (a interface passou a ser servida em
    `index.html`, `app.css`, `app.js` e `theme.js`), sem frames, sem `<base>`, sem objetos, `connect-src` só para o próprio host
    (incluindo o WebSocket). Único relaxamento: `style-src-attr 'unsafe-inline'`, porque a interface gera marcação com atributo
    `style`; ele não permite executar script. Além disso: `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`,
    `Permissions-Policy`, HSTS quando a conexão é HTTPS (direta ou por proxy confiável) e `Cache-Control: no-store` nas rotas de
    autenticação. O Swagger fica fora da CSP porque usa script embutido.
13. **Corpo de requisição limitado:** `POST`/`PUT` em `/api/**` com mais de 64 KB recebem 413 antes de qualquer leitura (sem
    `Content-Length`, o fluxo é limitado byte a byte), e o login recusa de imediato senha acima de 72 bytes, que nenhuma conta pode
    ter. Sem isso, as rotas públicas alocavam dezenas de megabytes por requisição de quem não fez login (achado do code review).
14. **CORS e WebSocket:** sem `MW_CORS_ORIGINS`, só a própria origem; com a lista, só as origens exatas, sem curinga (o `*` falha
    na partida) e sem credenciais. O WebSocket do stream deixou de aceitar qualquer origem.

## Consequências

- **Positivas:** autorização única para senha, Google e demonstração; reuso de refresh token detectado; o IP do cliente deixa de
  ser forjável; a promessa "a aplicação sobe sem nenhuma variável do Google" é testada.
- **Negativas:**
  - Contas e refresh tokens ficam **em memória** sem `MW_PERSISTENCE=true`: um reinício apaga as contas e encerra as sessões.
    Com a persistência ligada, o item #12 (ADR 014) os grava no PostgreSQL.
  - O refresh token trafega no corpo da resposta e fica no armazenamento do navegador; um cookie `HttpOnly` com `SameSite` o
    protegeria melhor de XSS, e a CSP restritiva é a mitigação atual.
  - O ID token do Google não leva `nonce`, então um token válido roubado pode ser reapresentado até expirar (cerca de 1 hora).
  - As travas de tentativa e o contador de rate limit em memória valem por instância (o contador usa o Redis no Compose).
  - O cadastro responde 409 quando o e-mail já existe, o que revela que ele está cadastrado; é uma escolha de UX (a pessoa
    precisa saber para entrar), mitigada pelo limite por IP. Os tetos globais de contas e de sessões de demonstração podem ser
    enchidos por um único IP ao longo do tempo; a persistência e a verificação de e-mail (item #12) reduzem o caso das contas.

## Alternativas rejeitadas

- **Spring Security completo:** muita superfície (CSRF, sessão, formulário) para uma API stateless; o ganho seria a convenção, não
  segurança adicional neste desenho.
- **Vincular contas pelo e-mail quando o Google o confirma:** o e-mail verificado prova a posse do e-mail no Google, não a de quem
  criou a conta com senha; vincular em silêncio permitiria tomada de conta.
- **RS256 para o token interno:** par de chaves sem ganho, pois só esta aplicação emite e valida.
- **Refresh token em JWT:** impediria a rotação com reuso detectável.
- **Travar por IP com o mesmo limite do e-mail:** um atacante atrás de um NAT ou de um proxy compartilhado trancaria todos os
  usuários legítimos.
