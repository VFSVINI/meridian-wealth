/* Ativo no visual Cosmos (item #48): título em pergunta, chips para trocar de ativo, "A leitura em uma frase", e "Conheça o ativo antes de decidir" em abas.
   Tudo sai do que a API já devolve: o sinal e os fatores do motor (regra publicada), os níveis, as métricas e a composição dos FIIs (CVM). Os textos de classe
   (o que é uma ação, um FII...) são educativos e gerais: não dizem nada sobre uma empresa ou fundo específico que a plataforma não saiba (nada é inventado).
   Depende de app.js (sel, A, rows, universe, select, openAsset, SIG, SIG_TXT, RISK, EXS, f, pc, esc, $, GL, quoteWhen). Sem global novo. */
(function () {
  const TABS = [['resumo', 'Resumo'], ['feito', 'Do que é feito'], ['riscos', 'Riscos'], ['carteira', 'Na carteira'], ['metricas', 'Janela e métricas']];
  const INFO = {
    'Ação': {what: 'Uma ação é uma pequena parte de uma empresa listada em bolsa. Ao comprar, você vira sócio dela, na proporção do que comprou.',
      gives: 'Dividendos (parte do lucro distribuída aos sócios) e valorização, se a empresa crescer e o mercado passar a valorizá-la mais.',
      risk: 'O preço oscila todos os dias com o resultado da empresa, o setor e a economia, e você pode perder parte do que investiu. Dividendo não é garantido.'},
    'FII': {what: 'Um fundo imobiliário reúne o dinheiro de muitos investidores para comprar imóveis ou títulos ligados a imóveis. Você compra cotas, negociadas na bolsa.',
      gives: 'Rendimentos distribuídos periodicamente (em geral todo mês) e a variação do valor da cota.',
      risk: 'Vacância e inadimplência dos imóveis, juros altos e queda do preço da cota; o rendimento muda de mês a mês e não é garantido.'},
    'ETF': {what: 'Um ETF é um fundo que copia um índice (por exemplo, o Ibovespa ou o S&P 500) e é negociado na bolsa como uma ação.',
      gives: 'Diversificação com uma só compra: você acompanha a cesta inteira do índice.',
      risk: 'Sobe e cai junto com o índice, cobra taxa de administração e alguns têm pouca liquidez.'},
    'Cripto': {what: 'Criptoativos são ativos digitais negociados 24 horas por dia, todos os dias, sem um emissor central.',
      gives: 'Participar de um mercado que não fecha e que, às vezes, se move diferente da bolsa.',
      risk: 'Variações de 5% ou mais num só dia são comuns; custódia, golpes e falta de regulação são riscos adicionais, e não há garantia.'},
    'Câmbio': {what: 'O câmbio é o preço de uma moeda em outra, por exemplo quantos reais custa um dólar.',
      gives: 'Não é um investimento em si: mostra o preço do dólar, do euro e de outras moedas e o efeito delas na economia.',
      risk: 'Oscila com juros, inflação, economia e geopolítica; afeta viagens, importados, dívidas em moeda estrangeira e empresas exportadoras.'},
    'Commodity': {what: 'Commodities são matérias-primas negociadas no mundo todo, como petróleo e ouro. Aqui aparecem os preços dos contratos futuros.',
      gives: 'Mostra o preço de insumos que movem empresas e a inflação; o ouro costuma ser procurado como proteção em crises.',
      risk: 'Os preços variam com oferta, demanda, clima e conflitos; futuros têm vencimento e não são o mesmo que o produto físico.'},
    'Índice': {what: 'Um índice mede o desempenho de uma cesta de ativos (por exemplo, o Ibovespa) e serve de termômetro do mercado. Não se compra o índice direto.',
      gives: 'Uma leitura rápida de como o mercado inteiro está indo e uma régua para comparar ativos.',
      risk: 'Para investir no índice é preciso um ETF ou fundo que o copie, com as taxas e os riscos deles.'}
  };
  const AK = {tab: 'resumo', comp: {}};
  const norm = s => String(s || '');
  const inst = () => universe.find(x => x.symbol === sel);
  const clsOf = u => (u && INFO[u.assetClass]) ? u.assetClass : null;

  const confidence = n => n < 40 ? 'Baixa' : n < 70 ? 'Média' : 'Alta';
  const kindLabel = u => ({'Ação': 'Ação', 'FII': 'Fundo imobiliário', 'ETF': 'ETF', 'Cripto': 'Cripto', 'Câmbio': 'Câmbio', 'Commodity': 'Commodity', 'Índice': 'Índice'}[u.assetClass] || u.assetClass);

  function head() {
    const u = inst();
    if (!u) return;
    $('#ckEyebrow').textContent = ['Ativo', kindLabel(u), EXS[u.exchange] || u.exchange].filter(Boolean).join(' · ');
    $('#ckTitle').textContent = u.symbol + ': o que o gráfico diz?';
    $('#ckLede').textContent = u.name + '. Uma leitura simples primeiro; o detalhe fica logo abaixo, se você quiser.';
  }

  function chips() {
    const pref = ['PETR4', 'VALE3', 'ITUB4', 'MXRF11', 'HGLG11', 'IBOV', 'AAPL', 'BTC', 'ETH', 'USDBRL'];
    const have = s => universe.some(u => u.symbol === s) && rows[s] && rows[s].candle;
    const list = pref.filter(have);
    for (const u of universe) { if (list.length >= 10) break; if (!list.includes(u.symbol) && have(u.symbol)) list.push(u.symbol); }
    if (sel && !list.includes(sel) && have(sel)) list.unshift(sel);
    $('#ckChips').innerHTML = list.map(s => `<button type="button" class="mk-pill" data-cko="${esc(s)}" aria-pressed="${s === sel}">${esc(s)}</button>`).join('');
  }

  function read() {
    const u = inst(), r = u && rows[u.symbol], box = $('#ckRead');
    if (!u || !r) return;
    if (!hasData(r) || !A) {
      box.innerHTML = `<p class="ck-read-k"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/></svg>A leitura em uma frase</p><p class="mute">${hasData(r) ? 'Dados reais insuficientes para o motor de sinais neste ativo.' : 'Sem dado em tempo real para este ativo: nenhum provedor respondeu e não há dado real gravado. Nada é estimado.'}</p>`;
      return;
    }
    const s = SIG[A.signal], pro = (A.factors || []).find(x => x.side === 'COMPRA'), con = (A.factors || []).find(x => x.side === 'VENDA' || x.side === 'ALERTA');
    const up = A.score >= 20 && A.signal !== 'HIGH_RISK_WAIT';
    const change = `${up ? `Fechar acima de ${f(A.target1)} confirma a alta. ` : ''}Fechar abaixo de ${f(A.invalidation)} invalida a leitura.`;
    const price = `${r.source} · ${quoteWhen(r)}`;
    box.innerHTML = `<p class="ck-read-k"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/></svg>A leitura em uma frase<span class="cs-chip am">análise, não ordem</span></p>`
      + `<div class="ck-read-main"><span class="mk-badge ${s[1]}">${esc(s[0])}</span><p>${esc(SIG_TXT[A.signal] || '')}</p></div>`
      + `<div class="ck-read-grid"><div><span>Confiança ${GL('confidence')}</span><b>${confidence(A.confidence)}</b><small class="num">${A.confidence}%</small></div>`
      + `<div><span>Por quê ${GL('signal')}</span><p>${pro ? 'A favor: ' + esc(pro.text) + '.' : 'Nenhum fator forte a favor agora.'}${con ? ' Contra: ' + esc(con.text) + '.' : ''}</p></div>`
      + `<div><span>O que mudaria essa leitura</span><p>${esc(change)}</p></div></div>`
      + `<p class="src">Fonte: ${esc(price)} · cálculo próprio com regra publicada. Não é ordem nem recomendação.</p>`;
  }

  /* ---------- conheça o ativo ---------- */
  async function composition(sym) {
    if (sym in AK.comp) return AK.comp[sym];
    AK.comp[sym] = null;
    try {
      const r = await fetch('/api/composition/' + encodeURIComponent(sym));
      AK.comp[sym] = r.ok ? await r.json() : null;
    } catch (e) { AK.comp[sym] = null; }
    return AK.comp[sym];
  }
  const cell = (k, v) => `<div class="ck-cell"><span>${esc(k)}</span><p>${v}</p></div>`;

  function tabBody(u) {
    const info = INFO[u.assetClass] || {what: u.assetClass, gives: 'não informado', risk: 'não informado'};
    switch (AK.tab) {
      case 'resumo':
        return cell('O que é', esc(info.what)) + cell('O que oferece', esc(info.gives))
          + cell('De onde é', esc([u.region, EXS[u.exchange] || u.exchange, 'moeda ' + (u.currency || 'não informada'), u.sector && u.sector !== 'amplo' ? 'setor ' + u.sector : null].filter(Boolean).join(' · ')));
      case 'feito': {
        const c = AK.comp[u.symbol];
        if (u.assetClass === 'FII') {
          if (c && c.assets) {
            const located = c.assets.filter(a => a.coordinates).length;
            return cell('Imóveis listados', `<b class="num">${c.assets.length}</b> imóve${c.assets.length === 1 ? 'l' : 'is'}${c.listedAreaM2 ? ' · ' + f(c.listedAreaM2, 0) + ' m²' : ''}`)
              + cell('No mapa', `${located} com coordenada, ${c.assets.length - located} sem (a fonte não publica o endereço completo). <a href="#/mapa">Abrir o mapa</a>`)
              + cell('Fonte', `${esc(c.source.kindLabel)}: ${esc(c.source.name)} · dados de ${esc(c.source.referenceDate || 'data não informada')}. <a href="#/composicao">Raio-X</a>`);
          }
          if (c === null && u.symbol in AK.comp) return cell('Composição', 'A plataforma não tem a lista de imóveis deste fundo: ele não publica imóveis no Informe da CVM (fundos de papel ou de fundos) ou não foi encontrado. <b>não informado</b>.');
          return cell('Composição', 'Buscando a lista de imóveis…');
        }
        return cell('Composição', u.assetClass === 'ETF' || u.assetClass === 'Índice' ? 'A lista dos ativos que compõem este ' + (u.assetClass === 'ETF' ? 'ETF' : 'índice') + ' não está nas fontes que a plataforma usa: <b>não informado</b>.'
          : 'Este tipo de ativo não tem composição de carteira. ' + (u.benchmark ? 'Ele é comparado com <b>' + esc(u.benchmark) + '</b> na força relativa.' : 'Sem benchmark definido.'));
      }
      case 'riscos':
        return cell('Riscos deste tipo', esc(info.risk)) + (A ? cell('Risco calculado ' + '', `<b>${esc(RISK[A.risk] || A.risk)}</b> · volatilidade anual ${pc(A.annVol)} · maior queda no histórico ${pc(A.maxDrawdown)} · VaR 95% de uma barra ${pc(A.var95)}`) : cell('Risco calculado', 'Dados reais insuficientes: <b>não informado</b>.'));
      case 'carteira':
        return cell('Antes de registrar', 'Veja o efeito de uma compra ou venda no peso da carteira, no custo e em cenários de queda, sem enviar nada à corretora. <button type="button" class="mk-btn" data-ckgo="sim">Simular compra ou venda</button>')
          + cell('Sua carteira', 'Registre operações em <a href="#/carteira">Minha Carteira</a> para ver este ativo no seu patrimônio.')
          + (A ? cell('Exposição de referência', `O motor sugere até <b class="num">${pc(A.suggestedExposure)}</b> do patrimônio neste ativo, pelo risco dele. Leitura analítica, não recomendação.`) : '');
      case 'metricas':
        return A ? `<div class="ck-metrics">${[['Z-Score (50)', f(A.zScore), 'zscore'], ['Percentil', f(A.percentile, 0) + '%', 'percentile'], ['RSI (14)', f(A.rsi, 0), 'rsi'], ['Sharpe', f(A.sharpe), 'sharpe'], ['Risco/retorno', f(A.riskReward), 'risk-reward'],
          ['Força relativa', (A.relStrength >= 0 ? '+' : '') + f(A.relStrength * 100) + ' pp', 'relative-strength']].map(([k, v, g]) => `<div><span>${esc(k)} ${GL(g)}</span><b class="num">${esc(v)}</b></div>`).join('')}</div>` : cell('Métricas', 'Dados reais insuficientes: <b>não informado</b>.');
      default: return '';
    }
  }

  function know() {
    const u = inst();
    if (!u) return;
    if (AK.tab === 'feito' && u.assetClass === 'FII' && !(u.symbol in AK.comp)) composition(u.symbol).then(() => { if (sel === u.symbol) know(); });
    $('#ckKnow').innerHTML = `<p class="ck-know-k"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3zM5 17a3 3 0 0 1 3-3h11"/></svg>Conheça o ativo antes de decidir<span class="cs-chip am">educativo, não recomendação</span></p>`
      + `<div class="mk-pills" role="tablist" aria-label="Sobre o ativo">${TABS.map(([k, l]) => `<button type="button" class="mk-pill" role="tab" data-ckk="${k}" aria-pressed="${AK.tab === k}" aria-selected="${AK.tab === k}">${l}</button>`).join('')}</div>`
      + `<div class="ck-cells" role="tabpanel">${tabBody(u)}</div>`;
  }

  let lastSym = null;
  function render() {
    if (!sel) return;
    head(); chips(); read();
    if (sel !== lastSym) { lastSym = sel; AK.tab = 'resumo'; }
    know();
  }
  document.addEventListener('mw:ativo-render', render);
  document.addEventListener('click', e => {
    const c = e.target.closest('[data-cko]'); if (c) { const s = c.dataset.cko; openAsset(s).then(ok => { if (ok) select(s); }); return; }
    const k = e.target.closest('[data-ckk]'); if (k) { AK.tab = k.dataset.ckk; know(); return; }
    const g = e.target.closest('[data-ckgo]'); if (g) { const b = $('#ckSim'); if (b) b.click(); }
  });
})();
