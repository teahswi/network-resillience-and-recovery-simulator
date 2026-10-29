/* NetRescue: shared core. Talks to the Java server, draws the network, routes clicks to the active tab.
   All data structures and algorithms run in Java; this file only draws what the server returns. */
'use strict';

const NR = {
  graph: { nodes: [], edges: [], title: '', undo: [], redo: [] },
  nodeById: new Map(),
  edgeById: new Map(),
  tab: 'network',
  tabs: {},                 // tab modules register themselves here
  view: null,               // decorations provided by the active tab
  zoom: { s: 1, x: 0, y: 0 },
};

/* ---------------------------------------------------------------- helpers */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = (v, d = 2) => {
  if (v === null || v === undefined || Number.isNaN(v)) return '-';
  if (!Number.isFinite(v)) return '∞';
  return Number.isInteger(v) ? String(v) : String(Math.round(v * 10 ** d) / 10 ** d);
};
const pct = v => Math.round(v * 100) + '%';

NR.api = async function (path, params = {}, method = 'GET', body = null) {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null) qs.set(k, v);
  const url = '/api/' + path + (qs.toString() ? '?' + qs : '');
  const res = await fetch(url, { method, body, headers: body ? { 'Content-Type': 'text/plain; charset=utf-8' } : {} });
  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { throw new Error('Server sent an unreadable reply: ' + text.slice(0, 120)); }
  if (!res.ok) throw new Error(data.error || ('HTTP ' + res.status));
  return data;
};

let toastTimer;
NR.toast = function (msg, isErr = false) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = 'toast show' + (isErr ? ' err' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.className = 'toast'), isErr ? 4200 : 2400);
};

/** Wraps an async action so any error shows as a red toast instead of failing silently. */
NR.guard = fn => async (...args) => {
  try { return await fn(...args); } catch (e) { console.error(e); NR.toast(e.message, true); }
};

/* ---------------------------------------------------------------- graph state */
NR.setGraph = function (g) {
  NR.graph = g;
  NR.nodeById = new Map(g.nodes.map(n => [n.id, n]));
  NR.edgeById = new Map(g.edges.map(e => [e.id, e]));
  $('#netTitle').textContent = g.title;
  const down = g.nodes.filter(n => !n.up).length + g.edges.filter(e => !e.up).length;
  $('#netStats').textContent = `${g.nodes.length} routers · ${g.edges.length} links` + (down ? ` · ${down} failed` : '');
  NR.fillNodeSelects();
  for (const t of Object.values(NR.tabs)) if (t.onGraph) t.onGraph(g);
  NR.render();
};

NR.loadGraph = async function () { NR.setGraph(await NR.api('graph')); };

NR.fillNodeSelects = function () {
  const sorted = [...NR.graph.nodes].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
  const opts = sorted.map(n => `<option value="${n.id}">${esc(n.name)}</option>`).join('');
  for (const sel of $$('.nodeSelect')) {
    const prev = sel.value;
    sel.innerHTML = opts;
    if (prev && NR.nodeById.has(+prev)) sel.value = prev;
  }
  // sensible defaults: first "To" select = farthest-apart pair by name order
  for (const [a, b] of [['#routeSrc', '#routeDst'], ['#algoSrc', '#algoDst'], ['#tSrc', '#tDst']]) {
    const sa = $(a), sb = $(b);
    if (sa && sb && sa.value === sb.value && sorted.length > 1) sb.value = sorted[sorted.length - 1].id;
  }
};

NR.name = id => (NR.nodeById.get(id) || { name: '?' }).name;
NR.edgeName = id => { const e = NR.edgeById.get(id); return e ? NR.name(e.u) + '–' + NR.name(e.v) : '?'; };

/* ---------------------------------------------------------------- drawing */
const NODE_R = 15;

