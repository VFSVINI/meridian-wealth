/* Mapa de ativos, aba Mundo (item #53): as bolsas dos ativos que a plataforma acompanha, sobre as fronteiras da Natural Earth (domínio público). Preço, variação e pregão aberto vêm
   das cotações reais da API (rows); a cidade de cada bolsa é um fato público. Cor do país = variação média dos ativos mostrados (a mesma conta do Mercados: fechamento sobre a abertura
   da barra); país sem ativo conectado fica pontilhado e "não informado", nunca neutro. Cripto, câmbio e commodities não têm lugar e ficam de fora, ditos. Depende de app.js (rows, universe, $, esc, f, hasData). Sem global novo. */
(function () {
  const EX = {
    B3: ['São Paulo · B3', 'BRA', -46.63, -23.55], NYSE: ['Nova York · NYSE', 'USA', -74.0, 40.71], XETRA: ['Frankfurt · Xetra', 'DEU', 8.68, 50.11], EURONEXT_AMS: ['Amsterdã · Euronext', 'NLD', 4.9, 52.37],
    LSE: ['Londres · LSE', 'GBR', -0.13, 51.51], TSE: ['Tóquio · TSE', 'JPN', 139.69, 35.69], HKEX: ['Hong Kong · HKEX', 'CHN', 114.17, 22.32], ASX: ['Sydney · ASX', 'AUS', 151.21, -33.87],
    TADAWUL: ['Riad · Tadawul', 'SAU', 46.68, 24.71], JSE: ['Joanesburgo · JSE', 'ZAF', 28.05, -26.2]
  };
  const KINDS = [['todos', 'Todos'], ['Ação', 'Ações'], ['FII', 'FIIs'], ['ETF', 'ETFs']];
  const MW = {geo: null, paths: null, kind: 'todos', pick: 'B3', loading: false, tab: 'mundo'};
  const W = 1000, H = 520;
  const onPage = () => { const p = document.querySelector('[data-page="mapa"]'); return !!p && !p.hidden; };
  const paint = (el, html) => { if (el && el.dataset.h !== html) { el.innerHTML = html; el.dataset.h = html; } };
  const ND = '<span class="gap">não informado</span>';
  const money = (u, v) => (u.currency === 'USD' ? 'US$ ' : u.currency === 'BRL' ? 'R$ ' : '') + f(v, v < 10 ? 4 : 2);

  function ne(lon, lat) {
    const l = lon * Math.PI / 180, p = lat * Math.PI / 180, p2 = p * p, p4 = p2 * p2;
    return [l * (0.8707 - 0.131979 * p2 + p4 * (-0.013791 + p4 * (0.003971 * p2 - 0.001529 * p4))), p * (1.007226 + p2 * (0.015085 + p4 * (-0.044475 + 0.028874 * p2 - 0.005916 * p4)))];
  }
  const fit = (() => {
    const pad = 8, edge = [ne(-180, 0)[0], ne(180, 0)[0]], top = ne(0, 83.6)[1], bot = ne(0, -56)[1], k = Math.min((W - 2 * pad) / (edge[1] - edge[0]), (H - 2 * pad) / (top - bot));
    return (lon, lat) => { const q = ne(lon, lat); return [(W - (edge[1] - edge[0]) * k) / 2 + (q[0] - edge[0]) * k, pad + (top - q[1]) * k]; };
  })();
  function build() {
    return MW.geo.features.map(ft => {
      const polys = ft.geometry.type === 'Polygon' ? [ft.geometry.coordinates] : ft.geometry.coordinates;
      let d = '';
      for (const poly of polys) for (const ring of poly) d += 'M' + ring.map(c => { const p = fit(c[0], c[1]); return p[0].toFixed(1) + ' ' + p[1].toFixed(1); }).join('L') + 'Z';
      return {iso: ft.properties.iso, name: ft.properties.name, d};
    });
  }

  const chg = r => (r && r.candle && r.candle.open > 0 ? r.candle.close / r.candle.open - 1 : null);
  const located = () => universe.filter(u => EX[u.exchange] && rows[u.symbol] && hasData(rows[u.symbol]) && (MW.kind === 'todos' || u.assetClass === MW.kind));
  function groups() {
    const g = new Map();
    located().forEach(u => { const k = u.exchange; if (!g.has(k)) g.set(k, []); g.get(k).push(u); });
    return [...g.entries()].map(([k, list]) => {
      const cs = list.map(u => chg(rows[u.symbol])).filter(v => v != null);
      return {k, list, avg: cs.length ? cs.reduce((a, b) => a + b, 0) / cs.length : null, open: list.some(u => rows[u.symbol].marketOpen)};
    });
  }
  const tint = v => { const t = Math.max(-1, Math.min(1, v / 0.03)); return t >= 0 ? `rgba(56,221,156,${(0.22 + 0.5 * t).toFixed(2)})` : `rgba(255,109,130,${(0.22 + 0.5 * -t).toFixed(2)})`; };
  const solid = v => (v == null ? '#9aa6c4' : v >= 0 ? '#38dd9c' : '#ff6d82');

  function map(gs) {
    const byCountry = new Map();
    gs.forEach(g => { const c = EX[g.k][1]; const cur = byCountry.get(c) || []; if (g.avg != null) cur.push(g.avg); byCountry.set(c, cur); });
    const fillOf = iso => { const a = byCountry.get(iso); if (!a) return null; return a.length ? tint(a.reduce((x, y) => x + y, 0) / a.length) : 'rgba(154,166,196,.25)'; };
    const paths = MW.paths.map(p => { const f0 = fillOf(p.iso); return `<path d="${p.d}" class="mw-c${f0 ? ' has' : ''}"${f0 ? ` style="fill:${f0}"` : ''}><title>${esc(p.name)}${f0 ? '' : ': sem ativo conectado'}</title></path>`; }).join('');
    const sp = fit(EX.B3[2], EX.B3[3]);
    const arcs = gs.filter(g => g.k !== 'B3').map(g => { const e = fit(EX[g.k][2], EX[g.k][3]), mx = (sp[0] + e[0]) / 2, my = (sp[1] + e[1]) / 2 - Math.hypot(e[0] - sp[0], e[1] - sp[1]) * 0.22; return `<path d="M${sp[0].toFixed(1)} ${sp[1].toFixed(1)}Q${mx.toFixed(1)} ${my.toFixed(1)} ${e[0].toFixed(1)} ${e[1].toFixed(1)}" class="mw-arc"/>`; }).join('');
    const dots = gs.map(g => { const p = fit(EX[g.k][2], EX[g.k][3]), c = solid(g.avg), pick = g.k === MW.pick;
      return `<g class="mw-dot${pick ? ' pick' : ''}" transform="translate(${p[0].toFixed(1)} ${p[1].toFixed(1)})"><circle r="${(8 + 3 * Math.min(g.list.length, 5)).toFixed(0)}" fill="${c}" class="mw-b" tabindex="0" role="button" data-mwx="${esc(g.k)}" aria-pressed="${pick}" aria-label="${esc(EX[g.k][0])}: ${g.list.length} ativo${g.list.length === 1 ? '' : 's'}, variação média ${g.avg == null ? 'não informada' : f(g.avg * 100) + '%'}"><title>${esc(EX[g.k][0])}</title></circle></g>`; }).join('');
    return `<svg viewBox="0 0 ${W} ${H}" role="group" aria-label="Mapa-múndi das bolsas dos ativos acompanhados. A lista da bolsa escolhida, ao lado, traz os mesmos números em texto.">${paths}${arcs}${dots}</svg>`;
  }

  function side(gs) {
    const g = gs.find(x => x.k === MW.pick) || gs[0];
    if (!g) return '<p class="mute">Nenhum ativo com cotação nesta seleção: <span class="gap">não informado</span>.</p>';
    return `<div class="mw-sh"><h3>${esc(EX[g.k][0])}</h3><span class="cs-chip ${g.open ? 'cy' : ''}">${g.open ? 'pregão aberto agora' : 'pregão fechado agora'}</span></div>
      <ul class="mw-list">${g.list.map(u => { const r = rows[u.symbol], c = chg(r);
        return `<li><button type="button" data-mwa="${esc(u.symbol)}"><span><b>${esc(u.symbol)}</b><small>${esc(u.name)} · ${money(u, r.candle.close)}</small></span><span class="num ${c == null ? '' : c >= 0 ? 'up' : 'dn'}">${c == null ? ND : (c >= 0 ? '+' : '') + f(c * 100) + '%'}</span></button></li>`; }).join('')}</ul>
      <p class="src">Variação: fechamento sobre a abertura da barra do dia, como no Mercados. Toque num ativo para abri-lo.</p>`;
  }

  function render() {
    if (!onPage() || !MW.paths || typeof universe === 'undefined') return;
    const gs = groups();
    if (!gs.some(g => g.k === MW.pick) && gs.length) MW.pick = gs[0].k;
    paint($('#mwKinds'), KINDS.map(([k, l]) => `<button type="button" class="mk-pill" data-mwk="${esc(k)}" aria-pressed="${MW.kind === k}">${l}</button>`).join(''));
    paint($('#mwMap'), map(gs));
    paint($('#mwSide'), side(gs));
    const out = {Cripto: 0, 'Câmbio': 0, Commodity: 0};
    universe.forEach(u => { if (u.assetClass in out) out[u.assetClass]++; });
    $('#mwOut').textContent = `Sem lugar no mapa: ${out.Cripto} criptoativos negociam 24 horas, todos os dias, e não têm sede nem horário; ${out['Câmbio']} pares de câmbio e ${out.Commodity} commodities também não pertencem a um país só. Países sem ativo conectado aparecem tracejados e "não informados", nunca com zero.`;
  }
  async function boot() {
    if (MW.loading) return; MW.loading = true;
    try {
      if (!MW.geo) { const r = await fetch('/data/countries.geojson'); if (!r.ok) throw new Error('countries'); MW.geo = await r.json(); MW.paths = build(); }
    } catch (e) { $('#mwSide').innerHTML = '<p class="mute">Não foi possível carregar o mapa agora. Tente de novo em instantes.</p>'; }
    finally { MW.loading = false; }
    render();
  }
  function tab(t) {
    MW.tab = t;
    document.querySelectorAll('[data-mpt]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.mpt === t)));
    $('#mwWorld').hidden = t !== 'mundo'; $('#mpBrasil').hidden = t !== 'brasil';
    $('#mwKinds').hidden = t !== 'mundo';   /* o filtro de classe só vale para as bolsas */
    if (t === 'brasil' && typeof MP !== 'undefined' && MP.map) setTimeout(() => MP.map.invalidateSize(), 50);
  }
  document.addEventListener('click', e => {
    const t = e.target.closest('[data-mpt]'); if (t) { tab(t.dataset.mpt); return; }
    const k = e.target.closest('[data-mwk]'); if (k) { MW.kind = k.dataset.mwk; render(); return; }
    const x = e.target.closest('[data-mwx]'); if (x) { MW.pick = x.dataset.mwx; render(); return; }
    const a = e.target.closest('[data-mwa]'); if (a && typeof openAsset === 'function') openAsset(a.dataset.mwa);
  });
  document.addEventListener('keydown', e => { if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('#mwMap [data-mwx]')) { e.preventDefault(); e.target.dispatchEvent(new MouseEvent('click', {bubbles: true})); } });
  addEventListener('hashchange', () => { if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] === 'mapa') setTimeout(boot, 300); });
  setInterval(() => { if (onPage()) render(); }, 5000);
  setTimeout(() => { if (onPage()) boot(); }, 1500);
})();
