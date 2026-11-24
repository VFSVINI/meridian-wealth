/* Cockpit em 3D (item #40): o histórico REAL do ativo como terreno de candles. x = tempo, y = preço, o volume vira barras no piso, à frente.
   Mesma fonte do gráfico 2D: os candles reais que o cockpit.js já carregou e desenhou (evento mw:ck-data); SMA50 e Bollinger são as mesmas contas do 2D.
   Nada é simulado nem animado: a única animação é a da câmera. Câmera orbital: arrastar gira, Ctrl+roda ou os botões dão zoom, duplo clique restaura,
   setas do teclado giram. Passar o mouse sobre um candle mostra os números reais e atualiza o inspetor. O 2D e a tabela continuam como alternativa.
   Módulo ES: não declara nenhum global; usa o kit3d.js. */
import {THREE, createStage} from '/kit3d.js';

const box = document.getElementById('ck3d');
const canvas = document.getElementById('ck3dCanvas');
const btn = document.getElementById('ck3dBtn');
const chart2d = document.getElementById('chart');
const tip = document.getElementById('ck3dTip');
const note = document.getElementById('ck3dNote');
if (!box || !canvas || !btn) throw new Error('cockpit 3D: elementos ausentes');

let kit = null, active = false, data = null, built = null;
const W = 12, H = 5;                                   /* largura do tempo e altura do preço, em unidades da cena */
const cam = {az: -0.55, el: 0.42, dist: 17, vaz: 0, vel: 0, sway: true, fit: true};
const START = {az: -0.55, el: 0.42, dist: 17};
const target = new THREE.Vector3(0, H * 0.42, 0.9);
const group = new THREE.Group();
let bodies = null, shown = [];                          /* candles visíveis (para o raycast e o tooltip) */
let hoverIdx = -1, hoverQueued = false, px = 0, py = 0;
let dragging = false, lastX = 0, lastY = 0, moved = 0;

const css = () => { const c = getComputedStyle(document.documentElement), k = n => c.getPropertyValue(n).trim(); return {up: k('--up'), down: k('--down'), gold: k('--gold'), info: k('--info'), text: k('--text-2'), line: k('--line-2'), bg: k('--panel')}; };

function label(text, color, size) {
  const c = document.createElement('canvas'), g = c.getContext('2d');
  const fs = 56; g.font = `500 ${fs * 0.5}px IBM Plex Mono, ui-monospace, monospace`;
  c.width = Math.ceil(g.measureText(text).width) + 16; c.height = fs;
  g.font = `500 ${fs * 0.5}px IBM Plex Mono, ui-monospace, monospace`; g.fillStyle = color; g.textBaseline = 'middle'; g.fillText(text, 8, fs / 2);
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace;
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({map: tex, transparent: true, depthWrite: false}));
  sp.scale.set(size * c.width / c.height, size, 1);
  return sp;
}
function disposeGroup() {
  group.traverse(o => {
    if (o.geometry) o.geometry.dispose();
    if (o.material) { if (o.material.map) o.material.map.dispose(); o.material.dispose(); }
    /* os buffers de matriz e de cor das instâncias só são liberados pelo dispose do próprio InstancedMesh (medido: sem isso, 46 buffers vazados em 12 reconstruções) */
    if (o.isInstancedMesh) o.dispose();
  });
  group.clear(); bodies = null; shown = [];
}

