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

st.set_page_config(page_title="NetRescue", layout="wide")
config = sidebar_controls()
graph = config["graph"]
src, dst = config["src"], config["dst"]
failed_nodes, failed_edges = config["failed_nodes"], config["failed_edges"]

failed_graph = graph.copy()
for node in failed_nodes:
    failed_graph.remove_node(node)
for u, v in failed_edges:
    failed_graph.remove_edge(u, v)

ap, bridges = tarjan_articulation_and_bridges(graph)
report = RecoveryEngine(graph).analyze(src, dst, failed_nodes, failed_edges)
old_route = report.old_path
new_route = report.dijkstra_path

network_tab, recovery_tab, critical_tab, benchmark_tab, resilience_tab = st.tabs(
    ["Network", "Recovery", "Critical Points", "Benchmark", "Resilience"]
)

with network_tab:
    st.header("Network topology")
    html = build_pyvis_html(
        graph,
        failed_nodes=failed_nodes,
        failed_edges=failed_edges,
        route=old_route,
        alt_route=new_route if new_route != old_route else (),
        critical=ap,
        bridges=bridges,
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
