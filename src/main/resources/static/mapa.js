/* Mapa de ativos reais (item #16): os imóveis dos fundos imobiliários com dado curado (GET /api/composition), sobre o contorno dos estados do IBGE.
   Sem mapa-base de terceiros: nenhuma requisição sai do servidor do projeto, o IP de quem abre a tela não vai a provedor de tiles e a CSP segue fechada.
   Só dado real: imóvel sem coordenada aparece na lista com o motivo e nunca ganha marcador. Depende de app.js (helpers $, esc, f) e do Leaflet. */
/* o app.js já usa o identificador global L (layout do gráfico de preço): o Leaflet é lido de window.L */
const LF=window.L;
const MP={funds:null,geo:null,map:null,layers:{states:null,pins:null},fund:'ALL',uf:'ALL',kind:'ALL',limit:60,loading:false,markers:new Map(),pending:null};
const MP_CLUSTER_PX=36,MP_CLUSTER_MAX_ZOOM=11; // agrupa o que ficaria a menos de 36 px um do outro, até o zoom 11
const UF_BY_CODE={11:'RO',12:'AC',13:'AM',14:'RR',15:'PA',16:'AP',17:'TO',21:'MA',22:'PI',23:'CE',24:'RN',25:'PB',26:'PE',27:'AL',28:'SE',29:'BA',31:'MG',32:'ES',33:'RJ',35:'SP',41:'PR',42:'SC',43:'RS',50:'MS',51:'MT',52:'GO',53:'DF'};
const MP_KIND={GALPAO_LOGISTICO:'Galpão logístico',EDIFICIO_COMERCIAL:'Edifício comercial',SHOPPING:'Shopping center',LAJE_CORPORATIVA:'Laje corporativa'};
const MP_FUND_COL=['--gold','--info','--up','--warn'];
const mpKind=k=>k?(MP_KIND[k]||k.replace(/_/g,' ').toLowerCase()):'Tipo não informado';
function mpColors(){const cs=getComputedStyle(document.documentElement),K=n=>cs.getPropertyValue(n).trim();return{line:K('--line-2'),panel:K('--panel-2'),text:K('--text'),mute:K('--text-3'),gold:K('--gold'),funds:MP_FUND_COL.map(K)}}
const mpFundColor=()=>mpColors().gold;   /* com centenas de fundos a cor por fundo não ajuda a ler: o marcador é dourado e o fundo está no texto */

async function loadMapa(){
  if(MP.loading)return;MP.loading=true;
  try{
    if(!MP.funds){
      const [c,g]=await Promise.all([fetch('/api/composition').then(r=>{if(!r.ok)throw new Error('composition');return r.json()}),
        fetch('/data/brasil-ufs.geojson').then(r=>{if(!r.ok)throw new Error('geojson');return r.json()})]);
      MP.funds=c;MP.geo=g;
    }
    if(!LF)throw new Error('leaflet');
    mpRenderMapa();
  }catch(e){$('#mpMsg').textContent='Não foi possível carregar o mapa agora. Os dados dos imóveis seguem na página Composição (Raio-X).'}
  finally{MP.loading=false}
}
const mpAssets=()=>(MP.funds||[]).flatMap(fd=>fd.assets.map(a=>({fund:fd.symbol,src:fd.source,a})));
const mpFiltered=()=>mpAssets().filter(x=>(MP.fund==='ALL'||x.fund===MP.fund)&&(MP.uf==='ALL'||x.a.uf===MP.uf)&&(MP.kind==='ALL'||(x.a.kind||'NONE')===MP.kind));

