# ADR 007 — Composição curada e geolocalização conservadora

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #19

## Contexto

O Raio-X mostra imóveis, área, vacância e localização de fundos reais. Os dois riscos são **afirmar um fato falso sobre um
ativo real** (um galpão no lugar errado, uma área trocada) e **depender de fontes frágeis em tempo de execução** (páginas
que mudam, bloqueios, limites do Nominatim).

## Decisão

1. **Dado curado e versionado.** Um script reproduzível (`scripts/curadoria`) lê as fontes e gera JSON em
   `src/main/resources/data/composicao`. A aplicação só lê esses arquivos; não raspa nada em produção.
2. **Cada dado traz fonte, tipo (oficial ou terceiros), data dos dados, data da consulta e SHA-256 do texto extraído.**
3. **Ausência é explícita:** campo que a fonte não publica fica `null` e a UI diz "não informado". Imóvel sem coordenada
   exige um motivo; o domínio recusa o contrário.
4. **Geocodificação só com concordância** de UF, cidade, CEP, via e número da porta (ou km em rodovia). Duas precisões
   (`ENDERECO` e `VIA`); `VIA` é rotulada aproximada. Em rodovia, resultado só de via é rejeitado.
5. **Falha alta na partida:** o carregador valida o arquivo (https, área positiva, coordenada dentro do Brasil, fundo
   repetido) e derruba a aplicação em vez de servir dado quebrado.
6. **Arquitetura:** domínio puro em `domain/composition`, porta `CompositionPort`, repositório JSON em `infrastructure`,
   `CompositionService` em `application`; a `web` só enxerga o caso de uso (ArchUnit).
7. **Links do Google Maps** apenas como URL documentada; nenhuma imagem é copiada (termos de uso).

## Consequências

- **Positivas:** nenhuma coordenada sem prova; o que é dúvida aparece como dúvida; a UI funciona sem rede além da API local;
  a curadoria é auditável e repetível.
- **Negativas:** o dado envelhece (atualização manual rodando o script); a cobertura de coordenadas é menor que a de
  imóveis (32 de 72 imóveis listados); só quatro fundos.

## Alternativas rejeitadas

- **Geocodificar em tempo de execução:** viola a política de uso do Nominatim e trocaria dado conferido por dado ao vivo sem
  conferência.
- **Aceitar o melhor resultado do geocodificador:** produziu um falso positivo real (posto no km 850 para um imóvel no
  km 863).
- **Preencher o que falta por inferência** (tipo de imóvel, cidade pelo nome): contraria a regra de dados reais.
