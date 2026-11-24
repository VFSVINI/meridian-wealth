/* kit3d (item #40): o que toda cena 3D do projeto precisa e que não deve ser repetido em cada página (ADR 026 e 028).
   Cria o renderizador do three.js (servido localmente) e cuida de: tamanho do palco, só desenhar o que se vê (fora da tela, em outra página ou com a aba
   oculta nenhum evento gasta GPU), perda e restauração do contexto WebGL (celular em segundo plano), movimento reduzido e renderização sob demanda.
   Módulo ES: não declara nenhum global. */
import * as THREE from '/vendor/three.module.min.js';
export {THREE};

/**
 * @param o.stage    elemento que dá o tamanho ao canvas
 * @param o.canvas   o canvas
 * @param o.fov      campo de visão da câmera (padrão 30)
 * @param o.alpha    fundo transparente (padrão true)
 * @param o.exposure exposição do mapeamento de tons (padrão 1.1)
 * @param o.onFrame  (dt, now) => boolean: atualiza a cena; devolver true mantém o laço rodando (animação ou inércia em curso)
 * @param o.onFail   chamado se não houver WebGL
 * @param o.onLost   chamado quando o contexto se perde
 * @param o.onRestore chamado quando o contexto volta (recrie o que mora na GPU, como mapas de reflexo)
 * @returns palco {scene, camera, renderer, reduced, onScreen(), invalidate(), wake()} ou null sem WebGL
 */
export function createStage(o) {
  const {stage, canvas} = o;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({canvas, antialias: true, alpha: o.alpha !== false, powerPreference: 'low-power'});
  } catch (e) {
    if (o.onFail) o.onFail(e);
    return null;
  }
  renderer.setClearColor(0x000000, o.alpha === false ? 1 : 0);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = o.exposure || 1.1;
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(o.fov || 30, 1, 0.1, o.far || 200);
  const s = {scene, camera, renderer, reduced, lost: false, visible: true, dirty: true, raf: 0, last: performance.now()};

  /* só desenha o que se vê */
  s.onScreen = () => s.visible && !document.hidden && stage.clientWidth > 0 && !s.lost;
  function size() {
    const w = Math.max(1, stage.clientWidth), h = Math.max(1, stage.clientHeight);
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
    renderer.setSize(w, h, false);
    camera.aspect = w / h; camera.updateProjectionMatrix();
  }
  function frame(now) {
    s.raf = 0;
    if (!s.onScreen()) return;
    const dt = Math.min(64, now - s.last); s.last = now;
    const keep = o.onFrame ? !!o.onFrame(dt, now) : false;
    if (s.dirty || keep) { s.dirty = false; renderer.render(scene, camera); }
    if (keep || s.dirty) s.raf = requestAnimationFrame(frame);
  }
  /* pede um quadro (sob demanda): chamado por quem mudou a cena */
  s.invalidate = () => { s.dirty = true; if (!s.raf && s.onScreen()) s.raf = requestAnimationFrame(frame); };
  /* redimensiona e desenha já; se o laço precisar continuar, o próprio quadro agenda o seguinte */
  s.wake = () => {
    cancelAnimationFrame(s.raf); s.raf = 0;
    if (!s.onScreen()) return;
    size(); s.last = performance.now(); s.dirty = true; frame(performance.now());
  };

  /* celulares tiram o contexto WebGL quando o navegador vai para segundo plano: sem tratar, o canvas vira um retângulo branco */
  canvas.addEventListener('webglcontextlost', e => {
    e.preventDefault(); s.lost = true; cancelAnimationFrame(s.raf); s.raf = 0;
    if (o.onLost) o.onLost();
  });
  canvas.addEventListener('webglcontextrestored', () => {
    s.lost = false;
    if (o.onRestore) o.onRestore();
    s.wake();
  });
  new ResizeObserver(s.wake).observe(stage);
  new IntersectionObserver(es => { s.visible = es[0].isIntersecting; s.wake(); }).observe(stage);
  document.addEventListener('visibilitychange', s.wake);
  reduced.addEventListener('change', s.wake);
  return s;
}

/**
 * Câmera orbital compartilhada (cockpit, Monte Carlo, correlação): arrastar gira com inércia, Ctrl + roda ou botões dão zoom, duplo clique restaura,
 * setas do teclado giram, + e - dão zoom, 0 restaura; só o botão principal do mouse arrasta; a roda só zooma com Ctrl ou com o canvas focado
 * (senão o 3D roubaria a rolagem da página). Com movimento reduzido não há balanço nem inércia.
 * @param kit    o palco de createStage
 * @param canvas o canvas
 * @param o.host elemento que recebe a classe "grabbing" (padrão: o canvas)
 * @param o.target ponto para onde a câmera olha (THREE.Vector3)
 * @param o.start {az, el, dist} posição inicial
 * @param o.fit  () => distância que cabe a cena na largura (enquanto o usuário não deu zoom)
 * @param o.onHover   (evento) chamado ao mover o ponteiro sem arrastar
 * @param o.onDragStart chamado ao começar a arrastar
 * @param o.onLeave   chamado quando o ponteiro sai do canvas
 * @returns {cam, update(dt) => boolean (true enquanto a câmera se move), zoom(f), reset(), dragging()}
 */
