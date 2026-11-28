/* Risco (item #53): cone de patrimônio por ano e "paisagem de probabilidades". Tudo sai do resultado que montecarlo.js já recebe do servidor (evento mw:mc-data): os percentis por mês
   e as densidades por ano (quantas das 5.000 trajetórias caíram em cada faixa de patrimônio, em cada ano). Nada é sorteado aqui e tudo é projeção, nunca promessa. Depende de app.js,
   montecarlo.js ($, f, MC, BRL). Sem global novo. */
(function () {
  const RK = {d: null, year: null, timer: null};
  const mil = v => { const n = Number(v); return n >= 1e6 ? 'R$ ' + f(n / 1e6, 2) + ' mi' : 'R$ ' + f(n / 1e3, 0) + ' mil'; };
  const onPage = () => { const p = document.querySelector('[data-page="montecarlo"]'); return !!p && !p.hidden; };
  const paint = (el, html) => { if (el && el.dataset.h !== html) { el.innerHTML = html; el.dataset.h = html; } };

  function ridges(d) {
    const dn = d.density, n = dn.counts.length - 1, W = 640, left = 24, right = 24, top = 24, step = 34;
    const want = Math.min(n, 9), years = [...new Set(Array.from({length: want + 1}, (_, i) => Math.round(i * n / want)))];
    const bins = dn.counts[0].length, xs = i => left + (W - left - right - 30) * (i + 0.5) / bins, finalPeak = Math.max(...dn.counts[n].map(Number)), unit = 78 / finalPeak, cap = 175;
    const smooth = a => a.map((_, i) => ((a[i - 1] ?? a[i]) + 2 * a[i] + (a[i + 1] ?? a[i])) / 4);
    const med = [];
    const g = years.map((y, k) => {
      const base = top + 130 + k * step, ox = k * 3.5, c = smooth(dn.counts[y].map(Number)), pts = c.map((v, i) => [xs(i) + ox, base - Math.min(cap, v * unit)]);
      const line = pts.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join('');
      const m = d.points[y * 12], mb = m ? Number(m.p50) / Number(dn.edges[dn.edges.length - 1]) * bins : 0;
      med.push([left + (W - left - right - 30) * Math.min(1, mb / bins) + ox, base]);
      const last = k === years.length - 1, pick = y === RK.year;
      return `<g class="rk-r${last ? ' fin' : ''}${pick ? ' pick' : ''}"><line x1="${left + ox}" x2="${W - right - 30 + ox}" y1="${base}" y2="${base}" class="rk-base"/>`
        + `<path d="${line}L${pts[pts.length - 1][0].toFixed(1)} ${base}L${pts[0][0].toFixed(1)} ${base}Z" class="rk-fill"/><path d="${line}" class="rk-line"/><title>Ano ${y}</title></g>`;
    });
    const H = top + 130 + (years.length - 1) * step + 28;   /* a última curva (o ano final) precisa caber inteira: a altura segue o número de anos */
    const trail = `<path d="${med.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join('')}" class="rk-med"/>`;
    return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Paisagem de probabilidades: uma curva por ano mostra onde terminaram as trajetórias simuladas; com o tempo a montanha se espalha. A tabela de percentis, nos gráficos detalhados, traz os mesmos números.">${g.join('')}${trail}</svg>`;
  }

  function cone(d) {
    const W = 640, H = 270, l = 12, r = 16, t = 14, b = 24, pts = d.points, max = Math.max(...pts.map(p => Number(p.p95))), n = pts.length - 1;
    const X = m => l + (W - l - r) * m / n, Y = v => t + (H - t - b) * (1 - Number(v) / max);
    const band = (lo, hi) => pts.map((p, i) => (i ? 'L' : 'M') + X(p.month).toFixed(1) + ' ' + Y(p[hi]).toFixed(1)).join('') + pts.slice().reverse().map(p => 'L' + X(p.month).toFixed(1) + ' ' + Y(p[lo]).toFixed(1)).join('') + 'Z';
    const line = pts.map((p, i) => (i ? 'L' : 'M') + X(p.month).toFixed(1) + ' ' + Y(p.p50).toFixed(1)).join('');
    const xm = X(RK.year * 12), at = pts[RK.year * 12];
    const goal = d.goal != null && Number(d.goal) <= max ? `<line x1="${l}" x2="${W - r}" y1="${Y(d.goal).toFixed(1)}" y2="${Y(d.goal).toFixed(1)}" class="rk-goal"/>` : '';
    return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Cone de patrimônio: faixa dos percentis 5 a 95, faixa central dos percentis 25 a 75 e a mediana, ao longo dos anos"><path d="${band('p5', 'p95')}" class="rk-b1"/><path d="${band('p25', 'p75')}" class="rk-b2"/>${goal}<path d="${line}" class="rk-mline"/>`
      + `<line x1="${xm.toFixed(1)}" x2="${xm.toFixed(1)}" y1="${t}" y2="${H - b}" class="rk-year"/><circle cx="${xm.toFixed(1)}" cy="${Y(at.p95).toFixed(1)}" r="4" class="rk-dot"/><circle cx="${xm.toFixed(1)}" cy="${Y(at.p50).toFixed(1)}" r="5" class="rk-dot m"/><circle cx="${xm.toFixed(1)}" cy="${Y(at.p5).toFixed(1)}" r="4" class="rk-dot"/></svg>`;
  }

  function ringSvg(pct) {
    const R = 52, C = 2 * Math.PI * R, a = Math.max(0, Math.min(100, pct)) / 100 * C;
    return `<svg viewBox="0 0 140 140" role="img" aria-label="${f(pct, 0)}%"><defs><linearGradient id="rkG" x1="0" x2="1"><stop offset="0" stop-color="#55d6ff"/><stop offset="1" stop-color="#ffc83d"/></linearGradient></defs><circle cx="70" cy="70" r="${R}" fill="none" class="rk-trk" stroke-width="9"/>`
      + `<circle cx="70" cy="70" r="${R}" fill="none" stroke="url(#rkG)" stroke-width="9" stroke-linecap="round" stroke-dasharray="${a.toFixed(1)} ${(C - a).toFixed(1)}" transform="rotate(-90 70 70)"/><text x="70" y="78" text-anchor="middle" class="rk-rv">${f(pct, 0)}%</text></svg>`;
  }

  function render() {
    const d = RK.d; if (!d || !d.density || !onPage()) return;
    const n = d.density.counts.length - 1; if (RK.year == null || RK.year > n) RK.year = n;
    const at = d.points[RK.year * 12];
    const sl = $('#rkYear'); sl.max = String(n); sl.min = '0'; sl.value = String(RK.year); $('#rkYearTxt').textContent = 'ano ' + RK.year + ' de ' + n;
    $('#rkNums').innerHTML = `<div><span>No ano ${RK.year}: ruim (1 em 20)</span><b class="num dn">${mil(at.p5)}</b></div><div><span>Mediano</span><b class="num">${mil(at.p50)}</b></div><div><span>Bom (1 em 20)</span><b class="num up">${mil(at.p95)}</b></div>`;
    paint($('#rkCone'), cone(d));
    paint($('#rkLand'), ridges(d));
    const goalPct = d.goal != null && d.goalProbabilityByYearPercent ? Number(d.goalProbabilityByYearPercent[RK.year]) : 100 - Number(d.probabilityBelowInvestedPercent);
    paint($('#rkRing'), ringSvg(goalPct) + `<p class="src">${d.goal != null ? 'chance da meta no ano ' + RK.year : 'chance de terminar acima do que foi investido'}</p>`);
    $('#rkNote').textContent = d.paths.toLocaleString('pt-BR') + ' trajetórias simuladas. Retorno e volatilidade são hipóteses, não previsão.' + (d.goal != null ? ' A meta é ' + mil(d.goal) + '.' : '');
    $('#rkLegend').innerHTML = '<span><i style="background:#ffb340"></i>mediana</span><span><i style="background:rgba(255,179,64,.35)"></i>faixa central (percentis 25 a 75)</span><span><i style="background:rgba(143,168,255,.3)"></i>faixa de 5 a 95</span>' + (d.goal != null ? '<span><i style="background:#55d6ff"></i>meta ' + mil(d.goal) + '</span>' : '');
  }
  document.addEventListener('mw:mc-data', e => { RK.d = e.detail.data; render(); });
  document.addEventListener('input', e => { if (e.target.id === 'rkYear') { RK.year = Number(e.target.value); render(); } });

  /* atalhos de aporte e prazo: preenchem o formulário e projetam de novo, com espera para não passar do limite de projeções por minuto */
  const SET = {aporte: ['mc_monthlyContribution', [500, 1000, 2000]], prazo: ['mc_horizonYears', [10, 15, 20]]};
  function pills() {
    ['aporte', 'prazo'].forEach(k => {
      const el = $('#rk-' + k), inp = document.getElementById(SET[k][0]); if (!el) return;
      const cur = inp ? Number(inp.value) : null;
      el.innerHTML = SET[k][1].map(v => `<button type="button" class="mk-pill" data-rk="${k}:${v}" aria-pressed="${cur === v}">${k === 'aporte' ? 'R$ ' + f(v, 0) : v + ' anos'}</button>`).join('');
    });
  }
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-rk]'); if (b) {
      const [k, v] = b.dataset.rk.split(':'), inp = document.getElementById(SET[k][0]); if (!inp) return;
      inp.value = v; inp.dispatchEvent(new Event('input', {bubbles: true})); pills();
      clearTimeout(RK.timer); RK.timer = setTimeout(() => { const form = $('#mcForm'); if (form && form.requestSubmit) form.requestSubmit(); }, 600);
      return;
    }
    if (e.target.closest('button[data-mcm]')) setTimeout(pills, 0);
  });
  document.addEventListener('submit', () => setTimeout(pills, 0));
  const more = document.getElementById('rkMore');
  if (more) more.addEventListener('toggle', () => requestAnimationFrame(() => Object.values(MC.charts).forEach(c => c.resize())));
  addEventListener('hashchange', () => {
    if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] !== 'montecarlo') return;
    setTimeout(() => { pills(); if (!RK.d && !RK.auto) { RK.auto = true; const form = $('#mcForm'); if (form && form.requestSubmit) form.requestSubmit(); } else render(); }, 500);
  });
  setTimeout(() => { pills(); if (onPage() && !RK.d && !RK.auto) { RK.auto = true; const form = $('#mcForm'); if (form && form.requestSubmit) form.requestSubmit(); } }, 1500);
})();
