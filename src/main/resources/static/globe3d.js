/* Globo 3D dos mercados (item #41): as bolsas do universo no globo, coloridas por aberta, fechada ou feriado AGORA.
   Dado real: o estado vem do servidor (/api/markets, com feriados oficiais e o aviso de calendário não verificado), chegando pelo evento mw:markets;
   os ativos de cada bolsa vêm do /api/universe. Os continentes são o Natural Earth 110m (domínio público, servido pelo projeto). As coordenadas são as
   das cidades das bolsas (fato geográfico, não dado de mercado) e a posição do Sol é calculada do horário UTC (aproximada, erro de poucos graus).
   FX e cripto (24 h, sem cidade) são anéis em órbita. Nenhum número é animado. Usa o kit3d.js. Módulo ES: não declara nenhum global. */
import {THREE, createStage} from '/kit3d.js';

const stageEl = document.getElementById('globeStage');
const canvas = document.getElementById('globeCanvas');
const tip = document.getElementById('globeTip');
const panel = document.getElementById('globeSel');
if (!stageEl || !canvas) throw new Error('globo 3D: elementos ausentes');

/* cidades das bolsas (latitude, longitude); FX e cripto não têm cidade */
const CITY = {
  NYSE: ['Nova York', 40.7069, -74.0113], B3: ['São Paulo', -23.5505, -46.6333], XETRA: ['Frankfurt', 50.1109, 8.6821], LSE: ['Londres', 51.5152, -0.0993],
  EURONEXT_AMS: ['Amsterdã', 52.3676, 4.9041], TSE: ['Tóquio', 35.6812, 139.7671], HKEX: ['Hong Kong', 22.2819, 114.1581], ASX: ['Sydney', -33.8688, 151.2093],
  TADAWUL: ['Riade', 24.7136, 46.6753], JSE: ['Joanesburgo', -26.2041, 28.0473]
};
const NAME = {NYSE: 'NYSE', B3: 'B3', XETRA: 'Xetra', LSE: 'LSE', EURONEXT_AMS: 'Euronext', TSE: 'Tóquio', HKEX: 'Hong Kong', ASX: 'ASX', TADAWUL: 'Tadawul', JSE: 'JSE', GLOBAL_24H: 'FX e commodities', CRYPTO: 'Cripto'};
const R = 1;
const vec = (lat, lon, r = R) => { const la = lat * Math.PI / 180, lo = lon * Math.PI / 180; return new THREE.Vector3(r * Math.cos(la) * Math.sin(lo), r * Math.sin(la), r * Math.cos(la) * Math.cos(lo)); };

let earth = null;   /* a esfera base: oclui os marcadores do outro lado no raycast */
let kit = null, started = false, markets = [], universe = [], pins = [], rings = [], dots = null, land = null, selected = null;
const globe = new THREE.Group();
const st = {az: 0, el: 0.25, vaz: 0, vel: 0, auto: true, dragging: false, lastX: 0, lastY: 0, moved: 0};
let hit = null, hoverQueued = false, px = 0, py = 0, lastSun = 0, sunVec = new THREE.Vector3(1, 0, 0);

const css = () => { const c = getComputedStyle(document.documentElement), k = n => c.getPropertyValue(n).trim(); return {up: k('--up'), warn: k('--warn'), mute: k('--text-3'), info: k('--info'), gold: k('--gold'), text: k('--text-2')}; };

/* posição aproximada do Sol (declinação pelo dia do ano e longitude pela hora UTC; ignora a equação do tempo, erro de poucos graus) */
function subsolar(d) {
  const day = (d - Date.UTC(d.getUTCFullYear(), 0, 0)) / 864e5;
  const lat = -23.44 * Math.cos(2 * Math.PI / 365 * (day + 10));
  const h = d.getUTCHours() + d.getUTCMinutes() / 60 + d.getUTCSeconds() / 3600;
  return {lat, lon: -(h - 12) * 15};
}
function inRing(x, y, ring) {
  let ins = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
    if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) ins = !ins;
  }
  return ins;
}
/* pontos sobre a terra: grade de ~1,5 grau (espaçamento em longitude corrigido pela latitude) testada contra os polígonos reais */
function landPoints(geo) {
  const polys = [];
  for (const f of geo.features) {
    const g = f.geometry, parts = g.type === 'Polygon' ? [g.coordinates] : g.coordinates;
    for (const rings2 of parts) { const xs = rings2[0].map(p => p[0]), ys = rings2[0].map(p => p[1]); polys.push({rings: rings2, x0: Math.min(...xs), x1: Math.max(...xs), y0: Math.min(...ys), y1: Math.max(...ys)}); }
  }
  const pts = [], step = 1.5;
  for (let lat = -84; lat <= 84; lat += step) {
    const dl = step / Math.max(0.2, Math.cos(lat * Math.PI / 180));
    for (let lon = -180; lon < 180; lon += dl) {
      let inside = false;
      for (const p of polys) {
        if (lon < p.x0 || lon > p.x1 || lat < p.y0 || lat > p.y1) continue;
        let n = 0; for (const r of p.rings) if (inRing(lon, lat, r)) n++;
        if (n % 2 === 1) { inside = true; break; }
      }
      if (inside) pts.push([lat, lon]);
    }
  }
  return pts;
}

