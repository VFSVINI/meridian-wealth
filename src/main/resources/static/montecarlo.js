/* Monte Carlo e metas (item #18): projeção patrimonial, SEMPRE rotulada projeção. Dois modos: premissas manuais (pública) e minha carteira
   (reamostragem de retornos reais; exige login). Nada daqui é cotação nem promessa de retorno. Depende de app.js, auth.js e carteira.js. */
const MC={mode:'manual',data:null,busy:false,charts:{}};
const MC_FIELDS={manual:[['initial','Patrimônio inicial (R$)','100000','inputmode="decimal"'],['monthlyContribution','Aporte mensal (R$)','1000','inputmode="decimal"'],['horizonYears','Horizonte (anos)','15','type="range" min="1" max="40" step="1"'],
  ['expectedReturnPercent','Retorno esperado (% ao ano)','10','inputmode="decimal"'],['volatilityPercent','Volatilidade (% ao ano)','15','inputmode="decimal"'],['goal','Meta de patrimônio (R$, opcional)','','inputmode="decimal"']],
 portfolio:[['monthlyContribution','Aporte mensal (R$)','0','inputmode="decimal"'],['horizonYears','Horizonte (anos)','10','type="range" min="1" max="40" step="1"'],['goal','Meta de patrimônio (R$, opcional)','','inputmode="decimal"']]};
