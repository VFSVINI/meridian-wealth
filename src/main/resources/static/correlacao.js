/* Correlação e concentração (item #19): correlação de Pearson entre os ativos e entre as classes da carteira salva, risco com a correlação e
   mapa de concentração por faixa de risco. Só retornos reais; célula sem dado fica "não informado", nunca zero. Cada célula mostra o número
   (a cor nunca é o único sinal). Depende de app.js, auth.js e carteira.js. */
const CR={data:null,loading:false};
const CR_RISK={LOW:'Baixo',MEDIUM:'Moderado',HIGH:'Alto',EXTREME:'Extremo',UNRATED:'Sem classificação'};
function crGate(){
  $('#crBody').hidden=true;$('#crGate').hidden=false;
  $('#crGate').innerHTML=`<h2>Entre para analisar sua carteira</h2><p>A correlação e a concentração usam as posições da sua carteira salva e o histórico real de cada ativo.</p><button type="button" class="primary" id="crLogin">Entrar ou criar conta</button>`;
  $('#crLogin').onclick=openLogin;
}
async function crLoad(){
  if(!isLogged()){crGate();return}
  if(CR.loading)return;CR.loading=true;
  $('#crGate').hidden=true;$('#crBody').hidden=false;$('#crMsg').textContent='';
  try{
    const r=await api('/api/me/risk/correlation');
    const d=await r.json().catch(()=>({}));
    if(r.status===401){crGate();return}
    if(!r.ok)throw new Error(d.error||'Não foi possível analisar agora.');
    CR.data=d;crRender();
  }catch(e){$('#crMsg').textContent=e.message;$('#crBody').hidden=true}
  finally{CR.loading=false}
}
/* cor da célula de correlação: verde para positiva, vermelha para negativa, intensidade pelo módulo; sem dado = cinza */
function crCell(v,days){
  if(v==null)return `<td class="crcell nd" title="não informado: ${days} pregões em comum (mínimo ${CR.data.minDays})" aria-label="não informado: ${days} pregões em comum, abaixo do mínimo de ${CR.data.minDays}"><span class="gap">n/d</span></td>`;
  const n=Number(v),a=Math.round(Math.abs(n)*70),col=n>=0?'var(--up)':'var(--down)',tip=`${f(n,2)}, calculada sobre ${days} pregões em comum${days<120?' (poucos: estimativa frágil)':''}`;
  return `<td class="crcell num${days<120?' few':''}" title="${tip}" aria-label="${tip}" style="background:color-mix(in srgb,${col} ${a}%,var(--panel-2))">${f(n,2)}</td>`;
}
function crMatrix(m,title,note){
  if(!m)return `<p class="mute">${esc(note)}</p>`;
  const head=m.labels.map(l=>`<th class="crh"><span title="${esc(l)}">${esc(l.length>16?l.slice(0,15)+'…':l)}</span></th>`).join('');
  const rows=m.labels.map((l,i)=>`<tr><th scope="row" class="crr" title="${esc(l)}">${esc(l.length>22?l.slice(0,21)+'…':l)}</th>${m.values[i].map((v,j)=>crCell(v,m.days[i][j])).join('')}</tr>`).join('');
  return `<div class="scroll"><table class="crtbl" aria-label="${esc(title)}"><thead><tr><th></th>${head}</tr></thead><tbody>${rows}</tbody></table></div>
    <div class="crlegend src"><span>−1</span><i class="neg"></i><i></i><i class="pos"></i><span>+1</span><span class="mute">Valores numéricos em todas as células; cinza = não informado. O número de pregões de cada par está no texto da célula (passe o cursor ou use o leitor de tela); com menos de 120 a estimativa é frágil e a célula ganha um traço pontilhado.</span></div>`;
}
function crRender(){
  const d=CR.data,r=d.risk;
  $('#crRisk').innerHTML=r?`<h2>Risco da carteira com a correlação <span class="tag info">dado real</span></h2>
    <div class="kv one"><div><span>Volatilidade da carteira${GL('volatility')}</span><span class="num">${f(r.portfolioVolatilityPercent,1)}% a.a.</span></div>
    <div><span>Média ponderada das volatilidades (ignora a correlação)</span><span class="num">${f(r.weightedAverageVolatilityPercent,1)}% a.a.</span></div>
    <div><span>Benefício da diversificação</span><span class="num up">${f(r.diversificationBenefitPercent,1)} p.p.</span></div>
    <div><span>Cobertura do patrimônio</span><span class="num">${f(r.coveredPercent,1)}%</span></div></div>
    <div class="src">${r.days} pregões em comum, ${r.assets.length} ativos: ${r.assets.map(esc).join(', ')}. A média ponderada é o limite superior que o simulador pré-ordem usa; a volatilidade da carteira é a real, medida sobre os retornos alinhados.</div>`
    :'<h2>Risco da carteira com a correlação</h2><p class="mute">Sem histórico real em comum suficiente entre os ativos para medir o risco da carteira.</p>';
  $('#crClasses').innerHTML=`<h2>Correlação entre classes <span class="tag info">${d.window} pregões</span></h2>${crMatrix(d.classes,'Correlação entre classes','Pelo menos duas classes com histórico real são necessárias para a matriz de classes.')}`;
  $('#crAssets').innerHTML=`<h2>Correlação entre ativos <span class="tag info">${d.window} pregões</span></h2>${crMatrix(d.assets,'Correlação entre ativos','Nenhum ativo da carteira tem histórico real suficiente.')}`;
  const c=d.concentration,max=Math.max(1,...c.cells.flat().map(Number));
  $('#crConc').innerHTML=`<h2>Mapa de concentração · peso da classe por faixa de risco</h2><div class="scroll"><table class="crtbl" aria-label="Peso de cada classe em cada faixa de risco, em percentual do patrimônio"><thead><tr><th></th>${c.riskLevels.map(k=>`<th class="crh">${esc(CR_RISK[k]||k)}</th>`).join('')}</tr></thead><tbody>${
    c.categoryLabels.map((l,i)=>`<tr><th scope="row" class="crr">${esc(l)}</th>${c.cells[i].map(v=>{const n=Number(v);return n>0?`<td class="crcell num" style="background:color-mix(in srgb,var(--gold) ${Math.round(10+n/max*55)}%,var(--panel-2))">${f(n,1)}%</td>`:'<td class="crcell zero"><span class="mute">—</span></td>'}).join('')}</tr>`).join('')}</tbody></table></div>
    <div class="src">Cada célula é o peso da classe na faixa de risco que o motor deu ao ativo, sobre o patrimônio total. "Sem classificação" reúne o que o motor não avalia (renda fixa, fundos, ativos sem dado).</div>
    <div class="kv" style="margin-top:12px"><div><span>HHI por posição${GL('hhi')}</span><span class="num">${f(c.assetHhi,3)}</span></div><div><span>HHI por classe</span><span class="num">${f(c.classHhi,3)}</span></div>
    <div><span>Posições efetivas${GL('effective-positions')}</span><span class="num">${f(c.effectivePositions,1)}</span></div><div><span>Maior posição${GL('concentration')}</span><span class="num">${f(c.largestPositionPercent,1)}%</span></div></div>
    <h3 class="sub">Maiores posições</h3><ul class="note">${c.topPositions.map(p=>`<li>${esc(p.label)}: <b class="num">${f(p.weightPercent,1)}%</b></li>`).join('')}</ul>`;
  $('#crExcl').innerHTML=d.excluded.length?`<h2>Fora da correlação (entram na concentração pelo valor)</h2><ul class="note">${d.excluded.map(e=>`<li><b>${esc(e.label)}</b>: ${esc(e.reason)}</li>`).join('')}</ul>`:'';
  $('#crExcl').hidden=!d.excluded.length;
  $('#crNotes').innerHTML=d.notes.map(n=>`<li>${esc(n)}</li>`).join('');
  $('#crDisc').textContent=d.disclaimer;
  crEmit();
}
/* as barras 3D (correlacao3d.js) leem a MESMA matriz que a tabela, por este evento */
function crEmit(){if(CR.data)document.dispatchEvent(new CustomEvent('mw:cr-data',{detail:{data:CR.data}}))}
document.addEventListener('mw:cr-need',crEmit);
function onCorrelacaoRoute(){if((location.hash.match(/^#\/([a-z]+)/)||[])[1]==='correlacao')crLoad()}
addEventListener('hashchange',onCorrelacaoRoute);
document.addEventListener('mw-auth',()=>{if((location.hash||'').startsWith('#/correlacao'))crLoad()});
onCorrelacaoRoute();