function paintLand(now) {
  if (!dots) return;
  const s = subsolar(new Date(now)); sunVec = vec(s.lat, s.lon, 1).normalize();
  const col = dots.geometry.getAttribute('color'), base = dots.userData.base, k = css(), day = new THREE.Color(k.info), night = new THREE.Color(k.info).multiplyScalar(0.5);
  for (let i = 0; i < base.length; i++) { const f = THREE.MathUtils.smoothstep(base[i].dot(sunVec), -0.12, 0.18); const c = night.clone().lerp(day, f); col.setXYZ(i, c.r, c.g, c.b); }
  col.needsUpdate = true; lastSun = now;
}

function buildStatic() {
  const sphere = new THREE.Mesh(new THREE.SphereGeometry(R * 0.995, 64, 48), new THREE.MeshStandardMaterial({color: 0x16304f, roughness: 0.9, metalness: 0}));
  earth = sphere; globe.add(sphere);
  const halo = new THREE.Mesh(new THREE.SphereGeometry(R * 1.1, 48, 32), new THREE.MeshBasicMaterial({color: new THREE.Color(css().info), transparent: true, opacity: 0.07, side: THREE.BackSide, depthWrite: false}));
  globe.add(halo);
  const grat = [];
  for (let lat = -60; lat <= 60; lat += 30) for (let lo = 0; lo <= 360; lo += 6) grat.push(vec(lat, lo - 180, R * 1.002));
  for (let lon = -180; lon < 180; lon += 30) for (let la = -84; la <= 84; la += 6) grat.push(vec(la, lon, R * 1.002));
  const gl = new THREE.BufferGeometry().setFromPoints(grat);
  globe.add(new THREE.Points(gl, new THREE.PointsMaterial({color: new THREE.Color(css().text), size: 0.004, transparent: true, opacity: 0.35, sizeAttenuation: true})));
  if (land) {
    const base = land.map(([la, lo]) => vec(la, lo, R * 1.004)), pos = new Float32Array(base.length * 3), colors = new Float32Array(base.length * 3);
    base.forEach((v, i) => pos.set([v.x, v.y, v.z], i * 3));
    const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(pos, 3)); g.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    dots = new THREE.Points(g, new THREE.PointsMaterial({size: 0.0105, vertexColors: true, sizeAttenuation: true, transparent: true, opacity: 0.95}));
    dots.userData.base = base.map(v => v.clone().normalize()); globe.add(dots); paintLand(Date.now());
  }
}

