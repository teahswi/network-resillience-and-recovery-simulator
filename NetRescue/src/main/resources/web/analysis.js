/* Analysis tab: critical routers/links (Tarjan), impact ranking, MST, Floyd-Warshall diameter and center. */
'use strict';

(function () {
  const S = { data: null, focus: null, version: -1 };
  const ov = () => Object.fromEntries($$('#overlayToggles input').map(i => [i.dataset.ov, i.checked]));

  const tab = {
    view: {
      nodeClass: n => {
        if (!S.data) return '';
        const o = ov();
        if (S.focus && S.focus.kind === 'node' && S.focus.id === n.id) return 'selected';
        if (o.critical && S.data.articulationPoints.some(a => a.id === n.id)) return 'st-ap';
        if (o.diameter && S.data.floyd.centerId === n.id) return 'st-center';
        return '';
      },
      edgeClass: e => {
        if (!S.data) return '';
        const o = ov();
        if (S.focus && S.focus.kind === 'edge' && S.focus.id === e.id) return 'selected';
        if (o.critical && S.data.bridges.some(b => b.id === e.id)) return 'st-bridge';
        if (o.mst && S.data.mst.edges.includes(e.id)) return 'st-mst';
        return '';
      },
      overlay: () => {
        if (!S.data) return '';
        const o = ov();
        let out = '';
        if (o.diameter && S.data.floyd.diameterPath) out += NR.pathLine(S.data.floyd.diameterPath, NR.css('--violet'), 6, 'route-line', '10 6');
        if (o.components && S.data.components > 1) {
          const colors = NR.ROUTE_COLORS().concat(['#0d9488', '#db2777', '#65a30d']);
          for (const n of NR.graph.nodes) {
            const c = S.data.componentOf[n.id];
            if (c >= 0) out += `<circle class="comp-ring" cx="${n.x}" cy="${n.y}" r="22" style="stroke:${colors[c % colors.length]}"/>`;
          }
        }
        return out;
      },
      legend: () => {
        if (!S.data) return '';
        const o = ov(), c = n => NR.css(n), items = [];
        if (o.critical) items.push([c('--danger'), 'critical router'], [c('--danger'), 'critical link', true]);
        if (o.mst) items.push([c('--ok'), 'minimum spanning tree', true]);
        if (o.diameter) items.push([c('--violet'), 'diameter route', true], [c('--violet'), 'center']);
        if (o.components && S.data.components > 1) items.push([c('--accent'), 'ring colour = piece']);
        return NR.legendItems(items);
      },
      hint: () => (S.data ? '' : 'Press "Analyse network".'),
    },

    init() {
      $('#btnAnalyse').addEventListener('click', NR.guard(analyse));
      for (const i of $$('#overlayToggles input')) i.addEventListener('change', () => NR.render());
    },
    onEnter() { if (S.version !== NR.graph.version) NR.guard(analyse)(); },
    onGraph(g) { if (NR.tab === 'analysis' && S.version !== g.version) NR.guard(analyse)(); },
    onReset() { S.data = null; S.focus = null; S.version = -1; $('#analysisBody').innerHTML = ''; },
    onNodeClick(id) { S.focus = { kind: 'node', id }; NR.render(); },
    onEdgeClick(id) { S.focus = { kind: 'edge', id }; NR.render(); },
    onBackgroundClick() { S.focus = null; NR.render(); },
  };
  NR.tabs.analysis = tab;

  async function analyse() {
    S.data = await NR.api('analysis');
    S.version = NR.graph.version;
    S.focus = null;
    draw();
    NR.render();
  }

  const scoreBar = v => `<div class="score ${v < 0.7 ? 'low' : v < 0.9 ? 'mid' : ''}"><div style="width:${Math.round(v * 100)}%"></div></div>`;

  function draw() {
    const d = S.data, f = d.floyd, m = d.mst;
    const maxCut = Math.max(1, ...d.articulationPoints.map(a => a.cutOff), ...d.bridges.map(b => b.cutOff));
    const item = (x, kind) => `<li data-kind="${kind}" data-id="${x.id}"><span>${esc(x.name)}</span><span class="bar"><div style="width:${(x.cutOff / maxCut) * 100}%"></div></span><span class="muted small">cuts off ${x.cutOff}</span></li>`;
    $('#analysisBody').innerHTML = `
      <div class="card">
        <h3>Resilience</h3>
        <div class="stat-grid">
          <div class="stat"><div class="v">${d.routers}</div><div class="k">routers up</div></div>
          <div class="stat"><div class="v">${d.links}</div><div class="k">links up</div></div>
          <div class="stat ${d.components > 1 ? 'bad' : 'good'}"><div class="v">${d.components}</div><div class="k">piece${d.components === 1 ? '' : 's'} (union-find)</div></div>
          <div class="stat"><div class="v">${pct(d.routerScore)}</div><div class="k">router score</div>${scoreBar(d.routerScore)}</div>
          <div class="stat"><div class="v">${pct(d.linkScore)}</div><div class="k">link score</div>${scoreBar(d.linkScore)}</div>
          <div class="stat ${d.articulationPoints.length + d.bridges.length ? 'bad' : 'good'}"><div class="v">${d.articulationPoints.length + d.bridges.length}</div><div class="k">single points of failure</div></div>
        </div>
        <p class="muted small">Score = share of routers (or links) whose failure alone would <i>not</i> split the network. Found with Tarjan's DFS in O(V + E).</p>
      </div>
      <div class="card">
        <h3>Critical routers <span class="tag">articulation points</span></h3>
        <ul class="list">${d.articulationPoints.map(a => item(a, 'node')).join('')}</ul>
        <h3 style="margin-top:14px">Critical links <span class="tag">bridges</span></h3>
        <ul class="list">${d.bridges.map(b => item(b, 'edge')).join('')}</ul>
        <p class="muted small">Impact = routers cut off from the main piece if it fails (re-checked with union-find), sorted with quicksort.</p>
      </div>
      <div class="card">
        <h3>Cheapest backbone <span class="tag">MST</span></h3>
        <dl class="kv">
          <dt>Kruskal cost</dt><dd>${fmt(m.cost)}</dd>
          <dt>Prim cost</dt><dd>${fmt(m.primCost)} ${Math.abs(m.cost - m.primCost) < 1e-6 ? '(same ✓)' : ''}</dd>
          <dt>All links cost</dt><dd>${fmt(m.networkCost)}</dd>
          <dt>MST links</dt><dd>${m.links} of ${d.links}${m.trees > 1 ? ` (forest of ${m.trees} trees)` : ''}</dd>
          <dt>Saving</dt><dd>${pct(m.saving)} of link cost</dd>
        </dl>
        <p class="muted small">An MST has no spare paths: every one of its links is a bridge. The links outside it are exactly the redundancy that lets traffic reroute.</p>
      </div>
      <div class="card">
        <h3>All-pairs distances <span class="tag">Floyd-Warshall</span></h3>
        <dl class="kv">
          <dt>Diameter</dt><dd>${f.diameterFrom ? `${fmt(f.diameter)} (${esc(f.diameterFrom)} → ${esc(f.diameterTo)})` : '-'}</dd>
          <dt>Center</dt><dd>${f.center ? `${esc(f.center)} (farthest router ${fmt(f.centerEccentricity)} away)` : '-'}</dd>
          <dt>Connected</dt><dd>${f.connected ? 'yes' : 'no: some distances are ∞'}</dd>
        </dl>
        <div class="tablewrap" style="margin-top:8px">${matrix(f)}</div>
        <p class="muted small">d[i][j] = min(d[i][j], d[i][k] + d[k][j]) for every k: O(V³). ${f.matrixNames.length < d.routers ? `Showing the first ${f.matrixNames.length} routers.` : ''}</p>
      </div>`;
    for (const li of $$('#analysisBody .list li')) li.addEventListener('click', () => { S.focus = { kind: li.dataset.kind, id: +li.dataset.id }; NR.render(); });
  }

  function matrix(f) {
    if (!f.matrixNames.length) return '';
    const head = '<tr><th></th>' + f.matrixNames.map(n => `<th class="num" title="${esc(n)}">${esc(n.slice(0, 5))}</th>`).join('') + '</tr>';
    const rows = f.matrix.map((r, i) => `<tr><th title="${esc(f.matrixNames[i])}">${esc(f.matrixNames[i].slice(0, 9))}</th>${r.map(v => `<td>${v === null ? '∞' : fmt(v, 1)}</td>`).join('')}</tr>`).join('');
    return `<table class="matrix">${head}${rows}</table>`;
  }
})();
