/* Cripto 24h (item #54): o que acontece no cripto agora. Só dado real: preço e volume de 24 h vêm das cotações da API (stream da bolsa conectada, com a fonte à vista), os
   fechamentos diários de /api/sparklines dão a variação de 7 dias e os mini-gráficos. O gráfico ao vivo é amostrado aqui, 1 vez por segundo, do preço que a API já recebeu: mostra
   só o que aconteceu desde que a página abriu. Não há negócio a negócio nem lado de compra e venda no stream, então não são desenhados. Valor de mercado não é fonte do projeto:
   a "galáxia" e a "dominância" usam o volume negociado em 24 h, e dizem isso. Depende de app.js (rows, universe, openAsset, hasData, $, esc, f). Sem global novo. */
(function () {
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const LIVE = ['BTC', 'ETH', 'SOL'];
  const ABOUT = {
    BTC: 'A moeda digital mais antiga e a maior. Muita gente a vê como "ouro digital", mas o preço oscila bastante.',
    ETH: 'Rede onde rodam contratos e aplicativos. A moeda paga as taxas de uso da rede.',
    XRP: 'Moeda ligada a uma rede de pagamentos entre bancos e empresas.',
    SOL: 'Rede pensada para transações rápidas e baratas, usada por aplicativos e coleções digitais.',
    BNB: 'Moeda de uma grande corretora de cripto e da rede ligada a ela.',
    DOGE: 'Nasceu como brincadeira em 2013 e virou uma das moedas mais conhecidas, com muita oscilação.',
    ADA: 'Moeda de uma rede que se apresenta como desenvolvida com base em pesquisa acadêmica.',
    AVAX: 'Moeda de uma rede de contratos que busca confirmar transações em poucos segundos.',
    LINK: 'Rede que leva dados do mundo real (como preços) para dentro de contratos.',
    DOT: 'Rede que conecta outras redes entre si.',
    LTC: 'Criada como uma "versão mais leve" do Bitcoin, com confirmações mais rápidas.',
    TRX: 'Moeda de uma rede muito usada para movimentar moedas estáveis.'
  };
  const CR = {ins: null, insAt: 0, hour: null, coin: 'BTC', win: '24h', range: '7d', live: 'BTC', paused: false, buf: [], trades: [], spark: null, sparkAt: 0, pick: 'BTC', timer: 0};
  const onPage = () => { const p = document.querySelector('[data-page="cripto"]'); return !!p && !p.hidden; };
  const ND = '<span class="gap">não informado</span>';
  const usd = v => 'US$ ' + f(v, v >= 100 ? 2 : v >= 1 ? 3 : 4);
  const big = v => v >= 1e9 ? 'US$ ' + f(v / 1e9, 2) + ' bi' : 'US$ ' + f(v / 1e6, 0) + ' mi';
  const signed = (v, d = 1) => (v >= 0 ? '+' : '−') + f(Math.abs(v) * 100, d) + '%';
  const cls = v => (v == null ? '' : v >= 0 ? 'up' : 'dn');
  const paint = (el, html) => {
    if (!el || el.dataset.h === html) return;
    const a = document.activeElement; let key = '';
    if (a && el.contains(a) && a.dataset) for (const k of ['crl', 'crc', 'crw', 'crr', 'cra', 'crh']) if (a.dataset[k] !== undefined) { key = `[data-${k}="${CSS.escape(a.dataset[k])}"]`; break; }
    const top = el.scrollTop; el.innerHTML = html; el.dataset.h = html; el.scrollTop = top;
    if (key) { const n = el.querySelector(key); if (n) n.focus({preventScroll: true}); }
  };

  const coins = () => universe.filter(u => u.assetClass === 'Cripto' && rows[u.symbol] && hasData(rows[u.symbol])).map(u => {
    const r = rows[u.symbol], c = r.candle, sp = CR.spark && CR.spark[u.symbol], n = sp ? sp.length : 0;
    return {u, r, price: c.close, vol: c.volume > 0 ? c.volume : null, d1: c.open > 0 ? c.close / c.open - 1 : null,
      d7: n > 7 ? sp[n - 1] / sp[n - 8] - 1 : null, d30: n > 1 ? sp[n - 1] / sp[0] - 1 : null, sp};
  });
  const bySym = (list, s) => list.find(x => x.u.symbol === s);

  async function loadSpark() {
    if (Date.now() - CR.sparkAt < 300000) return;
    CR.sparkAt = Date.now();
    try { const r = await fetch('/api/sparklines'); if (r.ok) CR.spark = await r.json(); } catch (e) { /* sem mini-gráficos: a tela mostra "não informado" */ }
  }

  /* ---------- relógio de 24 horas e medo e ganância (/api/crypto/insights) ---------- */
  async function loadIns() {
    if (Date.now() - CR.insAt < 300000) return;
    CR.insAt = Date.now();
    try { const r = await fetch('/api/crypto/insights'); if (r.ok) CR.ins = await r.json(); else CR.insAt = Date.now() - 240000; } catch (e) { CR.insAt = Date.now() - 240000; }
  }
  const nowBrt = () => { const p = new Intl.DateTimeFormat('pt-BR', {timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit', hour12: false}).formatToParts(new Date()); return {h: Number(p.find(x => x.type === 'hour').value) % 24, m: p.find(x => x.type === 'minute').value}; };
  function clock() {
    const hv = CR.ins && CR.ins.hourly, t = nowBrt();
    if (!hv) { $('#crClock').innerHTML = '<p class="mute">As bolsas não entregaram as barras horárias agora: ' + ND + '. Nada é estimado.</p>'; $('#crClockTxt').innerHTML = ''; return; }
    $('#crClkSrc').textContent = hv.source;
    const sel = CR.hour == null ? t.h : CR.hour, cx = 150, cy = 150, r0 = 62, rmax = 118;
    const bars = hv.hours.map(x => {
      const a = (x.hour / 24) * Math.PI * 2 - Math.PI / 2, a2 = ((x.hour + 0.86) / 24) * Math.PI * 2 - Math.PI / 2, r1 = x.relative == null ? r0 + 4 : r0 + 6 + (rmax - r0 - 6) * x.relative;
      const p = (rr, aa) => (cx + rr * Math.cos(aa)).toFixed(1) + ' ' + (cy + rr * Math.sin(aa)).toFixed(1);
      const fill = x.relative == null ? 'transparent' : x.hour === hv.peakHour ? '#ffb340' : '#8f86e8';
      return `<path d="M${p(r0, a)}L${p(r1, a)}L${p(r1, a2)}L${p(r0, a2)}Z" fill="${fill}" class="cr-hb${x.hour === sel ? ' sel' : ''}${x.relative == null ? ' nd' : ''}" tabindex="0" role="button" data-crh="${x.hour}" aria-pressed="${x.hour === sel}" aria-label="${x.hour} horas: ${x.relative == null ? 'sem dado' : f(x.relative * 100, 0) + '% do pico'}"><title>${x.hour}h</title></path>`;
    }).join('');
    const ang = ((t.h + Number(t.m) / 60) / 24) * Math.PI * 2 - Math.PI / 2;
    paint($('#crClock'), `<svg viewBox="0 0 300 300" role="group" aria-label="Relógio de 24 horas: cada barra é o volume médio do Bitcoin naquela hora do dia. O texto abaixo traz o mesmo número da hora escolhida.">${bars}`
      + `<line x1="${cx}" y1="${cy}" x2="${(cx + (rmax + 6) * Math.cos(ang)).toFixed(1)}" y2="${(cy + (rmax + 6) * Math.sin(ang)).toFixed(1)}" class="cr-hand"/><circle cx="${(cx + (rmax + 6) * Math.cos(ang)).toFixed(1)}" cy="${(cy + (rmax + 6) * Math.sin(ang)).toFixed(1)}" r="4" class="cr-hd"/>`
      + `<text x="${cx}" y="${cy - 14}" text-anchor="middle" class="cr-ck">AGORA</text><text x="${cx}" y="${cy + 12}" text-anchor="middle" class="cr-cv">${String(t.h).padStart(2, '0')}:${t.m}</text><text x="${cx}" y="${cy + 30}" text-anchor="middle" class="cr-ck">cripto aberto</text></svg>`);
    const hr = hv.hours[sel];
    $('#crClockTxt').innerHTML = `<p class="cr-cl"><b>Hora ${sel}h</b> · ${hr.relative == null ? 'volume ' + ND : 'volume relativo <b class="num">' + f(hr.relative * 100, 0) + '%</b> do pico (' + big(hr.averageUsd) + ' por hora, média de ' + hr.samples + ' dias)'}.</p><p class="cr-cl">Pico: <b>${hv.peakHour}h</b>. <span class="cr-key"><i style="background:#8f86e8"></i>volume por hora <i style="background:#ffb340"></i>pico</span></p>`;
  }
  function fear() {
    const fg = CR.ins && CR.ins.fearGreed;
    if (!fg) { $('#crFg').innerHTML = '<p class="mute">O índice não respondeu agora: ' + ND + '. Nada é estimado.</p>'; return; }
    const v = fg.value, a = Math.PI * (1 - v / 100), nx = 100 + 62 * Math.cos(a), ny = 100 - 62 * Math.sin(a);
    $('#crFg').innerHTML = `<svg viewBox="0 0 200 120" role="img" aria-label="Medo e ganância: ${v} de 100, ${esc(fg.label || '')}"><defs><linearGradient id="crFgG" x1="0" x2="1"><stop offset="0" stop-color="#ff6d82"/><stop offset=".5" stop-color="#ffc83d"/><stop offset="1" stop-color="#38dd9c"/></linearGradient></defs>`
      + `<path d="M20 100A80 80 0 0 1 180 100" fill="none" stroke="url(#crFgG)" stroke-width="12" stroke-linecap="round"/><line x1="100" y1="100" x2="${nx.toFixed(1)}" y2="${ny.toFixed(1)}" class="cr-hand"/><circle cx="100" cy="100" r="6" class="cr-hd"/></svg>`
      + `<p class="cr-fgv num">${v}</p><p class="cr-fgl">${esc(fg.label || '')}</p><p class="src">Atualizado em ${new Date(fg.updatedAt).toLocaleDateString('pt-BR')} · ${esc(fg.source)}.</p>`;
  }

  /* ---------- ao vivo ---------- */
  function sample() {
    if (!onPage() || CR.paused) return;
    const r = rows[CR.live]; if (!r || !hasData(r)) return;
    const p = r.candle.close, last = CR.buf[CR.buf.length - 1];
    CR.buf.push(p); if (CR.buf.length > 120) CR.buf.shift();
    if (!last || last !== p) { CR.trades.unshift({t: new Date(), p, up: last == null ? null : p >= last}); if (CR.trades.length > 8) CR.trades.pop(); }
    live();
  }
  function lineSvg(vals, W, H, marker) {
    let lo = Math.min(...vals), hi = Math.max(...vals);
    const min = hi * 0.002;   /* a escala nunca fica mais apertada que 0,2% do preço: um tremor de centavos não vira montanha */
    if (hi - lo < min) { const mid = (hi + lo) / 2; lo = mid - min / 2; hi = mid + min / 2; }
    const pad = (hi - lo) * 0.08 || 1, mn = lo - pad, mx = hi + pad;
    const X = i => (W - 70) * i / Math.max(vals.length - 1, 1) + 4, Y = v => 6 + (H - 12) * (1 - (v - mn) / (mx - mn));
    const pts = vals.map((v, i) => X(i).toFixed(1) + ',' + Y(v).toFixed(1));
    return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" class="cr-line" aria-hidden="true"><defs><linearGradient id="crG" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#8fa8ff" stop-opacity=".35"/><stop offset="1" stop-color="#8fa8ff" stop-opacity="0"/></linearGradient></defs>`
      + `<polygon points="${X(0).toFixed(1)},${H} ${pts.join(' ')} ${X(vals.length - 1).toFixed(1)},${H}" fill="url(#crG)"/><polyline points="${pts.join(' ')}" fill="none" stroke="#a98bff" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"/>`
      + (marker ? `<line x1="${X(vals.length - 1).toFixed(1)}" x2="${W}" y1="${Y(vals[vals.length - 1]).toFixed(1)}" y2="${Y(vals[vals.length - 1]).toFixed(1)}" stroke="#ffb340" stroke-dasharray="3 4"/><circle cx="${X(vals.length - 1).toFixed(1)}" cy="${Y(vals[vals.length - 1]).toFixed(1)}" r="5" fill="#ffb340"/>` : '') + '</svg>';
  }
  function live() {
    const u = universe.find(x => x.symbol === CR.live), r = rows[CR.live];
    paint($('#crTabs'), LIVE.map(s => `<button type="button" class="mk-pill" data-crl="${s}" aria-pressed="${CR.live === s}">${s}</button>`).join('') + `<button type="button" class="mk-pill" data-crl="pause" aria-pressed="${CR.paused}">${CR.paused ? 'Retomar' : 'Pausar'}</button>`);
    if (!r || !hasData(r) || !CR.buf.length) { $('#crLive').innerHTML = '<p class="mute">Sem cotação ao vivo deste ativo agora: ' + ND + '.</p>'; return; }
    const first = CR.buf[0], p = CR.buf[CR.buf.length - 1], d = first > 0 ? p / first - 1 : null;
    $('#crSrc').textContent = r.source + (r.freshness === 'REAL_TIME' ? ' · tempo real' : ' · ' + (r.freshness || ''));
    $('#crPrice').innerHTML = `<b class="num">${usd(p)}</b><small class="num ${cls(d)}">${d == null ? '' : signed(d, 2) + ' na janela'}</small><small>USD · ${esc(u ? u.name : CR.live)}</small>`;
    paint($('#crChart'), lineSvg(CR.buf.length > 1 ? CR.buf : [p, p], 640, 220, true));
    $('#crStats').innerHTML = `<span>máxima da janela <b class="num">${usd(Math.max(...CR.buf))}</b></span><span>mínima <b class="num">${usd(Math.min(...CR.buf))}</b></span><span>amostras <b class="num">${CR.buf.length}</b></span>`;
    paint($('#crTrades'), CR.trades.map(t => `<li><span class="num">${t.t.toLocaleTimeString('pt-BR')}</span><b class="num ${t.up == null ? '' : t.up ? 'up' : 'dn'}">${usd(t.p)}</b><span class="${t.up == null ? '' : t.up ? 'up' : 'dn'}">${t.up == null ? '·' : t.up ? '▲ subiu' : '▼ caiu'}</span></li>`).join(''));
  }

  /* ---------- preço da moeda escolhida ---------- */
  function priceChart(list) {
    const c = bySym(list, CR.pick); if (!c || !c.sp) { $('#crPriceBox').innerHTML = '<p class="mute">Sem histórico desta moeda agora: ' + ND + '.</p>'; return; }
    const n = CR.range === '7d' ? 8 : c.sp.length, vals = c.sp.slice(-n);
    paint($('#crRanges'), ['7d', '30d'].map(k => `<button type="button" class="mk-pill" data-crr="${k}" aria-pressed="${CR.range === k}">${k}</button>`).join(''));
    const d = vals[0] > 0 ? vals[vals.length - 1] / vals[0] - 1 : null;
    $('#crPriceHead').innerHTML = `<span class="src">${esc(c.u.symbol)}</span> <b class="num">${usd(c.price)}</b> <span class="num ${cls(d)}">${d == null ? '' : signed(d, 2)}</span>`;
    paint($('#crPriceBox'), lineSvg(vals, 640, 250, false));
    $('#crPriceNote').textContent = `Fechamentos diários dos últimos ${vals.length} dias (série real). Cripto negocia também no fim de semana. Fonte: ${c.r.source} (cotação) e histórico diário do painel.`;
  }

  /* ---------- sistema solar de moedas ---------- */
  function galaxy(list) {
    const ranked = list.filter(x => x.vol != null).sort((a, b) => b.vol - a.vol);
    if (!ranked.length) return '<p class="mute">Sem volume de 24 h agora: ' + ND + '.</p>';
    const W = 560, H = 440, cx = W / 2, cy = H / 2, sun = ranked[0], maxV = sun.vol;
    const rad = x => 8 + 34 * Math.sqrt(x.vol / maxV), chg = x => (CR.win === '24h' ? x.d1 : x.d7);
    const col = v => { if (v == null) return '#9aa6c4'; const t = Math.max(-1, Math.min(1, v / (CR.win === '24h' ? 0.05 : 0.12))); return t >= 0 ? `rgb(${Math.round(120 - 64 * t)},${Math.round(150 + 71 * t)},${Math.round(160 - 4 * t)})` : `rgb(${Math.round(150 + 105 * -t)},${Math.round(130 - 21 * -t)},${Math.round(150 - 20 * -t)})`; };
    const others = ranked.slice(1), orbits = others.map((_, i) => 98 + i * 14.5);
    const rings = orbits.map(r => `<ellipse cx="${cx}" cy="${cy}" rx="${r.toFixed(0)}" ry="${(r * 0.82).toFixed(0)}" class="cr-ring"/>`).join('');
    const pl = others.map((x, i) => {
      const a = i * 2.399 + 0.6, r = orbits[i], px = cx + r * Math.cos(a), py = cy + r * 0.82 * Math.sin(a), pick = x.u.symbol === CR.pick;
      return `<g class="cr-pl" style="--d:${70 + i * 18}s" transform="translate(${px.toFixed(1)} ${py.toFixed(1)})"><g class="cr-ctr"><circle r="${rad(x).toFixed(1)}" fill="${col(chg(x))}" class="cr-p${pick ? ' pick' : ''}" tabindex="0" role="button" data-crc="${esc(x.u.symbol)}" aria-pressed="${pick}" aria-label="${esc(x.u.name)}: ${chg(x) == null ? 'variação não informada' : signed(chg(x))} em ${CR.win}, volume de 24 horas ${big(x.vol)}"><title>${esc(x.u.symbol)} ${chg(x) == null ? '' : signed(chg(x))}</title></circle><text y="4" text-anchor="middle" class="cr-pt">${esc(x.u.symbol)}</text></g></g>`;
    }).join('');
    const spick = sun.u.symbol === CR.pick;
    return `<svg viewBox="0 0 ${W} ${H}" role="group" aria-label="Sistema de moedas: o tamanho é o volume negociado em 24 horas e a cor é a variação. A tabela abaixo traz os mesmos números.">${rings}<g class="cr-orb">${pl}</g>`
      + `<circle cx="${cx}" cy="${cy}" r="${rad(sun).toFixed(1)}" fill="${col(chg(sun))}" class="cr-sun cr-p${spick ? ' pick' : ''}" tabindex="0" role="button" data-crc="${esc(sun.u.symbol)}" aria-pressed="${spick}" aria-label="${esc(sun.u.name)}: ${chg(sun) == null ? 'variação não informada' : signed(chg(sun))} em ${CR.win}"><title>${esc(sun.u.symbol)}</title></circle><text x="${cx}" y="${cy + 5}" text-anchor="middle" class="cr-pt big">${esc(sun.u.symbol)}</text></svg>`;
  }
  function detail(list) {
    const c = bySym(list, CR.pick) || list[0]; if (!c) return '<p class="mute">Sem moedas com cotação agora.</p>';
    const spark = c.sp && c.sp.length > 1 ? lineSvg(c.sp.slice(-8), 320, 80, false) : '';
    return `<div class="cr-dh"><h3>${esc(c.u.symbol)} · ${esc(c.u.name)}</h3></div><p class="cr-about">${esc(ABOUT[c.u.symbol] || '')}</p>
      <div class="cr-kv"><div><span>24 horas</span><b class="num ${cls(c.d1)}">${c.d1 == null ? ND : signed(c.d1)}</b></div><div><span>7 dias</span><b class="num ${cls(c.d7)}">${c.d7 == null ? ND : signed(c.d7)}</b></div>
      <div><span>Preço</span><b class="num">${usd(c.price)}</b></div><div><span>Volume em 24 h</span><b class="num">${c.vol == null ? ND : big(c.vol)}</b></div></div>${spark}
      <p class="src">Cripto é muito volátil: variações de 5% ao dia são comuns. Informação, não recomendação. Fonte: ${esc(c.r.source)}.</p><button type="button" class="ghost" data-cra="${esc(c.u.symbol)}">Abrir a página do ativo</button>`;
  }

  /* ---------- dominância e tabela ---------- */
  function donut(list) {
    const withV = list.filter(x => x.vol != null), tot = withV.reduce((a, x) => a + x.vol, 0); if (!(tot > 0)) return '<p class="mute">Sem volume de 24 h agora: ' + ND + '.</p>';
    const top = withV.slice().sort((a, b) => b.vol - a.vol), parts = [['BTC', '#ffb340'], ['ETH', '#8fa8ff']].map(([s, c]) => { const x = bySym(top, s); return x ? {l: s, v: x.vol, c} : null; }).filter(Boolean);
    const rest = tot - parts.reduce((a, p) => a + p.v, 0); parts.push({l: 'Outras', v: rest, c: '#b48cff'});
    const R = 62, C = 2 * Math.PI * R; let off = 0;
    const arcs = parts.map(p => { const len = p.v / tot * C, s = `<circle cx="100" cy="100" r="${R}" fill="none" stroke="${p.c}" stroke-width="22" stroke-dasharray="${len.toFixed(1)} ${(C - len).toFixed(1)}" stroke-dashoffset="${(-off).toFixed(1)}" transform="rotate(-90 100 100)"><title>${esc(p.l)}: ${f(p.v / tot * 100, 1)}%</title></circle>`; off += len; return s; }).join('');
    return `<div class="cr-don"><svg viewBox="0 0 200 200" role="img" aria-label="Participação no volume de 24 horas das moedas listadas">${arcs}<text x="100" y="97" text-anchor="middle" class="cr-dv">${esc(big(tot))}</text><text x="100" y="116" text-anchor="middle" class="cr-dl">volume em 24 h</text></svg>`
      + `<ul class="cr-leg">${parts.map(p => `<li><i style="background:${p.c}"></i><span>${esc(p.l)}</span><b class="num">${f(p.v / tot * 100, 1)}%</b></li>`).join('')}</ul></div>`;
  }
  function openNow() {
    const ex = {B3: 'B3 (São Paulo)', NYSE: 'Nova York', LSE: 'Londres', XETRA: 'Frankfurt', EURONEXT_AMS: 'Amsterdã', TSE: 'Tóquio', HKEX: 'Hong Kong', ASX: 'Sydney', TADAWUL: 'Riad', JSE: 'Joanesburgo'}, open = new Set(), seen = new Set();
    universe.forEach(u => { if (ex[u.exchange] && rows[u.symbol]) { seen.add(u.exchange); if (rows[u.symbol].marketOpen) open.add(u.exchange); } });
    const names = k => [...k].map(x => ex[x]).join(', ');
    const closed = [...seen].filter(x => !open.has(x));
    return `<p class="cr-24">Cripto: <b>aberto agora</b>, como em todas as horas, inclusive no fim de semana.</p><p class="cr-24">Bolsas abertas agora: <b>${open.size ? esc(names(open)) : 'nenhuma'}</b>.</p><p class="cr-24 mute">${closed.length ? 'Fechadas: ' + esc(names(closed)) + '.' : ''}</p>`;
  }
  function table(list) {
    const rowsH = list.slice().sort((a, b) => (b.vol || 0) - (a.vol || 0)).map(c => {
      const sp = c.sp && c.sp.length > 7 ? c.sp.slice(-8) : null, up = sp && sp[sp.length - 1] >= sp[0];
      const mini = sp ? `<svg viewBox="0 0 90 28" class="cr-mini"><polyline points="${sp.map((v, i) => (i * 90 / (sp.length - 1)).toFixed(1) + ',' + (26 - 24 * (v - Math.min(...sp)) / ((Math.max(...sp) - Math.min(...sp)) || 1)).toFixed(1)).join(' ')}" fill="none" stroke="${up ? '#38dd9c' : '#ff6d82'}" stroke-width="1.8"/></svg>` : ND;
      return `<tr><td><b>${esc(c.u.symbol)}</b> <small>${esc(c.u.name)}</small></td><td class="n num">${usd(c.price)}</td><td class="n num ${cls(c.d1)}">${c.d1 == null ? ND : signed(c.d1)}</td><td class="n num ${cls(c.d7)}">${c.d7 == null ? ND : signed(c.d7)}</td><td class="n num">${c.vol == null ? ND : big(c.vol)}</td><td>${mini}</td></tr>`;
    }).join('');
    return `<div class="scroll"><table><thead><tr><th>Moeda</th><th class="n">Preço</th><th class="n">24 h</th><th class="n">7 d</th><th class="n">Volume em 24 h</th><th>7 dias</th></tr></thead><tbody>${rowsH}</tbody></table></div>`;
  }

  function render() {
    if (!onPage() || typeof universe === 'undefined') return;
    const list = coins();
    paint($('#crWin'), ['24h', '7d'].map(k => `<button type="button" class="mk-pill" data-crw="${k}" aria-pressed="${CR.win === k}">${k}</button>`).join(''));
    paint($('#crGal'), galaxy(list)); paint($('#crDet'), detail(list)); paint($('#crDon'), donut(list)); paint($('#crOpen'), openNow()); paint($('#crTbl'), table(list));
    $('#crCount').textContent = `${list.length} moedas · cotação por ${[...new Set(list.map(x => x.r.source))].join(', ') || 'nenhuma fonte conectada'}`;
    priceChart(list); live(); clock(); fear();
  }
  document.addEventListener('click', e => {
    const l = e.target.closest('[data-crl]'); if (l) { if (l.dataset.crl === 'pause') CR.paused = !CR.paused; else { CR.live = l.dataset.crl; CR.buf = []; CR.trades = []; sample(); } live(); return; }
    const w = e.target.closest('[data-crw]'); if (w) { CR.win = w.dataset.crw; render(); return; }
    const r = e.target.closest('[data-crr]'); if (r) { CR.range = r.dataset.crr; render(); return; }
    const c = e.target.closest('[data-crc]'); if (c) { CR.pick = c.dataset.crc; render(); return; }
    const hh = e.target.closest('[data-crh]'); if (hh) { CR.hour = Number(hh.dataset.crh); clock(); return; }
    const a = e.target.closest('[data-cra]'); if (a && typeof openAsset === 'function') openAsset(a.dataset.cra);
  });
  document.addEventListener('keydown', e => { if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('#crGal [data-crc], #crClock [data-crh]')) { e.preventDefault(); e.target.dispatchEvent(new MouseEvent('click', {bubbles: true})); } });
  async function boot() { await Promise.all([loadSpark(), loadIns()]); render(); }
  addEventListener('hashchange', () => { if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] === 'cripto') setTimeout(boot, 300); });
  setInterval(sample, 1000);
  setInterval(() => { if (onPage()) { loadSpark(); loadIns(); render(); } }, 5000);
  setTimeout(() => { if (onPage()) boot(); }, 1500);
})();