function build() {
  /* com o contexto perdido não há o que montar, e esconder o aviso aqui o apagaria: a restauração chama build() de novo */
  if (!kit || !data || kit.lost) return;
  disposeGroup();
  const all = data.bars || [];
  if (!all.length) { box.classList.add('empty3d'); note.textContent = 'Sem dado real para este ativo.'; note.hidden = false; kit.invalidate(); return; }
  note.hidden = true; box.classList.remove('empty3d');
  const last = all[all.length - 1].t, from = last - (data.days || 183) * 86400;
  const bars = all.filter(b => b.t >= from).slice(-400);
  const n = bars.length, dx = W / Math.max(1, n), bw = dx * 0.72;
  const bbBy = new Map((data.bb || []).map(b => [b.t, b])), smaBy = new Map((data.sma || []).map(s => [s.time, s.value]));
  let pmin = Infinity, pmax = -Infinity, vmax = 0;
  for (const b of bars) {
    pmin = Math.min(pmin, b.low); pmax = Math.max(pmax, b.high); vmax = Math.max(vmax, b.volume || 0);
    const bb = bbBy.get(b.t); if (bb) { pmin = Math.min(pmin, bb.l); pmax = Math.max(pmax, bb.u); }
  }
  const span = Math.max(1e-9, pmax - pmin), Y = p => (p - pmin) / span * H, X = i => (i - (n - 1) / 2) * dx;
  const k = css(), up = new THREE.Color(k.up), down = new THREE.Color(k.down);
  const dummy = new THREE.Object3D();

  /* corpos dos candles (x = tempo, y = preço, profundidade fixa) e pavios */
  const bodyMesh = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshStandardMaterial({roughness: 0.42, metalness: 0.15}), n);
  const wickMesh = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshStandardMaterial({roughness: 0.6, metalness: 0}), n);
  const volMesh = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshStandardMaterial({roughness: 0.7, metalness: 0, transparent: true, opacity: 0.8}), n);
  bars.forEach((b, i) => {
    const col = b.close >= b.open ? up : down, lo = Math.min(b.open, b.close), hi = Math.max(b.open, b.close);
    const bh = Math.max(0.03, Y(hi) - Y(lo));
    dummy.position.set(X(i), Y(lo) + bh / 2, 0); dummy.scale.set(bw, bh, 0.55); dummy.rotation.set(0, 0, 0); dummy.updateMatrix(); bodyMesh.setMatrixAt(i, dummy.matrix); bodyMesh.setColorAt(i, col);
    const wh = Math.max(0.02, Y(b.high) - Y(b.low));
    dummy.position.set(X(i), Y(b.low) + wh / 2, 0); dummy.scale.set(bw * 0.14, wh, 0.1); dummy.updateMatrix(); wickMesh.setMatrixAt(i, dummy.matrix); wickMesh.setColorAt(i, col);
    const vd = vmax > 0 ? Math.max(0.02, (b.volume || 0) / vmax * 2.4) : 0.02;   /* volume = profundidade, a partir da frente dos candles */
    dummy.position.set(X(i), 0.04, 0.55 + vd / 2 + 0.2); dummy.scale.set(bw, 0.08, vd); dummy.updateMatrix(); volMesh.setMatrixAt(i, dummy.matrix); volMesh.setColorAt(i, col);
  });
  [bodyMesh, wickMesh, volMesh].forEach(m => { m.instanceMatrix.needsUpdate = true; if (m.instanceColor) m.instanceColor.needsUpdate = true; group.add(m); });
  bodies = bodyMesh; shown = bars;

  /* SMA50 e Bollinger (as mesmas contas do gráfico 2D), mais a faixa entre as bandas */
  const line = (pts, color, opacity = 1) => { const g = new THREE.BufferGeometry().setFromPoints(pts); return new THREE.Line(g, new THREE.LineBasicMaterial({color, transparent: opacity < 1, opacity})); };
  const sma = [], bu = [], bl = [], bm = [];
  bars.forEach((b, i) => {
    const s = smaBy.get(b.t); if (s != null) sma.push(new THREE.Vector3(X(i), Y(s), 0.4));
    const bb = bbBy.get(b.t); if (bb) { bu.push(new THREE.Vector3(X(i), Y(bb.u), 0.4)); bl.push(new THREE.Vector3(X(i), Y(bb.l), 0.4)); bm.push(new THREE.Vector3(X(i), Y(bb.m), 0.4)); }
  });
  if (sma.length > 1) group.add(line(sma, new THREE.Color(k.gold)));
  if (bu.length > 1) {
    group.add(line(bu, new THREE.Color(k.info), 0.9)); group.add(line(bl, new THREE.Color(k.info), 0.9)); group.add(line(bm, new THREE.Color(k.info), 0.45));
    const pos = [], idx = [];
    bu.forEach((u, i) => { pos.push(u.x, u.y, u.z, bl[i].x, bl[i].y, bl[i].z); if (i > 0) { const a = 2 * (i - 1); idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); } });
    const bg = new THREE.BufferGeometry(); bg.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); bg.setIndex(idx);
    group.add(new THREE.Mesh(bg, new THREE.MeshBasicMaterial({color: new THREE.Color(k.info), transparent: true, opacity: 0.1, side: THREE.DoubleSide, depthWrite: false})));
  }

  /* piso e escalas: preços à esquerda, datas à frente */
  const grid = new THREE.GridHelper(W + 2, 14, new THREE.Color(k.line), new THREE.Color(k.line));
  grid.position.set(0, -0.001, 1.6); grid.scale.z = 0.62; grid.material.transparent = true; grid.material.opacity = 0.35; group.add(grid);
  const dec = pmax < 10 ? 4 : 2;
  for (let t = 0; t <= 4; t++) {
    const p = pmin + span * t / 4, sp = label(p.toLocaleString('pt-BR', {minimumFractionDigits: dec, maximumFractionDigits: dec}), k.text, 0.42);
    sp.position.set(-W / 2 - 0.15 - sp.scale.x / 2, Y(p), 0); group.add(sp);
  }
  for (let t = 0; t <= 3; t++) {
    const i = Math.round((n - 1) * t / 3), b = bars[i];
    const sp = label(data.when(b.t, {day: '2-digit', month: '2-digit', year: '2-digit'}), k.text, 0.42);
    sp.position.set(X(i), -0.05, 3.9); group.add(sp);
  }
  built = {n, pmin, pmax, symbol: data.symbol, range: data.range};
  canvas.setAttribute('aria-label', `Terreno 3D do histórico real de ${data.symbol}: ${n} barras, de ${data.when(bars[0].t, {day: '2-digit', month: '2-digit', year: 'numeric'})} a ${data.when(bars[n - 1].t, {day: '2-digit', month: '2-digit', year: 'numeric'})}, mínima ${pmin.toLocaleString('pt-BR')} e máxima ${pmax.toLocaleString('pt-BR')}. Para ler os preços use a tabela.`);
  kit.invalidate();
}

