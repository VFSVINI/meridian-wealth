#!/usr/bin/env python3
"""Gera os dados de composição dos FIIs a partir de fontes públicas, de forma reproduzível.

Uso:  python scripts/curadoria/gerar_composicao.py [--sem-geocodificar]

Regras (docs/composicao.md):
  * todo dado vem de um documento ou página pública identificada por URL, com a data da consulta;
  * o texto publicado (área, endereço) é guardado ao lado do valor interpretado;
  * campo que a fonte não traz fica ausente (nulo), nunca estimado;
  * coordenada só entra quando a geocodificação (OpenStreetMap Nominatim) bate com a cidade, o estado,
    o CEP e a rua publicados; caso contrário o imóvel fica sem coordenada, com o motivo.

Só biblioteca padrão. Os resultados do Nominatim ficam em cache-nominatim.json (política de uso do OSM:
no máximo 1 requisição por segundo, User-Agent identificado e cache dos resultados).
"""
import argparse
import hashlib
import html
import json
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from datetime import date
from decimal import Decimal, ROUND_HALF_EVEN
from pathlib import Path

AQUI = Path(__file__).resolve().parent
SAIDA = AQUI.parent.parent / "src" / "main" / "resources" / "data" / "composicao"
CACHE = AQUI / "cache-nominatim.json"
HOJE = date.today().isoformat()
UA_SITE = "Mozilla/5.0"
UA_NOMINATIM = "MeridianWealth-curadoria/0.1 (https://github.com/VFSVINI/meridian-wealth)"

FONTES = {
    "HGLG11": "https://fnet.bmfbovespa.com.br/fnet/publico/exibirDocumento?id=1382074&cvm=true",
    "KNRI11": "https://www.kinea.com.br/fundos/fundo-imobiliario-kinea-renda-knri11/",
    "XPML11": "https://www.fundsexplorer.com.br/funds/xpml11",
    "MXRF11": "https://fnet.bmfbovespa.com.br/fnet/publico/exibirDocumento?id=1388005&cvm=true",
}
ESTADOS = {"Minas Gerais": "MG", "São Paulo": "SP", "Rio de Janeiro": "RJ", "Bahia": "BA", "Paraná": "PR"}


# ---------------------------------------------------------------- utilidades
def baixar(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA_SITE})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def achatar_fnet(raw):
    t = raw.decode("utf-8", errors="replace")
    t = re.sub(r"<script.*?</script>|<style.*?</style>", "", t, flags=re.S)
    t = re.sub(r"</(td|th)>", " | ", t)
    t = re.sub(r"</tr>|<br\s*/?>|</p>|</div>", "\n", t)
    return _linhas(t)


def achatar_site(raw):
    t = raw.decode("utf-8", errors="replace")
    t = re.sub(r"<script.*?</script>|<style.*?</style>|<svg.*?</svg>", "", t, flags=re.S)
    t = re.sub(r"</(td|th)>", " | ", t)
    t = re.sub(r"<(br|/p|/div|/li|/tr|/h\d|/section|/a)[^>]*>", "\n", t)
    return _linhas(t)


def _linhas(t):
    t = html.unescape(re.sub(r"<[^>]+>", "", t))
    ls = [re.sub(r"\s+", " ", x).strip() for x in t.split("\n")]
    return [x for x in ls if x]


def sha(linhas):
    return hashlib.sha256("\n".join(linhas).encode("utf-8")).hexdigest()


def numero(texto):
    """'62.587,38' -> Decimal('62587.38'); '41.930' -> 41930; '3.898.63' (erro de digitação da fonte) -> 3898.63."""
    s = texto.strip().replace("m²", "").replace("m2", "").strip()
    if "," in s:
        return Decimal(s.replace(".", "").replace(",", "."))
    if s.count(".") >= 2:
        *resto, ultimo = s.split(".")
        return Decimal("".join(resto) + ("." + ultimo if len(ultimo) == 2 else ultimo))
    if s.count(".") == 1 and len(s.split(".")[1]) == 3:
        return Decimal(s.replace(".", ""))
    return Decimal(s)


def txt(d):
    return format(d.normalize(), "f") if d == d.to_integral() else format(d, "f")


def sem_acento(s):
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()