NR.render = function () {
  const g = NR.graph, v = NR.view || {};
  const showCosts = $('#showCosts').checked, showNames = $('#showNames').checked;
  let edges = '', labels = '', nodes = '';
  for (const e of g.edges) {
    const a = NR.nodeById.get(e.u), b = NR.nodeById.get(e.v);
    if (!a || !b) continue;
    const cls = ['edge'];
    const down = v.edgeDown ? v.edgeDown(e) : (!e.up || !a.up || !b.up);
    if (down) cls.push('down');
    const extra = v.edgeClass ? v.edgeClass(e) : '';
    if (extra) cls.push(extra);
    const width = v.edgeWidth ? v.edgeWidth(e) : null;
    const stroke = v.edgeColor ? v.edgeColor(e) : null;
    const style = (width ? `stroke-width:${width};` : '') + (stroke ? `stroke:${stroke};` : '');
    edges += `<line class="${cls.join(' ')}" x1="${a.x}" y1="${a.y}" x2="${b.x}" y2="${b.y}" style="${style}"/>`;
    edges += `<line class="edge-hit" data-edge="${e.id}" x1="${a.x}" y1="${a.y}" x2="${b.x}" y2="${b.y}"><title>${esc(a.name)} – ${esc(b.name)}: cost ${fmt(e.cost)}, capacity ${e.cap}${e.up ? '' : ' (FAILED)'}</title></line>`;
    const lab = v.edgeLabel ? v.edgeLabel(e) : (showCosts ? fmt(e.cost, 1) : '');
    if (lab !== '' && lab !== null) {
      const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
      labels += `<text class="${v.edgeLabel ? 'load-label' : 'edge-label'}" x="${mx}" y="${my - 5}" text-anchor="middle">${esc(lab)}</text>`;
    }
    if (v.edgeDown ? v.edgeDown(e) && !(v.nodeDown(a) || v.nodeDown(b)) : !e.up) {
      const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
      labels += `<path class="xmark" d="M${mx - 6} ${my - 6}L${mx + 6} ${my + 6}M${mx + 6} ${my - 6}L${mx - 6} ${my + 6}"/>`;
    }
  }
  for (const n of g.nodes) {
    const cls = ['node'];
    if (v.nodeDown ? v.nodeDown(n) : !n.up) cls.push('down');
    const extra = v.nodeClass ? v.nodeClass(n) : '';
    if (extra) cls.push(extra);
    const inner = v.nodeInner ? v.nodeInner(n) : '';
    nodes += `<g class="${cls.join(' ')}" data-node="${n.id}" transform="translate(${n.x} ${n.y})">` +
      `<circle r="${NODE_R}"/><text class="node-inner">${esc(inner)}</text><title>${esc(n.name)}${n.up ? '' : ' (FAILED)'}</title></g>`;
    if (showNames) labels += `<text class="node-name" x="${n.x}" y="${n.y + NODE_R + 14}">${esc(n.name)}</text>`;
    const badge = v.badge ? v.badge(n) : '';
    if (badge !== '' && badge !== null && badge !== undefined) labels += `<text class="node-badge" x="${n.x}" y="${n.y - NODE_R - 7}">${esc(badge)}</text>`;
  }
  $('#layerEdges').innerHTML = edges;
  $('#layerNodes').innerHTML = nodes;
  $('#layerLabels').innerHTML = labels;
  $('#layerOverlay').innerHTML = v.overlay ? v.overlay() : '';
  $('#legend').innerHTML = v.legend ? v.legend() : '';
  $('#canvasHint').textContent = v.hint ? v.hint() : '';
  applyZoom();
};

/** SVG polyline through a list of node ids. */
NR.pathLine = function (nodeIds, color, width = 7, cls = 'route-line', dash = '') {
  const pts = nodeIds.map(id => NR.nodeById.get(id)).filter(Boolean).map(n => `${n.x},${n.y}`).join(' ');
  return pts ? `<polyline class="${cls}" points="${pts}" style="stroke:${color};stroke-width:${width};${dash ? 'stroke-dasharray:' + dash : ''}"/>` : '';
};

NR.legendItems = items => items.map(([color, label, line]) =>
  `<span><i class="${line ? 'line' : ''}" style="background:${color}"></i>${esc(label)}</span>`).join('');