function place() {
  const ce = Math.cos(cam.el);
  kit.camera.position.set(target.x + cam.dist * ce * Math.sin(cam.az), target.y + cam.dist * Math.sin(cam.el), target.z + cam.dist * ce * Math.cos(cam.az));
  kit.camera.lookAt(target);
}
/* devolve true enquanto a câmera se move (balanço inicial, inércia): o kit mantém o laço; senão desenha só sob demanda */
function frame(dt) {
  /* distância que cabe o terreno inteiro na largura do palco (tela estreita afasta a câmera) enquanto o usuário não deu zoom */
  if (cam.fit) cam.dist = Math.max(17, 15.5 / (2 * Math.tan(kit.camera.fov * Math.PI / 360) * Math.max(0.5, kit.camera.aspect)));
  if (!dragging) {
    if (Math.abs(cam.vaz) > 0.00001 || Math.abs(cam.vel) > 0.00001) { cam.az += cam.vaz * dt; cam.el = Math.max(0.05, Math.min(1.45, cam.el + cam.vel * dt)); cam.vaz *= 0.93; cam.vel *= 0.93; }
    else if (cam.sway && !kit.reduced.matches) cam.az += 0.00016 * dt;       /* balanço lento até o primeiro toque */
  }
  place();
  return !!(cam.sway && !kit.reduced.matches) || Math.abs(cam.vaz) > 0.00001 || Math.abs(cam.vel) > 0.00001 || dragging;
}

