/* Saúde dos dados (item #53): constelação de fontes. Tudo vem de /api/status (provedores, circuito, chamadas, falhas, latência média medida pelo servidor e a fonte de cada ativo).
   Nada é simulado: não há série de disponibilidade nem de tempo de resposta no servidor, então elas não são desenhadas. Estrela = provedor; anel de dentro = ligados,
   anel de fora = desligados ou sem chave. Depende de app.js ($, esc, f). Recebe o evento mw:status disparado por loadStatus. Sem global novo. */
(function () {
  const SV = {st: null, pick: null};
  const onPage = () => { const p = document.querySelector('[data-page="saude"]'); return !!p && !p.hidden; };
  const ND = '<span class="gap">não informado</span>';
  const STATE = p => {
    if (!p.enabled) return {k: 'off', label: 'Desligado ou sem chave', cls: ''};
    if (p.circuit === 'OPEN') return {k: 'dn', label: 'Fora do ar', cls: 'am'};
    if (p.circuit === 'HALF_OPEN' || p.circuit === 'RECONECTANDO') return {k: 'warn', label: 'Tentando de novo', cls: 'am'};
    return {k: 'up', label: 'No ar', cls: 'cy'};
  };
  const healthy = p => p.enabled && (p.circuit === 'CLOSED' || p.circuit === 'CONECTADO');
  const usedBy = (p, st) => st.instruments.filter(i => i.source && (p.name.includes(i.source) || i.source.includes(p.name))).length;
  const latency = p => (p.attempts > 0 ? p.callNanos / p.attempts / 1e6 : null);
  const paint = (el, html) => {
    if (el.dataset.h === html) return;
    const a = document.activeElement, key = a && el.contains(a) && a.dataset.svp ? `[data-svp="${CSS.escape(a.dataset.svp)}"]` : '';
    el.innerHTML = html; el.dataset.h = html;
    if (key) { const n = el.querySelector(key); if (n) n.focus({preventScroll: true}); }
  };

  const CX = 300, CY = 215;
  function sky(st) {
    const on = st.providers.filter(p => p.enabled), off = st.providers.filter(p => !p.enabled);
    const star = (p, i, n, r) => {
      const a = -Math.PI / 2 + i * 2 * Math.PI / Math.max(n, 1), x = CX + r * Math.cos(a), y = CY + r * Math.sin(a), s = STATE(p), pick = SV.pick === p.name;
      return `<g class="sv-star sv-${s.k}${pick ? ' pick' : ''}" transform="translate(${x.toFixed(1)} ${y.toFixed(1)})"><g class="sv-ctr"><line x1="0" y1="0" x2="${(CX - x).toFixed(1)}" y2="${(CY - y).toFixed(1)}" class="sv-ray"/>`
        + `<circle r="${pick ? 15 : 11}" class="sv-body" tabindex="0" role="button" data-svp="${esc(p.name)}" aria-pressed="${pick}" aria-label="${esc(p.name)}: ${esc(s.label)}"><title>${esc(p.name)}: ${esc(s.label)}</title></circle>`
        + `<text y="${y < CY ? -20 : 28}" text-anchor="middle" class="sv-lbl">${esc(p.name.replace(/ \(.*\)$/, ''))}</text></g></g>`;
    };
    return `<svg viewBox="0 0 600 430" role="group" aria-label="Constelação de fontes: cada estrela é um provedor de dados. A lista de provedores no painel ao lado traz os mesmos estados em texto.">
      <circle cx="${CX}" cy="${CY}" r="120" class="sv-ring"/><circle cx="${CX}" cy="${CY}" r="175" class="sv-ring"/>
      <g class="sv-orbit sv-in">${on.map((p, i) => star(p, i, on.length, 120)).join('')}</g>
      <g class="sv-orbit sv-out">${off.map((p, i) => star(p, i, off.length, 175)).join('')}</g>
      <circle cx="${CX}" cy="${CY}" r="34" class="sv-sun"/><text x="${CX}" y="${CY + 3}" text-anchor="middle" class="sv-sunt">MERIDIAN</text></svg>`;
  }

  function side(st) {
    const p = st.providers.find(x => x.name === SV.pick) || st.providers.find(healthy) || st.providers[0];
    if (!p) return '<p class="mute">Sem provedores para mostrar.</p>';
    const s = STATE(p), lat = latency(p), tot = p.successes + p.failures, rate = tot > 0 ? p.successes / tot : null, n = usedBy(p, st);
    return `<div class="sv-sh"><h3>${esc(p.name)}</h3><span class="cs-chip ${s.cls}">${esc(s.label)}</span></div>
      <p class="src">${n ? `Fonte de ${n} ativo${n === 1 ? '' : 's'} do painel` : 'Não é a fonte de nenhum ativo do painel agora'}</p>
      <div class="sv-kv"><div><span>Chamadas</span><b class="num">${p.attempts}</b></div><div><span>Sucessos</span><b class="num">${p.successes}</b></div><div><span>Falhas</span><b class="num ${p.failures ? 'dn' : ''}">${p.failures}</b></div>
        <div><span>Barradas</span><b class="num">${p.shortCircuited}</b></div><div><span>Latência média</span><b class="num">${lat == null ? ND : f(lat, 0) + ' ms'}</b></div><div><span>Circuito</span><b>${esc(p.circuit)}</b></div></div>
      <p class="sv-k">Sucessos sobre as chamadas medidas</p>
      ${rate == null ? `<p class="mute">Nenhuma chamada medida desde que o servidor ligou: ${ND}.</p>` : `<div class="sv-bar" role="img" aria-label="${f(rate * 100, 0)}% de sucesso"><i style="width:${(rate * 100).toFixed(1)}%"></i></div><p class="src">${f(rate * 100, 1)}% de sucesso desde que o servidor ligou.</p>`}
      ${p.detail ? `<p class="src">${esc(p.detail)}</p>` : ''}`;
  }

  function render() {
    const st = SV.st; if (!st || !onPage()) return;
    const enabled = st.providers.filter(p => p.enabled), ok = enabled.filter(healthy);
    const live = st.instruments.filter(i => i.status === 'LIVE'), gaps = st.instruments.length - live.length;
    const bad = enabled.filter(p => !healthy(p)).map(p => p.name);
    $('#svKpis').innerHTML = `<article class="sv-kpi"><p>Fontes saudáveis</p><b class="num">${ok.length} de ${enabled.length}</b><small>${bad.length ? esc(bad.join(', ')) + ' fora do normal' : 'todas as fontes ligadas estão no ar'}</small></article>`
      + `<article class="sv-kpi"><p>Ativos com preço</p><b class="num">${live.length} de ${st.instruments.length}</b><small>${gaps ? gaps + ' sem cotação agora' : 'nenhum sem cotação no painel'}</small></article>`
      + `<article class="sv-kpi"><p>Dados "não informados"</p><b class="num">${gaps}</b><small>aparecem como "não informado", nunca como zero</small></article>`;
    paint($('#svSky'), sky(st));
    paint($('#svSide'), side(st));
  }
  document.addEventListener('mw:status', e => { SV.st = e.detail; render(); });
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-svp]'); if (!b || !SV.st) return;
    SV.pick = b.dataset.svp; render();
  });
  document.addEventListener('keydown', e => {
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('#svSky [data-svp]')) { e.preventDefault(); e.target.dispatchEvent(new MouseEvent('click', {bubbles: true})); }
  });
  addEventListener('hashchange', () => { if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] === 'saude') render(); });
  setTimeout(render, 1500);
})();
