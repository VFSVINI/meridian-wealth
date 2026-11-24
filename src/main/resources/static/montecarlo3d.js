/* Monte Carlo em 3D (item #44): o cone patrimonial como SUPERFÍCIE. x = tempo, y = patrimônio, z = percentil (5, 25, 50, 75 e 95). As cinco curvas são os
   percentis exatos que a simulação devolveu (os mesmos do gráfico 2D); a superfície só liga as curvas. O histograma do patrimônio final é uma parede no fim
   do cone, na MESMA escala de patrimônio, e a meta, quando existe, é um plano. É sempre uma PROJEÇÃO (rótulo fixo na tela): não é cotação nem promessa de
   retorno. Dado: o resultado que o montecarlo.js já recebeu da API (evento mw:mc-data). Nada é animado como dado; só a câmera. Usa o kit3d.js. Módulo ES. */
import {THREE, createStage, createOrbit, makeLabel, disposeGroup} from '/kit3d.js';

const box = document.getElementById('mc3d');
const canvas = document.getElementById('mc3dCanvas');
const btn = document.getElementById('mc3dBtn');
const flat = document.getElementById('mcCone');
const tip = document.getElementById('mc3dTip');
const note = document.getElementById('mc3dNote');
if (!box || !canvas || !btn) throw new Error('Monte Carlo 3D: elementos ausentes');

const W = 12, H = 5, Z = [-2.6, -1.3, 0, 1.3, 2.6], NAMES = ['P5', 'P25', 'P50', 'P75', 'P95'];
const BRL = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL', maximumFractionDigits: 0});
const short = v => v >= 1e6 ? (v / 1e6).toLocaleString('pt-BR', {maximumFractionDigits: 1}) + ' mi' : v >= 1e3 ? (v / 1e3).toLocaleString('pt-BR', {maximumFractionDigits: 0}) + ' mil' : String(Math.round(v));
const target = new THREE.Vector3(1.2, H * 0.3, 0);
const group = new THREE.Group();
let kit = null, orbit = null, active = false, data = null, cols = [], X = () => 0, marker = null, pickPlane = null;
let hoverQueued = false, px = 0, py = 0;
const css = () => { const c = getComputedStyle(document.documentElement), k = n => c.getPropertyValue(n).trim(); return {up: k('--up'), down: k('--down'), gold: k('--gold'), warn: k('--warn'), text: k('--text-2'), line: k('--line-2')}; };

