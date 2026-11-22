/* Sessão e login (item #15). Fala com /api/auth: senha, Google (ID token do Google Identity Services, validado no servidor) e demonstração.
   Depende de app.js (helpers $, esc, go). O refresh token fica no armazenamento local (dívida 34 do PROJETO.md); a CSP estrita é a defesa contra XSS. */
const SESSION_KEY='mw-session';
let SESS=null,AUTHCFG=null,GSTATE='loading',GERR='',refreshing=null,lastFocus=null;
try{SESS=JSON.parse(localStorage.getItem(SESSION_KEY)||'null')}catch(e){SESS=null}
function saveSess(s){
  SESS=s;
  try{if(s)localStorage.setItem(SESSION_KEY,JSON.stringify(s));else localStorage.removeItem(SESSION_KEY)}catch(e){}
  renderUser();document.dispatchEvent(new CustomEvent('mw-auth'));
}
function applySession(d){saveSess({accessToken:d.accessToken,accessExpiresAt:d.accessExpiresAt,refreshToken:d.refreshToken||null,user:d.user,demo:!!d.demo})}
/* Outra aba pode ter renovado: o refresh gira a cada uso e usar um antigo derruba a família inteira (detecção de reuso). Antes de renovar,
   e ao receber um 401, adota-se a sessão mais nova do armazenamento; o evento "storage" mantém as abas em sincronia, inclusive no logout. */
function adoptStored(){
  let s=null;
  try{s=JSON.parse(localStorage.getItem(SESSION_KEY)||'null')}catch(e){return false}
  const same=(!s&&!SESS)||(s&&SESS&&s.accessToken===SESS.accessToken&&s.refreshToken===SESS.refreshToken);
  if(same)return false;
  SESS=s;renderUser();document.dispatchEvent(new CustomEvent('mw-auth'));return true;
}
addEventListener('storage',e=>{if(e.key===SESSION_KEY||e.key===null)adoptStored()});
async function refreshSession(){
  adoptStored();
  if(!SESS||!SESS.refreshToken)return false;
  if(!refreshing){
    refreshing=fetch('/api/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:SESS.refreshToken})})
      .then(async r=>{if(!r.ok)throw new Error('refresh');applySession(await r.json());return true})
      .catch(()=>{saveSess(null);return false}).finally(()=>{refreshing=null});
  }
  return refreshing;
}
/* fetch com o token da sessão; um 401 tenta uma renovação (uma só, compartilhada) e repete a chamada; sem sucesso a sessão é encerrada. */
async function api(url,opts={}){
  const call=()=>fetch(url,{...opts,headers:{...(opts.headers||{}),...(SESS?{Authorization:'Bearer '+SESS.accessToken}:{})}});
  let r=await call();
  if(r.status===401&&SESS){
    const before=SESS&&SESS.accessToken;
    if(adoptStored()&&SESS&&SESS.accessToken!==before)r=await call(); // outra aba já renovou: usa o token novo, sem gastar o refresh
    if(r.status===401&&SESS&&await refreshSession())r=await call();
  }
  if(r.status===401&&SESS)saveSess(null);
  return r;
}
const isLogged=()=>!!SESS;
async function errText(r,fallback){try{const d=await r.json();return d.error||fallback}catch(e){return fallback}}

/* ---------- botão da barra e menu do usuário ---------- */
function renderUser(){
  const b=$('#userBtn');if(!b)return;
  if(!SESS){b.className='ghost';b.innerHTML='Entrar';b.setAttribute('aria-label','Entrar ou criar conta');b.setAttribute('aria-expanded','false');$('#userMenu').hidden=true;return}
  const u=SESS.user,name=u.displayName||u.email||'Conta',ini=(name.trim()[0]||'?').toUpperCase();
  b.className='ghost user';b.setAttribute('aria-label','Menu da conta de '+name);
  b.innerHTML=`<span class="av" aria-hidden="true">${esc(ini)}</span><span class="un">${esc(name)}${SESS.demo?'<small>demonstração</small>':u.profile?'<small>'+esc(PROFILE_LABEL[u.profile]||'')+'</small>':'<small>sem perfil</small>'}</span>`;
}
const PROFILE_LABEL={CONSERVATIVE:'conservador',MODERATE:'moderado',BOLD:'arrojado',AGGRESSIVE:'agressivo'};
function toggleUserMenu(){
  const m=$('#userMenu'),b=$('#userBtn');
  if(!SESS){openLogin();return}
  const open=m.hidden;
  if(open){
    const u=SESS.user;
    m.innerHTML=`<div class="who"><b>${esc(u.displayName||'Conta')}</b><span class="mute">${esc(SESS.demo?'Sessão de demonstração (60 minutos, não é guardada)':u.email||'')}</span></div>
      <a href="#/carteira">Minha Carteira</a><a href="#/perfil">Perfil de investidor</a><button type="button" id="logoutBtn">Sair</button>`;
    m.hidden=false;b.setAttribute('aria-expanded','true');
    $('#logoutBtn').onclick=logout;
    m.querySelectorAll('a').forEach(a=>a.addEventListener('click',()=>toggleUserMenu()));
    (m.querySelector('a')||m).focus?.();
  }else{m.hidden=true;b.setAttribute('aria-expanded','false')}
}
async function logout(){
  const rt=SESS&&SESS.refreshToken;
  saveSess(null);$('#userMenu').hidden=true;
  if(rt)fetch('/api/auth/logout',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:rt})}).catch(()=>{});
  if(location.hash.startsWith('#/carteira'))renderUser();
}
document.addEventListener('click',e=>{const m=$('#userMenu');if(m&&!m.hidden&&!e.target.closest('#userMenu')&&!e.target.closest('#userBtn')){m.hidden=true;$('#userBtn').setAttribute('aria-expanded','false')}});
addEventListener('keydown',e=>{const m=$('#userMenu');if(e.key==='Escape'&&m&&!m.hidden){m.hidden=true;$('#userBtn').setAttribute('aria-expanded','false');$('#userBtn').focus()}});