function ensure() {
  if (kit) return true;
  kit = createStage({
    stage: box, canvas, fov: 34, exposure: 1.0, alpha: true,
    onFail: () => { note.textContent = 'Seu navegador não tem WebGL: use o gráfico 2D e a tabela.'; note.hidden = false; },
    onLost: () => { note.textContent = 'O navegador pausou o 3D. Volte para a aba ou alterne o gráfico para retomar.'; note.hidden = false; },
    onRestore: () => { note.hidden = true; build(); },
    onFrame: frame
  });
  if (!kit) return false;
  kit.scene.add(group);
  kit.scene.add(new THREE.HemisphereLight(0xffffff, 0x445066, 0.9));
  const key = new THREE.DirectionalLight(0xffffff, 1.6); key.position.set(-6, 10, 8); kit.scene.add(key);
  const rim = new THREE.DirectionalLight(0xbcd4ff, 0.6); rim.position.set(8, 4, -6); kit.scene.add(rim);
  const ray = new THREE.Raycaster(), ndc = new THREE.Vector2();

  const showTip = i => {
    const b = shown[i]; if (!b) { tip.hidden = true; return; }
    const p = b.close < 10 ? 4 : 2, fmt = v => v.toLocaleString('pt-BR', {minimumFractionDigits: p, maximumFractionDigits: p});
    tip.textContent = '';
    const head = document.createElement('div'); head.className = 'src'; head.textContent = data.when(b.t, {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'});
    const rows = document.createElement('div'); rows.className = 'num';
    rows.textContent = `Abertura    ${fmt(b.open)}\nMáxima      ${fmt(b.high)}\nMínima      ${fmt(b.low)}\nFechamento  ${fmt(b.close)}\nVolume      ${b.volume ? b.volume.toLocaleString('pt-BR') : 'não informado'}`;
    tip.append(head, rows); tip.hidden = false;
    const r = box.getBoundingClientRect(); tip.style.left = Math.min(r.width - tip.offsetWidth - 8, Math.max(8, px - r.left + 14)) + 'px'; tip.style.top = Math.max(8, py - r.top - tip.offsetHeight - 14) + 'px';
  };
  const pick = () => {
    hoverQueued = false;
    if (!bodies || dragging) return;
    const r = canvas.getBoundingClientRect();
    ndc.set(((px - r.left) / r.width) * 2 - 1, -((py - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, kit.camera);
    const hit = ray.intersectObject(bodies, false)[0], i = hit ? hit.instanceId : -1;
    if (i !== hoverIdx) { hoverIdx = i; document.dispatchEvent(new CustomEvent('mw:ck-hover', {detail: {t: i >= 0 ? shown[i].t : null}})); }
    showTip(i);
  };
  const queue = () => { if (!hoverQueued) { hoverQueued = true; requestAnimationFrame(pick); } };

  canvas.addEventListener('pointerdown', e => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    dragging = true; moved = 0; lastX = e.clientX; lastY = e.clientY; cam.sway = false; tip.hidden = true;
    try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* ponteiro já inativo */ }
    box.classList.add('grabbing'); kit.invalidate();
  });
  canvas.addEventListener('pointermove', e => {
    px = e.clientX; py = e.clientY;
    if (!dragging) { queue(); return; }
    const dx = e.clientX - lastX, dy = e.clientY - lastY; moved += Math.abs(dx) + Math.abs(dy);
    cam.az -= dx * 0.008; cam.el = Math.max(0.05, Math.min(1.45, cam.el + dy * 0.006));
    cam.vaz = -dx * 0.008 / 16; cam.vel = dy * 0.006 / 16; lastX = e.clientX; lastY = e.clientY; kit.invalidate();
  });
  const release = e => {
    if (!dragging) return;
    dragging = false; box.classList.remove('grabbing');
    try { if (canvas.hasPointerCapture(e.pointerId)) canvas.releasePointerCapture(e.pointerId); } catch (err) { /* já solto */ }
    if (kit.reduced.matches) { cam.vaz = 0; cam.vel = 0; }
    kit.invalidate();
  };
  canvas.addEventListener('pointerup', release); canvas.addEventListener('pointercancel', release); canvas.addEventListener('lostpointercapture', release);
  canvas.addEventListener('pointerleave', () => { hoverIdx = -1; tip.hidden = true; document.dispatchEvent(new CustomEvent('mw:ck-hover', {detail: {t: null}})); });
  canvas.addEventListener('dblclick', () => reset());
  /* a roda só dá zoom com Ctrl (ou com o gráfico focado): senão ela rolaria a página e o 3D a "roubaria" */
  canvas.addEventListener('wheel', e => { if (!(e.ctrlKey || e.metaKey || document.activeElement === canvas)) return; e.preventDefault(); zoom(e.deltaY > 0 ? 1.1 : 0.9); }, {passive: false});
  canvas.addEventListener('keydown', e => {
    const step = {ArrowLeft: [0.12, 0], ArrowRight: [-0.12, 0], ArrowUp: [0, -0.08], ArrowDown: [0, 0.08]}[e.key];
    if (step) { e.preventDefault(); cam.sway = false; cam.az += step[0]; cam.el = Math.max(0.05, Math.min(1.45, cam.el + step[1])); kit.invalidate(); }
    else if (e.key === '+' || e.key === '=') { e.preventDefault(); zoom(0.9); }
    else if (e.key === '-') { e.preventDefault(); zoom(1.1); }
    else if (e.key === '0' || e.key === 'Home') { e.preventDefault(); reset(); }
  });
  return true;
}
function zoom(f) { cam.sway = false; cam.fit = false; cam.dist = Math.max(7, Math.min(40, cam.dist * f)); kit.invalidate(); }
function reset() { Object.assign(cam, START, {vaz: 0, vel: 0, fit: true}); kit.invalidate(); }

function setActive(on) {
  active = on; btn.setAttribute('aria-pressed', String(on));
  box.hidden = !on; chart2d.hidden = on;
  const ex = document.getElementById('ckExport'); if (ex) { ex.disabled = on; ex.title = on ? 'A exportação de imagem vale para o gráfico 2D' : ''; }
  if (on) { if (!ensure()) return; document.dispatchEvent(new Event('mw:ck-need')); build(); kit.wake(); }
}
btn.addEventListener('click', () => setActive(!active));
document.getElementById('ck3dIn').addEventListener('click', () => kit && zoom(0.85));
document.getElementById('ck3dOut').addEventListener('click', () => kit && zoom(1.18));
document.getElementById('ck3dReset').addEventListener('click', () => kit && reset());
document.addEventListener('mw:ck-data', e => { data = e.detail; if (active && kit) build(); });
new MutationObserver(() => { if (active && kit) build(); }).observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