function mpRenderMapa(){
  $('#mpMsg').textContent='';
  mpRenderFilters();ensureMap();mpRenderStates();mpRenderPins();mpRenderList();mpRenderCompare();
}
function chips(group,items,cur){
  return `<div class="chips" role="group" aria-label="${esc(group)}">${items.map(([k,l])=>`<button type="button" class="fchip" data-mp="${esc(group)}" data-v="${esc(k)}" aria-pressed="${cur===k}">${esc(l)}</button>`).join('')}</div>`;
}
function mpRenderFilters(){
  const all=mpAssets();
  const ufs=[...new Set(all.map(x=>x.a.uf).filter(Boolean))].sort();
  const kinds=[...new Set(all.map(x=>x.a.kind||'NONE'))];
  const withAssets=MP.funds.filter(fd=>fd.assets.length);
  const cur=MP.fund==='ALL'?'':MP.fund;
  $('#mpFilters').innerHTML=`<h2>Filtros</h2><h3 class="sub">Fundo <span class="src">(${withAssets.length} com imóveis)</span></h3>
   <div class="mpfund"><input id="mpFundQ" type="search" list="mpFundList" placeholder="Buscar fundo (ex.: XPLG11)" aria-label="Buscar fundo pelo código" autocomplete="off" value="${esc(cur)}"><datalist id="mpFundList">${withAssets.map(fd=>`<option value="${esc(fd.symbol)}">${esc(fd.name||'')}</option>`).join('')}</datalist>
   ${MP.fund==='ALL'?'':'<button type="button" class="fchip" data-mp="fund" data-v="ALL">Todos os fundos</button>'}</div>
   <h3 class="sub">UF</h3>${chips('uf',[['ALL','Todas'],...ufs.map(u=>[u,u])],MP.uf)}
   <h3 class="sub">Tipo</h3>${chips('kind',[['ALL','Todos'],...kinds.map(k=>[k,k==='NONE'?mpKind(null):mpKind(k)])],MP.kind)}`;
}
function legend(){
  const k=mpColors();
  $('#mpLegend').innerHTML=`<h2>Legenda de precisão</h2><ul class="legendlist col">
    <li><i class="pin solid" style="--c:${k.gold}"></i>Endereço localizado</li>
    <li><i class="pin dashed" style="--c:${k.gold}"></i>Posição aproximada por via</li>
    <li><span class="tag">sem coordenada</span>aparece só na lista, com o motivo</li></ul>
    <div class="src">O contorno dos estados é mais claro onde há mais imóveis listados.</div>`;
}
function ensureMap(){
  if(MP.map){MP.map.invalidateSize();return}
  MP.map=LF.map('mpMap',{zoomControl:true,attributionControl:false,minZoom:3,maxZoom:14,zoomSnap:.25,keyboard:true,worldCopyJump:false});
  MP.map.fitBounds([[-34.5,-74.5],[5.5,-33.5]],{animate:false});
  MP.layers.pins=LF.layerGroup().addTo(MP.map);
  MP.map.on('zoomend',()=>mpRenderPins());
}
function stateCounts(){const m={};mpAssets().forEach(x=>{if(x.a.uf)m[x.a.uf]=(m[x.a.uf]||0)+1});return m}
function mpRenderStates(){
  const k=mpColors(),counts=stateCounts(),max=Math.max(1,...Object.values(counts));
  if(MP.layers.states)MP.map.removeLayer(MP.layers.states);
  MP.layers.states=LF.geoJSON(MP.geo,{
    style:ft=>{const n=counts[UF_BY_CODE[ft.properties.codarea]]||0;return{color:k.line,weight:1,fillColor:n?k.gold:k.panel,fillOpacity:n?.12+.5*n/max:.5}},
    onEachFeature:(ft,layer)=>{const uf=UF_BY_CODE[ft.properties.codarea],n=counts[uf]||0;layer.bindTooltip(`${uf}: ${n} imóve${n===1?'l':'is'} listado${n===1?'':'s'}`,{sticky:true})},
    interactive:true,bubblingMouseEvents:false}).addTo(MP.map);
  MP.layers.states.bringToBack();
  document.getElementById('mpMap').style.background=k.panel;
  legend();
}
function popupHtml(x){
  const a=x.a,c=a.coordinates,nd='<span class="gap">não informado</span>';
  const sectors=a.tenantSectors&&a.tenantSectors.length?a.tenantSectors.map(t=>esc(t.setor||t.sector)).join(', '):'não informado';
  return `<div class="mpop"><b>${esc(a.name)}</b><div class="src">${esc(x.fund)} · ${esc([a.city,a.uf].filter(Boolean).join('/'))} · ${esc(mpKind(a.kind))}</div>
   <dl><dt>ABL</dt><dd>${f(a.areaM2,0)} m²</dd><dt>Participação</dt><dd>${f(a.areaSharePercent,1)}%</dd><dt>Vacância</dt><dd>${a.vacancyPercent==null?nd:f(a.vacancyPercent,1)+'%'}</dd>
   <dt>Precisão</dt><dd>${c.approximate?'Posição aproximada por via':'Endereço localizado'}</dd></dl>
   <div class="src">Setores: ${sectors}</div>
   <div class="plinks"><a href="${esc(c.satelliteUrl)}" target="_blank" rel="noopener noreferrer">Satélite</a><a href="${esc(c.streetViewUrl)}" target="_blank" rel="noopener noreferrer">Street View</a></div>
   <div class="src">${esc(x.src.kindLabel)}: ${esc(x.src.name)} · dados de ${esc(x.src.referenceDate||'data não informada')}</div></div>`;
}
/* Agrupa por proximidade em pixels no zoom atual: cada imóvel entra no primeiro grupo cujo centro está a menos de MP_CLUSTER_PX (e o centro é
   recalculado). Uma grade fixa deixaria marcadores de células vizinhas se sobrepondo; sem agrupar, os imóveis de SP, RJ e MG se empilham. */
