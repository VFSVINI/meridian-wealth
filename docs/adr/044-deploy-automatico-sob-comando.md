# ADR 044 — Deploy automático sob comando, com o commit no ar conferido

- **Status:** aceito
- **Data:** 2026-11-28
- **Issue:** #111 (item #62 do roadmap, Milestone 10)

## Contexto

O deploy no Render era manual: o dono entrava no painel e mandava publicar o último commit, sem confirmar o que realmente subiu. O Render está com `autoDeploy: false` por decisão do dono (publicar quando ele manda, não a cada push), e o plano gratuito dorme e demora para partir.

## Decisão

1. **Fluxo do GitHub Actions disparado só por `workflow_dispatch` ou por release publicada**, nunca por push: preserva o `autoDeploy: false`. Um teste trava isso.
2. **Portão de qualidade:** antes de publicar, o fluxo consulta a API de checagens e exige que "Build, testes, cobertura e estilo" tenha terminado em `success` **naquele commit**. Sem CI verde, nada sai.
3. **Imagem no GHCR** (`ghcr.io/<dono>/meridian-wealth`), com tags do commit e `latest` e rótulos OCI de origem e revisão: sem custo, sem conta nova, e quem quiser roda o projeto sem compilar. O Render **não** consome essa imagem: continua construindo a partir do `Dockerfile` do repositório, para não recriar o serviço existente.
4. **Deploy hook do Render** como segredo do repositório, mascarado nos logs; o fluxo para com uma mensagem clara quando o segredo ou a variável do endereço não existem. Ambiente `production` para o dono poder exigir aprovação.
5. **Verificação do que subiu:** `/actuator/info` mostra o commit (`RENDER_GIT_COMMIT` no Render, `GIT_COMMIT` na imagem). O fluxo repete a consulta a cada 15 s por até 15 minutos até ver o commit pedido, e só então confere o `liveness`. "O hook respondeu 200" não prova que a versão nova subiu.
6. **`actionlint` na CI** valida os fluxos.

## Consequências

- Duas peças de configuração manuais, uma vez: o segredo do hook e a variável do endereço (passo a passo em `docs/deploy.md`).
- **Não validado até o primeiro uso:** se o Render de fato expõe `RENDER_GIT_COMMIT` em tempo de execução. Se não expuser, o fluxo falha por tempo esgotado em vez de dar um falso sucesso, e a correção é trocar a origem do valor.
- O endereço público do serviço e o commit são informação pública; o hook (que contém uma chave) nunca é escrito em arquivo.
