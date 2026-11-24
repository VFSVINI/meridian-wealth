/* Minha Carteira em 3D (item #43): a alocação da carteira salva como rosca 3D. Anel de fora = classes de ativo (os percentuais EXATOS que a API devolve);
   anel de dentro = posições (peso pelo valor em reais de hoje). Só entram posições com valor em reais, e isso é dito na tela. Passar o mouse numa fatia a
   destaca e mostra o nome e o percentual (nas posições, também o valor). A lista, o treemap 2D e a tabela de posições continuam como alternativa.
   Privacidade: ao sair da conta (mw:ct-clear) o módulo esquece a carteira, esvazia a cena e troca o texto de acessibilidade por um genérico.
   Dado do usuário, só leitura: o módulo consome o evento mw:ct-data do carteira.js. Nada é animado como dado. Usa o kit3d.js. Módulo ES. */
import {THREE, createStage, createOrbit, makeLabel, disposeGroup} from '/kit3d.js';

const card = document.getElementById('ct3dCard');
const box = document.getElementById('ct3d');
const canvas = document.getElementById('ct3dCanvas');
const tip = document.getElementById('ct3dTip');
const note = document.getElementById('ct3dNote');
if (!card || !box || !canvas) throw new Error('carteira 3D: elementos ausentes');

const H = 0.55, GENERIC = 'Rosca 3D da alocação da carteira. Entre na sua conta para ver.';
const RING = {pos: [1.15, 2.15], cls: [2.3, 3.4]};
const group = new THREE.Group();
const target = new THREE.Vector3(0, 0.2, 0);
const pct = v => v.toLocaleString('pt-BR', {minimumFractionDigits: 1, maximumFractionDigits: 1}) + '%';
const brl = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
let kit = null, orbit = null, data = null, slices = [], hovered = -1, hoverQueued = false, px = 0, py = 0;
const css = () => { const c = getComputedStyle(document.documentElement), k = n => c.getPropertyValue(n).trim(); return {gold: k('--gold'), text: k('--text-2'), line: k('--line-2')}; };

function arc(r0, r1, a0, a1) {
  const s = new THREE.Shape();
  s.moveTo(Math.cos(a0) * r1, Math.sin(a0) * r1);
  s.absarc(0, 0, r1, a0, a1, true);
  s.lineTo(Math.cos(a1) * r0, Math.sin(a1) * r0);
  s.absarc(0, 0, r0, a1, a0, false);
  s.closePath();
  return s;
}
/* uma fatia: o anel é um arco no plano e a extrusão sobe em Y (rotateX(-90°) manda y do desenho para -z do mundo) */
function slice(r0, r1, a0, a1, color, meta) {
  const gap = Math.min(0.012, (a0 - a1) * 0.2), A0 = a0 - gap / 2, A1 = a1 + gap / 2;
  const geo = new THREE.ExtrudeGeometry(arc(r0, r1, A0, A1), {depth: H, bevelEnabled: true, bevelSize: 0.025, bevelThickness: 0.025, bevelSegments: 2, curveSegments: Math.max(2, Math.ceil((a0 - a1) * 14))});
  geo.rotateX(-Math.PI / 2);
  const mesh = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({color, roughness: 0.5, metalness: 0.15}));
  const mid = (a0 + a1) / 2; meta.dir = new THREE.Vector3(Math.cos(mid), 0, -Math.sin(mid)); meta.r = (r0 + r1) / 2; mesh.userData = meta;
  group.add(mesh); slices.push(mesh);
  return mesh;
}

function build() {
  /* com o contexto perdido não há o que montar (e a restauração chama build de novo) */
  if (!kit || !data || kit.lost) return;
  disposeGroup(group); slices = []; hovered = -1;
  const cls = (data.byClass || []).map(c => ({label: c.label, v: Number(c.percent), category: c.category})).filter(c => c.v > 0);
  const pos = (data.positions || []).filter(p => p.weight > 0);
  if (!cls.length) { card.hidden = true; return; }
  card.hidden = false;
  const k = css(), colOf = c => new THREE.Color((data.catCol && data.catCol[c]) || '#46505f');
  /* anel de fora: classes, pelo percentual exato da API (somam 100) */
  let acc = 0; const sumC = cls.reduce((a, c) => a + c.v, 0) || 1;
  cls.forEach(c => { const f = c.v / sumC, a0 = Math.PI / 2 - acc * 2 * Math.PI, a1 = Math.PI / 2 - (acc + f) * 2 * Math.PI; acc += f; slice(RING.cls[0], RING.cls[1], a0, a1, colOf(c.category), {kind: 'classe', label: c.label, text: pct(c.v)}); });
  /* anel de dentro: posições, pelo peso no valor em reais */
  const sumP = pos.reduce((a, p) => a + p.weight, 0); acc = 0;
  pos.forEach((p, i) => { const f = p.weight / (sumP || 1), a0 = Math.PI / 2 - acc * 2 * Math.PI, a1 = Math.PI / 2 - (acc + f) * 2 * Math.PI; acc += f; const col = new THREE.Color(k.gold).offsetHSL(0, 0, ((i % 5) - 2) * 0.05);
    slice(RING.pos[0], RING.pos[1], a0, a1, col, {kind: 'posição', label: p.label, text: pct(p.weight * 100) + (p.valueBrl != null ? ' · ' + brl.format(p.valueBrl) : '')}); });
  /* rótulos nas fatias grandes (as pequenas só pelo tooltip) */
  slices.forEach(m => { const big = Number(m.userData.text.split('%')[0].replace(',', '.')) >= 7; if (!big) return; const l = makeLabel(m.userData.label.length > 16 ? m.userData.label.slice(0, 15) + '…' : m.userData.label, k.text, 0.34); l.position.copy(m.userData.dir).multiplyScalar(m.userData.r).setY(H + 0.3); group.add(l); });
  const grid = new THREE.GridHelper(8.4, 14, new THREE.Color(k.line), new THREE.Color(k.line)); grid.position.y = -0.001; grid.material.transparent = true; grid.material.opacity = 0.3; group.add(grid);
  const dropped = (data.positionsWithoutBrlValue || 0);
  canvas.setAttribute('aria-label', `Rosca 3D da alocação da carteira. Classes: ${cls.map(c => `${c.label} ${pct(c.v)}`).join(', ')}. Posições com valor em reais: ${pos.length}${dropped ? `; ${dropped} sem valor em reais ficam de fora` : ''}. A lista de classes e a tabela de posições trazem os mesmos números.`);
  kit.invalidate();
}

