"""Gera src/main/resources/catalog/assets.tsv a partir das listas públicas de símbolos (item #33, ADR 034).

Fontes (baixadas por quem roda o script; o projeto só guarda o resultado compacto):
  - Nasdaq Trader: nasdaqlisted.txt e otherlisted.txt (https://www.nasdaqtrader.com/dynamic/SymDir/), ações e ETFs dos EUA
  - brapi: GET https://brapi.dev/api/quote/list?limit=2000&page=1 e page=2, em arquivos JSON (ações, units, BDRs, ETFs e fundos listados da B3)

Uso: python -I scripts/build-catalog.py <pasta-com-as-listas> <saida.tsv>
Só biblioteca padrão. A saída é determinística (ordenada) e cada linha tem: símbolo, nome, classe, bolsa, setor, tipo.
"""
import csv, json, re, sys
from datetime import date
from pathlib import Path

CURATED = set()  # preenchido de Universe.java pelo próprio teste; aqui só se evita colisão pelo mesmo critério (símbolo curado vence)

SECTOR = {
    'Energy Minerals': 'energia', 'Non-Energy Minerals': 'mineracao', 'Finance': 'financeiro',
    'Technology Services': 'tecnologia', 'Electronic Technology': 'tecnologia',
    'Consumer Non-Durables': 'consumo', 'Consumer Durables': 'consumo', 'Consumer Services': 'consumo', 'Retail Trade': 'consumo',
}
FUND_KIND = {'fii': 'FII', 'fi-agro': 'Fiagro', 'fi-infra': 'FI-Infra', 'fip': 'FIP', 'fidc': 'FIDC'}
SKIP_NAME = re.compile(r'\b(warrants?|rights?|units?|notes?|preferred|subordinated|debentures?|depositary shares? representing .*preferred)\b', re.I)


def clean(name, limit=90):
    name = re.sub(r'\s+', ' ', name.encode('utf-8', 'replace').decode('utf-8', 'replace').replace('\ufffd', '')).strip()
    name = name.replace('\t', ' ')
    return name if len(name) <= limit else name[:limit - 1].rstrip() + '…'


def b3(src):
    out = []
    for f in sorted(src.glob('brapi-*.json')):
        for x in json.load(open(f, encoding='utf-8', errors='replace')).get('stocks', []):
            sym, name, typ, sub = x['stock'].strip().upper(), clean(x.get('name') or x['stock']), x.get('type'), x.get('subType')
            if not re.fullmatch(r'[A-Z0-9]{4,7}', sym):
                continue
            if typ == 'stock':
                out.append((sym, name, 'Ação', 'B3', SECTOR.get(x.get('sector'), 'amplo'), 'Unit' if sub == 'unit' else 'Ação'))
            elif typ == 'bdr':
                out.append((sym, name, 'Ação', 'B3', SECTOR.get(x.get('sector'), 'amplo'), 'BDR'))
            elif typ == 'fund' and sub == 'etf':
                out.append((sym, name, 'ETF', 'B3', 'amplo', 'ETF'))
            elif typ == 'fund':
                out.append((sym, name, 'FII', 'B3', 'imobiliario', FUND_KIND.get(sub, 'Fundo listado')))
    return out


def us(src):
    out = []
    with open(src / 'nasdaqlisted.txt', encoding='utf-8', errors='replace') as fh:
        for r in csv.DictReader(fh, delimiter='|'):
            if r['Symbol'] and r['Test Issue'] == 'N' and not r['Symbol'].startswith('File Creation'):
                out.append((r['Symbol'], r['Security Name'], r['ETF'] == 'Y'))
    with open(src / 'otherlisted.txt', encoding='utf-8', errors='replace') as fh:
        for r in csv.DictReader(fh, delimiter='|'):
            if r['ACT Symbol'] and r['Test Issue'] == 'N' and not r['ACT Symbol'].startswith('File Creation'):
                out.append((r['ACT Symbol'], r['Security Name'], r['ETF'] == 'Y'))
    rows = []
    for sym, name, etf in out:
        if '$' in sym or SKIP_NAME.search(name):
            continue
        ysym = sym.replace('.', '-').upper()
        if not re.fullmatch(r'[A-Z]{1,5}(-[A-Z])?', ysym):
            continue
        rows.append((ysym, clean(name), 'ETF' if etf else 'Ação', 'NYSE', 'amplo' if etf else 'amplo', 'ETF' if etf else 'Ação'))
    return rows


def main(src_dir, dst):
    src = Path(src_dir)
    seen, rows = set(), []
    for r in b3(src) + us(src):
        if r[0] in seen:
            continue
        seen.add(r[0]); rows.append(r)
    rows.sort(key=lambda r: (r[3], r[0]))
    with open(dst, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('# Catálogo de ativos negociados (item #33). Gerado por scripts/build-catalog.py em %s.\n' % date.today().isoformat())
        fh.write('# Fontes: Nasdaq Trader (nasdaqlisted.txt, otherlisted.txt) e brapi (lista de cotações da B3). Só símbolo, nome, classe, bolsa, setor e tipo; nenhum preço.\n')
        fh.write('# símbolo\tnome\tclasse\tbolsa\tsetor\ttipo\n')
        for r in rows:
            fh.write('\t'.join(r) + '\n')
    print(len(rows), 'ativos;', sum(1 for r in rows if r[3] == 'B3'), 'B3;', sum(1 for r in rows if r[3] == 'NYSE'), 'EUA')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
