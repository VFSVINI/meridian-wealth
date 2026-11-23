/* Seletor de ativo com busca (item #30): combobox acessível. Digite símbolo, nome, classe ou bolsa; setas, Enter e Esc funcionam.
   Substitui o select longo: com centenas de ativos, rolar a lista não é pesquisa. */
const assetPickerNorm = s => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/** Ordena por relevância: símbolo começando com o termo, símbolo contendo, nome contendo, outros campos. */
function assetPickerFilter(items, term, limit) {
  const t = assetPickerNorm(term).trim();
  if (!t) return items.slice(0, limit);
  const scored = [];
  for (const u of items) {
    const sym = assetPickerNorm(u.symbol), name = assetPickerNorm(u.name);
    const rest = assetPickerNorm([u.assetClass, u.exchange, u.region, u.sector].join(' '));
    let s = -1;
    if (sym === t) s = 0;
    else if (sym.startsWith(t)) s = 1;
    else if (sym.includes(t)) s = 2;
    else if (name.startsWith(t)) s = 3;
    else if (name.includes(t)) s = 4;
    else if (rest.includes(t)) s = 5;
    if (s >= 0) scored.push([s, u]);
  }
  scored.sort((a, b) => a[0] - b[0]);
  return scored.slice(0, limit).map(x => x[1]);
}

function mountAssetPicker(host, items, onPick) {
  const id = host.id || 'ap' + Math.random().toString(36).slice(2, 7);
  host.classList.add('combo');
  host.innerHTML = '<input type="text" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="' + id + '-lb" aria-label="Ativo: digite o símbolo ou o nome" autocomplete="off" spellcheck="false" placeholder="Digite o símbolo ou o nome do ativo">'
    + '<ul id="' + id + '-lb" role="listbox" hidden></ul>';
  const input = host.querySelector('input'), list = host.querySelector('ul');
  let current = null, shown = [], active = -1;
  const label = u => u.symbol + ' · ' + u.name;
  const meta = u => [u.assetClass, u.exchange].filter(Boolean).join(' · ');
  const esc = s => String(s).replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

  function close() {
    list.hidden = true; input.setAttribute('aria-expanded', 'false'); input.removeAttribute('aria-activedescendant'); active = -1;
    input.value = current ? label(current) : '';
  }
  function paint() {
    list.innerHTML = shown.length
      ? shown.map((u, i) => '<li role="option" id="' + id + '-o' + i + '" data-i="' + i + '"' + (i === active ? ' aria-selected="true"' : '') + '><b>' + esc(u.symbol) + '</b> <span>' + esc(u.name) + '</span><small>' + esc(meta(u)) + '</small></li>').join('')
      : '<li class="none" role="presentation">Nenhum ativo encontrado para essa busca</li>';
    if (active >= 0) input.setAttribute('aria-activedescendant', id + '-o' + active); else input.removeAttribute('aria-activedescendant');
    const el = list.querySelector('[aria-selected="true"]');
    if (el && el.scrollIntoView) el.scrollIntoView({block: 'nearest'});
  }
  function open(term) {
    shown = assetPickerFilter(items, term, 60);
    active = shown.length ? 0 : -1;
    list.hidden = false; input.setAttribute('aria-expanded', 'true'); paint();
  }
  function pick(u) {
    if (!u) return;
    current = u; input.value = label(u); list.hidden = true; input.setAttribute('aria-expanded', 'false');
    if (onPick) onPick(u.symbol);
  }
  input.addEventListener('focus', () => { input.select(); open(''); });
  input.addEventListener('input', () => open(input.value));
  input.addEventListener('keydown', e => {
    if (e.key === 'ArrowDown') { e.preventDefault(); if (list.hidden) open(''); else { active = Math.min(shown.length - 1, active + 1); paint(); } }
    else if (e.key === 'ArrowUp') { e.preventDefault(); active = Math.max(0, active - 1); paint(); }
    else if (e.key === 'Enter') { if (!list.hidden && shown[active]) { e.preventDefault(); pick(shown[active]); } }
    else if (e.key === 'Escape') { if (!list.hidden) { e.preventDefault(); close(); } }
  });
  list.addEventListener('mousedown', e => { const li = e.target.closest('li[data-i]'); if (li) { e.preventDefault(); pick(shown[Number(li.dataset.i)]); } });
  input.addEventListener('blur', () => setTimeout(close, 120));
  host.setAsset = sym => { current = items.find(u => u.symbol === sym) || null; input.value = current ? label(current) : ''; };
  return host;
}
