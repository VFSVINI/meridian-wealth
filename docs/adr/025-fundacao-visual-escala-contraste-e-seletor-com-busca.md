# ADR 025 — Fundação visual: escala, contraste e seletor de ativo com busca

- **Status:** aceito
- **Data:** 2026-11-23
- **Issue:** #60 (item #30 do roadmap)

## Contexto

O primeiro uso do site no ar (notebook comum, com escala de 125%) mostrou quatro problemas de base: a tela não se ajustava e exigia Ctrl +/-; o tema claro parecia "lavado"; a saudação dizia "Bom dia" à 00h24; e escolher um ativo era rolar uma lista longa, o que não escala para centenas de ativos.

A medição mudou o diagnóstico do contraste: as cores de **texto** dos dois temas já passavam no mínimo AA (4,5:1). O problema do tema claro era de **superfície**: cartão (`#fbf8f1`) quase igual ao fundo (`#f5f1e8`), bordas apagadas e gráfico creme sobre creme.

## Decisão

1. **Escala:** a barra lateral começa recolhida até 1279 px de largura (o notebook de 1366 px a 125% tem cerca de 1093 px), a menos que o usuário já tenha escolhido; espaçamentos, títulos e cartões usam `clamp()`; entre 981 e 1180 px, com a lateral recolhida, as páginas mantêm duas colunas em vez de empilhar tudo. Sem zoom automático por script: ele esconderia o problema e deixaria o texto pequeno.
2. **Tema claro:** fundo creme mais escuro (`#eeeadd`) e cartões brancos com sombra suave, borda de controle com contraste 3:1 sobre o cartão, e ouro, verde e vermelho levemente mais escuros para manter 4,5:1 sobre o novo fundo. O escuro continua o padrão. Os pares de cor são conferidos por teste.
3. **Seletor de ativo:** um combobox (`picker.js`) com busca por símbolo, nome, classe e bolsa, ordenada por relevância, com teclado e atributos ARIA. Ele recebe a lista de ativos como argumento, então o catálogo grande do item #33 entra sem mudar a interface.
4. **Saudação:** madrugada (0h–4h59), dia, tarde e noite, no fuso de Brasília.

## Consequências

- Erro que cometi e peguei medindo: uma regra que pintava todo `canvas` de branco no tema claro escondia as camadas de baixo do gráfico (que empilha canvas transparentes). Foi removida e um teste proíbe regra que torne todo `canvas` opaco.
- O CSS continua sem etapa de build (ADR 003); a verificação visual foi feita em 390, 768, 1093 e 1366 px nas 16 rotas.
- Não foi testado em dispositivo real nem em outros navegadores além do Chromium do painel de pré-visualização.

## Alternativas descartadas

- **`zoom` automático por script:** esconde o problema, encolhe o texto e é inconsistente entre navegadores.
- **Remover o tema claro:** o usuário respondeu que prefere mantê-lo, corrigido.
- **Biblioteca de combobox de terceiros:** mais uma dependência a baixar, auditar e fixar por checksum para um componente de 80 linhas.
