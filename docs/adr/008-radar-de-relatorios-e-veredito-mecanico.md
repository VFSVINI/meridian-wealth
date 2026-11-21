# ADR 008 — Radar de relatórios e veredito mecânico

- **Status:** aceito
- **Data:** 2026-11-21
- **Issue:** #20

## Contexto

Relatórios e balanços movem preços. O risco é dar um "veredito" que pareça recomendação, com número errado, base de comparação
trocada ou prazo inventado.

## Decisão

1. **Fonte oficial e versionada:** dados abertos da CVM (ITR, DFP e Informe Mensal de FII), lidos por script reproduzível
   (`scripts/curadoria/gerar_relatorios.py`) e gravados em `data/relatorios`. A aplicação não consulta a CVM em execução.
2. **O veredito é calculado no domínio** (`ReportAnalyzer`), nunca no script nem no arquivo: regra, limiares e bordas ficam em
   código testado. Todo sinal carrega os números, a base, o limiar e uma frase explicativa.
3. **Regra simples e auditável:** variação percentual contra uma base do próprio relatório, limiares por métrica, combinação de
   sinais; prejuízo nunca é POSITIVO; sem base, sem veredito (em vez de um palpite).
4. **Prazos só com base regulatória** (Resolução CVM 80 e Anexo III da 175, lidos nos PDFs oficiais). Bimestral e semestral não
   foram criados. O prazo é o máximo legal e é rotulado assim.
5. **Pontualidade pela primeira entrega**; os números, pela última versão.
6. **Dado curado envelhece:** `DataAge` sinaliza retrato desatualizado (45 dias aqui; 135 na composição do item #6).
7. Carregador estrito (falha na partida), como no ADR 007.

## Consequências

- **Positivas:** veredito reproduzível e explicável; nenhuma data ou número sem fonte; a regra muda em um lugar só.
- **Negativas:** cobertura restrita (3 companhias e 4 FIIs); ações dos EUA sem fonte (SEC/EDGAR); atualização manual; o
  veredito é uma leitura contábil e ignora não recorrentes.

## Alternativas rejeitadas

- **Pontuação ponderada ou modelo estatístico:** menos auditável e sem base para os pesos.
- **Calendário de divulgação por raspagem de agenda de RI:** frágil e sem fonte oficial estável.
- **Veredito preenchido no script:** esconderia a regra fora do código testado.
