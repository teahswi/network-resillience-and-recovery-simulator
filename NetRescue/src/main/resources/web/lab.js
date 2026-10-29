/* DS Lab tab: poke each data structure directly and see its insides. */
'use strict';

(function () {
  const O = (n, note = '') => `<code>${n}</code>${note ? ` <span class="muted">${note}</span>` : ''}`;
  const STRUCTS = [
    { key: 'array', name: 'Dynamic array', sub: 'resizable array', use: 'Base of the stack, heap, edge lists and router table.',
      inputs: [['a', 'value'], ['b', 'index']],
      ops: [['add', 'add(value)'], ['insert', 'insertAt(index, value)'], ['removeAt', 'removeAt(index)'], ['get', 'get(index)']],
      cx: [['get / set', O('O(1)')], ['add at end', O('O(1)', 'amortised: doubling')], ['insert / remove in middle', O('O(n)', 'shifts elements')]] },
    { key: 'slist', name: 'Singly linked list', sub: 'head + tail pointers', use: 'Adjacency lists: each router\'s links.',
      inputs: [['a', 'value']],
      ops: [['addFirst', 'addFirst'], ['addLast', 'addLast'], ['removeFirst', 'removeFirst'], ['remove', 'remove(value)'], ['reverse', 'reverse']],
      cx: [['addFirst / addLast / removeFirst', O('O(1)')], ['search / remove(value)', O('O(n)')], ['reverse', O('O(n)', 'flip next pointers')]] },
    { key: 'dlist', name: 'Doubly linked list', sub: 'sentinel nodes', use: 'LRU recency order, simulator event log / timeline.',
      inputs: [['a', 'value']],
      ops: [['addFirst', 'addFirst'], ['addLast', 'addLast'], ['removeFirst', 'removeFirst'], ['removeLast', 'removeLast']],
      cx: [['add / remove at either end', O('O(1)')], ['unlink a known node', O('O(1)', 'prev + next pointers')], ['get(i)', O('O(n)')]] },
    { key: 'stack', name: 'Stack', sub: 'LIFO', use: 'Transfer rollback (undo log), editor undo/redo, iterative DFS, GML bracket matching.',
      inputs: [['a', 'value']],
      ops: [['push', 'push'], ['pop', 'pop'], ['peek', 'peek']],
      cx: [['push / pop / peek', O('O(1)', 'amortised')]] },
    { key: 'queue', name: 'Queue', sub: 'circular buffer', use: 'BFS frontier, transfer admission queue.',
      inputs: [['a', 'value']],
      ops: [['enqueue', 'enqueue'], ['dequeue', 'dequeue'], ['peek', 'peek']],
      cx: [['enqueue', O('O(1)', 'amortised')], ['dequeue / peek', O('O(1)', 'head index moves, nothing shifts')]] },
    { key: 'deque', name: 'Deque', sub: 'double-ended queue', use: '0-1 BFS (safest route), sliding window of recent setup times.',
      inputs: [['a', 'value']],
      ops: [['addFirst', 'addFirst'], ['addLast', 'addLast'], ['pollFirst', 'pollFirst'], ['pollLast', 'pollLast']],
      cx: [['add / poll at either end', O('O(1)', 'amortised')]] },
    { key: 'heap', name: 'Binary min-heap', sub: 'priority queue', use: 'Simulator event clock, Prim, Dijkstra (indexed version).',
      inputs: [['a', 'number']],
      ops: [['insert', 'insert'], ['extract', 'extractMin'], ['random', 'heapify 10 random']],
      cx: [['insert', O('O(log n)', 'sift up')], ['extractMin', O('O(log n)', 'sift down')], ['peek min', O('O(1)')], ['heapify n items', O('O(n)', 'bottom-up')]] },
    { key: 'map', name: 'Hash map', sub: 'separate chaining', use: 'Router name → id, transfer id → transfer, LRU index.',
      inputs: [['a', 'key'], ['b', 'value']],
      ops: [['put', 'put(key, value)'], ['get', 'get(key)'], ['remove', 'remove(key)'], ['fill', 'insert 10 cities']],
      cx: [['put / get / remove', O('O(1)', 'expected')], ['worst case (all collide)', O('O(n)')], ['resize (load > 0.75)', O('O(n)', 'rare, so O(1) amortised')]] },
    { key: 'trie', name: 'Trie', sub: 'prefix tree', use: 'Router-name autocomplete in the search box.',
      inputs: [['a', 'word / prefix']],
      ops: [['insert', 'insert'], ['remove', 'remove'], ['search', 'words with prefix']],
      cx: [['insert / contains', O('O(L)', 'L = word length')], ['prefix search', O('O(L + k)', 'k = results')]] },
    { key: 'uf', name: 'Union-find', sub: 'disjoint sets 0-9', use: 'Connectivity after failures, Kruskal MST, fail-fast reachability.',
      inputs: [['a', 'x (0-9)'], ['b', 'y (0-9)']],
      ops: [['union', 'union(x, y)'], ['find', 'find(x)'], ['connected', 'connected(x, y)']],
      cx: [['union / find', O('O(α(n))', 'inverse Ackermann, ≤ 4 in practice')]] },
    { key: 'lru', name: 'LRU cache', sub: 'hash map + doubly linked list', use: 'Route cache in the simulator (capacity 4 here).',
      inputs: [['a', 'key'], ['b', 'value']],
      ops: [['put', 'put(key, value)'], ['get', 'get(key)']],
      cx: [['get / put', O('O(1)')], ['evict least recent', O('O(1)', 'drop the list tail')]] },
  ];

  const S = { cur: 'stack', snap: null };

  const tab = {
    init() {
      $('#labNav').innerHTML = STRUCTS.map(s => `<button data-ds="${s.key}"><b>${esc(s.name)}</b><small>${esc(s.sub)}</small></button>`).join('');
      for (const b of $$('#labNav button')) b.addEventListener('click', NR.guard(() => select(b.dataset.ds)));
    },
    onEnter: NR.guard(async () => { await select(S.cur); }),
  };
  NR.tabs.lab = tab;

  async function select(key) {
    S.cur = key;
    const s = STRUCTS.find(x => x.key === key);
    for (const b of $$('#labNav button')) b.classList.toggle('active', b.dataset.ds === key);
    $('#labTitle').textContent = s.name;
    $('#labUse').textContent = 'Used for: ' + s.use;
    $('#labControls').innerHTML = s.inputs.map(([id, ph]) => `<input id="lab_${id}" placeholder="${esc(ph)}" aria-label="${esc(ph)}">`).join('') +
      s.ops.map(([op, label]) => `<button class="ghost" data-op="${op}">${esc(label)}</button>`).join('') +
      '<button class="ghost" data-op="clear">clear</button>';
    for (const b of $$('#labControls button')) b.addEventListener('click', NR.guard(() => op(b.dataset.op)));
    for (const i of $$('#labControls input')) i.addEventListener('keydown', e => { if (e.key === 'Enter') NR.guard(() => op(s.ops[0][0]))(); });
    $('#labComplexity').innerHTML = '<table>' + s.cx.map(([k, v]) => `<tr><td>${esc(k)}</td><td>${v}</td></tr>`).join('') + '</table>';
    const r = await NR.api('lab', { ds: key });
    show(r);
    $('#labMsg').textContent = 'Ready.';
    $('#labMsg').className = 'labmsg';
  }

  async function op(name) {
    const a = $('#lab_a') ? $('#lab_a').value : '', b = $('#lab_b') ? $('#lab_b').value : '';
    const r = await NR.api('lab', { ds: S.cur, op: name, a, b }, 'POST');
    show(r);
    $('#labMsg').textContent = r.msg;
    $('#labMsg').className = 'labmsg' + (r.ok ? '' : ' err');
    if (r.ok && ['add', 'push', 'enqueue', 'addFirst', 'addLast', 'insert', 'put'].includes(name) && $('#lab_a')) {
      // auto-increment numbers / letters to make repeated clicks easy
      const v = $('#lab_a').value;
      if (/^\d+$/.test(v) && S.cur !== 'uf') $('#lab_a').value = String(+v + 1 + (S.cur === 'heap' ? Math.floor(Math.random() * 20) : 0));
      else if (/^[A-Y]$/.test(v)) $('#lab_a').value = String.fromCharCode(v.charCodeAt(0) + 1);
    }
    $('#lab_a') && $('#lab_a').focus();
  }

  function show(r) { S.snap = r.snap; $('#labView').innerHTML = draw(r.snap); }

  /* -------------------------------------------------- drawings */
  const box = (x, y, w, h, text, cls = '') => `<rect class="box ${cls}" x="${x}" y="${y}" width="${w}" height="${h}" rx="6"/><text x="${x + w / 2}" y="${y + h / 2}">${esc(text)}</text>`;
  const arrow = (x1, y1, x2, y2) => `<line class="arrow" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" marker-end="url(#ah)"/>`;
  const svg = (w, h, body) => `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}"><defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="var(--muted)"/></marker></defs>${body}</svg>`;
  const short = s => { s = String(s); return s.length > 9 ? s.slice(0, 8) + '…' : s; };

  function draw(s) {
    switch (s.ds) {
      case 'array': {
        const n = s.capacity, W = 62, out = [];
        for (let i = 0; i < n; i++) {
          const has = i < s.items.length;
          out.push(box(20 + i * W, 40, W - 6, 40, has ? short(s.items[i]) : '', has ? '' : 'empty') + `<text class="muted-t" x="${20 + i * W + (W - 6) / 2}" y="94">${i}</text>`);
        }
        return svg(Math.max(320, 40 + n * W), 120, `<text class="tag-t" x="20" y="22" text-anchor="start">size ${s.items.length} · capacity ${n} (doubles when full)</text>${out.join('')}`);
      }
      case 'slist': case 'dlist': {
        const dbl = s.ds === 'dlist', W = 110, items = s.items;
        let body = '', x = 20;
        if (dbl) { body += box(x, 40, 70, 36, 'head', 'empty'); x += 110; }
        else body += `<text class="ptr" x="${x + 40}" y="24">head</text>`;
        items.forEach((v, i) => {
          if (dbl || i > 0) body += arrow(x - 38, 52, x - 3, 52) + (dbl ? arrow(x - 3, 66, x - 38, 66) : '');
          body += box(x, 40, 72, 36, short(v), i === 0 ? 'hi' : '');
          if (!dbl && i === items.length - 1) body += `<text class="ptr" x="${x + 36}" y="96">tail</text>`;
          x += W;
        });
        if (dbl) body += (items.length ? arrow(x - 38, 52, x - 3, 52) + arrow(x - 3, 66, x - 38, 66) : arrow(x - 38, 52, x - 3, 52) + arrow(x - 3, 66, x - 38, 66)) + box(x, 40, 70, 36, 'tail', 'empty');
        else body += (items.length ? arrow(x - 38, 58, x - 3, 58) : '') + `<text class="muted-t" x="${x + 14}" y="58">null</text>`;
        return svg(Math.max(320, x + 100), 110, body);
      }
      case 'stack': {
        const it = s.items, H = 38;
        let body = '';
        it.forEach((v, i) => { body += box(60, 20 + i * H, 140, H - 6, short(v), i === 0 ? 'hi' : '') + (i === 0 ? `<text class="ptr" x="232" y="${20 + (H - 6) / 2}">← top</text>` : ''); });
        if (!it.length) body = '<text class="muted-t" x="130" y="40">empty stack</text>';
        body += `<path d="M52 12V${28 + Math.max(1, it.length) * H}H208V12" fill="none" stroke="var(--border-strong)" stroke-width="2"/>`;
        return svg(300, 40 + Math.max(1, it.length) * H, body);
      }
      case 'queue': {
        const n = s.capacity, W = 64, tail = (s.head + s.size) % n;
        let body = `<text class="tag-t" x="20" y="14" text-anchor="start">ring buffer: head = ${s.head}, size = ${s.size}, capacity = ${n}</text>` +
          `<text class="tag-t" x="20" y="32" text-anchor="start">next free slot = (head + size) mod capacity = ${tail}</text>`;
        for (let i = 0; i < n; i++) {
          const v = s.slots[i];
          body += box(20 + i * W, 66, W - 6, 40, v === null ? '' : short(v), v === null ? 'empty' : i === s.head ? 'hi' : '') + `<text class="muted-t" x="${20 + i * W + (W - 6) / 2}" y="120">${i}</text>`;
        }
        if (s.size) body += `<text class="ptr" x="${20 + s.head * W + (W - 6) / 2}" y="56">head</text>`;
        body += `<text class="ptr" x="${20 + tail * W + (W - 6) / 2}" y="${s.size ? 140 : 56}" style="fill:var(--ok)">${s.size === n ? 'full' : 'next'}</text>`;
        return svg(Math.max(420, 40 + n * W), 152, body);
      }
      case 'deque': {
        const it = s.items, W = 80;
        let body = '<text class="ptr" x="20" y="60" text-anchor="start">front</text>';
        it.forEach((v, i) => { body += box(70 + i * W, 40, W - 8, 40, short(v), i === 0 || i === it.length - 1 ? 'hi' : ''); });
        body += `<text class="ptr" x="${80 + it.length * W}" y="60" text-anchor="start">back</text>`;
        return svg(Math.max(320, 140 + it.length * W), 110, body);
      }
      case 'heap':
        return s.items.length ? `<div style="max-width:620px">${NR.heapSVG(s.items.map(String))}</div><div class="ds-label" style="margin-top:8px">array (children of i at 2i+1 and 2i+2)</div><div class="ds-row" style="font-family:var(--mono)">${s.items.map((v, i) => `<span class="ds-cell ${i === 0 ? 'first' : ''}" title="index ${i}">${v}</span>`).join('')}</div>`
          : '<p class="muted">Empty heap. Insert numbers or heapify random ones.</p>';
      case 'map':
        return `<p class="small"><b>${s.size}</b> entries in <b>${s.capacity}</b> buckets · load factor <b>${fmt(s.load, 2)}</b> (resizes above 0.75) · resized ${s.resizes} time(s)</p>` +
          s.buckets.map((b, i) => `<div class="bucket"><span class="idx">${i}</span><span class="slot"></span>${b.map(e => `<span class="arr">→</span><span class="ent">${esc(e)}</span>`).join('')}</div>`).join('');
      case 'trie': return drawTree(s.edges.map(([p, c, ch, end]) => ({ p, c, label: ch, end })), `${s.words} words · ${s.nodes} nodes${s.found ? ' · last search: ' + s.found : ''}`);
      case 'uf': {
        const edges = s.parent.map((p, i) => ({ p: p === i ? -1 : p, c: i, label: String(i), end: p === i }));
        return `<p class="small"><b>${s.components}</b> set(s). Each element points to its parent; roots (green) point to themselves and name their set.</p>` +
          `<div class="ds-row" style="margin-bottom:10px;font-family:var(--mono)"><span class="ds-label">parent[]</span>${s.parent.map((p, i) => `<span class="ds-cell" title="parent[${i}] = ${p}">${i}→${p}</span>`).join('')}</div>` + drawForest(edges);
      }
      case 'lru':
        return `<p class="small">capacity ${s.capacity} · hits ${s.hits} · misses ${s.misses} · evictions ${s.evictions}</p>` +
          svg(Math.max(360, 60 + s.items.length * 110), 110,
            '<text class="ptr" x="30" y="24" text-anchor="start">most recent</text>' +
            s.items.map((k, i) => (i ? arrow(20 + i * 110 - 34, 58, 20 + i * 110 - 4, 58) : '') + box(20 + i * 110, 40, 76, 36, short(k), i === 0 ? 'hi' : '')).join('') +
            (s.items.length ? `<text class="ptr" x="${20 + (s.items.length - 1) * 110 + 38}" y="96" style="fill:var(--danger)">evicted next</text>` : '<text class="muted-t" x="60" y="58">empty</text>')) +
          '<p class="muted small">The hash map finds a key\'s list node in O(1); the list keeps recency, so the tail is always the least recently used.</p>';
      default: return '';
    }
  }

  /** Simple tidy tree: leaves get consecutive x slots, parents sit above the middle of their children. */
  function drawTree(edges, caption) {
    const kids = new Map(), info = new Map();
    for (const e of edges) {
      info.set(e.c, e);
      if (!kids.has(e.p)) kids.set(e.p, []);
      kids.get(e.p).push(e.c);
    }
    const pos = new Map();
    let leaf = 0, maxDepth = 0;
    const place = (id, d) => {
      maxDepth = Math.max(maxDepth, d);
      const ch = kids.get(id) || [];
      if (!ch.length) { pos.set(id, { x: leaf++, d }); return; }
      ch.forEach(c => place(c, d + 1));
      pos.set(id, { x: (pos.get(ch[0]).x + pos.get(ch[ch.length - 1]).x) / 2, d });
    };
    for (const r of kids.get(-1) || []) place(r, 0);
    const X = x => 30 + x * 40, Y = d => 30 + d * 52;
    let body = '';
    for (const e of edges) if (e.p >= 0 && pos.has(e.p)) {
      const a = pos.get(e.p), b = pos.get(e.c);
      body += `<line class="arrow" x1="${X(a.x)}" y1="${Y(a.d) + 13}" x2="${X(b.x)}" y2="${Y(b.d) - 13}"/>`;
    }
    for (const [id, p] of pos) {
      const e = info.get(id);
      body += `<circle cx="${X(p.x)}" cy="${Y(p.d)}" r="13" class="box ${e.end ? 'word' : ''}"/><text x="${X(p.x)}" y="${Y(p.d)}">${esc(e.label || '·')}</text>`;
    }
    return `<p class="small">${esc(caption)} · <span style="color:var(--ok)">green</span> = end of a word</p>` + svg(Math.max(300, 60 + leaf * 40), 60 + maxDepth * 52, body);
  }

  function drawForest(edges) {
    // reuse the tree layout: roots have parent -1
    const html = drawTree(edges, '');
    return html.replace(/^<p class="small">.*?<\/p>/, '');
  }
})();
