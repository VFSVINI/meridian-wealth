/* Correlação em 3D (item #42): a matriz de correlação de Pearson entre os ativos da carteira como barras. Altura = correlação: positiva sobe em verde,
   negativa desce em vermelho. A célula sem dado ("não informado", poucos pregões em comum) NUNCA vira zero: é só um marcador cinza achatado, sem barra.
   Só retornos reais: o módulo consome a matriz que a API já devolveu (evento mw:cr-data) e mostra os mesmos números da tabela. Nada é animado como dado.
   Usa o kit3d.js (órbita, rótulos, pausa fora da tela, perda de contexto). Módulo ES: não declara nenhum global. */
import {THREE, createStage, createOrbit, makeLabel, disposeGroup} from '/kit3d.js';

const card = document.getElementById('cr3dCard');
const box = document.getElementById('cr3d');
const canvas = document.getElementById('cr3dCanvas');
const tip = document.getElementById('cr3dTip');
const note = document.getElementById('cr3dNote');
if (!card || !box || !canvas) throw new Error('correlação 3D: elementos ausentes');

const HMAX = 3;                                        /* altura da correlação 1,0 em unidades da cena */
const group = new THREE.Group();
let kit = null, orbit = null, data = null, matrix = null, bars = null, voids = null, barIdx = [], voidIdx = [], hovered = null, hoverQueued = false, px = 0, py = 0;
const target = new THREE.Vector3(0, 0.9, 0.3);
const css = () => { const c = getComputedStyle(document.documentElement), k = n => c.getPropertyValue(n).trim(); return {up: k('--up'), down: k('--down'), mute: k('--text-3'), text: k('--text-2'), line: k('--line-2'), gold: k('--gold')}; };
const fmt = v => Number(v).toLocaleString('pt-BR', {minimumFractionDigits: 2, maximumFractionDigits: 2});
const cut = l => l.length > 14 ? l.slice(0, 13) + '…' : l;

function build() {
  /* com o contexto perdido não há o que montar (e a restauração chama build de novo) */
  if (!kit || !data || kit.lost) return;
  disposeGroup(group); bars = null; voids = null; barIdx = []; voidIdx = []; hovered = null;
  const m = data.assets;
  if (!m || !m.labels || m.labels.length < 2) { card.hidden = true; return; }
  card.hidden = false; matrix = m;
  const n = m.labels.length, k = css(), up = new THREE.Color(k.up), down = new THREE.Color(k.down), mute = new THREE.Color(k.mute);
  const off = (n - 1) / 2, gap = 1.15;
  const posBars = [], posVoids = [];
  for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) { const v = m.values[i][j]; if (v == null) posVoids.push([i, j]); else posBars.push([i, j, Number(v)]); }
  const d = new THREE.Object3D();
  bars = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshStandardMaterial({roughness: 0.45, metalness: 0.12}), Math.max(1, posBars.length));
  posBars.forEach(([i, j, v], idx) => {
    const h = Math.max(0.03, Math.abs(v) * HMAX);
    d.position.set((i - off) * gap, v >= 0 ? h / 2 : -h / 2, (j - off) * gap); d.scale.set(0.82, h, 0.82); d.updateMatrix();
    bars.setMatrixAt(idx, d.matrix); bars.setColorAt(idx, v >= 0 ? up : down); barIdx[idx] = [i, j];
  });
  bars.count = posBars.length; bars.instanceMatrix.needsUpdate = true; if (bars.instanceColor) bars.instanceColor.needsUpdate = true; group.add(bars);
  /* "não informado": marcador cinza achatado, nunca uma barra de altura zero (que pareceria correlação nula) */
  voids = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshStandardMaterial({color: mute, roughness: 0.9, transparent: true, opacity: 0.45}), Math.max(1, posVoids.length));
  posVoids.forEach(([i, j], idx) => { d.position.set((i - off) * gap, 0.015, (j - off) * gap); d.scale.set(0.82, 0.03, 0.82); d.updateMatrix(); voids.setMatrixAt(idx, d.matrix); voidIdx[idx] = [i, j]; });
  voids.count = posVoids.length; voids.instanceMatrix.needsUpdate = true; group.add(voids);
  /* piso, plano de correlação zero e rótulos dos ativos nos dois eixos */
  const size = n * gap + 1.2;
  const grid = new THREE.GridHelper(size, n + 1, new THREE.Color(k.line), new THREE.Color(k.line)); grid.position.y = -0.001; grid.material.transparent = true; grid.material.opacity = 0.35; group.add(grid);
  const lblSize = Math.min(0.5, 4.2 / n + 0.18);
  for (let i = 0; i < n; i++) {
    const a = makeLabel(cut(m.labels[i]), k.text, lblSize); a.position.set((i - off) * gap, 0.02, off * gap + gap * 0.9 + a.scale.x / 2 * 0.15); group.add(a);
    const b = makeLabel(cut(m.labels[i]), k.text, lblSize); b.position.set(-off * gap - gap * 0.7 - b.scale.x / 2, 0.02, (i - off) * gap); group.add(b);
  }
  card.dataset.n = String(n);
  canvas.setAttribute('aria-label', `Barras 3D da correlação entre ${n} ativos da carteira: altura é a correlação de Pearson, verde positiva e vermelha negativa; células sem dado ficam como marcador cinza, nunca como zero. A tabela acima traz os mesmos números.`);
  kit.invalidate();
}