def cidade_uf(endereco):
    """Extrai cidade e UF do fim do endereço publicado, quando ele traz; senão (None, None)."""
    if not endereco:
        return None, None
    m = re.search(r"na cidade de ([^,]+), Estado de (.+)$", endereco)
    if m:
        return m.group(1).strip(), ESTADOS.get(m.group(2).strip())
    m = re.search(r"[,–-]\s*([^,–-]+?)\s*[,–-]\s*([A-Za-z]{2})\.?$", endereco.strip())
    if m and not re.search(r"\d", m.group(1)):
        return m.group(1).strip(), m.group(2).upper()
    m = re.search(r"Cidade de ([^,]+)$", endereco.strip())
    if m:
        return m.group(1).strip(), None
    return None, None


def slug(simbolo, nome):
    return simbolo.lower() + "-" + re.sub(r"[^a-z0-9]+", "-", sem_acento(nome)).strip("-")


# ---------------------------------------------------------------- HGLG11 (Informe Trimestral, FNET)
def analisar_hglg(L):
    # Só a seção 1.1.2.1.1 (imóveis para renda acabados): o mesmo documento repete o bloco "Área (m2):" na
    # seção 2.2.2 (alienações do trimestre), que não faz parte da carteira atual.
    ini = next(k for k, l in enumerate(L) if l == "1.1.2.1.1")
    fim_secao = next(k for k, l in enumerate(L) if l == "1.1.2.1.2")
    idx = [i for i, l in enumerate(L) if l == "Área (m2):" and ini < i < fim_secao]
    ativos = []
    for n, i in enumerate(idx):
        anterior = L[i - 2]
        if re.fullmatch(r"[\d.,%\s|-]*", anterior):  # sem linha de endereço: o nome está logo acima
            nome, endereco = L[i - 1], None
        else:
            nome, endereco = anterior, L[i - 1]
        area_pub = L[i + 1]
        pct = re.findall(r"([\d.]+,\d+)%", L[i + 4])
        fim = idx[n + 1] - 2 if n + 1 < len(idx) else next(
            (j for j in range(i + 5, len(L)) if re.fullmatch(r"\d+(\.\d+){2,}", L[j])), len(L))
        cauda = L[i + 5:fim]
        grupos, atual = [], []
        for linha in cauda:
            if linha == "|":
                grupos.append(atual)
                atual = []
            else:
                atual.append(re.sub(r"\s*\|$", "", linha))
        if atual:
            grupos.append(atual)
        nomes = grupos[0] if grupos else []
        pcts_imovel = grupos[1] if len(grupos) > 1 else []
        setores = [{"setor": s, "percentualDaReceitaDoImovel": txt(numero(p.rstrip("%")))}
                   for s, p in zip(nomes, pcts_imovel)
                   if re.fullmatch(r"[\d.]+,\d+%", p) and s.strip('"').strip() not in ("-", "")]  # "-" = sem dado
        cidade, uf = cidade_uf(endereco)
        ativos.append({
            "nome": nome, "enderecoPublicado": endereco, "cidade": cidade, "uf": uf,
            "tipo": None, "areaM2": txt(numero(area_pub)), "areaPublicada": area_pub,
            "vacanciaPercentual": txt(numero(pct[0])) if len(pct) > 0 else None,
            "percentualDaReceitaDoFundo": txt(numero(pct[2])) if len(pct) > 2 else None,
            "setoresDosInquilinos": setores,
        })
    return ativos


# ---------------------------------------------------------------- KNRI11 (página oficial da Kinea)
def analisar_knri(L):
    i = next(k for k, l in enumerate(L) if l == "Carteira" and L[k + 1].startswith("Portfólio composto por"))
    declarado = int(re.search(r"composto por (\d+) propriedades", L[i + 1]).group(1))
    ativos, j = [], i + 2
    while L[j] != "Mostrar mais ativos":
        nome, endereco, tipo, area = L[j:j + 4]
        j += 4
        cidade, uf = cidade_uf(endereco)
        if nome == "Centro de Distribuição Cabreúva":
            cidade = None  # a cidade só aparece no nome do imóvel, não no endereço publicado
        ativos.append({
            "nome": nome, "enderecoPublicado": endereco, "cidade": cidade, "uf": uf,
            "tipo": {"Edifício comercial": "EDIFICIO_COMERCIAL", "Galpão logístico": "GALPAO_LOGISTICO"}.get(tipo),
            "tipoPublicado": tipo, "areaM2": txt(numero(area)), "areaPublicada": area,
        })
    def depois(rotulo):
        return L[next(k for k, l in enumerate(L) if l == rotulo) + 1]
    metricas = {
        "ablTotalM2": txt(numero(depois("Área Bruta Locável"))), "ablTotalPublicada": depois("Área Bruta Locável"),
        "vacanciaFisicaPercentual": txt(numero(depois("Vacância física").rstrip("%"))),
        "vacanciaFinanceiraPercentual": txt(numero(depois("Vacância financeira").rstrip("%"))),
        "referenciaDasMetricas": "2026-11-18",
    }
    return ativos, declarado, metricas


