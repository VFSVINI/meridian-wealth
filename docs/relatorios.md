# Radar de relatórios periódicos e balanços

Item #7 do roadmap (Issue #20). Mostra **quando** cada relatório deve sair, o que **já saiu** e um **veredito** mecânico
(POSITIVO, NEUTRO ou NEGATIVO) com os números e a regra que o geraram.

> Ferramenta analítica e educacional. O veredito não prevê preço e não é recomendação de investimento.

## Cobertura e fontes (consultadas em 2026-11-21)

| Emissores | Fonte (oficial) | O que lemos |
|---|---|---|
| PETR4, VALE3, ITUB4 | Dados abertos da CVM: ITR e DFP das companhias abertas (`dados.cvm.gov.br`) | receita e resultado líquido consolidado, do período e do mesmo período do ano anterior |
| HGLG11, KNRI11, XPML11, MXRF11 | Dados abertos da CVM: Informe Mensal de FII | dividend yield do mês e valor patrimonial da cota |

Ativos de outros mercados (ações dos EUA, cripto etc.) respondem **404** com a explicação: não há fonte integrada (dívida 23).
Cada arquivo guarda o SHA-256 dos zips lidos. O CNPJ de cada FII foi conferido pelo código ISIN do próprio Informe Mensal.

## Periodicidades: só as que têm base regulatória

O enunciado do item cita mensal, bimestral, trimestral (1T a 4T), semestral e anual. Para os emissores cobertos:

- **Mensal:** informe mensal de FII, até 15 dias após o mês (Anexo Normativo III da Resolução CVM 175).
- **Trimestral:** ITR das companhias (1T a 3T), até 45 dias após o trimestre (Resolução CVM 80, art. 31); informe trimestral de FII, até 45 dias (Anexo III da 175).
- **Anual:** DFP, até 3 meses após o exercício (Resolução CVM 80, art. 30), que traz o **4T**; informe anual de FII, até 90 dias.
- **Bimestral e semestral não existem** como obrigação desses emissores, então **não foram criadas** (o domínio recusaria um prazo sem fonte).

O prazo é o **máximo legal**; a divulgação real pode ser anterior. Quando a fonte registra a entrega, o calendário mostra a data
e se foi no prazo. A pontualidade usa a **primeira** entrega (uma reentrega posterior não apaga atraso nem cria pontualidade).
Entregas que a fonte não acompanha (informe trimestral e anual de FII) aparecem como "entrega não acompanhada nesta fonte".

## Regra do veredito (código: `ReportAnalyzer`)

Para cada métrica: variação = (atual − base) / |base|, em %, 2 casas, `HALF_EVEN`. A base é o **mesmo trimestre do ano anterior**
(companhias, do mesmo documento), o **exercício anterior** (DFP), a **média dos 3 meses anteriores** (dividend yield do FII) ou o
**mês anterior** (valor patrimonial da cota).

| Métrica | Limiar |
|---|---|
| Receita, resultado líquido | ±5% |
| Dividend yield (sobre a média de 3 meses) | ±5% |
| Valor patrimonial da cota (sobre o mês anterior) | ±1% |

Variação maior ou igual ao limiar: sinal +1; menor ou igual a menos o limiar: −1; no meio: 0. Só positivos: **POSITIVO**; só
negativos: **NEGATIVO**; positivos e negativos juntos, ou nenhum: **NEUTRO**.

Bordas tratadas e testadas: base negativa (sair de prejuízo para lucro é variação positiva); base zero ou ausente (sem sinal, e
sem nenhuma base não há veredito); **prejuízo no período nunca resulta em POSITIVO**; limiar inclusivo.

## Honestidade dos dados

- Bancos usam outro plano de contas: a receita é "Receitas da Intermediação Financeira" (3.01) e o resultado é a conta 3.09 (nas
  demais, 3.11). A conta e o rótulo da fonte aparecem em cada número.
- Só o **trimestre isolado** é comparado, não o acumulado do ano.
- Reentregas: os números são os da **última versão**; a data de pontualidade é a da **primeira** entrega.
- O dividend yield do Informe Mensal não tem unidade documentada no dicionário da CVM. O veredito compara o valor com o próprio
  histórico (a unidade se cancela) e a tela mostra o valor publicado, sem converter.
- O veredito não separa resultado não recorrente.
- Dado desatualizado: passados 45 dias da consulta (30 do informe mensal + 15 do prazo), a API e a tela avisam que pode haver
  relatório mais novo.

## Como atualizar

```powershell
python scripts/curadoria/gerar_relatorios.py --cache target/cvm-cache   # baixa os zips da CVM (centenas de MB)
python -m unittest scripts/curadoria/test_gerar_relatorios.py
```

A aplicação **não** consulta a CVM em tempo de execução; lê os JSON versionados em `data/relatorios`. Revise o diff antes de commitar.

## API

`GET /api/reports` (resumo por ativo), `GET /api/reports/{symbol}` (relatórios com sinais, calendário, regra e fontes; 404 sem dado).