function build() {
  /* sem contexto não há o que montar (e a restauração chama build de novo) */
  if (!kit || !data || kit.lost) return;
  disposeGroup(group); cols = []; marker = null; pickPlane = null;
  const pts = (data.points || []).map(p => ({m: Number(p.month), v: [p.p5, p.p25, p.p50, p.p75, p.p95].map(Number)}));
  if (pts.length < 2) { note.textContent = 'Sem projeção para mostrar.'; note.hidden = false; kit.invalidate(); return; }
  note.hidden = true;
  const step = Math.max(1, Math.ceil(pts.length / 130)), sel = pts.filter((_, i) => i % step === 0 || i === pts.length - 1);
  const hist = data.histogram || {edges: [], counts: []}, edges = (hist.edges || []).map(Number), counts = (hist.counts || []).map(Number);
  const goal = data.goal != null ? Number(data.goal) : null;
  const ymax = Math.max(...sel.map(p => p.v[4]), edges.length ? edges[edges.length - 1] : 0, goal || 0) * 1.04 || 1;
  const Y = v => Math.max(0, v) / ymax * H, n = sel.length, tmax = sel[n - 1].m || 1;
  X = m => (m / tmax - 0.5) * W;
  cols = sel;
  const k = css(), tint = [new THREE.Color(k.down), new THREE.Color(k.down).lerp(new THREE.Color(k.gold), 0.5), new THREE.Color(k.gold), new THREE.Color(k.gold).lerp(new THREE.Color(k.up), 0.5), new THREE.Color(k.up)];

  /* superfície entre as cinco curvas (x = tempo, z = percentil, y = patrimônio) */
  const pos = [], colr = [], idx = [];
  sel.forEach((p, i) => { for (let r = 0; r < 5; r++) { pos.push(X(p.m), Y(p.v[r]), Z[r]); colr.push(tint[r].r, tint[r].g, tint[r].b); } });
  for (let i = 0; i < n - 1; i++) for (let r = 0; r < 4; r++) { const a = i * 5 + r, b = a + 1, c = a + 5, d = c + 1; idx.push(a, b, c, b, d, c); }
  const sg = new THREE.BufferGeometry(); sg.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); sg.setAttribute('color', new THREE.Float32BufferAttribute(colr, 3)); sg.setIndex(idx); sg.computeVertexNormals();
  group.add(new THREE.Mesh(sg, new THREE.MeshStandardMaterial({vertexColors: true, transparent: true, opacity: 0.62, side: THREE.DoubleSide, roughness: 0.55, metalness: 0.1})));
  /* as cinco curvas dos percentis exatos */
  for (let r = 0; r < 5; r++) {
    const g = new THREE.BufferGeometry().setFromPoints(sel.map(p => new THREE.Vector3(X(p.m), Y(p.v[r]), Z[r])));
    group.add(new THREE.Line(g, new THREE.LineBasicMaterial({color: tint[r]})));
    const end = sel[n - 1], l = makeLabel(NAMES[r], k.text, 0.5); l.position.set(W / 2 + 0.55, Y(end.v[r]), Z[r]); group.add(l);
  }
  /* parede do histograma do patrimônio final, na mesma escala de patrimônio */
  if (counts.length && edges.length === counts.length + 1) {
    const mx = Math.max(...counts, 1), hb = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshStandardMaterial({color: new THREE.Color(k.gold), roughness: 0.5, metalness: 0.2, transparent: true, opacity: 0.85}), counts.length), d = new THREE.Object3D();
    counts.forEach((c, i) => { const y0 = Y(edges[i]), y1 = Y(edges[i + 1]), len = Math.max(0.02, c / mx * 2.4); d.position.set(W / 2 + 1.5 + len / 2, (y0 + y1) / 2, 0); d.scale.set(len, Math.max(0.02, (y1 - y0) * 0.86), 0.5); d.updateMatrix(); hb.setMatrixAt(i, d.matrix); });
    hb.instanceMatrix.needsUpdate = true; group.add(hb);
    const hl = makeLabel('distribuição do patrimônio final', k.text, 0.34); hl.position.set(W / 2 + 3.4, -0.4, 0); group.add(hl);
  }
  /* meta, quando existe: plano horizontal na altura dela */
  if (goal != null && goal > 0 && goal <= ymax) {
    const plane = new THREE.Mesh(new THREE.PlaneGeometry(W, 5.6), new THREE.MeshBasicMaterial({color: new THREE.Color(k.warn), transparent: true, opacity: 0.12, side: THREE.DoubleSide, depthWrite: false}));
    plane.rotation.x = -Math.PI / 2; plane.position.set(0, Y(goal), 0); group.add(plane);
    const gl = makeLabel('meta ' + short(goal), k.warn, 0.4); gl.position.set(-W / 2 - 0.9, Y(goal), 0); group.add(gl);
  }
  /* piso e escalas: patrimônio à esquerda, anos à frente */
  const grid = new THREE.GridHelper(W + 2, 14, new THREE.Color(k.line), new THREE.Color(k.line)); grid.position.set(0, -0.001, 0); grid.scale.z = 0.8; grid.material.transparent = true; grid.material.opacity = 0.35; group.add(grid);
  for (let t = 0; t <= 4; t++) { const v = ymax * t / 4, sp = makeLabel('R$ ' + short(v), k.text, 0.5); sp.position.set(-W / 2 - 0.3 - sp.scale.x / 2, Y(v), Z[0] - 0.4); group.add(sp); }
  const years = tmax / 12;
  for (let t = 0; t <= 4; t++) { const m = tmax * t / 4, sp = makeLabel((years * t / 4).toLocaleString('pt-BR', {maximumFractionDigits: 1}) + ' anos', k.text, 0.5); sp.position.set(X(m), -0.05, Z[4] + 0.9); group.add(sp); }
  /* marcador do mês sob o ponteiro e o plano invisível que o raycast usa para achar o mês */
  const mg = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, Z[0]), new THREE.Vector3(0, H, Z[0]), new THREE.Vector3(0, H, Z[4]), new THREE.Vector3(0, 0, Z[4]), new THREE.Vector3(0, 0, Z[0])]);
  marker = new THREE.Line(mg, new THREE.LineBasicMaterial({color: new THREE.Color(k.text), transparent: true, opacity: 0.55})); marker.visible = false; group.add(marker);
  pickPlane = new THREE.Mesh(new THREE.PlaneGeometry(W, H), new THREE.MeshBasicMaterial({visible: false, side: THREE.DoubleSide})); pickPlane.position.set(0, H / 2, 0); group.add(pickPlane);

  const last = pts[pts.length - 1];
  canvas.setAttribute('aria-label', `Superfície 3D da projeção do patrimônio ao longo de ${years.toLocaleString('pt-BR', {maximumFractionDigits: 1})} anos: percentis 5, 25, 50, 75 e 95; no fim, o percentil 5 é ${BRL.format(last.v[0])}, a mediana ${BRL.format(last.v[2])} e o percentil 95 ${BRL.format(last.v[4])}. É uma projeção, não uma promessa de retorno. Para ler os números use a tabela.`);
  kit.invalidate();
}