# ---------------------------------------------------------------- XPML11 (Funds Explorer, terceiro)
def analisar_xpml(L):
    ativos = []
    for i, l in enumerate(L):
        if l.startswith("Endereço: ") and L[i + 2].startswith("Cidade: ") and L[i + 3].startswith("Área Bruta Locável: "):
            nome = L[i - 1]
            endereco = l[len("Endereço: "):]
            bairro = L[i + 1][len("Bairro:"):].strip() or None
            cid = L[i + 2][len("Cidade: "):].strip()
            area = L[i + 3][len("Área Bruta Locável: "):]
            cep = uf = cidade = None
            if re.fullmatch(r"\d{5} - \d{3}", cid):
                cep = cid.replace(" ", "")
            elif re.fullmatch(r"\d{5} - [A-Z]{2}", cid):
                cep, uf = cid[:5], cid[-2:]
            else:
                m = re.fullmatch(r"(.+) - ([A-Z]{2})", cid)
                cidade, uf = (m.group(1), m.group(2)) if m else (None, None)
            ativos.append({
                "nome": nome, "enderecoPublicado": endereco, "bairro": bairro, "cidade": cidade, "uf": uf,
                "cep": cep, "cidadePublicada": cid, "tipo": None,
                "areaM2": txt(numero(area)), "areaPublicada": area,
            })
    return ativos


# ---------------------------------------------------------------- MXRF11 (Informe Mensal, FNET)
def analisar_mxrf(L):
    def valor(padrao, mesma_linha=True):
        for k, l in enumerate(L):
            if re.search(padrao, l):
                for linha in (L[k], L[k + 1], L[k + 2]):
                    m = re.findall(r"(\d{1,3}(?:\.\d{3})*,\d{2})(?!\d)", linha)
                    if m and (linha is not L[k] or mesma_linha):
                        return numero(m[-1])
        raise SystemExit("MXRF11: rótulo não encontrado: " + padrao)
    ativo = valor(r"^1 \| Ativo – R\$")
    itens = [
        ("10.16", "CRI (Certificados de Recebíveis Imobiliários)", valor(r'Certificado de Recebíveis Imobiliários "CRI"')),
        ("10.8", "FII (cotas de outros fundos imobiliários)", valor(r"^Fundo de Investimento Imobiliário \(FII\)")),
        ("10.1", "Direitos reais sobre bens imóveis", valor(r"^Direitos reais sobre bens imóveis")),
        ("9", "Necessidades de liquidez", valor(r"Total mantido para as Necessidades de Liquidez")),
        ("11", "Valores a receber", valor(r"^Valores a Receber")),
    ]
    try:
        itens.insert(2, ("10.x", "Ações de sociedades de propósito exclusivo em FII",
                         valor(r"^Ações de Sociedades cujo único propósito")))
    except SystemExit:
        pass
    aloc = [{"codigo": c, "rotulo": r, "valorReais": f"{v:.2f}",
             "percentualDoAtivoCalculado": str((v / ativo * 100).quantize(Decimal("0.01"), ROUND_HALF_EVEN))}
            for c, r, v in itens]
    soma = sum(v for _, _, v in itens)
    patrimonio = valor(r"^2 \| Patrimônio Líquido")
    imoveis_venda = valor(r"^Imóveis para Venda Acabados")
    meta = {"ativoTotalReais": f"{ativo:.2f}", "patrimonioLiquidoReais": f"{patrimonio:.2f}",
            "somaDosItensReais": f"{soma:.2f}", "diferencaParaOAtivoReais": f"{ativo - soma:.2f}",
            "imoveisParaVendaAcabadosReais": f"{imoveis_venda:.2f}"}
    return aloc, meta


