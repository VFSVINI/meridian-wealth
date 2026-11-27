/* Mercados no visual Cosmos (item #48): pílulas por classe, lista com mini-gráfico real, ativo em destaque que troca sozinho a cada 7 s (com pausa), visões Lista,
   Tabela e Mapa. Só dado real: preço, variação, fonte e frescor vêm das cotações da API; os mini-gráficos vêm de /api/sparklines (fechamentos reais) e os candles
   do destaque de /api/candles. Nada é animado como dado: a "troca" só muda qual ativo está em destaque, e com movimento reduzido ela nem acontece.
   Depende de app.js (rows, universe, sel, select, go, SIG, SIG_TXT, quoteWhen, freshChip, isStale, hasData, f, pc, esc, $, GL, openAsset). Sem global novo. */
(function () {
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const ROTATE_MS = 7000;
  const CLASSES = [['todos', 'Todos'], ['acoes-br', 'Ações BR'], ['fii', 'FIIs'], ['acoes-eua', 'Ações EUA'], ['acoes-mundo', 'Ações do mundo'], ['etf', 'ETFs'],
    ['cripto', 'Cripto'], ['cambio', 'Câmbio'], ['commodity', 'Commodities'], ['indice', 'Índices']];
  const RANGE_BARS = {'1M': 22, '3M': 66, '6M': 130};
  const MK = {cls: 'todos', view: 'lista', feat: null, auto: true, range: '3M', spark: {}, sparkAt: 0, candles: {}, candAt: {}, raf: 0, timer: 0};

  const page = () => document.querySelector('[data-page="mercados"]');
  const onPage = () => { const p = page(); return !!p && !p.hidden; };

  function clsOf(u) {
    switch (u.assetClass) {
      case 'Ação': return u.region === 'Brasil' ? 'acoes-br' : u.region === 'EUA' ? 'acoes-eua' : 'acoes-mundo';
      case 'FII': return 'fii';
      case 'ETF': return 'etf';
      case 'Cripto': return 'cripto';
      case 'Câmbio': return 'cambio';
      case 'Commodity': return 'commodity';
      case 'Índice': return 'indice';
      default: return 'acoes-mundo';
    }
  }
  const norm = s => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  function matches(u) {
    const q = norm($('#q').value.trim());
    return !q || norm(u.symbol + ' ' + u.name).includes(q);
  }
  const filtered = () => universe.filter(u => rows[u.symbol] && (MK.cls === 'todos' || clsOf(u) === MK.cls) && matches(u));
  const withData = list => list.filter(u => hasData(rows[u.symbol]));
  const money = (u, v) => (u.currency === 'USD' ? 'US$ ' : u.currency === 'BRL' ? 'R$ ' : '') + f(v, v < 10 ? 4 : 2);

  /* ---------- peças de desenho ---------- */
  function sparkSvg(vals) {
    if (!vals || vals.length < 2) return '';
    const lo = Math.min(...vals), hi = Math.max(...vals), W = 100, H = 30, n = vals.length;
    const d = vals.map((v, i) => (i ? 'L' : 'M') + (i / (n - 1) * W).toFixed(1) + ' ' + (H - 2 - (v - lo) / ((hi - lo) || 1) * (H - 4)).toFixed(1)).join('');
    const up = vals[n - 1] >= vals[0];
    return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" aria-hidden="true"><path d="${d}" fill="none" stroke="var(--${up ? 'up' : 'down'})" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke"/></svg>`;
  }

  function candleSvg(cs, u) {
    const N = RANGE_BARS[MK.range], bars = cs.slice(-N);
    if (bars.length < 3) return '<p class="mute">Histórico insuficiente para o gráfico.</p>';
    const W = 800, H = 250, R = 58, T = 10, B = 10, pw = W - R - 6;
    let lo = Math.min(...bars.map(c => c.low)), hi = Math.max(...bars.map(c => c.high));
    const pad = (hi - lo) * 0.06 || hi * 0.02; lo -= pad; hi += pad;
    const y = v => T + (hi - v) / (hi - lo) * (H - T - B), step = pw / bars.length, bw = Math.max(1.5, step * 0.62);
    const grid = [0, 1, 2, 3].map(i => { const v = hi - (hi - lo) * i / 3; return `<line x1="0" x2="${pw}" y1="${y(v).toFixed(1)}" y2="${y(v).toFixed(1)}" class="g"/><text x="${pw + 6}" y="${(y(v) + 4).toFixed(1)}">${f(v, v < 10 ? 2 : 1)}</text>`; }).join('');
    const body = bars.map((c, i) => {
      const x = i * step + step / 2, up = c.close >= c.open, col = up ? 'var(--up)' : 'var(--down)';
      const top = y(Math.max(c.open, c.close)), h = Math.max(1, Math.abs(y(c.open) - y(c.close)));
      return `<line x1="${x.toFixed(1)}" x2="${x.toFixed(1)}" y1="${y(c.high).toFixed(1)}" y2="${y(c.low).toFixed(1)}" stroke="${col}" stroke-width="1.2"/><rect x="${(x - bw / 2).toFixed(1)}" y="${top.toFixed(1)}" width="${bw.toFixed(1)}" height="${h.toFixed(1)}" fill="${col}" rx="1"/>`;
    }).join('');
    const last = bars[bars.length - 1], ly = y(last.close).toFixed(1);
    const label = `Candles dos últimos ${bars.length} pregões de ${u.symbol}: de ${f(bars[0].close, 2)} a ${f(last.close, 2)}. A visão Tabela traz os números.`;
    return `<svg class="mk-candles" viewBox="0 0 ${W} ${H}" role="img" aria-label="${esc(label)}">${grid}${body}<line x1="0" x2="${pw}" y1="${ly}" y2="${ly}" class="last"/></svg>`;
  }

  /* ---------- peças de tela ---------- */
  function pills() {
    const counts = {todos: universe.filter(u => rows[u.symbol]).length};
    universe.forEach(u => { if (rows[u.symbol]) counts[clsOf(u)] = (counts[clsOf(u)] || 0) + 1; });
    $('#mkPills').innerHTML = CLASSES.filter(([k]) => counts[k]).map(([k, l]) =>
      `<button type="button" class="mk-pill" data-mkc="${k}" aria-pressed="${MK.cls === k}">${esc(l)} <small>${counts[k]}</small></button>`).join('');
  }

  function items(list) {
    $('#mkCount').textContent = list.length + ' ATIVO' + (list.length === 1 ? '' : 'S');
    $('#mkItems').innerHTML = list.map(u => {
      const r = rows[u.symbol], on = u.symbol === MK.feat;
      if (!hasData(r)) {
        return `<li><button type="button" class="mk-item" data-mks="${esc(u.symbol)}" aria-pressed="${on}"><span class="mk-id"><b>${esc(u.symbol)}</b><small>${esc(u.name)}</small></span><span class="mk-nodata">sem dado em tempo real</span></button></li>`;
      }
      const c = r.candle, chg = c.open > 0 ? c.close / c.open - 1 : null;
      return `<li><button type="button" class="mk-item" data-mks="${esc(u.symbol)}" aria-pressed="${on}"><span class="mk-id"><b>${esc(u.symbol)}</b><small>${esc(u.name)}</small></span>`
        + `<span class="mk-spark">${sparkSvg(MK.spark[u.symbol])}</span><span class="mk-px"><b class="num">${f(c.close, c.close < 10 ? 2 : 2)}</b>`
        + `<small class="num ${chg == null ? '' : chg >= 0 ? 'up' : 'dn'}">${chg == null ? 'não informado' : (chg >= 0 ? '+' : '') + f(chg * 100) + '%'}</small></span></button></li>`;
    }).join('') || '<li class="mute mk-none">Nenhum ativo com esses filtros.</li>';
  }

  function metricCards(A) {
    const rsiTxt = A.rsi >= 70 ? 'sobrecomprado' : A.rsi <= 30 ? 'sobrevendido' : 'neutro';
    const abv = A.price >= A.fairValue;
    return `<div class="mk-metrics"><div><span>RSI (14) ${GL('rsi')}</span><b class="num">${f(A.rsi, 1)}</b><small>${rsiTxt}</small></div>`
      + `<div><span>Volatilidade anual ${GL('volatility')}</span><b class="num">${pc(A.annVol)}</b><small>calculada sobre o histórico real</small></div>`
      + `<div><span>Média 50 dias ${GL('fair-value')}</span><b>${abv ? 'preço acima' : 'preço abaixo'}</b><small>média ${f(A.fairValue)}</small></div></div>`;
  }

  function detail(list) {
    const u = universe.find(x => x.symbol === MK.feat), r = u && rows[u.symbol];
    if (!u || !r) { $('#mkDetail').innerHTML = '<p class="mute">Escolha um ativo na lista.</p>'; return; }
    if (!hasData(r)) {
      $('#mkDetail').innerHTML = `<div class="mk-dh"><div><h2>${esc(u.symbol)}</h2><p>${esc(u.name)}</p></div></div><p class="mute">Nenhum provedor respondeu para ${esc(u.symbol)} e não há dado real gravado. Nada é estimado.</p>`;
      return;
    }
    const c = r.candle, chg = c.open > 0 ? c.close / c.open - 1 : null, A = r.assessment, cs = MK.candles[u.symbol] || [];
    const last = cs[cs.length - 1];
    const read = A ? `<div class="mk-read"><div class="mk-readh"><span class="mk-badge ${SIG[A.signal][1]}">${esc(SIG[A.signal][0])}</span><span class="cs-chip am">análise, não ordem</span></div><p>${esc(SIG_TXT[A.signal] || '')}</p></div>`
      : '<div class="mk-read"><p class="mute">Dados reais insuficientes para o motor de sinais neste ativo.</p></div>';
    $('#mkDetail').innerHTML = `<div class="mk-dh"><div><h2>${esc(u.symbol)}</h2><p>${esc(u.name)} · ${esc(u.assetClass)} · ${esc(EXS[u.exchange] || u.exchange || '')}</p></div>`
      + `<div class="mk-dp"><b class="num">${money(u, c.close)}</b><small class="num ${chg == null ? '' : chg >= 0 ? 'up' : 'dn'}">${chg == null ? 'variação não informada' : (chg >= 0 ? '+' : '') + f(chg * 100) + '%'}</small></div></div>`
      + `<div class="mk-chips">${freshChip(r)}<span class="cs-chip cy">Fonte: ${esc(r.source)}</span><span class="cs-chip">${esc(quoteWhen(r))}</span>${isStale(r) ? '<span class="cs-chip dn">desatualizada</span>' : ''}</div>`
      + `<div class="mk-ranges" role="group" aria-label="Período do gráfico">${Object.keys(RANGE_BARS).map(k => `<button type="button" data-mkr="${k}" aria-pressed="${MK.range === k}">${k}</button>`).join('')}</div>`
      + `<div class="mk-chart">${cs.length ? candleSvg(cs, u) : '<span class="skel"></span>'}</div>`
      + (last ? `<p class="mk-ohlc num">${esc(new Date(last.time).toLocaleDateString('pt-BR'))} <span>A ${f(last.open, 4)}</span> <span>Máx ${f(last.high, 4)}</span> <span>Mín ${f(last.low, 4)}</span> <span class="${last.close >= last.open ? 'up' : 'dn'}">F ${f(last.close, 4)}</span></p>` : '')
      + read + (A ? metricCards(A) : '')
      + `<div class="mk-foot"><span>Indicadores e leitura calculados aqui sobre dados reais. Não é ordem nem recomendação.</span><button type="button" class="mk-btn go" data-mko="${esc(u.symbol)}">Abrir análise completa <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg></button></div>`;
  }

  function table(list) {
    $('#list').innerHTML = list.map(u => {
      const r = rows[u.symbol], sel0 = u.symbol === sel ? 'sel' : '';
      if (!hasData(r)) return `<tr class="row ${sel0}" data-s="${esc(u.symbol)}"><td><b class="mono">${esc(u.symbol)}</b><div class="src"><i class="dot ${r.marketOpen ? 'on' : ''}"></i>sem fonte</div></td><td colspan="3" class="mute" style="font-size:12px">sem dado em tempo real</td></tr>`;
      const c = r.candle, chg = c.open > 0 ? c.close / c.open - 1 : null, s = r.assessment ? SIG[r.assessment.signal] : null;
      return `<tr class="row ${sel0}" data-s="${esc(u.symbol)}"><td><b class="mono">${esc(u.symbol)}</b><div class="src"><i class="dot ${r.marketOpen ? 'on' : ''}"></i>${esc(r.source)} · ${esc(quoteWhen(r))}</div><div>${freshChip(r)}${isStale(r) ? ' <span class="fresh dn">desatualizada</span>' : ''}</div></td>`
        + `<td class="n ${r.flash || ''}">${f(c.close, c.close < 10 ? 4 : 2)}<div class="src">${esc(u.currency || '')}</div></td><td class="n ${chg == null ? '' : chg >= 0 ? 'up' : 'dn'}">${chg == null ? 'não informado' : (chg >= 0 ? '+' : '') + f(chg * 100) + '%'}</td>`
        + `<td>${s ? `<span class="chip ${s[1]}">${esc(s[0])}</span><div class="num mute" style="font-size:11px">${r.assessment.score > 0 ? '+' : ''}${r.assessment.score}</div>` : '<span class="mute" style="font-size:11px">dados insuficientes</span>'}</td></tr>`;
    }).join('') || '<tr><td colspan="4" class="mute">Nenhum ativo com esses filtros.</td></tr>';
  }

  /* ---------- dados sob demanda ---------- */
  async function loadSpark() {
    if (Date.now() - MK.sparkAt < 55000) return;
    MK.sparkAt = Date.now();
    try { MK.spark = await (await fetch('/api/sparklines?bars=30')).json(); render(); } catch (e) { MK.sparkAt = 0; }
  }
  async function loadCandles(sym) {
    if (!sym || Date.now() - (MK.candAt[sym] || 0) < 55000) return;
    MK.candAt[sym] = Date.now();
    try {
      const r = await fetch('/api/candles/' + encodeURIComponent(sym) + '?limit=140');
      if (r.ok) { MK.candles[sym] = await r.json(); render(); } else MK.candAt[sym] = 0;
    } catch (e) { MK.candAt[sym] = 0; }
  }

  /* ---------- destaque que troca sozinho ---------- */
  function progress(run) {
    const p = $('#mkProg'); if (!p) return;
    p.classList.remove('run'); void p.offsetWidth;
    if (run) p.classList.add('run');
  }
  function autoUi() {
    const can = !reduced.matches;
    $('#mkPause').hidden = !can;
    $('#mkAutoTag').classList.toggle('off', !MK.auto || !can);
    $('#mkAutoTxt').textContent = !can ? 'Escolha um ativo na lista' : MK.auto ? 'Troca a cada 7 segundos' : 'Troca pausada';
    $('#mkPause').textContent = MK.auto ? 'Pausar troca' : 'Retomar troca';
  }
  function feature(sym, fromUser) {
    MK.feat = sym;
    if (fromUser && MK.auto) { MK.auto = false; }
    loadCandles(sym);
    autoUi(); progress(MK.auto && !reduced.matches);
    render();
  }
  function rotate() {
    if (!MK.auto || reduced.matches || document.hidden || !onPage() || MK.view !== 'lista') return;
    const live = withData(filtered());
    if (live.length < 2) return;
    const i = live.findIndex(u => u.symbol === MK.feat);
    feature(live[(i + 1) % live.length].symbol, false);
  }

  /* ---------- ciclo de desenho ---------- */
  function render() {
    if (MK.raf) return;
    MK.raf = requestAnimationFrame(() => {
      MK.raf = 0;
      if (!onPage() || !universe.length) return;
      const list = filtered();
      if (!MK.feat || !list.some(u => u.symbol === MK.feat)) {
        const pick = withData(list).find(u => u.symbol === sel) || withData(list)[0] || list[0];
        MK.feat = pick ? pick.symbol : null;
        if (MK.feat) loadCandles(MK.feat);
      }
      pills();
      if (MK.view === 'lista') { items(list); detail(list); }
      else if (MK.view === 'tabela') table(list);
    });
  }
  function setView(v) {
    MK.view = v;
    ['lista', 'tabela', 'mapa'].forEach(k => { const el = document.getElementById('mk' + k[0].toUpperCase() + k.slice(1)); if (el) el.hidden = k !== v; });
    document.querySelectorAll('[data-mkv]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.mkv === v)));
    progress(v === 'lista' && MK.auto && !reduced.matches);
    render();
  }

  document.addEventListener('click', e => {
    const c = e.target.closest('[data-mkc]'); if (c) { MK.cls = c.dataset.mkc; MK.feat = null; render(); return; }
    const v = e.target.closest('[data-mkv]'); if (v) { setView(v.dataset.mkv); return; }
    const it = e.target.closest('[data-mks]'); if (it) { feature(it.dataset.mks, true); return; }
    const rg = e.target.closest('[data-mkr]'); if (rg) { MK.range = rg.dataset.mkr; render(); return; }
    if (e.target.closest('#mkPause')) { MK.auto = !MK.auto; autoUi(); progress(MK.auto && !reduced.matches); return; }
    const o = e.target.closest('[data-mko]'); if (o) { const s = o.dataset.mko; openAsset(s).then(ok => { if (ok) { select(s); go('cockpit'); } }); }
  });
  /* na visão Tabela uma linha abre o cockpit (o app.js já trata o clique em #list); aqui só a busca */
  $('#q').addEventListener('input', () => { MK.feat = null; render(); });
  document.addEventListener('mw:mk-render', render);
  reduced.addEventListener('change', () => { autoUi(); progress(MK.auto && !reduced.matches); });
  function onRoute() {
    if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] !== 'mercados') return;
    loadSpark(); autoUi(); progress(MK.view === 'lista' && MK.auto && !reduced.matches); render();
  }
  addEventListener('hashchange', onRoute);
  setInterval(rotate, ROTATE_MS);
  setInterval(() => { if (onPage()) { loadSpark(); loadCandles(MK.feat); } }, 60000);
  autoUi(); onRoute();
})();