function ensure() {
  if (kit) return true;
  kit = createStage({
    stage: box, canvas, fov: 34, exposure: 1.0, alpha: true,
    onFail: () => { note.textContent = 'Seu navegador não tem WebGL: use a lista de classes e a tabela de posições.'; note.hidden = false; },
    onLost: () => { note.textContent = 'O navegador pausou o 3D. Volte para a aba para retomar; a lista e a tabela continuam valendo.'; note.hidden = false; },
    onRestore: () => { note.hidden = true; build(); },
    onFrame: dt => { const keep = orbit.update(dt); easeHover(dt); return keep || hoverAnimating; }
  });
  if (!kit) return false;
  kit.scene.add(group);
  kit.scene.add(new THREE.HemisphereLight(0xffffff, 0x445066, 0.95));
  const key = new THREE.DirectionalLight(0xffffff, 1.5); key.position.set(-5, 9, 7); kit.scene.add(key);
  const ray = new THREE.Raycaster(), ndc = new THREE.Vector2();
  const pick = () => {
    hoverQueued = false;
    if (!slices.length || orbit.dragging()) return;
    const r = canvas.getBoundingClientRect(); ndc.set(((px - r.left) / r.width) * 2 - 1, -((py - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, kit.camera);
    const h = ray.intersectObjects(slices, false)[0], i = h ? slices.indexOf(h.object) : -1;
    if (i !== hovered) { hovered = i; kit.invalidate(); }
    if (i < 0) { tip.hidden = true; return; }
    const m = slices[i].userData;
    tip.textContent = '';
    const head = document.createElement('div'); head.className = 'src'; head.textContent = m.kind;
    const body = document.createElement('div'); body.className = 'num'; body.textContent = `${m.label}\n${m.text}`;
    tip.append(head, body); tip.hidden = false;
    const rb = box.getBoundingClientRect(); tip.style.left = Math.min(rb.width - tip.offsetWidth - 8, Math.max(8, px - rb.left + 14)) + 'px'; tip.style.top = Math.max(8, py - rb.top - tip.offsetHeight - 14) + 'px';
  };
  orbit = createOrbit(kit, canvas, {
    host: box, target, start: {az: -0.3, el: 0.85, dist: 11},
    fit: () => Math.max(11, 9.4 / (2 * Math.tan(kit.camera.fov * Math.PI / 360) * Math.max(0.5, kit.camera.aspect))),
    onHover: e => { px = e.clientX; py = e.clientY; if (!hoverQueued) { hoverQueued = true; requestAnimationFrame(pick); } },
    onTap: e => { px = e.clientX; py = e.clientY; pick(); },
    onDragStart: () => { tip.hidden = true; hovered = -1; },
    onLeave: () => { tip.hidden = true; hovered = -1; kit.invalidate(); }
  });
  return true;
}
/* a fatia sob o mouse sai alguns centésimos do anel (efeito só visual; nenhum número muda) */
let hoverAnimating = false;
function easeHover() {
  hoverAnimating = false;
  slices.forEach((m, i) => {
    const goal = i === hovered ? 0.2 : 0, cur = m.userData.off || 0, next = cur + (goal - cur) * 0.25;
    m.userData.off = Math.abs(goal - next) < 0.003 ? goal : next;
    m.position.copy(m.userData.dir).multiplyScalar(m.userData.off);
    if (m.userData.off !== goal) hoverAnimating = true;
  });
}

document.getElementById('ct3dIn').addEventListener('click', () => orbit && orbit.zoom(0.85));
document.getElementById('ct3dOut').addEventListener('click', () => orbit && orbit.zoom(1.18));
document.getElementById('ct3dReset').addEventListener('click', () => orbit && orbit.reset());
document.addEventListener('mw:ct-data', e => {
  data = e.detail || null;
  if (!data || !data.byClass || !data.byClass.length) { card.hidden = true; return; }
  if (!ensure()) return;
  card.hidden = false; build(); kit.wake();
});
/* sair da conta: o 3D esquece a carteira, esvazia a cena e troca o texto de acessibilidade por um genérico */
document.addEventListener('mw:ct-clear', () => {
  data = null; hovered = -1; tip.hidden = true; card.hidden = true;
  canvas.setAttribute('aria-label', GENERIC);
  if (kit) { disposeGroup(group); slices = []; kit.invalidate(); }
});
new MutationObserver(() => { if (kit && data) build(); }).observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
document.dispatchEvent(new Event('mw:ct-need'));