NR.css = name => getComputedStyle(document.documentElement).getPropertyValue(name).trim();
NR.ROUTE_COLORS = () => [NR.css('--ok'), NR.css('--accent'), NR.css('--violet'), NR.css('--warn'), NR.css('--danger')];

/* ---------------------------------------------------------------- zoom & pan */
function applyZoom() {
  const z = NR.zoom;
  $('#viewport').setAttribute('transform', `translate(${z.x} ${z.y}) scale(${z.s})`);
}

/** Converts a mouse position to network coordinates (undoing zoom and pan). */
NR.toWorld = function (evt) {
  const svg = $('#canvas');
  const p = svg.createSVGPoint();
  p.x = evt.clientX; p.y = evt.clientY;
  const m = $('#viewport').getScreenCTM().inverse();
  const w = p.matrixTransform(m);
  return { x: Math.round(w.x), y: Math.round(w.y) };
};

NR.fit = function () { NR.zoom = { s: 1, x: 0, y: 0 }; applyZoom(); };

function setupCanvas() {
  const svg = $('#canvas');
  let drag = null;   // {kind:'node'|'pan', id, start, orig, moved}

  svg.addEventListener('wheel', e => {
    e.preventDefault();
    const before = NR.toWorld(e);
    const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15;
    NR.zoom.s = Math.min(6, Math.max(0.3, NR.zoom.s * factor));
    applyZoom();
    const after = NR.toWorld(e);
    NR.zoom.x += (after.x - before.x) * NR.zoom.s;
    NR.zoom.y += (after.y - before.y) * NR.zoom.s;
    applyZoom();
  }, { passive: false });

  svg.addEventListener('pointerdown', e => {
    const nodeEl = e.target.closest('[data-node]');
    const edgeEl = e.target.closest('[data-edge]');
    const tab = NR.tabs[NR.tab];
    if (nodeEl) {
      const id = +nodeEl.dataset.node;
      const n = NR.nodeById.get(id);
      const canDrag = tab && tab.canDragNodes && tab.canDragNodes();
      drag = { kind: 'node', id, start: NR.toWorld(e), orig: { x: n.x, y: n.y }, moved: false, canDrag };
      svg.setPointerCapture(e.pointerId);
    } else if (edgeEl) {
      drag = { kind: 'edge', id: +edgeEl.dataset.edge };
    } else {
      drag = { kind: 'pan', sx: e.clientX, sy: e.clientY, ox: NR.zoom.x, oy: NR.zoom.y, moved: false, world: NR.toWorld(e) };
      svg.setPointerCapture(e.pointerId);
    }
  });

  svg.addEventListener('pointermove', e => {
    if (!drag) return;
    if (drag.kind === 'node' && drag.canDrag) {
      const p = NR.toWorld(e);
      const dx = p.x - drag.start.x, dy = p.y - drag.start.y;
      if (Math.abs(dx) + Math.abs(dy) > 3) drag.moved = true;
      if (drag.moved) {
        const n = NR.nodeById.get(drag.id);
        n.x = drag.orig.x + dx; n.y = drag.orig.y + dy;
        NR.render();
      }
    } else if (drag.kind === 'pan') {
      const rect = svg.getBoundingClientRect();
      const scale = 1000 / rect.width > 640 / rect.height ? 1000 / rect.width : 640 / rect.height;
      const dx = (e.clientX - drag.sx) * scale, dy = (e.clientY - drag.sy) * scale;
      if (Math.abs(dx) + Math.abs(dy) > 4) drag.moved = true;
      NR.zoom.x = drag.ox + dx; NR.zoom.y = drag.oy + dy;
      applyZoom();
    }
  });

  svg.addEventListener('pointerup', e => {
    const d = drag;
    drag = null;
    if (!d) return;
    const tab = NR.tabs[NR.tab];
    if (!tab) return;
    if (d.kind === 'node') {
      if (d.moved && d.canDrag) {
        const n = NR.nodeById.get(d.id);
        tab.onNodeMoved && tab.onNodeMoved(d.id, n.x, n.y, d.orig);
      } else tab.onNodeClick && tab.onNodeClick(d.id, e);
    } else if (d.kind === 'edge') {
      tab.onEdgeClick && tab.onEdgeClick(d.id, e);
    } else if (d.kind === 'pan' && !d.moved) {
      tab.onBackgroundClick && tab.onBackgroundClick(d.world, e);
    }
  });
}

