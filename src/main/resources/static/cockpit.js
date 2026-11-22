/* Cockpit do ativo (item #17): gráfico com TradingView Lightweight Charts (Apache-2.0, servido pelo projeto): candles, volume, SMA50, bandas de Bollinger,
   zonas e níveis do motor de timing como linhas de preço. Só dado real: as séries vêm dos candles reais carregados; Bollinger e SMA50 são calculadas aqui
   a partir deles (mesma conta do motor: 20 barras e 2σ; 50 barras). Depende de app.js (candles, A, sel, $, f, esc, go).
   Gráfico © TradingView, Inc. (https://www.tradingview.com/), Apache License 2.0. */
const LW=window.LightweightCharts;
const CK={chart:null,candle:null,volume:null,bbU:null,bbL:null,bbM:null,sma:null,lines:[],sym:null,key:null,sig:'',range:'6M',show:{bb:true,sma:true,zones:true},byTime:new Map()};
const CK_DAYS={'1M':31,'3M':92,'6M':183,'1A':366};
function ckColors(){
  const cs=getComputedStyle(document.documentElement),K=n=>cs.getPropertyValue(n).trim();
  return{text:K('--text-2'),mute:K('--text-3'),line:K('--line'),up:K('--up'),down:K('--down'),warn:K('--warn'),info:K('--info'),gold:K('--gold')};
}
const withAlpha=(hex,a)=>/^#[0-9a-f]{6}$/i.test(hex)?hex+a:hex;
function ckEnsure(){
  if(CK.chart)return true;
  const el=document.getElementById('chart');
  if(!LW||!el||!el.clientWidth)return false;
  const k=ckColors();
  CK.chart=LW.createChart(el,{autoSize:true,layout:{background:{type:'solid',color:'transparent'},attributionLogo:false,textColor:k.text,fontFamily:'IBM Plex Mono, ui-monospace, monospace',fontSize:11},
    grid:{vertLines:{color:k.line},horzLines:{color:k.line}},rightPriceScale:{borderColor:k.line},timeScale:{borderColor:k.line,timeVisible:true,secondsVisible:false,rightOffset:4},
    crosshair:{mode:LW.CrosshairMode.Normal},localization:{locale:'pt-BR'}});
  CK.candle=CK.chart.addCandlestickSeries({upColor:k.up,downColor:k.down,wickUpColor:k.up,wickDownColor:k.down,borderVisible:false,priceLineVisible:true});
  CK.volume=CK.chart.addHistogramSeries({priceFormat:{type:'volume'},priceScaleId:'vol',lastValueVisible:false,priceLineVisible:false});
  CK.chart.priceScale('vol').applyOptions({scaleMargins:{top:.82,bottom:0}});
  const line=(color,w=1,style=0)=>CK.chart.addLineSeries({color,lineWidth:w,lineStyle:style,lastValueVisible:false,priceLineVisible:false,crosshairMarkerVisible:false});
  CK.bbU=line(k.info,1,2);CK.bbL=line(k.info,1,2);CK.bbM=line(withAlpha(k.info,'88'),1,0);CK.sma=line(k.gold,2,0);
  CK.chart.subscribeCrosshairMove(p=>inspect(p&&p.time?p.time:null));
  return true;
}
/* séries derivadas dos candles reais */
function bollinger(cs){
  const out=[];
  for(let i=19;i<cs.length;i++){
    let m=0;for(let j=i-19;j<=i;j++)m+=cs[j].close;m/=20;
    let v=0;for(let j=i-19;j<=i;j++)v+=(cs[j].close-m)**2;const sd=Math.sqrt(v/19);
    out.push({t:cs[i].t,m,u:m+2*sd,l:m-2*sd});
  }
  return out;
}
function smaSeries(cs,n=50){
  const out=[];let s=0;
  for(let i=0;i<cs.length;i++){s+=cs[i].close;if(i>=n)s-=cs[i-n].close;if(i>=n-1)out.push({time:cs[i].t,value:s/n})}
  return out;
}
function ckBars(){
  const out=[];let last=-1;
  candles.forEach(c=>{const t=Math.floor(c.time/1000);if(t>last){out.push({...c,t});last=t}});
  return out;
}
function applyTheme(){
  const k=ckColors();
  CK.chart.applyOptions({layout:{textColor:k.text},grid:{vertLines:{color:k.line},horzLines:{color:k.line}},rightPriceScale:{borderColor:k.line},timeScale:{borderColor:k.line}});
  CK.candle.applyOptions({upColor:k.up,downColor:k.down,wickUpColor:k.up,wickDownColor:k.down});
  CK.bbU.applyOptions({color:k.info});CK.bbL.applyOptions({color:k.info});CK.bbM.applyOptions({color:withAlpha(k.info,'88')});CK.sma.applyOptions({color:k.gold});
}
function applyRange(){
  const bars=CK.bars;if(!bars||!bars.length)return;
  const to=bars[bars.length-1].t,from=to-CK_DAYS[CK.range]*86400;
  CK.chart.timeScale().setVisibleRange({from:Math.max(from,bars[0].t),to:to+86400*2});
}
function applyVisibility(){
  [CK.bbU,CK.bbL,CK.bbM].forEach(s=>s.applyOptions({visible:CK.show.bb}));CK.sma.applyOptions({visible:CK.show.sma});
  syncLines(true);
}
/* níveis do motor de timing: zona de entrada, zona de realização, alvos e invalidação */
function levelsSig(){return A?[sel,A.signal,A.score,A.entryLow,A.entryHigh,A.invalidation,A.target1,A.target2,A.price,A.atr,CK.show.zones].join('|'):''}
function syncLines(force){
  const sig=levelsSig();
  if(!force&&sig===CK.sig)return;
  CK.sig=sig;
  CK.lines.forEach(l=>CK.candle.removePriceLine(l));CK.lines=[];
  if(!A||!CK.show.zones)return;
  const k=ckColors(),add=(price,color,title,style=2)=>CK.lines.push(CK.candle.createPriceLine({price:Number(price),color,lineWidth:1,lineStyle:style,axisLabelVisible:true,title}));
  if(A.score>=20&&A.signal!=='HIGH_RISK_WAIT'){add(A.entryLow,k.up,'Entrada');add(A.entryHigh,k.up,'Entrada')}
  if(A.signal==='TAKE_PROFIT_WINDOW'||A.signal==='SELL_EXIT_SETUP'){add(A.price-A.atr,k.warn,'Realização');add(A.price+A.atr,k.warn,'Realização')}
  add(A.invalidation,k.down,'Invalidação');add(A.target1,k.warn,'Alvo 1');add(A.target2,k.warn,'Alvo 2');
}
function draw(){
  if(!ckEnsure())return;
  const bars=ckBars();CK.bars=bars;
  if(!bars.length){[CK.candle,CK.volume,CK.bbU,CK.bbL,CK.bbM,CK.sma].forEach(s=>s.setData([]));CK.sym=null;CK.key=null;renderTable();inspect(null);return}
  const k=ckColors(),key=bars.length+':'+bars[0].t,fresh=CK.sym!==sel;
  if(fresh||CK.key!==key){
    CK.candle.setData(bars.map(c=>({time:c.t,open:c.open,high:c.high,low:c.low,close:c.close})));
    CK.volume.setData(bars.map(c=>({time:c.t,value:c.volume||0,color:withAlpha(c.close>=c.open?k.up:k.down,'66')})));
    const bb=bollinger(bars);
    CK.bbU.setData(bb.map(b=>({time:b.t,value:b.u})));CK.bbL.setData(bb.map(b=>({time:b.t,value:b.l})));CK.bbM.setData(bb.map(b=>({time:b.t,value:b.m})));
    CK.sma.setData(smaSeries(bars));
    CK.byTime=new Map(bb.map(b=>[b.t,b]));
    CK.key=key;if(fresh){CK.sym=sel;applyRange()}
  }else{
    // tick: só a última barra muda
    const c=bars[bars.length-1],bb=bollinger(bars.slice(-20)),b=bb[bb.length-1];
    CK.candle.update({time:c.t,open:c.open,high:c.high,low:c.low,close:c.close});
    CK.volume.update({time:c.t,value:c.volume||0,color:withAlpha(c.close>=c.open?k.up:k.down,'66')});
    if(b){CK.bbU.update({time:c.t,value:b.u});CK.bbL.update({time:c.t,value:b.l});CK.bbM.update({time:c.t,value:b.m});CK.byTime.set(c.t,b)}
    const s=smaSeries(bars.slice(-50));if(s.length)CK.sma.update(s[s.length-1]);
  }
  applyTheme();syncLines(false);renderTable();
  if(!CK.hovering)inspect(null);
}
/* inspetor do candle: o que está sob o cursor, ou a última barra */
function inspect(time){
  const el=document.getElementById('insp');if(!el)return;
  CK.hovering=time!=null;
  const bars=CK.bars||[];if(!bars.length){el.innerHTML='<div class="mute">Sem dado real para este ativo.</div>';return}
  const c=time!=null?bars.find(b=>b.t===time):bars[bars.length-1];if(!c){return}
  const b=CK.byTime.get(c.t),d=new Date(c.t*1000),p=c.close<10?4:2;
  const row=(k,v,cls='')=>`<div><span>${k}</span><span class="num ${cls}">${v}</span></div>`;
  el.innerHTML=`<div class="src">${esc(d.toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}))}${time==null?' (última barra)':''}</div><div class="kv one">
    ${row('Abertura',f(c.open,p))}${row('Máxima',f(c.high,p))}${row('Mínima',f(c.low,p))}${row('Fechamento',f(c.close,p),c.close>=c.open?'up':'dn')}${row('Volume',c.volume?f(c.volume,0):'<span class="gap">não informado</span>')}
    ${row('Banda superior',b?f(b.u,p):'<span class="gap">n/d</span>')}${row('Banda inferior',b?f(b.l,p):'<span class="gap">n/d</span>')}</div>`;
}
/* alternativa em tabela (o gráfico não é o único meio de ler os preços) */
function renderTable(){
  const wrap=document.getElementById('ckTable');if(!wrap||wrap.hidden)return;
  const bars=(CK.bars||[]).slice(-30).reverse();
  wrap.innerHTML=bars.length?`<div class="scroll"><table><thead><tr><th>Data</th><th class="n">Abertura</th><th class="n">Máxima</th><th class="n">Mínima</th><th class="n">Fechamento</th><th class="n">Volume</th></tr></thead><tbody>${bars.map(c=>{const p=c.close<10?4:2;
    return `<tr><td class="num">${esc(new Date(c.t*1000).toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',year:'2-digit',hour:'2-digit',minute:'2-digit'}))}</td><td class="n">${f(c.open,p)}</td><td class="n">${f(c.high,p)}</td><td class="n">${f(c.low,p)}</td><td class="n ${c.close>=c.open?'up':'dn'}">${f(c.close,p)}</td><td class="n">${c.volume?f(c.volume,0):'—'}</td></tr>`}).join('')}</tbody></table></div><div class="src">Últimas 30 barras.</div>`:'<div class="mute">Sem dado real.</div>';
}
document.addEventListener('click',e=>{
  const r=e.target.closest('button[data-ckr]');
  if(r){CK.range=r.dataset.ckr;document.querySelectorAll('button[data-ckr]').forEach(b=>b.setAttribute('aria-pressed',String(b===r)));if(CK.chart)applyRange();return}
  const t=e.target.closest('button[data-ckt]');
  if(t){const on=t.getAttribute('aria-pressed')!=='true';CK.show[t.dataset.ckt]=on;t.setAttribute('aria-pressed',String(on));if(CK.chart)applyVisibility();return}
  if(e.target.closest('#ckTableBtn')){const w=$('#ckTable'),b=$('#ckTableBtn');w.hidden=!w.hidden;b.setAttribute('aria-expanded',String(!w.hidden));b.textContent=w.hidden?'Ver como tabela':'Ocultar tabela';renderTable();return}
  if(e.target.closest('#ckExport')){
    if(!CK.chart||!(CK.bars||[]).length)return;
    CK.chart.takeScreenshot().toBlob(blob=>{const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`${sel||'ativo'}-grafico.png`;document.body.appendChild(a);a.click();a.remove();URL.revokeObjectURL(url)});return}
  if(e.target.closest('#ckSim')){
    go('simulador');
    const pick=()=>{const s=$('#smAsset');if(!s)return false;const o=[...s.options].find(x=>x.value==='S:'+sel);if(o){s.value=o.value;const sd=$('#smSide');if(sd)sd.focus();$('#smAmt')&&$('#smAmt').focus()}return true};
    if(!pick())setTimeout(pick,800);
  }
});
new MutationObserver(()=>{if(CK.chart){applyTheme();syncLines(true)}}).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
