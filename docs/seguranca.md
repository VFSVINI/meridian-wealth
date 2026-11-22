# Segurança, autenticação e Login com Google

Item #11 do roadmap. Decisões em [ADR 013](adr/013-autenticacao-jwt-google-e-defesas-de-borda.md).

## O que é público e o que exige login

- **Público:** dados de mercado, glossário, simulador, perfil (questionário) e teses. Nada disso guarda dados do usuário.
- **Exige login (`Authorization: Bearer <access token>`):** tudo sob `/api/me`. Hoje: dados da conta e perfil de investidor salvo.
  Carteira e movimentações entram sob `/api/me` nos itens #12 e #13.
- **Demonstração:** `POST /api/auth/demo` dá uma sessão efêmera (60 min, sem refresh) sem cadastro.

## Rotas de autenticação

| Rota | Corpo | Resultado |
|---|---|---|
| `GET /api/auth/config` | (nenhum) | `googleLoginEnabled`, `demoEnabled`, `registrationEnabled`, `passwordMinLength` |
| `POST /api/auth/register` | `email`, `password`, `displayName` | 201 com a sessão |
| `POST /api/auth/login` | `email`, `password` | 200 com a sessão |
| `POST /api/auth/refresh` | `refreshToken` | 200 com sessão nova; o token antigo morre |
| `POST /api/auth/logout` | `refreshToken` | 204; encerra a sessão (família do token) |
| `POST /api/auth/google` | `idToken` | 200 com a sessão; 503 se o Google não está configurado |
| `POST /api/auth/demo` | (nenhum) | 200 com sessão de demonstração |
| `GET /api/me`, `GET`/`PUT /api/me/profile` | | dados do próprio usuário |

A sessão traz `accessToken` (JWT, 15 min), `refreshToken` (opaco, 7 dias), `user`, `newUser`, `needsSuitability` e `demo`. Erros
trazem `error` (texto em português) e `code` (estável): `INVALID_INPUT` 400; `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`,
`REFRESH_TOKEN_REUSED`, `INVALID_GOOGLE_TOKEN`, `UNAUTHENTICATED` 401; `EMAIL_REGISTERED_WITH_PASSWORD`,
`EMAIL_REGISTERED_WITH_GOOGLE`, `EMAIL_TAKEN` 409; `TOO_MANY_ATTEMPTS` e `RATE_LIMITED` 429; `GOOGLE_NOT_CONFIGURED` e
`CAPACITY_REACHED` 503.

## Variáveis de ambiente

| Variável | Padrão | Uso |
|---|---|---|
| `MW_JWT_SECRET` | aleatório a cada partida | segredo HS256 do JWT, pelo menos 32 caracteres. Defina em produção e em mais de uma instância |
| `MW_GOOGLE_CLIENT_ID` | vazio | liga o Login com Google; vazio = desligado |
| `MW_GOOGLE_CLIENT_SECRET` | vazio | reservada: o fluxo de ID token não a usa |
| `MW_TRUSTED_PROXIES` | vazio | IPs ou CIDR de proxies cujo `X-Forwarded-For` vale (ex.: `10.0.0.0/8,172.18.0.5`) |
| `MW_CORS_ORIGINS` | vazio | origens exatas permitidas, separadas por vírgula; vazio = só a própria origem |
| `MW_DEMO_MODE` | `true` | liga a sessão de demonstração |
| `MW_REGISTRATION` | `true` | permite cadastro por senha |
| `MW_METRICS_TOKEN` | vazio | Bearer das métricas (`/actuator/prometheus`, `/actuator/metrics`), mínimo 24 caracteres; vazio = 404. Comparação em tempo constante. `health` e `info` seguem públicos, sem detalhes (os detalhes do grupo `dependencies` não trazem dado de usuário) |
| `MW_BCRYPT_STRENGTH` | `12` | custo do BCrypt (4 a 16) |

Nenhum segredo vai para o repositório: `.env` está no `.gitignore` e `.env.example` só traz nomes.

## Login com Google: passo a passo no Google Cloud Console

1. Em <https://console.cloud.google.com/>, crie ou escolha um projeto.
2. Em **APIs e serviços > Tela de consentimento OAuth**, configure o aplicativo (tipo Externo, nome, e-mail de suporte) e inclua
   os escopos `openid`, `email` e `profile`.
3. Em **APIs e serviços > Credenciais > Criar credenciais > ID do cliente OAuth**, escolha **Aplicativo da Web**.
4. **Origens JavaScript autorizadas:**
   - local: `http://localhost:8080`
   - Hugging Face Spaces: `https://<usuario>-<space>.hf.space` (a URL do seu Space)
5. **URIs de redirecionamento autorizados:** o botão do Google (item #15) usa o fluxo de ID token em popup e não precisa de
   redirecionamento; se for adotado o fluxo de código, registre `http://localhost:8080/login/oauth2/code/google` e a URL
   equivalente do Space.
6. Copie o **ID do cliente** (termina em `.apps.googleusercontent.com`) para `MW_GOOGLE_CLIENT_ID`. No Hugging Face, use
   **Settings > Variables and secrets** do Space; localmente, o `.env`. Nunca coloque o valor no código.
7. Reinicie a aplicação e confira: `GET /api/auth/config` deve devolver `"googleLoginEnabled": true`.

Sem a variável, tudo funciona normalmente e o endpoint de config diz que o Google não está configurado.

**Cliente (item #15).** `GET /api/auth/config` devolve `googleClientId` (público por natureza) só quando o Google está configurado. Nesse caso o navegador carrega o script do Google Identity Services e a CSP libera exatamente `script-src https://accounts.google.com/gsi/client`, `style-src .../gsi/style`, `connect-src` e `frame-src https://accounts.google.com/gsi/` (nada de curinga, nada de imagem externa). Sem a variável a CSP não menciona o Google e o botão aparece desabilitado com o aviso. O token de acesso e o refresh ficam no `localStorage` do navegador (dívidas 34 e 42).

## Atrás de proxy (Hugging Face Spaces, nginx, Cloudflare)

Liste o IP ou a faixa do proxy em `MW_TRUSTED_PROXIES`; sem isso, todos os clientes aparecem com o IP do proxy e dividem um balde de
rate limit. Não liste redes que clientes externos possam alcançar diretamente. O proxy deve **acrescentar** o IP real à direita do
`X-Forwarded-For` (o comportamento padrão de nginx e da maioria dos proxies).

## Limites conhecidos

Ver as consequências negativas do ADR 013: contas e sessões em memória sem `MW_PERSISTENCE=true` (com ela, ficam no PostgreSQL desde o item #12), refresh token no corpo da resposta (não em
cookie `HttpOnly`), ID token sem `nonce` e travas por instância.
