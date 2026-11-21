# Fixtures da CVM (dados abertos)

Recortes **reais**, sem edição de conteúdo (apenas linhas filtradas, na codificação original ISO-8859-1), de
`https://dados.cvm.gov.br/dados/CIA_ABERTA/DOC/ITR/DADOS/itr_cia_aberta_2026.zip`, baixado em 2026-11-21:

| Arquivo | Conteúdo |
|---|---|
| `itr-2026-indice-recorte.csv` | índice dos ITR (1T e 3T de 2026) da Petrobras (inclui a reentrega, versão 2, do 1T) e do Itaú Unibanco Holding |
| `itr-2026-dre-con-recorte.csv` | DRE consolidada dessas entregas, só as contas 3.01 (receita), 3.09 (resultado, bancos) e 3.11 (resultado, demais) |

Usados por `scripts/curadoria/test_gerar_relatorios.py` e pelos testes do radar de relatórios.
