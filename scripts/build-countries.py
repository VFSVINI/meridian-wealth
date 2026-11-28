"""Gera src/main/resources/static/data/countries.geojson a partir do ne_110m_admin_0_countries.geojson da Natural Earth (item #51, ADR 039).

Natural Earth é domínio público (https://www.naturalearthdata.com/about/terms-of-use/). O arquivo original (839 KB) vem de
https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson. Aqui ficam só o código de três letras
(ADM0_A3) e o nome, as coordenadas com duas casas e sem a Antártida (o mapa é de mercados). Uso: python -I scripts/build-countries.py <original> <saida>
"""
import json, sys


def rnd(x):
    return [[round(c[0], 2), round(c[1], 2)] for c in x]


def geom(g):
    if g['type'] == 'Polygon':
        return {'type': 'Polygon', 'coordinates': [rnd(r) for r in g['coordinates']]}
    return {'type': 'MultiPolygon', 'coordinates': [[rnd(r) for r in poly] for poly in g['coordinates']]}


def main(src, dst):
    d = json.load(open(src, encoding='utf-8'))
    out = []
    for f in d['features']:
        p = f['properties']
        if p['ADM0_A3'] == 'ATA':
            continue
        out.append({'type': 'Feature', 'properties': {'iso': p['ADM0_A3'], 'name': p['NAME']}, 'geometry': geom(f['geometry'])})
    out.sort(key=lambda f: f['properties']['iso'])
    with open(dst, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump({'type': 'FeatureCollection', 'features': out}, fh, ensure_ascii=False, separators=(',', ':'))
    print(len(out), 'países')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
