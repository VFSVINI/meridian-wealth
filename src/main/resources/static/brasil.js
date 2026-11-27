/* Brasil: juros e inflação no visual Cosmos (item #50). Tudo vem de /api/brazil/history (séries oficiais do Banco Central, com a data de cada ponto): os três gráficos, as mudanças da
   meta Selic (a linha do tempo) e o juro real. As contas dos exemplos são aritmética simples sobre o valor real mais recente e dizem isso. O que o Banco Central não entregou
   aparece como "não informado" (nunca zero, nunca estimado). Depende de app.js ($, esc). Sem global novo. */
(function () {
  const BR = {data: null, tab: 'selic', at: 0, loading: false};
  const num = (v, d = 2) => Number(v).toLocaleString('pt-BR', {minimumFractionDigits: d, maximumFractionDigits: d});
  const day = iso => new Date(iso + 'T12:00:00').toLocaleDateString('pt-BR', {day: '2-digit', month: '2-digit', year: 'numeric'});
  const month = iso => new Date(iso + 'T12:00:00').toLocaleDateString('pt-BR', {month: 'short', year: '2-digit'}).replace('.', '');
  const onPage = () => { const p = document.querySelector('[data-page="brasil"]'); return !!p && !p.hidden; };
  const ND = '<span class="gap">não informado</span>';

  function chart(s, kind, fmt) {
    const pts = s.points.map(p => ({d: p.date, v: Number(p.value)}));
    if (pts.length < 2) return '<p class="mute">Poucos pontos para o gráfico.</p>';
    const lo = Math.min(...pts.map(p => p.v)), hi = Math.max(...pts.map(p => p.v)), pad = (hi - lo) * 0.12 || hi * 0.02, a = lo - pad, b = hi + pad;
    const W = 400, H = 150, x = i => i / (pts.length - 1) * W, y = v => H - 4 - (v - a) / (b - a) * (H - 10);
    const d = pts.map((p, i) => (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(p.v).toFixed(1)).join('');
    const area = kind === 'area' ? `<path d="${d}L${W} ${H}L0 ${H}Z" class="fill"/>` : '';
    const first = pts[0], last = pts[pts.length - 1];
    const label = `${s.label}: de ${fmt(first.v)} em ${day(first.d)} a ${fmt(last.v)} em ${day(last.d)}; mínimo ${fmt(lo)} e máximo ${fmt(hi)}.`;
    return `<div class="br-chart"><span class="br-yt">${fmt(hi)}</span><span class="br-yb">${fmt(lo)}</span><svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" role="img" aria-label="${esc(label)}">${area}<path d="${d}" class="line" vector-effect="non-scaling-stroke"/></svg>`
      + `<div class="br-xa"><span>${esc(month(first.d))}</span><span>${esc(month(last.d))}</span></div></div>`;
  }

  function card(key, title, sub, extra, kind, fmt) {
    const s = BR.data.series[key];
    if (!s) return `<article class="br-card"><p class="br-k">${esc(title)}</p><b class="br-v">${ND}</b><p class="br-s">${esc(sub)}</p><p class="mute">O Banco Central não entregou esta série agora. Nada é estimado.</p></article>`;
    return `<article class="br-card ${key}"><p class="br-k">${esc(title)}<span class="cs-chip ${s.stale ? 'dn' : 'cy'}">${s.stale ? 'desatualizado' : esc(s.dateLabel + ' ' + (key === 'ipca12m' ? month(s.lastDate) : day(s.lastDate)))}</span></p>`
      + `<b class="br-v num">${esc(fmt(Number(s.lastValue)))}</b><p class="br-s">${esc(sub)}${extra ? ' · ' + esc(extra) : ''}</p>${chart(s, kind, fmt)}</article>`;
  }

  function cards() {
    const pct = v => num(v) + '%';
    $('#brCards').innerHTML = card('selic', 'Selic', 'ao ano · meta definida pelo Copom', '', 'area', pct)
      + card('ipca12m', 'IPCA, 12 meses', 'inflação oficial acumulada', '', 'line', pct)
      + card('ptax', 'Dólar (PTAX)', 'R$ por US$ · taxa de referência', '', 'area', v => 'R$ ' + num(v));
    const srcs = Object.values(BR.data.series).map(s => s.source).filter((v, i, a) => a.indexOf(v) === i);
    $('#brSrc').textContent = 'Fonte: Banco Central do Brasil (' + (srcs.join(', ') || 'SGS') + '). Cada ponto traz a data de referência; valores exatos como o Banco Central os publicou. Janela de 12 meses.';
  }

  const last = k => { const s = BR.data.series[k]; return s ? Number(s.lastValue) : null; };
  function explain() {
    const selic = last('selic'), ipca = last('ipca12m'), usd = last('ptax');
    const T = {
      selic: {h: 'Selic alta encarece o crédito', p: 'A Selic é a taxa básica de juros. Quando sobe, empréstimos e financiamentos ficam mais caros e a renda fixa paga mais; quando cai, o crédito alivia e a renda fixa rende menos.',
        pocket: 'Prestações de financiamento sobem e o CDB e o Tesouro Selic rendem mais.',
        ex: selic == null ? ND : `Com a Selic a ${num(selic)}% ao ano, R$ 10.000 num título pós-fixado rendem cerca de R$ ${num(10000 * selic / 100, 0)} em um ano, antes do imposto (conta simples sobre a meta atual).`},
      inflacao: {h: 'Inflação alta corrói o poder de compra', p: 'O IPCA mede quanto os preços subiram. Se o seu dinheiro rende menos do que a inflação, você compra menos coisas no futuro, mesmo com o saldo crescendo.',
        pocket: 'Supermercado, aluguel e contas pesam mais, e só um rendimento acima da inflação aumenta o que você consegue comprar.',
        ex: ipca == null ? ND : `Com o IPCA em ${num(ipca)}% em 12 meses, o que custava R$ 100 há um ano custa em média cerca de R$ ${num(100 * (1 + ipca / 100))} hoje (conta simples sobre o acumulado).`},
      dolar: {h: 'Dólar alto encarece o que vem de fora', p: 'O câmbio é o preço do dólar em reais. Dólar mais alto encarece importados, viagens e dívidas em dólar, e favorece empresas que vendem para fora.',
        pocket: 'Eletrônicos, combustíveis e passagens tendem a subir; quem recebe em dólar ganha mais reais.',
        ex: usd == null ? ND : `Com o dólar a R$ ${num(usd)}, um produto de US$ 100 custa cerca de R$ ${num(100 * usd)} (conta simples pela taxa de referência).`}
    }[BR.tab];
    $('#brExplain').innerHTML = `<h3>${esc(T.h)}</h3><p>${esc(T.p)}</p><div class="br-boxes"><div class="pocket"><span>No seu bolso</span><p>${esc(T.pocket)}</p></div><div class="ex"><span>Exemplo</span><p>${T.ex.startsWith('<') ? T.ex : esc(T.ex)}</p></div></div>`;
    document.querySelectorAll('[data-brt]').forEach(b => { b.setAttribute('aria-pressed', String(b.dataset.brt === BR.tab)); b.setAttribute('aria-selected', String(b.dataset.brt === BR.tab)); });
  }

  function real() {
    const c = BR.data.comparison || [];
    if (!c.length) { $('#brReal').innerHTML = '<p class="br-pk">Juro real: quanto sobra da inflação</p><p class="mute">Sem o IPCA de 12 meses não dá para calcular o juro real. <b>não informado</b>.</p>'; return; }
    const max = Math.max(...c.map(x => Number(x.nominal)));
    $('#brReal').innerHTML = `<p class="br-pk">Juro real: quanto sobra da inflação <span class="cs-chip">taxas ao ano</span></p><div class="br-bars" role="list">${c.map(x => `<div class="br-bar ${esc(x.key)}" role="listitem"><span>${esc(x.label)}</span><i style="width:${(Number(x.nominal) / max * 100).toFixed(1)}%"></i><b class="num">${num(x.nominal)}%</b><small class="num ${Number(x.real) >= 0 ? 'up' : 'dn'}">${x.key === 'ipca' ? 'referência' : 'juro real ' + (Number(x.real) >= 0 ? '+' : '') + num(x.real) + '%'}</small></div>`).join('')}</div>`
      + `<p class="src">Juro real = (1 + taxa) ÷ (1 + IPCA de 12 meses) − 1. Antes de imposto e taxas. Poupança pela regra legal (${esc((c.find(x => x.key === 'savings') || {}).rule || '')}). Comparação ilustrativa de ganho ao ano.</p>`;
  }

  function timeline() {
    const d = (BR.data.decisions || []).slice(0, 6);
    $('#brTimeline').innerHTML = `<p class="br-pk">Linha do tempo da meta Selic <span class="cs-chip">mudanças reais</span></p>`
      + (d.length ? `<ol class="br-tl">${d.map(x => `<li class="${x.direction === 'ALTA' ? 'rise' : 'fall'}"><time datetime="${esc(x.date)}">${esc(day(x.date))}</time><b>${x.direction === 'ALTA' ? 'Meta sobe' : 'Meta cai'}: ${num(x.from)}% → ${num(x.to)}%</b><small>${x.direction === 'ALTA' ? 'Juros mais altos para frear a inflação.' : 'Juros mais baixos para aliviar o crédito.'}</small></li>`).join('')}</ol>`
        : '<p class="mute">Nenhuma mudança da meta nos últimos 24 meses, ou a série não chegou. <b>não informado</b>.</p>')
      + '<p class="src">Só aparecem as reuniões em que a meta mudou: a série pública do Banco Central não registra as que a deixaram igual.</p>';
  }

  function render() {
    if (!BR.data) return;
    cards(); explain(); real(); timeline();
  }
  async function load(force) {
    if (BR.loading || (!force && BR.data && Date.now() - BR.at < 600000)) return;
    BR.loading = true;
    try {
      const r = await fetch('/api/brazil/history');
      if (!r.ok) throw new Error('history');
      BR.data = await r.json(); BR.at = Date.now();
      render();
    } catch (e) {
      if (!BR.data) $('#brCards').innerHTML = '<p class="mute">Sem resposta do servidor para o histórico do Banco Central agora. Tente de novo em instantes.</p>';
    } finally { BR.loading = false; }
  }
  document.addEventListener('click', e => { const t = e.target.closest('[data-brt]'); if (t) { BR.tab = t.dataset.brt; explain(); } });
  function onRoute() { if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] === 'brasil') load(false); }
  addEventListener('hashchange', onRoute);
  setInterval(() => { if (onPage()) load(false); }, 60000);
  onRoute();
})();