# ---------------------------------------------------------------- geocodificação
def carregar_cache():
    return json.loads(CACHE.read_text(encoding="utf-8")) if CACHE.exists() else {}


def consultar(q, cache, ao_vivo):
    if q in cache:
        return cache[q]
    if not ao_vivo:
        return None
    time.sleep(1.3)  # política do OSM: no máximo 1 requisição por segundo
    url = "https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode(
        {"q": q, "format": "jsonv2", "addressdetails": 1, "limit": 3, "countrycodes": "br"})
    req = urllib.request.Request(url, headers={"User-Agent": UA_NOMINATIM})
    with urllib.request.urlopen(req, timeout=60) as r:
        cache[q] = json.loads(r.read().decode("utf-8"))
    CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")
    return cache[q]


def tokens_rua(texto):
    t = re.sub(r"\b(avenida|av|rua|rodovia|estrada|alameda|praca|travessa|parque|km|sn|br)\b", " ", sem_acento(texto))
    return {w for w in re.findall(r"[a-z]{4,}", t)}


def aceitar(r, pub):
    a = r.get("address", {})
    if pub.get("uf") and a.get("ISO3166-2-lvl4") != "BR-" + pub["uf"]:
        return None
    if pub.get("cidade"):
        locais = {sem_acento(a.get(k, "")) for k in ("city", "town", "municipality", "village")}
        if sem_acento(pub["cidade"]) not in locais:
            return None
    if pub.get("cep") and a.get("postcode") and a["postcode"].replace("-", "")[:5] != pub["cep"].replace("-", "")[:5]:
        return None
    rua = a.get("road") or ""
    if not rua or not (tokens_rua(pub["rua"]) & tokens_rua(rua)):
        return None
    precisao = "ENDERECO" if a.get("house_number") else "VIA"
    numero_pub = numero_da_porta(pub.get("endereco", "")) or marco_km(pub.get("endereco", ""))
    if precisao == "ENDERECO" and numero_pub and re.sub(r"\D", "", a["house_number"]) != numero_pub:
        precisao = "VIA"  # o número achado não é o publicado (em rodovia, costuma ser o marco quilométrico)
    if precisao == "VIA" and re.match(r"(?i)(rodovia|rod\.|estrada|est\.|br[- ]?\d)", pub["rua"]):
        # O ponto de uma rodovia ou estrada pode estar a quilômetros do imóvel: não serve para localizá-lo.
        return "RODOVIA"
    return {"lat": f'{Decimal(r["lat"]):.6f}', "lng": f'{Decimal(r["lon"]):.6f}', "metodo": "OSM_NOMINATIM",
            "precisao": precisao, "enderecoEncontrado": r["display_name"],
            "osm": f'{r.get("osm_type")}/{r.get("osm_id")}', "geocodificadoEm": HOJE}


def numero_da_porta(endereco):
    """Primeiro número de porta do endereço publicado ('1.195' -> '1195'); ignora marcos de km e lotes."""
    sem_km = re.sub(r"(?i)\bkm\s*[\d.,]+|\blotes?\s+[\d\s\w]+?(?=,|$)|\bquadra\s+\d+", " ", endereco)
    m = re.search(r"(?<![\w.])(\d{1,3}(?:\.\d{3})+|\d{2,})(?![\w])", sem_km)
    return re.sub(r"\D", "", m.group(1)) if m else None


def marco_km(endereco):
    """Marco quilométrico publicado ('km 863' -> '863'); em rodovia ele faz o papel do número de porta."""
    m = re.search(r"(?i)\bkm\s*(\d[\d.,]*)", endereco)
    return re.sub(r"\D", "", m.group(1)) if m else None


def limpar_para_busca(s):
    s = re.sub(r"\(.*?\)", "", s)
    s = re.sub(r"\bnº\s*", "", s)
    s = re.sub(r"^venida\b", "Avenida", s)
    return re.sub(r"\s+", " ", s).strip(" ,.")


