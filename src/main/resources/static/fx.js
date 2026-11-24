/* Efeitos de interface (item #45): transição "íris" entre telas, cartões que se inclinam em 3D e revelação ao rolar.
   Regra do projeto (ADR 004): NENHUM número é animado. Este arquivo não escreve texto em elemento nenhum; só classes e variáveis de CSS.
   Desligado com prefers-reduced-motion; a inclinação também fica desligada em telas de toque. Sem global: tudo dentro de uma função. */
(function () {
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  if (reduced.matches) return;
  document.documentElement.classList.add('fx');

  /* 1. transição entre telas: a página que perde o atributo hidden entra com a íris. O roteador troca as páginas de forma síncrona
        (ECharts e Leaflet medem o tamanho na hora); aqui só se acrescenta uma classe de animação à página que entrou. */
  const main = document.getElementById('main');
  new MutationObserver(list => {
    for (const m of list) {
      const el = m.target;
      if (!el.classList || !el.classList.contains('page') || el.hidden) continue;
      el.classList.remove('enter'); void el.offsetWidth; el.classList.add('enter');
      /* só o fim da PRÓPRIA animação da página: o pulso de cotação nova (beat, fd, fu) de um filho também dispara animationend e borbulha */
      const done = e => { if (e.target === el && e.animationName === 'irisIn') { el.classList.remove('enter'); el.removeEventListener('animationend', done); } };
      el.addEventListener('animationend', done);
    }
  }).observe(main, {subtree: true, attributes: true, attributeFilter: ['hidden']});

  /* 2. cartões que se inclinam: delegação de eventos, sem listener por cartão */
  const TILT = '.sig-card, .shortcuts a, .tilt';
  if (matchMedia('(hover: hover) and (pointer: fine)').matches) {
    let cur = null;
    const reset = el => { if (!el) return; el.style.removeProperty('--rx'); el.style.removeProperty('--ry'); el.style.removeProperty('--gx'); el.style.removeProperty('--gy'); el.classList.remove('tilting'); };
    document.addEventListener('pointermove', e => {
      const el = e.target.closest && e.target.closest(TILT);
      if (el !== cur) { reset(cur); cur = el; }
      if (!el) return;
      const r = el.getBoundingClientRect();
      const x = (e.clientX - r.left) / Math.max(1, r.width), y = (e.clientY - r.top) / Math.max(1, r.height);
      el.classList.add('tilting');
      el.style.setProperty('--ry', ((x - 0.5) * 10).toFixed(2) + 'deg');
      el.style.setProperty('--rx', ((0.5 - y) * 8).toFixed(2) + 'deg');
      el.style.setProperty('--gx', (x * 100).toFixed(1) + '%');
      el.style.setProperty('--gy', (y * 100).toFixed(1) + '%');
    }, {passive: true});
    document.addEventListener('pointerleave', () => { reset(cur); cur = null; }, true);
  }

  /* 3. revelação ao rolar: cartões abaixo da dobra entram com subida suave; sem JavaScript ou sem IntersectionObserver, ficam visíveis */
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver(es => {
      for (const e of es) if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
    }, {rootMargin: '0px 0px -8% 0px', threshold: 0.05});
    const scan = () => document.querySelectorAll('.page:not([hidden]) .card:not(.rv)').forEach(c => {
      const below = c.getBoundingClientRect().top > innerHeight * 0.9;
      c.classList.add('rv');
      if (below) io.observe(c); else c.classList.add('in');
    });
    new MutationObserver(scan).observe(main, {subtree: true, attributes: true, attributeFilter: ['hidden']});
    addEventListener('load', scan);
    scan();
  }
})();
