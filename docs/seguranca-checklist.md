# Checklist de segurança e conformidade

Auditoria do MeridianWealth contra três listas que o usuário trouxe: **40 requisitos** de conformidade e segurança para apps feitos com IA, **20 medidas técnicas** e os **erros de segurança mais comuns**. Cada item aponta onde está resolvido (e o teste que prova), por que não se aplica, ou a dívida que fica. Feita em 2026-11-22 (Issue #43), com sondas no app real além da leitura do código.

> Este documento é uma autoavaliação técnica. **Não é parecer jurídico nem auditoria de conformidade independente**: os textos de privacidade e termos foram escritos para este projeto e merecem revisão de um advogado antes de qualquer uso comercial.

Legenda: ✅ feito e testado · ⚠️ parcial ou dívida declarada · ➖ não se aplica a este produto.

## Os 40 requisitos

| # | Requisito | Status | Onde e como |
|---|---|---|---|
| 1 | Autenticação segura | ✅ | JWT HS256 de 15 min, refresh opaco com rotação e detecção de reuso, Login com Google validado no servidor (ADR 013, `AuthApiIntegrationTest`) |
| 2 | Controle de acesso | ✅ | `/api/me/**` só com token; o dono vem do token, nunca de parâmetro; `IsolationGuard`; posição de outro usuário responde 404 |
| 3 | Criptografia de dados | ⚠️ | Em trânsito: HTTPS (item 4). Senhas: hash. Em repouso: depende do disco e do PostgreSQL do host (item #21); sem criptografia por coluna (dívida 43) |
| 4 | HTTPS/TLS | ✅ | HSTS sob HTTPS, `MW_FORCE_HTTPS` (308 com host validado), cookie `Secure` (`ForceHttpsIntegrationTest`). O TLS termina no proxy; **não validado com TLS real** |
| 5 | Hashing de senhas | ✅ | BCrypt custo 12; hash fictício para e-mail desconhecido (sem canal de tempo) |
| 6 | SQL injection | ✅ | Toda consulta é parametrizada; só constantes do adaptador entram no texto (`InjectionSurfaceTest`) |
| 7 | XSS / CSRF | ✅ | CSP sem script e estilo embutidos, `esc()` em toda marcação dinâmica, `nosniff`; cookie `SameSite=Strict` mais cabeçalho `X-Session-Mode`; o access token vai em `Authorization`, nunca em cookie |
| 8 | Rate limiting | ✅ | Por IP real e por usuário, mais apertado nas rotas de autenticação, mais trava de tentativas por e-mail e IP (`RateLimitIntegrationTest`) |
| 9 | Proteção de APIs | ✅ | Autenticação, limites, corpo máximo de 64 KB, CORS por origem exata, cabeçalhos; todo filtro decide pelo caminho despachado (`RequestPaths`) |
| 10 | Auditoria de dependências | ✅ | Dependabot semanal, CodeQL `security-extended`, revisão de dependências em todo PR (`dependency-review.yml`) |
| 11 | Política de privacidade | ✅ | Página `#/privacidade` |
| 12 | Bases legais | ✅ | Declaradas na política (LGPD, art. 7º, V) |
| 13 | Minimização de dados | ✅ | Só e-mail, nome, perfil, posições e livro; sem foto do Google, sem rastreadores |
| 14 | Finalidades documentadas | ✅ | Seção "Para que usamos" da política |
| 15 | Gestão de consentimento | ⚠️ | Caixa de aceite no cadastro; **o aceite não é gravado com data e versão nem exigido pela API** (dívida 43) |
| 16 | Política de cookies | ✅ | Um cookie essencial de sessão e preferências de tema; sem cookie de rastreamento, logo sem banner |
| 17 | Direitos dos titulares | ✅ | Acesso e portabilidade (`GET /api/me/export`); eliminação (`POST /api/me/erase`); correção do perfil na tela; correção do nome ainda não existe (dívida 43) |
| 18 | Exclusão de dados | ✅ | Apaga conta, posições, livro e sessões, com e-mail digitado como confirmação; testado em memória e no PostgreSQL |
| 19 | Retenção de dados | ✅ | Política: enquanto a conta existir; demonstração some em 60 min. Não há expurgo de contas inativas (dívida 43) |
| 20 | Contratos com operadores | ⚠️ | Provedores de mercado recebem só símbolos e datas. O contrato com a hospedagem (operador de dados pessoais) depende do deploy (item #21) |
| 21 | Termos de uso | ✅ | Página `#/privacidade` |
| 22 | Transparência de preços | ➖ | Serviço gratuito, sem cobrança |
| 23 | Cobranças transparentes | ➖ | Sem cobrança |
| 24 | Cancelamento de assinaturas | ➖ | Sem assinatura (a exclusão da conta existe, item 18) |
| 25 | Política de reembolso | ➖ | Sem cobrança |
| 26 | Identificação do fornecedor | ⚠️ | O responsável e o canal de contato são o repositório; sem razão social ou CNPJ porque não há oferta comercial. Muda se houver |
| 27 | Publicidade transparente | ➖ | Sem anúncios |
| 28 | Depoimentos autênticos | ➖ | Nenhum depoimento ou avaliação exibido |
| 29 | Licenças de uso | ⚠️ | Fontes SIL OFL e ECharts Apache 2.0 com licença e NOTICE ao lado (testado). **O projeto não tem arquivo `LICENSE`: a licença é uma decisão do dono do projeto** (dívida 43) |
| 30 | Transparência sobre IA | ✅ | O produto não usa IA generativa em nada que o usuário vê: sinais, notas, vereditos e teses são regras mecânicas explícitas e documentadas. Cada tela diz isso |
| 31 | Backups automatizados | ⚠️ | Responsabilidade da infraestrutura do PostgreSQL no deploy (item #21); nada implementado aqui (dívida 43) |
| 32 | Logs seguros | ✅ | Nenhuma senha, token ou corpo em log; Correlation-ID validado contra injeção de linha; log de acesso só com método, caminho, status e duração |
| 33 | Monitoramento de ameaças | ⚠️ | Métricas por provedor e health em grupos (item #14); **sem alertas** de pico de 401 ou 429 (dívida 43) |
| 34 | Resposta a incidentes | ✅ | Roteiro em [resposta-a-incidentes.md](resposta-a-incidentes.md) |
| 35 | Notificação de incidentes | ✅ | No roteiro e na política (LGPD, art. 48) |
| 36 | Testes de segurança | ⚠️ | Testes automatizados de bypass, IDOR, enumeração, limites, CSP e cookies; CodeQL. **Sem teste de invasão externo** (dívida 43) |
| 37 | Revisão de código | ✅ | Todo PR passa por revisão com achado verificado no app real (já pegou o contorno do token das métricas e o conflito de sessão entre abas) |
| 38 | Privilégios administrativos | ✅ | Não existe papel administrativo; métricas só com `MW_METRICS_TOKEN`; Swagger desligado por padrão |
| 39 | Gestão de vulnerabilidades | ✅ | `SECURITY.md` (relato privado), Dependabot, CodeQL, revisão de dependências |
| 40 | Auditoria de conformidade | ✅ | Este documento, com testes de contrato que quebram o `verify` se a proteção regredir |

## As 20 medidas técnicas

| # | Medida | Status | Onde |
|---|---|---|---|
| 1 | Esconder as chaves de API | ✅ | Chaves só por variável de ambiente no servidor; nenhuma no navegador (`SecretScanTest`) |
| 2 | Limpar os segredos do Git | ✅ | `.gitignore` de `.env` e chaves; varredura de segredos em código, docs e workflows; o histórico inteiro foi varrido por assinaturas de chaves: nada encontrado |
| 3 | Chave pública do banco de dados | ➖ | O navegador nunca fala com o banco; só o servidor, com credencial em variável de ambiente |
| 4 | Segurança em nível de linha (RLS) | ➖ | Sem acesso direto do cliente ao banco; o isolamento é na aplicação (`user_id` em toda consulta, testado contra IDOR) |
| 5 | Criptografar dados sensíveis | ⚠️ | Ver item 3 dos requisitos |
| 6 | Autenticação no servidor | ✅ | `AuthenticationFilter`; nenhuma decisão de acesso no navegador |
| 7 | Travar o acesso aos registros de log | ✅ | Não há endpoint de log; actuator só com token |
| 8 | Bloquear adulteração de campos | ✅ | DTOs explícitos (sem mass assignment), dono do token, versão otimista, limites de magnitude |
| 9 | Proteger os cookies da sessão | ✅ | `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, `Secure` sob HTTPS (`AuthApiIntegrationTest`) |
| 10 | Hash das senhas | ✅ | BCrypt |
| 11 | Limitar tentativas de login | ✅ | 5 por e-mail e 50 por IP na janela, mais o limite por minuto |
| 12 | Proteções contra bots | ⚠️ | Rate limit e travas; **sem CAPTCHA** (exigiria um serviço de terceiros e foi adiado, dívida 43) |
| 13 | Parametrizar as queries | ✅ | Ver item 6 |
| 14 | Validar todas as entradas | ✅ | Enumerações, expressões regulares, limites de tamanho e de magnitude, símbolos contra o universo |
| 15 | Escapar conteúdo do usuário na URL | ✅ | `esc()` e `encodeURIComponent`; CSP; o `path` saiu do corpo de erro |
| 16 | Restringir o upload de arquivos | ➖ | Não existe upload |
| 17 | Enxugar as respostas de API | ✅ | DTOs sem hash nem identificadores internos; erro só com `status` e `error` (`ErrorBodyConfig`) |
| 18 | Cabeçalhos de segurança | ✅ | CSP, `nosniff`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, HSTS, COOP |
| 19 | Forçar o HTTPS | ✅ | `MW_FORCE_HTTPS` |
| 20 | Escanear dependências | ✅ | Dependabot, CodeQL, revisão de dependências |

## Os erros mais comuns

| Erro | Status | Como é evitado |
|---|---|---|
| Secrets expostos no frontend | ✅ | Nada no navegador além do ID de cliente do Google, que é público por natureza; varredura automática |
| Entradas sem validação | ✅ | Ver medida 14 |
| SQL injection e prompt injection | ✅ / ➖ | Consultas parametrizadas; não há LLM no produto, logo não há prompt a injetar |
| XSS | ✅ | CSP, escape, `nosniff`, nenhum token acessível a script |
| IDOR/BOLA | ✅ | O dono vem do token; posição ou entrada de outro usuário é 404; testado nos dois modos de persistência |
| SSRF | ✅ | Nenhuma rota recebe URL, host ou endereço de retorno; os clientes de provedor usam hosts fixos (`InjectionSurfaceTest`) |
| Senhas em texto puro | ✅ | BCrypt; senha nunca em resposta nem em log |
| Abuso de requisições (DoS) | ⚠️ | Rate limit, corpo de 64 KB, limite de 200 posições, circuit breakers. **Sem WAF nem proteção de rede**: isso é do deploy |
| Enumeração e rotas administrativas | ⚠️ / ✅ | Login responde igual para e-mail ou senha errados, com custo de hash igual. O cadastro revela e-mail já usado (inerente a qualquer cadastro; limitado por rate limit). Swagger e `/v3/api-docs` fora do ar por padrão; actuator só `health`, `info` e métricas com token |
| Mensagens de erro que vazam dados | ✅ | Sem stack trace, sem `path`, sem eco da entrada (`AuthApiIntegrationTest`) |

## O que a sonda no app real achou (2026-11-22)

- **Swagger e `/v3/api-docs` abertos para qualquer um** (lista completa das rotas): agora desligados por padrão (`MW_API_DOCS=true` liga, para desenvolvimento).
- Corpo de erro devolvia a **URL pedida** (`path`) e uma mensagem de ativo repetia a entrada: removidos.
- `/api/%61uth/login` caía no limite geral em vez do limite apertado das rotas de autenticação: os filtros passaram a decidir pelo caminho que o Spring despacha, não pelo texto cru.
- Refresh token e access token no `localStorage` (dívidas 34 e 42): o refresh foi para cookie `HttpOnly` e nenhum token fica no armazenamento do navegador.

## O que fica como dívida (43)

Criptografia em repouso e backups (deploy), aceite dos termos gravado, correção do nome, expurgo de contas inativas, arquivo `LICENSE`, alertas de monitoramento, CAPTCHA, teste de invasão externo, `nonce` do login com Google e travas de login compartilhadas entre instâncias.
