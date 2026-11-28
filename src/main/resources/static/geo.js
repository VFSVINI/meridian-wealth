/* Geopolítica no visual Cosmos (item #51): mapa-múndi de risco segundo as manchetes, passo a passo, quatro termômetros e notícias com link para a matéria.
   Tudo vem do radar macro que a API já publica (macro: vix, dollarIndex, yield10y, riskScore, countries, news, sectorImpact, changes) e das fronteiras da Natural Earth
   (/data/countries.geojson, domínio público). O risco por país é derivado das manchetes por palavras-chave (método publicado, não índice oficial): país que nenhuma manchete
   cita fica "não informado", nunca risco zero. Das notícias só o título, a fonte, o horário e o link aparecem; o texto fica na fonte. Depende de app.js ($, esc, f, GL). Sem global novo. */
(function () {
  const STEPS = [
    ['Evento', 'Algo acontece', 'Conflito, sanção, eleição ou decisão de um banco central. Tudo começa com um fato novo no mundo.'],
    ['Expectativa', 'Mercado reage', 'Antes de qualquer número mudar, investidores apostam no que pode vir: mais risco, menos oferta, juros diferentes.'],
    ['Preço', 'Preços mudam', 'Petróleo, dólar, juros e ações se movem. Quem depende desses preços sente primeiro: energia, transporte, exportadoras.'],
    ['Carteira', 'Chega a você', 'O efeito chega aos seus ativos e ao seu bolso: renda fixa, ações, fundos e as contas do mês. Diversificar reduz o susto.']
  ];
  const LEVELS = [[0.25, 'Baixo'], [0.5, 'Moderado'], [0.75, 'Alto'], [1.01, 'Muito alto']];
  const level = r => LEVELS.find(l => r < l[0])[1];
  const GP = {macro: null, geo: null, paths: null, tab: 'risco', sector: null, pick: null, step: 0, loading: false};
  const onPage = () => { const p = document.querySelector('[data-page="geo"]'); return !!p && !p.hidden; };
  const num = (v, d = 2) => Number(v).toLocaleString('pt-BR', {minimumFractionDigits: d, maximumFractionDigits: d});
  const ND = '<span class="gap">não informado</span>';

  /* projeção Natural Earth (Šavrič et al.): lon e lat em graus -> x e y */
  function ne(lon, lat) {
    const l = lon * Math.PI / 180, p = lat * Math.PI / 180, p2 = p * p, p4 = p2 * p2;
    return [l * (0.8707 - 0.131979 * p2 + p4 * (-0.013791 + p4 * (0.003971 * p2 - 0.001529 * p4))),
      p * (1.007226 + p2 * (0.015085 + p4 * (-0.044475 + 0.028874 * p2 - 0.005916 * p4)))];
  }
  function buildPaths() {
    const W = 1000, H = 520, pad = 8, edge = [ne(-180, 0)[0], ne(180, 0)[0]], top = ne(0, 83.6)[1], bot = ne(0, -56)[1];
    const k = Math.min((W - 2 * pad) / (edge[1] - edge[0]), (H - 2 * pad) / (top - bot));
    const X = x => (W - (edge[1] - edge[0]) * k) / 2 + (x - edge[0]) * k, Y = y => pad + (top - y) * k;
    return GP.geo.features.map(ft => {
      const polys = ft.geometry.type === 'Polygon' ? [ft.geometry.coordinates] : ft.geometry.coordinates;
      let best = null, d = '';
      for (const poly of polys) {
        for (const ring of poly) {
          const pts = ring.map(c => { const q = ne(c[0], c[1]); return [X(q[0]), Y(q[1])]; });
          d += 'M' + pts.map(p => p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join('L') + 'Z';
        }
        const outer = poly[0].map(c => { const q = ne(c[0], c[1]); return [X(q[0]), Y(q[1])]; });
        const xs = outer.map(p => p[0]), ys = outer.map(p => p[1]), area = (Math.max(...xs) - Math.min(...xs)) * (Math.max(...ys) - Math.min(...ys));
        if (!best || area > best.area) best = {area, cx: outer.reduce((s, p) => s + p[0], 0) / outer.length, cy: outer.reduce((s, p) => s + p[1], 0) / outer.length};
      }
      return {iso: ft.properties.iso, name: ft.properties.name, d, cx: best.cx, cy: best.cy};
    });
  }

  const risks = () => { const m = new Map(); ((GP.macro && GP.macro.countries) || []).forEach(c => m.set(c.iso3, c)); return m; };
  const ago = iso => {
    if (!iso) return 'horário não informado';
    const m = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 60000));
    return m < 60 ? `há ${Math.max(1, m)} min` : m < 1440 ? `há ${Math.round(m / 60)} h` : `há ${Math.round(m / 1440)} d`;
  };
  const link = n => n && n.link && /^https?:\/\//i.test(n.link)
    ? `<a class="gp-read" href="${esc(n.link)}" target="_blank" rel="noopener noreferrer">Ler a matéria completa<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 4h6v6M20 4l-9 9M10 6H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4"/></svg></a>`
    : '<span class="mute">Esta fonte não informou o link da matéria.</span>';

  function fill(c, sectorHit) {
    if (!c) return null;
    if (GP.tab === 'setores') return sectorHit ? 'rgba(255,179,64,.78)' : 'rgba(85,214,255,.12)';
    const r = c.risk;
    return r < 0.12 ? 'rgba(85,214,255,.30)' : `rgba(255,${Math.round(190 - 60 * r)},${Math.round(70 - 30 * r)},${(0.28 + 0.62 * r).toFixed(2)})`;
  }

  function map() {
    if (!GP.paths) return;
    const rk = risks();
    $('#gpMap').innerHTML = GP.paths.map(p => {
      const c = rk.get(p.iso), hit = !!(GP.sector && c && c.sectors.includes(GP.sector)), f0 = fill(c, hit);
      const label = c ? `${c.name}: risco ${level(c.risk).toLowerCase()} nas manchetes (${c.headlines} manchete${c.headlines === 1 ? '' : 's'})` : `${p.name}: não informado`;
      return `<path d="${p.d}" data-iso="${p.iso}" class="gp-c${c ? ' has' : ''}${GP.pick === p.iso ? ' pick' : ''}" ${f0 ? `style="fill:${f0}"` : ''} ${c ? `tabindex="0" role="button" aria-label="${esc(label)}"` : ''}><title>${esc(label)}</title></path>`;
    }).join('');
    if (GP.tab === 'manchetes') {
      $('#gpMap').insertAdjacentHTML('beforeend', GP.paths.filter(p => rk.has(p.iso)).map(p => {
        const c = rk.get(p.iso);
        return `<g class="gp-dot" data-iso="${p.iso}"><circle cx="${p.cx.toFixed(1)}" cy="${p.cy.toFixed(1)}" r="${(7 + 3 * Math.min(c.headlines, 4)).toFixed(0)}"/><text x="${p.cx.toFixed(1)}" y="${(p.cy + 4).toFixed(1)}" text-anchor="middle">${c.headlines}</text></g>`;
      }).join(''));
    }
    $('#gpMapTitle').textContent = {risco: 'Risco por país nas manchetes', setores: 'Países ligados ao setor escolhido', manchetes: 'Países com manchetes agora'}[GP.tab];
  }

  function side() {
    const m = GP.macro, rk = risks();
    if (!m) { $('#gpSide').innerHTML = '<p class="mute">Sem resposta do radar macro agora. Nada é estimado.</p>'; return; }
    if (GP.tab === 'setores') {
      const secs = Object.entries(m.sectorImpact || {}).sort((a, b) => b[1] - a[1]);
      $('#gpSide').innerHTML = `<p class="gp-sk">Setores pressionados</p>` + (secs.length
        ? `<div class="gp-secs">${secs.map(([k, v]) => `<button type="button" class="gp-sec" data-gps="${esc(k)}" aria-pressed="${GP.sector === k}"><span>${esc(k)}</span><i style="width:${Math.round(v * 100)}%"></i><b class="num">${Math.round(v * 100)}</b></button>`).join('')}</div><p class="src">Impacto de 0 a 100: soma do risco das manchetes do setor. Escolha um setor para ver os países ligados a ele no mapa.</p>`
        : '<p class="mute">Nenhuma manchete pressiona um setor agora. <b>não informado</b>.</p>');
      return;
    }
    const c = GP.pick ? rk.get(GP.pick) : [...rk.values()][0];
    if (!c) { $('#gpSide').innerHTML = `<p class="gp-sk">Nenhum país com manchete</p><p class="mute">As manchetes recentes não citam nenhum país da lista. Os países ficam como ${ND}, nunca como risco zero.</p>`; return; }
    GP.pick = GP.pick || c.iso3;
    $('#gpSide').innerHTML = `<div class="gp-sh"><p class="gp-sk">${esc(c.region)}</p><span class="cs-chip">${c.headlines} manchete${c.headlines === 1 ? '' : 's'}</span></div><h3>${esc(c.name)}</h3>`
      + `<p class="gp-lv">Nível de risco nas manchetes</p><b class="gp-lvv ${c.risk >= 0.5 ? 'hi' : ''}">${level(c.risk)}</b>`
      + `<p class="gp-lv">Setores pressionados</p><p>${c.sectors.length ? esc(c.sectors.join(', ')) : ND}</p>`
      + `<p class="gp-lv">Manchete de maior risco</p><p class="gp-hl">${esc(c.top.title)}</p><p class="src">${esc(c.top.source)} · ${esc(ago(c.top.publishedAt))}</p>${link(c.top)}`
      + `<div class="gp-others">${[...rk.values()].slice(0, 12).map(x => `<button type="button" class="mk-pill" data-gpc="${x.iso3}" aria-pressed="${x.iso3 === GP.pick}">${esc(x.name)}</button>`).join('')}</div>`;
  }

  function steps() {
    $('#gpSteps').innerHTML = STEPS.map((s, i) => `<button type="button" role="tab" class="gp-step" data-gpst="${i}" aria-pressed="${GP.step === i}"><small>0${i + 1}</small><b>${s[0]}</b><span>${s[1]}</span></button>`).join('');
    $('#gpStepTxt').innerHTML = `<b>${STEPS[GP.step][0]}.</b> ${esc(STEPS[GP.step][2])}`;
  }

  function thermo() {
    const m = GP.macro;
    const card = (name, gid, val, chg, chip, unit) => `<article class="gp-t"><p>${name} ${GL(gid)}</p><b class="num">${val == null ? ND : esc(num(val, unit === 'pct' ? 2 : 2)) + (unit === 'pct' ? '%' : '')}</b>`
      + `<div class="gp-tf">${chg == null ? '<span class="src">variação do dia não informada</span>' : `<span class="num ${chg >= 0 ? 'up' : 'dn'}">${chg >= 0 ? '+' : ''}${num(chg * 100)}%</span>`}<span class="cs-chip ${chip[1]}">${esc(chip[0])}</span></div></article>`;
    if (!m) { $('#gpTh').innerHTML = '<p class="mute">Sem resposta do radar macro agora.</p>'; return; }
    const ch = m.changes || {};
    $('#gpTh').innerHTML = card('VIX', 'vix', m.vix, ch.vix, ['Yahoo · atrasado ~15 min', 'cy'])
      + card('DXY', 'dollar-index', m.dollarIndex, ch.dxy, ['Yahoo · atrasado ~15 min', 'cy'])
      + card('Juro de 10 anos (EUA)', 'yield-10y', m.yield10y, null, [(m.source || '').includes('FRED') ? 'FRED · fechamento' : 'Yahoo · atrasado ~15 min', 'cy'], 'pct')
      + `<article class="gp-t"><p>Índice de risco macro ${GL('geo-risk')}</p><b class="num">${m.available ? Math.round(m.riskScore * 100) + ' de 100' : ND}</b><div class="gp-tf"><span class="src">média do VIX e das manchetes</span><span class="cs-chip am">índice da plataforma</span></div></article>`;
  }

  function news() {
    const list = ((GP.macro && GP.macro.news) || []).slice(0, 6), rk = risks();
    $('#gpNews').innerHTML = list.length ? list.map(n => {
      const c = (n.countries || []).map(i => rk.get(i)).find(Boolean);
      return `<article class="gp-n"><p class="gp-nh"><span class="cs-chip cy">${esc(c ? c.name : 'Mundo')}</span><span class="cs-chip">risco ${esc(level(n.risk).toLowerCase())}</span></p><h3>${esc(n.title)}</h3><p class="src">${esc(n.source)} · ${esc(ago(n.publishedAt))}</p>${link(n)}</article>`;
    }).join('') : '<p class="mute">Sem manchetes de risco agora. <b>não informado</b>.</p>';
  }

  function render() {
    if (!onPage()) return;
    map(); side(); steps(); thermo(); news();
    $('#gpReal').textContent = GP.macro && GP.macro.available ? 'dados reais' : 'sem dado agora';
  }
  async function boot() {
    if (GP.loading) return;
    GP.loading = true;
    try {
      if (!GP.geo) { const r = await fetch('/data/countries.geojson'); if (!r.ok) throw new Error('countries'); GP.geo = await r.json(); GP.paths = buildPaths(); }
      if (!GP.macro) { const o = await (await fetch('/api/overview')).json(); GP.macro = o.macro; }
    } catch (e) { $('#gpSide').innerHTML = '<p class="mute">Não foi possível carregar o mapa agora. Tente de novo em instantes.</p>'; }
    finally { GP.loading = false; }
    render();
  }
  document.addEventListener('mw:macro', e => { GP.macro = e.detail; if (onPage() && GP.paths) { map(); side(); thermo(); news(); } });
  document.addEventListener('click', e => {
    const t = e.target.closest('[data-gpt]'); if (t) { GP.tab = t.dataset.gpt; document.querySelectorAll('[data-gpt]').forEach(b => { b.setAttribute('aria-pressed', String(b === t)); b.setAttribute('aria-selected', String(b === t)); }); map(); side(); return; }
    const s = e.target.closest('[data-gps]'); if (s) { GP.sector = GP.sector === s.dataset.gps ? null : s.dataset.gps; map(); side(); return; }
    const st = e.target.closest('[data-gpst]'); if (st) { GP.step = Number(st.dataset.gpst); steps(); return; }
    const pc = e.target.closest('[data-gpc]'); if (pc) { GP.pick = pc.dataset.gpc; map(); side(); return; }
    const c = e.target.closest('#gpMap [data-iso]'); if (c && risks().has(c.dataset.iso)) { GP.pick = c.dataset.iso; if (GP.tab === 'setores') { GP.tab = 'risco'; document.querySelectorAll('[data-gpt]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.gpt === 'risco'))); } map(); side(); }
  });
  document.addEventListener('keydown', e => { if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('#gpMap path.has')) { e.preventDefault(); e.target.dispatchEvent(new MouseEvent('click', {bubbles: true})); } });
  addEventListener('hashchange', () => { if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] === 'geo') boot(); });
  setInterval(() => { if (onPage() && GP.paths) { map(); side(); news(); } }, 60000);
  if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] === 'geo') boot();
})();