def geocodificar(ativo, cache, ao_vivo):
    end = ativo.get("enderecoPublicado")
    if not end:
        return None, "a fonte não publica o endereço"
    cidade, uf, cep = ativo.get("cidade"), ativo.get("uf"), ativo.get("cep")
    if not cidade and not cep:
        return None, "o endereço publicado não traz a cidade nem o CEP"
    partes = [p.strip() for p in end.split(",")]
    rua = partes[0]
    simples = ", ".join(p for p in partes[:2] if p == partes[0] or re.fullmatch(r"[\d.]+|km [\d.,]+", p))
    base_local = ", ".join(x for x in (cidade, uf) if x)
    consultas = []
    if cidade:
        consultas.append(f"{limpar_para_busca(end)}, Brasil")
        consultas.append(f"{simples}, {base_local}, Brasil")
    else:
        consultas.append(f"{limpar_para_busca(rua)}, {cep}, Brasil")
        if ativo.get("bairro"):
            consultas.append(f"{limpar_para_busca(rua)}, {ativo['bairro']}, {uf or ''}, Brasil".replace(", ,", ","))
    pub = {"uf": uf, "cidade": cidade, "cep": cep, "rua": rua, "endereco": end}
    for q in dict.fromkeys(consultas):
        achados = consultar(q, cache, ao_vivo)
        if achados is None:
            return None, "geocodificação desligada e sem cache"
        for r in achados:
            ok = aceitar(r, pub)
            if ok == "RODOVIA":
                return None, ("a via publicada é uma rodovia ou estrada e o OpenStreetMap só localiza a via, "
                              "não o número: o ponto poderia estar a quilômetros do imóvel")
            if ok:
                ok["consulta"] = q
                return ok, None
    return None, "nenhum resultado do OpenStreetMap bateu com cidade, estado, CEP e rua publicados"


# ---------------------------------------------------------------- montagem
def montar(simbolo, nome_fundo, fonte, ativos, extras, geocod, ao_vivo, cache):
    com = sem = 0
    for n, a in enumerate(ativos, 1):
        a["id"] = slug(simbolo, a["nome"])
        if geocod:
            coord, motivo = geocodificar(a, cache, ao_vivo)
            a["coordenadas"] = coord
            if coord:
                com += 1
            else:
                a["coordenadasMotivoAusencia"] = motivo
                sem += 1
        print(f"  {simbolo} {n}/{len(ativos)} {a['nome'][:40]:40} {'ok ' + a['coordenadas']['precisao'] if a.get('coordenadas') else 'sem coordenada'}",
              file=sys.stderr)
    doc = {"versaoDoEsquema": 1, "fundo": simbolo, "nomeDoFundo": nome_fundo, "fonte": fonte,
           "resumoDaGeocodificacao": {"comCoordenadas": com, "semCoordenadas": sem} if geocod else None}
    doc.update(extras)
    doc["ativos"] = ativos
    return doc