function clusterGroups(items,zoom){
  if(zoom>=MP_CLUSTER_MAX_ZOOM)return items.map(x=>[x]);
  const groups=[];
  items.forEach(x=>{
    const pt=MP.map.project([Number(x.a.coordinates.lat),Number(x.a.coordinates.lng)],zoom);
    const g=groups.find(c=>Math.hypot(c.x-pt.x,c.y-pt.y)<MP_CLUSTER_PX);
    if(g){g.items.push(x);g.x=(g.x*(g.items.length-1)+pt.x)/g.items.length;g.y=(g.y*(g.items.length-1)+pt.y)/g.items.length}
    else groups.push({x:pt.x,y:pt.y,items:[x]});
  });
  return groups.map(g=>g.items);
}
function mpRenderPins(){
  MP.layers.pins.clearLayers();MP.markers.clear();
  const located=mpFiltered().filter(x=>x.a.coordinates),k=mpColors();
  clusterGroups(located,MP.map.getZoom()).forEach(group=>{
    if(group.length>1){
      const lat=group.reduce((s,x)=>s+Number(x.a.coordinates.lat),0)/group.length,lng=group.reduce((s,x)=>s+Number(x.a.coordinates.lng),0)/group.length;
      const funds=[...new Set(group.map(x=>x.fund))].join(', ');
      const icon=LF.divIcon({className:'mpin cluster',html:`<i style="--c:${k.gold}"><b>${group.length}</b></i>`,iconSize:[30,30],iconAnchor:[15,15]});
      const m=LF.marker([lat,lng],{icon,keyboard:true,title:`${group.length} imóveis (${funds}): ampliar`});
      m.on('click',()=>{const bounds=LF.latLngBounds(group.map(x=>[Number(x.a.coordinates.lat),Number(x.a.coordinates.lng)]));
        if(bounds.getSouthWest().equals(bounds.getNorthEast()))MP.map.setView(bounds.getCenter(),MP_CLUSTER_MAX_ZOOM,{animate:true});else MP.map.fitBounds(bounds,{padding:[50,50],maxZoom:12})});
      m.on('keypress',e=>{if(e.originalEvent&&(e.originalEvent.key==='Enter'||e.originalEvent.key===' '))m.fire('click')});
      m.addTo(MP.layers.pins);return;
    }
    const x=group[0],c=x.a.coordinates,col=mpFundColor(x.fund);
    const icon=LF.divIcon({className:'mpin '+(c.approximate?'dashed':'solid'),html:`<i style="--c:${col}"></i>`,iconSize:[22,22],iconAnchor:[11,11],popupAnchor:[0,-12]});
    const m=LF.marker([Number(c.lat),Number(c.lng)],{icon,keyboard:true,title:`${x.a.name} (${x.fund})`,alt:`${x.a.name}, ${x.fund}, ${c.approximate?'posição aproximada':'endereço localizado'}`});
    m.bindPopup(popupHtml(x),{maxWidth:300,className:'mw-popup'});
    m.addTo(MP.layers.pins);MP.markers.set(x.a.id,m);
  });
  if(MP.pending&&MP.markers.has(MP.pending)){MP.markers.get(MP.pending).openPopup();MP.pending=null}
}
function mpRenderList(){
  const items=mpFiltered(),located=items.filter(x=>x.a.coordinates),approx=located.filter(x=>x.a.coordinates.approximate).length,none=items.length-located.length;
  $('#mpCount').textContent=`${located.length-approx} localizado${located.length-approx===1?'':'s'} · ${approx} aproximado${approx===1?'':'s'} · ${none} sem coordenada`;
  $('#mpList').innerHTML=`<h2>${items.length} imóve${items.length===1?'l':'is'} <a href="#/composicao" style="margin-left:auto;font-size:12px;letter-spacing:0;text-transform:none">Raio-X</a></h2><ul class="mplist">${items.slice(0,MP.limit).map(x=>{
    const a=x.a,c=a.coordinates;
    return `<li><${c?'button type="button" data-pin="'+esc(a.id)+'"':'div'} class="mpitem"><div class="mphead"><b>${esc(a.name)}</b><span class="mono src">${esc(x.fund)}</span></div>
     <div class="src">${esc([a.city,a.uf].filter(Boolean).join('/'))||'cidade não informada'} · ${esc(mpKind(a.kind))}</div>
     <div class="src">${c?(c.approximate?'Posição aproximada por via':'Endereço localizado'):'<span class="tag">sem coordenada</span>'} · vacância ${a.vacancyPercent==null?'não informada':f(a.vacancyPercent,1)+'%'}</div>
     ${c?'':`<div class="src">${esc(a.noCoordinatesReason||'motivo não informado')}</div>`}</${c?'button':'div'}></li>`}).join('')||'<li class="mute">Nenhum imóvel com esses filtros.</li>'}</ul>${items.length>MP.limit?`<button type="button" class="fchip" id="mpMore">Mostrar mais (${Math.min(60,items.length-MP.limit)} de ${items.length-MP.limit} restantes)</button>`:''}`;
  const shown=[...new Set(items.map(x=>x.fund))].map(sym=>MP.funds.find(fd=>fd.symbol===sym)).filter(Boolean);
  const one=shown.length===1?shown[0]:null;
  const bySrc={};shown.forEach(fd=>{(bySrc[fd.source.name]=bySrc[fd.source.name]||[]).push(fd)});
  $('#mpSrc').innerHTML=one?`<b>${esc(one.symbol)}</b>: ${esc(one.source.kindLabel)} · ${esc(one.source.name)} · dados de ${esc(one.source.referenceDate||'data não informada')} · consultado em ${esc(one.source.consultedOn)}${one.stale?' <span class="fresh dn">desatualizado</span>':''}`
    :Object.entries(bySrc).map(([name,fds])=>{const ds=fds.map(fd=>fd.source.referenceDate).filter(Boolean).sort();
      return `<b>${fds.length} fundo${fds.length===1?'':'s'}</b>: ${esc(fds[0].source.kindLabel)} · ${esc(name)} · dados de ${esc(ds.length?(ds[0]===ds[ds.length-1]?ds[0]:ds[0]+' a '+ds[ds.length-1]):'data não informada')}${fds.some(fd=>fd.stale)?' <span class="fresh dn">'+fds.filter(fd=>fd.stale).length+' desatualizado(s)</span>':''}`}).join('<br>');
}
function mpRenderCompare(){
  const MAXC=15,pool=MP.funds.filter(fd=>fd.assets.length&&(MP.fund==='ALL'||fd.symbol===MP.fund)&&(MP.uf==='ALL'||fd.assets.some(a=>a.uf===MP.uf))).sort((a,b)=>Number(b.listedAreaM2||0)-Number(a.listedAreaM2||0));
  const cmpFunds=pool.slice(0,MAXC);
  $('#mpCompare').innerHTML=`<h2>Comparação de fundos</h2><div class="scroll"><table><thead><tr><th>Fundo</th><th class="n">ABL listada${GL('abl')}</th><th class="n">Vacância média${GL('vacancy')}</th><th class="n">Imóveis</th><th class="n">Com coordenada</th></tr></thead><tbody>${cmpFunds.map(fd=>{
    if(!fd.assets.length)return `<tr><td class="mono"><b>${esc(fd.symbol)}</b></td><td colspan="4" class="mute">Fundo sem imóveis listados (fundo de papel): veja a alocação por classe no <a href="#/composicao">Raio-X</a>.</td></tr>`;
    const withVac=fd.assets.filter(a=>a.vacancyPercent!=null),area=withVac.reduce((s,a)=>s+Number(a.areaM2),0);
    const vac=area>0?withVac.reduce((s,a)=>s+Number(a.vacancyPercent)*Number(a.areaM2),0)/area:null;
    return `<tr><td class="mono"><b>${esc(fd.symbol)}</b></td><td class="n">${f(fd.listedAreaM2,0)} m²</td><td class="n">${vac!=null?f(vac,1)+'%':fd.metrics&&fd.metrics.physicalVacancyPercent!=null?f(fd.metrics.physicalVacancyPercent,1)+'% <span class="src">(do fundo)</span>':NI}${withVac.length<fd.assets.length?`<div class="src">${withVac.length} de ${fd.assets.length} imóveis informam</div>`:''}</td><td class="n">${fd.listedAssetCount}${fd.declaredAssetCount!=null&&fd.declaredAssetCount!==fd.listedAssetCount?` <span class="src">de ${fd.declaredAssetCount}</span>`:''}</td><td class="n">${fd.geolocatedCount} de ${fd.listedAssetCount}</td></tr>`}).join('')}</tbody></table></div>${pool.length>MAXC?`<div class="src">Mostrando os ${MAXC} maiores por ABL listada de ${pool.length} fundos; escolha um fundo ou uma UF nos filtros para ver os demais.</div>`:''}
    <div class="src">Vacância média ponderada pela ABL, só dos imóveis que informam vacância; sem isso, a vacância física publicada para o fundo inteiro, quando existe. A cobertura de coordenadas depende do que a fonte publica (cidade, CEP e rua).</div>`;
}
document.addEventListener('click',e=>{
  const c=e.target.closest('button[data-mp]');
  if(c){MP[c.dataset.mp]=c.dataset.v;MP.limit=60;mpRenderFilters();mpRenderPins();mpRenderList();mpRenderCompare();return}
  if(e.target.closest('#mpMore')){MP.limit+=60;mpRenderList();return}
  const p=e.target.closest('button[data-pin]');
  if(p&&MP.map){const x=mpAssets().find(y=>y.a.id===p.dataset.pin);if(x&&x.a.coordinates){MP.pending=x.a.id;MP.map.flyTo([Number(x.a.coordinates.lat),Number(x.a.coordinates.lng)],MP_CLUSTER_MAX_ZOOM,{duration:.6});setTimeout(()=>{if(MP.pending)mpRenderPins()},900)}}
});
function onMapaRoute(){if((location.hash.match(/^#\/([a-z]+)/)||[])[1]==='mapa')loadMapa().then(()=>MP.map&&setTimeout(()=>MP.map.invalidateSize(),50))}
document.addEventListener('change',e=>{
  if(e.target.id!=='mpFundQ')return;
  const v=e.target.value.trim().toUpperCase();
  MP.fund=(MP.funds||[]).some(fd=>fd.symbol===v&&fd.assets.length)?v:'ALL';MP.limit=60;
  mpRenderFilters();mpRenderPins();mpRenderList();mpRenderCompare();
});
addEventListener('hashchange',onMapaRoute);
new MutationObserver(()=>{if(MP.map&&MP.funds){mpRenderStates();mpRenderPins()}}).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
addEventListener('resize',()=>{if(MP.map)MP.map.invalidateSize()});
onMapaRoute();
