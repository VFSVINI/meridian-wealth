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
