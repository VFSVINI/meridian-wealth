/* Academia (item #49): o curso em módulos do protótipo. Conteúdo em /data/academia.json (9 módulos, 26 capítulos); cada capítulo tem ideia central, diagrama, exemplo,
   "por que importa" e mini-quiz. O progresso (capítulos concluídos e respostas) fica SÓ neste navegador (localStorage), sem conta e sem enviar nada ao servidor.
   Os números dos exemplos são didáticos e rotulados como hipotéticos; nenhum dado de mercado é inventado aqui. Rotas: #/academia, #/academia/m3, #/academia/m3/m3c1.
   Depende de app.js ($, esc, f). Sem global novo. */
(function () {
  const KEY = 'mw-academia';
  const ICONS = {
    book: 'M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3zM5 17a3 3 0 0 1 3-3h11', trend: 'M3 17l6-6 4 4 8-9M15 6h6v6', coin: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM14.5 9.5c-.6-.8-1.5-1-2.5-1-1.3 0-2.3.6-2.3 1.7 0 2.4 5 1.2 5 3.6 0 1.1-1 1.7-2.4 1.7-1.1 0-2-.3-2.6-1.1M12 6.5v11',
    building: 'M5 21V5a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v16M15 10h3a1 1 0 0 1 1 1v10M3 21h18M8 8h4M8 12h4M8 16h4', eye: 'M2 12c3-6 7-6 10 0s7 6 10 0M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z',
    scale: 'M12 3v18M5 7h14M5 7l-3 7a3 3 0 0 0 6 0zM19 7l-3 7a3 3 0 0 0 6 0z', chart: 'M4 19V5M4 19h16M8 15l3-4 3 2 4-6', shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6zM9 12l2 2 4-4',
    globe: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18'
  };
  const AC = {data: null, loading: false, state: {done: {}, ans: {}}};

  function load() { try { const s = JSON.parse(localStorage.getItem(KEY) || 'null'); if (s && s.done && s.ans) AC.state = s; } catch (e) { /* navegador sem armazenamento: o curso funciona sem salvar */ } }
  function save() { try { localStorage.setItem(KEY, JSON.stringify(AC.state)); } catch (e) { /* sem armazenamento */ } }
  const page = () => document.querySelector('[data-page="academia"]');
  const onPage = () => { const p = page(); return !!p && !p.hidden; };
  const mods = () => AC.data.modulos;
  const chapters = () => mods().flatMap(m => m.capitulos.map(c => ({m, c})));
  const done = id => !!AC.state.done[id];
  const parts = () => { const h = (location.hash.match(/^#\/academia(?:\/([a-z0-9]+))?(?:\/([a-z0-9]+))?/) || []); return {m: h[1] || null, c: h[2] || null}; };
  const num = (i) => String(i + 1).padStart(2, '0');
  const icon = n => `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[n] || ICONS.book}"/></svg>`;
  const pt = n => String(n).replace('.', ',');

  /* ordem das opções do quiz: embaralhada de forma determinística por capítulo e pergunta (a resposta certa não fica sempre na primeira); o valor do botão segue sendo o índice original */
  function order(n, seed) {
    let h = 0;
    for (const ch of seed) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    const a = [...Array(n).keys()];
    for (let i = n - 1; i > 0; i--) { h = (h * 1664525 + 1013904223) >>> 0; const j = h % (i + 1); [a[i], a[j]] = [a[j], a[i]]; }
    return a;
  }

  /* ---------- diagramas (HTML e CSS acessíveis; só compound, candle e scatter são SVG) ---------- */
  function diagram(d) {
    const title = `<p class="ac-dtitle">${esc(d.titulo)}</p>`;
    switch (d.tipo) {
      case 'flow': return title + `<ol class="ac-flow">${d.passos.map(p => `<li>${esc(p)}</li>`).join('')}</ol>`;
      case 'cols': return title + `<div class="ac-cols">${d.colunas.map(c => `<div><b>${esc(c.n)}</b><p>${esc(c.t)}</p></div>`).join('')}</div>`;
      case 'bars': {
        const max = Math.max(...d.barras.map(b => b.v));
        return title + `<div class="ac-bars" role="list">${d.barras.map(b => `<div class="ac-bar" role="listitem"><span>${esc(b.n)}</span><i style="width:${(b.v / max * 100).toFixed(1)}%"></i><b class="num">${esc(pt(b.v))}</b></div>`).join('')}</div><p class="ac-unit">${esc(d.unidade || '')}</p>`;
      }
      case 'split': {
        const tot = d.segmentos.reduce((s, x) => s + x.v, 0);
        return title + `<div class="ac-split" role="list">${d.segmentos.map((s, i) => `<div class="ac-seg s${i % 3}" role="listitem" style="flex:${s.v / tot}">${esc(s.n)}</div>`).join('')}</div>`;
      }
      case 'scale': {
        const pos = v => ((v - d.min) / (d.max - d.min) * 100).toFixed(1);
        return title + `<div class="ac-scale"><div class="ac-zones">${d.zonas.map(z => `<span style="left:${pos(z.de)}%;width:${(pos(z.ate) - pos(z.de)).toFixed(1)}%">${esc(z.n)}</span>`).join('')}</div>`
          + `<i class="ac-mark" style="left:${pos(d.marca)}%"></i><div class="ac-ticks"><span>${esc(pt(d.min))}</span><span class="ac-markl" style="left:${pos(d.marca)}%">${esc(d.rotuloMarca)}</span><span>${esc(pt(d.max))}</span></div></div>`;
      }
      case 'scatter': {
        const W = 520, H = 260, P = 34, X = r => P + r / 100 * (W - P - 20), Y = g => H - P - g / 100 * (H - P - 16);
        return title + `<svg class="ac-svg" viewBox="0 0 ${W} ${H}" role="img" aria-label="${esc(d.titulo + ': ' + d.pontos.map(p => p.n).join(', ') + '; quanto mais à direita, maior o risco, e quanto mais acima, maior o retorno esperado')}"><line x1="${P}" y1="${H - P}" x2="${W - 14}" y2="${H - P}" class="ax"/><line x1="${P}" y1="${H - P}" x2="${P}" y2="12" class="ax"/>`
          + `<text x="${W - 14}" y="${H - 10}" text-anchor="end">${esc(d.x)} →</text><text x="12" y="20">${esc(d.y)} ↑</text>`
          + d.pontos.map(p => `<circle cx="${X(p.r).toFixed(1)}" cy="${Y(p.g).toFixed(1)}" r="7" class="pt"/><text x="${(X(p.r) + 11).toFixed(1)}" y="${(Y(p.g) + 4).toFixed(1)}">${esc(p.n)}</text>`).join('') + '</svg>';
      }
      case 'candle': {
        return title + `<svg class="ac-svg" viewBox="0 0 360 240" role="img" aria-label="Candle de alta: o corpo vai da abertura, embaixo, ao fechamento, em cima; o pavio de cima chega ao máximo e o de baixo ao mínimo"><line x1="150" y1="22" x2="150" y2="218" class="wick"/><rect x="130" y="62" width="40" height="110" rx="3" class="body"/>`
          + [['Máximo', 22], ['Fechamento', 62], ['Abertura', 172], ['Mínimo', 218]].map(([n, y]) => `<line x1="176" y1="${y}" x2="214" y2="${y}" class="ax"/><text x="220" y="${y + 4}">${n}</text>`).join('') + '</svg>';
      }
      case 'compound': {
        const W = 520, H = 250, P = 38, yrs = d.anos, simples = [], comp = [];
        for (let a = 0; a <= yrs; a++) { simples.push(d.principal * (1 + d.taxa * a)); comp.push(d.principal * Math.pow(1 + d.taxa, a)); }
        const max = comp[yrs], X = a => P + a / yrs * (W - P - 16), Y = v => H - 26 - (v - d.principal * 0.8) / (max - d.principal * 0.8) * (H - 50);
        const path = arr => arr.map((v, a) => (a ? 'L' : 'M') + X(a).toFixed(1) + ' ' + Y(v).toFixed(1)).join('');
        return title + `<svg class="ac-svg" viewBox="0 0 ${W} ${H}" role="img" aria-label="Linhas de R$ ${f(d.principal, 0)} a ${f(d.taxa * 100, 0)}% ao ano por ${yrs} anos: juros simples chegam a R$ ${f(simples[yrs])} e juros compostos a R$ ${f(comp[yrs])}"><line x1="${P}" y1="${H - 26}" x2="${W - 10}" y2="${H - 26}" class="ax"/>`
          + `<path d="${path(simples)}" class="l1"/><path d="${path(comp)}" class="l2"/><text x="${X(yrs) - 6}" y="${Y(simples[yrs]) - 8}" text-anchor="end">simples: R$ ${f(simples[yrs])}</text><text x="${X(yrs) - 6}" y="${Y(comp[yrs]) - 8}" text-anchor="end" class="t2">compostos: R$ ${f(comp[yrs])}</text>`
          + `<text x="${P}" y="${H - 8}">0</text><text x="${W - 12}" y="${H - 8}" text-anchor="end">${yrs} anos</text></svg>`;
      }
      default: return '';
    }
  }

  /* ---------- telas ---------- */
  function home() {
    const total = chapters().length, ok = chapters().filter(x => done(x.c.id)).length, pctDone = total ? Math.round(ok / total * 100) : 0;
    $('#acGrid').innerHTML = mods().map((m, i) => {
      const d = m.capitulos.filter(c => done(c.id)).length;
      return `<a class="ac-card" href="#/academia/${m.id}"><span class="ac-n">${num(i)}</span><span class="ac-ic" aria-hidden="true">${icon(m.icone)}</span><h2>${esc(m.titulo)}</h2><p>${esc(m.resumo)}</p><small>${d} de ${m.capitulos.length} capítulos${d === m.capitulos.length ? ' · concluído' : ''}</small></a>`;
    }).join('') + `<aside class="ac-prog"><p class="ac-pk">Seu progresso</p><b class="num">${pctDone}%</b><p>${ok} de ${total} capítulos concluídos. O progresso fica salvo neste navegador.</p></aside>`;
  }
  function module(id) {
    const i = mods().findIndex(m => m.id === id), m = mods()[i];
    if (!m) return false;
    const d = m.capitulos.filter(c => done(c.id)).length, next = m.capitulos.find(c => !done(c.id)) || m.capitulos[0];
    $('#acModule').innerHTML = `<nav class="ac-crumb" aria-label="Você está em"><a href="#/academia">Academia</a><span aria-hidden="true">›</span><span>${esc(m.titulo)}</span></nav>`
      + `<div class="ph"><p class="eyebrow">Módulo ${num(i)}</p><h1>${esc(m.titulo)}</h1><p class="lede">${esc(m.resumo)} ${d} de ${m.capitulos.length} capítulos concluídos.</p></div>`
      + `<ol class="ac-chaps">${m.capitulos.map((c, k) => `<li><a class="ac-chap ${done(c.id) ? 'ok' : ''}" href="#/academia/${m.id}/${c.id}"><span class="ac-ck" aria-hidden="true">${done(c.id) ? '✓' : k + 1}</span><span class="ac-ct"><b>${esc(c.titulo)}</b><small>${c.minutos} min de leitura · ${done(c.id) ? 'concluído' : 'mini-quiz no fim'}</small></span></a></li>`).join('')}</ol>`
      + `<a class="cs-btn ac-go" href="#/academia/${m.id}/${next.id}">${d ? 'Continuar' : 'Começar o módulo'}</a>`;
    return true;
  }
  function chapter(mid, cid) {
    const mi = mods().findIndex(m => m.id === mid), m = mods()[mi], ci = m ? m.capitulos.findIndex(c => c.id === cid) : -1;
    if (!m || ci < 0) return false;
    const c = m.capitulos[ci], prev = m.capitulos[ci - 1], next = m.capitulos[ci + 1], ans = AC.state.ans[c.id] || {};
    const allOk = c.quiz.every((q, k) => ans[k] === q.c);
    $('#acChapter').innerHTML = `<nav class="ac-crumb" aria-label="Você está em"><a href="#/academia">Academia</a><span aria-hidden="true">›</span><a href="#/academia/${m.id}">${esc(m.titulo)}</a><span aria-hidden="true">›</span><span>Capítulo ${ci + 1}</span></nav>`
      + `<div class="ph"><p class="eyebrow">Módulo ${num(mi)} · ${c.minutos} min de leitura</p><h1>${esc(c.titulo)}</h1></div>`
      + `<div class="ac-body"><div class="ac-text">${c.blocos.map(b => `<section><h2>${esc(b.t)}</h2><p>${esc(b.p)}</p></section>`).join('')}`
      + `<aside class="ac-ex"><b>${esc(c.exemplo.titulo)}</b><p>${esc(c.exemplo.texto)}</p></aside><aside class="ac-why"><b>Por que importa para o seu dinheiro</b><p>${esc(c.importa)}</p></aside></div>`
      + `<figure class="ac-fig">${diagram(c.diagrama)}</figure></div>`
      + `<section class="ac-quiz" aria-labelledby="acQ"><h2 id="acQ">Mini-quiz</h2>${c.quiz.map((q, k) => {
        const got = ans[k];
        return `<fieldset data-q="${k}"><legend>${esc(q.q)}</legend>${order(q.o.length, c.id + ':' + k).map(j => `<label class="ac-opt ${got === j ? (j === q.c ? 'right' : 'wrong') : ''}"><input type="radio" name="q${k}" value="${j}" ${got === j ? 'checked' : ''}>${esc(q.o[j])}</label>`).join('')}`
          + (got === undefined ? '' : `<p class="ac-fb ${got === q.c ? 'right' : 'wrong'}" role="status"><b>${got === q.c ? 'Isso mesmo.' : 'Ainda não.'}</b> ${got === q.c ? esc(q.e) : 'Releia o capítulo e tente outra opção.'}</p>`) + '</fieldset>';
      }).join('')}${allOk ? '<p class="ac-done" role="status">Capítulo concluído. Seu progresso foi salvo neste navegador.</p>' : ''}</section>`
      + `<nav class="ac-pn" aria-label="Capítulos">${prev ? `<a href="#/academia/${m.id}/${prev.id}">← ${esc(prev.titulo)}</a>` : `<a href="#/academia/${m.id}">← ${esc(m.titulo)}</a>`}${next ? `<a href="#/academia/${m.id}/${next.id}">${esc(next.titulo)} →</a>` : `<a href="#/academia">Voltar aos módulos →</a>`}</nav>`
      + `<p class="src">${esc(AC.data.aviso)}</p>`;
    return true;
  }

  function render(inPlace) {
    if (!onPage() || !AC.data) return;
    const {m, c} = parts();
    let view = 'Home';
    if (m && c && chapter(m, c)) view = 'Chapter';
    else if (m && module(m)) view = 'Module';
    else home();
    ['Home', 'Module', 'Chapter'].forEach(v => { $('#ac' + v).hidden = v !== view; });
    document.title = (view === 'Home' ? 'Academia' : $('#ac' + view + ' h1').textContent) + ' · MeridianWealth';
    if (view !== 'Home' && !inPlace) window.scrollTo(0, 0);   /* só a troca de módulo ou capítulo vai ao topo; responder o quiz redesenha no lugar */
  }
  async function boot() {
    if (AC.data || AC.loading) return;
    AC.loading = true;
    try {
      const r = await fetch('/data/academia.json');
      if (!r.ok) throw new Error('academia');
      AC.data = await r.json(); load();
    } catch (e) { $('#acGrid').innerHTML = '<p class="mute">Não foi possível carregar o curso agora. Tente de novo em instantes.</p>'; }
    finally { AC.loading = false; }
    render();
  }
  document.addEventListener('change', e => {
    const r = e.target.closest('.ac-quiz input[type=radio]');
    if (!r) return;
    const {c} = parts(), q = Number(r.closest('fieldset').dataset.q), chap = chapters().find(x => x.c.id === c);
    if (!chap) return;
    (AC.state.ans[c] = AC.state.ans[c] || {})[q] = Number(r.value);
    if (chap.c.quiz.every((qq, k) => AC.state.ans[c][k] === qq.c)) AC.state.done[c] = true;
    save();
    const y = window.scrollY, name = r.name, value = r.value;
    render(true);
    window.scrollTo(0, y);
    const again = document.querySelector('.ac-quiz input[name="' + name + '"][value="' + value + '"]');
    if (again) again.focus({preventScroll: true});
  });
  addEventListener('hashchange', () => { if ((location.hash.match(/^#\/([a-z]+)/) || [])[1] === 'academia') boot().then(render); });
  new MutationObserver(() => { if (onPage()) boot(); }).observe(document.getElementById('main'), {subtree: true, attributes: true, attributeFilter: ['hidden']});
  if (onPage()) boot();
})();