export function createOrbit(kit, canvas, o = {}) {
  const target = o.target || new THREE.Vector3();
  const start = Object.assign({az: -0.55, el: 0.42, dist: 17}, o.start);
  const cam = Object.assign({vaz: 0, vel: 0, sway: true, fit: !!o.fit}, start);
  const host = o.host || canvas;
  let dragging = false, lastX = 0, lastY = 0;
  const EL_MIN = 0.05, EL_MAX = 1.45;
  const clampEl = v => Math.max(EL_MIN, Math.min(EL_MAX, v));

  function place() {
    const ce = Math.cos(cam.el);
    kit.camera.position.set(target.x + cam.dist * ce * Math.sin(cam.az), target.y + cam.dist * Math.sin(cam.el), target.z + cam.dist * ce * Math.cos(cam.az));
    kit.camera.lookAt(target);
  }
  const moving = () => Math.abs(cam.vaz) > 0.00001 || Math.abs(cam.vel) > 0.00001;
  function update(dt) {
    if (cam.fit && o.fit) cam.dist = o.fit();
    if (!dragging) {
      if (moving()) { cam.az += cam.vaz * dt; cam.el = clampEl(cam.el + cam.vel * dt); cam.vaz *= 0.93; cam.vel *= 0.93; }
      else if (cam.sway && !kit.reduced.matches) cam.az += 0.00016 * dt;       /* balanço lento até o primeiro toque */
    }
    place();
    return !!(cam.sway && !kit.reduced.matches) || moving() || dragging;
  }
  const zoom = f => { cam.sway = false; cam.fit = false; cam.dist = Math.max(o.minDist || 7, Math.min(o.maxDist || 40, cam.dist * f)); kit.invalidate(); };
  const reset = () => { Object.assign(cam, start, {vaz: 0, vel: 0, fit: !!o.fit}); kit.invalidate(); };

  canvas.addEventListener('pointerdown', e => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    dragging = true; lastX = e.clientX; lastY = e.clientY; cam.sway = false;
    if (o.onDragStart) o.onDragStart();
    try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* ponteiro já inativo */ }
    host.classList.add('grabbing'); kit.invalidate();
  });
  canvas.addEventListener('pointermove', e => {
    if (!dragging) { if (o.onHover) o.onHover(e); return; }
    const dx = e.clientX - lastX, dy = e.clientY - lastY;
    cam.az -= dx * 0.008; cam.el = clampEl(cam.el + dy * 0.006);
    cam.vaz = -dx * 0.008 / 16; cam.vel = dy * 0.006 / 16; lastX = e.clientX; lastY = e.clientY; kit.invalidate();
  });
  const release = e => {
    if (!dragging) return;
    dragging = false; host.classList.remove('grabbing');
    try { if (canvas.hasPointerCapture(e.pointerId)) canvas.releasePointerCapture(e.pointerId); } catch (err) { /* já solto */ }
    if (kit.reduced.matches) { cam.vaz = 0; cam.vel = 0; }
    kit.invalidate();
  };
  canvas.addEventListener('pointerup', release); canvas.addEventListener('pointercancel', release); canvas.addEventListener('lostpointercapture', release);
  canvas.addEventListener('pointerleave', () => { if (o.onLeave) o.onLeave(); });
  canvas.addEventListener('dblclick', reset);
  canvas.addEventListener('wheel', e => { if (!(e.ctrlKey || e.metaKey || document.activeElement === canvas)) return; e.preventDefault(); zoom(e.deltaY > 0 ? 1.1 : 0.9); }, {passive: false});
  canvas.addEventListener('keydown', e => {
    const step = {ArrowLeft: [0.12, 0], ArrowRight: [-0.12, 0], ArrowUp: [0, -0.08], ArrowDown: [0, 0.08]}[e.key];
    if (step) { e.preventDefault(); cam.sway = false; cam.az += step[0]; cam.el = clampEl(cam.el + step[1]); kit.invalidate(); }
    else if (e.key === '+' || e.key === '=') { e.preventDefault(); zoom(0.9); }
    else if (e.key === '-') { e.preventDefault(); zoom(1.1); }
    else if (e.key === '0' || e.key === 'Home') { e.preventDefault(); reset(); }
  });
  return {cam, update, zoom, reset, dragging: () => dragging};
}

/** Rótulo de texto como sprite (escalas dos gráficos 3D): o texto vira textura de um canvas 2D, sempre voltado para a câmera. */
export function makeLabel(text, color, size) {
  const c = document.createElement('canvas'), g = c.getContext('2d');
  const fs = 56, font = `500 ${fs * 0.5}px IBM Plex Mono, ui-monospace, monospace`;
  g.font = font; c.width = Math.ceil(g.measureText(text).width) + 16; c.height = fs;
  g.font = font; g.fillStyle = color; g.textBaseline = 'middle'; g.fillText(text, 8, fs / 2);
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace;
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({map: tex, transparent: true, depthWrite: false}));
  sp.scale.set(size * c.width / c.height, size, 1);
  return sp;
}

/** Libera da GPU tudo o que o grupo usa e o esvazia. Os buffers de matriz e de cor das instâncias só saem com o dispose do próprio InstancedMesh
 *  (medido no cockpit: sem isso, 46 buffers vazados em 12 reconstruções). */
export function disposeGroup(group) {
  group.traverse(o => {
    if (o.geometry) o.geometry.dispose();
    if (o.material) { if (o.material.map) o.material.map.dispose(); o.material.dispose(); }
    if (o.isInstancedMesh) o.dispose();
  });
  group.clear();
}
