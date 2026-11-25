"""Gera a composição de TODOS os FIIs com imóveis publicados na CVM (item #34, ADR 035).

Fonte oficial e única: dados abertos da CVM, Informe Trimestral de FII (https://dados.cvm.gov.br/dados/FII/DOC/INF_TRIMESTRAL/DADOS/):
  inf_trimestral_fii_imovel_AAAA.csv, inf_trimestral_fii_imovel_renda_acabado_inquilino_AAAA.csv e inf_trimestral_fii_geral_AAAA.csv,
mais inf_mensal_fii_geral_AAAA.csv (código ISIN e nome). O código de negociação (ticker) é ligado ao CNPJ pelo ISIN que o próprio Informe publica:
"BR" + 4 letras + "CTF" + 3 dígitos; o ticker é essas 4 letras + 11, 12, 13... e só vale se existir no catálogo como FII, se for único e se o nome do
fundo no catálogo concordar com o nome na CVM. Qualquer dúvida deixa o fundo de fora: nada é adivinhado.

Uso: python -I scripts/curadoria/gerar_composicao_cvm.py <pasta> [--sem-geocodificar] [--so-cache]
  <pasta> tem as subpastas tri2026, tri2025 (Informe Trimestral extraído) e men2026 (Informe Mensal extraído).
Só biblioteca padrão. Reaproveita a geocodificação conservadora de gerar_composicao.py (Nominatim, 1 req/s, cache).
"""
import argparse, csv, hashlib, json, re, sys, unicodedata
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))
import gerar_composicao as gc  # noqa: E402  (geocodificação, slug, cidade_uf, formatação)

CATALOGO = AQUI.parent.parent / "src" / "main" / "resources" / "catalog" / "assets.tsv"
CURADOS = {"HGLG11", "KNRI11", "XPML11", "MXRF11"}  # composição própria, mais rica (ver gerar_composicao.py)
URL = "https://dados.cvm.gov.br/dados/FII/DOC/INF_TRIMESTRAL/DADOS/inf_trimestral_fii_{ano}.zip"
GENERICAS = {"fundo", "fundos", "investimento", "investimentos", "imobiliario", "imobiliarios", "fii", "fiis", "de", "do", "da", "dos", "das", "e",
             "responsabilidade", "limitada", "ltda", "cotas", "classe", "unica", "em", "participacoes", "ativos", "renda", "resp", "invest", "imob"}


def ler(caminho):
    with open(caminho, encoding="latin-1", newline="") as f:
        return list(csv.DictReader(f, delimiter=";"))


def tokens(nome):
    s = unicodedata.normalize("NFKD", nome).encode("ascii", "ignore").decode().lower()
    return {t for t in re.findall(r"[a-z0-9]+", s) if len(t) >= 3 and t not in GENERICAS}


def catalogo_fiis():
    out = {}
    for linha in open(CATALOGO, encoding="utf-8"):
        if linha.startswith("#") or not linha.strip():
            continue
        c = linha.rstrip("\n").split("\t")
        if c[2] == "FII" and c[3] == "B3":
            out[c[0]] = c[1]
    return out


def mapear_tickers(isins, nomes, fiis):
    """CNPJ -> ticker, só quando o ISIN, o catálogo e o nome concordam e o ticker é único. Devolve (mapa, descartados)."""
    candidatos, descartados = {}, []
    for cnpj, isin in isins.items():
        m = re.fullmatch(r"BR([A-Z0-9]{4})CTF\d{3}", isin or "")
        if not m:
            continue
        for sufixo in ("11", "12", "13", "14", "15"):
            t = m.group(1) + sufixo
            if t in fiis:
                comum = tokens(fiis[t]) & tokens(nomes.get(cnpj, ""))
                if comum:
                    candidatos.setdefault(t, []).append(cnpj)
                else:
                    descartados.append((cnpj, t, "nome do catálogo não concorda com o da CVM"))
                break
    mapa = {}
    for t, cs in candidatos.items():
        if len(cs) == 1:
            mapa[cs[0]] = t
        else:
            descartados.extend((c, t, "mais de um CNPJ para o mesmo ticker") for c in cs)
    return mapa, descartados


def pct(frac):
    """Fração da CVM (0.1039) -> percentual em texto ('10.39'); vazio -> None."""
    if frac is None or str(frac).strip() in ("", "-"):
        return None
    try:
        return gc.txt((Decimal(str(frac).strip()) * 100).quantize(Decimal("0.0001")))
    except InvalidOperation:
        return None


