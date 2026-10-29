/* Benchmarks tab: runs the Java benchmark suites and plots measured times. */
'use strict';

(function () {
  const S = { suites: [], results: {} };
  const isExtra = name => /comparisons|longest chain|probes per/i.test(name);

  const tab = {
    init() {
      $('#btnBenchAll').addEventListener('click', NR.guard(async () => { for (const s of S.suites) await run(s.key); }));
      $('#benchLog').addEventListener('change', () => { for (const k of Object.keys(S.results)) draw(k); });
    },
    onEnter: NR.guard(async () => {
      if (S.suites.length) return;
      S.suites = await NR.api('bench/list');
      $('#benchGrid').innerHTML = S.suites.map(s => `
        <div class="card bench-card" id="bench-${s.key}">
          <div class="row" style="justify-content:space-between;gap:10px;align-items:flex-start">
            <h3>${esc(s.title)}</h3><button class="ghost small" data-run="${s.key}">Run</button>
          </div>
          <p class="note">${esc(s.note)}</p>
          <div class="bench-chart"><p class="muted small">Not run yet.</p></div>
          <div class="tablewrap bench-table"></div>
        </div>`).join('');
      for (const b of $$('[data-run]')) b.addEventListener('click', NR.guard(() => run(b.dataset.run)));
    }),
  };
  NR.tabs.bench = tab;

  async function run(key) {
    const card = $('#bench-' + key);
    const btn = $('[data-run]', card);
    btn.disabled = true;
    $('.bench-chart', card).innerHTML = '<p class="muted small"><span class="spinner"></span> measuring… (a few seconds)</p>';
    try {
      S.results[key] = await NR.api('bench', { suite: key });
      draw(key);
    } catch (e) {
      $('.bench-chart', card).innerHTML = `<p class="small" style="color:var(--danger)">${esc(e.message)}</p>`;
      throw e;
    } finally { btn.disabled = false; }
  }

  function draw(key) {
    const r = S.results[key], card = $('#bench-' + key);
    const timeCols = r.series.map((n, i) => i).filter(i => !isExtra(r.series[i]));
    const colors = [NR.css('--accent'), NR.css('--danger'), NR.css('--ok'), NR.css('--violet')];
    const logY = $('#benchLog').checked;
    const W = 460, H = 220, P = { l: 52, r: 14, t: 12, b: 34 };
    const xs = r.rows.map(row => row[0]);
    const vals = r.rows.flatMap(row => timeCols.map(i => row[i + 1])).filter(v => v > 0);
    const xMin = Math.min(...xs), xMax = Math.max(...xs);
    let yMin = logY ? Math.min(...vals) : 0, yMax = Math.max(...vals);
    if (logY) { yMin = 10 ** Math.floor(Math.log10(yMin)); yMax = 10 ** Math.ceil(Math.log10(yMax)); }
    const X = x => P.l + (Math.log(x / xMin) / Math.log(xMax / xMin || 2)) * (W - P.l - P.r);
    const Y = v => {
      const f = logY ? (Math.log10(Math.max(v, yMin)) - Math.log10(yMin)) / (Math.log10(yMax) - Math.log10(yMin) || 1) : v / (yMax || 1);
      return H - P.b - f * (H - P.t - P.b);
    };
    let grid = '';
    const ticks = logY
      ? Array.from({ length: Math.round(Math.log10(yMax / yMin)) + 1 }, (_, i) => yMin * 10 ** i)
      : [0, 0.25, 0.5, 0.75, 1].map(f => f * yMax);
    for (const t of ticks) grid += `<line class="gl" x1="${P.l}" x2="${W - P.r}" y1="${Y(t)}" y2="${Y(t)}"/><text x="${P.l - 6}" y="${Y(t) + 3}" text-anchor="end">${fmt(t, t < 1 ? 3 : 1)}</text>`;
    for (const x of xs) grid += `<text x="${X(x)}" y="${H - P.b + 14}" text-anchor="middle">${x >= 1000 ? x / 1000 + 'k' : x}</text>`;
    let lines = '';
    timeCols.forEach((ci, k) => {
      const pts = r.rows.map(row => `${X(row[0])},${Y(row[ci + 1])}`);
      lines += `<polyline fill="none" stroke="${colors[k]}" stroke-width="2.4" points="${pts.join(' ')}"/>`;
      lines += r.rows.map(row => `<circle cx="${X(row[0])}" cy="${Y(row[ci + 1])}" r="3.5" fill="${colors[k]}"><title>${esc(r.series[ci])}: ${fmt(row[ci + 1], 3)} ms at ${row[0]}</title></circle>`).join('');
    });
    $('.bench-chart', card).innerHTML =
      `<div class="bench-legend">${timeCols.map((ci, k) => `<span><i style="background:${colors[k]}"></i>${esc(r.series[ci])}</span>`).join('')}</div>` +
      `<svg viewBox="0 0 ${W} ${H}"><line class="axis" x1="${P.l}" y1="${H - P.b}" x2="${W - P.r}" y2="${H - P.b}"/><line class="axis" x1="${P.l}" y1="${P.t}" x2="${P.l}" y2="${H - P.b}"/>${grid}${lines}
       <text x="${(W + P.l) / 2}" y="${H - 4}" text-anchor="middle">${esc(r.x)} (log scale)</text><text x="12" y="${(H - P.b) / 2}" transform="rotate(-90 12 ${(H - P.b) / 2})" text-anchor="middle">${esc(r.y)}</text></svg>`;
    // growth ratio between consecutive sizes tells the complexity: doubling n -> x2 linear, x4 quadratic, x8 cubic
    const ratioCol = timeCols[timeCols.length - 1];
    $('.bench-table', card).innerHTML = `<table><tr><th class="num">${esc(r.x.split(' ')[0])}</th>${r.series.map(s => `<th class="num">${esc(s)}</th>`).join('')}<th class="num" title="time(n) / time(previous n) for the last timed column">growth</th></tr>` +
      r.rows.map((row, i) => `<tr><td class="num">${row[0]}</td>${row.slice(1).map((v, ci) => `<td class="num">${fmt(v, isExtra(r.series[ci]) ? 2 : 3)}</td>`).join('')}<td class="num">${i ? '×' + fmt(row[ratioCol + 1] / r.rows[i - 1][ratioCol + 1], 1) : ''}</td></tr>`).join('') + '</table>';
  }
})();