function ensure() {
  if (kit) return true;
  kit = createStage({
    stage: box, canvas, fov: 34, exposure: 1.0, alpha: true,
    onFail: () => { note.textContent = 'Seu navegador não tem WebGL: use a tabela acima.'; note.hidden = false; },
    onLost: () => { note.textContent = 'O navegador pausou o 3D. Volte para a aba para retomar; a tabela acima continua valendo.'; note.hidden = false; },
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
    if (!bars || orbit.dragging()) return;
    const r = canvas.getBoundingClientRect(); ndc.set(((px - r.left) / r.width) * 2 - 1, -((py - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, kit.camera);
    const hb = ray.intersectObject(bars, false)[0], hv = voids ? ray.intersectObject(voids, false)[0] : null;
    const best = hb && (!hv || hb.distance <= hv.distance) ? ['bar', hb.instanceId] : hv ? ['void', hv.instanceId] : null;
    const key = best ? best.join(':') : null;
    if (key === hovered) { if (best) place(); return; }
    hovered = key;
    if (!best) { tip.hidden = true; kit.invalidate(); return; }
    const [i, j] = best[0] === 'bar' ? barIdx[best[1]] : voidIdx[best[1]], v = matrix.values[i][j], days = matrix.days[i][j];
    tip.textContent = '';
    const head = document.createElement('div'); head.className = 'src'; head.textContent = `${matrix.labels[i]} × ${matrix.labels[j]}`;
    const body = document.createElement('div'); body.className = 'num';
    body.textContent = v == null ? `não informado\n${days} pregões em comum (mínimo ${data.minDays})` : `correlação ${fmt(v)}\n${days} pregões em comum${days < 120 ? '\nestimativa frágil (poucos pregões)' : ''}`;
    tip.append(head, body); tip.hidden = false; place(); kit.invalidate();
  };
  const place = () => { const rb = box.getBoundingClientRect(); tip.style.left = Math.min(rb.width - tip.offsetWidth - 8, Math.max(8, px - rb.left + 14)) + 'px'; tip.style.top = Math.max(8, py - rb.top - tip.offsetHeight - 14) + 'px'; };
  orbit = createOrbit(kit, canvas, {
    host: box, target, start: {az: -0.62, el: 0.6, dist: 14},
    fit: () => { const n = Number(card.dataset.n || 4), need = n * 1.15 + 5; return Math.max(13, need / (2 * Math.tan(kit.camera.fov * Math.PI / 360) * Math.max(0.5, kit.camera.aspect))) * 1.3; },
    onHover: e => { px = e.clientX; py = e.clientY; if (!hoverQueued) { hoverQueued = true; requestAnimationFrame(pick); } },
    onDragStart: () => { tip.hidden = true; hovered = null; },
    onLeave: () => { tip.hidden = true; hovered = null; }
  });
  return true;
}

document.getElementById('cr3dIn').addEventListener('click', () => orbit && orbit.zoom(0.85));
document.getElementById('cr3dOut').addEventListener('click', () => orbit && orbit.zoom(1.18));
document.getElementById('cr3dReset').addEventListener('click', () => orbit && orbit.reset());
document.addEventListener('mw:cr-data', e => {
  data = e.detail && e.detail.data;
  if (!data || !data.assets) { card.hidden = true; return; }
  if (!ensure()) return;
  card.hidden = false; build(); kit.wake();
});
new MutationObserver(() => { if (kit && data) build(); }).observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
document.dispatchEvent(new Event('mw:cr-need'));
