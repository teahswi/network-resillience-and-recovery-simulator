"""Entry point for NetRescue Streamlit app.

The app glues together the sidebar controls, the visualizer and the core
recovery/benchmark logic. It is deliberately lightweight – all heavy work lives
in the ``netrescue.core`` package.
"""

from __future__ import annotations

import streamlit as st
from pathlib import Path

from netrescue.ui.components import sidebar_controls, reset_state
from netrescue.ui.visualizer import build_pyvis_html, save_visualization
from netrescue.core.recovery import recover
from netrescue.core.bfs import bfs
from netrescue.core.benchmark import run_benchmarks, compute_resilience_score

# ---------------------------------------------------------------------------
st.set_page_config(page_title="NetRescue – Network Resilience Simulator", layout="wide")

# Sidebar – get configuration and store in session_state
config = sidebar_controls()
graph = config["graph"]
src = config["src"]
dst = config["dst"]
failed_nodes = config["failed_nodes"]
failed_edges = config["failed_edges"]
algorithm = config["algorithm"]

# Main area – tabs ----------------------------------------------------------
tab1, tab2, tab3, tab4, tab5 = st.tabs([
    "Network",
    "Recovery",
    "Critical Points",
    "Benchmark",
    "Resilience Score",
])

# -------------------- Network tab ----------------------------------------
with tab1:
    st.header("📡 Network topology")
    # Build visualisation HTML (cached)
    @st.cache_data(show_spinner=False)
    def get_html():
        return build_pyvis_html(
            graph,
            failed_nodes=failed_nodes,
            failed_edges=failed_edges,
        )
    html = get_html()
    st.components.v1.html(html, height=600, scrolling=True)

# -------------------- Recovery tab ---------------------------------------
with tab2:
    st.header("🔧 Recovery analysis")
    from netrescue.core.failure import FailureSimulator
    # Build a minimal FailureSimulator with the selected failures
    failure_sim = FailureSimulator()
    failure_sim.failed_nodes = set(failed_nodes)
    failure_sim.failed_edges = set(failed_edges)
    result = recover(graph, src, dst, failure_sim=failure_sim)
    st.subheader(f"Status: {result['status']}")
    col1, col2 = st.columns(2)
    with col1:
        st.write("**Original path**")
        st.write(result["old_path"] or "–")
        st.write(f"Cost: {result['old_cost']:.2f}" if result['old_cost'] != float('inf') else "∞")
    with col2:
        st.write("**New path after failures**")
        st.write(result["new_path"] or "–")
        st.write(f"Cost: {result['new_cost']:.2f}" if result['new_cost'] != float('inf') else "∞")
    if result["cost_increase_pct"] is not None:
        st.metric("Cost increase %", f"{result['cost_increase_pct']:.1f}%")
    if result["hop_delta"] is not None:
        st.metric("Hop delta", f"{result['hop_delta']:+d}")

# -------------------- Critical Points tab --------------------------------
with tab3:
    st.header("🧩 Critical routers & bridges")
    from netrescue.core.critical import tarjan_articulation_and_bridges, vulnerability_report
    ap, bridges = tarjan_articulation_and_bridges(graph)
    st.subheader("Articulation points (critical routers)")
    if ap:
        report = vulnerability_report(graph)
        st.table([{"Router": r, "Nodes cut off": loss} for r, loss in report])
    else:
        st.write("No articulation points.")
    st.subheader("Bridges (critical links)")
    if bridges:
        st.write([f"{u} – {v}" for u, v in bridges])
    else:
        st.write("No bridges.")

# -------------------- Benchmark tab --------------------------------------
with tab4:
    st.header("⚡ Benchmark")
    sizes = st.multiselect("Graph sizes to benchmark", options=[10, 50, 100, 500, 1000], default=[10, 50, 100])
    if st.button("Run benchmark"):
        df = run_benchmarks(sizes=sizes)
        st.dataframe(df)
        st.line_chart(df.pivot(index="size", columns="algorithm", values="avg_time_ms"), y="avg_time_ms")

# -------------------- Resilience Score tab ------------------------------
with tab5:
    st.header("🏆 Resilience Score")
    trials = st.slider("Monte‑Carlo trials", 50, 500, 200, step=50)
    if st.button("Compute score"):
        score = compute_resilience_score(graph, trials=trials)
        st.success(f"Resilience Score: {score}/100")
        st.caption(
            "Score = 0.5·connectivity + 0.3·(1‑critical‑frac) + 0.2·(1/(1+cost‑inflation)) multiplied by 100"
        )

# ---------------------------------------------------------------------------
if st.sidebar.button("Reset all"):
    reset_state()
    st.experimental_rerun()
