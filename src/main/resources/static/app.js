const $=s=>document.querySelector(s);
const f=(v,d=2)=>Number(v).toLocaleString('pt-BR',{minimumFractionDigits:d,maximumFractionDigits:d});
const pc=(v,d=1)=>f(v*100,d)+'%';
const SIG={BUY_DIP_WINDOW:['Janela de compra','up'],ACCUMULATE:['Acumular','up'],HOLD_RIDE_TREND:['Manter tendência','ice'],TAKE_PROFIT_WINDOW:['Realizar lucro','warn'],SELL_EXIT_SETUP:['Reduzir / sair','dn'],HIGH_RISK_WAIT:['Aguardar','mute']};
const PHASE={ACUMULACAO:'Acumulação',TENDENCIA_ATIVA:'Tendência ativa',ESTICADO_REALIZAR:'Esticado: realizar lucro',CAUTELA_EVITAR:'Cautela: evitar'};
const REGIME={UPTREND:'Tendência de alta',DOWNTREND:'Tendência de baixa',RANGE:'Lateralização',STRESS:'Estresse / alta volatilidade'};
const RISK={LOW:'Baixo',MEDIUM:'Moderado',HIGH:'Alto',EXTREME:'Extremo'};
const SIDE={COMPRA:'var(--up)',VENDA:'var(--warn)',ALERTA:'var(--dn)'};
const FRESH={REAL_TIME:['tempo real','up'],DELAYED:['~15 min','warn'],END_OF_DAY:['fechamento','ice'],STORED:['último dado real gravado','dn']};
let universe=[],rows={},sel=null,candles=[],A=null,es=null,ws=null,paused=false,selecting=false;

Object.entries(SIG).forEach(([k,v])=>$('#sigf').insertAdjacentHTML('beforeend',`<option value="${k}">${v[0]}</option>`));

async function j(u){const r=await fetch(u);if(!r.ok)throw new Error(u);return r.json()}
const dt=ms=>new Date(ms).toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'});
const tm=ms=>new Date(ms).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit',second:'2-digit'});
const hasData=r=>r&&r.status!=='UNAVAILABLE'&&r.candle;
function freshChip(r){const x=FRESH[r.freshness];return x?`<span class="fresh ${x[1]}" title="Atraso da fonte">${x[0]}</span>`:''}
const isStale=r=>!!r.stale;
const dayOnly=ms=>new Date(ms).toLocaleDateString('pt-BR',{day:'2-digit',month:'2-digit'});
function quoteWhen(r){return r.status==='STORED'?'em '+dt(r.quoteTime):r.freshness==='END_OF_DAY'?'fechamento de '+dayOnly(r.quoteTime):tm(r.quoteTime)}

async function init(){
  universe=await j('/api/universe');buildPickers();
  tryRenderSimulator();tryRenderLens();
  const ov=await j('/api/overview');
  ov.ticks.forEach(t=>rows[t.symbol]={...t,flash:''});
  renderMode(ov);renderMacro(ov.macro);renderHome();
  const first=universe.find(u=>hasData(rows[u.symbol]))||universe[0];
  await select(first.symbol);
  connect();
}

function connect(){
  if(paused)return;
  try{
    ws=new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws/stream');
    let opened=false;
    ws.onopen=()=>{opened=true;setLive('on','ao vivo · WebSocket')};
    ws.onmessage=e=>onTick(JSON.parse(e.data));
    ws.onclose=()=>{ws=null;if(!paused)startSSE()};
    ws.onerror=()=>{if(!opened)setLive('off','tentando SSE')};
  }catch(e){startSSE()}
}
function startSSE(){
  if(es||paused)return;
  es=new EventSource('/api/stream');
  es.onopen=()=>setLive('on','ao vivo · SSE');
  es.onerror=()=>setLive('off','reconectando');
  es.addEventListener('tick',e=>onTick(JSON.parse(e.data)));
}
function stopStream(){if(ws){ws.onclose=null;ws.close();ws=null}if(es){es.close();es=null}}
function setLive(c,t){$('#live').className='live '+c;$('#liveTxt').textContent=t}

function renderMode(b){
  const missing=b.ticks.filter(t=>t.status!=='LIVE').length;
  const M={REAL:'dados reais',PARCIAL:`dados reais · ${missing} sem cotação ao vivo`,SEM_DADOS:'sem dado em tempo real'};
  $('#mode').textContent=M[b.mode]||b.mode;
}

function onTick(b){
  renderMode(b);renderMacro(b.macro);
  let fresh=false;
  b.ticks.forEach(t=>{
    const old=rows[t.symbol];
    const newQuote=!!(old&&hasData(t)&&old.quoteTime!==t.quoteTime);
    if(newQuote)fresh=true;
    const dir=newQuote&&old.candle?(t.candle.close>old.candle.close?'flash-up':t.candle.close<old.candle.close?'flash-dn':''):'';
    rows[t.symbol]={...t,flash:dir};
    if(t.symbol===sel){
      if(!hasData(t)){candles=[];A=null}
      else if(!candles.length){if(!selecting)select(sel)}
      else{if(t.newBar)candles.push(t.candle);else candles[candles.length-1]=t.candle;if(candles.length>500)candles.shift();A=t.assessment}
    }
  });
  if(fresh){const l=$('#live');l.classList.remove('beat');void l.offsetWidth;l.classList.add('beat')}
  renderList();renderDetail();draw();renderHome();
}

function renderList(){
  const rg=$('#region').value,sg=$('#sigf').value,q=$('#q').value.trim().toLowerCase();
  const html=universe.filter(u=>{const r=rows[u.symbol];return (!rg||u.region===rg)&&r&&(!sg||(r.assessment&&r.assessment.signal===sg))&&(!q||(u.symbol+u.name).toLowerCase().includes(q))})
   .map(u=>{const r=rows[u.symbol];
    if(!hasData(r))return `<tr class="row ${u.symbol===sel?'sel':''}" data-s="${u.symbol}"><td><b class="mono">${u.symbol}</b><div class="src"><i class="dot ${r.marketOpen?'on':''}"></i>sem fonte</div></td>
      <td colspan="3" class="mute" style="font-size:12px">sem dado em tempo real</td></tr>`;
    const c=r.candle,chg=c.close/c.open-1,s=r.assessment?SIG[r.assessment.signal]:null;
    const when=quoteWhen(r);
    return `<tr class="row ${u.symbol===sel?'sel':''}" data-s="${u.symbol}"><td><b class="mono">${u.symbol}</b><div class="src"><i class="dot ${r.marketOpen?'on':''}"></i>${r.source} · ${when}</div><div>${freshChip(r)}${isStale(r)?' <span class="fresh dn">desatualizada</span>':''}</div></td>
    <td class="n ${r.flash}">${f(c.close,c.close<10?4:2)}<div class="src">${u.currency||''}</div></td><td class="n ${chg>=0?'up':'dn'}">${chg>=0?'+':''}${f(chg*100)}%</td>
    <td>${s?`<span class="chip ${s[1]}">${s[0]}</span><div class="num mute" style="font-size:11px">${r.assessment.score>0?'+':''}${r.assessment.score}</div>`:'<span class="mute" style="font-size:11px">dados insuficientes</span>'}</td></tr>`}).join('');
  $('#list').innerHTML=html||'<tr><td colspan="4" class="mute">Nenhum ativo com esses filtros.</td></tr>';
}

function renderProv(){
  const r=rows[sel];if(!r){$('#hProv').innerHTML='';return}
  if(!hasData(r)){$('#hProv').innerHTML='<span class="fresh dn">sem dado em tempo real</span><span>nenhum provedor respondeu e não há dado real gravado</span>';return}
  const streaming=/ WS$/.test(r.source||'');
  const next=!streaming&&r.nextUpdateAt?Math.max(0,Math.round((r.nextUpdateAt-Date.now())/1000)):null;
  const when=r.status==='STORED'?`último dado real em <b>${dt(r.quoteTime)}</b>`:r.freshness==='END_OF_DAY'?`<b>${quoteWhen(r)}</b>`:`cotação de <b>${tm(r.quoteTime)}</b>`;
  $('#hProv').innerHTML=`${freshChip(r)}<span>fonte <b>${r.source}</b></span><span>${when}</span>`
   +(r.status==='STORED'?'<span class="dn">sem dado em tempo real</span>':'')
   +(isStale(r)?'<span class="dn">cotação desatualizada: o provedor não respondeu nas últimas consultas</span>':'')
   +(streaming?'<span>stream ao vivo: cada preço chega assim que a bolsa o publica</span>':'')
   +(next!=null&&!paused?`<span>próxima atualização em <b class="num">${next}s</b></span>`:'')
   +`<span>pregão ${r.marketOpen?'aberto':'fechado'}</span>`;
}
setInterval(renderProv,1000);
$('#list').addEventListener('click',e=>{const tr=e.target.closest('tr[data-s]');if(tr){select(tr.dataset.s);go('cockpit')}});
['region','sigf','q'].forEach(id=>$('#'+id).addEventListener('input',renderList));

