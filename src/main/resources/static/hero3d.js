/* Hero 3D da página inicial (item #31): medalhão ORIGINAL do MeridianWealth com o "M" da marca, em three.js servido localmente.
   É identidade visual, não dado: nenhum preço ou número aparece nele. Inclina com o ponteiro e gira devagar; pausa fora da tela, com a aba
   oculta e com movimento reduzido (um quadro estático); sem WebGL, fica o medalhão em CSS. Módulo ES: não declara nenhum global. */
import * as THREE from '/vendor/three.module.min.js';

const hero = document.getElementById('hero3d');
const canvas = document.getElementById('heroCanvas');
const stage = hero && hero.querySelector('.hero-stage');

function start() {
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({canvas, antialias: true, alpha: true, powerPreference: 'low-power'});
  } catch (e) {
    hero.classList.add('no3d');
    return;
  }
  renderer.setClearColor(0x000000, 0);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 50);
  camera.position.set(0, 0, 5.4);

  /* ambiente de estúdio: caixas de luz quentes e frias refletidas pelo metal (sem arquivo HDR de terceiros) */
  const envScene = new THREE.Scene();
  envScene.background = new THREE.Color(0x0a1424);
  const softbox = (color, w, h, x, y, z) => {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({color, side: THREE.DoubleSide}));
    m.position.set(x, y, z); m.lookAt(0, 0, 0); envScene.add(m);
  };
  softbox(new THREE.Color(9, 8.2, 6.6), 6, 3, -5, 5, 4);
  softbox(new THREE.Color(2.2, 3.2, 5.4), 3, 6, 6, 1, 3);
  softbox(new THREE.Color(3.2, 3.2, 3.4), 8, 1.4, 0, 6, -5);
  softbox(new THREE.Color(5, 4.4, 3.4), 2, 2, 2, -4, 5);
  softbox(new THREE.Color(2.4, 2.0, 1.4), 9, 9, 0.6, 0.4, 9); /* painel frontal: a face da moeda reflete ouro, não o fundo escuro */
  const pmrem = new THREE.PMREMGenerator(renderer);
  let envTarget = null;
  /* o mapa de reflexo mora na GPU e se perde com o contexto: por isso é uma função, refeita ao restaurar */
  const buildEnvironment = () => {
    if (envTarget) envTarget.dispose();
    envTarget = pmrem.fromScene(envScene, 0.035);
    scene.environment = envTarget.texture;
  };
  buildEnvironment();

  const gold = new THREE.MeshStandardMaterial({color: 0xd9b765, metalness: 1, roughness: 0.26});
  const goldSoft = new THREE.MeshStandardMaterial({color: 0xc7a257, metalness: 1, roughness: 0.46});
  const coin = new THREE.Group();

  const body = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 0.16, 128, 1), gold);
  body.rotation.x = Math.PI / 2;
  coin.add(body);
  for (const side of [1, -1]) {
    const rim = new THREE.Mesh(new THREE.TorusGeometry(0.955, 0.04, 20, 160), gold);
    rim.position.z = 0.084 * side; coin.add(rim);
    const face = new THREE.Mesh(new THREE.CircleGeometry(0.915, 96), goldSoft);
    face.position.z = 0.0815 * side; if (side < 0) face.rotation.y = Math.PI; coin.add(face);
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.8, 0.012, 12, 160), gold);
    ring.position.z = 0.088 * side; coin.add(ring);
  }

  /* o "M": duas hastes e duas diagonais, extrudadas em relevo nas duas faces */
  const quad = pts => { const s = new THREE.Shape(); s.moveTo(pts[0][0], pts[0][1]); pts.slice(1).forEach(p => s.lineTo(p[0], p[1])); s.closePath(); return s; };
  const mShapes = [
    quad([[-0.5, -0.4], [-0.36, -0.4], [-0.36, 0.4], [-0.5, 0.4]]),
    quad([[0.36, -0.4], [0.5, -0.4], [0.5, 0.4], [0.36, 0.4]]),
    quad([[-0.5, 0.4], [-0.34, 0.4], [0.09, -0.4], [-0.07, -0.4]]),
    quad([[0.5, 0.4], [0.34, 0.4], [-0.09, -0.4], [0.07, -0.4]])
  ];
  const mGeo = new THREE.ExtrudeGeometry(mShapes, {depth: 0.045, bevelEnabled: true, bevelThickness: 0.012, bevelSize: 0.012, bevelSegments: 3});
  mGeo.translate(0, 0, 0);
  const mFront = new THREE.Mesh(mGeo, gold);
  mFront.scale.setScalar(0.92); mFront.position.set(0, 0, 0.088); coin.add(mFront);
  const mBack = new THREE.Mesh(mGeo, gold);
  mBack.scale.setScalar(0.92); mBack.position.set(0, 0, -0.088); mBack.rotation.y = Math.PI; coin.add(mBack);
  scene.add(coin);

  /* luz de apoio (o brilho vem do ambiente) */
  const key = new THREE.DirectionalLight(0xfff1d0, 1.1); key.position.set(-3, 4, 5); scene.add(key);
  scene.add(new THREE.AmbientLight(0x6f86ad, 0.25));

  let px = 0, py = 0, tx = 0, ty = 0, t0 = performance.now(), raf = 0, visible = true, lost = false;

  function size() {
    const w = Math.max(1, stage.clientWidth), h = Math.max(1, stage.clientHeight);
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
    renderer.setSize(w, h, false);
    camera.aspect = w / h; camera.updateProjectionMatrix();
  }
  function pose(t) {
    if (reduced.matches) { coin.rotation.set(-0.12, -0.55, 0); coin.position.y = 0; return; }
    px += (tx - px) * 0.06; py += (ty - py) * 0.06;
    coin.rotation.y = t * 0.00035 + px * 0.7;
    coin.rotation.x = -0.1 + py * 0.4;
    coin.position.y = Math.sin(t * 0.0008) * 0.045;
  }
  function frame(now) {
    if (lost) { raf = 0; return; }
    pose(now - t0);
    renderer.render(scene, camera);
    raf = running() ? requestAnimationFrame(frame) : 0;
  }
  const running = () => visible && !document.hidden && !reduced.matches && stage.clientWidth > 0;
  function wake() { cancelAnimationFrame(raf); raf = 0; if (lost) return; size(); pose(performance.now() - t0); renderer.render(scene, camera); if (running()) raf = requestAnimationFrame(frame); }

  /* celulares tiram o contexto WebGL quando o navegador vai para segundo plano: sem tratar, o canvas vira um retângulo branco */
  canvas.addEventListener('webglcontextlost', e => {
    e.preventDefault(); lost = true; cancelAnimationFrame(raf); raf = 0;
    hero.classList.remove('ready'); hero.classList.add('no3d');
  });
  canvas.addEventListener('webglcontextrestored', () => {
    lost = false; buildEnvironment();
    hero.classList.remove('no3d'); hero.classList.add('ready'); wake();
  });
  hero.addEventListener('pointermove', e => {
    const r = stage.getBoundingClientRect();
    tx = Math.max(-1, Math.min(1, ((e.clientX - r.left) / Math.max(1, r.width)) * 2 - 1));
    ty = Math.max(-1, Math.min(1, ((e.clientY - r.top) / Math.max(1, r.height)) * 2 - 1));
  });
  hero.addEventListener('pointerleave', () => { tx = 0; ty = 0; });
  new ResizeObserver(wake).observe(stage);
  new IntersectionObserver(es => { visible = es[0].isIntersecting; wake(); }).observe(stage);
  document.addEventListener('visibilitychange', wake);
  reduced.addEventListener('change', wake);
  wake();
  hero.classList.add('ready');
}

if (hero && canvas && stage) {
  try { start(); } catch (e) { hero.classList.add('no3d'); }
}
