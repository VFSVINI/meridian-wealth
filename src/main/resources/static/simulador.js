/* Simulador pré-ordem (item #53): "órbita da carteira". A conta de impacto é a do servidor (POST /api/impact/pre-order); aqui só se desenha o antes e o depois.
   Carteira = as linhas do modo avançado (#smPos), que podem vir da carteira salva do usuário. Planetas = posições, tamanho = peso depois da ordem, anel = volatilidade anualizada
   do ativo (dado real do painel); sem volatilidade, a posição fica no anel pontilhado de fora. Nada é gravado e nenhuma ordem é enviada. Depende de app.js ($, esc, f, universe, rows). Sem global novo. */
(function () {
  const PICKS = ['PETR4', 'VALE3', 'ITUB4', 'IVVB11', 'BTC', 'MXRF11'];
  const AMOUNTS = [1000, 5000, 10000, 20000];
  const SM = {asset: null, side: 'BUY', amount: 5000, timer: null, busy: false, seq: 0, d: null, ctx: null};
  const BRL = v => 'R$ ' + f(v, 2);
  const BRL0 = v => 'R$ ' + f(v, 0);
  const ND = '<span class="gap">não informado</span>';
  const CLS = {'Ação': ['ação', '#ffb340'], 'FII': ['FII', '#55d6ff'], 'ETF': ['ETF', '#8fa8ff'], 'Cripto': ['cripto', '#b48cff']};
  const RINGS = [[0.15, 'baixa'], [0.30, 'média'], [0.50, 'alta'], [9, 'muito alta']];
  const FRESH = {END_OF_DAY: 'fechamento', DELAYED: '~15 min', REAL_TIME: 'tempo real'};
  const onPage = () => { const p = document.querySelector('[data-page="simulador"]'); return !!p && !p.hidden; };
  const paint = (el, html) => { if (el && el.dataset.h !== html) { el.innerHTML = html; el.dataset.h = html; } };
  const uni = s => (typeof universe !== 'undefined' ? universe : []).find(u => u.symbol === s);
  const price = s => { const r = typeof rows !== 'undefined' ? rows[s] : null; return r && r.candle && r.candle.close > 0 ? r : null; };

  function picks() {
    const have = PICKS.filter(s => uni(s));
    return (have.length ? have : (typeof universe !== 'undefined' ? universe : []).slice(0, 6).map(u => u.symbol));
  }
  /* carteira informada no modo avançado: [{key,label,value,cls,vol}] */
  function portfolio() {
    const m = new Map();
    document.querySelectorAll('#smPos .hold').forEach(h => {
      const sel = h.querySelector('select'), v = Number(h.querySelector('input').value);
      if (!sel || !(v > 0)) return;
      const key = sel.value, sym = key.startsWith('S:') ? key.slice(2) : null, u = sym ? uni(sym) : null, r = sym ? price(sym) : null;
      const cur = m.get(key) || {key, sym, label: sym || sel.selectedOptions[0].textContent, value: 0, cls: u ? u.assetClass : null, vol: r && r.assessment ? r.assessment.annVol : null};
      cur.value += v; m.set(key, cur);
    });
    return [...m.values()];
  }
  function afterList(before, key, sym, side, amt) {
    const m = new Map(before.map(p => [p.key, {...p}]));
    const u = uni(sym), r = price(sym);
    const cur = m.get(key) || {key, sym, label: sym, value: 0, cls: u ? u.assetClass : null, vol: r && r.assessment ? r.assessment.annVol : null};
    cur.value = Math.max(0, cur.value + (side === 'BUY' ? amt : -amt));
    if (cur.value > 0) m.set(key, cur); else m.delete(key);
    return [...m.values()];
  }
  const total = l => l.reduce((a, p) => a + p.value, 0);
  const weightOf = (l, key) => { const t = total(l); const p = l.find(x => x.key === key); return t > 0 && p ? p.value / t : 0; };

  function orbit(list, key) {
    const t = total(list), ringR = [70, 112, 154, 196, 236];
    const rings = ringR.map((r, i) => `<circle cx="300" cy="260" r="${r}" class="sm-ring${i === 4 ? ' nd' : ''}"/>`).join('');
    const byRing = [[], [], [], [], []];
    list.forEach(p => { const i = p.vol == null ? 4 : RINGS.findIndex(x => p.vol < x[0]); byRing[i].push(p); });
    const planets = byRing.map((g, i) => g.map((p, j) => {
      const w = t > 0 ? p.value / t : 0, a = (j / Math.max(g.length, 1)) * Math.PI * 2 + i * 0.9 + 0.4, x = 300 + ringR[i] * Math.cos(a), y = 260 + ringR[i] * Math.sin(a);
      const c = (CLS[p.cls] || ['', '#9aa6c4'])[1], r = 9 + 38 * Math.sqrt(w), pick = p.key === key;
      return `<g class="sm-p" style="--d:${90 + i * 40}s" transform="translate(${x.toFixed(1)} ${y.toFixed(1)})"><g class="sm-c"><circle r="${r.toFixed(1)}" class="sm-planet${pick ? ' pick' : ''}" fill="${c}" style="--c:${c}"><title>${esc(p.label)}: ${f(w * 100, 1)}% da carteira</title></circle>${r > 18 ? `<text y="4" text-anchor="middle" class="sm-pl">${esc((p.sym || '').slice(0, 6))}</text>` : ''}</g></g>`;
    }).join(''));
    return `<svg viewBox="0 0 600 520" role="img" aria-label="Órbita da carteira: cada planeta é uma posição, o tamanho é o peso depois da ordem e o anel é a volatilidade do ativo. A tabela e o resumo trazem os mesmos números."><defs><radialGradient id="smSun"><stop offset="0" stop-color="#fff1c8"/><stop offset=".5" stop-color="#ffb340"/><stop offset="1" stop-color="#e08400"/></radialGradient></defs>${rings}`
      + `<g class="sm-orb">${planets.map((g, i) => `<g class="sm-r r${i}">${g}</g>`).join('')}</g><circle cx="300" cy="260" r="34" fill="url(#smSun)" class="sm-sun"/><text x="300" y="263" text-anchor="middle" class="sm-sunt">CARTEIRA</text></svg>`;
  }
  const legend = list => {
    const used = [...new Set(list.map(p => p.cls))].filter(Boolean);
    return used.map(c => `<span><i style="background:${(CLS[c] || ['', '#9aa6c4'])[1]}"></i>${esc((CLS[c] || [c.toLowerCase()])[0])}</span>`).join('')
      + '<span class="src">anéis: volatilidade menor → maior · pontilhado: sem volatilidade · tamanho = peso depois da ordem</span>';
  };

  function ring(after, before) {
    const R = 62, C = 2 * Math.PI * R, a = Math.min(1, after) * C, b = Math.min(1, before) * C;
    const tick = (() => { const ang = -Math.PI / 2 + Math.min(1, before) * Math.PI * 2; return [100 + 74 * Math.cos(ang), 100 + 74 * Math.sin(ang), 100 + 52 * Math.cos(ang), 100 + 52 * Math.sin(ang)]; })();
    return `<svg viewBox="0 0 200 200" role="img" aria-label="Concentração no ativo: ${f(after * 100, 1)}% depois, ${f(before * 100, 1)}% antes"><circle cx="100" cy="100" r="${R}" fill="none" class="sm-trk" stroke-width="16"/>`
      + `<circle cx="100" cy="100" r="${R}" fill="none" stroke="#ffc83d" stroke-width="16" stroke-linecap="round" stroke-dasharray="${a.toFixed(1)} ${(C - a).toFixed(1)}" transform="rotate(-90 100 100)"/>`
      + `<line x1="${tick[0].toFixed(1)}" y1="${tick[1].toFixed(1)}" x2="${tick[2].toFixed(1)}" y2="${tick[3].toFixed(1)}" stroke="#fff" stroke-width="2.5"/>`
      + `<text x="100" y="104" text-anchor="middle" class="sm-rv">${f(after * 100, 1)}%</text><text x="100" y="124" text-anchor="middle" class="sm-rl">antes: ${f(before * 100, 1)}%</text></svg>`;
  }
  function fall(valueAfter) {
    const pcts = [0.1, 0.2, 0.3], paths = pcts.map((p, k) => {
      const pts = Array.from({length: 31}, (_, i) => { const t = i / 30, y = -p * Math.pow(t, 0.85) + 0.008 * Math.sin(t * 11 + k * 2) * t; return [10 + t * 300, 10 + (-y) * 340]; });
      return `<polyline points="${pts.map(q => q[0].toFixed(1) + ',' + q[1].toFixed(1)).join(' ')}" class="sm-fall" style="opacity:${0.5 + k * 0.25}"/>`;
    }).join('');
    return `<svg viewBox="0 0 320 130" role="img" aria-label="Três quedas hipotéticas de 10%, 20% e 30%">${paths}</svg>`
      + pcts.map(p => `<div class="sm-fr"><span>Se cair ${f(p * 100, 0)}%</span><b class="num dn">${valueAfter > 0 ? '−' + BRL0(valueAfter * p) : ND}</b></div>`).join('');
  }

  function controls() {
    const ps = picks();
    if (!SM.asset || !ps.includes(SM.asset)) SM.asset = ps[0] || null;
    paint($('#smAssets'), ps.map(s => `<button type="button" class="mk-pill" data-sma="${esc(s)}" aria-pressed="${SM.asset === s}">${esc(s)}</button>`).join(''));
    paint($('#smSides'), [['BUY', 'Compra'], ['SELL', 'Venda']].map(([k, l]) => `<button type="button" class="mk-pill" data-sms="${k}" aria-pressed="${SM.side === k}">${l}</button>`).join(''));
    paint($('#smAmts'), AMOUNTS.map(v => `<button type="button" class="mk-pill" data-smv="${v}" aria-pressed="${SM.amount === v}">R$ ${f(v, 0)}</button>`).join(''));
    const r = SM.asset && price(SM.asset);
    $('#smRef').textContent = r ? `Preço de referência: ${(uni(SM.asset).currency === 'USD' ? 'US$ ' : 'R$ ') + f(r.candle.close, 2)} (${FRESH[r.freshness] || r.freshness}). Fonte: ${r.source}. Ao mudar qualquer escolha, a simulação roda sozinha.` : 'Sem cotação deste ativo agora. Ao mudar qualquer escolha, a simulação roda sozinha.';
  }

  function show(d, ctx) {
    const {before, after: aft, key, sym, side, amt} = ctx, wB = weightOf(before, key), wA = weightOf(aft, key), pv = (aft.find(p => p.key === key) || {value: 0}).value;
    const r = price(sym), u = uni(sym), qty = r && u.currency === 'BRL' ? Math.floor(amt / r.candle.close) : null;
    paint($('#smOrbit'), orbit(aft, key));
    paint($('#smLegend'), legend(aft));
    $('#smSum').innerHTML = `<p class="sm-sent">${side === 'BUY' ? 'Registrar uma compra' : 'Registrar uma venda'} de ${BRL0(amt)} em ${esc(sym)} deixaria o ativo com <b>${f(wA * 100, 1)}%</b> da carteira${before.length ? '' : ' (você ainda não informou outras posições)'}.</p>
      <div class="sm-kv"><div><span>Quantidade estimada</span><b class="num">${qty == null ? ND : f(qty, 0)}</b><small>${qty == null ? 'precisa de cotação em reais' : 'valor da ordem ÷ preço de referência'}</small></div><div><span>Valor da ordem</span><b class="num">${BRL0(amt)}</b><small>custos não incluídos</small></div><div><span>Peso na carteira</span><b class="num">${f(wA * 100, 1)}%</b><small>antes: ${f(wB * 100, 1)}%</small></div></div>`;
    paint($('#smFall'), fall(pv));
    paint($('#smConc'), ring(wA, wB) + `<p class="src">${esc(sym)} ${wA * 100 > 25 ? 'fica acima de 25% da carteira: ela fica mais dependente dele.' : 'fica abaixo de 25% da carteira.'}</p>`
      + (d.after && d.after.largestLabel ? `<p class="src">Maior posição depois da ordem: ${esc(d.after.largestLabel)}, com ${f(d.after.largestPercent, 1)}% (atenção a partir de 25%).</p>` : ''));
    $('#smFind').innerHTML = d.findings.map(x => `<li class="${x.level === 'ATTENTION' ? 'att' : ''}">${x.level === 'ATTENTION' ? '<b>Atenção:</b> ' : ''}${esc(x.text)}</li>`).join('');
    $('#smAss').textContent = d.assumptions.join(' ') + ' ' + d.disclaimer;
    $('#smMsg').textContent = '';
  }

  async function run() {
    if (!onPage() || !SM.asset || SM.busy) { if (SM.busy) SM.again = true; return; }
    const key = 'S:' + SM.asset, before = portfolio(), amt = SM.amount, side = SM.side, sym = SM.asset;
    const body = {positions: before.map(p => ({[p.sym ? 'symbol' : 'type']: p.sym || p.key.slice(2), valueBrl: String(p.value)})), order: {side, symbol: sym, valueBrl: String(amt)}, profile: null};
    const my = ++SM.seq; SM.busy = true;
    try {
      const r = await fetch('/api/impact/pre-order', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
      const d = await r.json(); if (!r.ok) throw new Error(d.error || 'dados inválidos');
      if (my === SM.seq) show(d, {before, after: afterList(before, key, sym, side, amt), key, sym, side, amt});
    } catch (e) { $('#smMsg').textContent = 'Não foi possível simular agora: ' + e.message; }
    finally { SM.busy = false; if (SM.again) { SM.again = false; schedule(); } }
  }
  function schedule() { clearTimeout(SM.timer); SM.timer = setTimeout(run, 250); }
  function boot() {
    if (!onPage()) return;
    controls();
    const pos = document.getElementById('smPos');
    if (pos && !SM.obs) { SM.obs = new MutationObserver(schedule); SM.obs.observe(pos, {childList: true}); }  /* a carteira salva preenche as linhas por script, sem evento de input */
    if (pos && !SM.tried && typeof isLogged === 'function' && isLogged() && ![...pos.querySelectorAll('input')].some(i => i.value)) {
      SM.tried = true; const b = document.querySelector('[data-saved="smPos"]'); if (b) b.click();
    }
    schedule();
  }
  document.addEventListener('click', e => {
    const a = e.target.closest('[data-sma]'), s = e.target.closest('[data-sms]'), v = e.target.closest('[data-smv]');
    if (a) SM.asset = a.dataset.sma; else if (s) SM.side = s.dataset.sms; else if (v) { SM.amount = Number(v.dataset.smv); $('#smCustom').value = ''; } else if (e.target.closest('#smRun')) { schedule(); return; } else return;
    controls(); schedule();
  });
  document.addEventListener('input', e => {
    if (e.target.id === 'smCustom') { const n = Number(e.target.value); if (n > 0) { SM.amount = n; controls(); schedule(); } }
    else if (e.target.closest && e.target.closest('#smPos')) schedule();
  });
  document.addEventListener('change', e => { if (e.target.closest && e.target.closest('#smPos')) schedule(); });
  addEventListener('hashchange', () => { if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] === 'simulador') setTimeout(boot, 400); });
  document.addEventListener('mw-auth', () => { SM.tried = false; if (onPage()) setTimeout(boot, 400); });
  setTimeout(boot, 1800);
})();
