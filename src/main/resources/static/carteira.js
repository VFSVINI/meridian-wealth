/* Minha Carteira (item #15): posições, valorização, alocação, aderência ao perfil e livro de movimentações do usuário logado.
   Só dado real: cotação com fonte e horário, PTAX do Banco Central; o que não tem dado fica "não informado". Depende de app.js e auth.js. */
const CT={sum:null,adh:null,entries:[],filter:'ALL',charts:{},key:null,op:null,loading:false};
const BRL=v=>'R$ '+f(v,2);
const NI='<span class="gap">não informado</span>';
const CAT_COL={RENDA_FIXA_PUBLICA:'#5b7a9f',RENDA_FIXA_PRIVADA:'#3f5a78',FUNDOS:'#8a7448',RENDA_VARIAVEL:'#2f6f60',CRIPTO:'#7d6a3a',CAMBIO_E_COMMODITIES:'#46505f'};
const ENTRY={OPENING:['Abertura','ice'],BUY:['Compra','up'],SELL:['Venda','dn'],ADJUSTMENT:['Ajuste','warn'],WRITE_OFF:['Baixa','mute']};
const FILTERS=[['ALL','Todas'],['BUY','Compra'],['SELL','Venda'],['OPENING','Abertura'],['ADJUSTMENT','Ajuste'],['WRITE_OFF','Baixa']];
const todayBr=()=>new Date().toLocaleDateString('sv-SE',{timeZone:'America/Sao_Paulo'});
const signed=(v,fmt)=>`${Number(v)>0?'+':Number(v)<0?'−':''}${fmt(Math.abs(Number(v)))}`;
const ccy=c=>c==='USD'?'US$ ':'R$ ';

/* decimais saem exatamente como foram digitados (sem passar por double): o servidor trabalha em BigDecimal */
function dec(raw,label,{max=8,required=true}={}){
  const s=String(raw??'').trim().replace(',','.');
  if(!s){if(required)throw new Error('Informe '+label+'.');return null}
  if(!new RegExp('^\\d{1,15}(\\.\\d{1,'+max+'})?$').test(s))throw new Error('Valor inválido em "'+label+'": use números, com até '+max+' casas decimais.');
  return {__dec:s};
}
const ser=o=>JSON.stringify(o,(k,v)=>v&&v.__dec?`@@${v.__dec}@@`:v).replace(/"@@(\d+(?:\.\d+)?)@@"/g,'$1');

function gate(){
  $('#ctBody').hidden=true;$('#ctActions').innerHTML='';
  $('#ctGate').hidden=false;
  $('#ctGate').innerHTML=`<h2>Entre para ver sua carteira</h2><p>Sua carteira é um registro seu, guardado na sua conta: posições, preço médio ponderado, câmbio de cada compra e o livro de movimentações. Cada posição é valorizada com a cotação real (com fonte e horário) e o PTAX do Banco Central.</p>
    <button type="button" class="primary" id="ctLogin">Entrar ou criar conta</button> <span class="src">ou explore com a sessão de demonstração</span>`;
  $('#ctLogin').onclick=openLogin;
}
async function loadCarteira(){
  if(!isLogged()){gate();return}
  $('#ctGate').hidden=true;$('#ctBody').hidden=false;
  $('#ctActions').innerHTML='<button type="button" class="primary" id="ctAdd">Adicionar posição</button>';
  $('#ctAdd').onclick=()=>openOp('open');
  if(CT.loading)return;CT.loading=true;
  try{
    const r=await api('/api/me/portfolio');
    if(r.status===401){gate();return}
    if(!r.ok)throw new Error(await errText(r,'Não foi possível carregar a carteira.'));
    CT.sum=await r.json();
    await Promise.all([loadAdherence(),loadBook()]);
    renderCarteira();
  }catch(e){$('#ctMsg').className='dn';$('#ctMsg').textContent=e.message}
  finally{CT.loading=false}
}
async function loadAdherence(){
  CT.adh=null;
  const prof=SESS&&SESS.user&&SESS.user.profile;
  if(!prof)return;
  const by={};
  CT.sum.positions.forEach(l=>{if(l.currentValueBrl!=null)by[l.position.type]=(by[l.position.type]||0)+Number(l.currentValueBrl)});
  const holdings=Object.entries(by).filter(([,v])=>v>0).map(([type,v])=>({type,valueBrl:v.toFixed(2)}));
  if(!holdings.length)return;
  try{
    const r=await fetch('/api/suitability/adherence',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({profile:prof,holdings})});
    if(r.ok)CT.adh=await r.json();
  }catch(e){}
}
async function loadBook(){
  try{const r=await api('/api/me/portfolio/transactions?limit=200');CT.entries=r.ok?(await r.json()).entries:[]}catch(e){CT.entries=[]}
}

