/* Hero 3D da página inicial (item #31): medalhão ORIGINAL do MeridianWealth com o "M" da marca, em three.js servido localmente.
   É identidade visual, não dado: nenhum preço ou número aparece nele. Interação: arrastar para girar (com inércia), clique ou toque dá um
   impulso, o brilho segue o ponteiro e a rolagem da página inclina a moeda. O que é comum a toda cena 3D (tamanho, pausa fora da tela, perda
   de contexto, movimento reduzido) vem do kit3d.js. Módulo ES: não declara nenhum global. */
import {THREE, createStage} from '/kit3d.js';

const hero = document.getElementById('hero3d');
const canvas = document.getElementById('heroCanvas');
const stage = hero && hero.querySelector('.hero-stage');

function start() {
  const kit = createStage({
    stage, canvas, fov: 30, exposure: 1.15,
    onFail: () => hero.classList.add('no3d'),
    onLost: () => { hero.classList.remove('ready'); hero.classList.add('no3d'); },
    onRestore: () => { buildEnvironment(); hero.classList.remove('no3d'); hero.classList.add('ready'); },
    onFrame: step
  });
  if (!kit) return;
  const {scene, camera, renderer, reduced} = kit;
  camera.position.set(0, 0, 4.7);

  /* ambiente de estúdio: caixas de luz quentes e frias refletidas pelo metal (sem arquivo HDR de terceiros) */
  const envScene = new THREE.Scene();
  envScene.background = new THREE.Color(0x0a1424);
  const softbox = (color, w, h, x, y, z) => {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({color, side: THREE.DoubleSide}));
    m.position.set(x, y, z); m.lookAt(0, 0, 0); envScene.add(m);
  };
  softbox(new THREE.Color(10, 8.6, 6.4), 6, 3, -5, 5, 4);
  softbox(new THREE.Color(2.2, 3.2, 5.4), 3, 6, 6, 1, 3);
  softbox(new THREE.Color(3.6, 3.2, 2.8), 8, 1.4, 0, 6, -5);
  softbox(new THREE.Color(6, 4.8, 3.2), 2, 2, 2, -4, 5);
  softbox(new THREE.Color(2.6, 2.1, 1.4), 9, 9, 0.6, 0.4, 9); /* painel frontal: a face da moeda reflete ouro, não o fundo escuro */
  softbox(new THREE.Color(4.2, 2.8, 1.6), 1.2, 7, -6, -1, -2); /* luz de borda quente */
  const pmrem = new THREE.PMREMGenerator(renderer);
  let envTarget = null;
  /* o mapa de reflexo mora na GPU e se perde com o contexto: por isso é uma função, refeita ao restaurar */
  function buildEnvironment() {
    if (envTarget) envTarget.dispose();
    envTarget = pmrem.fromScene(envScene, 0.035);
    scene.environment = envTarget.texture;
  }
  buildEnvironment();

  const gold = new THREE.MeshStandardMaterial({color: 0xe3bc62, metalness: 1, roughness: 0.22});
  const goldSoft = new THREE.MeshStandardMaterial({color: 0xcfa856, metalness: 1, roughness: 0.42});
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
  const mFront = new THREE.Mesh(mGeo, gold);
  mFront.scale.setScalar(0.92); mFront.position.set(0, 0, 0.088); coin.add(mFront);
  const mBack = new THREE.Mesh(mGeo, gold);
  mBack.scale.setScalar(0.92); mBack.position.set(0, 0, -0.088); mBack.rotation.y = Math.PI; coin.add(mBack);
  scene.add(coin);

  /* luz de apoio e o brilho que segue o ponteiro */
  const key = new THREE.DirectionalLight(0xfff1d0, 1.1); key.position.set(-3, 4, 5); scene.add(key);
  scene.add(new THREE.AmbientLight(0x6f86ad, 0.25));
  const glint = new THREE.PointLight(0xffe2a8, 9, 10); glint.position.set(0, 0, 2.6); scene.add(glint);

  /* estado do movimento */
  const IDLE = 0.00035;                 /* giro lento, radianos por milissegundo */
  let ang = -0.55, vel = IDLE;          /* ângulo em Y e a velocidade atual (a inércia decai de volta ao giro lento) */
  let tilt = 0, tiltTarget = 0;         /* inclinação em X pelo arrasto */
  let px = 0, py = 0, tx = 0, ty = 0;   /* ponteiro suavizado e alvo (-1 a 1) */
  let gx = 0, gy = 0;                   /* posição suavizada do brilho */
  let scrollTilt = 0, scrollScale = 1;
  let dragging = false, moved = 0, lastX = 0, lastY = 0, lastT = 0;

  function onScroll() {
    const r = hero.getBoundingClientRect();
    const p = Math.max(0, Math.min(1, -r.top / Math.max(1, r.height)));
    scrollTilt = p * 0.55; scrollScale = 1 - p * 0.18;
  }
  /* atualiza a cena; devolve true enquanto algo se move (o kit mantém o laço) */
  function step(dt, now) {
    if (!dragging) {
      if (reduced.matches) vel *= 0.9;                 /* sem giro automático: só a inércia do que o usuário acionou */
      else vel += (IDLE - vel) * Math.min(1, dt * 0.0035);
      ang += vel * dt;
      tiltTarget *= 0.94;
    }
    tilt += (tiltTarget - tilt) * 0.12;
    px += (tx - px) * 0.07; py += (ty - py) * 0.07;
    gx += (tx - gx) * 0.14; gy += (ty - gy) * 0.14;
    coin.rotation.y = ang + px * 0.35;
    coin.rotation.x = -0.1 + py * 0.28 + tilt + scrollTilt;
    coin.position.y = reduced.matches ? 0 : Math.sin(now * 0.0008) * 0.04;
    coin.scale.setScalar(scrollScale);
    glint.position.set(gx * 2.4, -gy * 1.8, 2.6);
    return !reduced.matches || dragging || Math.abs(vel) > 0.00002 || Math.abs(tilt - tiltTarget) > 0.001 || Math.abs(tx - px) > 0.002 || Math.abs(ty - py) > 0.002;
  }

  /* arrastar para girar; clique ou toque (sem arrastar) dá um impulso */
  stage.addEventListener('pointerdown', e => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;   /* clique direito e do meio não giram a moeda (e o menu de contexto pode engolir o pointerup) */
    dragging = true; moved = 0; lastX = e.clientX; lastY = e.clientY; lastT = performance.now();
    try { stage.setPointerCapture(e.pointerId); } catch (err) { /* ponteiro já inativo: o arrasto segue pelos eventos do palco */ }
    stage.classList.add('grabbing'); hero.classList.add('touched');
    kit.invalidate();
  });
  stage.addEventListener('pointermove', e => {
    if (!dragging) return;
    const now = performance.now(), dx = e.clientX - lastX, dy = e.clientY - lastY;
    moved += Math.abs(dx) + Math.abs(dy);
    ang += dx * 0.012;
    tiltTarget = Math.max(-0.7, Math.min(0.7, tiltTarget + dy * 0.006));
    vel = Math.max(-0.02, Math.min(0.02, (dx * 0.012) / Math.max(8, now - lastT)));
    lastX = e.clientX; lastY = e.clientY; lastT = now;
    kit.invalidate();
  });
  const release = e => {
    if (!dragging) return;
    dragging = false; stage.classList.remove('grabbing');
    try { if (stage.hasPointerCapture(e.pointerId)) stage.releasePointerCapture(e.pointerId); } catch (err) { /* já solto */ }
    if (moved < 5 && e.type === 'pointerup') vel += 0.014;   /* um toque: impulso de giro (cancelar ou perder a captura não é toque) */
    kit.invalidate();
  };
  stage.addEventListener('pointerup', release);
  stage.addEventListener('pointercancel', release);
  stage.addEventListener('lostpointercapture', release);

  /* o brilho acompanha o mouse sobre o hero inteiro */
  hero.addEventListener('pointermove', e => {
    const r = stage.getBoundingClientRect();
    tx = Math.max(-1, Math.min(1, ((e.clientX - r.left) / Math.max(1, r.width)) * 2 - 1));
    ty = Math.max(-1, Math.min(1, ((e.clientY - r.top) / Math.max(1, r.height)) * 2 - 1));
    kit.invalidate();
  });
  hero.addEventListener('pointerleave', () => { tx = 0; ty = 0; kit.invalidate(); });
  addEventListener('scroll', () => { onScroll(); kit.invalidate(); }, {passive: true});
  onScroll();
  kit.wake();
  hero.classList.add('ready');
}

if (hero && canvas && stage) {
  try { start(); } catch (e) { hero.classList.add('no3d'); }
}