UFS = set("AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split())


def local_cvm(end):
    """(cidade, uf, cep) do endereço como a CVM o publica; o que não der para ler com segurança fica None (e o geocodificador explica a falta).

    Formatos vistos: 'Rua X, 100 - Bairro, Cidade - UF', 'Rua X, 100, Cidade/UF, Brasil, 20021-290', 'Rodovia Y, km 3 - Bairro - Serra-ES, CEP 29163-370'.
    """
    if not end:
        return None, None, None
    t = end.strip()
    cep = None
    m = re.search(r"(?:CEP:?\s*)?(\d{2}\.?\d{3}-?\d{3})\s*\.?$", t)
    if m:
        cep = re.sub(r"\D", "", m.group(1))
        cep = cep[:5] + "-" + cep[5:]
        t = t[:m.start()].rstrip(" ,;-–")
    t = re.sub(r"[,\s]+Brasil\.?$", "", t, flags=re.I).strip(" ,;-–")
    m = re.search(r"^(.*?)[\s,]*[-–/,]\s*([A-Za-z]{2})\.?$", t)
    if not m or m.group(2).upper() not in UFS:
        cidade, uf, _ = gc.cidade_uf(end) + (None,)
        if uf is not None and uf not in UFS:   # sigla que não é UF não vira estado
            return None, None, cep
        return cidade, uf, cep
    uf, cabeca = m.group(2).upper(), m.group(1)
    ult = re.split(r"\s+[-–]\s+|,\s*|/", cabeca)[-1].strip()
    if not ult or re.search(r"\d", ult) or len(ult) < 3:
        return None, uf, cep
    return ult, uf, cep


def titulo(nome):
    return nome.title() if nome.isupper() else nome


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pasta")
    ap.add_argument("--sem-geocodificar", action="store_true")
    ap.add_argument("--so-cache", action="store_true")
    args = ap.parse_args()
    base = Path(args.pasta)
    geocod, ao_vivo, cache = not args.sem_geocodificar, not args.so_cache, gc.carregar_cache()

    imoveis, inquilinos, gerais = [], [], []
    for ano in ("2025", "2026"):
        d = base / f"tri{ano}"
        if d.exists():
            for r in ler(d / f"inf_trimestral_fii_imovel_{ano}.csv"):
                r["_ano"] = ano; imoveis.append(r)
            inquilinos += ler(d / f"inf_trimestral_fii_imovel_renda_acabado_inquilino_{ano}.csv")
            for r in ler(d / f"inf_trimestral_fii_geral_{ano}.csv"):
                gerais.append(r)
    for r in ler(base / "men2026" / "inf_mensal_fii_geral_2026.csv"):
        gerais.append(r)
    isins, nomes = {}, {}
    for g in gerais:
        if g.get("Codigo_ISIN"):
            isins[g["CNPJ_Fundo_Classe"]] = g["Codigo_ISIN"]
        nomes[g["CNPJ_Fundo_Classe"]] = g.get("Nome_Fundo_Classe") or nomes.get(g["CNPJ_Fundo_Classe"], "")
    fiis = catalogo_fiis()
    mapa, descartados = mapear_tickers(isins, nomes, fiis)

    ultima = {}
    for r in imoveis:
        k, v = r["CNPJ_Fundo_Classe"], (r["Data_Referencia"], int(r["Versao"]))
        if k not in ultima or v > ultima[k]:
            ultima[k] = v
    por_fundo = {}
    for r in imoveis:
        k = r["CNPJ_Fundo_Classe"]
        if k in mapa and (r["Data_Referencia"], int(r["Versao"])) == ultima[k]:
            por_fundo.setdefault(k, []).append(r)
    setores = {}
    for r in inquilinos:
        setores.setdefault((r["CNPJ_Fundo_Classe"], r["Data_Referencia"], int(r["Versao"]), r["Nome_Imovel"]), []).append(r)

    gc.SAIDA.mkdir(parents=True, exist_ok=True)
    total = sem_area = 0
    for cnpj, linhas in sorted(por_fundo.items(), key=lambda x: mapa[x[0]]):
        ticker = mapa[cnpj]
        if ticker in CURADOS:
            continue
        ref, versao = ultima[cnpj]
        ativos, usados, vistos, sem = [], [], {}, 0
        for r in linhas:
            try:
                area = Decimal(r["Area"].strip())
            except InvalidOperation:
                area = Decimal(0)
            if area <= 0:
                sem += 1
                continue
            end = (r["Endereco"] or "").strip()
            end = None if end in ("", "-") else end
            cidade, uf, cep = local_cvm(end)
            ss = []
            for s in setores.get((cnpj, ref, versao, r["Nome_Imovel"]), []):
                if s["Setor_Atuacao"].strip() not in ("", "-"):
                    ss.append({"setor": s["Setor_Atuacao"].strip(), "percentualDaReceitaDoImovel": pct(s["Percentual_Receita_Imovel"])})
                usados.append(s)
            nome = r["Nome_Imovel"].strip()
            if nome in ("", "-"):
                nome = "Imóvel sem nome publicado"   # o Informe deixou o nome em branco: não se inventa um
            vistos[nome] = vistos.get(nome, 0) + 1
            ativos.append({"nome": nome, "enderecoPublicado": end, "cidade": cidade, "uf": uf, "tipo": None, "areaM2": gc.txt(area),
                           "areaPublicada": r["Area"].strip(), "vacanciaPercentual": pct(r["Percentual_Vacancia"]),
                           "percentualDaReceitaDoFundo": pct(r["Percentual_Receitas_FII"]), "setoresDosInquilinos": ss,
                           "_n": vistos[nome], "cep": cep})
            usados.append(r)
        if not ativos:
            continue
        # id único dentro do fundo mesmo com nomes repetidos
        for a in ativos:
            suf = f" {a['_n']}" if vistos[a["nome"]] > 1 else ""
            a["nome_id"] = a["nome"] + suf
            del a["_n"]
        sem_area += sem
        ano = next(r["_ano"] for r in linhas)
        linhas_hash = [json.dumps({k: v for k, v in r.items() if not k.startswith("_")}, sort_keys=True, ensure_ascii=False) for r in usados]
        fonte = {"nome": "Informe Trimestral de FII, dados abertos da CVM (arquivos imovel e imovel_renda_acabado_inquilino)",
                 "url": URL.format(ano=ano), "tipo": "OFICIAL", "referenciaDosDados": ref, "consultadoEm": gc.HOJE,
                 "sha256DoTextoExtraido": gc.sha(linhas_hash)}
        com = semc = 0
        for n, a in enumerate(ativos, 1):
            a["id"] = gc.slug(ticker, a.pop("nome_id"))
            if geocod:
                coord, motivo = gc.geocodificar(a, cache, ao_vivo)
                a.pop("cep", None)
                a["coordenadas"] = coord
                if coord:
                    com += 1
                else:
                    a["coordenadasMotivoAusencia"] = motivo
                    semc += 1
            else:
                a.pop("cep", None)
                a["coordenadas"] = None
                a["coordenadasMotivoAusencia"] = "geocodificação ainda não executada para este imóvel"
                semc += 1
            print(f"  {ticker} {n}/{len(ativos)} {a['nome'][:36]:36} {'ok ' + a['coordenadas']['precisao'] if a.get('coordenadas') else 'sem coordenada'}", file=sys.stderr)
        avisos = [f"Fonte: Informe Trimestral de FII nos dados abertos da CVM, base {ref[8:]}/{ref[5:7]}/{ref[:4]}, versão {versao}. A CVM publica nome, endereço, "
                  "área, vacância, inadimplência, participação na receita e setores dos inquilinos; não publica o tipo do imóvel nem o nome dos inquilinos.",
                  f"O código {ticker} foi ligado ao CNPJ pelo código ISIN que o próprio Informe publica ({isins[cnpj]}), conferido com o catálogo de ativos e o nome do fundo.",
                  "O Informe separa imóveis para renda e para venda, acabados e em construção: esta lista traz todos os que informam área."]
        if sem:
            avisos.append(f"{sem} imóve{'l' if sem == 1 else 'is'} do Informe não trazem área positiva e não aparecem aqui.")
        doc = {"versaoDoEsquema": 1, "fundo": ticker, "nomeDoFundo": titulo(nomes[cnpj]), "fonte": fonte,
               "resumoDaGeocodificacao": {"comCoordenadas": com, "semCoordenadas": semc}, "cnpj": cnpj, "rotuloDaArea": "Área (m²)",
               "avisos": avisos, "ativos": ativos}
        (gc.SAIDA / f"{ticker.lower()}.json").write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
        total += len(ativos)
        print(f"{ticker}: {len(ativos)} imóveis ({com} com coordenada)", file=sys.stderr)
    print(f"fundos mapeados: {len(mapa)}; com imóveis gravados: {len(por_fundo)}; imóveis: {total}; sem área: {sem_area}; descartados no mapeamento: {len(descartados)}", file=sys.stderr)
    for c, t, m in descartados[:40]:
        print(f"  descartado {c} {t}: {m}", file=sys.stderr)


if __name__ == "__main__":
    main()
