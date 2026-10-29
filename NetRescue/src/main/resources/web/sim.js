/* Simulation tab: transfers with stack-based rollback over a failing network, event heap, LRU route cache, timeline replay. */
'use strict';

(function () {
  const S = {
    st: null,          // latest state from the server
    timer: null,       // play loop
    replay: null,      // snapshot shown by the timeline scrubber (null = live)
    focus: null,       // selected transfer id
    detail: null,      // its details (with undo stack + history)
    busy: false,
  };

  // which state to draw: the replay snapshot, or live state
  const linkInfo = () => {
    const src = S.replay || S.st;
    const m = new Map();
    if (src) for (const [id, used, cap, up] of src.links) m.set(id, { used, cap, up });
    return m;
  };
  const nodesUp = () => (S.replay || S.st || {}).nodesUp || [];

  const tab = {
    view: {
      nodeDown: n => nodesUp()[n.id] === false,
      edgeDown: e => {
        const li = linkInfo().get(e.id);
        const nu = nodesUp();
        return (li ? !li.up : !e.up) || nu[e.u] === false || nu[e.v] === false;
      },
      edgeWidth: e => {
        const li = linkInfo().get(e.id);
        return li && li.used > 0 ? 2.5 + 7 * (li.used / li.cap) : null;
      },
      edgeColor: e => {
        const li = linkInfo().get(e.id);
        if (!li || !li.up || li.used === 0) return null;
        const r = li.used / li.cap;
        return r >= 0.999 ? NR.css('--danger') : r >= 0.6 ? NR.css('--warn') : NR.css('--accent');
      },
      edgeLabel: e => {
        const li = linkInfo().get(e.id);
        if (!li) return '';
        return li.used > 0 ? `${li.used}/${li.cap}` : ($('#showCosts').checked ? `0/${li.cap}` : '');
      },
      nodeClass: n => {
        const t = focusTransfer();
        if (t && n.id === t.srcId) return 'st-current';
        if (t && n.id === t.dstId) return 'st-path';
        return '';
      },
      nodeInner: n => { const t = focusTransfer(); return t && n.id === t.srcId ? 'S' : t && n.id === t.dstId ? 'D' : ''; },
      overlay: () => {
        let out = '';
        const t = focusTransfer();
        if (t && !S.replay && t.routeEdges.length) out += edgesLine(t.routeEdges, NR.css('--violet'), 3, '6 6');
        const res = S.replay ? S.replay.reservations : (S.st ? S.st.transfers.filter(x => x.state === 'SETUP' || x.state === 'ACTIVE').map(x => [x.id, ...routeHeld(x)]) : []);
        for (const r of res) {
          const held = r.slice(1);
          const isFocus = S.focus === r[0];
          out += edgesLine(held, NR.css('--violet'), isFocus ? 5 : 2.5, '', isFocus ? 0.95 : 0.85);
        }
        return out;
      },
      legend: () => NR.legendItems([[NR.css('--accent'), 'link load < 60%', true], [NR.css('--warn'), '≥ 60%', true], [NR.css('--danger'), 'full / failed', true], [NR.css('--violet'), 'reserved by a transfer', true]]),
      hint: () => S.replay ? `Replaying t=${fmt(S.replay.time)}: ${S.replay.text}` : 'Click a router or link to schedule its failure. Labels show used/capacity.',
    },

    init() {
      $('#sStep').addEventListener('click', NR.guard(() => step({ mode: 'events', n: 1 })));
      $('#sTick').addEventListener('click', NR.guard(() => step({ mode: 'until', dt: 1 })));
      $('#sFinish').addEventListener('click', NR.guard(() => { stopPlay(); return step({ mode: 'finish' }); }));
      $('#sPlay').addEventListener('click', () => (S.timer ? stopPlay() : play()));
      $('#btnRandom').addEventListener('click', NR.guard(async () => {
        const r = await NR.api('sim/random', { transfers: $('#rTransfers').value, failures: $('#rFailures').value, span: $('#rSpan').value, seed: $('#rSeed').value }, 'POST');
        $('#rSeed').value = +$('#rSeed').value + 1;
        NR.toast(r.msg + ' Press Play.');
        await refresh();
      }));
      $('#btnTransfer').addEventListener('click', NR.guard(async () => {
        const r = await NR.api('sim/transfer', { src: $('#tSrc').value, dst: $('#tDst').value, amount: $('#tAmount').value, priority: $('#tPrio').value }, 'POST');
        S.focus = r.id;
        NR.toast(r.msg);
        await refresh();
      }));
      $('#btnSimReset').addEventListener('click', NR.guard(reset));
      $('#btnExperiment').addEventListener('click', NR.guard(experiment));
      $('#tlScrub').addEventListener('input', NR.guard(e => showEntry(+e.target.value)));
      $('#tlLive').addEventListener('click', () => { S.replay = null; markTimeline(); NR.render(); });
      $('#simLog').addEventListener('click', NR.guard(e => { const li = e.target.closest('li'); if (li) return showEntry(+li.dataset.i); }));
      $('#simTransfers').addEventListener('click', NR.guard(e => { const tr = e.target.closest('tr[data-id]'); if (tr) return focusOn(+tr.dataset.id); }));
    },
    onEnter() { NR.guard(refresh)(); },
    onLeave() { stopPlay(); },
    onReset() { stopPlay(); S.st = null; S.replay = null; S.focus = null; S.detail = null; $('#experimentOut').innerHTML = ''; },
    onGraph() { if (NR.tab === 'sim') NR.guard(refresh)(); },

    onNodeClick: NR.guard(async id => scheduleFailure('node', id, NR.name(id))),
    onEdgeClick: NR.guard(async id => scheduleFailure('edge', id, NR.edgeName(id))),
    onBackgroundClick() { S.focus = null; S.detail = null; drawTransfers(); NR.render(); },
  };
  NR.tabs.sim = tab;

  function focusTransfer() { return S.st && S.focus ? S.st.transfers.find(t => t.id === S.focus) : null; }

  /** Links a transfer currently holds = its undo stack (names only), mapped back to edge ids via route order. */
  function routeHeld(t) {
    // the undo stack holds the first k links of the route (pushed in order)
    return t.routeEdges.slice(0, t.undoStack.length);
  }

  function edgesLine(edgeIds, color, width, dash, opacity = 0.9) {
    let out = '';
    for (const id of edgeIds) {
      const e = NR.edgeById.get(id);
      if (!e) continue;
      const a = NR.nodeById.get(e.u), b = NR.nodeById.get(e.v);
      out += `<line class="route-line" x1="${a.x}" y1="${a.y}" x2="${b.x}" y2="${b.y}" style="stroke:${color};stroke-width:${width};opacity:${opacity};${dash ? 'stroke-dasharray:' + dash : ''}"/>`;
    }
    return out;
  }

  async function refresh() { apply(await NR.api('sim/state', { log: 80 })); }

  async function step(params) {
    if (S.busy) return;
    S.busy = true;
    try {
      const r = await NR.api('sim/step', Object.assign({ log: 80 }, params), 'POST');
      S.replay = null;
      apply(r.state);
      if (S.focus) await loadDetail(S.focus);
      return r;
    } finally { S.busy = false; }
  }

  function play() {
    if (S.st && S.st.pendingEvents === 0) { NR.toast('Nothing scheduled. Add a random scenario or a transfer first.'); return; }
    $('#sPlay').textContent = 'Pause';
    const tick = async () => {
      try {
        const r = await step({ mode: 'until', dt: 0.5 });
        if (!S.st || S.st.pendingEvents === 0) { stopPlay(); if (r) NR.toast('Simulation finished.'); return; }
      } catch (e) { stopPlay(); NR.toast(e.message, true); return; }
      if (S.timer) S.timer = setTimeout(tick, 280);
    };
    S.timer = setTimeout(tick, 0);
  }
  function stopPlay() { clearTimeout(S.timer); S.timer = null; $('#sPlay').textContent = 'Play'; }

  async function reset() {
    stopPlay();
    S.replay = null; S.focus = null; S.detail = null;
    apply(await NR.api('sim/reset', {
      strategy: $('#cStrategy').value, priority: $('#cPriority').value, maxActive: $('#cMaxActive').value,
      maxRetries: $('#cRetries').value, hopDelay: $('#cHop').value, cache: $('#cCache').value, log: 80,
    }, 'POST'));
    NR.toast('Simulation reset with new settings.');
  }

  async function scheduleFailure(kind, id, label) {
    const r = await NR.api('sim/failure', { kind, id, delay: $('#fDelay').value, duration: $('#fDur').value }, 'POST');
    NR.toast(`${kind === 'node' ? 'Router' : 'Link'} ${label}: ${r.msg}`);
    await refresh();
  }

  async function focusOn(id) { S.focus = id; await loadDetail(id); NR.render(); }
  async function loadDetail(id) { S.detail = await NR.api('sim/transfer', { id }); drawTransfers(); }

  async function showEntry(i) {
    S.replay = await NR.api('sim/entry', { i });
    if (S.replay.focus > 0) S.focus = S.replay.focus;
    markTimeline();
    NR.render();
  }

  function markTimeline() {
    $('#timelineBox').classList.toggle('replaying', !!S.replay);
    $('#tlLive').hidden = !S.replay;
    $('#tlLabel').textContent = S.replay ? `#${S.replay.i} at t=${fmt(S.replay.time)}` : '(live)';
    if (S.replay) $('#tlScrub').value = S.replay.i;
    for (const li of $$('#simLog li')) li.classList.toggle('cur', !!S.replay && +li.dataset.i === S.replay.i);
  }

  /* -------------------------------------------------- drawing the panel */
  function apply(st) {
    S.st = st;
    $('#simClock').textContent = fmt(st.clock);
    $('#simPending').textContent = st.pendingEvents;
    $('#simQueued').textContent = st.queued;
    $('#simActive').textContent = `${st.slotsInUse}/${st.settings.maxActive}`;
    syncSettings(st.settings);
    drawMetrics(st);
    drawTransfers();
    $('#simEvents').innerHTML = st.events.map(e => `<li>t=${fmt(e.time)} ${esc(e.type)} <b>${esc(e.target)}</b></li>`).join('');
    $('#simQueue').innerHTML = st.queue.map(q => `<span>${esc(q)}</span>`).join('');
    $('#cacheStats').textContent = st.settings.useCache ? `${st.cache.size}/${st.cache.capacity} · hits ${st.cache.hits} · misses ${st.cache.misses} · invalidated ${st.cache.invalidations}` : '(off)';
    $('#simCache').innerHTML = st.cache.keys.map(k => `<span>${esc(k)}</span>`).join('');
    const log = $('#simLog');
    const atBottom = log.scrollTop + log.clientHeight >= log.scrollHeight - 30;
    log.innerHTML = st.log.map(l => `<li class="${l.kind}" data-i="${l.i}"><span class="t">${fmt(l.time)}</span><span>${esc(l.text)}</span></li>`).join('');
    if (atBottom) log.scrollTop = log.scrollHeight;
    const last = st.log.length ? st.log[st.log.length - 1].i : 0;
    $('#tlScrub').min = st.logFirstIndex;
    $('#tlScrub').max = last;
    if (!S.replay) $('#tlScrub').value = last;
    markTimeline();
    NR.render();
  }

  function syncSettings(c) {
    $('#cStrategy').value = c.strategy; $('#cPriority').value = String(c.priorityMode); $('#cMaxActive').value = c.maxActive;
    $('#cRetries').value = c.maxRetries; $('#cHop').value = c.hopDelay; $('#cCache').value = String(c.useCache);
  }

  function drawMetrics(st) {
    const m = st.metrics;
    const stat = (v, k, cls = '') => `<div class="stat ${cls}"><div class="v">${v}</div><div class="k">${k}</div></div>`;
    $('#simMetrics').innerHTML =
      stat(m.submitted, 'submitted') + stat(m.done, 'done', 'good') + stat(m.failed, 'failed', m.failed ? 'bad' : '') +
      stat(m.done + m.failed ? pct(m.successRate) : '-', 'success rate') + stat(m.rollbacks, 'rollbacks') + stat(m.reroutes, 'reroutes') +
      stat(fmt(m.avgSetup, 1), 'avg setup time') + stat(fmt(m.avgHops, 1), 'avg hops') + stat(st.settings.useCache ? pct(m.cacheHitRate) : 'off', 'cache hit rate');
    $('#simChart').innerHTML = chart(st.series);
  }

  function chart(series) {
    if (series.length < 2) return '';
    const W = 340, H = 130, P = { l: 28, r: 8, t: 8, b: 18 };
    const tMax = Math.max(...series.map(s => s[0])) || 1;
    const yMax = Math.max(1, ...series.map(s => Math.max(s[1], s[2], s[3], s[4])));
    const X = t => P.l + (t / tMax) * (W - P.l - P.r), Y = v => H - P.b - (v / yMax) * (H - P.t - P.b);
    const line = (i, color) => `<polyline fill="none" stroke="${color}" stroke-width="2" points="${series.map(s => `${X(s[0])},${Y(s[i])}`).join(' ')}"/>`;
    return `<svg viewBox="0 0 ${W} ${H}">
      <line class="axis" x1="${P.l}" y1="${H - P.b}" x2="${W - P.r}" y2="${H - P.b}"/><line class="axis" x1="${P.l}" y1="${P.t}" x2="${P.l}" y2="${H - P.b}"/>
      <text x="${P.l - 4}" y="${P.t + 8}" text-anchor="end">${yMax}</text><text x="${P.l - 4}" y="${H - P.b}" text-anchor="end">0</text>
      <text x="${W - P.r}" y="${H - 4}" text-anchor="end">t=${fmt(tMax, 1)}</text>
      ${line(1, NR.css('--ok'))}${line(2, NR.css('--danger'))}${line(3, NR.css('--accent'))}${line(4, NR.css('--violet'))}
    </svg><div class="bench-legend"><span><i style="background:${NR.css('--ok')}"></i>done</span><span><i style="background:${NR.css('--danger')}"></i>failed</span><span><i style="background:${NR.css('--accent')}"></i>active</span><span><i style="background:${NR.css('--violet')}"></i>rollbacks</span></div>`;
  }

  function drawTransfers() {
    if (!S.st) return;
    const rows = S.st.transfers.slice(-150).reverse();
    $('#simTransfers').innerHTML = rows.length ? `<table><tr><th>#</th><th>route</th><th>units</th><th>state</th><th class="num">retry</th><th class="num">rollb.</th></tr>` +
      rows.map(t => `<tr class="click ${t.id === S.focus ? 'sel' : ''}" data-id="${t.id}"><td>T${t.id}</td><td>${esc(t.src)} → ${esc(t.dst)}</td><td class="num">${t.amount}</td><td><span class="state ${t.state}">${t.state}</span></td><td class="num">${t.retries}</td><td class="num">${t.rollbacks}</td></tr>`).join('') + '</table>'
      : '<p class="muted small">No transfers yet. Add a random scenario, or send one by hand.</p>';
    const d = S.detail && S.detail.id === S.focus ? S.detail : null;
    $('#transferDetail').innerHTML = d ? `<div class="detail">
      <b>T${d.id}</b> ${esc(d.src)} → ${esc(d.dst)}, ${d.amount} units, priority ${d.priority} · <span class="state ${d.state}">${d.state}</span>
      ${d.route ? `<div class="muted small" style="margin-top:4px">route: ${esc(d.route)}${d.cached ? ' (from LRU cache)' : ''}</div>` : ''}
      ${d.reason ? `<div class="small" style="color:var(--danger)">${esc(d.reason)}</div>` : ''}
      <div class="tiny-h">Undo stack (reserved links)</div>
      <div class="undo-stack">${d.undoStack.length ? d.undoStack.map(s => `<span>${esc(s)}</span>`).join('') : '<span class="muted" style="border:0">empty</span>'}</div>
      <div class="tiny-h">History</div><ol class="hist">${d.history.map(h => `<li>${esc(h)}</li>`).join('')}</ol></div>` : '';
  }

  async function experiment() {
    const btn = $('#btnExperiment');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> running…';
    try {
      const r = await NR.api('experiment', { transfers: Math.max(20, +$('#rTransfers').value * 2), failures: Math.max(4, +$('#rFailures').value), span: $('#rSpan').value, seed: $('#rSeed').value });
      const best = Math.max(...r.rows.map(x => x.metrics.successRate));
      $('#experimentOut').innerHTML = `<p class="muted small">${r.transfers} transfers, ${r.failures} failures over ${r.span} time units, seed ${r.seed}.</p><table>
        <tr><th>strategy</th><th class="num">success</th><th class="num">rollb.</th><th class="num">setup</th><th class="num">hops</th><th class="num">cost</th><th class="num">routings</th></tr>
        ${r.rows.map(x => { const m = x.metrics; return `<tr><td>${esc(x.name)}</td><td class="num" style="${m.successRate === best ? 'color:var(--ok);font-weight:700' : ''}">${pct(m.successRate)}</td><td class="num">${m.rollbacks}</td><td class="num">${fmt(m.avgSetup, 1)}</td><td class="num">${fmt(m.avgHops, 1)}</td><td class="num">${fmt(m.avgCost, 1)}</td><td class="num">${m.routeComputations}</td></tr>`; }).join('')}
      </table><p class="muted small">"routings" = Dijkstra/BFS runs; the LRU cache lowers it. Setup = time from arrival to all links reserved.</p>`;
    } finally {
      btn.disabled = false;
      btn.textContent = 'Run experiment';
    }
  }
})();