async function select(sym){
  sel=sym;selecting=true;syncPick();
  const r=rows[sym];
  try{
    if(hasData(r)){const [c,a]=await Promise.all([j(`/api/candles/${sym}?limit=400`),j(`/api/assessment/${sym}`).catch(()=>null)]);if(sel!==sym)return;candles=c;A=a}
    else{candles=[];A=null}
  }finally{selecting=false}
  $('#btOut').innerHTML='<span class="mute">Rode o backtest para este ativo.</span>';
  renderList();renderDetail();draw();loadMicro();loadSignals();loadComposition(sym);loadReports(sym);
}

async function loadMicro(){
  const m=hasData(rows[sel])?await j('/api/micro/'+sel).catch(()=>null):null;
  if(!m){$('#micro').innerHTML='<div style="grid-column:1/-1;border:0" class="mute">Sem dado real suficiente para este ativo.</div>';return}
  if(m.symbol!==sel)return;
  const fd=m.fundamentals,v=(x,fn)=>x==null?'n/d':fn(x);
  const rows2=[['Setor',m.sector],['Benchmark',m.benchmark],['Força relativa (60)',(m.relStrength>=0?'+':'')+f(m.relStrength*100)+' pp'],['Volume relativo',f(m.relVolume)+'x'],
   ['Valor de mercado',fd?v(fd.marketCap,x=>f(x/1e9,1)+' bi'):'n/d'],['P/L',fd?v(fd.peRatio,x=>f(x,1)):'n/d'],['Margem líquida',fd?v(fd.netMargin,x=>f(x,1)+'%'):'n/d'],['Cresc. receita',fd?v(fd.revenueGrowth,x=>f(x,1)+'%'):'n/d']];
  $('#micro').innerHTML=rows2.map(([k,x])=>`<div><span>${k}${GLL(k)}</span><span class="num">${x}</span></div>`).join('')+(fd?'':'<div style="grid-column:1/-1;border:0" class="mute">Fundamentos exigem FINNHUB_API_KEY e ação dos EUA.</div>');
}
async function loadSignals(){
  const h=await j('/api/signals/'+sel+'?limit=8').catch(()=>[]);
  $('#sigHist').innerHTML=h.length?h.map(x=>`<div style="display:flex;justify-content:space-between"><span>${new Date(x.ts).toLocaleTimeString('pt-BR')}</span><span class="${SIG[x.signal][1]}">${SIG[x.signal][0]}</span><span class="num">${x.score>0?'+':''}${x.score}</span></div>`).join(''):'Sem histórico (persistência ligada só no Docker Compose).';
}
const esc=t=>String(t??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const KIND={GALPAO_LOGISTICO:'Galpão logístico',EDIFICIO_COMERCIAL:'Edifício comercial'};
const dash=(v,fmt)=>v==null?'<span class="gap">não informado</span>':fmt(v);
function rxAsset(a){
  const c=a.coordinates;
  const links=c?`<a href="${esc(c.satelliteUrl)}" target="_blank" rel="noopener noreferrer">Satélite</a> · <a href="${esc(c.streetViewUrl)}" target="_blank" rel="noopener noreferrer">Street View</a>`
      +(c.approximate?'<div class="mute" title="'+esc(c.matchedAddress)+'">posição aproximada (via)</div>':'<div class="mute" title="'+esc(c.matchedAddress)+'">endereço localizado</div>')
    :`<span class="gap">não informado</span><div class="mute">${esc(a.noCoordinatesReason)}</div>`;
  const sectors=a.tenantSectors.length?a.tenantSectors.map(t=>esc(t.setor||t.sector)+' '+f(t.percentOfPropertyRevenue,1)+'%').join('; '):'<span class="gap">não informado</span>';
  return `<tr><td><b>${esc(a.name)}</b><div class="mute">${esc([a.city,a.uf].filter(Boolean).join(' / '))||'cidade não informada'}${a.kind?' · '+esc(KIND[a.kind]||a.kind):''}</div><div class="mute">${sectors}</div></td>
    <td class="n">${f(a.areaM2,0)} m²<div class="mute">${f(a.areaSharePercent,2)}% da lista</div></td>
    <td class="n">${dash(a.vacancyPercent,v=>f(v,2)+'%')}</td><td>${links}</td></tr>`;
}
async function loadComposition(sym){
  const el=$('#rx');
  try{
    const r=await fetch('/api/composition/'+encodeURIComponent(sym));
    if(sel!==sym)return;
    if(r.status===404){el.innerHTML='<span class="mute">Sem composição publicada para '+esc(sym)+': só há dados curados com fonte para alguns fundos imobiliários (HGLG11, KNRI11, XPML11, MXRF11).</span>';return}
    if(!r.ok)throw new Error('composition');
    const d=await r.json();if(sel!==sym)return;
    const s=d.source;
    let h=d.stale?`<div class="stale">Dado consultado há ${d.dataAgeDays} dias (limite ${d.staleAfterDays}): pode estar desatualizado.</div>`:'';
    h+=`<div class="src"><b>${esc(s.kindLabel)}</b>: <a href="${esc(s.url)}" target="_blank" rel="noopener noreferrer">${esc(s.name)}</a> · dados de ${esc(s.referenceDate||'data não informada')} · consultado em ${esc(s.consultedOn)}</div>`;
    if(d.classification)h+=`<div class="src">Classificação no informe: ${esc(d.classification)}</div>`;
    if(d.metrics){const m=d.metrics;h+=`<div class="kv"><div><span>ABL publicada${GL('abl')}</span><span>${dash(m.totalAreaM2,v=>f(v,0)+' m²')}</span></div><div><span>Vacância física${GL('vacancy')}</span><span>${dash(m.physicalVacancyPercent,v=>f(v,2)+'%')}</span></div><div><span>Vacância financeira</span><span>${dash(m.financialVacancyPercent,v=>f(v,2)+'%')}</span></div><div><span>Ref. das métricas</span><span>${esc(m.asOf||'—')}</span></div></div>`}
    if(d.assets.length){
      h+=`<div class="src" style="margin-top:8px">${d.listedAssetCount} imóveis listados${d.declaredAssetCount!=null&&d.declaredAssetCount!==d.listedAssetCount?' de '+d.declaredAssetCount+' declarados':''} · ${d.geolocatedCount} com coordenada · ${esc(d.areaLabel||'Área')}: ${f(d.listedAreaM2,0)} m² (soma da lista)</div>`;
      h+=`<div class="scroll"><table><thead><tr><th>Imóvel</th><th class="n">Área</th><th class="n">Vacância${GL('vacancy')}</th><th>Mapa</th></tr></thead><tbody>${d.assets.map(rxAsset).join('')}</tbody></table></div>`;
    }
    if(d.allocations.length){
      h+=`<table style="margin-top:8px"><thead><tr><th>Alocação</th><th class="n">R$</th><th class="n">% do ativo</th></tr></thead><tbody>${d.allocations.map(a=>`<tr><td>${esc(a.label)}</td><td class="n">${f(a.valueBrl,2)}</td><td class="n">${f(a.percentOfAssets,2)}%</td></tr>`).join('')}</tbody></table>`;
    }
    if(d.notices.length)h+='<ul class="note">'+d.notices.map(n=>'<li>'+esc(n)+'</li>').join('')+'</ul>';
    h+=`<div class="src" style="margin-top:8px">${esc(d.attribution)} Coordenadas por via são aproximadas. ${esc(d.disclaimer)}</div>`;
    el.className='';el.style.fontSize='12px';el.innerHTML=h;
  }catch(e){if(sel===sym){el.className='mute';el.textContent='Composição indisponível agora.'}}
}
const VD={POSITIVE:'POSITIVO',NEUTRAL:'NEUTRO',NEGATIVE:'NEGATIVO'};
const MV=(u,v)=>v==null?'<span class="gap">não informado</span>':u==='BRL'?'R$ '+f(v,2):f(v,6);
function rrReport(r){
  const vd=r.verdict||'NONE';
  const sig=r.signals.map(s=>`<li>${esc(s.explanation)}${s.variationPercent==null?'':` <span class="mute">[${esc(s.label)}: ${MV(s.unit,s.current)} (${esc(s.currentPeriod)}) × ${MV(s.unit,s.base)} (${esc(s.basePeriod||'—')})]</span>`}</li>`).join('');
  const cav=r.caveats.map(c=>'<li>'+esc(c)+'</li>').join('');
  const link=r.link?` · <a href="${esc(r.link)}" target="_blank" rel="noopener noreferrer">documento na CVM</a>`:'';
  return `<details><summary><b>${esc(r.label)}</b><span class="vd ${vd}">${esc(r.verdictLabel)}</span><span class="mute">entregue em ${esc(r.firstDeliveredOn)}${r.version>1?' (reentregue, versão '+r.version+')':''}</span></summary>
    <ul class="note">${sig}${cav}</ul><div class="src">${esc(r.periodicityLabel)} · período até ${esc(r.periodEnd)}${link}</div></details>`;
}
async function loadReports(sym){
  const el=$('#rr');
  try{
    const r=await fetch('/api/reports/'+encodeURIComponent(sym));
    if(sel!==sym)return;
    if(r.status===404){el.className='mute';el.innerHTML='Sem relatórios para '+esc(sym)+': o radar cobre ações e FIIs brasileiros com dados abertos da CVM (PETR4, VALE3, ITUB4, HGLG11, KNRI11, XPML11, MXRF11). Ativos de outros mercados não têm fonte integrada.';return}
    if(!r.ok)throw new Error('reports');
    const d=await r.json();if(sel!==sym)return;
    let h=d.stale?`<div class="stale">Dado consultado há ${d.dataAgeDays} dias (limite ${d.staleAfterDays}): pode haver relatório mais novo.</div>`:'';
    const n=d.nextDeadline;
    h+=n?`<div class="src"><b>Próximo prazo${GL('report-deadline')}:</b> ${esc(n.periodicityLabel)} ${esc(n.label)} até <b>${esc(n.deadline)}</b> <span class="mute">(prazo máximo legal; a divulgação pode vir antes)</span></div>`:'';
    h+='<div class="src">'+d.sources.map(s=>`<b>${esc(s.kindLabel)}</b>: <a href="${esc(s.url)}" target="_blank" rel="noopener noreferrer">${esc(s.name)}</a> · consultado em ${esc(s.consultedOn)}`).join('<br>')+'</div>';
    h+='<div class="scroll">'+d.reports.slice(0,8).map(rrReport).join('')+'</div>';
    h+=`<table style="margin-top:8px"><thead><tr><th>Prazo</th><th>Período</th><th>Situação</th></tr></thead><tbody>${d.calendar.map(c=>`<tr><td>${esc(c.deadline)}</td><td>${esc(c.periodicityLabel)} ${esc(c.label)}<div class="mute">${esc(c.rule)}</div></td><td>${esc(c.statusLabel)}${c.deliveredOn?'<div class="mute">entregue em '+esc(c.deliveredOn)+'</div>':''}</td></tr>`).join('')}</tbody></table>`;
    if(d.notices.length)h+='<ul class="note">'+d.notices.map(x=>'<li>'+esc(x)+'</li>').join('')+'</ul>';
    h+=`<div class="src" style="margin-top:8px"><b>Regra do veredito${GL('verdict')}.</b> ${esc(d.rule)} ${esc(d.disclaimer)}</div>`;
    el.className='';el.style.fontSize='12px';el.innerHTML=h;
  }catch(e){if(sel===sym){el.className='mute';el.textContent='Radar indisponível agora.'}}
}
let SPQ=null,SPMP=null,SPTYPES=[],SPPROFILE=null;
const PCT=v=>f(v,2)+'%';
function spTargets(mp,cur){
  const max=Math.max(60,...mp.targets.map(t=>Number(t.percent)),...(cur||[]).map(c=>Number(c.currentPercent)));
  return '<div class="tg">'+mp.targets.map((t,i)=>{const c=cur?cur[i]:null;
    return `<span>${esc(t.label)}</span><span class="b"><i style="width:${Number(t.percent)/max*100}%"></i>${c?`<u style="left:calc(${Number(c.currentPercent)/max*100}% - 1px)"></u>`:''}</span><span class="mono">${PCT(t.percent)}</span>`}).join('')+'</div>'
    +(cur?'<div class="src">Barra dourada: meta do perfil. Marca azul: sua carteira.</div>':'');
}
const SHORT={RENDA_FIXA_PUBLICA:'RF pública',RENDA_FIXA_PRIVADA:'RF privada',FUNDOS:'Fundos',RENDA_VARIAVEL:'Renda variável',CRIPTO:'Cripto',CAMBIO_E_COMMODITIES:'Câmbio e commod.'};
function spRadar(cur){
  const n=cur.length,R=70,C=100,max=Math.max(20,Math.ceil(Math.max(...cur.map(c=>Math.max(Number(c.currentPercent),Number(c.targetPercent))))/10)*10);
  const pt=(i,v)=>{const a=-Math.PI/2+i*2*Math.PI/n,r=R*v/max;return [C+r*Math.cos(a),C+r*Math.sin(a)]};
  const poly=k=>cur.map((c,i)=>pt(i,Number(c[k])).map(x=>x.toFixed(1)).join(',')).join(' ');
  const axes=cur.map((c,i)=>{const [x,y]=pt(i,max);const [lx,ly]=pt(i,max*1.22);return `<line x1="${C}" y1="${C}" x2="${x.toFixed(1)}" y2="${y.toFixed(1)}" stroke="var(--line)"/><text x="${lx.toFixed(1)}" y="${ly.toFixed(1)}" fill="var(--mute)" font-size="7" text-anchor="middle" dominant-baseline="middle">${esc(SHORT[c.category]||c.label)}</text>`}).join('');
  const ring=[.5,1].map(k=>`<polygon points="${cur.map((c,i)=>pt(i,max*k).map(x=>x.toFixed(1)).join(',')).join(' ')}" fill="none" stroke="var(--line)"/>`).join('');
  return `<svg viewBox="0 0 200 200" role="img" aria-label="Radar de aderência: meta do perfil contra a carteira" style="width:100%;max-width:300px;display:block;margin:var(--s2) auto">${ring}${axes}<polygon points="${poly('targetPercent')}" fill="color-mix(in srgb,var(--gold) 18%,transparent)" stroke="var(--gold)" stroke-dasharray="3 2"/><polygon points="${poly('currentPercent')}" fill="color-mix(in srgb,var(--info) 22%,transparent)" stroke="var(--info)"/></svg>
  <div class="legend" style="justify-content:center"><span style="--c:var(--gold)">Meta do perfil</span><span style="--c:var(--ice)">Sua carteira</span></div>`;
}
function spHoldingRow(){
  return `<div class="hold"><select aria-label="Tipo de ativo">${SPTYPES.map(t=>`<option value="${esc(t.key)}">${esc(t.label)}</option>`).join('')}</select><input type="number" min="0" step="0.01" placeholder="Valor em R$" aria-label="Valor em reais"></div>`;
}
function renderSuitability(){
  const el=$('#sp');if(!SPQ||!SPMP){return}
  const qs=SPQ.questions.map(q=>`<fieldset class="q"><legend>${esc(q.text)} <span class="ref">${esc(q.dimensionLabel)} · ${esc(q.legalRef)}</span></legend>${q.options.map(o=>`<label><input type="radio" name="q_${esc(q.id)}" value="${esc(o.id)}"> ${esc(o.text)}</label>`).join('')}</fieldset>`).join('');
  el.className='';el.style.fontSize='12px';
  el.innerHTML=`<div class="src">Suas respostas e valores não são gravados nem enviados a terceiros: o cálculo acontece nesta aplicação e o resultado some ao recarregar a página.</div>
  <div class="banner-info" id="spBanner" hidden>Sua conta ainda não tem perfil de investidor. Responda o questionário e salve o resultado para ver a aderência da sua carteira.</div>
  <details><summary><b>Descobrir meu perfil</b> <span class="mute">9 perguntas</span></summary><form id="spForm">${qs}<button type="submit">Calcular perfil</button> <span id="spErr" class="dn"></span></form></details>
  <div id="spRes"></div>
  <details style="margin-top:8px"><summary><b>Carteiras modelo</b> <span class="mute">metas por perfil</span></summary>${SPMP.portfolios.map(p=>`<h2 style="margin-top:12px;font-size:13px">${esc(p.profileLabel)}</h2>${spTargets(p)}`).join('')}</details>
  <div class="src" style="margin-top:8px">${esc(SPQ.disclaimer)} Faixas da nota (0 a 100): ${Object.entries(SPQ.scoreBands).map(([k,v])=>esc(k==='CONSERVATIVE'?'conservador':k==='MODERATE'?'moderado':k==='BOLD'?'arrojado':'agressivo')+' '+esc(v)).join('; ')}. Metas ilustrativas definidas pelo produto, sem fonte externa.</div>`;
  if(typeof profileBanner==='function')profileBanner();
  $('#spForm').addEventListener('submit',async e=>{
    e.preventDefault();$('#spErr').textContent='';
    const answers={};SPQ.questions.forEach(q=>{const c=document.querySelector(`input[name="q_${q.id}"]:checked`);if(c)answers[q.id]=c.value});
    if(Object.keys(answers).length<SPQ.questions.length){$('#spErr').textContent='Responda todas as perguntas.';return}
    try{
      const r=await fetch('/api/suitability/evaluate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({answers})});
      const d=await r.json();if(!r.ok)throw new Error(d.error||'erro');
      SPPROFILE=d.profile;
      $('#spRes').innerHTML=`<div style="margin:10px 0"><span class="pf">${esc(d.profileLabel)}</span> <span class="mute">nota ${f(d.score,2)} de 100${d.profile!==d.scoreProfile?' · pela nota seria '+esc(d.scoreProfileLabel):''}</span></div>
      ${d.appliedCaps.length?'<ul class="note">'+d.appliedCaps.map(c=>'<li>'+esc(c)+'</li>').join('')+'</ul>':''}
      <div id="spSave" style="margin:10px 0"></div><div class="src">Meta da carteira modelo ${esc(d.profileLabel)}:</div>${spTargets(d.modelPortfolio)}
      <div style="margin-top:12px"><b>Aderência da minha carteira</b><div id="spHold">${spHoldingRow()}</div>
      <button type="button" id="spAdd">+ posição</button> <button type="button" id="spCalc">Calcular aderência</button> <span id="spErr2" class="dn"></span></div><div id="spAd"></div>`;
      $('#spAdd').onclick=()=>$('#spHold').insertAdjacentHTML('beforeend',spHoldingRow());
      offerSaveProfile(SPPROFILE);
      $('#spCalc').onclick=spAdherence;
    }catch(err){$('#spErr').textContent=err.message}
  });
}
async function spAdherence(){
  $('#spErr2').textContent='';
  const holdings=[...document.querySelectorAll('#spHold .hold')].map(h=>({type:h.querySelector('select').value,valueBrl:h.querySelector('input').value})).filter(h=>h.valueBrl!=='');
  if(!holdings.length){$('#spErr2').textContent='Informe ao menos uma posição.';return}
  try{
    const r=await fetch('/api/suitability/adherence',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({profile:SPPROFILE,holdings})});
    const d=await r.json();if(!r.ok)throw new Error(d.error||'valor inválido');
    const ST={WITHIN:'dentro da faixa',ABOVE:'acima da meta',BELOW:'abaixo da meta'};
    $('#spAd').innerHTML=`<div style="margin:10px 0"><b>Aderência: ${f(d.score,2)} de 100</b>${GL('adherence')} <span class="mute">· ${d.classesOutsideBand} classe(s) fora da faixa de ±${f(d.bandPoints,0)} p.p. · total R$ ${f(d.totalBrl,2)}</span></div>${spRadar(d.classes)}
    <table><thead><tr><th>Classe</th><th class="n">Atual</th><th class="n">Meta</th><th class="n">Desvio</th></tr></thead><tbody>${d.classes.map(c=>`<tr><td>${esc(c.label)}<div class="mute">${ST[c.status]}</div></td><td class="n">${PCT(c.currentPercent)}</td><td class="n">${PCT(c.targetPercent)}</td><td class="n ${c.status==='WITHIN'?'':'warn'}">${Number(c.deviationPoints)>0?'+':''}${f(c.deviationPoints,2)} p.p.</td></tr>`).join('')}</tbody></table>
    <div class="src">Mostra só o desvio em relação à meta; não indica o que comprar ou vender. ${esc(d.disclaimer)}</div>`;
  }catch(err){$('#spErr2').textContent=err.message}
}
function smAssetOptions(){
  const inv=(typeof universe!=='undefined'?universe:[]).filter(x=>x.assetClass!=='Índice');
  return `<optgroup label="Ativos do painel (volatilidade com dado real)">${inv.map(x=>`<option value="S:${esc(x.symbol)}">${esc(x.symbol)} · ${esc(x.name)}</option>`).join('')}</optgroup>
  <optgroup label="Por tipo (sem volatilidade)">${SPTYPES.map(t=>`<option value="T:${esc(t.key)}">${esc(t.label)}</option>`).join('')}</optgroup>`;
}
function smRow(){return `<div class="hold"><select aria-label="Ativo">${smAssetOptions()}</select><input type="number" min="0" step="0.01" placeholder="Valor em R$" aria-label="Valor em reais"></div>`}
const smRef=v=>v.startsWith('S:')?{symbol:v.slice(2)}:{type:v.slice(2)};
/* O simulador precisa do universo (ativos do painel) e da taxonomia: monta uma única vez, quando os dois chegarem, em qualquer ordem. */
let SMDONE=false;
function tryRenderSimulator(){if(!SMDONE&&SPTYPES.length&&universe.length){SMDONE=true;renderSimulator()}}
function renderSimulator(){
  const el=$('#sm');if(!SPTYPES.length)return;
  el.className='';el.style.fontSize='12px';
  el.innerHTML=`<div class="banner">SIMULAÇÃO${GL('simulation')}: não é cotação nem ordem. A plataforma não executa ordens.</div>
  <div class="src">Informe sua carteira em reais e uma compra ou venda hipotética. Nada é gravado.</div>
  <div style="margin-top:8px"><b>Minha carteira</b><div id="smPos">${smRow()}</div><button type="button" id="smAdd">+ posição</button> <button type="button" data-saved="smPos">Usar minha carteira salva</button> <span class="src" data-savedmsg="smPos" role="status"></span></div>
  <div style="margin-top:10px"><b>Ordem hipotética</b>
   <div class="hold"><select id="smSide" aria-label="Compra ou venda"><option value="BUY">Compra</option><option value="SELL">Venda</option></select><select id="smAsset" aria-label="Ativo da ordem">${smAssetOptions()}</select><input id="smAmt" type="number" min="0" step="0.01" placeholder="Valor em R$" aria-label="Valor da ordem"></div>
   <div class="hold"><select id="smProfile" aria-label="Perfil para a aderência"><option value="">Sem perfil (não calcular aderência)</option><option value="CONSERVATIVE">Perfil CONSERVADOR</option><option value="MODERATE">Perfil MODERADO</option><option value="BOLD">Perfil ARROJADO</option><option value="AGGRESSIVE">Perfil AGRESSIVO</option></select></div>
   <button type="button" id="smGo">Simular impacto</button> <span id="smErr" class="dn"></span></div><div id="smOut"></div>`;
  $('#smAdd').onclick=()=>$('#smPos').insertAdjacentHTML('beforeend',smRow());
  $('#smGo').onclick=runSimulation;
}
async function runSimulation(){
  $('#smErr').textContent='';
  const positions=[...document.querySelectorAll('#smPos .hold')].map(h=>({...smRef(h.querySelector('select').value),valueBrl:h.querySelector('input').value})).filter(p=>p.valueBrl!=='');
  const amt=$('#smAmt').value;if(amt===''){$('#smErr').textContent='Informe o valor da ordem.';return}
  const body={positions,order:{side:$('#smSide').value,...smRef($('#smAsset').value),valueBrl:amt},profile:$('#smProfile').value||null};
  try{
    const r=await fetch('/api/impact/pre-order',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
    const d=await r.json();if(!r.ok)throw new Error(d.error||'dados inválidos');
    const b=d.before,a=d.after,nf=(v,k=2)=>v==null?'<span class="gap">n/d</span>':f(v,k);
    const vol=v=>v==null?'<span class="gap">n/d</span>':f(v*100,2)+'%';
    const row=(label,x,y,fmt)=>`<tr><td>${label}</td><td class="n">${fmt(x)}</td><td class="n">${fmt(y)}</td></tr>`;
    const ad=(x)=>x?f(x.score,2):'<span class="gap">n/d</span>';
    $('#smOut').innerHTML=`<div class="banner">${esc(d.label)}</div>
    <div class="src">${d.side==='BUY'?'Compra':'Venda'} hipotética de R$ ${f(d.orderAmountBrl,2)} em ${esc(d.orderLabel)}</div>
    <table><thead><tr><th>Métrica</th><th class="n">Antes</th><th class="n">Depois</th></tr></thead><tbody>
    ${row('Total (R$)',b.totalBrl,a.totalBrl,v=>f(v,2))}
    ${row('Maior posição'+GL('concentration'),b.largestPercent,a.largestPercent,v=>v==null?'<span class="gap">n/d</span>':f(v,2)+'%')}
    ${row('HHI'+GL('hhi'),b.hhi,a.hhi,v=>nf(v,2))}
    ${row('Posições efetivas'+GL('effective-positions'),b.effectivePositions,a.effectivePositions,v=>nf(v,2))}
    ${row('Volatilidade ponderada'+GL('weighted-volatility'),b.weightedVolatility,a.weightedVolatility,vol)}
    ${row('Cobertura da volatilidade'+GL('volatility-coverage'),b.volatilityCoverage,a.volatilityCoverage,v=>v==null?'<span class="gap">n/d</span>':f(v,2)+'%')}
    ${d.adherenceBefore||d.adherenceAfter?row('Aderência ao perfil'+GL('adherence'),d.adherenceBefore,d.adherenceAfter,ad):''}
    </tbody></table>
    <ul class="note">${d.findings.map(x=>`<li class="${x.level==='ATTENTION'?'att':''}">${x.level==='ATTENTION'?'<b>Atenção:</b> ':''}${esc(x.text)}</li>`).join('')}</ul>
    <div class="src"><b>Premissas.</b> ${d.assumptions.map(esc).join(' ')} ${esc(d.disclaimer)}</div>`;
  }catch(err){$('#smErr').textContent=err.message}
}
/* Tradutor geopolítico e teses: lê a carteira informada pelo radar macro real e por cinco lentes genéricas. Nada é gravado. */
let TZDONE=false;
function tryRenderLens(){if(!TZDONE&&SPTYPES.length&&universe.length){TZDONE=true;renderLens()}}
function renderLens(){
  const el=$('#tz');el.className='';el.style.fontSize='12px';
  el.innerHTML=`<div class="banner">TESES ILUSTRATIVAS${GL('thesis')}: genéricas, inspiradas no estilo público de cada gestora. Não representam posições reais de nenhuma instituição.</div>
  <div class="src">Informe sua carteira em reais. Nada é gravado; o setor vem do universo do painel.</div>
  <div style="margin-top:8px"><b>Minha carteira</b><div id="tzPos">${smRow()}</div><button type="button" id="tzAdd">+ posição</button> <button type="button" data-saved="tzPos">Usar minha carteira salva</button> <span class="src" data-savedmsg="tzPos" role="status"></span></div>
  <div style="margin-top:10px"><button type="button" id="tzGo">Traduzir carteira</button> <span id="tzErr" class="dn"></span></div><div id="tzOut"></div>`;
  $('#tzAdd').onclick=()=>$('#tzPos').insertAdjacentHTML('beforeend',smRow());
  $('#tzGo').onclick=runLens;
}
async function runLens(){
  $('#tzErr').textContent='';
  const positions=[...document.querySelectorAll('#tzPos .hold')].map(h=>({...smRef(h.querySelector('select').value),valueBrl:h.querySelector('input').value})).filter(p=>p.valueBrl!=='');
  if(!positions.length){$('#tzErr').textContent='Informe ao menos uma posição.';return}
  try{
    const r=await fetch('/api/thesis/lens',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({positions})});
    const d=await r.json();if(!r.ok)throw new Error(d.error||'dados inválidos');
    const g=d.geopolitics,mc=d.macro,nd='<span class="gap">não informado</span>';
    const sectorRows=g.sectors.map(s=>`<tr><td>${esc(s.sector)}</td><td class="n">${f(s.exposurePercent,2)}%</td><td class="n">${f(s.pressure,2)}</td><td class="n">${f(s.contributionPoints,2)} p.p.</td></tr>
      ${s.headlines.map(h=>`<tr><td colspan="4" class="mute">${esc(h.title)} <i>(${esc(h.source)})</i></td></tr>`).join('')}`).join('');
    $('#tzOut').innerHTML=`<h3 style="margin:12px 0 4px">Exposição ao noticiário${GL('geo-pressure')}</h3>
    <div class="src">Radar macro: ${mc.available?esc(mc.source):'sem dado agora'} · VIX ${mc.vix==null?nd:f(mc.vix,2)} · Índice do dólar ${mc.dollarIndex==null?nd:f(mc.dollarIndex,2)} · Yield 10 anos EUA ${mc.us10yYield==null?nd:f(mc.us10yYield,2)}</div>
    <table><thead><tr><th>Setor</th><th class="n">Peso</th><th class="n">Pressão</th><th class="n">Contribuição</th></tr></thead><tbody>${sectorRows||'<tr><td colspan="4" class="mute">Nenhuma posição com setor conhecido.</td></tr>'}</tbody></table>
    <ul class="note">${g.findings.map(x=>`<li>${esc(x)}</li>`).join('')}</ul>
    <div class="src"><b>Limites.</b> ${g.assumptions.map(esc).join(' ')}</div>
    <h3 style="margin:12px 0 4px">Teses institucionais${GL('thesis')}</h3>
    ${d.readings.map(x=>`<details class="thesis"><summary><b>${esc(x.thesis.name)}</b> · favorecido ${f(x.favoredPercent,2)}% · prejudicado ${f(x.hurtPercent,2)}% · fora da tese ${f(x.outsideThesisPercent,2)}%</summary>
      <div class="src">${esc(x.thesis.inspiration)}</div><p>${esc(x.thesis.summary)}</p>
      <b>Premissas</b><ul class="note">${x.thesis.premises.map(p=>`<li>${esc(p)}</li>`).join('')}</ul>
      <table><thead><tr><th>Ativos sensíveis</th><th>Efeito</th><th class="n">Peso</th></tr></thead><tbody>${x.sensitivities.map(s=>`<tr><td>${esc(s.label)}<div class="mute">${esc(s.rationale)}${s.holdings.length?' · '+s.holdings.map(esc).join(', '):''}</div></td><td>${esc(s.effect)}</td><td class="n">${f(s.percent,2)}%</td></tr>`).join('')}</tbody></table>
      <b>O que invalidaria</b><ul class="note">${x.thesis.invalidations.map(p=>`<li>${esc(p)}</li>`).join('')}</ul></details>`).join('')}
    <div class="src">${esc(d.disclaimer)}</div>`;
  }catch(err){$('#tzErr').textContent=err.message}
}
async function loadSuitability(){
  try{
    const [q,m,t]=await Promise.all([j('/api/suitability/questionnaire'),j('/api/suitability/model-portfolios'),j('/api/taxonomy')]);
    SPQ=q;SPMP=m;SPTYPES=t.filter(x=>x.investable);renderSuitability();tryRenderSimulator();tryRenderLens();
  }catch(e){$('#sp').textContent='Suitability indisponível agora.'}
}
loadSuitability();
/* Cada rótulo de métrica do painel liga a uma entrada do glossário (GL): a tabela GID mapeia rótulo -> id e é testada contra o glossário. */
const GID=[[/^Z-Score/,'zscore'],[/^Percentil/,'percentile'],[/^RSI/,'rsi'],[/^MACD/,'macd'],[/^Vol\. anual/,'volatility'],[/^Max drawdown/,'max-drawdown'],
 [/^Sharpe/,'sharpe'],[/^VaR/,'var95'],[/^ATR/,'atr'],[/^Valor justo/,'fair-value'],[/^Upside/,'upside'],[/^Downside/,'downside'],[/^Risco\/retorno/,'risk-reward'],
 [/^Expectativa/,'expected-value'],[/^Força relativa/,'relative-strength'],[/^Volume relativo/,'relative-volume'],[/^Beta/,'beta'],[/^Benchmark/,'benchmark'],[/^Setor/,'sector'],
 [/^P\/L/,'pe-ratio'],[/^Margem líquida/,'net-margin'],[/^Cresc\. receita/,'revenue-growth'],[/^Valor de mercado/,'market-cap'],[/^Regime/,'regime'],
 [/^Confiança/,'confidence'],[/^Risco$/,'risk'],[/^Exposição/,'suggested-exposure'],[/^Entrada/,'entry-zone'],[/^Invalidação/,'invalidation'],[/^Alvo/,'targets'],
 [/^VIX/,'vix'],[/^Índice do dólar/,'dollar-index'],[/^Yield 10/,'yield-10y'],[/^Risco geopolítico/,'geo-risk'],[/^Estratégia|^Comprar e segurar|^Operações|^Barras/,'backtest'],
 [/^Acerto/,'win-rate'],[/Selic/i,'selic'],[/CDI/,'cdi'],[/IPCA/,'ipca'],[/PTAX|dólar/i,'ptax'],[/^Score/,'score'],[/^Sinal/,'signal'],[/^Fase/,'phase'],
 [/^Maior posição/,'concentration'],[/^HHI/,'hhi'],[/^Posições efetivas/,'effective-positions'],[/^Volatilidade ponderada/,'weighted-volatility'],
 [/^Cobertura/,'volatility-coverage'],[/^Aderência/,'adherence'],[/^Simulação/,'simulation'],[/^Veredito/,'verdict'],[/^Variação/,'variation'],
 [/^Dividend yield/,'dividend-yield'],[/^Valor patrimonial/,'nav-per-share'],[/^ABL|^Área/,'abl'],[/^Vacância/,'vacancy'],[/^Prazo/,'report-deadline'],
 [/^Selo|^Frescor/,'freshness'],[/^Desatualizado/,'stale']];
const gid=label=>{const t=String(label).replace(/<[^>]*>/g,'').trim();const m=GID.find(([re])=>re.test(t));return m?m[1]:null};
const GL=id=>id?`<button type="button" class="gl" data-g="${esc(id)}" aria-label="O que é isto? Abrir no glossário" title="O que é, como ler e quando se preocupar">?</button>`:'';
const GLL=label=>GL(gid(label));
let GLOSS=null;
async function loadGloss(){
  if(GLOSS)return GLOSS;
  const d=await j('/api/glossary');GLOSS=d;
  const groups={};d.entries.forEach(e=>{(groups[e.groupLabel]=groups[e.groupLabel]||[]).push(e)});
  $('#glossBody').innerHTML=Object.entries(groups).map(([g,es])=>`<h3>${esc(g)}</h3>`+es.map(e=>`<details id="g-${esc(e.id)}" data-t="${esc((e.term+' '+e.what+' '+e.howToRead+' '+e.whenToWorry).toLowerCase())}"><summary>${esc(e.term)}</summary>
    <dl><dt>O que é</dt><dd>${esc(e.what)}</dd><dt>Como ler</dt><dd>${esc(e.howToRead)}</dd><dt>Quando se preocupar</dt><dd>${esc(e.whenToWorry)}</dd></dl>
    ${e.related.length?`<div class="rel src">Veja também: ${e.related.map(r=>{const t=d.entries.find(x=>x.id===r);return t?`<button type="button" class="gl-rel" data-g="${esc(r)}">${esc(t.term)}</button>`:''}).join('')}</div>`:''}</details>`).join('')).join('')
    +`<div class="src" style="margin-top:12px">${esc(d.disclaimer)}</div>`;
  return d;
}
async function openGloss(id){
  const dlg=$('#gloss');
  try{await loadGloss()}catch(e){$('#glossBody').textContent='Glossário indisponível agora.'}
  if(!dlg.open)dlg.showModal();
  $('#glossQ').value='';document.querySelectorAll('#gloss details').forEach(x=>{x.hidden=false;x.classList.remove('hit')});
  if(id){const el=document.getElementById('g-'+id);if(el){el.open=true;el.classList.add('hit');el.scrollIntoView({block:'center'})}}
}
document.addEventListener('click',e=>{const b=e.target.closest('[data-g]');if(b){e.preventDefault();openGloss(b.dataset.g)}});
document.addEventListener('click',e=>{if(e.target.closest('#glossBtn'))openGloss()});
$('#glossX').addEventListener('click',()=>$('#gloss').close());
$('#glossQ').addEventListener('input',e=>{const q=e.target.value.trim().toLowerCase();document.querySelectorAll('#gloss details').forEach(x=>{x.hidden=!!q&&!x.dataset.t.includes(q)})});
async function loadStatus(){
  const st=await j('/api/status').catch(()=>null);if(!st)return;
  const c={CLOSED:'up',HALF_OPEN:'warn',OPEN:'dn',CONECTADO:'up',RECONECTANDO:'warn',DESLIGADO:'mute'};
  $('#status').innerHTML=st.providers.map(p=>`<div style="display:flex;justify-content:space-between"><span>${p.name}${p.enabled?'':' (sem chave)'}</span><span class="${p.enabled?c[p.circuit]:'mute'}">${p.circuit}</span><span class="num mute">${p.successes}/${p.failures}</span></div>${p.detail?`<div class="src" style="margin:-1px 0 5px">${p.detail}</div>`:''}`).join('')
   +`<div style="margin-top:6px">Cache: <b>${st.cache}</b> · Banco: <b>${st.persistence?'ligado':'desligado'}</b></div><div>Conexões: ${(st.channels||[]).map(c=>c.name+' '+c.clients).join(' · ')}</div>`;
}
async function loadBrazil(){
  const r=await j('/api/brazil/rates').catch(()=>null);
  if(!r){$('#br').innerHTML='<span class="mute">Sem resposta do servidor para os indicadores do Banco Central.</span>';return}
  const fmt=v=>{const d=Math.min(6,(v.split('.')[1]||'').length);return Number(v).toLocaleString('pt-BR',{minimumFractionDigits:d,maximumFractionDigits:d})};
  const when=i=>i.periodicity==='MONTHLY'
    ?new Date(i.date+'T12:00:00').toLocaleDateString('pt-BR',{month:'short',year:'numeric'})
    :new Date(i.date+'T12:00:00').toLocaleDateString('pt-BR',{day:'2-digit',month:'2-digit'});
  $('#br').innerHTML=r.items.map(i=>i.status==='UNAVAILABLE'
    ?`<div style="display:flex;justify-content:space-between;gap:8px;padding:2px 0"><span>${i.label}${GLL(i.label)}</span><span class="fresh dn">sem dado</span></div>`
    :`<div style="display:flex;justify-content:space-between;gap:8px;padding:2px 0;align-items:baseline"><span>${i.label}${GLL(i.label)}<span class="src"> · ${i.dateLabel} ${when(i)}</span></span>
      <span class="num">${fmt(i.value)} <span class="src">${i.unit}</span>${i.status==='STALE'?' <span class="fresh dn" title="sem dado novo no prazo esperado ou sem confirmação do Banco Central nas últimas 3 horas">desatualizado</span>':''}</span></div>`).join('')
    +`<div class="src" style="margin-top:6px">Fonte: Banco Central do Brasil (SGS e PTAX), com a data de referência de cada publicação. IPCA mostra o mês de referência.${r.fetchedAt?' Atualizado em '+dt(Date.parse(r.fetchedAt))+'.':''}</div>`;
}
loadBrazil();setInterval(loadBrazil,300000);
setInterval(loadStatus,10000);setInterval(()=>{if(sel&&!paused){loadMicro();loadSignals()}},30000);

function renderDetail(){
  const u=universe.find(x=>x.symbol===sel);if(!u)return;
  $('#hSym').textContent=sel;$('#hName').textContent=u.name+' · '+u.assetClass+' · '+(EXS[u.exchange]||u.exchange||'')+' · '+(u.currency||'');
  renderProv();
  const c=candles[candles.length-1];
  $('#empty').hidden=!!c;
  if(!c){
    $('#emptyTxt').textContent='Nenhum provedor respondeu para '+sel+' e não há dado real gravado. Nada é estimado ou simulado; o sistema tenta de novo automaticamente.';
    $('#hPx').textContent='';$('#hChg').textContent='';
  }else{
    $('#hPx').textContent=f(c.close,c.close<10?4:2);
    const chg=c.close/c.open-1;$('#hChg').className='num '+(chg>=0?'up':'dn');$('#hChg').textContent=(chg>=0?'+':'')+f(chg*100)+'%';
  }
  if(!A){
    const msg=c?'Dados reais insuficientes para o motor de timing.':'Sem dado em tempo real.';
    $('#timing').innerHTML=`<h2>Timing</h2><div class="mute">${msg}</div>`;
    $('#metrics').innerHTML=`<div style="grid-column:1/-1;border:0" class="mute">${msg}</div>`;
    $('#why').innerHTML=`<li style="--c:var(--line)" class="mute">${msg}</li>`;
    return;
  }
  const s=SIG[A.signal];
  const sc=A.score,w=Math.abs(sc)/2,col=sc>=0?'var(--up)':'var(--warn)';
  $('#timing').innerHTML=`<div class="score"><div><div class="chip ${s[1]}" style="font-size:13px">${s[0]}</div>${GL('signal')}<div class="mute" style="margin-top:6px">${PHASE[A.phase]}${GL('phase')}</div></div>
   <div class="big num ${sc>=0?'up':'warn'}">${sc>0?'+':''}${sc}</div>${GL('score')}</div>
   <div class="meter"><b style="${sc>=0?'left:50%':'right:50%'};width:${w}%;background:${col}"></b></div>
   <div class="mute" style="display:flex;justify-content:space-between;font-size:11px"><span>realizar / sair</span><span>comprar</span></div>
   <div class="kv" style="margin-top:10px"><div><span>Regime${GL('regime')}</span><span>${REGIME[A.regime]}</span></div><div><span>Confiança${GL('confidence')}</span><span class="num">${A.confidence}%</span></div>
   <div><span>Risco${GL('risk')}</span><span>${RISK[A.risk]}</span></div><div><span>Exposição sugerida${GL('suggested-exposure')}</span><span class="num">${pc(A.suggestedExposure)}</span></div>
   <div><span>Entrada${GL('entry-zone')}</span><span class="num">${f(A.entryLow)}–${f(A.entryHigh)}</span></div><div><span>Invalidação${GL('invalidation')}</span><span class="num dn">${f(A.invalidation)}</span></div>
   <div><span>Alvo 1${GL('targets')}</span><span class="num warn">${f(A.target1)}</span></div><div><span>Alvo 2${GL('targets')}</span><span class="num warn">${f(A.target2)}</span></div></div>`;
  const m=[['Z-Score (50)',f(A.zScore)],['Percentil',f(A.percentile,0)+'%'],['RSI (14)',f(A.rsi,0)],['MACD hist.',f(A.macdHist,3)],[`Vol. anual. (√${A.periodsPerYear})`,pc(A.annVol)],['Max drawdown',pc(A.maxDrawdown)],
   ['Sharpe',f(A.sharpe)],['VaR 95% (1 barra)',pc(A.var95)],['ATR (14)',f(A.atr)],['Valor justo (SMA50)',f(A.fairValue)],['Upside',pc(A.upside)],['Downside',pc(A.downside)],['Risco/retorno',f(A.riskReward)],['Expectativa',pc(A.expectedValue,2)],['Força relativa',(A.relStrength>=0?'+':'')+f(A.relStrength*100)+' pp'],['Volume relativo',f(A.relVolume)+'x']];
  if(A.beta!=null){const bn=(universe.find(x=>x.symbol===sel)||{}).benchmark||'benchmark';m.push([`Beta vs ${bn} (90)`,f(A.beta)])}
  $('#metrics').innerHTML=m.map(([k,v])=>`<div><span>${k}${GLL(k)}</span><span class="num">${v}</span></div>`).join('');
  $('#why').innerHTML=A.factors.map(x=>`<li style="--c:${SIDE[x.side]}"><b class="mono" style="color:${SIDE[x.side]};font-size:11px">${x.side}</b> ${x.text}</li>`).join('');
}

function renderMacro(m){
  const row=(k,v,txt,pct,c)=>v==null
    ?`<div style="display:flex;justify-content:space-between"><span class="mute">${k}${GLL(k)}</span><span class="mute">sem dado</span></div><div class="bar"></div>`
    :`<div style="display:flex;justify-content:space-between"><span class="mute">${k}${GLL(k)}</span><span class="num">${txt}</span></div><div class="bar"><i style="width:${Math.max(0,Math.min(100,pct))}%;background:${c}"></i></div>`;
  const sec=Object.entries(m.sectorImpact||{}).sort((a,b)=>b[1]-a[1]).slice(0,4).map(([k,v])=>`<span class="chip ${v>.5?'dn':'warn'}" style="margin:0 4px 4px 0">${k} ${f(v*100,0)}</span>`).join('');
  const news=(m.news||[]).slice(0,4).map(n=>`<div class="news" style="--c:${n.risk>.5?'var(--dn)':n.risk>0?'var(--warn)':'var(--line)'}">${n.title}<div class="src">${n.source}</div></div>`).join('');
  $('#macro').innerHTML=row('VIX',m.vix,m.vix!=null&&f(m.vix,1),(m.vix-10)/45*100,m.vix>25?'var(--dn)':'var(--ice)')
   +row('Índice do dólar',m.dollarIndex,m.dollarIndex!=null&&f(m.dollarIndex,2),(m.dollarIndex-95)/20*100,'var(--ice)')
   +row('Yield 10 anos',m.yield10y,m.yield10y!=null&&f(m.yield10y,2)+'%',m.yield10y/6*100,'var(--ice)')
   +row('Risco geopolítico',m.geoRisk,m.geoRisk!=null&&f(m.geoRisk*100,0)+'/100',m.geoRisk*100,m.geoRisk>.5?'var(--dn)':'var(--warn)')
   +(m.available
     ?`<div style="margin:2px 0 6px">Índice de risco macro: <b class="num ${m.riskScore>.5?'dn':'up'}">${f(m.riskScore*100,0)}/100</b> <span class="src">${m.source}</span></div>`
     :'<div class="mute" style="margin:2px 0 6px">Sem dado macro em tempo real: o motor não aplica ajuste macro.</div>')
   +(sec?`<div class="mute" style="font-size:11px;margin-bottom:4px">Setores pressionados</div>${sec}`:'')+news;
  $('#hmNews').innerHTML=news||'<span class="mute">Sem manchetes de risco agora.</span>';
}

$('#pause').onclick=async()=>{
  paused=!paused;$('#pause').textContent=paused?'Retomar':'Pausar';
  if(paused){stopStream();setLive('','pausado')}else{await select(sel);connect()}
};
$('#bRun').onclick=async()=>{
  $('#btOut').textContent='Calculando…';
  const r=await j(`/api/backtest/${sel}?buyScore=${$('#bBuy').value}&sellScore=${$('#bSell').value}&bars=${$('#bBars').value}`).catch(()=>null);
  if(!r){$('#btOut').innerHTML='<span class="mute">Sem dado real suficiente para rodar o backtest deste ativo.</span>';return}
  const t=r.trades.slice(-6).reverse().map(x=>`<tr><td class="n">${x.entryIdx}→${x.exitIdx}</td><td class="n ${x.ret>=0?'up':'dn'}">${pc(x.ret)}</td><td>${x.reason}</td></tr>`).join('');
  $('#btOut').innerHTML=`<div class="mute" style="font-size:11px;margin-bottom:6px">Projeção sobre o histórico real carregado; não é cotação nem promessa de retorno.</div><div class="kv"><div><span>Estratégia${GL('backtest')}</span><span class="num ${r.strategyReturn>=0?'up':'dn'}">${pc(r.strategyReturn)}</span></div><div><span>Comprar e segurar${GL('backtest')}</span><span class="num">${pc(r.buyHoldReturn)}</span></div>
   <div><span>Operações</span><span class="num">${r.trades.length}</span></div><div><span>Acerto${GL('win-rate')}</span><span class="num">${pc(r.winRate,0)}</span></div><div><span>Max drawdown${GL('max-drawdown')}</span><span class="num">${pc(r.maxDrawdown)}</span></div><div><span>Barras</span><span class="num">${r.evaluatedBars}</span></div></div>
   <table style="margin-top:8px"><thead><tr><th>Barras</th><th style="text-align:right">Retorno</th><th>Saída</th></tr></thead><tbody>${t||'<tr><td colspan="3" class="mute">Nenhuma operação nesta janela.</td></tr>'}</tbody></table>`;
};

/* O gráfico do cockpit (Lightweight Charts) está em cockpit.js; draw() é definido lá. */

/* ---------- relógio de mercados (estado calculado no servidor, com feriados oficiais) ---------- */
const EXS={NYSE:'NYSE',B3:'B3',XETRA:'Xetra',LSE:'LSE',EURONEXT_AMS:'Euronext',TSE:'Tóquio',HKEX:'HK',ASX:'ASX',TADAWUL:'Tadawul',JSE:'JSE',GLOBAL_24H:'FX',CRYPTO:'Cripto'};
async function clock(){
  const ms=await j('/api/markets').catch(()=>null);if(!ms)return;
  $('#clock').innerHTML=ms.map(m=>{const st=m.open?'<b>aberto</b>':m.holiday?'<i>feriado</i>':'<u>fechado</u>';
    const tip=`${m.reason} · hora local ${new Date(m.localTime).toLocaleTimeString('pt-BR',{timeZone:m.localTime.match(/\[(.+)\]/)?.[1]||'UTC',hour:'2-digit',minute:'2-digit'})}${m.holidaysVerified?'':' · calendário de feriados não verificado'}`;
    return `<span title="${tip}">${EXS[m.exchange]||m.exchange} ${st}${m.holidaysVerified?'':'<sup>*</sup>'}</span>`}).join('');
}
clock();setInterval(clock,30000);
/* ---------- shell: navegação por rota, tema, busca rápida e painel inicial (protótipo aprovado, ADR 012) ---------- */
const IC={home:'M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',mercados:'M4 19V5M4 19h16M8 15l3-4 3 2 4-6',cockpit:'M4 18a8 8 0 1 1 16 0M12 18l4-6',
 radar:'M7 3h8l4 4v14H7zM15 3v4h4M10 12h6M10 16h6',brasil:'M5 19L19 5M7 7h.01M17 17h.01',geo:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18',
 carteira:'M4 7h15a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 7l2-3h11M16 13h.01',perfil:'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0',
 simulador:'M5 8h14M5 16h14M9 4l-4 4 4 4M15 12l4 4-4 4',mapa:'M12 21s7-6 7-11a7 7 0 1 0-14 0c0 5 7 11 7 11zM12 12a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
 composicao:'M12 3l9 5-9 5-9-5zM3 13l9 5 9-5',montecarlo:'M3 17c4-8 6-8 9-4s6 0 9-8',correlacao:'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',stress:'M13 2L4 14h7l-1 8 9-12h-7z',
 glossario:'M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3zM5 17a3 3 0 0 1 3-3h11',alertas:'M6 17V11a6 6 0 1 1 12 0v6l2 2H4zM10 21h4',config:'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19 12l2-1-2-4-2 .5-1.5-1L15 4h-4l-.5 2.5-1.5 1-2-.5-2 4 2 1v2l-2 1 2 4 2-.5 1.5 1L11 20h4l.5-2.5 1.5-1 2 .5 2-4-2-1z',
 saude:'M3 12h4l3-8 4 16 3-8h4',shopping:'M4 7h16l-1.5 11a2 2 0 0 1-2 1.8H7.5a2 2 0 0 1-2-1.8zM8 7a4 4 0 0 1 8 0',privacidade:'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6zM9 12l2 2 4-4'};
const NAV=[['VISÃO GERAL',[['home','Painel inicial'],['mercados','Mercados']]],
 ['ANÁLISE',[['cockpit','Cockpit do ativo'],['radar','Radar de relatórios'],['brasil','Brasil: juros e inflação'],['geo','Geopolítica e teses']]],
 ['PATRIMÔNIO',[['carteira','Minha Carteira'],['shopping','Shopping financeiro'],['perfil','Perfil e carteira modelo'],['simulador','Simulador pré-ordem'],['mapa','Mapa de ativos reais'],['composicao','Composição (Raio-X)']]],
 ['RISCO',[['montecarlo','Monte Carlo'],['correlacao','Correlação e concentração','item #19'],['stress','Stress testing','sob pedido']]],
 ['APRENDER',[['glossario','Glossário']]],
 ['CONTA',[['alertas','Alertas','sob pedido'],['config','Configurações','item #11 e #15'],['saude','Saúde dos dados'],['privacidade','Privacidade e termos']]]];
const icon=id=>`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${IC[id]||''}"/></svg>`;
const PAGES=new Set([...document.querySelectorAll('.page')].map(p=>p.dataset.page));
function buildNav(){
  $('#nav').innerHTML=NAV.map(([g,items])=>`<h3>${g}</h3>`+items.map(([id,label,soon])=>{
    if(id==='glossario')return `<button type="button" class="item" id="glossBtn" title="Glossário didático das métricas">${icon(id)}<span class="lbl">${label}</span></button>`;
    if(soon)return `<a class="soon" role="link" aria-disabled="true" title="Chega com o ${soon} do roadmap: ainda não existe dado real para esta página" tabindex="-1">${icon(id)}<span class="lbl">${label}</span><span class="tagsm">${soon}</span></a>`;
    return `<a href="#/${id}" data-nav="${id}" title="${label}">${icon(id)}<span class="lbl">${label}</span></a>`}).join('')).join('')
  +`<div class="foot"><div class="real"><b><i></i>Dados reais</b><span>Ferramenta analítica e educacional</span></div><button type="button" class="item" id="navCollapse" aria-label="Recolher ou expandir o menu"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg><span class="lbl">Recolher</span></button></div>`;
}
const PAGE_TITLE={};NAV.forEach(([,it])=>it.forEach(([id,label])=>PAGE_TITLE[id]=label));
function go(id){location.hash='#/'+id}
function route(){
  let id=(location.hash.match(/^#\/([a-z]+)/)||[])[1];if(!PAGES.has(id))id='home';
  document.querySelectorAll('.page').forEach(p=>{p.hidden=p.dataset.page!==id});
  document.querySelectorAll('#nav a[data-nav]').forEach(a=>{if(a.dataset.nav===id)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current')});
  document.title=(PAGE_TITLE[id]||'Painel')+' · MeridianWealth';
  document.body.classList.remove('navopen');$('#menuBtn').setAttribute('aria-expanded','false');
  window.scrollTo(0,0);if(id==='cockpit')requestAnimationFrame(()=>draw());
}
addEventListener('hashchange',route);
buildNav();route();
$('#menuBtn').onclick=()=>{const o=document.body.classList.toggle('navopen');$('#menuBtn').setAttribute('aria-expanded',String(o))};
function closeNav(back){document.body.classList.remove('navopen');$('#menuBtn').setAttribute('aria-expanded','false');if(back)$('#menuBtn').focus()}
$('#scrim').onclick=()=>closeNav(false);
addEventListener('keydown',e=>{if(e.key==='Escape'&&document.body.classList.contains('navopen'))closeNav(true)});
try{if(localStorage.getItem('mw-nav')==='collapsed')document.body.classList.add('collapsed')}catch(e){}
$('#navCollapse').onclick=()=>{const c=document.body.classList.toggle('collapsed');try{localStorage.setItem('mw-nav',c?'collapsed':'open')}catch(e){}requestAnimationFrame(draw)};
$('#theme').onclick=()=>{const r=document.documentElement,n=r.dataset.theme==='ivory'?'dark':'ivory';r.dataset.theme=n;try{localStorage.setItem('mw-theme',n)}catch(e){}draw()};
/* seletor de ativo das páginas de análise (todos sincronizados com o ativo selecionado) */
function buildPickers(){
  const opts=universe.map(u=>`<option value="${esc(u.symbol)}">${esc(u.symbol)} · ${esc(u.name)}</option>`).join('');
  document.querySelectorAll('.symPick').forEach(s=>{s.innerHTML=opts;s.value=sel||'';s.onchange=()=>select(s.value)});
}
function syncPick(){document.querySelectorAll('.symPick').forEach(s=>{if(sel)s.value=sel})}
/* busca rápida (Ctrl+K): páginas e ativos */
function palOpen(){const d=$('#pal');if(!d.open)d.showModal();$('#palQ').value='';palRender();$('#palQ').focus()}
function palRender(){
  const q=$('#palQ').value.trim().toLowerCase();
  const pages=NAV.flatMap(([g,it])=>it.filter(i=>!i[2]).map(i=>({kind:'page',id:i[0],label:i[1],g})));
  const hitP=pages.filter(p=>!q||p.label.toLowerCase().includes(q)).slice(0,8);
  const hitA=universe.filter(u=>q&&(u.symbol+' '+u.name).toLowerCase().includes(q)).slice(0,10);
  $('#palList').innerHTML=(hitP.length?'<li class="grp">PÁGINAS</li>'+hitP.map(p=>`<li><button type="button" data-pg="${p.id}">${esc(p.label)}</button></li>`).join(''):'')
   +(hitA.length?'<li class="grp">ATIVOS</li>'+hitA.map(u=>`<li><button type="button" data-as="${esc(u.symbol)}"><b class="mono">${esc(u.symbol)}</b><span class="mute">${esc(u.name)}</span></button></li>`).join(''):'')
   ||'<li class="grp">Nada encontrado</li>';
}
$('#searchBtn').onclick=palOpen;
$('#palQ').addEventListener('input',palRender);
$('#palQ').addEventListener('keydown',e=>{const bs=[...document.querySelectorAll('#palList button')];
  if(e.key==='Enter'&&bs[0]){e.preventDefault();bs[0].click()}else if(e.key==='ArrowDown'&&bs[0]){e.preventDefault();bs[0].focus()}});
$('#palList').addEventListener('keydown',e=>{const bs=[...document.querySelectorAll('#palList button')],i=bs.indexOf(document.activeElement);
  if(e.key==='ArrowDown'&&bs[i+1]){e.preventDefault();bs[i+1].focus()}if(e.key==='ArrowUp'){e.preventDefault();(bs[i-1]||$('#palQ')).focus()}});
$('#palList').addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;$('#pal').close();
  if(b.dataset.pg){if(b.dataset.pg==='glossario')openGloss();else go(b.dataset.pg)}else if(b.dataset.as){select(b.dataset.as);go('cockpit')}});
$('#pal').addEventListener('click',e=>{if(e.target===$('#pal'))$('#pal').close()});
addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();palOpen()}});
/* painel inicial: só dado real, com frescor e fonte */
function renderHome(){
  const now=new Date(),tz={timeZone:'America/Sao_Paulo'};
  const h=Number(now.toLocaleTimeString('pt-BR',{hour:'2-digit',hour12:false,...tz}).slice(0,2));
  $('#hmH1').textContent=h<12?'Bom dia':h<18?'Boa tarde':'Boa noite';
  $('#hmDate').textContent=now.toLocaleDateString('pt-BR',{weekday:'long',day:'2-digit',month:'long',year:'numeric',...tz}).toUpperCase()+' · '+now.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit',...tz})+' (BRASÍLIA)';
  const live=universe.filter(u=>hasData(rows[u.symbol]));
  const sig=live.filter(u=>rows[u.symbol].assessment).sort((a,b)=>Math.abs(rows[b.symbol].assessment.score)-Math.abs(rows[a.symbol].assessment.score)).slice(0,3);
  $('#hmSignals').innerHTML=sig.length?sig.map(u=>{const r=rows[u.symbol],a=r.assessment,s=SIG[a.signal];
    return `<button type="button" class="sig-card" data-s="${esc(u.symbol)}"><div class="sy">${esc(u.symbol)} ${freshChip(r)}</div><div class="st">${s[0]}</div><div class="num ${s[1]}">Score ${a.score>0?'+':''}${a.score}</div><div class="src">${esc(r.source)} · ${quoteWhen(r)}</div></button>`}).join('')
    :'<span class="mute">Sem dado real suficiente para calcular sinais agora.</span>';
  const mv=live.map(u=>{const c=rows[u.symbol].candle;return {u,r:rows[u.symbol],chg:c.close/c.open-1}}).sort((a,b)=>Math.abs(b.chg)-Math.abs(a.chg)).slice(0,6);
  $('#hmMovers').innerHTML=mv.length?mv.map(({u,r,chg})=>`<tr class="row" data-s="${esc(u.symbol)}"><td><b class="mono">${esc(u.symbol)}</b><div class="src">${esc(u.name)}</div></td><td class="n">${f(r.candle.close,r.candle.close<10?4:2)}<div class="src">${esc(u.currency||'')}</div></td><td class="n ${chg>=0?'up':'dn'}">${chg>=0?'+':''}${f(chg*100)}%</td><td>${freshChip(r)}<div class="src">${esc(r.source)} · ${quoteWhen(r)}</div></td></tr>`).join('')
    :'<tr><td colspan="4" class="mute">Sem cotação real agora.</td></tr>';
}
['hmSignals','hmMovers'].forEach(id=>$('#'+id).addEventListener('click',e=>{const t=e.target.closest('[data-s]');if(t){select(t.dataset.s);go('cockpit')}}));
loadStatus();init().catch(e=>{setLive('off','falha ao iniciar');console.error(e)});
