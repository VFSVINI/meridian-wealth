# Deploy gratuito: Render + Neon

Runbook para publicar o MeridianWealth sem custo: a aplicação roda num **serviço web do Render** (Docker, plano gratuito) e os dados de contas e carteira ficam num **PostgreSQL do Neon**. As contas e os segredos são **seus**: nada aqui cria conta nem guarda chave no repositório.

> **Por que não o Hugging Face Spaces?** O roadmap previa o Hugging Face, mas ao tentar o primeiro deploy o site mostrou que **Spaces com Docker exigem plano pago (PRO)**; só Spaces estáticos são gratuitos. A pesquisa de 2026-11-23 também descartou o Koyeb (passou a exigir cartão) e o Back4App (256 MB, pouco para Java). Ver o [ADR 024](adr/024-deploy-gratuito-render-e-neon.md).

> **Estado de validação:** o blueprint e este runbook foram conferidos por teste (coerência de variáveis, memória da JVM, ausência de segredo) e a imagem compila na CI, mas o deploy em si **não foi concluído**. Os planos gratuitos mudam: confira os limites atuais em render.com/docs/free antes de começar. Os pontos que só aparecem com o serviço no ar estão em [Pontos a verificar no primeiro deploy](#pontos-a-verificar-no-primeiro-deploy).

## O que sobe

| Peça | Onde | Observação |
|---|---|---|
| Aplicação (Java 21, Spring Boot) | Render, serviço web Docker, plano gratuito | 512 MB de RAM e CPU compartilhada pequena; dorme após 15 minutos sem acesso e leva de dezenas de segundos a alguns minutos para acordar |
| PostgreSQL 16 | Neon (plano gratuito) | contas, sessões, carteira e livro; o banco também suspende quando ocioso |
| Redis | **não usado** | o cache fica em memória (`MW_CACHE_TYPE=memory`) |

## 1. Banco no Neon

1. Crie um projeto no Neon com **PostgreSQL 16**, na região **AWS US East 2 (Ohio)** (a mesma do serviço no Render, para a latência ficar baixa), e use o banco padrão `neondb`.
2. Em **Conectar**, **desligue** o *Connection pooling* e anote **host** (sem `-pooler` no nome), **usuário** e **senha**. Use o host **direto**: o Flyway usa comandos que o pooler em modo transação não aceita.
3. Monte a URL **JDBC** (não a `postgresql://` que o painel mostra, e sem `channel_binding`):

   ```
   jdbc:postgresql://<host>/neondb?sslmode=require
   ```

O Flyway cria as tabelas sozinho na primeira partida. **Nunca cole a senha em chat, issue ou arquivo do repositório.**

## 2. Serviço no Render

1. Crie a conta em render.com e conecte o GitHub (confira se o cadastro pede cartão; se pedir, pare e decida antes).
2. **New > Blueprint**, escolha o repositório `meridian-wealth` e o Render lê o [`render.yaml`](../render.yaml): serviço web Docker, plano gratuito, região Ohio, verificação de saúde em `/actuator/health/liveness`, limite de memória da JVM e o banco ligado com o Redis desligado.
3. O Render pede os valores marcados como segredo. Preencha **você mesmo** no painel:

| Nome | Valor |
|---|---|
| `DB_URL` | a URL JDBC do passo 1 |
| `DB_USER` | usuário do Neon (`neondb_owner`) |
| `DB_PASSWORD` | senha do Neon |

`MW_JWT_SECRET` é gerado pelo próprio Render (valor aleatório forte). Em **Environment**, você pode acrescentar depois, se quiser: `MW_TRUSTED_PROXIES` (ver abaixo), `MW_METRICS_TOKEN` (24 caracteres ou mais), `FINNHUB_API_KEY`, `FRED_API_KEY`, `MW_BRAPI_TOKEN` e `MW_GOOGLE_CLIENT_ID`. Sem as chaves, o sistema usa as fontes sem chave e mostra o que faltar.

4. Crie o serviço. O Render compila a imagem pelo `Dockerfile` da raiz (alguns minutos) e define a `PORT` sozinho; a aplicação já a lê.

Sem o Blueprint, dá para criar um **Web Service** à mão com os mesmos valores (runtime Docker, plano Free, *Health Check Path* `/actuator/health/liveness` e as variáveis do `render.yaml`).

## 3. Conferir

```
curl -s https://<nome>.onrender.com/actuator/health/liveness
```

Deve responder `{"status":"UP"}` (a primeira chamada depois de uma pausa demora). O `/actuator/health` agregado pode dizer `DEGRADED` (HTTP 200) enquanto falta dado de algum provedor: é normal (ver docs/observabilidade.md). Depois: abrir a página, entrar em modo demonstração, criar uma conta, salvar uma posição e **reiniciar o serviço** (*Manual Deploy > Restart*): a conta e a posição precisam continuar lá, o que prova que estão no Neon e não em memória.

## Atrás do proxy do Render

O tráfego chega ao contêiner por um proxy do Render. Sem `MW_TRUSTED_PROXIES`, todos os visitantes aparecem com o IP do proxy e dividem **um único balde** de limite de requisições (300 por minuto), e o cookie de sessão não recebe o atributo `Secure` (o servidor não sabe que a conexão original é HTTPS). Veja o ponto 1 abaixo para descobrir o endereço do proxy. Não ligue `MW_FORCE_HTTPS` antes de o proxy ser confiável: sem isso o redirecionamento entraria em laço.

## Pontos a verificar no primeiro deploy

Estes itens dependem do ambiente real e não puderam ser validados antes:

1. **Faixa do proxy:** com `MW_TRUSTED_PROXIES` vazio, a primeira requisição que chegar com `X-Forwarded-For` gera nos *Logs* do serviço o aviso `X-Forwarded-For ignorado: a conexão vem de <endereço>, que não está em MW_TRUSTED_PROXIES` (só o endereço do proxy é registrado, nunca o IP do visitante). Liste esse endereço (ou a faixa que o contém, se o Render documentar uma) em `MW_TRUSTED_PROXIES`, reinicie e confira que o aviso sumiu, que o cookie `mw_rt` sai com `Secure` e que a resposta traz `Strict-Transport-Security` e `wss://` na CSP. Enquanto o proxy não é confiável, o servidor acha que a conexão é HTTP: sem `Secure` no cookie, sem HSTS e com `ws://` na CSP.
2. **Memória:** o ajuste da JVM (`-Xmx280m`, medido cerca de 236 MB em uso leve) cabe nos 512 MB, mas o consumo durante um Monte Carlo pesado não foi medido. Se o serviço for reiniciado por falta de memória, os *Logs* e as métricas do Render mostram.
3. **Partida lenta:** a CPU gratuita é pequena e o Spring Boot pode levar minutos para subir; se a verificação de saúde do Render derrubar o serviço antes, aumente a tolerância ou use um plano pago.
4. **Cripto:** a Binance bloqueia algumas regiões de nuvem (HTTP 451); o failover para Coinbase e Kraken existe (ADR 005), mas só se prova no ar (dívida 13). O status do stream mostra a bolsa em uso.
5. **Partida a frio do Neon:** o banco suspende ocioso e a primeira conexão pode levar alguns segundos (`DB_CONNECT_TIMEOUT_MS`, 30 s por padrão).
6. **Fontes de dados:** os IPs do Render podem ser limitados por Yahoo e brapi; sem dado, a tela diz "sem dado em tempo real" (nunca inventa preço).
7. **Tráfego:** as fontes divergem sobre o limite de banda gratuita e sobre a suspensão ao estourá-lo; confira em render.com/docs/free.

## Reverter

- **Versão anterior:** no painel do serviço, *Events* permite refazer o deploy de um commit anterior; ou dê `git revert` e publique.
- **Banco:** as migrações do Flyway só avançam; antes de uma versão que migre o banco, use o *branching* do Neon para guardar uma cópia.
- **Segredo vazado:** troque o valor no painel do Render (e a senha no Neon) e reinicie; trocar `MW_JWT_SECRET` encerra todas as sessões.

## Limites do plano gratuito

Recursos compartilhados e variáveis ao longo do tempo (os valores mudam e não foram copiados aqui; confira os planos atuais do Render e do Neon). Em geral: o serviço dorme sem acesso e o banco suspende ocioso, então o primeiro acesso depois de uma pausa é lento; não há disco persistente (os dados ficam no Neon); não há backup automático além do que o plano do Neon oferece (dívida 43).
