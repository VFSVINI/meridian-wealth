/* Shopping financeiro (item #17): os tipos de ativo da taxonomia lado a lado, com as referências oficiais do Banco Central.
   Não há ofertas de emissores no projeto, então nenhuma taxa de produto é mostrada (nada é inventado): o catálogo diz o que cada tipo é e a referência
   oficial que o indexador dele acompanha. Depende de app.js (helpers $, esc, f, go) e carteira.js (openOp). */
const SH={types:null,rates:null,cat:'ALL',q:'',loading:false};
const SH_REFS=['selicTarget','cdiAnnual','ipca12m','ipcaMonthly','ptaxSell'];
function shRate(key){return SH.rates&&SH.rates.items.find(i=>i.key===key)}
function shFmt(i){if(!i||i.value==null)return null;const d=Math.min(4,(String(i.value).split('.')[1]||'').length);return Number(i.value).toLocaleString('pt-BR',{minimumFractionDigits:d,maximumFractionDigits:d})+' '+esc(i.unit)}
/* a referência oficial que o indexador do tipo acompanha, sem inventar a taxa do produto */
function shReference(t){
  const refs={SELIC:'selicTarget',IPCA_MAIS:'ipca12m',CONFORME_EMISSAO:'cdiAnnual'};
  const i=shRate(refs[t.indexer]);
  const nd='<span class="gap">não informado</span>';
  switch(t.indexer){
    case 'SELIC':return `Meta Selic ${shFmt(i)||nd}${i&&i.dateLabel?` <span class="src">(${esc(i.dateLabel)} ${esc(i.date||'')})</span>`:''}`;
    case 'IPCA_MAIS':return `IPCA 12 meses ${shFmt(i)||nd} <span class="src">+ a taxa do título (a plataforma não a tem)</span>`;
    case 'PRE_FIXADO':return '<span class="src">A taxa pré é a do título no dia (a plataforma não a tem)</span>';
    case 'CONFORME_EMISSAO':return `CDI anualizado ${shFmt(i)||nd} <span class="src">como referência; o indexador real é definido na emissão</span>`;
    default:return '<span class="mute">—</span>';
  }
}
async function loadShopping(){
  if(SH.loading)return;SH.loading=true;
  try{
    if(!SH.types){
      const [t,r]=await Promise.all([fetch('/api/taxonomy').then(x=>{if(!x.ok)throw new Error('taxonomy');return x.json()}),fetch('/api/brazil/rates').then(x=>x.ok?x.json():null).catch(()=>null)]);
      SH.types=t.filter(x=>x.investable);SH.rates=r;
    }
    renderShopping();
  }catch(e){$('#shTable').innerHTML='<p class="mute">Não foi possível carregar o catálogo agora.</p>'}
  finally{SH.loading=false}
}
function renderShopping(){
  const refs=SH_REFS.map(k=>shRate(k)).filter(Boolean);
  $('#shRefs').innerHTML=`<h2>Referências oficiais agora <span class="tag info">Banco Central</span></h2>${refs.length?`<div class="kv">${refs.map(i=>`<div><span>${esc(i.label)}${GLL(i.label)}<span class="src"> · ${esc(i.dateLabel||'referência')} ${esc(i.date||'')}</span></span><span class="num">${i.value==null?'<span class="gap">sem dado</span>':shFmt(i)}${i.status==='STALE'?' <span class="fresh dn">desatualizado</span>':''}</span></div>`).join('')}</div>`
    :'<p class="mute">Sem resposta do Banco Central agora: as referências ficam como "não informado".</p>'}<div class="src">Fonte: Banco Central do Brasil (SGS e PTAX), com a data de referência de cada publicação.</div>`;
  const cats=[...new Map(SH.types.map(t=>[t.category,t.categoryLabel])).entries()];
  $('#shCats').innerHTML=[['ALL','Todas'],...cats].map(([k,l])=>`<button type="button" class="fchip" data-shc="${esc(k)}" aria-pressed="${SH.cat===k}">${esc(l)}</button>`).join('');
  const q=SH.q.trim().toLowerCase();
  const list=SH.types.filter(t=>(SH.cat==='ALL'||t.category===SH.cat)&&(!q||(t.label+' '+t.code).toLowerCase().includes(q)));
  $('#shTable').innerHTML=`<table class="shtbl"><thead><tr><th>Tipo</th><th>Classe</th><th>Indexador</th><th>Rendimento</th><th>Proteção</th><th>Preço</th><th>Referência oficial</th><th></th></tr></thead><tbody>${list.map(t=>{
    const market=t.priceSource==='MERCADO';
    return `<tr><td><b>${esc(t.label)}</b><div class="src mono">${esc(t.code)}${t.hasMaturity?' · tem vencimento':''}</div></td><td>${esc(t.categoryLabel)}</td><td>${esc(t.indexerLabel)}</td><td>${esc(t.incomeLabel)}</td><td>${esc(t.protectionLabel)}</td><td>${esc(t.priceSourceLabel)}</td><td>${shReference(t)}</td>
      <td class="acts">${market?'<a href="#/mercados">Ver no painel</a>':`<button type="button" data-shsim="${esc(t.key)}">Simular</button><button type="button" data-shadd="${esc(t.key)}">Carteira</button>`}</td></tr>`}).join('')||'<tr><td colspan="8" class="mute">Nenhum tipo com esses filtros.</td></tr>'}</tbody></table>`;
  $('#shNote').textContent='Rendimento, proteção e fonte do preço são atributos definidos pela plataforma ou verificados em fonte pública (docs/taxonomia.md). Imposto e liquidez ficam de fora porque não foram verificados. "Simular" leva ao simulador pré-ordem; nenhuma ordem é executada.';
}
document.addEventListener('click',e=>{
  const c=e.target.closest('button[data-shc]');if(c){SH.cat=c.dataset.shc;renderShopping();return}
  const s=e.target.closest('button[data-shsim]');
  if(s){go('simulador');const pick=()=>{const sel=$('#smAsset');if(!sel)return false;const o=[...sel.options].find(x=>x.value==='T:'+s.dataset.shsim);if(o)sel.value=o.value;return true};if(!pick())setTimeout(pick,800);return}
  const a=e.target.closest('button[data-shadd]');
  if(a){if(!isLogged()){openLogin();return}openOp('open');const sel=$('#opAsset');if(sel){const o=[...sel.options].find(x=>x.value==='T:'+a.dataset.shadd);if(o){sel.value=o.value;sel.dispatchEvent(new Event('change'))}}}
});
document.addEventListener('input',e=>{if(e.target.id==='shQ'){SH.q=e.target.value;renderShopping();$('#shQ').focus()}});
function onShoppingRoute(){if((location.hash.match(/^#\/([a-z]+)/)||[])[1]==='shopping')loadShopping()}
addEventListener('hashchange',onShoppingRoute);onShoppingRoute();
