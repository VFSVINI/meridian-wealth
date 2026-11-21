# Raio-X de composição e geolocalização de ativos reais

Item #6 do roadmap (Issue #19). Mostra **de que é feito** um fundo imobiliário (imóveis, área, vacância, setores dos
inquilinos, alocação) e **onde ficam** os imóveis (coordenadas, com links para o Google Maps em Satélite e Street View).

> Ferramenta analítica e educacional. Não é recomendação de investimento.

## Regra de ouro: nada é inventado

Cada dado vem de uma **fonte identificável, com data**. O que a fonte não publica aparece como **"não informado"**, com o
motivo quando é coordenada. Não há estimativa, preenchimento nem inferência (o tipo do imóvel só é preenchido quando a
própria fonte o declara).

## Fontes (consultadas em 2026-11-21)

| Fundo | Fonte | Tipo | Referência dos dados |
|---|---|---|---|
| HGLG11 | Informe Trimestral de FII (Anexo 39-II, CVM) via B3/FNET, documento `id=1382074` | Oficial | 30/09/2026 |
| KNRI11 | Página do fundo no site da gestora (Kinea Investimentos) | Oficial | 10/2026 (vacância em 18/11/2026) |
| XPML11 | Funds Explorer (as páginas oficiais respondem 403) | **Terceiros** | não informada |
| MXRF11 | Informe Mensal de FII (CVM) via B3/FNET, documento `id=1388005` | Oficial | 10/2026 |

A UI e a API mostram o selo "Fonte oficial" ou "Fonte de terceiros", a data dos dados e a data da consulta. Cada arquivo
guarda o SHA-256 do texto extraído da fonte, para auditar a curadoria.

## Como o dado é produzido

`scripts/curadoria/gerar_composicao.py` (Python, só biblioteca padrão) baixa as fontes, extrai os imóveis, geocodifica os
endereços e grava `src/main/resources/data/composicao/*.json`. O resultado é commitado: a aplicação **não** consulta
essas fontes nem o Nominatim em tempo de execução. Para atualizar, rode o script de novo e revise o diff.

```powershell
python scripts/curadoria/gerar_composicao.py            # baixa, geocodifica (≤ 1 requisição/s) e grava
python scripts/curadoria/gerar_composicao.py --so-cache # usa só o cache do Nominatim
python -m unittest scripts/curadoria/test_gerar_composicao.py
```

### Geocodificação conservadora

Usa o OpenStreetMap Nominatim (política: no máximo 1 requisição/s, User-Agent identificado, resultados em cache). Um
resultado só vira coordenada se **concordar com o endereço publicado**: mesma UF e cidade, mesmo CEP quando conhecido, via
com palavras em comum e **mesmo número da porta** (em rodovia, o mesmo marco de quilômetro). Níveis de precisão:

- `ENDERECO`: o OSM localizou a porta publicada (posição do imóvel).
- `VIA`: só a via bateu; a posição é **aproximada** e a UI avisa.
- Sem coordenada: nenhum resultado bateu, ou a via é rodovia e o OSM só localiza a via (o ponto poderia estar a
  quilômetros do imóvel). O motivo aparece na tabela.

Exemplo real de falso positivo barrado: o CD Pouso Alegre (Rodovia Fernão Dias, km 863) casava com um posto no km 850.

Dados do mapa © colaboradores do OpenStreetMap, licença ODbL (atribuição exibida na UI e na API).

### Links do Google Maps

Formato documentado em "Maps URLs" (developers.google.com/maps/documentation/urls, lido em 2026-11-21):

- Satélite: `https://www.google.com/maps/@?api=1&map_action=map&center=LAT%2CLNG&zoom=17&basemap=satellite`
- Street View: `https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=LAT%2CLNG`

Os links só abrem o local; nenhuma imagem do Google é copiada nem incorporada. Se não houver imagem de Street View no
ponto, o Google mostra isso ao abrir o link.

## API

| Rota | Resposta |
|---|---|
| `GET /api/composition` | composição de todos os fundos curados |
| `GET /api/composition/{symbol}` | composição de um fundo; **404** com mensagem clara se não há dado curado |
| `GET /api/composition/geo` | só os imóveis com coordenada validada (base do mapa do item #16) |

Números saem como texto exato (ADR 002). A resposta traz `source`, `notices`, `attribution` e `disclaimer`.

## O que ficou de fora (e por quê)

- **HGLG11:** a soma dos percentuais de receita por imóvel publicados é 70,85%; o documento não explica a diferença e o
  produto **não** a apresenta como 100%. Três imóveis (Embu, Guarulhos e Parque Novo Mundo) trazem `-` no lugar dos setores
  e ficam sem setores. O HGLG Itapevi I, vendido em 30/07/2026, não consta da carteira atual e está registrado em aviso.
  Só 6 dos 36 imóveis têm coordenada (os demais não trazem cidade nem CEP no informe).
- **KNRI11:** o site lista 17 dos 20 imóveis declarados; a soma da ABL listada (660.609 m²) é menor que a ABL publicada
  (765.508 m²). A vacância por imóvel não é publicada (só a do fundo).
- **XPML11:** fonte de terceiros e sem data de referência; a classificação do fundo pode divergir entre fontes.
- **MXRF11:** é fundo de papel; só tem alocação (CRI 69,53%, FII 12,86%, ações 5,00%, direitos reais 5,35%, liquidez 6,89%,
  valores a receber 0,37%). O Informe se classifica como "Tijolo" e agregadores como "Papéis": o conflito é mostrado.
- Inquilinos: o informe traz só **setores** dos inquilinos acima de 10% da receita, não nomes. Nomes não são inventados.
- Plantas industriais, plataformas e shoppings do enunciado do item não têm fundo curado ainda (só galpões e escritórios);
  entram quando houver fonte pública com data.

## Validação

Testes: `CompositionDomainTest` (invariantes do domínio), `JsonCompositionRepositoryTest` (fontes https, coordenadas com
prova, caixa por UF, somas, falha alta com o nome do arquivo), `ApiIntegrationTest` (rotas) e `test_gerar_composicao.py`
(regras de aceitação do geocodificador). A tabela do painel foi verificada no navegador com dados reais.
