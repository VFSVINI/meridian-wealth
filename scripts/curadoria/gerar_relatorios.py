#!/usr/bin/env python3
"""Curadoria do radar de relatórios (item #7).

Lê os dados abertos da CVM (https://dados.cvm.gov.br) e grava, em src/main/resources/data/relatorios/*.json, só o
que a fonte publica:

* companhias abertas (PETR4, VALE3, ITUB4): ITR (1T a 3T) e DFP (anual) com receita (conta 3.01) e resultado
  líquido consolidado (conta 3.11) do período e do mesmo período do ano anterior, ambos dentro do mesmo documento;
* FIIs (HGLG11, KNRI11, XPML11, MXRF11): Informe Mensal (dividend yield do mês e valor patrimonial da cota).

Nada é estimado. O veredito NÃO é calculado aqui: as regras ficam no domínio Java, com testes.

Uso: python scripts/curadoria/gerar_relatorios.py [--cache DIR]
Só biblioteca padrão. O diretório de cache guarda os zips da CVM (centenas de MB) e o SHA-256 de cada um vai no JSON.
"""
import argparse
import csv
import hashlib
import io
import json
import sys
import unicodedata
import urllib.request
import zipfile
from datetime import date, datetime, timedelta
from decimal import Decimal
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
SAIDA = RAIZ / "src/main/resources/data/relatorios"
BASE = "https://dados.cvm.gov.br/dados"
UA = "MeridianWealth-curadoria/1.0 (projeto educacional; dados abertos da CVM)"
CSV_ENC = "latin-1"

EMPRESAS = [
    ("PETR4", "33.000.167/0001-01", "Petróleo Brasileiro S.A. - Petrobras"),
    ("VALE3", "33.592.510/0001-54", "Vale S.A."),
    ("ITUB4", "60.872.504/0001-23", "Itaú Unibanco Holding S.A."),
]
# CNPJ conferido no Informe Mensal da CVM (campos Nome_Fundo_Classe e Codigo_ISIN, que traz o código do fundo).
FIIS = [
    ("HGLG11", "11.728.688/0001-47", "BRHGLGCTF004"),
    ("KNRI11", "12.005.956/0001-65", "BRKNRICTF007"),
    ("XPML11", "28.757.546/0001-00", "BRXPMLCTF000"),
    ("MXRF11", "97.521.225/0001-25", "BRMXRFCTF008"),
]
MESES_FII = 8


