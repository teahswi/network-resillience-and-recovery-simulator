"""UI sidebar components for NetRescue.

This module contains functions that build Streamlit widgets and expose the
selected configuration via a dict. The heavy lifting (graph generation,
failure simulation, etc.) lives in the core package.
"""

from __future__ import annotations

import json
import random
from pathlib import Path
from typing import Dict, Any, List

import streamlit as st

from ..core.generator import (
    random_graph,
    ring,
    star,
    mesh,
    grid,
    tree,
)
from ..core.graph import Graph

# ---------------------------------------------------------------------------
def _load_sample_networks() -> Dict[str, Path]:
    """Return a mapping from sample name to JSON file path.
    Expected files live under ``data/sample_networks``.
    """
    base = Path(__file__).resolve().parents[2] / "data" / "sample_networks"
    mapping = {}
    if base.is_dir():
        for p in base.glob("*.json"):
            mapping[p.stem] = p
    return mapping

# ---------------------------------------------------------------------------
def sidebar_controls() -> Dict[str, Any]:
    """Render the sidebar and return a dict of user selections.

    The returned dict contains:
        * ``graph`` – a :class:`netrescue.core.graph.Graph` instance.
        * ``src`` / ``dst`` – selected source and destination node IDs.
        * ``failed_nodes`` / ``failed_edges`` – sets of failures.
        * ``algorithm`` – selected algorithm name.
    """
    st.sidebar.title("🛠️ NetRescue Controls")

    # ----- Topology picker -------------------------------------------------
    topology_option = st.sidebar.selectbox(
        "Topology", 
        ["Random", "Ring", "Star", "Mesh", "Grid", "Tree", "Sample JSON"]
    )
    graph: Graph
    if topology_option == "Random":
        n_nodes = st.sidebar.slider("Number of nodes", 5, 200, 20)
        prob = st.sidebar.slider("Edge probability", 0.0, 1.0, 0.2, 0.05)
        seed = st.sidebar.number_input("Seed", value=42, step=1)
        graph = random_graph(num_nodes=n_nodes, edge_prob=prob, seed=int(seed))
    elif topology_option == "Ring":
        n_nodes = st.sidebar.slider("Number of nodes", 3, 200, 10)
        graph = ring(n_nodes)
    elif topology_option == "Star":
        n_nodes = st.sidebar.slider("Number of nodes", 2, 200, 10)
        graph = star(n_nodes)
    elif topology_option == "Mesh":
        n_nodes = st.sidebar.slider("Number of nodes", 1, 100, 5)
        graph = mesh(n_nodes)
    elif topology_option == "Grid":
        rows = st.sidebar.slider("Rows", 1, 20, 3)
        cols = st.sidebar.slider("Cols", 1, 20, 3)
        graph = grid(rows, cols)
    elif topology_option == "Tree":
        n_nodes = st.sidebar.slider("Number of nodes", 1, 200, 20)
        seed = st.sidebar.number_input("Seed", value=42, step=1)
        graph = tree(n_nodes, seed=int(seed))
    else:  # Sample JSON
        samples = _load_sample_networks()
        if not samples:
            st.sidebar.error("No sample JSON files found in data/sample_networks.")
            st.stop()
        choice = st.sidebar.selectbox("Sample", list(samples.keys()))
        with open(samples[choice], "r", encoding="utf-8") as f:
            data = json.load(f)
        from ..core.graph import Graph as G
        graph = G.from_dict(data)

    # ----- Source / Destination --------------------------------------------
    node_list = list(graph.nodes())
    src = st.sidebar.selectbox("Source router", options=node_list, index=0)
    dst = st.sidebar.selectbox("Destination router", options=node_list, index=len(node_list)-1)

    # ----- Failure controls -------------------------------------------------
    st.sidebar.subheader("Failures")
    fail_mode = st.sidebar.radio("Failure mode", ["Manual", "Random"])
    failed_nodes: set = set()
    failed_edges: set = set()
    if fail_mode == "Manual":
        failed_nodes = set(st.sidebar.multiselect("Failed routers", options=node_list))
        # For edges, present as "u‑v" strings
        edge_choices = [f"{u}-{v}" for u, v, _ in graph.edges()]
        selected_edges = st.sidebar.multiselect("Failed links", options=edge_choices)
        for e in selected_edges:
            u, v = e.split('-')
            failed_edges.add((u, v) if u < v else (v, u))
    else:  # Random
        n_fail = st.sidebar.slider("# failed routers", 0, len(node_list), 0)
        e_fail = st.sidebar.slider("# failed links", 0, len(list(graph.edges())) // 2, 0)
        seed = st.sidebar.number_input("Random seed", value=0, step=1)
        from ..core.failure import FailureSimulator
        sim = FailureSimulator()
        sim.random_failure(graph, node_count=n_fail, edge_count=e_fail, seed=int(seed))
        failed_nodes = sim.failed_nodes
        failed_edges = sim.failed_edges

    # ----- Algorithm selector ----------------------------------------------
    algorithm = st.sidebar.selectbox("Algorithm", ["BFS", "DFS", "Dijkstra", "All (compare)"])

    # Store in session_state for reuse in main area
    st.session_state.graph = graph
    st.session_state.src = src
    st.session_state.dst = dst
    st.session_state.failed_nodes = failed_nodes
    st.session_state.failed_edges = failed_edges
    st.session_state.algorithm = algorithm

    return {
        "graph": graph,
        "src": src,
        "dst": dst,
        "failed_nodes": failed_nodes,
        "failed_edges": failed_edges,
        "algorithm": algorithm,
    }

# ---------------------------------------------------------------------------
def reset_state() -> None:
    """Utility to clear Streamlit session state when the user wants a fresh start."""
    for key in ["graph", "src", "dst", "failed_nodes", "failed_edges", "algorithm"]:
        if key in st.session_state:
            del st.session_state[key]