function ensure() {
  if (kit) return true;
  kit = createStage({
    stage: box, canvas, fov: 34, exposure: 1.0, alpha: true,
    onFail: () => { note.textContent = 'Seu navegador não tem WebGL: use o gráfico 2D e a tabela.'; note.hidden = false; },
    onLost: () => { note.textContent = 'O navegador pausou o 3D. Volte para a aba ou alterne o gráfico para retomar.'; note.hidden = false; },
    onRestore: () => { note.hidden = true; build(); },
    onFrame: dt => orbit.update(dt)
  });
  if (!kit) return false;
  kit.scene.add(group);
  kit.scene.add(new THREE.HemisphereLight(0xffffff, 0x445066, 0.95));
  const key = new THREE.DirectionalLight(0xffffff, 1.5); key.position.set(-6, 10, 8); kit.scene.add(key);
  const ray = new THREE.Raycaster(), ndc = new THREE.Vector2();
  const pick = () => {
    hoverQueued = false;
    if (!pickPlane || !cols.length || orbit.dragging()) return;
    const r = canvas.getBoundingClientRect(); ndc.set(((px - r.left) / r.width) * 2 - 1, -((py - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, kit.camera);
    const h = ray.intersectObject(pickPlane, false)[0];
    if (!h) { tip.hidden = true; marker.visible = false; kit.invalidate(); return; }
    const tmax = cols[cols.length - 1].m || 1, m = (h.point.x / W + 0.5) * tmax;
    let best = 0; for (let i = 1; i < cols.length; i++) if (Math.abs(cols[i].m - m) < Math.abs(cols[best].m - m)) best = i;
    const p = cols[best]; marker.position.x = X(p.m); marker.visible = true;
    tip.textContent = '';
    const head = document.createElement('div'); head.className = 'src'; head.textContent = `Mês ${p.m} (${(p.m / 12).toLocaleString('pt-BR', {maximumFractionDigits: 1})} anos) · projeção`;
    const rows = document.createElement('div'); rows.className = 'num'; rows.textContent = NAMES.map((n, i) => `${n.padEnd(4)} ${BRL.format(p.v[i])}`).join('\n');
    tip.append(head, rows); tip.hidden = false;
    const rb = box.getBoundingClientRect(); tip.style.left = Math.min(rb.width - tip.offsetWidth - 8, Math.max(8, px - rb.left + 14)) + 'px'; tip.style.top = Math.max(8, py - rb.top - tip.offsetHeight - 14) + 'px';
    kit.invalidate();
  };
  orbit = createOrbit(kit, canvas, {
    host: box, target, start: {az: -0.5, el: 0.72, dist: 16},
    fit: () => Math.max(16, 20 / (2 * Math.tan(kit.camera.fov * Math.PI / 360) * Math.max(0.5, kit.camera.aspect))),
    onHover: e => { px = e.clientX; py = e.clientY; if (!hoverQueued) { hoverQueued = true; requestAnimationFrame(pick); } },
    onDragStart: () => { tip.hidden = true; if (marker) marker.visible = false; },
    onLeave: () => { tip.hidden = true; if (marker) { marker.visible = false; kit.invalidate(); } }
  });
  return true;
}

function setActive(on) {
  active = on; btn.setAttribute('aria-pressed', String(on));
  box.hidden = !on; flat.hidden = on;
  if (on) { if (!ensure()) return; document.dispatchEvent(new Event('mw:mc-need')); build(); kit.wake(); }
  else document.dispatchEvent(new Event('mw:mc-2d-visible'));
}
btn.addEventListener('click', () => setActive(!active));
document.getElementById('mc3dIn').addEventListener('click', () => orbit && orbit.zoom(0.85));
document.getElementById('mc3dOut').addEventListener('click', () => orbit && orbit.zoom(1.18));
document.getElementById('mc3dReset').addEventListener('click', () => orbit && orbit.reset());
document.addEventListener('mw:mc-data', e => { data = e.detail && e.detail.data; if (active && kit) build(); });
new MutationObserver(() => { if (active && kit) build(); }).observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