/* ---------- renderização ---------- */
function weights(){
  const total=Number(CT.sum.totalCurrentValueBrl||0);
  return CT.sum.positions.map(l=>({l,w:l.currentValueBrl!=null&&total>0?Number(l.currentValueBrl)/total:null}));
}
function renderCarteira(){
  const s=CT.sum;if(!s)return;
  renderTotal(s);renderAlloc(s);renderAdherence();renderPositions(s);renderBook();renderPnl(s);
}
function renderTotal(s){
  const ws=weights().filter(x=>x.w!=null),hhi=ws.reduce((a,x)=>a+x.w*x.w,0),big=ws.reduce((m,x)=>Math.max(m,x.w),0);
  const withPnl=s.positions.filter(l=>l.unrealizedPnlBrl!=null),pnl=withPnl.reduce((a,l)=>a+Number(l.unrealizedPnlBrl),0);
  const fx=s.fx?`Câmbio PTAX ${f(s.fx.brlPerUsd,4)} (${esc(s.fx.source)}, ${esc(new Date(s.fx.date+'T12:00:00').toLocaleDateString('pt-BR'))})${s.fx.stale?' <span class="fresh dn">desatualizado</span>':''}`:'Sem PTAX do Banco Central agora';
  $('#ctTotal').innerHTML=`<h2>Patrimônio total <span class="tag info">dado real</span></h2>
    ${s.positions.length?`<div class="bigv num">${BRL(s.totalCurrentValueBrl)}</div>
    <div class="src">${fx}</div>
    <div class="kv one"><div><span>Custo total${GL('variation')}</span><span class="num">${BRL(s.totalCostBasisBrl)}</span></div>
    <div><span>Resultado não realizado</span><span class="num ${pnl>=0?'up':'dn'}">${withPnl.length?signed(pnl,BRL):NI}</span></div>
    <div><span>Maior posição${GL('concentration')}</span><span class="num">${ws.length?pc(big,2):NI}</span></div>
    <div><span>HHI${GL('hhi')}</span><span class="num">${ws.length?f(hhi,3):NI}</span></div>
    <div><span>Posições efetivas${GL('effective-positions')}</span><span class="num">${ws.length?f(1/hhi,1):NI}</span></div></div>
    ${s.positionsWithoutBrlValue?`<div class="src" style="margin-top:8px">${s.positionsWithoutBrlValue} posição(ões) sem valor em reais: ${NI}, ficam fora do total e das medidas de concentração.</div>`:''}
    ${s.notes.map(n=>`<div class="src" style="margin-top:6px">${esc(n)}</div>`).join('')}`
    :`<p class="mute">Você ainda não registrou nenhuma posição. Use <b>Adicionar posição</b>: informe o ativo, a quantidade e o preço médio que você pagou.</p>`}`;
}
function chartColors(){const cs=getComputedStyle(document.documentElement),K=n=>cs.getPropertyValue(n).trim();return{text:K('--text'),mute:K('--text-3'),line:K('--line'),gold:K('--gold'),up:K('--up'),down:K('--down'),info:K('--info'),panel:K('--panel')}}
function chart(id){
  const el=document.getElementById(id);if(!el||!window.echarts)return null;
  let c=CT.charts[id];
  if(c&&c.getDom()!==el){c.dispose();c=null}
  if(!c){c=echarts.init(el,null,{renderer:'canvas'});CT.charts[id]=c}
  return c;
}
function renderAlloc(s){
  const el=$('#ctAllocWrap');
  if(!s.byClass.length){el.innerHTML='<p class="mute">Sem posições com valor em reais para mostrar a alocação.</p>';disposeChart('ctTreemap');return}
  el.innerHTML='<div id="ctTreemap" class="echart" role="img" aria-label="Alocação por classe de ativo, em percentual do patrimônio"></div><ul class="legendlist" id="ctAllocList"></ul>';
  const k=chartColors(),c=chart('ctTreemap');
  if(c)c.setOption({animation:false,tooltip:{formatter:p=>`${esc(p.name)}: ${f(p.value,2)}%`},series:[{type:'treemap',roam:false,nodeClick:false,breadcrumb:{show:false},width:'100%',height:'100%',
    itemStyle:{borderColor:k.panel,borderWidth:2,gapWidth:2},label:{show:true,color:'#f3f1ea',fontFamily:'IBM Plex Mono, monospace',fontSize:13,formatter:p=>`${p.name}\n${f(p.value,2)}%`},
    data:s.byClass.map(w=>({name:w.label,value:Number(w.percent),itemStyle:{color:CAT_COL[w.category]||'#46505f'}}))}]},true);
  $('#ctAllocList').innerHTML=s.byClass.map(w=>`<li><i style="background:${CAT_COL[w.category]||'#46505f'}"></i>${esc(w.label)}<b class="num">${f(w.percent,2)}%</b></li>`).join('');
}
function disposeChart(id){if(CT.charts[id]){CT.charts[id].dispose();delete CT.charts[id]}}
function renderAdherence(){
  const el=$('#ctAdh'),prof=SESS&&SESS.user&&SESS.user.profile;
  if(!prof){
    disposeChart('ctRadar');
    el.innerHTML=`<h2>Aderência ao perfil</h2><p>Para comparar sua carteira com a meta de um perfil de investidor, descubra e salve o seu no questionário de suitability.</p><a class="btnlink" href="#/perfil">Descobrir meu perfil</a>`;
    $('#ctRadarCard').hidden=true;return;
  }
  const a=CT.adh;
  if(!a){disposeChart('ctRadar');el.innerHTML=`<h2>Aderência ao perfil ${esc(PROFILE_LABEL[prof]||'')}</h2><p class="mute">Sem posições com valor em reais para calcular a aderência.</p>`;$('#ctRadarCard').hidden=true;return}
  const ST={WITHIN:['Dentro da faixa','up'],ABOVE:['Atenção: acima da meta','warn'],BELOW:['Atenção: abaixo da meta','warn']};
  el.innerHTML=`<h2>Aderência ao perfil ${esc(PROFILE_LABEL[prof]||'')} · nota <b class="num" style="color:var(--gold)">${f(a.score,0)}</b>/100${GL('adherence')}</h2>
    <div class="scroll"><table><thead><tr><th>Classe</th><th class="n">Meta</th><th class="n">Atual</th><th class="n">Desvio</th><th>Leitura</th></tr></thead><tbody>${a.classes.map(c=>`<tr><td>${esc(c.label)}</td><td class="n">${f(c.targetPercent,2)}%</td><td class="n">${f(c.currentPercent,2)}%</td><td class="n">${signed(c.deviationPoints,v=>f(v,2))} p.p.</td><td><span class="chip ${ST[c.status][1]}">${ST[c.status][0]}</span></td></tr>`).join('')}</tbody></table></div>
    <div class="src">Faixa de ±${f(a.bandPoints,0)} p.p. por classe. Mostra só o desvio em relação à meta: não indica o que comprar ou vender. ${esc(a.disclaimer)}</div>`;
  $('#ctRadarCard').hidden=false;
  const k=chartColors(),c=chart('ctRadar'),mx=Math.max(20,Math.ceil(Math.max(...a.classes.map(x=>Math.max(Number(x.currentPercent),Number(x.targetPercent))))/10)*10);
  if(c)c.setOption({animation:false,color:[k.gold,k.info],legend:{bottom:0,textStyle:{color:k.mute},icon:'roundRect'},tooltip:{},
    radar:{radius:'62%',indicator:a.classes.map(x=>({name:SHORT[x.category]||x.label,max:mx})),axisName:{color:k.mute,fontSize:11},splitLine:{lineStyle:{color:k.line}},splitArea:{show:false},axisLine:{lineStyle:{color:k.line}}},
    series:[{type:'radar',symbol:'none',data:[{name:'Meta',value:a.classes.map(x=>Number(x.targetPercent)),lineStyle:{type:'dashed',width:1.5},areaStyle:{opacity:.12}},{name:'Carteira',value:a.classes.map(x=>Number(x.currentPercent)),lineStyle:{width:1.5},areaStyle:{opacity:.18}}]}]},true);
}
function priceCell(l){
  const p=l.price;
  if(!p)return `<div class="src">sem cotação: ${l.basis==='COST'?'avaliada pelo custo':''}</div>`;
  return `<div class="src">${esc(p.source)} · ${esc(p.freshness)}${p.stale?' <span class="fresh dn">desatualizada</span>':''}</div>`;
}
function renderPositions(s){
  const body=s.positions.length?weights().map(({l,w})=>{
    const p=l.position;
    const res=l.unrealizedPnlBrl!=null?`<span class="${Number(l.unrealizedPnlBrl)>=0?'up':'dn'}">${signed(l.unrealizedPnlBrl,BRL)}</span>${l.unrealizedPnlPercentBrl!=null?`<div class="src">${signed(l.unrealizedPnlPercentBrl,v=>f(v,1)+'%')}</div>`:''}`:NI;
    return `<tr><td><b>${esc(p.symbol||p.label)}</b><div class="src">${esc(p.typeLabel)}${p.symbol&&p.label&&p.label!==p.symbol?' · '+esc(p.label.replace(p.symbol+' · ','')):''}</div></td>
      <td class="n">${f(p.quantity,Number(p.quantity)%1?4:0)}</td><td class="n">${ccy(p.currency)}${f(p.averagePrice,2)}${p.currency==='USD'&&p.averageCostBrl!=null?`<div class="src">custo ${BRL(p.averageCostBrl)}</div>`:p.currency==='USD'?`<div class="src">custo em R$ ${NI}</div>`:''}</td>
      <td class="n">${l.currentValueBrl!=null?BRL(l.currentValueBrl):NI}${l.basis==='COST'?'<div class="src">a custo</div>':''}${priceCell(l)}</td>
      <td class="n">${res}</td><td class="n">${w!=null?pc(w,2):NI}</td>
      <td class="acts"><button type="button" data-op="buy" data-id="${esc(p.id)}">Compra</button><button type="button" data-op="sell" data-id="${esc(p.id)}">Venda</button><button type="button" data-op="adjust" data-id="${esc(p.id)}">Ajustar</button><button type="button" class="ghost" data-op="close" data-id="${esc(p.id)}">Baixar</button></td></tr>`}).join('')
    :'<tr><td colspan="7" class="mute">Nenhuma posição ainda.</td></tr>';
  $('#ctPos').innerHTML=`<h2>Posições <span class="src" style="margin-left:auto;text-transform:none;letter-spacing:0">preço médio ponderado na moeda do ativo · valores em reais com 2 casas</span></h2>
    <div class="scroll"><table><thead><tr><th>Ativo</th><th class="n">Qtd.</th><th class="n">Preço médio${GL('average-price')}</th><th class="n">Valor</th><th class="n">Resultado</th><th class="n">Peso</th><th></th></tr></thead><tbody>${body}</tbody></table></div>`;
}
function renderBook(){
  const rows=CT.entries.filter(e=>CT.filter==='ALL'||e.entryType===CT.filter).sort((a,b)=>b.tradeDate.localeCompare(a.tradeDate)||b.createdAt.localeCompare(a.createdAt));
  $('#ctBook').innerHTML=`<h2>Livro de movimentações <span class="chips" role="group" aria-label="Filtrar por tipo">${FILTERS.map(([k,l])=>`<button type="button" class="fchip" data-f="${k}" aria-pressed="${CT.filter===k}">${l}</button>`).join('')}</span></h2>
    <div class="scroll"><table><thead><tr><th>Data</th><th>Tipo</th><th>Ativo</th><th class="n">Quantidade</th><th class="n">Preço</th><th class="n">Resultado realizado</th><th>Origem</th></tr></thead><tbody>${rows.length?rows.map(e=>{
      const t=ENTRY[e.entryType]||[e.entryType,'mute'];
      const origin=e.entryType==='WRITE_OFF'?'baixa sem venda':e.currency==='USD'?(e.fxSource==='PTAX'?`câmbio PTAX de ${esc(new Date(e.fxDate+'T12:00:00').toLocaleDateString('pt-BR'))}`:e.fxSource==='USER'?'câmbio informado por você':'câmbio não informado'):'lançamento manual';
      return `<tr><td class="num">${esc(new Date(e.tradeDate+'T12:00:00').toLocaleDateString('pt-BR'))}</td><td><span class="chip ${t[1]}">${t[0]}</span></td><td>${esc(e.symbol||e.label)}</td>
        <td class="n">${e.entryType==='WRITE_OFF'?'—':f(e.quantity,Number(e.quantity)%1?4:0)}</td><td class="n">${e.entryType==='WRITE_OFF'?'—':ccy(e.currency)+f(e.unitPrice,2)}</td>
        <td class="n">${e.entryType==='SELL'?(e.realizedPnlBrl!=null?`<span class="${Number(e.realizedPnlBrl)>=0?'up':'dn'}">${signed(e.realizedPnlBrl,BRL)}</span>`:NI):'—'}</td><td class="src">${origin}</td></tr>`}).join(''):'<tr><td colspan="7" class="mute">Nenhuma movimentação neste filtro.</td></tr>'}</tbody></table></div>
    <div class="src">Imposto de renda: o apoio ao IR está em <code>/api/me/tax/report</code>; não calcula imposto devido nem DARF. A importação de extratos de corretoras não existe ainda.</div>`;
}
function renderPnl(s){
  const el=$('#ctPnlWrap');
  const items=s.positions.filter(l=>(l.unrealizedPnlPercentBrl??l.unrealizedPnlPercent)!=null).map(l=>({n:l.position.symbol||l.position.label,v:Number(l.unrealizedPnlPercentBrl??l.unrealizedPnlPercent)}));
  const missing=s.positions.length-items.length;
  if(!items.length){disposeChart('ctPnl');el.innerHTML=`<p class="mute">Nenhuma posição com cotação para medir o resultado: ${NI}.</p>`;return}
  el.innerHTML=`<div id="ctPnl" class="echart" style="height:${Math.max(160,items.length*34+40)}px" role="img" aria-label="Ganho ou perda percentual de cada posição sobre o preço médio"></div>
    <div class="src">Resultado não realizado sobre o preço médio (em reais quando há câmbio de compra). ${missing?missing+' posição(ões) sem cotação ou sem custo em reais não entram: '+'ficam como "não informado".':''}</div>`;
  items.sort((a,b)=>a.v-b.v);
  const k=chartColors(),c=chart('ctPnl');
  if(c)c.setOption({animation:false,grid:{left:70,right:48,top:8,bottom:20},tooltip:{formatter:p=>`${esc(p.name)}: ${signed(p.value,v=>f(v,1)+'%')}`},
    xAxis:{type:'value',axisLabel:{color:k.mute,formatter:'{value}%'},splitLine:{lineStyle:{color:k.line}}},yAxis:{type:'category',data:items.map(x=>x.n),axisLabel:{color:k.text,fontFamily:'IBM Plex Mono, monospace'},axisLine:{lineStyle:{color:k.line}}},
    series:[{type:'bar',data:items.map(x=>({value:x.v,itemStyle:{color:x.v>=0?k.up:k.down}})),label:{show:true,position:'right',color:k.mute,formatter:p=>signed(p.value,v=>f(v,1)+'%')}}]},true);
}