function mcForm(){
  const el=$('#mcForm');
  el.innerHTML=MC_FIELDS[MC.mode].map(([id,label,val,attr])=>`<label>${label}${id==='horizonYears'?` <b class="num" id="mcYears">${val} anos</b>`:''}<input id="mc_${id}" value="${val}" ${attr}></label>`).join('')
    +(MC.mode==='portfolio'?'<p class="src">O patrimônio inicial é o valor atual da sua carteira em reais; os retornos vêm do histórico real dos seus ativos.</p>':'<p class="src">Premissas suas: ajuste-as e veja como o cone muda. Valores de exemplo não são previsão.</p>')
    +'<button type="submit" class="primary" id="mcGo">Projetar</button> <span class="dn" id="mcErr" role="alert"></span>';
  const y=$('#mc_horizonYears');y.addEventListener('input',()=>{$('#mcYears').textContent=y.value+' anos'});
}
function mcTabs(){
  document.querySelectorAll('button[data-mcm]').forEach(b=>b.setAttribute('aria-selected',String(b.dataset.mcm===MC.mode)));
}
function mcBody(){
  const b={};
  MC_FIELDS[MC.mode].forEach(([id])=>{const v=($('#mc_'+id).value||'').trim().replace(',','.');if(v!=='')b[id]=id==='horizonYears'?Number(v):v});
  return b;
}
async function mcRun(e){
  e.preventDefault();const err=$('#mcErr'),go2=$('#mcGo');err.textContent='';
  if(MC.mode==='portfolio'&&!isLogged()){openLogin();return}
  go2.disabled=true;go2.textContent='Projetando…';
  try{
    const body=mcBody();
    const r=MC.mode==='portfolio'?await api('/api/me/risk/monte-carlo',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
      :await fetch('/api/risk/monte-carlo',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
    const d=await r.json().catch(()=>({}));
    if(!r.ok)throw new Error(d.error||'Não foi possível projetar agora.');
    MC.data=d;mcRender();
  }catch(x){err.textContent=x.message}
  finally{go2.disabled=false;go2.textContent='Projetar'}
}
function mcChart(id){
  const el=document.getElementById(id);if(!el||!window.echarts)return null;
  let c=MC.charts[id];if(c&&c.getDom()!==el){c.dispose();c=null}
  if(!c){c=echarts.init(el);MC.charts[id]=c}
  return c;
}
const mcN=v=>Number(v);
function mcRender(){
  const d=MC.data;if(!d)return;
  setTimeout(mcEmit,0);   /* o 3D refaz a superfície com o mesmo resultado */
  const k=chartColors(),pts=d.points,years=d.months/12;
  const base=pts.map(p=>mcN(p.p5));
  const diff=(a,b)=>pts.map(p=>Math.max(0,mcN(p[b])-mcN(p[a])));
  const d1=diff('p5','p25'),d2=diff('p25','p50'),d3=diff('p50','p75'),d4=diff('p75','p95');
  $('#mcResult').hidden=false;
  $('#mcKpi').innerHTML=`<div class="mckpi"><span class="src">${d.goal!=null?'Probabilidade de estar na meta no fim de '+years+' anos':'Mediana no fim de '+years+' anos'}</span>
    <b class="num big" style="color:var(--gold)">${d.goal!=null?f(d.goalProbabilityPercent,0)+'%':BRL(pts[pts.length-1].p50)}</b>
    <span class="src">${d.goal!=null?'meta de '+BRL(d.goal)+' · ':''}probabilidade de terminar abaixo do que foi investido (${BRL(d.totalInvested)}): ${f(d.probabilityBelowInvestedPercent,0)}%</span></div>`;
  const c=mcChart('mcCone');
  if(c)c.setOption({animation:false,grid:{left:70,right:24,top:24,bottom:36},tooltip:{trigger:'axis',valueFormatter:v=>BRL(v)},
    xAxis:{type:'category',boundaryGap:false,data:pts.map(p=>p.month),name:'anos',nameLocation:'middle',nameGap:24,axisLabel:{color:k.mute,interval:Math.max(0,Math.round(years/8)*12-1),formatter:v=>String(Number(v)/12)},axisTick:{show:false},axisLine:{lineStyle:{color:k.line}}},
    yAxis:{type:'value',axisLabel:{color:k.mute,formatter:v=>v>=1e6?f(v/1e6,1)+' mi':f(v/1e3,0)+' mil'},splitLine:{lineStyle:{color:k.line}}},
    series:[{name:'P5',type:'line',stack:'band',symbol:'none',lineStyle:{opacity:0},data:base},
      {name:'P5 a P25',type:'line',stack:'band',symbol:'none',lineStyle:{opacity:0},areaStyle:{color:k.gold,opacity:.12},data:d1},
      {name:'P25 a P50',type:'line',stack:'band',symbol:'none',lineStyle:{opacity:0},areaStyle:{color:k.gold,opacity:.26},data:d2},
      {name:'P50 a P75',type:'line',stack:'band',symbol:'none',lineStyle:{opacity:0},areaStyle:{color:k.gold,opacity:.26},data:d3},
      {name:'P75 a P95',type:'line',stack:'band',symbol:'none',lineStyle:{opacity:0},areaStyle:{color:k.gold,opacity:.12},data:d4},
      {name:'Mediana (P50)',type:'line',symbol:'none',lineStyle:{color:k.gold,width:2},data:pts.map(p=>mcN(p.p50)),
        markLine:d.goal!=null?{silent:true,symbol:'none',lineStyle:{color:k.gold,type:'dashed'},label:{formatter:'meta',color:k.gold},data:[{yAxis:mcN(d.goal)}]}:undefined}]},true);
  // distribuição do patrimônio final
  const h=d.histogram,mids=h.counts.map((_,i)=>(mcN(h.edges[i])+mcN(h.edges[i+1]))/2);
  const ch=mcChart('mcHist');
  if(ch)ch.setOption({animation:false,grid:{left:50,right:16,top:16,bottom:30},tooltip:{formatter:p=>`${BRL(h.edges[p.dataIndex])} a ${BRL(h.edges[p.dataIndex+1])}: ${p.value} trajetórias`},
    xAxis:{type:'category',data:mids.map(v=>f(v/1000,0)+' mil'),axisLabel:{color:k.mute,interval:Math.ceil(mids.length/5)}},yAxis:{type:'value',axisLabel:{color:k.mute},splitLine:{lineStyle:{color:k.line}}},
    series:[{type:'bar',data:h.counts,itemStyle:{color:k.gold},barCategoryGap:'8%'}]},true);
  // probabilidade da meta ano a ano
  const cg=mcChart('mcGoal');
  if(d.goal!=null&&d.goalProbabilityByYearPercent&&cg){
    $('#mcGoalCard').hidden=false;
    cg.setOption({animation:false,grid:{left:46,right:16,top:16,bottom:30},tooltip:{valueFormatter:v=>f(v,0)+'%'},xAxis:{type:'category',name:'anos',nameLocation:'middle',nameGap:22,data:d.goalProbabilityByYearPercent.map((_,i)=>i),axisLabel:{color:k.mute,interval:Math.ceil(d.goalProbabilityByYearPercent.length/8)-1}},
      yAxis:{type:'value',min:0,max:100,axisLabel:{color:k.mute,formatter:'{value}%'},splitLine:{lineStyle:{color:k.line}}},series:[{type:'line',data:d.goalProbabilityByYearPercent.map(mcN),areaStyle:{color:k.gold,opacity:.15},lineStyle:{color:k.gold},symbol:'none'}]},true);
  }else $('#mcGoalCard').hidden=true;
  // tabela equivalente (o gráfico não é a única forma de ler)
  const yearly=pts.filter(p=>p.month%12===0&&p.month>0);
  $('#mcTable').innerHTML=`<div class="scroll"><table><thead><tr><th>Ano</th><th class="n">P5</th><th class="n">P25</th><th class="n">P50 (mediana)</th><th class="n">P75</th><th class="n">P95</th></tr></thead><tbody>${yearly.map(p=>`<tr><td>${p.month/12}</td><td class="n">${BRL(p.p5)}</td><td class="n">${BRL(p.p25)}</td><td class="n">${BRL(p.p50)}</td><td class="n">${BRL(p.p75)}</td><td class="n">${BRL(p.p95)}</td></tr>`).join('')}</tbody></table></div>
    <div class="src">Percentis de ${d.paths.toLocaleString('pt-BR')} trajetórias simuladas, em reais nominais. Semente ${d.seed}: a mesma entrada dá a mesma projeção.</div>`;
  $('#mcAssume').innerHTML=d.assumptions.map(a=>`<li>${esc(a)}</li>`).join('');
  const cov=$('#mcCoverage');
  if(d.coverage&&d.coverage.length){
    cov.hidden=false;
    cov.innerHTML=`<h2>O que entrou na projeção <span class="tag info">dado real</span></h2>${d.statistics?`<div class="src">Histórico em comum: ${d.statistics.commonDays} pregões (${esc(d.statistics.from)} a ${esc(d.statistics.to)}). Da parte com histórico: retorno anualizado ${f(d.statistics.annualReturnPercent,1)}% e volatilidade ${f(d.statistics.annualVolatilityPercent,1)}%. Cobertura: ${f(d.coveredPercent,1)}% do patrimônio.</div>`:''}
      <div class="scroll"><table><thead><tr><th>Posição</th><th class="n">Valor</th><th class="n">Peso</th><th>Tratamento</th><th>Motivo</th></tr></thead><tbody>${d.coverage.map(c=>`<tr><td>${esc(c.label)}</td><td class="n">${BRL(c.valueBrl)}</td><td class="n">${f(c.weightPercent,1)}%</td><td><span class="chip ${c.treatment==='HISTORICO'?'up':'warn'}">${c.treatment==='HISTORICO'?'histórico real':'parada no valor atual'}</span></td><td class="src">${esc(c.reason)}</td></tr>`).join('')}</tbody></table></div>`;
  }else cov.hidden=true;
  $('#mcNote').textContent=d.disclaimer;
  $('#mcLabel').textContent=d.label;
}
document.addEventListener('click',e=>{
  const t=e.target.closest('button[data-mcm]');
  if(t){MC.mode=t.dataset.mcm;mcTabs();mcForm();MC.data=null;$('#mcResult').hidden=true;
    if(MC.mode==='portfolio'&&!isLogged())$('#mcErr').textContent='Entre na sua conta para projetar a carteira salva.'}
});
$('#mcForm').addEventListener('submit',mcRun);
addEventListener('resize',()=>Object.values(MC.charts).forEach(c=>c.resize()));
new MutationObserver(()=>{if(MC.data)mcRender()}).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
/* o Monte Carlo em 3D (montecarlo3d.js) lê o MESMO resultado que os gráficos 2D, por este evento */
function mcEmit(){if(MC.data)document.dispatchEvent(new CustomEvent('mw:mc-data',{detail:{data:MC.data}}))}
document.addEventListener('mw:mc-need',mcEmit);
function onMonteCarloRoute(){if((location.hash.match(/^#\/([a-z]+)/)||[])[1]==='montecarlo')setTimeout(()=>Object.values(MC.charts).forEach(c=>c.resize()),50)}
addEventListener('hashchange',onMonteCarloRoute);
mcTabs();mcForm();
