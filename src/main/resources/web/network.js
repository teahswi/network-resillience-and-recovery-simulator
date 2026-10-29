/* Network tab: editing, failures, search, routing with automatic rerouting. */
'use strict';

(function () {
  const S = {
    mode: 'select',
    sel: null,            // {kind:'node'|'edge', id}
    pendingLink: null,    // first router clicked in "+ Link" mode
    route: null,          // last route request {src, dst, mode, routes:[...]}
    oldRoute: null,       // previous route, shown dashed after a reroute
  };

  const HINTS = {
    select: 'Drag routers to move them. Click to select. Scroll to zoom, drag empty space to pan.',
    addNode: 'Click empty space to place a new router.',
    addEdge: 'Click two routers to link them (cost = distance).',
    toggle: 'Click a router or link to fail it; click again to repair.',
    delete: 'Click a router or link to delete it (Undo brings it back).',
  };

  const tab = {
    view: {
      hint: () => S.pendingLink !== null ? `Link from ${NR.name(S.pendingLink)}: now click the second router (Esc to cancel).` : HINTS[S.mode],
      nodeClass: n => {
        if (S.pendingLink === n.id) return 'pending';
        if (S.sel && S.sel.kind === 'node' && S.sel.id === n.id) return 'selected';
        if (S.route && S.route.routes.length && (n.id === S.route.src || n.id === S.route.dst)) return 'st-path';
        return '';
      },
      edgeClass: e => (S.sel && S.sel.kind === 'edge' && S.sel.id === e.id ? 'selected' : ''),
      nodeInner: n => (S.route && n.id === S.route.src ? 'S' : S.route && n.id === S.route.dst ? 'D' : ''),
      overlay: () => {
        let out = '';
        if (S.oldRoute) out += NR.pathLine(S.oldRoute.nodes, '', 4, 'old-route');
        if (S.route) {
          const colors = NR.ROUTE_COLORS();
          // draw in reverse so route 1 ends up on top
          for (let i = S.route.routes.length - 1; i >= 0; i--) {
            const r = S.route.routes[i];
            if (r.found) out += NR.pathLine(r.nodes, colors[i % colors.length], i === 0 ? 8 : 5);
          }
        }
        return out;
      },
      legend: () => {
        if (!S.route) return '';
        const colors = NR.ROUTE_COLORS();
        const items = S.route.routes.filter(r => r.found).map((r, i) => [colors[i % colors.length], r.algorithm, true]);
        if (S.oldRoute) items.push([NR.css('--muted'), 'route before failure', true]);
        return NR.legendItems(items);
      },
    },

    canDragNodes: () => S.mode === 'select',

    init() {
      for (const b of $$('#modeSeg button')) b.addEventListener('click', () => setMode(b.dataset.mode));
      $('#btnUndo').addEventListener('click', NR.guard(() => edit({ op: 'undo' })));
      $('#btnRedo').addEventListener('click', NR.guard(() => edit({ op: 'redo' })));
      $('#btnRepairAll').addEventListener('click', NR.guard(() => edit({ op: 'repairAll' })));
      $('#btnLayout').addEventListener('click', NR.guard(() => edit({ op: 'layout' })));
      $('#btnRoute').addEventListener('click', NR.guard(() => findRoute(true)));
      $('#routeMode').addEventListener('change', NR.guard(() => { if (S.route) findRoute(true); }));
      setupSearch();
      document.addEventListener('keydown', e => {
        if (NR.tab !== 'network' || e.target.matches('input, select, textarea')) return;
        const k = e.key.toLowerCase();
        if ((e.ctrlKey || e.metaKey) && k === 'z' && !e.shiftKey) { e.preventDefault(); NR.guard(() => edit({ op: 'undo' }))(); }
        else if ((e.ctrlKey || e.metaKey) && (k === 'y' || (k === 'z' && e.shiftKey))) { e.preventDefault(); NR.guard(() => edit({ op: 'redo' }))(); }
        else if (k === 'escape') { S.pendingLink = null; S.sel = null; inspector(); NR.render(); }
        else if ((k === 'delete' || k === 'backspace') && S.sel) {
          e.preventDefault();
          NR.guard(() => edit(S.sel.kind === 'node' ? { op: 'deleteNode', id: S.sel.id } : { op: 'deleteEdge', id: S.sel.id }))();
        }
      });
    },

    onGraph(g) {
      if (S.sel && !(S.sel.kind === 'node' ? NR.nodeById.has(S.sel.id) : NR.edgeById.has(S.sel.id))) S.sel = null;
      if (S.pendingLink !== null && !NR.nodeById.has(S.pendingLink)) S.pendingLink = null;
      renderStacks(g);
      inspector();
    },

    onReset() { S.sel = null; S.pendingLink = null; S.route = null; S.oldRoute = null; $('#routeResult').innerHTML = ''; },

    onNodeClick: NR.guard(async id => {
      switch (S.mode) {
        case 'addEdge':
          if (S.pendingLink === null) { S.pendingLink = id; NR.render(); return; }
          if (S.pendingLink === id) { S.pendingLink = null; NR.render(); return; }
          { const u = S.pendingLink; S.pendingLink = null; await edit({ op: 'addEdge', u, v: id }); }
          return;
        case 'toggle': {
          const n = NR.nodeById.get(id);
          await edit({ op: n.up ? 'fail' : 'repair', kind: 'node', id });
          return;
        }
        case 'delete': await edit({ op: 'deleteNode', id }); return;
        default: select({ kind: 'node', id });
      }
    }),

    onEdgeClick: NR.guard(async id => {
      switch (S.mode) {
        case 'toggle': { const e = NR.edgeById.get(id); await edit({ op: e.up ? 'fail' : 'repair', kind: 'edge', id }); return; }
        case 'delete': await edit({ op: 'deleteEdge', id }); return;
        default: select({ kind: 'edge', id });
      }
    }),

    onBackgroundClick: NR.guard(async p => {
      if (S.mode === 'addNode') await edit({ op: 'addNode', x: p.x, y: p.y });
      else { S.sel = null; S.pendingLink = null; inspector(); NR.render(); }
    }),

    onNodeMoved: NR.guard(async (id, x, y) => { await edit({ op: 'moveNode', id, x, y }, true); }),
  };
  NR.tabs.network = tab;

  function setMode(m) {
    S.mode = m;
    S.pendingLink = null;
    for (const b of $$('#modeSeg button')) b.classList.toggle('active', b.dataset.mode === m);
    NR.render();
  }

  function select(sel) { S.sel = sel; inspector(); NR.render(); }

  /** Sends an edit to the server; the reply contains the updated network. */
  async function edit(params, quiet = false) {
    const res = await NR.api('edit', params, 'POST');
    NR.setGraph(res.graph);
    if (!quiet) NR.toast(res.msg);
    if (S.route) await afterChange();
  }

  /** The network changed while a route is shown: reroute automatically (or drop the route). */
  async function afterChange() {
    if (!$('#autoReroute').checked) { S.route = null; S.oldRoute = null; $('#routeResult').innerHTML = ''; NR.render(); return; }
    const before = S.route.routes[0];
    if (!NR.nodeById.has(S.route.src) || !NR.nodeById.has(S.route.dst)) { S.route = null; $('#routeResult').innerHTML = ''; NR.render(); return; }
    const srcUp = NR.nodeById.get(S.route.src).up, dstUp = NR.nodeById.get(S.route.dst).up;
    if (!srcUp || !dstUp) {
      S.oldRoute = before && before.found ? before : S.oldRoute;
      S.route.routes = [];
      $('#routeResult').innerHTML = `<div class="route-item" style="border-left-color:var(--danger)">The ${!srcUp ? 'source' : 'destination'} router is down: no route possible.</div>`;
      NR.render();
      return;
    }
    await findRoute(false);
    const after = S.route.routes[0];
    const changed = before && after && before.found && (!after.found || before.edges.join() !== after.edges.join());
    if (changed) {
      S.oldRoute = before;
      NR.toast(after.found ? `Rerouted: cost ${fmt(before.cost)} → ${fmt(after.cost)}, ${before.hops} → ${after.hops} hops` : 'Route lost: the destination is now unreachable', !after.found);
    } else if (after && after.found && S.oldRoute && after.edges.join() === S.oldRoute.edges.join()) {
      S.oldRoute = null;   // back on the original route after a repair
    }
    NR.render();
  }

  async function findRoute(fresh) {
    const src = +$('#routeSrc').value, dst = +$('#routeDst').value;
    if (src === dst) throw new Error('Pick two different routers.');
    const mode = $('#routeMode').value;
    if (fresh) S.oldRoute = null;
    const res = await NR.api('route', { src, dst, mode, k: 3 });
    S.route = { src, dst, mode, routes: res.routes };
    showRoutes();
    NR.render();
  }

  function showRoutes() {
    const colors = NR.ROUTE_COLORS();
    const mode = S.route.mode;
    $('#routeResult').innerHTML = S.route.routes.map((r, i) => {
      if (!r.found) return `<div class="route-item" style="border-left-color:var(--danger)"><div class="route-text">${esc(r.algorithm)}: no route</div><div class="route-meta">${esc(NR.name(S.route.src))} cannot reach ${esc(NR.name(S.route.dst))}</div></div>`;
      let meta = `cost ${fmt(r.cost)} · ${r.hops} hops · ${fmt(r.micros, 1)} µs`;
      if (mode === 'safest') meta += ` · crosses ${r.visited} critical link(s)`;
      else if (r.algorithm === 'Bellman-Ford') meta += ` · ${r.visited} round(s)`;
      else meta += ` · settled ${r.visited} routers`;
      return `<div class="route-item" style="border-left-color:${colors[i % colors.length]}"><div class="route-text">${esc(r.algorithm)}</div><div>${esc(r.text)}</div><div class="route-meta">${meta}</div></div>`;
    }).join('');
  }

  function renderStacks(g) {
    $('#undoList').innerHTML = g.undo.map(s => `<li>${esc(s)}</li>`).join('');
    $('#redoList').innerHTML = g.redo.map(s => `<li>${esc(s)}</li>`).join('');
    $('#btnUndo').disabled = g.undo.length === 0;
    $('#btnRedo').disabled = g.redo.length === 0;
  }

  function inspector() {
    const box = $('#inspectorBody');
    if (!S.sel) { box.className = 'muted'; box.innerHTML = 'Click a router or link.'; return; }
    box.className = '';
    if (S.sel.kind === 'node') {
      const n = NR.nodeById.get(S.sel.id);
      const links = NR.graph.edges.filter(e => e.u === n.id || e.v === n.id);
      box.innerHTML = `
        <dl class="kv"><dt>Router</dt><dd>${esc(n.name)}</dd><dt>Status</dt><dd>${n.up ? 'up' : '<span style="color:var(--danger)">FAILED</span>'}</dd>
        <dt>Degree</dt><dd>${links.length} link(s)</dd><dt>Neighbours</dt><dd style="white-space:normal">${links.map(e => esc(NR.name(e.u === n.id ? e.v : e.u))).join(', ') || '-'}</dd></dl>
        <div class="row gap wrap">
          <button class="ghost small" id="iToggle">${n.up ? 'Fail router' : 'Repair router'}</button>
          <button class="ghost small" id="iDel">Delete</button>
          <button class="ghost small" id="iFrom">Route from here</button>
          <button class="ghost small" id="iTo">Route to here</button>
        </div>`;
      $('#iToggle').onclick = NR.guard(() => edit({ op: n.up ? 'fail' : 'repair', kind: 'node', id: n.id }));
      $('#iDel').onclick = NR.guard(() => edit({ op: 'deleteNode', id: n.id }));
      $('#iFrom').onclick = () => { $('#routeSrc').value = n.id; };
      $('#iTo').onclick = NR.guard(async () => { $('#routeDst').value = n.id; if (+$('#routeSrc').value !== n.id) await findRoute(true); });
    } else {
      const e = NR.edgeById.get(S.sel.id);
      box.innerHTML = `
        <dl class="kv"><dt>Link</dt><dd>${esc(NR.name(e.u))} – ${esc(NR.name(e.v))}</dd><dt>Status</dt><dd>${e.up ? 'up' : '<span style="color:var(--danger)">FAILED</span>'}</dd></dl>
        <div class="grid2" style="margin-top:8px"><label>Cost<input type="number" id="iCost" value="${e.cost}" min="0.1" step="0.1"></label><label>Capacity<input type="number" id="iCap" value="${e.cap}" min="1"></label></div>
        <div class="row gap wrap">
          <button class="primary small" id="iSave">Save</button>
          <button class="ghost small" id="iToggle">${e.up ? 'Fail link' : 'Repair link'}</button>
          <button class="ghost small" id="iDel">Delete</button>
        </div>`;
      $('#iSave').onclick = NR.guard(() => edit({ op: 'updateEdge', id: e.id, cost: $('#iCost').value, cap: $('#iCap').value }));
      $('#iToggle').onclick = NR.guard(() => edit({ op: e.up ? 'fail' : 'repair', kind: 'edge', id: e.id }));
      $('#iDel').onclick = NR.guard(() => edit({ op: 'deleteEdge', id: e.id }));
    }
  }

  function setupSearch() {
    const input = $('#searchInput'), list = $('#searchResults');
    let items = [], hl = 0, timer;
    const show = () => {
      const q = input.value;
      list.innerHTML = items.map((it, i) => `<li data-id="${it.id}" class="${i === hl ? 'hl' : ''}"><b>${esc(it.name.slice(0, q.length))}</b>${esc(it.name.slice(q.length))}</li>`).join('');
    };
    const pick = it => {
      if (!it) return;
      input.value = it.name;
      list.innerHTML = '';
      S.sel = { kind: 'node', id: it.id };
      inspector();
      NR.render();
    };
    input.addEventListener('input', () => {
      clearTimeout(timer);
      timer = setTimeout(NR.guard(async () => {
        if (!input.value) { items = []; list.innerHTML = ''; return; }
        items = await NR.api('search', { q: input.value });
        hl = 0;
        show();
      }), 80);
    });
    input.addEventListener('keydown', e => {
      if (e.key === 'ArrowDown') { hl = Math.min(items.length - 1, hl + 1); show(); e.preventDefault(); }
      else if (e.key === 'ArrowUp') { hl = Math.max(0, hl - 1); show(); e.preventDefault(); }
      else if (e.key === 'Enter') pick(items[hl]);
      else if (e.key === 'Escape') list.innerHTML = '';
    });
    list.addEventListener('mousedown', e => {
      const li = e.target.closest('li');
      if (li) pick(items.find(it => it.id === +li.dataset.id));
    });
    input.addEventListener('blur', () => setTimeout(() => (list.innerHTML = ''), 150));
  }
})();