def baixar(caminho_relativo, cache):
    destino = cache / Path(caminho_relativo).name
    if not destino.exists():
        print("baixando", caminho_relativo, file=sys.stderr)
        req = urllib.request.Request(f"{BASE}/{caminho_relativo}", headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=600) as r, open(destino, "wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
    return destino


def sha256(caminho):
    h = hashlib.sha256()
    with open(caminho, "rb") as f:
        while chunk := f.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def linhas(zip_path, membro):
    with zipfile.ZipFile(zip_path) as z:
        with z.open(membro) as f:
            yield from csv.DictReader(io.TextIOWrapper(f, encoding=CSV_ENC, newline=""), delimiter=";")


def iso(texto):
    return datetime.strptime(texto, "%Y-%m-%d").date()


RESULTADO_LIQUIDO = "LUCRO/PREJUIZO CONSOLIDADO DO PERIODO"  # conta 3.11 nas empresas e 3.09 nos bancos


def sem_acento(texto):
    return unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode().strip().upper()


def exato(valor):
    """Decimal como texto, sem zeros à direita nem notação científica."""
    texto = format(valor, "f")
    return texto.rstrip("0").rstrip(".") if "." in texto else texto


def ordem(linha):
    """ORDEM_EXERC sem acento: 'ULTIMO' (período atual) ou 'PENULTIMO' (mesmo período do ano anterior)."""
    texto = unicodedata.normalize("NFKD", linha["ORDEM_EXERC"]).encode("ascii", "ignore").decode().upper()
    return "PENULTIMO" if texto.startswith("PEN") else "ULTIMO"


def um_ano_antes(d):
    try:
        return d.replace(year=d.year - 1)
    except ValueError:  # 29 de fevereiro
        return d.replace(year=d.year - 1, day=28)


def valor_em_reais(linha):
    fator = Decimal(1000) if linha["ESCALA_MOEDA"].strip().upper() == "MIL" else Decimal(1)
    return Decimal(linha["VL_CONTA"]) * fator


def https(link):
    return link.replace("http://", "https://", 1)


def relatorios_da_empresa(cnpj, zips):
    """zips: lista de (categoria 'ITR'|'DFP', ano, caminho do zip). Devolve os relatórios mais novos primeiro."""
    saida = []
    for categoria, ano, zip_path in zips:
        prefixo = f"{categoria.lower()}_cia_aberta"
        indice = {}
        primeira = {}
        for x in linhas(zip_path, f"{prefixo}_{ano}.csv"):
            if x["CNPJ_CIA"] != cnpj:
                continue
            ref = x["DT_REFER"]
            primeira[ref] = min(primeira.get(ref, x["DT_RECEB"]), x["DT_RECEB"])
            if ref not in indice or int(x["VERSAO"]) > int(indice[ref]["VERSAO"]):
                indice[ref] = x
        if not indice:
            continue
        cd_cvm = next(iter(indice.values()))["CD_CVM"]
        contas = {}
        for x in linhas(zip_path, f"{prefixo}_DRE_con_{ano}.csv"):
            if x["CD_CVM"] == cd_cvm and (x["CD_CONTA"] == "3.01" or sem_acento(x["DS_CONTA"]) == RESULTADO_LIQUIDO):
                contas.setdefault((x["DT_REFER"], x["VERSAO"]), []).append(x)
        for ref, doc in sorted(indice.items()):
            rows = contas.get((ref, doc["VERSAO"]), [])
            fim = iso(ref)
            maximo = 95 if categoria == "ITR" else 400  # ITR: só o trimestre isolado; DFP: o exercício
            metricas = []
            for codigo, rotulo_padrao in (("RECEITA", "Receita"), ("RESULTADO_LIQUIDO", "Resultado líquido consolidado")):
                atual = base = None
                for x in rows:
                    if (x["CD_CONTA"] == "3.01") != (codigo == "RECEITA"):
                        continue
                    if (iso(x["DT_FIM_EXERC"]) - iso(x["DT_INI_EXERC"])).days > maximo:
                        continue
                    fim_linha = iso(x["DT_FIM_EXERC"])
                    if ordem(x) == "ULTIMO" and fim_linha == fim:
                        atual = x
                    elif ordem(x) == "PENULTIMO" and fim_linha == um_ano_antes(fim):
                        base = x
                if atual is None:
                    continue
                metricas.append({
                    "codigo": codigo,
                    "contaNaFonte": atual["CD_CONTA"],
                    "rotuloNaFonte": atual["DS_CONTA"],
                    "rotulo": rotulo_padrao,
                    "unidade": "BRL",
                    "atual": exato(valor_em_reais(atual)),
                    "periodoAtual": f'{atual["DT_INI_EXERC"]} a {atual["DT_FIM_EXERC"]}',
                    "base": exato(valor_em_reais(base)) if base else None,
                    "periodoBase": f'{base["DT_INI_EXERC"]} a {base["DT_FIM_EXERC"]}' if base else None,
                })
            trimestre = (fim.month - 1) // 3 + 1
            saida.append({
                "id": f"{categoria}-{ref}",
                "periodicidade": "TRIMESTRAL" if categoria == "ITR" else "ANUAL",
                "tipoDeDocumento": categoria,
                "rotulo": f"{trimestre}T{fim.year}" if categoria == "ITR" else f"Anual {fim.year} (inclui o 4T)",
                "fimDoPeriodo": ref,
                "primeiraEntregaEm": primeira[ref],
                "versaoEntregueEm": doc["DT_RECEB"],
                "versao": int(doc["VERSAO"]),
                "link": https(doc["LINK_DOC"]),
                "metricas": metricas,
            })
    saida.sort(key=lambda r: r["fimDoPeriodo"], reverse=True)
    return saida


def meses_do_fii(cnpj, zips):
    geral, comp, primeira = {}, {}, {}
    for ano, zip_path in zips:
        for x in linhas(zip_path, f"inf_mensal_fii_geral_{ano}.csv"):
            if x["CNPJ_Fundo_Classe"] == cnpj:
                chave = x["Data_Referencia"]
                primeira[chave] = min(primeira.get(chave, x["Data_Entrega"]), x["Data_Entrega"])
                if chave not in geral or int(x["Versao"]) > int(geral[chave]["Versao"]):
                    geral[chave] = x
        for x in linhas(zip_path, f"inf_mensal_fii_complemento_{ano}.csv"):
            if x["CNPJ_Fundo_Classe"] == cnpj:
                chave = (x["Data_Referencia"], x["Versao"])
                comp[chave] = x
    meses = []
    for ref in sorted(geral, reverse=True)[:MESES_FII + 3]:
        g = geral[ref]
        c = comp.get((ref, g["Versao"]))
        if not c:
            continue

        def num(campo):
            v = c.get(campo, "").strip()
            return exato(Decimal(v)) if v else None
        meses.append({
            "id": f"INF_MENSAL-{ref}",
            "fimDoPeriodo": fim_do_mes(ref),
            "primeiraEntregaEm": primeira[ref],
            "versaoEntregueEm": g["Data_Entrega"],
            "versao": int(g["Versao"]),
            "dividendYieldPercentual": num("Percentual_Dividend_Yield_Mes"),
            "valorPatrimonialPorCota": num("Valor_Patrimonial_Cotas"),
            "patrimonioLiquido": num("Patrimonio_Liquido"),
            "rentabilidadeEfetivaPercentual": num("Percentual_Rentabilidade_Efetiva_Mes"),
            "cotistas": int(c["Total_Numero_Cotistas"]) if c.get("Total_Numero_Cotistas", "").strip() else None,
            "encerramentoDoExercicio": g["Encerramento_Exercicio_Social"],
        })
    return meses, geral


def fim_do_mes(ref):
    """O Informe Mensal usa o 1º dia do mês como Data_Referencia; o período vai até o último dia."""
    d = iso(ref)
    proximo = d.replace(year=d.year + (d.month == 12), month=d.month % 12 + 1, day=1)
    return (proximo - timedelta(days=1)).isoformat()


def gravar(simbolo, conteudo):
    SAIDA.mkdir(parents=True, exist_ok=True)
    (SAIDA / f"{simbolo.lower()}.json").write_text(
        json.dumps(conteudo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=str(RAIZ / "target/cvm-cache"))
    args = ap.parse_args()
    cache = Path(args.cache)
    cache.mkdir(parents=True, exist_ok=True)
    hoje = date.today().isoformat()

    itr = [("ITR", a, baixar(f"CIA_ABERTA/DOC/ITR/DADOS/itr_cia_aberta_{a}.zip", cache)) for a in (2025, 2026)]
    dfp = [("DFP", 2025, baixar("CIA_ABERTA/DOC/DFP/DADOS/dfp_cia_aberta_2025.zip", cache))]
    fonte_itr = ("Dados abertos da CVM: ITR das companhias abertas", "https://dados.cvm.gov.br/dataset/cia_aberta-doc-itr")
    fonte_dfp = ("Dados abertos da CVM: DFP das companhias abertas", "https://dados.cvm.gov.br/dataset/cia_aberta-doc-dfp")
    for simbolo, cnpj, nome in EMPRESAS:
        rel = relatorios_da_empresa(cnpj, itr + dfp)
        gravar(simbolo, {
            "versaoDoEsquema": 1, "ativo": simbolo, "tipo": "EMPRESA", "nome": nome, "cnpj": cnpj,
            "fontes": [
                {"nome": fonte_itr[0], "url": fonte_itr[1], "tipo": "OFICIAL", "consultadoEm": hoje,
                 "arquivos": {z.name: sha256(z) for _, _, z in itr}},
                {"nome": fonte_dfp[0], "url": fonte_dfp[1], "tipo": "OFICIAL", "consultadoEm": hoje,
                 "arquivos": {z.name: sha256(z) for _, _, z in dfp}}],
            "relatorios": rel,
            "avisos": ["Os valores são os publicados no documento entregue à CVM (versão mais recente de cada período), em reais, "
                       "convertidos da escala informada. A base de comparação é o mesmo período do ano anterior, do mesmo documento."],
        })
        print(simbolo, len(rel), "relatórios", file=sys.stderr)

    fii_zips = [(a, baixar(f"FII/DOC/INF_MENSAL/DADOS/inf_mensal_fii_{a}.zip", cache)) for a in (2025, 2026)]
    for simbolo, cnpj, isin in FIIS:
        meses, _ = meses_do_fii(cnpj, fii_zips)
        gravar(simbolo, {
            "versaoDoEsquema": 1, "ativo": simbolo, "tipo": "FII", "cnpj": cnpj, "codigoIsin": isin,
            "fontes": [{"nome": "Dados abertos da CVM: Informe Mensal de FII",
                        "url": "https://dados.cvm.gov.br/dataset/fii-doc-inf_mensal", "tipo": "OFICIAL",
                        "consultadoEm": hoje, "arquivos": {z.name: sha256(z) for _, z in fii_zips}}],
            "meses": meses,
            "avisos": ["Dividend yield e valor patrimonial da cota são os do Informe Mensal (versão mais recente de cada mês)."],
        })
        print(simbolo, len(meses), "meses", file=sys.stderr)


if __name__ == "__main__":
    main()
