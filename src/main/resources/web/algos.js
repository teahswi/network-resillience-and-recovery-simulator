/* Algorithms tab: replays the step-by-step trace recorded by the Java algorithm. */
'use strict';

(function () {
  const ABOUT = {
    bfs: '<b>Breadth-first search</b> explores in rings: every router 1 hop away, then 2 hops, and so on. A FIFO <b>queue</b> keeps that order, so the first time it reaches the target it has used the fewest hops. Time O(V + E).',
    dfs: '<b>Depth-first search</b> goes as deep as it can, then backtracks. Here the explicit <b>stack</b> replaces recursion: pop a router, visit it, push its unvisited neighbours. Time O(V + E).',
    dijkstra: '<b>Dijkstra</b> always settles the unsettled router with the smallest tentative distance (taken from an <b>indexed min-heap</b>), then relaxes its links: if dist[u] + w &lt; dist[v], lower dist[v]. Correct because costs are never negative. Time O((V + E) log V).',
    bellman: '<b>Bellman-Ford</b> relaxes <i>every</i> link in rounds. After round k, all shortest paths with at most k links are correct, so V − 1 rounds suffice; it stops early when a round changes nothing. Handles negative costs. Time O(V·E).',
    tarjan: '<b>Tarjan</b> finds critical routers and links in one DFS. disc = discovery time, low = earliest disc reachable from the subtree using one back link. Child v of u with low[v] ≥ disc[u] makes u critical; low[v] &gt; disc[u] makes u–v a bridge. Labels show disc/low. Time O(V + E).',
    kruskal: '<b>Kruskal</b> sorts links by cost (merge sort), then takes each link unless both ends are already connected, which <b>union-find</b> answers in near O(1). Result: the cheapest set of links that keeps everything connected. Time O(E log E).',
    kruskalq: '<b>Kruskal</b> with <b>quicksort</b> instead of merge sort. Same tree, different sort: quicksort is in place but not stable; merge sort is stable but needs extra memory. Time O(E log E) expected.',
    prim: '<b>Prim</b> grows a single tree from one router, always adding the cheapest link that leaves the tree. A <b>min-heap</b> holds candidate links; stale ones (both ends already inside) are discarded when popped. Time O(E log E).',
  };
  const NEEDS = { bfs: 'both', dfs: 'src', dijkstra: 'both', bellman: 'both', tarjan: 'none', kruskal: 'none', kruskalq: 'none', prim: 'none' };

  const S = { trace: null, result: null, name: null, idx: 0, timer: null, nodes: new Map(), edges: new Map(), labels: new Map(), ds: null };

  const tab = {
    view: {
      nodeClass: n => (S.nodes.has(n.id) ? 'st-' + S.nodes.get(n.id) : ''),
      edgeClass: e => (S.edges.has(e.id) ? 'st-' + S.edges.get(e.id) : ''),
      badge: n => S.labels.get(n.id) || '',
      hint: () => (S.trace ? '' : 'Choose an algorithm and press Run to record its steps.'),
      legend: () => {
        if (!S.trace) return '';
        const c = NR.css.bind(NR);
        const base = [[c('--warn'), 'in queue / heap / stack'], [c('--accent'), 'current'], [c('--accent-soft'), 'done'], [c('--ok'), 'result', true]];
        if (S.name === 'tarjan') return NR.legendItems([[c('--accent'), 'current'], [c('--danger'), 'critical router'], [c('--danger'), 'critical link (bridge)', true], [c('--accent'), 'DFS tree link', true]]);
        if (S.name.startsWith('kruskal') || S.name === 'prim') return NR.legendItems([[c('--ok'), 'in the MST', true], [c('--danger'), 'rejected (cycle)', true], [c('--accent-soft'), 'joined']]);
        return NR.legendItems(base.concat([[c('--accent'), 'tree link', true]]));
      },
    },

    init() {
      $('#algoSelect').addEventListener('change', updateForm);
      $('#btnAlgoRun').addEventListener('click', NR.guard(run));
      $('#pFirst').addEventListener('click', () => go(0));
      $('#pPrev').addEventListener('click', () => go(S.idx - 1));
      $('#pNext').addEventListener('click', () => go(S.idx + 1));
      $('#pLast').addEventListener('click', () => go(S.trace.steps.length - 1));
      $('#pPlay').addEventListener('click', togglePlay);
      $('#pScrub').addEventListener('input', e => go(+e.target.value));
      document.addEventListener('keydown', e => {
        if (NR.tab !== 'algos' || !S.trace || e.target.matches('input:not([type=range]), select')) return;
        if (e.key === 'ArrowRight') { go(S.idx + 1); e.preventDefault(); }
        else if (e.key === 'ArrowLeft') { go(S.idx - 1); e.preventDefault(); }
        else if (e.key === ' ') { togglePlay(); e.preventDefault(); }
      });
      updateForm();
    },
    onLeave() { stop(); },
    onReset() { clear(); },
    onGraph() { if (S.trace && NR.tab !== 'algos') clear(); },
    onNodeClick(id, e) {
      // click = set start, shift+click = set target
      if (e.shiftKey) $('#algoDst').value = id; else $('#algoSrc').value = id;
      NR.toast((e.shiftKey ? 'Target: ' : 'Start: ') + NR.name(id) + (e.shiftKey ? '' : '  (shift+click sets the target)'));
    },
  };
  NR.tabs.algos = tab;

  function updateForm() {
    const a = $('#algoSelect').value;
    $('#algoAbout').innerHTML = ABOUT[a];
    $('#algoEnds').hidden = NEEDS[a] === 'none';
    $('#algoDstWrap').hidden = NEEDS[a] !== 'both';
  }

  function clear() {
    stop();
    Object.assign(S, { trace: null, result: null, idx: 0, ds: null });
    S.nodes.clear(); S.edges.clear(); S.labels.clear();
    $('#playerCard').hidden = $('#dsCard').hidden = $('#algoResultCard').hidden = true;
  }

  async function run() {
    stop();
    const a = $('#algoSelect').value;
    const params = { name: a === 'kruskalq' ? 'kruskal' : a, sort: a === 'kruskalq' ? 'quick' : 'merge', src: $('#algoSrc').value, dst: $('#algoDst').value };
    if (NEEDS[a] === 'both' && params.src === params.dst) throw new Error('Pick two different routers.');
    const res = await NR.api('algo', params);
    S.name = a;
    S.trace = res.trace;
    S.result = res;
    $('#playerCard').hidden = false;
    $('#pScrub').max = S.trace.steps.length - 1;
    showResult(res);
    go(0);
    if (S.trace.truncated) NR.toast('Long run: only the first ' + S.trace.steps.length + ' steps were recorded.');
    play();
  }

  function showResult(res) {
    const box = $('#algoResult');
    let html = '';
    if (res.path) {
      const p = res.path;
      html = p.found
        ? `<div class="route-item"><div class="route-text">${esc(p.text)}</div><div class="route-meta">cost ${fmt(p.cost)} · ${p.hops} hops · ${p.algorithm === 'Bellman-Ford' ? p.visited + ' round(s)' : p.visited + ' routers settled'}</div></div>`
        : '<div class="route-item" style="border-left-color:var(--danger)">Target not reachable.</div>';
    } else if (res.order) html = `<p><b>Visit order:</b> ${res.order.map(esc).join(' → ')}</p>`;
    else if (res.articulationPoints !== undefined) html = `<p>${res.articulationPoints} critical router(s), ${res.bridges} critical link(s). See the Analysis tab for their impact.</p>`;
    else if (res.cost !== undefined) html = `<p>Minimum spanning tree: <b>${res.links}</b> links, total cost <b>${fmt(res.cost)}</b>.</p>`;
    html += `<p class="muted small">${S.trace.steps.length} recorded steps.</p>`;
    box.innerHTML = html;
    $('#algoResultCard').hidden = false;
  }

  /** Rebuilds the state at step k by applying steps 0..k in order. */
  function go(k) {
    if (!S.trace) return;
    const steps = S.trace.steps;
    k = Math.max(0, Math.min(steps.length - 1, k));
    S.idx = k;
    S.nodes.clear(); S.edges.clear(); S.labels.clear(); S.ds = null;
    for (let i = 0; i <= k; i++) {
      const st = steps[i];
      if (st.n) for (const [id, s] of st.n) S.nodes.set(id, s);
      if (st.e) for (const [id, s] of st.e) S.edges.set(id, s);
      if (st.l) for (const [id, s] of st.l) S.labels.set(id, s);
      if (st.ds) S.ds = st.ds;
    }
    // "current" only lasts for its own step; afterwards the router counts as visited
    for (const [id, s] of S.nodes) if (s === 'current' && !(steps[k].n || []).some(([nid, ns]) => nid === id && ns === 'current')) S.nodes.set(id, 'visited');
    $('#pScrub').value = k;
    $('#pCount').textContent = `step ${k + 1} / ${steps.length}`;
    $('#pMsg').textContent = steps[k].msg;
    drawDS();
    NR.render();
    if (k === steps.length - 1) stop();
  }

  function togglePlay() { if (S.timer) stop(); else { if (S.idx >= S.trace.steps.length - 1) go(0); play(); } }
  function play() {
    stop();
    const tick = () => {
      if (!S.trace || S.idx >= S.trace.steps.length - 1) { stop(); return; }
      go(S.idx + 1);
      S.timer = setTimeout(tick, 1600 / +$('#pSpeed').value);
    };
    S.timer = setTimeout(tick, 1600 / +$('#pSpeed').value);
    $('#pPlay').innerHTML = '&#10074;&#10074;';
  }
  function stop() { clearTimeout(S.timer); S.timer = null; $('#pPlay').innerHTML = '&#9654;'; }

  /* -------------------------------------------------- data structure panel */
  function drawDS() {
    const ds = S.ds;
    $('#dsCard').hidden = !ds;
    if (!ds) return;
    $('#dsTitle').textContent = ds.title;
    $('#dsView').innerHTML = NR.drawDS(ds.kind, ds.items);
  }

  /** Shared drawing of simple structures (also used by the simulator panel). */
  NR.drawDS = function (kind, items) {
    if (!items.length) return '<div class="ds-empty">empty</div>';
    switch (kind) {
      case 'queue':
        return `<div class="ds-row"><span class="ds-label">front →</span>${items.map((x, i) => `<span class="ds-cell ${i === 0 ? 'first' : ''}">${esc(x)}</span>`).join('')}<span class="ds-label">← back</span></div>`;
      case 'stack':
        return `<div class="ds-col">${items.map((x, i) => `<span class="ds-cell ${i === 0 ? 'first' : ''}">${esc(x)}${i === 0 ? '  ← top' : ''}</span>`).join('')}</div>`;
      case 'heap':
        return NR.heapSVG(items) + `<div class="ds-label" style="margin-top:6px">array view (index 0 = root = minimum)</div><div class="ds-row">${items.map((x, i) => `<span class="ds-cell ${i === 0 ? 'first' : ''}" title="index ${i}">${esc(x)}</span>`).join('')}</div>`;
      case 'sets':
        return `<div class="ds-row">${items.map(x => `<span class="ds-cell">${esc(x)}</span>`).join('')}</div>`;
      default:
        return `<div class="ds-col">${items.map(x => `<span class="ds-cell">${esc(x)}</span>`).join('')}</div>`;
    }
  };

  /** Draws an array-backed binary heap as a tree: children of i are 2i+1 and 2i+2. */
  NR.heapSVG = function (items, maxShow = 31) {
    const n = Math.min(items.length, maxShow);
    const depth = Math.floor(Math.log2(n)) + 1;
    const W = 340, rowH = 46, boxH = 22;
    const pos = [];
    for (let i = 0; i < n; i++) {
      const level = Math.floor(Math.log2(i + 1));
      const first = 2 ** level - 1, count = 2 ** level;
      pos.push({ x: (W / count) * (i - first + 0.5), y: 16 + level * rowH });
    }
    const label = s => { const t = String(s); return t.length > 14 ? t.slice(0, 13) + '…' : t; };
    const bw = Math.max(34, Math.min(110, W / 2 ** (depth - 1) - 6));
    let lines = '', boxes = '';
    for (let i = 1; i < n; i++) { const p = pos[(i - 1) >> 1]; lines += `<line x1="${p.x}" y1="${p.y}" x2="${pos[i].x}" y2="${pos[i].y}"/>`; }
    for (let i = 0; i < n; i++) {
      const w = depth > 3 ? Math.min(bw, 70) : bw + 30;
      boxes += `<rect class="${i === 0 ? 'root' : ''}" x="${pos[i].x - w / 2}" y="${pos[i].y - boxH / 2}" width="${w}" height="${boxH}" rx="5"/>` +
        `<text x="${pos[i].x}" y="${pos[i].y}">${esc(label(items[i]))}</text><text class="idx" x="${pos[i].x}" y="${pos[i].y + boxH / 2 + 7}">${i}</text>`;
    }
    const h = 16 + (depth - 1) * rowH + 26;
    const more = items.length > n ? `<div class="ds-label">+ ${items.length - n} more not drawn</div>` : '';
    return `<svg class="heap-svg" viewBox="0 0 ${W} ${h}">${lines}${boxes}</svg>${more}`;
  };
})();