/* ---------------------------------------------------------------- tabs */
NR.switchTab = function (name) {
  const prev = NR.tabs[NR.tab];
  if (prev && prev.onLeave) prev.onLeave();
  NR.tab = name;
  for (const b of $$('.tab')) b.classList.toggle('active', b.dataset.tab === name);
  for (const p of $$('.panel')) p.hidden = p.dataset.panel !== name;
  const full = name === 'bench' || name === 'lab';
  $('#stage').hidden = full;
  $('#side').hidden = full;
  $('#benchPage').hidden = name !== 'bench';
  $('#labPage').hidden = name !== 'lab';
  $('#main').classList.toggle('full', full);
  const t = NR.tabs[name];
  NR.view = t && t.view ? t.view : null;
  if (t && t.onEnter) t.onEnter();
  if (!full) NR.render();
};

/* ---------------------------------------------------------------- loading networks */
async function loadSamples() {
  const list = await NR.api('samples');
  $('#sampleSelect').innerHTML = '<option value="">Load sample network…</option>' +
    list.map(s => `<option value="${s.key}">${esc(s.name)}</option>`).join('');
}

NR.replaceGraph = function (g) {
  NR.fit();
  for (const t of Object.values(NR.tabs)) if (t.onReset) t.onReset();
  NR.setGraph(g);
};

function setupLoaders() {
  $('#sampleSelect').addEventListener('change', NR.guard(async e => {
    const key = e.target.value;
    if (!key) return;
    NR.replaceGraph(await NR.api('load', { sample: key }, 'POST'));
    NR.toast('Loaded ' + NR.graph.title);
    e.target.value = '';
  }));
  $('#btnOpenFile').addEventListener('click', () => $('#fileInput').click());
  $('#fileInput').addEventListener('change', NR.guard(async e => {
    const f = e.target.files[0];
    if (!f) return;
    const text = await f.text();
    e.target.value = '';
    NR.replaceGraph(await NR.api('load', { format: f.name.toLowerCase().endsWith('.gml') ? 'gml' : 'txt' }, 'POST', text));
    NR.toast(`Loaded ${f.name}: ${NR.graph.nodes.length} routers, ${NR.graph.edges.length} links`);
  }));
  $('#btnGenerate').addEventListener('click', () => { $('#modal').hidden = false; $('#gN').focus(); });
  $('#gCancel').addEventListener('click', () => ($('#modal').hidden = true));
  $('#gOk').addEventListener('click', NR.guard(async () => {
    const g = await NR.api('generate', { n: $('#gN').value, alpha: $('#gAlpha').value, beta: $('#gBeta').value, seed: $('#gSeed').value }, 'POST');
    $('#modal').hidden = true;
    NR.replaceGraph(g);
    NR.toast('Generated ' + g.title);
  }));
  $('#modal').addEventListener('click', e => { if (e.target.id === 'modal') $('#modal').hidden = true; });
}

/* ---------------------------------------------------------------- start */
window.addEventListener('DOMContentLoaded', NR.guard(async () => {
  setupCanvas();
  setupLoaders();
  for (const b of $$('.tab')) b.addEventListener('click', () => NR.switchTab(b.dataset.tab));
  $('#showCosts').addEventListener('change', () => NR.render());
  $('#showNames').addEventListener('change', () => NR.render());
  $('#btnFit').addEventListener('click', () => NR.fit());
  for (const t of Object.values(NR.tabs)) if (t.init) t.init();
  await loadSamples();
  await NR.loadGraph();
  NR.switchTab('network');
}));
