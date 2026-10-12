'use strict';
(function () {
const A = window.Android || {
  getMeta: () => '{}', getSettings: () => '{}', setSettings() {}, getPos: () => '', savePos() {},
  getRecent: () => '[]', setBookInfo() {}, openFile() {}, openBundled() {}, openRecent() {}, openExternal() {}, toast() {}, exit() {}
};
const $ = s => document.querySelector(s);
const XL = 'http://www.w3.org/1999/xlink';
const content = $('#content');
const meta = JSON.parse(A.getMeta() || '{}');
const S = JSON.parse(A.getSettings() || '{}');
const key = meta.key || 'none';
let toc = [];            // [{title, p, f, kids, el}]
let flat = [];
let pathIndex = {};
let allEls = content.getElementsByTagName('*');
let jumpStack = [];
let ready = false;

const bookUrl = p => '/book/' + (meta.gen || 0) + '/' + p.split('/').map(encodeURIComponent).join('/');
function resolve(base, rel) {
  try {
    const u = new URL(rel, 'https://x.invalid/' + base);
    if (u.host !== 'x.invalid') return { ext: u.href };
    return { path: decodeURIComponent(u.pathname.slice(1)), frag: decodeURIComponent(u.hash.slice(1)) };
  } catch (e) { return { path: base, frag: '' }; }
}
const byTag = (d, t) => Array.from(d.getElementsByTagNameNS('*', t));
async function fetchText(p) { const r = await fetch(bookUrl(p)); if (!r.ok) throw new Error('404 ' + p); return r.text(); }
function parseXml(t) { return new DOMParser().parseFromString(t, 'application/xml'); }
function parseDoc(t) {
  let d = new DOMParser().parseFromString(t, 'application/xhtml+xml');
  if (d.getElementsByTagName('parsererror').length || !d.body) d = new DOMParser().parseFromString(t, 'text/html');
  return d;
}
const clean = s => (s || '').replace(/\s+/g, ' ').trim();

// ---------------- settings ----------------
function applyTheme() {
  const t = S.theme || S.autoTheme || 'light';
  const h = document.documentElement;
  h.classList.remove('light', 'sepia', 'dark', 'ff-serif', 'ff-sans', 'lh');
  h.classList.add(t);
  if (S.font === 'serif' || S.font === 'sans') h.classList.add('ff-' + S.font);
  if (S.lh && +S.lh > 0) { h.classList.add('lh'); h.style.setProperty('--lh', S.lh); }
  document.querySelectorAll('#themes button').forEach(b => b.classList.toggle('on', b.dataset.t === t));
  document.querySelectorAll('#fonts button').forEach(b => b.classList.toggle('on', b.dataset.f === (S.font || 'book')));
  document.querySelectorAll('#lhs button').forEach(b => b.classList.toggle('on', b.dataset.l === String(S.lh || 0)));
  $('#fVal').textContent = (S.zoom || 100) + '%';
  h.style.setProperty('--uz', String(100 / (S.zoom || 100))); // keep UI text size constant under WebView textZoom
  $('#vol').checked = !!S.vol;
}
function saveSettings(keepPos) {
  const a = keepPos ? anchor() : null;
  const out = Object.assign({}, S); delete out.autoTheme;
  A.setSettings(JSON.stringify(out));
  applyTheme();
  if (a) restoreSoon(a);
}
let pendingAnchor = null, pendingTimer = 0;
function restoreSoon(a) {
  pendingAnchor = a;
  clearTimeout(pendingTimer);
  // textZoom is applied by the WebView asynchronously; re-anchor on resize and after a short delay
  const go = () => { if (pendingAnchor) restore(pendingAnchor); };
  requestAnimationFrame(() => requestAnimationFrame(go));
  pendingTimer = setTimeout(() => { go(); pendingAnchor = null; }, 350);
}
new ResizeObserver(() => { if (pendingAnchor) restore(pendingAnchor); }).observe(content);

// ---------------- position ----------------
function docMax() { return Math.max(1, document.documentElement.scrollHeight - innerHeight); }
function anchor() {
  const pct = scrollY / docMax();
  const ys = document.body.classList.contains('bars') ? [64, 90, 130, 180] : [6, 24, 48, 90, 140];
  for (const xr of [0.5, 0.3, 0.7]) for (const y of ys) {
    let e = document.elementFromPoint(innerWidth * xr, y);
    if (!e || e === content || !content.contains(e)) continue;
    const i = Array.prototype.indexOf.call(allEls, e);
    if (i < 0) continue;
    const r = e.getBoundingClientRect();
    return { i, f: r.height > 0 ? (-r.top) / r.height : 0, pct };
  }
  return { pct };
}
function restore(a) {
  if (!a) return;
  const e = a.i != null ? allEls[a.i] : null;
  if (e) {
    const r = e.getBoundingClientRect();
    window.scrollTo(0, Math.max(0, r.top + scrollY + (a.f || 0) * r.height));
  } else if (a.pct != null) window.scrollTo(0, a.pct * docMax());
}
let saveT = 0;
function saveNow() { if (ready) A.savePos(key, JSON.stringify(anchor())); }
window.saveNow = saveNow;

// ---------------- EPUB ----------------
async function loadEpub() {
  const cont = parseXml(await fetchText('META-INF/container.xml'));
  const rf = byTag(cont, 'rootfile')[0];
  const opfPath = rf.getAttribute('full-path');
  const opf = parseXml(await fetchText(opfPath));
  const items = {};
  byTag(opf, 'item').forEach(it => {
    items[it.getAttribute('id')] = {
      href: resolve(opfPath, it.getAttribute('href')).path,
      type: it.getAttribute('media-type') || '', props: it.getAttribute('properties') || ''
    };
  });
  const title = clean((byTag(opf, 'title')[0] || {}).textContent) || meta.name || 'Книга';
  const author = byTag(opf, 'creator').map(c => clean(c.textContent)).filter(Boolean).join(', ');
  setInfo(title, author);
  const spineEl = byTag(opf, 'spine')[0];
  const spine = byTag(opf, 'itemref').map(r => items[r.getAttribute('idref')])
    .filter(x => x && /html|xml/.test(x.type) && !/(^|\s)nav(\s|$)/.test(x.props));
  const texts = await Promise.all(spine.map(s => fetchText(s.href).catch(() => '')));
  const readerCss = $('#readercss');
  const cssDone = new Set();
  const frag = document.createDocumentFragment();
  spine.forEach((s, i) => {
    if (!texts[i]) return;
    const d = parseDoc(texts[i]);
    d.querySelectorAll('link').forEach(l => {
      if (!/stylesheet/i.test(l.getAttribute('rel') || '')) return;
      const r = resolve(s.href, l.getAttribute('href') || '');
      if (r.ext || cssDone.has(r.path)) return;
      cssDone.add(r.path);
      const n = document.createElement('link'); n.rel = 'stylesheet'; n.href = bookUrl(r.path);
      document.head.insertBefore(n, readerCss);
    });
    d.querySelectorAll('style').forEach(st => {
      const n = document.createElement('style'); n.textContent = st.textContent; document.head.insertBefore(n, readerCss);
    });
    const body = d.body || d.getElementsByTagName('body')[0];
    if (!body) return;
    fixTree(body, s.href);
    const sec = document.createElement('section');
    sec.className = 'ch'; sec.dataset.i = i;
    const bc = body.getAttribute('class'); if (bc) sec.className += ' ' + bc;
    Array.from(body.childNodes).forEach(n => sec.appendChild(document.importNode(n, true)));
    pathIndex[s.href] = sec;
    frag.appendChild(sec);
  });
  content.appendChild(frag);

  // TOC: EPUB3 nav, else NCX
  const navItem = Object.values(items).find(x => /(^|\s)nav(\s|$)/.test(x.props));
  if (navItem) {
    try {
      const d = parseDoc(await fetchText(navItem.href));
      const navs = Array.from(d.getElementsByTagName('nav'));
      const nav = navs.find(n => /toc/.test(n.getAttribute('epub:type') || n.getAttributeNS('http://www.idpf.org/2007/ops', 'type') || '')) || navs[0];
      const ol = nav && Array.from(nav.children).find(c => c.localName === 'ol');
      if (ol) toc = walkOl(ol, navItem.href);
    } catch (e) {}
  }
  if (!toc.length) {
    const ncxId = spineEl && spineEl.getAttribute('toc');
    const ncx = (ncxId && items[ncxId]) || Object.values(items).find(x => /ncx/.test(x.type));
    if (ncx) {
      try {
        const d = parseXml(await fetchText(ncx.href));
        const map = byTag(d, 'navMap')[0];
        if (map) toc = walkNcx(map, ncx.href);
      } catch (e) {}
    }
  }
  if (!toc.length) toc = spine.map((s, i) => {
    const sec = pathIndex[s.href]; const h = sec && sec.querySelector('h1,h2,h3');
    return { title: clean(h && h.textContent) || ('Глава ' + (i + 1)), p: s.href, f: '', kids: [] };
  });
}
function walkOl(ol, base) {
  return Array.from(ol.children).filter(li => li.localName === 'li').map(li => {
    const a = Array.from(li.children).find(c => c.localName === 'a' || c.localName === 'span');
    const sub = Array.from(li.children).find(c => c.localName === 'ol');
    const r = a && a.getAttribute('href') ? resolve(base, a.getAttribute('href')) : { path: null, frag: '' };
    return { title: clean(a && a.textContent) || '…', p: r.path, f: r.frag, kids: sub ? walkOl(sub, base) : [] };
  });
}
function walkNcx(node, base) {
  return Array.from(node.children).filter(c => c.localName === 'navPoint').map(np => {
    const lab = byTag(np, 'text')[0];
    const c = Array.from(np.children).find(x => x.localName === 'content');
    const r = c ? resolve(base, c.getAttribute('src') || '') : { path: null, frag: '' };
    return { title: clean(lab && lab.textContent) || '…', p: r.path, f: r.frag, kids: walkNcx(np, base) };
  });
}
function fixTree(root, base) {
  root.querySelectorAll('script,iframe,object,embed').forEach(n => n.remove());
  const els = root.getElementsByTagName('*');
  for (let k = 0; k < els.length; k++) {
    const el = els[k];
    for (const at of Array.from(el.attributes)) if (/^on/i.test(at.name)) el.removeAttribute(at.name);
    const src = el.getAttribute('src');
    if (src) { const r = resolve(base, src); if (!r.ext) el.setAttribute('src', bookUrl(r.path)); }
    if (el.localName === 'image') {
      const h = el.getAttributeNS(XL, 'href') || el.getAttribute('href');
      if (h) { const r = resolve(base, h); if (!r.ext) { el.setAttributeNS(XL, 'xlink:href', bookUrl(r.path)); el.setAttribute('href', bookUrl(r.path)); } }
    } else if (el.localName === 'a' && el.hasAttribute('href')) {
      const r = resolve(base, el.getAttribute('href'));
      if (r.ext) { el.dataset.ext = r.ext; }
      else { el.dataset.p = r.path; el.dataset.f = r.frag; el.setAttribute('href', '#'); }
    }
  }
}

// ---------------- FB2 ----------------
async function loadFb2() {
  const buf = await (await fetch(bookUrl('book.fb2'))).arrayBuffer();
  const head = new TextDecoder('latin1').decode(new Uint8Array(buf.slice(0, 400)));
  const m = /encoding\s*=\s*["']([\w.-]+)["']/i.exec(head);
  let txt;
  try { txt = new TextDecoder(m ? m[1].toLowerCase() : 'utf-8').decode(buf); }
  catch (e) { txt = new TextDecoder('utf-8').decode(buf); }
  let d = parseXml(txt);
  if (d.getElementsByTagName('parsererror').length) d = new DOMParser().parseFromString(txt, 'text/html');
  const bin = {};
  byTag(d, 'binary').forEach(b => { bin[b.getAttribute('id')] = 'data:' + (b.getAttribute('content-type') || 'image/jpeg') + ';base64,' + b.textContent.replace(/\s+/g, ''); });
  const ti = byTag(d, 'title-info')[0];
  const title = clean(ti && (byTag(ti, 'book-title')[0] || {}).textContent) || meta.name || 'Книга';
  const author = ti ? byTag(ti, 'author').map(a => ['first-name', 'middle-name', 'last-name'].map(t => clean((byTag(a, t)[0] || {}).textContent)).filter(Boolean).join(' ') || clean((byTag(a, 'nickname')[0] || {}).textContent)).filter(Boolean).join(', ') : '';
  setInfo(title, author);
  let secN = 0;
  const INL = { emphasis: 'em', strong: 'strong', strikethrough: 's', sub: 'sub', sup: 'sup', code: 'code', style: 'span' };
  const BLK = { p: 'p', poem: 'div', stanza: 'div', v: 'div', epigraph: 'div', cite: 'div', 'text-author': 'p', subtitle: 'p', date: 'p', annotation: 'div', table: 'table', tr: 'tr', td: 'td', th: 'th' };
  const imgOf = n => {
    const h = n.getAttributeNS(XL, 'href') || n.getAttribute('l:href') || n.getAttribute('xlink:href') || n.getAttribute('href') || '';
    const img = document.createElement('img'); img.src = bin[h.replace(/^#/, '')] || ''; img.alt = '';
    return img;
  };
  function conv(n, depth, list) {
    if (n.nodeType === 3) return document.createTextNode(n.nodeValue);
    if (n.nodeType !== 1) return null;
    const tag = n.localName;
    if (tag === 'section' || tag === 'title') {
      if (tag === 'title') return titleEl(n, depth);
      const s = document.createElement('section'); s.className = 'fb-sec';
      s.id = 'fbs' + (++secN);
      const t = Array.from(n.children).find(c => c.localName === 'title');
      let entry = null;
      if (t) { entry = { title: clean(t.textContent), p: '', f: s.id, kids: [] }; list.push(entry); }
      n.childNodes.forEach(c => { const x = conv(c, depth + 1, entry ? entry.kids : list); if (x) s.appendChild(x); });
      return s;
    }
    if (tag === 'image') { const w = document.createElement(n.parentNode && /^(p|v|subtitle|td)$/.test(n.parentNode.localName) ? 'span' : 'div'); w.className = 'fb-img'; w.appendChild(imgOf(n)); return w; }
    if (tag === 'empty-line') { const e = document.createElement('div'); e.className = 'fb-empty'; return e; }
    if (tag === 'a') {
      const a = document.createElement('a');
      const h = n.getAttributeNS(XL, 'href') || n.getAttribute('l:href') || n.getAttribute('xlink:href') || n.getAttribute('href') || '';
      if (h.startsWith('#')) { a.href = '#'; a.dataset.p = ''; a.dataset.f = h.slice(1); } else { a.href = h; a.dataset.ext = h; }
      n.childNodes.forEach(c => { const x = conv(c, depth, list); if (x) a.appendChild(x); });
      return (n.getAttribute('type') === 'note') ? wrapSup(a) : a;
    }
    const out = document.createElement(INL[tag] || BLK[tag] || 'div');
    out.className = 'fb-' + tag;
    const id = n.getAttribute('id'); if (id) out.id = id;
    n.childNodes.forEach(c => { const x = conv(c, depth, list); if (x) out.appendChild(x); });
    return out;
  }
  const wrapSup = a => { const s = document.createElement('sup'); s.appendChild(a); return s; };
  function titleEl(n, depth) {
    const h = document.createElement('h' + Math.min(depth + 1, 6)); h.className = 'fb-title';
    n.childNodes.forEach(c => { const x = conv(c, depth, []); if (x) h.appendChild(x); });
    return h;
  }
  const frag = document.createDocumentFragment();
  const hd = document.createElement('div'); hd.className = 'fb-head';
  hd.innerHTML = '<div class="a"></div><div class="t"></div>';
  hd.firstChild.textContent = author; hd.lastChild.textContent = title;
  const cover = ti && byTag(ti, 'coverpage')[0];
  const cimg = cover && byTag(cover, 'image')[0];
  if (cimg) { const c = document.createElement('div'); c.className = 'cover'; c.appendChild(imgOf(cimg)); frag.appendChild(c); }
  frag.appendChild(hd);
  const ann = ti && byTag(ti, 'annotation')[0];
  if (ann) { const x = conv(ann, 0, []); x.className = 'fb-annot'; frag.appendChild(x); }
  const root = d.documentElement;
  Array.from(root.children).filter(c => c.localName === 'body').forEach(b => {
    const notes = /notes|comments/.test(b.getAttribute('name') || '');
    const wrap = document.createElement('div'); wrap.className = notes ? 'fb-body fb-notes' : 'fb-body';
    let list = toc;
    if (notes) { wrap.id = 'fbnotes' + (++secN); const e = { title: 'Примечания', p: '', f: wrap.id, kids: [] }; toc.push(e); list = e.kids; }
    b.childNodes.forEach(c => { const x = conv(c, notes ? 1 : 0, notes ? [] : list); if (x) wrap.appendChild(x); });
    frag.appendChild(wrap);
  });
  content.appendChild(frag);
  pathIndex[''] = content;
}

// ---------------- navigation ----------------
function targetOf(p, f) {
  const sec = p == null ? null : pathIndex[p];
  if (!sec) return null;
  if (!f) return sec;
  let e = null;
  try { e = sec.querySelector('[id="' + f.replace(/"/g, '\\"') + '"]') || sec.querySelector('[name="' + f.replace(/"/g, '\\"') + '"]'); } catch (x) {}
  return e || document.getElementById(f) || sec;
}
function scrollToEl(e, push) {
  if (!e) return;
  if (push) jumpStack.push(scrollY);
  const top = e.getBoundingClientRect().top + scrollY - 6;
  window.scrollTo(0, Math.max(0, top));
}
function buildFlat(list, depth, out) { list.forEach(t => { t.depth = depth; out.push(t); buildFlat(t.kids, depth + 1, out); }); return out; }
function currentToc() {
  let cur = null;
  const y = scrollY + 80;
  for (const t of flat) {
    if (!t.el) t.el = targetOf(t.p, t.f);
    if (!t.el) continue;
    const top = t.el.getBoundingClientRect().top + scrollY;
    if (top <= y) { if (!cur || top >= cur._top) { cur = t; cur._top = top; } }
  }
  return cur;
}
function renderToc() {
  const box = $('#tocList');
  box.textContent = '';
  const cur = currentToc();
  const mk = (list, depth, parent) => {
    list.forEach(t => {
      const w = document.createElement('div'); w.className = 'd' + depth;
      const row = document.createElement('div'); row.className = 'ti' + (t === cur ? ' cur' : '');
      row.style.paddingLeft = (depth * 14) + 'px';
      const tw = document.createElement('button'); tw.className = 'tw';
      const a = document.createElement('a'); a.textContent = t.title; a.href = '#';
      a.onclick = e => { e.preventDefault(); closePanels(); hideBars(); jumpStack = []; scrollToEl(targetOf(t.p, t.f), false); };
      row.append(tw, a); w.appendChild(row);
      if (t.kids.length) {
        const k = document.createElement('div'); k.className = 'tk';
        const open = depth < 2 || contains(t, cur);
        if (open) k.classList.add('open');
        tw.textContent = open ? '▾' : '▸';
        tw.onclick = () => { const o = k.classList.toggle('open'); tw.textContent = o ? '▾' : '▸'; };
        mk(t.kids, depth + 1, k); w.appendChild(k);
      }
      parent.appendChild(w);
      if (t === cur) t._row = row;
    });
  };
  mk(toc, 0, box);
  if (cur && cur._row) requestAnimationFrame(() => { cur._row.scrollIntoView({ block: 'center' }); });
}
function contains(t, c) { return !!c && t.kids.some(k => k === c || contains(k, c)); }

// ---------------- UI ----------------
function setInfo(title, author) {
  $('#tTitle').textContent = title; $('#tAuthor').textContent = author || '';
  document.title = title;
  A.setBookInfo(key, title, author || '');
}
const body = document.body;
function showBars() { body.classList.add('bars'); updateProgress(true); }
function hideBars() { body.classList.remove('bars'); }
function toggleBars() { body.classList.contains('bars') ? hideBars() : showBars(); }
function openPanel(id) { closePanels(); $(id).classList.add('open'); body.classList.add('modal'); }
function closePanels() { document.querySelectorAll('.panel.open').forEach(p => p.classList.remove('open')); body.classList.remove('modal'); }
function updateProgress(full) {
  const p = scrollY / docMax();
  $('#pct').textContent = Math.round(p * 100) + '%';
  if (!sliding) $('#slider').value = Math.round(p * 1000);
  if (full) { const c = currentToc(); $('#chap').textContent = c ? c.title : ''; }
}
let sliding = false;
$('#slider').addEventListener('input', e => { sliding = true; window.scrollTo(0, e.target.value / 1000 * docMax()); updateProgress(true); });
$('#slider').addEventListener('change', () => { sliding = false; });
$('#bToc').onclick = () => { renderToc(); openPanel('#tocPanel'); };
$('#bSet').onclick = () => { applyTheme(); openPanel('#setPanel'); };
$('#bLib').onclick = () => { renderLib(); openPanel('#libPanel'); };
$('#shade').onclick = closePanels;
document.querySelectorAll('#themes button').forEach(b => b.onclick = () => { S.theme = b.dataset.t; saveSettings(false); });
document.querySelectorAll('#fonts button').forEach(b => b.onclick = () => { S.font = b.dataset.f; saveSettings(true); });
document.querySelectorAll('#lhs button').forEach(b => b.onclick = () => { S.lh = +b.dataset.l; saveSettings(true); });
$('#fMinus').onclick = () => { S.zoom = Math.max(60, (S.zoom || 100) - 10); saveSettings(true); };
$('#fPlus').onclick = () => { S.zoom = Math.min(260, (S.zoom || 100) + 10); saveSettings(true); };
$('#vol').onchange = e => { S.vol = e.target.checked; saveSettings(false); };
$('#openFile').onclick = () => { closePanels(); saveNow(); A.openFile(); };
$('#openBundled').onclick = () => { closePanels(); if (!meta.bundled) { saveNow(); A.openBundled(); } };
function renderLib() {
  $('#openBundled').classList.toggle('cur', !!meta.bundled);
  const box = $('#recent'); box.textContent = '';
  const r = JSON.parse(A.getRecent() || '[]');
  if (!r.length) return;
  const h = document.createElement('div'); h.className = 'rh'; h.textContent = 'Недавние файлы'; box.appendChild(h);
  r.forEach(o => {
    const b = document.createElement('button'); b.className = 'li' + (o.key === key ? ' cur' : '');
    const t = document.createElement('b'); t.textContent = o.title || o.name;
    const s = document.createElement('small'); s.textContent = [o.author, o.name].filter(Boolean).join(' · ');
    b.append(t, s);
    b.onclick = () => { closePanels(); if (o.key !== key) { saveNow(); A.openRecent(o.key); } };
    box.appendChild(b);
  });
}
content.addEventListener('click', e => {
  const a = e.target.closest('a');
  if (a && a.dataset.p !== undefined) { e.preventDefault(); scrollToEl(targetOf(a.dataset.p, a.dataset.f), true); return; }
  if (a && a.dataset.ext) { e.preventDefault(); A.openExternal(a.dataset.ext); return; }
  if (a) return;
  if (getSelection && String(getSelection()).length) return;
  toggleBars();
});
window.addEventListener('scroll', () => {
  if (!ready) return;
  if (body.classList.contains('bars') && !sliding) hideBars();
  clearTimeout(saveT); saveT = setTimeout(() => { saveNow(); }, 600);
}, { passive: true });
window.page = dir => { window.scrollBy({ top: dir * (innerHeight - 60), behavior: 'smooth' }); };
window.handleBack = () => {
  if (document.querySelector('.panel.open')) { closePanels(); return true; }
  if (body.classList.contains('bars')) { hideBars(); return true; }
  if (jumpStack.length) { window.scrollTo(0, jumpStack.pop()); return true; }
  saveNow();
  return false;
};

// ---------------- start ----------------
async function start() {
  applyTheme();
  try {
    if (meta.format === 'fb2') await loadFb2(); else await loadEpub();
  } catch (e) {
    content.innerHTML = '<p style="text-align:center;margin-top:30vh">Не удалось прочитать книгу.</p>';
    A.toast('Ошибка: ' + e.message);
  }
  flat = buildFlat(toc, 0, []);
  // wait for images so restored position is stable
  const imgs = Array.from(content.querySelectorAll('img')).filter(i => !i.complete);
  await Promise.race([
    Promise.all(imgs.map(i => new Promise(r => { i.onload = i.onerror = r; }))),
    new Promise(r => setTimeout(r, 2500))
  ]);
  let pos = null; try { pos = JSON.parse(A.getPos(key) || 'null'); } catch (e) {}
  restore(pos);
  requestAnimationFrame(() => { restore(pos); ready = true; $('#loading').classList.add('hide'); });
}
start();
})();