/* ---------- operações ---------- */
function newKey(){return (crypto.randomUUID?crypto.randomUUID():String(Date.now())+Math.random().toString(16).slice(2)).slice(0,64)}
const lineOf=id=>CT.sum.positions.find(l=>l.position.id===id);
function field(id,label,attrs='',hint=''){return `<label>${label}<input id="${id}" ${attrs}></label>${hint?`<p class="src">${hint}</p>`:''}`}
function assetOptions(){
  const inv=universe.filter(x=>x.assetClass!=='Índice'&&(x.currency==='BRL'||x.currency==='USD'));
  return `<optgroup label="Ativos do painel (cotação real)">${inv.map(x=>`<option value="S:${esc(x.symbol)}" data-ccy="${esc(x.currency)}">${esc(x.symbol)} · ${esc(x.name)} (${esc(x.currency)})</option>`).join('')}</optgroup>
   <optgroup label="Outros tipos (sem cotação: avaliados pelo custo)">${SPTYPES.map(t=>`<option value="T:${esc(t.key)}">${esc(t.label)}</option>`).join('')}</optgroup>`;
}
const TITLES={open:'Adicionar posição',buy:'Registrar compra',sell:'Registrar venda',adjust:'Ajustar posição',close:'Baixar posição'};
function openOp(kind,id){
  const l=id?lineOf(id):null,p=l&&l.position;
  CT.op={kind,id,key:newKey(),version:p?p.version:null};
  const usd=p&&p.currency==='USD';
  let h=`<p class="src">${kind==='open'?'Informe o que você já tem: o preço médio que pagou.':kind==='close'?'A baixa remove a posição sem venda e sem resultado realizado.':`${esc(p.symbol||p.label)} · posição atual ${f(p.quantity,Number(p.quantity)%1?4:0)} a ${ccy(p.currency)}${f(p.averagePrice,2)}`}</p>`;
  if(kind==='open'){
    h+=`<label>Ativo <select id="opAsset">${assetOptions()}</select></label><div id="opTyped" hidden>${field('opLabel','Nome do ativo','maxlength="80" autocomplete="off"')}<label>Moeda <select id="opCcy"><option value="BRL">BRL (real)</option><option value="USD">USD (dólar)</option></select></label></div>`;
    h+=field('opQty','Quantidade','inputmode="decimal" autocomplete="off"')+field('opPrice','Preço médio pago','inputmode="decimal" autocomplete="off"');
  }else if(kind==='buy'||kind==='sell'){
    h+=field('opQty','Quantidade','inputmode="decimal" autocomplete="off"')+field('opPrice',kind==='buy'?'Preço de compra':'Preço de venda','inputmode="decimal" autocomplete="off"')+field('opFees','Taxas e corretagem (opcional)','inputmode="decimal" autocomplete="off"',kind==='buy'?'As taxas entram no preço médio.':'As taxas abatem o valor da venda.');
  }else if(kind==='adjust'){
    h+=field('opQty','Nova quantidade',`inputmode="decimal" value="${esc(p.quantity)}"`)+field('opPrice','Novo preço médio',`inputmode="decimal" value="${esc(p.averagePrice)}"`);
  }
  h+=field('opDate','Data da operação',`type="date" max="${todayBr()}" value="${todayBr()}"`);
  h+=`<div id="opFxWrap" ${usd||kind==='open'?'':'hidden'}>${field('opFx','Câmbio (R$ por US$), opcional','inputmode="decimal" autocomplete="off"','Em branco: usa o PTAX de venda do Banco Central do último dia útil até a data.')}</div>`;
  $('#opTitle').textContent=TITLES[kind];$('#opBody').innerHTML=h;$('#opErr').textContent='';$('#opGo').disabled=false;$('#opGo').textContent=kind==='close'?'Baixar posição':'Salvar';
  if(kind==='open'){
    const sync=()=>{const v=$('#opAsset').value,o=$('#opAsset').selectedOptions[0],typed=v.startsWith('T:');$('#opTyped').hidden=!typed;
      const c=typed?$('#opCcy').value:o.dataset.ccy;$('#opFxWrap').hidden=c!=='USD'};
    $('#opAsset').onchange=sync;$('#opCcy').onchange=sync;sync();
  }
  lastFocus=document.activeElement;$('#op').showModal();(($('#opAsset')||$('#opQty')||$('#opDate'))).focus();
}
async function submitOp(e){
  e.preventDefault();const o=CT.op,err=$('#opErr');err.textContent='';
  let url,method='POST',body=null;
  try{
    const date=$('#opDate').value;if(!date)throw new Error('Informe a data da operação.');
    const fxOn=!$('#opFxWrap').hidden,fx=fxOn?dec($('#opFx').value,'o câmbio',{max:6,required:false}):null;
    const base='/api/me/portfolio/positions';
    if(o.kind==='open'){
      const v=$('#opAsset').value,typed=v.startsWith('T:');
      if(typed&&!$('#opLabel').value.trim())throw new Error('Informe o nome do ativo.');
      body=ser({...(typed?{type:v.slice(2),label:$('#opLabel').value.trim(),currency:$('#opCcy').value}:{symbol:v.slice(2)}),quantity:dec($('#opQty').value,'a quantidade'),averagePrice:dec($('#opPrice').value,'o preço médio',{max:6}),tradeDate:date,...(fx?{fxRate:fx}:{})});
      url=base;
    }else if(o.kind==='buy'||o.kind==='sell'){
      body=ser({quantity:dec($('#opQty').value,'a quantidade'),unitPrice:dec($('#opPrice').value,'o preço',{max:6}),fees:dec($('#opFees').value,'as taxas',{max:6,required:false})||undefined,tradeDate:date,...(fx?{fxRate:fx}:{}),expectedVersion:o.version});
      url=`${base}/${o.id}/${o.kind==='buy'?'purchases':'sales'}`;
    }else if(o.kind==='adjust'){
      body=ser({quantity:dec($('#opQty').value,'a quantidade'),averagePrice:dec($('#opPrice').value,'o preço médio',{max:6}),tradeDate:date,...(fx?{fxRate:fx}:{}),expectedVersion:o.version});
      url=`${base}/${o.id}`;method='PUT';
    }else{url=`${base}/${o.id}?expectedVersion=${encodeURIComponent(o.version)}&tradeDate=${encodeURIComponent(date)}`;method='DELETE'}
  }catch(x){err.textContent=x.message;return}
  const go2=$('#opGo');go2.disabled=true;go2.textContent='Salvando…';
  try{
    const r=await api(url,{method,headers:{'Idempotency-Key':o.key,...(body?{'Content-Type':'application/json'}:{})},body});
    if(!r.ok){
      const d=await r.json().catch(()=>({}));
      const msg=d.code==='VERSION_CONFLICT'?'A posição mudou em outra aba ou dispositivo. Feche, recarregue a carteira e tente de novo.':d.code==='FX_UNAVAILABLE'?'O PTAX do Banco Central não está disponível agora. Informe o câmbio manualmente ou tente mais tarde.':(d.error||'Não foi possível salvar.');
      throw new Error(msg);
    }
    const d=await r.json();
    $('#op').close();
    $('#ctMsg').className='up';$('#ctMsg').textContent=d.replayed?'Operação já registrada (repetição ignorada).':d.closed?'Posição baixada.':'Operação registrada.';
    await loadCarteira();
  }catch(x){err.textContent=x.message;go2.disabled=false;go2.textContent='Tentar de novo'}
}
$('#opForm').addEventListener('submit',submitOp);
$('#opX').onclick=()=>$('#op').close();
$('#op').addEventListener('click',e=>{if(e.target===$('#op'))$('#op').close()});
$('#op').addEventListener('close',()=>{if(lastFocus&&lastFocus.focus)try{lastFocus.focus()}catch(e){}});
$('#ctPos').addEventListener('click',e=>{const b=e.target.closest('button[data-op]');if(b)openOp(b.dataset.op,b.dataset.id)});
$('#ctBook').addEventListener('click',e=>{const b=e.target.closest('button[data-f]');if(b){CT.filter=b.dataset.f;renderBook()}});

/* ---------- ciclo de vida ---------- */
function onCarteiraRoute(){if((location.hash.match(/^#\/([a-z]+)/)||[])[1]==='carteira')loadCarteira()}
addEventListener('hashchange',onCarteiraRoute);
document.addEventListener('mw-auth',()=>{if(!$('[data-page="carteira"]').hidden||(location.hash||'').startsWith('#/carteira'))loadCarteira()});
addEventListener('resize',()=>Object.values(CT.charts).forEach(c=>c.resize()));
new MutationObserver(()=>{if(CT.sum)renderCarteira()}).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
new ResizeObserver(()=>Object.values(CT.charts).forEach(c=>c.resize())).observe(document.body);
onCarteiraRoute();
