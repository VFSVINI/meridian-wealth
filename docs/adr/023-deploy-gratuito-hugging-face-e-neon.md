# ADR 023 — Deploy gratuito no Hugging Face Spaces com PostgreSQL no Neon

- **Status:** substituído pelo [ADR 024](024-deploy-gratuito-render-e-neon.md) (o Hugging Face exige plano pago para Docker; a premissa não foi conferida no site)
- **Data:** 2026-11-23
- **Issue:** #55 (item #21 do roadmap)

## Contexto

O roadmap prevê um deploy sem custo. Os dois serviços pedem contas e segredos do dono do projeto, que o repositório não pode criar nem guardar. O Space do Hugging Face roda uma imagem Docker na porta 7860 como usuário de UID 1000, dorme sem acesso e fica atrás de um proxy; o Neon gratuito limita conexões e suspende o banco ocioso.

## Decisão

1. **Aplicação no Space (SDK Docker), dados no Neon.** O Redis fica de fora (`MW_CACHE_TYPE=memory`): o cache é recuperável, e um terceiro serviço gratuito só somaria falhas. O Space liga o banco e o Flyway e desliga só o Redis, por `MW_EXCLUDE_AUTOCONFIG` com as duas classes do Redis; um teste sobe a aplicação inteira exatamente assim contra PostgreSQL e confere que ela fica saudável.
2. **A configuração do Space vive no repositório**, em `deploy/hf/` (`Dockerfile` com porta 7860 e UID 1000, `README.md` com `sdk: docker` e `app_port: 7860`). Um workflow **manual** (`workflow_dispatch`) monta o conteúdo trocando esses dois arquivos e envia ao Space com os segredos `HF_TOKEN` e `HF_SPACE`; sem eles, para com mensagem clara. Nunca publica sozinho.
3. **Porta e pool configuráveis** (`PORT`, `DB_POOL_SIZE`, `DB_CONNECT_TIMEOUT_MS`), com os padrões de antes. O `HEALTHCHECK` passa a usar `/actuator/health/liveness`: o agregado devolve `DEGRADED` (HTTP 200) quando falta dado de algum provedor, o que é normal e não é falha do contêiner (o comando antigo procurava `UP` no agregado).
4. **Runbook** em `docs/deploy.md` com o passo a passo, as variáveis (segredo ou variável), a verificação, a reversão e, separados, **os pontos que só se provam com o Space no ar**.
5. **Teste de coerência** (`DeployFilesTest`) trava porta, SDK, UID, workflow manual sem credencial escrita e as variáveis do runbook.

## Consequências

- O deploy depende de ações do dono (criar contas, cadastrar segredos, rodar o workflow); o repositório entrega tudo o que não depende delas.
- **Não validado:** o deploy real. Ficam para o primeiro deploy: a faixa do proxy em `MW_TRUSTED_PROXIES` (sem ela o limite de requisições é compartilhado entre todos e o cookie de sessão não recebe `Secure`), o failover da cripto em nuvem (dívida 13) e a CSP `frame-ancestors 'none'`, que impede a página do huggingface.co de embutir o app (usa-se o endereço `.hf.space` direto).
- O plano gratuito dorme e suspende: o primeiro acesso depois de uma pausa é lento. Sem backup próprio (dívida 43).

## Alternativas descartadas

- **Publicar automaticamente a cada merge:** expõe uma versão não revisada por quem opera o Space e gasta o tempo de build do plano gratuito; o workflow é manual.
- **Redis gratuito de terceiros:** mais uma conta e mais um ponto de falha por um cache que se refaz sozinho.
- **Afrouxar a CSP para o iframe do Hugging Face:** troca uma proteção contra clickjacking por conveniência.
