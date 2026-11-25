# ADR 035 — Composição e mapa de todos os FIIs com imóveis publicados na CVM

- **Status:** aceito
- **Data:** 2026-11-25
- **Issue:** #84 (item #34 do roadmap)

## Contexto

O Raio-X de composição e o Mapa de ativos reais tinham 4 fundos curados à mão (HGLG11, KNRI11, XPML11 e MXRF11), cada um de uma fonte diferente. O dono pediu todos os fundos imobiliários. Curar centenas de fundos à mão é inviável e repetiria o risco de dado errado; a CVM, porém, publica nos dados abertos o Informe Trimestral de FII em arquivos estruturados, com os imóveis de cada fundo.

## Decisão

1. **Fonte única e oficial:** dados abertos da CVM, Informe Trimestral de FII (`inf_trimestral_fii_imovel`, `..._imovel_renda_acabado_inquilino` e `..._geral`), mais o Informe Mensal (`inf_mensal_fii_geral`) para o ISIN e o nome. Arquivos baixados em 2026-11-25 com a permissão do dono (2,2 MB + 2,5 MB + 1,4 MB + 10 KB de dicionário), de dados.cvm.gov.br. O projeto guarda só o resultado (um JSON por fundo em `data/composicao`) e o script que o gera; a aplicação não consulta a CVM nem o Nominatim em tempo de execução.
2. **Ticker ligado ao CNPJ pelo ISIN**, que o próprio Informe publica (`BR` + 4 letras + `CTF` + 3 dígitos): o ticker é as 4 letras mais 11, 12, 13... e **só vale** se existir no catálogo como FII (ADR 034), se o nome do catálogo concordar com o da CVM (palavras distintivas em comum) e se nenhum outro CNPJ reivindicar o mesmo ticker. Qualquer dúvida deixa o fundo de fora e o script lista o motivo. Os 4 fundos curados à mão ficam como estão (têm campos que a CVM não publica).
3. **Última referência de cada fundo:** só a data-base e a versão mais recentes, com todos os imóveis que informam área positiva (o domínio exige área; os que não informam são contados no aviso do fundo e não aparecem). Percentuais da CVM (frações) viram percentuais; vacância e participação ausentes ficam "não informado"; setor "-" é descartado; **o tipo do imóvel fica nulo**, porque a CVM não o publica.
4. **Coordenadas:** a mesma geocodificação conservadora do ADR 018 (Nominatim, no máximo 1 requisição por segundo, cache, só resultado que concorda com cidade, UF, CEP e rua, com a precisão à vista). Endereços da CVM trazem formatos variados: um leitor tolerante extrai cidade, UF e CEP e devolve "não lido" quando não tem certeza, e então o imóvel fica sem coordenada, com o motivo.
5. **Mapa para centenas de fundos:** busca do fundo pelo código (no lugar de um botão por fundo), filtros por UF e tipo, agrupamento por proximidade que já existia, lista com "mostrar mais", fontes resumidas por origem e comparação limitada aos 15 maiores do filtro. O marcador é dourado (cor por fundo não ajuda com centenas). As respostas JSON do servidor passam a sair comprimidas (a composição inteira fica grande).

## Consequências

- Só fundos de tijolo aparecem no mapa; fundos de papel (CRI) não têm imóveis no Informe e continuam só com alocação, quando houver.
- Muitos endereços ficam sem coordenada (cidade ilegível, rodovia, nenhum resultado que concorde): a tela diz o motivo de cada um, em vez de chutar a posição.
- O dado envelhece: o script deve rodar de novo a cada trimestre e o diff deve ser revisado.

## Alternativas descartadas

- **Curar mais fundos à mão (FNET, sites de gestoras):** não escala e mistura fontes.
- **Ligar ticker e CNPJ pelo nome:** fundos têm nomes parecidos; o ISIN é determinístico.
- **Estimar a posição pelo CEP ou pela cidade:** seria um ponto inventado; o projeto prefere "sem coordenada" com motivo.