def fonte(url, tipo, nome, referencia, linhas):
    return {"nome": nome, "url": url, "tipo": tipo, "referenciaDosDados": referencia, "consultadoEm": HOJE,
            "sha256DoTextoExtraido": sha(linhas)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sem-geocodificar", action="store_true")
    ap.add_argument("--so-cache", action="store_true", help="usa só o cache do Nominatim, sem rede")
    args = ap.parse_args()
    geocod, ao_vivo, cache = not args.sem_geocodificar, not args.so_cache, carregar_cache()
    SAIDA.mkdir(parents=True, exist_ok=True)

    L = achatar_fnet(baixar(FONTES["HGLG11"]))
    nome = L[3].split("|")[1].strip()
    ref = re.search(r"Data de Encerramento do Trimestre: \| (\d{2})/(\d{2})/(\d{4})", " ".join(L))
    referencia = f"{ref.group(3)}-{ref.group(2)}-{ref.group(1)}"
    cnpj = re.search(r"\d\d\.\d{3}\.\d{3}/\d{4}-\d\d", L[3]).group(0)
    ativos = analisar_hglg(L)
    soma_receita = sum(numero(a["percentualDaReceitaDoFundo"]) for a in ativos if a["percentualDaReceitaDoFundo"])
    doc = montar("HGLG11", nome.title() if nome.isupper() else nome,
                 fonte(FONTES["HGLG11"], "OFICIAL", "Informe Trimestral de FII (Anexo 39-II, CVM), via B3/FNET", referencia, L),
                 ativos, {"cnpj": cnpj, "rotuloDaArea": "Área (m2)",
                          "avisos": ["O documento não informa tipo de imóvel, nome dos inquilinos nem participação do fundo; "
                                     "traz só os setores dos inquilinos acima de 10% da receita de cada imóvel.",
                                     "O documento não define 'ABL': o campo publicado é 'Área (m2)'.",
                                     "O mesmo Informe registra, na seção 2.2.2 (alienações do trimestre), a venda do HGLG Itapevi I em "
                                     "30/07/2026 (34.286,00 m²); ele não consta da carteira atual.",
                                     f"A soma dos percentuais de receita publicados por imóvel é {soma_receita:.2f}%; o documento "
                                     "não explica a diferença para 100%.",
                                     "Três imóveis (HGLG Embu, HGLG Guarulhos e HGLG Parque Novo Mundo) trazem '-' no lugar dos "
                                     "setores dos inquilinos: ficam sem setores informados."]},
                 geocod, ao_vivo, cache)
    gravar("HGLG11", doc)

    L = achatar_site(baixar(FONTES["KNRI11"]))
    ativos, declarado, metricas = analisar_knri(L)
    doc = montar("KNRI11", "Kinea Renda Imobiliária FII",
                 fonte(FONTES["KNRI11"], "OFICIAL", "Página do fundo no site da gestora (Kinea Investimentos)", "2026-10", L),
                 ativos, {"rotuloDaArea": "Área Bruta Locável", "quantidadeDeAtivosDeclarada": declarado,
                          "metricasDoFundo": metricas,
                          "avisos": [f"A página lista {len(ativos)} dos {declarado} imóveis declarados; os demais ficam atrás de "
                                     "'Mostrar mais ativos' e não constam desta lista.",
                                     "A página não informa os inquilinos por imóvel nem a participação do fundo (exceto no Biosquare, "
                                     "fora desta lista)."]},
                 geocod, ao_vivo, cache)
    gravar("KNRI11", doc)

    L = achatar_site(baixar(FONTES["XPML11"]))
    ativos = analisar_xpml(L)
    doc = montar("XPML11", "XP Malls FII",
                 fonte(FONTES["XPML11"], "TERCEIRO", "Funds Explorer (agregador; as páginas oficiais da gestora respondem 403)", None, L),
                 ativos, {"rotuloDaArea": "Área Bruta Locável",
                          "avisos": ["Fonte de terceiros: a lista de imóveis não traz data-base. A página mais recente do fundo no "
                                     "agregador é de 18/11/2026.",
                                     "Não informado pela fonte: participação do fundo, inquilinos, vacância e se a área é total ou própria.",
                                     "Em vários shoppings a fonte publica o CEP no lugar da cidade."]},
                 geocod, ao_vivo, cache)
    gravar("XPML11", doc)

    L = achatar_fnet(baixar(FONTES["MXRF11"]))
    aloc, meta = analisar_mxrf(L)
    doc = montar("MXRF11", "Maxi Renda FII", fonte(FONTES["MXRF11"], "OFICIAL",
                 "Informe Mensal de FII (Anexo 39-I, CVM), via B3/FNET", "2026-10", L), [],
                 {"cnpj": re.search(r"\d\d\.\d{3}\.\d{3}/\d{4}-\d\d", L[3]).group(0),
                  "classificacaoNoInforme": "Tijolo; Subclassificação: Renda; Gestão: Ativa; Segmento de Atuação: Logística",
                  "alocacoes": aloc, "totais": meta,
                  "avisos": ["Os percentuais são calculados sobre o ativo total; o documento publica só valores em reais.",
                             "O Informe não lista as posições individuais (emissores de CRI, outros FIIs) nem detalha os imóveis; "
                             "os R$ em 'Imóveis para Venda Acabados' não têm localização publicada.",
                             "Sites de terceiros classificam o fundo como 'Papéis' e 'Híbrido'; o Informe oficial o classifica como "
                             "'Tijolo'."]},
                 False, ao_vivo, cache)
    gravar("MXRF11", doc)


def gravar(simbolo, doc):
    (SAIDA / f"{simbolo.lower()}.json").write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{simbolo}: {len(doc['ativos'])} imóveis", file=sys.stderr)


if __name__ == "__main__":
    main()