function colorOf(m, k) { return new THREE.Color(m.holiday ? k.warn : m.open ? k.up : k.mute); }
let pinSig = '';
function updateAria() {
  const open = markets.filter(m => m.open).map(m => NAME[m.exchange] || m.exchange);
  canvas.setAttribute('aria-label', `Globo 3D das bolsas. Abertas agora: ${open.length ? open.join(', ') : 'nenhuma'}. A lista ao lado traz o estado de todas em texto.`);
}
/* a cada 30 s o estado chega de novo; se as bolsas são as mesmas, só recolore (medido: recriar tudo criava e destruía ~170 buffers da GPU por atualização) */
function refreshPins() {
  const k = css();
  for (const m of markets) {
    const col = colorOf(m, k), p = pins.find(x => x.m.exchange === m.exchange), r = rings.find(x => x.m.exchange === m.exchange);
    if (p) { p.m = m; p.open = m.open; p.group.children.forEach(c => { if (c.material && c.material.visible !== false) c.material.color.copy(col); }); p.ring.material.opacity = m.open ? 0.8 : 0.35; }
    if (r) { r.m = m; r.group.children[0].material.color.copy(col); r.sat.material.color.copy(col); }
  }
  updateAria(); paintSelection(); kit && kit.invalidate();
}
function buildPins() {
  const sig = markets.map(m => m.exchange).join(',');
  if (sig === pinSig && pins.length + rings.length === markets.length) { refreshPins(); return; }
  pinSig = sig;
  pins.forEach(p => { globe.remove(p.group); p.group.traverse(o => { if (o.geometry) o.geometry.dispose(); if (o.material) o.material.dispose(); }); });
  rings.forEach(r => { globe.remove(r.group); r.group.traverse(o => { if (o.geometry) o.geometry.dispose(); if (o.material) o.material.dispose(); }); });
  pins = []; rings = [];
  const k = css(); let ringIdx = 0;
  for (const m of markets) {
    const col = colorOf(m, k);
    if (CITY[m.exchange]) {
      const [, la, lo] = CITY[m.exchange], g = new THREE.Group(), p = vec(la, lo, R * 1.012), n = p.clone().normalize();
      const dot = new THREE.Mesh(new THREE.SphereGeometry(0.026, 16, 12), new THREE.MeshBasicMaterial({color: col})); dot.position.copy(p); g.add(dot);
      const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.004, 0.004, 0.09, 6), new THREE.MeshBasicMaterial({color: col}));
      stem.position.copy(n.clone().multiplyScalar(R * 1.012 + 0.045)); stem.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), n); g.add(stem);
      const ring = new THREE.Mesh(new THREE.RingGeometry(0.04, 0.047, 40), new THREE.MeshBasicMaterial({color: col, transparent: true, opacity: m.open ? 0.8 : 0.35, side: THREE.DoubleSide, depthWrite: false}));
      /* orientação em coordenadas do próprio globo (lookAt usaria o mundo e quebraria com o globo girado): o anel olha para fora */
      ring.position.copy(n.clone().multiplyScalar(R * 1.014)); ring.quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), n); g.add(ring);
      const hitMesh = new THREE.Mesh(new THREE.SphereGeometry(0.07, 8, 6), new THREE.MeshBasicMaterial({visible: false})); hitMesh.position.copy(p); hitMesh.userData.exchange = m.exchange; g.add(hitMesh);
      globe.add(g); pins.push({group: g, m, ring, hitMesh, open: m.open});
    } else {
      /* 24 h: anel em órbita com uma esfera que o percorre */
      const g = new THREE.Group(), rad = 1.28 + ringIdx * 0.1;
      const orbit = new THREE.Mesh(new THREE.TorusGeometry(rad, 0.004, 8, 120), new THREE.MeshBasicMaterial({color: col, transparent: true, opacity: 0.5})); g.add(orbit);
      const sat = new THREE.Mesh(new THREE.SphereGeometry(0.03, 14, 10), new THREE.MeshBasicMaterial({color: col})); g.add(sat);
      const hitMesh = new THREE.Mesh(new THREE.SphereGeometry(0.09, 8, 6), new THREE.MeshBasicMaterial({visible: false})); hitMesh.userData.exchange = m.exchange; sat.add(hitMesh);
      g.rotation.x = Math.PI / 2 - 0.5 + ringIdx * 0.9; g.rotation.y = ringIdx * 0.8; g.userData.rad = rad;
      globe.add(g); rings.push({group: g, m, sat, rad, phase: ringIdx * 2.2, hitMesh}); ringIdx++;
    }
  }
  updateAria();
  paintSelection();
  kit && kit.invalidate();
}

