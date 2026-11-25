/* Céu do fundo (item #47, ADR 033): estrelas em três profundidades que derivam, reagem ao ponteiro (paralaxe e um leve afastamento perto do cursor),
   nebulosas que orbitam devagar e o "salto de velocidade da luz" entre páginas (evento mw:warp: as estrelas viram riscos que partem do centro).
   É só ambiente: não desenha nem escreve nenhum dado (ADR 004). Sem global (tudo numa função), um canvas fixo, 30 quadros por segundo no máximo,
   pausado com a aba oculta; com prefers-reduced-motion fica uma imagem parada (sem deriva, sem reação, sem salto). O Ivory não tem céu. */
(function () {
  const cv = document.getElementById('stars');
  if (!cv || !cv.getContext) return;
  const g = cv.getContext('2d');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const ivory = () => document.documentElement.dataset.theme === 'ivory';
  let w = 0, h = 0, stars = [], raf = 0, last = 0, t = 0, sy = 0, warp = 0, warpStart = 0;
  const ptr = {x: 0, y: 0, tx: 0, ty: 0, px: -999, py: -999};        /* ponteiro suavizado (-1 a 1) e posição em pixels */
  const WARP_MS = 900;
  const NEB = [[.12, .3, .34, '85,214,255', .13, 90000, 0], [.88, .72, .3, '255,160,40', .10, 120000, 2], [.6, .1, .26, '169,139,255', .08, 150000, 4]];

  /* gerador pseudoaleatório com semente: as estrelas ficam nos mesmos lugares ao redimensionar */
  function rng(seed) { return () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; }; }

  function build() {
    w = innerWidth; h = innerHeight;
    cv.width = w; cv.height = h;
    const r = rng(20261124), n = Math.min(300, Math.round(w * h / 6500));
    stars = [];
    for (let i = 0; i < n; i++) {
      const depth = r() < .55 ? 0 : r() < .65 ? 1 : 2;           /* 0 = longe e fraca, 2 = perto e viva */
      const a = (r() < .5 ? -1 : 1) * (0.55 + r() * 0.9);        /* inclinação do risco */
      stars.push({x: r() * w, y: r() * h, len: 2 + r() * (4 + depth * 5), ang: a, al: .2 + r() * .25 + depth * .22, depth, gold: r() < .1, ph: r() * 6.28});
    }
  }

  function nebulae() {
    for (const [nx, ny, nr, rgb, al, per, ph] of NEB) {
      const a = reduced.matches ? ph : t / per * 6.283 + ph, R = Math.max(w, h) * nr;
      const x = (nx + Math.cos(a) * .06 + ptr.x * .012) * w, y = (ny + Math.sin(a * 1.3) * .06 + ptr.y * .012) * h;
      const gr = g.createRadialGradient(x, y, 0, x, y, R);
      gr.addColorStop(0, 'rgba(' + rgb + ',' + al + ')'); gr.addColorStop(1, 'rgba(' + rgb + ',0)');
      g.fillStyle = gr; g.fillRect(x - R, y - R, 2 * R, 2 * R);
    }
  }

  function draw() {
    g.clearRect(0, 0, w, h);
    if (ivory()) return;
    nebulae();
    g.lineCap = 'round';
    const still = reduced.matches, cx = w / 2, cy = h / 2;
    for (const s of stars) {
      const d = s.depth, par = still ? 0 : sy * (.02 + d * .035);
      const drift = still ? 0 : t * (5 + d * 11) / 1000;           /* px por segundo: devagar, mas se vê andando */
      let x = s.x + (still ? 0 : ptr.x * (6 + d * 16)), y = (((s.y - par - drift + (still ? 0 : ptr.y * (6 + d * 16))) % h) + h) % h;
      /* perto do cursor as estrelas se afastam um pouco, como poeira empurrada */
      if (!still) {
        const dx = x - ptr.px, dy = y - ptr.py, dd = dx * dx + dy * dy;
        if (dd < 14400 && dd > 1) { const k = (1 - Math.sqrt(dd) / 120) * (10 + d * 14), m = Math.sqrt(dd); x += dx / m * k; y += dy / m * k; }
      }
      const tw = still ? 1 : .72 + .28 * Math.sin(t / 1800 + s.ph);
      let al = s.al * tw, len = s.len, ang = s.ang, lw = .8 + d * .5;
      if (warp > 0) {
        /* salto: o risco aponta para fora do centro e se estica, mais para as estrelas de perto */
        const vx = x - cx, vy = y - cy, vm = Math.hypot(vx, vy) || 1, e = warp * warp;
        ang = Math.atan2(vy, vx); len = s.len + e * (60 + d * 150) * (vm / Math.max(cx, cy) + .35);
        x += vx / vm * e * (40 + d * 120); y += vy / vm * e * (40 + d * 120);
        al = Math.min(1, al + warp * .6); lw = (.8 + d * .5) * (1 + warp * 1.2);
      }
      g.strokeStyle = s.gold && warp < .4 ? 'rgba(255,193,90,' + al + ')' : warp > 0 ? 'rgba(210,228,255,' + al + ')' : 'rgba(232,236,255,' + al + ')';
      g.lineWidth = lw;
      g.beginPath(); g.moveTo(x, y); g.lineTo(x + Math.cos(ang) * len, y + Math.sin(ang) * len); g.stroke();
    }
    if (warp > 0) {                                                  /* clarão suave no centro durante o salto */
      const gr = g.createRadialGradient(cx, cy, 0, cx, cy, Math.max(w, h) * .5);
      gr.addColorStop(0, 'rgba(190,220,255,' + (warp * .16) + ')'); gr.addColorStop(1, 'rgba(190,220,255,0)');
      g.fillStyle = gr; g.fillRect(0, 0, w, h);
    }
  }

  function loop(now) {
    raf = 0;
    if (document.hidden || reduced.matches || ivory()) return;
    const dt = Math.min(100, now - last);
    if (dt >= 33) {
      last = now; t += dt;
      ptr.x += (ptr.tx - ptr.x) * .08; ptr.y += (ptr.ty - ptr.y) * .08;
      if (warp > 0) { const p = (now - warpStart) / WARP_MS; warp = p >= 1 ? 0 : Math.sin(Math.PI * Math.min(1, p) * 0.9 + 0.2) * (1 - p) * 1.25; if (warp < .01) warp = 0; }
      draw();
    }
    raf = requestAnimationFrame(loop);
  }
  function wake() { cancelAnimationFrame(raf); raf = 0; draw(); if (!document.hidden && !reduced.matches && !ivory()) { last = performance.now(); raf = requestAnimationFrame(loop); } }

  build(); wake();
  addEventListener('resize', () => { build(); wake(); });
  addEventListener('scroll', () => { sy = scrollY; if (!raf) draw(); }, {passive: true});
  addEventListener('pointermove', e => { ptr.tx = e.clientX / w * 2 - 1; ptr.ty = e.clientY / h * 2 - 1; ptr.px = e.clientX; ptr.py = e.clientY; }, {passive: true});
  /* o mouse saiu da página: pointerout no document sem relatedTarget (o pointerleave não chega à window) */
  document.addEventListener('pointerout', e => { if (!e.relatedTarget) { ptr.tx = 0; ptr.ty = 0; ptr.px = -999; ptr.py = -999; } });
  /* a troca de página pede o salto: o roteador dispara mw:warp */
  document.addEventListener('mw:warp', () => { if (reduced.matches || ivory() || document.hidden) return; warpStart = performance.now(); warp = .01; if (!raf) wake(); });
  document.addEventListener('visibilitychange', wake);
  reduced.addEventListener('change', wake);
  new MutationObserver(wake).observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
})();