/* ---------- diálogo de entrada ---------- */
let LTAB='in';
async function loadAuthConfig(){
  if(AUTHCFG)return AUTHCFG;
  try{const r=await fetch('/api/auth/config');if(!r.ok)throw new Error('config');AUTHCFG=await r.json();return AUTHCFG}
  catch(e){AUTHCFG=null;return null}
}
function googleButtonHtml(){
  const G='<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="#4285F4" d="M23 12.3c0-.8-.1-1.6-.2-2.3H12v4.4h6.2a5.3 5.3 0 0 1-2.3 3.5v2.9h3.7c2.2-2 3.4-5 3.4-8.5z"/><path fill="#34A853" d="M12 24c3.1 0 5.7-1 7.6-2.8l-3.7-2.9c-1 .7-2.3 1.1-3.9 1.1-3 0-5.5-2-6.4-4.8H1.8v3A12 12 0 0 0 12 24z"/><path fill="#FBBC05" d="M5.6 14.6a7.2 7.2 0 0 1 0-4.6V7H1.8a12 12 0 0 0 0 10.8z"/><path fill="#EA4335" d="M12 4.8c1.7 0 3.2.6 4.4 1.7l3.3-3.3A11.5 11.5 0 0 0 12 0 12 12 0 0 0 1.8 7l3.8 3c.9-2.8 3.4-5.2 6.4-5.2z"/></svg>';
  const txt={loading:'Carregando o login do Google…',off:'Continuar com Google',error:'Continuar com Google'}[GSTATE];
  if(GSTATE==='on')return '<div id="gBtn" class="gbtn-host" aria-label="Continuar com Google"></div>';
  return `<button type="button" class="gbtn" id="gBtnOff" disabled aria-disabled="true">${G}<span>${txt}</span></button>`
    +(GSTATE==='off'?'<p class="gnote" role="note">O login com Google não está configurado neste ambiente (falta <code>MW_GOOGLE_CLIENT_ID</code>). Use e-mail e senha ou a sessão de demonstração.</p>':'')
    +(GSTATE==='error'?`<p class="gnote err" role="alert">${esc(GERR)} <button type="button" class="linkish" id="gRetry">Tentar de novo</button></p>`:'');
}
function renderLogin(){
  const c=AUTHCFG,reg=c&&c.registrationEnabled,up=LTAB==='up';
  $('#loginBody').innerHTML=`
    <div class="tabs" role="tablist" aria-label="Entrar ou criar conta">
      <button type="button" role="tab" id="tabIn" aria-selected="${!up}" aria-controls="loginForm">Entrar</button>
      ${reg?`<button type="button" role="tab" id="tabUp" aria-selected="${up}" aria-controls="loginForm">Criar conta</button>`:''}
    </div>
    <div class="gwrap" id="gWrap">${googleButtonHtml()}</div>
    <div class="or" aria-hidden="true"><span>ou</span></div>
    <form id="loginForm" novalidate>
      ${up?'<label>Nome <input id="lName" autocomplete="name" maxlength="80" required></label>':''}
      <label>E-mail <input id="lEmail" type="email" autocomplete="email" maxlength="254" required></label>
      <label>Senha <input id="lPass" type="password" autocomplete="${up?'new-password':'current-password'}" maxlength="128" required aria-describedby="lHint"></label>
      <p class="src" id="lHint">${up&&c?esc(c.passwordHint):'&nbsp;'}</p>
      <p class="dn" id="lErr" role="alert"></p>
      <button type="submit" class="primary" id="lGo">${up?'Criar conta':'Entrar'}</button>
    </form>
    ${c&&c.demoEnabled?'<button type="button" class="demo" id="lDemo">Explorar com sessão de demonstração <small>60 minutos, nada é guardado</small></button>':''}
    <p class="src">Ferramenta analítica e educacional. A senha vai ao servidor por HTTPS e fica guardada só como hash; nada aqui é recomendação de investimento.</p>`;
  $('#tabIn').onclick=()=>{LTAB='in';renderLogin()};
  if($('#tabUp'))$('#tabUp').onclick=()=>{LTAB='up';renderLogin()};
  $('#loginForm').addEventListener('submit',submitLogin);
  if($('#lDemo'))$('#lDemo').onclick=startDemo;
  if($('#gRetry'))$('#gRetry').onclick=()=>{GSTATE='loading';renderLogin();initGoogle()};
  if(GSTATE==='on')mountGoogle();
}
async function openLogin(){
  const dlg=$('#login');lastFocus=document.activeElement;
  if(!dlg.open)dlg.showModal();
  renderLogin();
  await loadAuthConfig();
  if(!AUTHCFG){GSTATE='error';GERR='Não foi possível consultar a configuração de login.'}
  else if(!AUTHCFG.googleLoginEnabled||!AUTHCFG.googleClientId)GSTATE='off';
  else if(GSTATE==='loading'||GSTATE==='off')GSTATE=window.google&&google.accounts&&google.accounts.id?'on':'loading';
  renderLogin();
  if(AUTHCFG&&AUTHCFG.googleLoginEnabled&&GSTATE==='loading')initGoogle();
  const f=$('#lEmail');if(f)f.focus();
}
function closeLogin(){const d=$('#login');if(d.open)d.close();if(lastFocus&&lastFocus.focus)lastFocus.focus()}
async function afterLogin(d){
  applySession(d);closeLogin();
  if(d.needsSuitability&&!d.demo){go('perfil');return}
  if(!location.hash||location.hash==='#/home'||location.hash==='#/')go('carteira');
}
async function postAuth(path,body){
  const r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});
  if(!r.ok)throw new Error(r.status===429?'Muitas tentativas. Aguarde alguns minutos e tente de novo.':await errText(r,'Não foi possível concluir. Tente de novo.'));
  return r.json();
}
async function submitLogin(e){
  e.preventDefault();const err=$('#lErr'),go2=$('#lGo');err.textContent='';
  const email=$('#lEmail').value.trim(),password=$('#lPass').value,up=LTAB==='up';
  if(!email||!password){err.textContent='Informe e-mail e senha.';return}
  if(up&&!$('#lName').value.trim()){err.textContent='Informe seu nome.';return}
  go2.disabled=true;go2.textContent=up?'Criando…':'Entrando…';
  try{
    const d=await postAuth(up?'/api/auth/register':'/api/auth/login',up?{email,password,displayName:$('#lName').value.trim()}:{email,password});
    await afterLogin(d);
  }catch(x){err.textContent=x.message;go2.disabled=false;go2.textContent=up?'Criar conta':'Entrar'}
}
async function startDemo(){
  const b=$('#lDemo'),err=$('#lErr');b.disabled=true;
  try{const d=await postAuth('/api/auth/demo');await afterLogin(d)}catch(x){err.textContent=x.message;b.disabled=false}
}
/* ---------- Google Identity Services (só quando o servidor tem MW_GOOGLE_CLIENT_ID; a CSP libera o domínio nesse caso) ---------- */
function initGoogle(){
  if(window.google&&google.accounts&&google.accounts.id){GSTATE='on';renderLogin();return}
  const s=document.createElement('script');s.src='https://accounts.google.com/gsi/client';s.async=true;
  s.onload=()=>{GSTATE='on';renderLogin()};
  s.onerror=()=>{GSTATE='error';GERR='Não foi possível carregar o login do Google (rede ou bloqueador).';renderLogin()};
  document.head.appendChild(s);
}
function mountGoogle(){
  const host=$('#gBtn');if(!host||!window.google)return;
  google.accounts.id.initialize({client_id:AUTHCFG.googleClientId,callback:onGoogleCredential,ux_mode:'popup',auto_select:false});
  google.accounts.id.renderButton(host,{type:'standard',theme:'filled_black',size:'large',text:'continue_with',shape:'pill',locale:'pt-BR',width:Math.min(340,host.clientWidth||340)});
}
async function onGoogleCredential(resp){
  const err=$('#lErr');if(err)err.textContent='';
  try{const d=await postAuth('/api/auth/google',{idToken:resp.credential});await afterLogin(d)}
  catch(x){if(err)err.textContent=x.message}
}
/* ---------- "montador de perfil": salvar o resultado do questionário no perfil da conta ---------- */
async function saveProfile(profile){
  const r=await api('/api/me/profile',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({profile})});
  if(!r.ok)throw new Error(await errText(r,'Não foi possível salvar o perfil.'));
  const u=await r.json();SESS={...SESS,user:u};saveSess(SESS);return u;
}
function offerSaveProfile(profile){
  const el=$('#spSave');if(!el)return;
  if(!SESS){el.innerHTML='<div class="src">Entre na sua conta para guardar este perfil e ver a aderência da sua carteira. <button type="button" class="linkish" id="spLogin">Entrar</button></div>';$('#spLogin').onclick=openLogin;return}
  el.innerHTML=`<button type="button" class="primary" id="spSaveBtn">Salvar como meu perfil</button> <span id="spSaveMsg" class="src" role="status"></span>`;
  $('#spSaveBtn').onclick=async()=>{
    const m=$('#spSaveMsg');m.textContent='Salvando…';
    try{await saveProfile(profile);m.className='up';m.innerHTML='Perfil salvo. <a href="#/carteira">Ver a aderência da minha carteira</a>'}catch(x){m.className='dn';m.textContent=x.message}
  };
}
function profileBanner(){
  const el=$('#spBanner');if(!el)return;
  el.hidden=!(SESS&&!SESS.demo&&SESS.user&&!SESS.user.profile);
}
document.addEventListener('mw-auth',()=>{profileBanner();if($('#spSave')&&typeof SPPROFILE!=='undefined'&&SPPROFILE)offerSaveProfile(SPPROFILE)});
$('#userBtn').onclick=toggleUserMenu;
$('#loginX').onclick=closeLogin;
$('#login').addEventListener('click',e=>{if(e.target===$('#login'))closeLogin()});
$('#login').addEventListener('close',()=>{if(lastFocus&&lastFocus.focus)try{lastFocus.focus()}catch(e){}});
renderUser();profileBanner();
/* sessão guardada: confirma no servidor (e renova se preciso) para não mostrar uma conta que já expirou */
if(SESS){api('/api/me').then(async r=>{if(r.ok){const u=await r.json();SESS={...SESS,user:u};saveSess(SESS)}}).catch(()=>{})}