/* o servidor já devolve a hora NO FUSO DA BOLSA (ex.: 2026-11-24T15:09:23+11:00): a hora local dela é o HH:MM da própria string, não a do navegador */
function localTime(m) { return m.localTime.slice(11, 16); }
function describe(m) {
  const status = m.holiday ? 'feriado' : m.open ? 'aberta' : 'fechada';
  return {title: NAME[m.exchange] || m.exchange, line: `${status} · ${m.reason} · hora local ${localTime(m)}${m.holidaysVerified ? '' : ' · calendário de feriados não verificado'}`};
}
function showPanel(m) {
  panel.textContent = '';
  const d = describe(m), h = document.createElement('div'); h.className = 'gs-h';
  const t = document.createElement('b'); t.textContent = d.title; const c = CITY[m.exchange]; if (c && c[0] !== d.title) { const s = document.createElement('span'); s.className = 'mute'; s.textContent = ' · ' + c[0]; h.append(t, s); } else h.append(t);
  const p = document.createElement('p'); p.textContent = d.line; p.className = 'mute';
  const list = document.createElement('div'); list.className = 'gs-assets';
  const mine = universe.filter(u => u.exchange === m.exchange);
  if (!mine.length) { const e = document.createElement('span'); e.className = 'mute'; e.textContent = 'Nenhum ativo do universo nesta bolsa.'; list.append(e); }
  for (const u of mine) { const b = document.createElement('button'); b.type = 'button'; b.className = 'fchip'; b.textContent = u.symbol; b.title = u.name; b.addEventListener('click', () => document.dispatchEvent(new CustomEvent('mw:open-asset', {detail: {symbol: u.symbol}}))); list.append(b); }
  panel.append(h, p, list);
}
function paintSelection() {
  pins.forEach(p => { const on = p.m.exchange === selected; p.ring.scale.setScalar(on ? 1.9 : 1); p.ring.material.opacity = on ? 1 : (p.open ? 0.8 : 0.35); });
  document.querySelectorAll('#clock button[data-ex]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.ex === selected)));
  const m = markets.find(x => x.exchange === selected); if (m) showPanel(m);
}
function select(ex, focus) {
  selected = ex; paintSelection();
  const c = CITY[ex]; if (c && focus) { st.auto = false; st.target = -c[2] * Math.PI / 180; }
  kit && kit.invalidate();
}

function frame(dt, now) {
  if (now - lastSun > 60000) paintLand(Date.now());
  if (!st.dragging) {
    if (st.target != null) { let d = st.target - st.az; d = Math.atan2(Math.sin(d), Math.cos(d)); st.az += d * Math.min(1, dt * 0.006); if (Math.abs(d) < 0.002) st.target = null; }
    else if (Math.abs(st.vaz) > 0.00001 || Math.abs(st.vel) > 0.00001) { st.az += st.vaz * dt; st.el = Math.max(-1.2, Math.min(1.2, st.el + st.vel * dt)); st.vaz *= 0.94; st.vel *= 0.94; }
    else if (st.auto && !kit.reduced.matches) st.az += 0.00012 * dt;
  }
  globe.rotation.y = st.az; globe.rotation.x = st.el;
  const t = now / 1000;
  for (const p of pins) if (p.open && !kit.reduced.matches) { const s = 1 + 0.22 * Math.sin(t * 2.4 + p.m.exchange.length); if (p.m.exchange !== selected) p.ring.scale.setScalar(s); }
  for (const r of rings) { const a = (kit.reduced.matches ? r.phase : t * 0.5 + r.phase); r.sat.position.set(Math.cos(a) * r.rad, Math.sin(a) * r.rad, 0); }
  /* o Sol fica fixo no mundo: a luz acompanha a posição calculada para a Terra que gira */
  light.position.copy(globe.localToWorld(sunVec.clone().multiplyScalar(6)));
  return !kit.reduced.matches || st.dragging || st.target != null || Math.abs(st.vaz) > 0.00001 || Math.abs(st.vel) > 0.00001;
}

let light = null;
function ensure() {
  if (kit) return true;
  kit = createStage({
    stage: stageEl, canvas, fov: 32, exposure: 1.05,
    onFail: () => { canvas.hidden = true; panel.textContent = 'Seu navegador não tem WebGL: use a lista de pregões ao lado.'; },
    onLost: () => { panel.textContent = 'O navegador pausou o 3D. Volte para a aba para retomar; a lista de pregões continua valendo.'; },
    onRestore: () => { panel.textContent = 'Clique numa bolsa no globo para ver os ativos dela.'; paintSelection(); },   /* nada mora só na GPU: o three.js reenvia tudo sozinho */
    onFrame: frame
  });
  if (!kit) return false;
  kit.camera.position.set(0, 0, 4.3);
  kit.scene.add(globe);
  kit.scene.add(new THREE.AmbientLight(0x8aa2c8, 1.0));
  light = new THREE.DirectionalLight(0xfff1d0, 2.2); kit.scene.add(light);
  const ray = new THREE.Raycaster(), ndc = new THREE.Vector2();
  const pickAt = () => {
    hoverQueued = false; if (st.dragging) return;
    const r = canvas.getBoundingClientRect(); ndc.set(((px - r.left) / r.width) * 2 - 1, -((py - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, kit.camera);
    const targets = [...pins.map(p => p.hitMesh), ...rings.map(x => x.hitMesh)];
    /* o globo entra no raycast como oclusor: o primeiro objeto atingido decide, então um marcador do outro lado do planeta (invisível) não é achado */
    const h = ray.intersectObjects(earth ? [earth, ...targets] : targets, false)[0]; hit = h && h.object !== earth ? h.object.userData.exchange : null;
    canvas.style.cursor = hit ? 'pointer' : '';
    if (hit) { const m = markets.find(x => x.exchange === hit), d = describe(m); tip.textContent = ''; const b = document.createElement('b'); b.textContent = d.title; const l = document.createElement('div'); l.className = 'src'; l.textContent = d.line; tip.append(b, l); tip.hidden = false; const rr = stageEl.getBoundingClientRect(); tip.style.left = Math.min(rr.width - tip.offsetWidth - 8, Math.max(8, px - rr.left + 14)) + 'px'; tip.style.top = Math.max(8, py - rr.top - tip.offsetHeight - 12) + 'px'; } else tip.hidden = true;
  };
  canvas.addEventListener('pointermove', e => {
    px = e.clientX; py = e.clientY;
    if (!st.dragging) { if (!hoverQueued) { hoverQueued = true; requestAnimationFrame(pickAt); } return; }
    const dx = e.clientX - st.lastX, dy = e.clientY - st.lastY; st.moved += Math.abs(dx) + Math.abs(dy);
    st.az += dx * 0.006; st.el = Math.max(-1.2, Math.min(1.2, st.el + dy * 0.005)); st.vaz = dx * 0.006 / 16; st.vel = dy * 0.005 / 16; st.lastX = e.clientX; st.lastY = e.clientY; kit.invalidate();
  });
  canvas.addEventListener('pointerdown', e => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    st.dragging = true; st.moved = 0; st.lastX = e.clientX; st.lastY = e.clientY; st.auto = false; st.target = null; tip.hidden = true;
    try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* ponteiro já inativo */ }
    stageEl.classList.add('grabbing'); kit.invalidate();
  });
  const release = e => {
    if (!st.dragging) return;
    st.dragging = false; stageEl.classList.remove('grabbing');
    try { if (canvas.hasPointerCapture(e.pointerId)) canvas.releasePointerCapture(e.pointerId); } catch (err) { /* já solto */ }
    if (st.moved < 6 && e.type === 'pointerup') { px = e.clientX; py = e.clientY; pickAt(); if (hit) select(hit, true); }   /* clique sem arrastar: escolhe a bolsa sob o ponteiro */
    if (kit.reduced.matches) { st.vaz = 0; st.vel = 0; }
    kit.invalidate();
  };
  canvas.addEventListener('pointerup', release); canvas.addEventListener('pointercancel', release); canvas.addEventListener('lostpointercapture', release);
  canvas.addEventListener('pointerleave', () => { tip.hidden = true; hit = null; });
  canvas.addEventListener('keydown', e => {
    const s = {ArrowLeft: [0.15, 0], ArrowRight: [-0.15, 0], ArrowUp: [0, 0.1], ArrowDown: [0, -0.1]}[e.key];
    if (s) { e.preventDefault(); st.auto = false; st.az += s[0]; st.el = Math.max(-1.2, Math.min(1.2, st.el + s[1])); kit.invalidate(); }
  });
  return true;
}

async function start() {
  if (started) return; started = true;
  if (!ensure()) return;
  try { const [geo, uni] = await Promise.all([fetch('/data/ne_110m_land.geojson').then(r => r.json()), fetch('/api/universe').then(r => r.json())]); land = landPoints(geo); universe = uni; } catch (e) { /* sem continentes: o globo segue com a grade e as bolsas */ }
  buildStatic(); buildPins();
  st.az = -(-new Date().getTimezoneOffset() / 60 * 15) * Math.PI / 180;   /* começa de frente para a longitude do usuário (fuso do navegador) */
  kit.wake();
}
/* o estado das bolsas chega do app.js a cada 30 s (mw:markets); o globo só se monta (e só baixa os continentes) quando está à vista */
let visible = false;
const maybeStart = () => { if (visible && markets.length && !started) start(); };
document.addEventListener('mw:markets', e => {
  markets = e.detail || [];
  if (!started) { maybeStart(); return; }
  if (kit && !kit.lost) buildPins();
});
document.addEventListener('mw:market-pick', e => { if (started) select(e.detail.exchange, true); });
new IntersectionObserver(es => { visible = es[0].isIntersecting; maybeStart(); }).observe(stageEl);
/* o primeiro mw:markets sai antes de este módulo existir (módulos executam depois do HTML): pede o estado agora em vez de esperar 30 s */
document.dispatchEvent(new Event('mw:markets-need'));
new MutationObserver(() => { if (started && kit && !kit.lost) { buildStatic2(); } }).observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
function buildStatic2() { /* troca de tema: recolore marcadores e terra */ buildPins(); paintLand(Date.now()); kit.invalidate(); }
