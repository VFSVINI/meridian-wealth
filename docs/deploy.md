# Deploy gratuito: Hugging Face Spaces + Neon

Runbook para publicar o MeridianWealth sem custo: a aplicação roda num **Space do Hugging Face** (SDK Docker, porta 7860) e os dados de contas e carteira ficam num **PostgreSQL do Neon**. As contas e os segredos são **seus**: nada aqui cria conta nem guarda chave no repositório.

> **Estado de validação:** os arquivos deste runbook foram conferidos por teste (coerência de porta, SDK e segredos) e a imagem compila na CI, mas o deploy em si **não foi executado** por quem escreveu (exige as suas contas). Os pontos que só aparecem com o Space no ar estão em [Pontos a verificar no primeiro deploy](#pontos-a-verificar-no-primeiro-deploy).

## O que sobe

| Peça | Onde | Observação |
|---|---|---|
| Aplicação (Java 21, Spring Boot) | Space do Hugging Face, Docker, porta 7860 | CPU básica gratuita; dorme após um período sem acesso e volta no próximo |
| PostgreSQL 16 | Neon (plano gratuito) | contas, sessões, carteira e livro; o banco também suspende quando ocioso |
| Redis | **não usado** | o cache fica em memória (`MW_CACHE_TYPE=memory`); o Redis é opcional |

## 1. Banco no Neon

1. Crie um projeto no Neon (PostgreSQL 16) e um banco, por exemplo `meridianwealth`.
2. Anote **host**, **usuário**, **senha** e o nome do banco (o painel mostra os quatro; use o host **direto**, não o do *pooler*, porque o Flyway usa comandos que o pooler em modo transação não aceita).
3. Monte a URL **JDBC** (não a `postgresql://` que o painel mostra):

   ```
   jdbc:postgresql://<host>/<banco>?sslmode=require
   ```

O Flyway cria as tabelas sozinho na primeira partida.

## 2. Space no Hugging Face

1. Crie um Space: **SDK Docker**, em branco, público ou privado (o nome vira `usuario/nome`).
2. Em **Settings > Variables and secrets**, cadastre como **Secrets** (nunca como Variables) o que é sensível e como **Variables** o resto:

| Nome | Tipo | Valor |
|---|---|---|
| `MW_PERSISTENCE` | variável | `true` |
| `MW_EXCLUDE_AUTOCONFIG` | variável | `org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration` (liga banco e Flyway, mantém o Redis desligado) |
| `DB_URL` | variável | a URL JDBC do passo 1 |
| `DB_USER` | variável | usuário do Neon |
| `DB_PASSWORD` | **secret** | senha do Neon |
| `DB_POOL_SIZE` | variável | `5` (o plano gratuito limita conexões) |
| `MW_JWT_SECRET` | **secret** | segredo forte, 32 caracteres ou mais (`openssl rand -base64 48`); sem ele as sessões morrem a cada reinício |
| `MW_TRUSTED_PROXIES` | variável | ver [Atrás do proxy](#atrás-do-proxy-do-hugging-face) |
| `MW_METRICS_TOKEN` | **secret**, opcional | 24 caracteres ou mais, só se for coletar métricas |
| `FINNHUB_API_KEY`, `FRED_API_KEY`, `MW_BRAPI_TOKEN`, `MW_GOOGLE_CLIENT_ID` | opcionais | iguais às do README; sem elas o sistema usa as fontes sem chave e mostra o que faltar |

## 3. Publicar

Duas formas:

- **Pelo GitHub (recomendada):** em *Settings > Secrets and variables > Actions* do repositório, cadastre `HF_TOKEN` (token do Hugging Face com permissão de escrita) e `HF_SPACE` (`usuario/nome`). Depois rode o workflow **Publicar no Hugging Face Space** (aba *Actions*, botão *Run workflow*). Ele monta o conteúdo com o `Dockerfile` e o `README.md` de `deploy/hf/` (que trazem `sdk: docker` e `app_port: 7860`) e envia ao Space.
- **À mão:** clone o Space, copie o repositório para dentro dele, troque o `Dockerfile` e o `README.md` pelos de `deploy/hf/` e faça `git push`.

O Space compila a imagem (alguns minutos) e sobe na porta 7860.

## 4. Conferir

```
curl -s https://<usuario>-<nome>.hf.space/actuator/health/liveness
```

Deve responder `{"status":"UP"}`. O `/actuator/health` agregado pode dizer `DEGRADED` (HTTP 200) enquanto falta dado de algum provedor: é normal e não é falha (ver docs/observabilidade.md). Depois: abrir a página, entrar em modo demonstração, criar uma conta, salvar uma posição e **reiniciar o Space** (*Settings > Factory reboot*): a conta e a posição precisam continuar lá, o que prova que estão no Neon e não em memória.

## Atrás do proxy do Hugging Face

O tráfego chega ao contêiner por um proxy do Hugging Face. Sem `MW_TRUSTED_PROXIES`, todos os visitantes aparecem com o IP do proxy e dividem **um único balde** de limite de requisições (300 por minuto), e o cookie de sessão não recebe o atributo `Secure` (o servidor não sabe que a conexão original é HTTPS). Como o contêiner só é alcançável pelo proxy, é aceitável listar as faixas privadas, por exemplo `10.0.0.0/8,172.16.0.0/12,192.168.0.0/16`, **depois de conferir o endereço de origem** pelo aviso nos logs (ver abaixo, ponto 1). Não ligue `MW_FORCE_HTTPS` antes de o proxy ser confiável: sem isso o redirecionamento entraria em laço.

## Pontos a verificar no primeiro deploy

Estes itens dependem do ambiente real e não puderam ser validados antes:

1. **Faixa do proxy:** com `MW_TRUSTED_PROXIES` vazio, a primeira requisição que chegar com `X-Forwarded-For` gera nos *Logs* do Space o aviso `X-Forwarded-For ignorado: a conexão vem de <endereço>, que não está em MW_TRUSTED_PROXIES`; esse é o endereço do proxy (só ele é registrado, nunca o IP do visitante). Liste-o (ou a faixa que o contém) em `MW_TRUSTED_PROXIES`, reinicie e confira que o aviso sumiu, que o cookie `mw_rt` sai com `Secure` e que a resposta traz `Strict-Transport-Security` e `wss://` na CSP. Enquanto o proxy não é confiável, o servidor acha que a conexão é HTTP: sem `Secure` no cookie, sem HSTS e com `ws://` na CSP.
2. **Cripto:** a Binance bloqueia algumas regiões de nuvem (HTTP 451); o failover para Coinbase e Kraken existe (ADR 005), mas só se prova no Space (dívida 13). O status do stream mostra a bolsa em uso.
3. **Iframe:** a página do Space no huggingface.co embute a aplicação num iframe, e a CSP do projeto proíbe ser embutido (`frame-ancestors 'none'`, por segurança). Use o endereço direto `https://<usuario>-<nome>.hf.space`.
4. **Partida a frio:** o Neon suspende o banco ocioso, então a primeira conexão pode levar alguns segundos (`DB_CONNECT_TIMEOUT_MS`, 30 s por padrão).
5. **Fontes de dados:** os IPs do Space podem ser limitados por Yahoo e brapi; sem dado, a tela diz "sem dado em tempo real" (nunca inventa preço).

## Reverter

- **Versão anterior:** rode o workflow de novo a partir de um commit anterior (*Run workflow* permite escolher a branch ou a tag) ou dê `git revert` e publique.
- **Banco:** as migrações do Flyway só avançam; antes de uma versão que migre o banco, use o *branching* do Neon para guardar uma cópia.
- **Segredo vazado:** troque o valor em *Settings* do Space (e a senha no Neon) e reinicie; trocar `MW_JWT_SECRET` encerra todas as sessões.

## Limites do plano gratuito

Recursos compartilhados e variáveis ao longo do tempo (consulte os planos atuais do Hugging Face e do Neon: os valores mudam e não foram copiados aqui). Em geral: o Space dorme sem acesso e o banco suspende ocioso, então o primeiro acesso depois de uma pausa é lento. Não há backup automático além do que o plano do Neon oferece (dívida 43).
