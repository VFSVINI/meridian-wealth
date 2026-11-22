/* Sessão e login (item #15). Fala com /api/auth: senha, Google (ID token do Google Identity Services, validado no servidor) e demonstração.
   Depende de app.js (helpers $, esc, go).
   Nenhum token fica no armazenamento do navegador: o access token vive só na memória da aba e o refresh token está num cookie HttpOnly
   (SameSite=Strict, restrito a /api/auth) que JavaScript nenhum consegue ler, nem um injetado por XSS. O cabeçalho X-Session-Mode liga o modo
   cookie no servidor. Abas diferentes dividem o cookie: o refresh gira a cada uso e um refresh antigo derruba a família inteira, então as
   renovações passam por um Web Lock (uma aba por vez) e login e logout são avisados às outras abas por BroadcastChannel. */
const DEMO_KEY='mw-demo',HAS_KEY='mw-has-session';
const JSON_H={'Content-Type':'application/json','X-Session-Mode':'cookie'};
let SESS=null,AUTHCFG=null,GSTATE='loading',GERR='',refreshing=null,lastFocus=null;
const chan=typeof BroadcastChannel==='function'?new BroadcastChannel('mw-auth'):null;
const withLock=fn=>navigator.locks&&navigator.locks.request?navigator.locks.request('mw-refresh',fn):fn();
function mark(on){try{if(on)localStorage.setItem(HAS_KEY,'1');else localStorage.removeItem(HAS_KEY)}catch(e){}}
function hasMark(){try{return localStorage.getItem(HAS_KEY)==='1'}catch(e){return false}}
function saveSess(s){
  SESS=s;
  try{if(s&&s.demo)sessionStorage.setItem(DEMO_KEY,JSON.stringify(s));else sessionStorage.removeItem(DEMO_KEY)}catch(e){}
  if(!s||s.demo)mark(false);else mark(true);
  renderUser();document.dispatchEvent(new CustomEvent('mw-auth'));
}
function applySession(d,announce=true){
  saveSess({accessToken:d.accessToken,accessExpiresAt:d.accessExpiresAt,user:d.user,demo:!!d.demo});
  if(announce&&chan)chan.postMessage('login');
}
function refreshSession(){
  if(SESS&&SESS.demo)return Promise.resolve(false); // a demonstração não renova
  if(!refreshing){
    refreshing=withLock(async()=>{
      const r=await fetch('/api/auth/refresh',{method:'POST',headers:JSON_H});
      // só o 401 do servidor diz que a sessão acabou; rede fora do ar, 5xx e 429 são transitórios e o cookie segue válido
      if(r.status===401){const e=new Error('refresh');e.definitive=true;throw e}
      if(!r.ok)throw new Error('transient');
      applySession(await r.json(),false);return true;
    }).catch(e=>{if(e&&e.definitive){if(SESS)saveSess(null);else mark(false)}return false}).finally(()=>{refreshing=null});
  }
  return refreshing;
}
/* fetch com o token da sessão; um 401 tenta uma renovação (uma só, compartilhada) e repete a chamada; sem sucesso a sessão é encerrada. */
async function api(url,opts={}){
  const call=()=>fetch(url,{...opts,headers:{...(opts.headers||{}),...(SESS?{Authorization:'Bearer '+SESS.accessToken}:{})}});
  let r=await call();
  if(r.status===401&&SESS){
    if(await refreshSession())r=await call();
    else if(SESS&&SESS.demo)saveSess(null); // a demonstração não renova: vencida, acabou. Nos outros casos a renovação já encerrou a sessão (401) ou foi transitória (mantém)
  }
  return r;
}
const isLogged=()=>!!SESS;
/* ao abrir a página: sessão de demonstração da própria aba, ou, se já houve login neste navegador, uma renovação pelo cookie */
async function bootSession(){
  let demo=null;
  try{demo=JSON.parse(sessionStorage.getItem(DEMO_KEY)||'null')}catch(e){}
  if(demo&&Date.parse(demo.accessExpiresAt)>Date.now()){SESS=demo;renderUser();document.dispatchEvent(new CustomEvent('mw-auth'));return}
  if(hasMark())await refreshSession();
}
if(chan)chan.onmessage=e=>{
  if(e.data==='logout'&&SESS){SESS=null;try{sessionStorage.removeItem(DEMO_KEY)}catch(x){}renderUser();document.dispatchEvent(new CustomEvent('mw-auth'))}
  else if(e.data==='login'&&!SESS)refreshSession();
};
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
  const cookieSession=SESS&&!SESS.demo;
  saveSess(null);$('#userMenu').hidden=true;
  if(chan)chan.postMessage('logout');
  if(cookieSession)fetch('/api/auth/logout',{method:'POST',headers:JSON_H}).catch(()=>{});
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
      ${up?'<label class="chk"><input id="lTerms" type="checkbox"> <span>Li e aceito os <a href="#/privacidade" id="lTermsLink" target="_self">termos de uso e a política de privacidade</a></span></label>':''}
      <p class="dn" id="lErr" role="alert"></p>
      <button type="submit" class="primary" id="lGo">${up?'Criar conta':'Entrar'}</button>
    </form>
    ${c&&c.demoEnabled?'<button type="button" class="demo" id="lDemo">Explorar com sessão de demonstração <small>60 minutos, nada é guardado</small></button>':''}
    <p class="src">Ferramenta analítica e educacional. A senha vai ao servidor por HTTPS e fica guardada só como hash; nada aqui é recomendação de investimento.</p>`;
  $('#tabIn').onclick=()=>{LTAB='in';renderLogin()};
  if($('#tabUp'))$('#tabUp').onclick=()=>{LTAB='up';renderLogin()};
  $('#loginForm').addEventListener('submit',submitLogin);
  if($('#lTermsLink'))$('#lTermsLink').onclick=()=>closeLogin();
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
  const r=await fetch(path,{method:'POST',headers:JSON_H,body:body?JSON.stringify(body):undefined});
  if(!r.ok)throw new Error(r.status===429?'Muitas tentativas. Aguarde alguns minutos e tente de novo.':await errText(r,'Não foi possível concluir. Tente de novo.'));
  return r.json();
}
async function submitLogin(e){
  e.preventDefault();const err=$('#lErr'),go2=$('#lGo');err.textContent='';
  const email=$('#lEmail').value.trim(),password=$('#lPass').value,up=LTAB==='up';
  if(!email||!password){err.textContent='Informe e-mail e senha.';return}
  if(up&&!$('#lName').value.trim()){err.textContent='Informe seu nome.';return}
  if(up&&!$('#lTerms').checked){err.textContent='Para criar a conta, aceite os termos de uso e a política de privacidade.';return}
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
bootSession();

/* ---------- direitos do titular (LGPD, art. 18): exportar e excluir ---------- */
function renderMyData(){
  const el=$('#pvBody');if(!el)return;
  if(!SESS){el.className='mute';el.innerHTML='Entre para exportar ou excluir seus dados. <button type="button" class="linkish" id="pvLogin">Entrar</button>';$('#pvLogin').onclick=openLogin;return}
  el.className='';
  el.innerHTML=`<p class="src">Conta: <b>${esc(SESS.user.email)}</b>${SESS.demo?' (demonstração: some sozinha em 60 minutos)':''}</p>
    <h3 class="sub">Exportar</h3><p class="src">Baixe tudo que guardamos de você em JSON (conta, perfil, posições e livro). Não inclui senha nem tokens.</p>
    <button type="button" id="pvExport">Baixar meus dados</button> <span id="pvExpMsg" class="src" role="status"></span>
    <h3 class="sub">Excluir a conta</h3><p class="src">Apaga a conta, as posições, o livro e as sessões, agora e sem volta. ${SESS.demo?'':'Digite o e-mail da conta para confirmar.'}</p>
    ${SESS.demo?'':'<label class="blk">E-mail da conta <input id="pvEmail" type="email" autocomplete="off"></label>'}
    <button type="button" class="danger" id="pvErase">Excluir minha conta</button> <span id="pvEraseMsg" class="dn" role="alert"></span>`;
  $('#pvExport').onclick=async()=>{
    const m=$('#pvExpMsg');m.textContent='Preparando…';
    try{
      const r=await api('/api/me/export');if(!r.ok)throw new Error(await errText(r,'Não foi possível exportar agora.'));
      const url=URL.createObjectURL(await r.blob()),a=document.createElement('a');a.href=url;a.download='meridianwealth-meus-dados.json';document.body.appendChild(a);a.click();a.remove();URL.revokeObjectURL(url);
      m.className='up';m.textContent='Arquivo baixado.';
    }catch(x){m.className='dn';m.textContent=x.message}
  };
  $('#pvErase').onclick=async()=>{
    const m=$('#pvEraseMsg');m.textContent='';
    const email=SESS.demo?'':($('#pvEmail').value||'').trim();
    if(!SESS.demo&&!email){m.textContent='Digite o e-mail da conta.';return}
    if(!confirm('Excluir a conta e todos os dados, sem volta?'))return;
    try{
      const r=await api('/api/me/erase',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({confirmEmail:email})});
      if(!r.ok)throw new Error(await errText(r,'Não foi possível excluir agora.'));
      await logout();go('home');
    }catch(x){m.textContent=x.message}
  };
}
document.addEventListener('mw-auth',renderMyData);
renderMyData();
