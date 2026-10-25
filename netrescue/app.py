"""Streamlit entry point for NetRescue."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import streamlit as st

from netrescue.core.bfs import bfs
from netrescue.core.benchmark import (
    compute_resilience,
    recovery_rate,
    run_benchmarks,
)
from netrescue.core.critical import tarjan_articulation_and_bridges, vulnerability_report
from netrescue.core.dijkstra import dijkstra
from netrescue.core.recovery import RecoveryEngine
from netrescue.ui.components import reset_state, sidebar_controls
from netrescue.ui.visualizer import build_pyvis_html

st.set_page_config(page_title="NetRescue | Network Resilience", page_icon="◈", layout="wide")
st.markdown("""
<style>
:root { color-scheme: dark; }
.stApp { background: radial-gradient(ellipse at 48% 0%, #142333 0%, #080d14 52%); color: #e2e8f0; }
[data-testid="stSidebar"] { background: #0a111b; border-right: 1px solid #263548; }
[data-testid="stHeader"] { background: rgba(8,13,20,.85); }
h1, h2, h3 { letter-spacing: -.035em; }
h1 { color: #eaf8ff; font-weight: 700; }
h2, h3 { color: #c9f4ff; }
[data-testid="stMetric"] { background: #0b1420; border: 1px solid #263548; border-radius: 10px; padding: 14px 16px; }
[data-testid="stMetricLabel"] { color: #91a4ba; }
[data-testid="stMetricValue"] { color: #d9f8ff; }
.stTabs [data-baseweb="tab-list"] { gap: 8px; border-bottom: 1px solid #29394b; }
.stTabs [data-baseweb="tab"] { background: #0d1723; border: 1px solid #263548; border-radius: 7px 7px 0 0; padding: 9px 16px; }
.stTabs [aria-selected="true"] { color: #a5f3fc !important; border-color: #398ba0 !important; }
div[data-testid="stAlert"], div[data-testid="stExpander"] { border-radius: 8px; }
.stButton > button { border: 1px solid #31546b; border-radius: 7px; background: #102232; color: #d9f8ff; }
.stButton > button:hover { border-color: #67e8f9; color: #fff; }
hr { border-color: #263548; }
small, .stCaption { color: #91a4ba !important; }
</style>
""", unsafe_allow_html=True)
st.markdown("""
<div style="display:flex;align-items:center;justify-content:space-between;gap:16px;
padding:10px 0 18px;border-bottom:1px solid #263548;margin-bottom:18px;">
 <div><div style="font-size:12px;letter-spacing:3px;color:#67e8f9;">◈ NETRESCUE / CONTROL PLANE</div>
 <div style="font-size:30px;font-weight:750;letter-spacing:-1.2px;color:#eefaff;">Network Resilience Intelligence</div>
 <div style="font-size:13px;color:#91a4ba;">Graph algorithms · Fault simulation · Recovery analysis</div></div>
 <div style="border:1px solid #285b4d;background:#0b211e;color:#86efac;border-radius:20px;padding:7px 12px;font-size:11px;white-space:nowrap;">● SIMULATION READY</div>
</div>
""", unsafe_allow_html=True)
config = sidebar_controls()
graph = config["graph"]
src, dst = config["src"], config["dst"]
failed_nodes, failed_edges = config["failed_nodes"], config["failed_edges"]
algorithm = config["algorithm"]

failed_graph = graph.copy()
for node in failed_nodes:
    failed_graph.remove_node(node)
for u, v in failed_edges:
    failed_graph.remove_edge(u, v)

ap, bridges = tarjan_articulation_and_bridges(graph)
report = RecoveryEngine(graph).analyze(src, dst, failed_nodes, failed_edges)
old_route = report.old_path
new_route = report.dijkstra_path

# Compact overview metrics make simulation state visible at a glance.
active_nodes = len(graph.nodes()) - len(failed_nodes)
active_links = sum(
    1 for u, v, _ in graph.edges()
    if u not in failed_nodes and v not in failed_nodes
    and (u, v) not in failed_edges and (v, u) not in failed_edges
)
def _component_count(g):
    seen = set()
    count = 0
    for start in g.nodes():
        if start in seen:
            continue
        count += 1
        stack = [start]
        seen.add(start)
        while stack:
            current = stack.pop()
            for neighbor in g.neighbors(current):
                if neighbor not in seen:
                    seen.add(neighbor)
                    stack.append(neighbor)
    return count

m1, m2, m3, m4 = st.columns(4)
m1.metric("ONLINE ROUTERS", f"{active_nodes}/{len(graph.nodes())}")
m2.metric("ACTIVE LINKS", active_links)
m3.metric("NETWORK PARTITIONS", _component_count(failed_graph))
m4.metric("SIMULATION STATE", "DEGRADED" if failed_nodes or failed_edges else "STABLE")

network_tab, recovery_tab, critical_tab, benchmark_tab, resilience_tab = st.tabs(
    ["Network", "Recovery", "Critical Points", "Benchmark", "Resilience"]
)

with network_tab:
    st.header("Network topology")
    replay_trace: list[dict] = []
    if algorithm in {"BFS", "All (compare)"}:
        bfs(failed_graph, src, dst, trace=replay_trace)
    elif algorithm == "Dijkstra":
        dijkstra(failed_graph, src, dst, trace=replay_trace)
    replay_step = st.slider("Algorithm replay step", 0, len(replay_trace), len(replay_trace))
    replay_events = replay_trace[:replay_step]
    replay_state = {
        "visited": [event["node"] for event in replay_events if event["kind"] == "visit"],
        "frontier": replay_events[-1]["frontier"] if replay_events else [],
        "current": replay_events[-1]["node"] if replay_events else None,
    }
    html = build_pyvis_html(
        graph,
        failed_nodes=failed_nodes,
        failed_edges=failed_edges,
        route=old_route,
        alt_route=new_route if new_route != old_route else (),
        critical=ap,
        bridges=bridges,
        replay_state=replay_state,
    )
    st.components.v1.html(html, height=650, scrolling=True)

with recovery_tab:
    st.header("Recovery analysis")
    badge = {
        "RECOVERED": "🟢", "UNAFFECTED": "🟢",
        "DEGRADED": "🟠", "DISCONNECTED": "🔴",
    }[report.status]
    st.subheader(f"{badge} {report.status}")
    if report.reason:
        st.caption(f"Reason: {report.reason}")
    left, right = st.columns(2)
    left.write({"original_path": report.old_path, "cost": report.old_cost, "hops": report.old_hops})
    right.write({
        "dijkstra_path": report.dijkstra_path,
        "dijkstra_cost": report.dijkstra_cost,
        "bfs_path": report.bfs_path,
        "bfs_hops": report.bfs_hops,
    })
    if report.cost_increase_pct is not None:
        st.metric("Cost increase", f"{report.cost_increase_pct:.1f}%")
    if report.extra_hops is not None:
        st.metric("Extra hops", report.extra_hops)
    if report.components:
        st.write("Islands", report.components)

with critical_tab:
    st.header("Critical routers and links")
    impacts = vulnerability_report(graph)
    st.dataframe([
        {"kind": item.kind, "id": str(item.id),
         "nodes_cut_off": item.nodes_cut_off,
         "islands_created": item.islands_created}
        for item in impacts
    ], use_container_width=True)

with benchmark_tab:
    st.header("Benchmark")
    sizes = st.multiselect(
        "Graph sizes",
        [10, 50, 100, 500, 1000],
        default=[10, 50, 100],
    )
    if st.button("Run benchmark"):
        rows = run_benchmarks(sizes=tuple(sizes), repeats=3, pairs=5)
        st.dataframe(rows, use_container_width=True)
        st.line_chart(
            {
                row["algorithm"]: {
                    row["size"]: row["avg_time_ms"] for row in rows
                    if row["algorithm"] == algorithm
                }
                for algorithm in sorted({row["algorithm"] for row in rows})
            }
        )
    trap_results = recovery_rate(graph, trials=10, node_failures=0, edge_failures=0)
    st.caption(f"Recovery-rate sample: {trap_results['rate']:.0%}")

with resilience_tab:
    st.header("Resilience")
    trials = st.slider("Monte Carlo trials", 0, 500, 50, step=10)
    if st.button("Compute resilience"):
        resilience = compute_resilience(graph, trials=trials)
        st.metric("Resilience score", f"{resilience.score:.2f}/100")
        st.write({
            "connectivity": resilience.connectivity,
            "critical contribution": resilience.critical_term,
            "inflation contribution": resilience.inflation_term,
            "average inflation": resilience.avg_inflation,
        })
        st.latex(
            r"100\left(0.5C + 0.3(1-\frac{|AP|}{|V|}) + "
            r"0.2\frac{1}{1+\bar I}\right)"
        )

if st.sidebar.button("Reset all"):
    reset_state()
    st.rerun()
