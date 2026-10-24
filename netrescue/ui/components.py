"""Streamlit sidebar controls for NetRescue."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import streamlit as st

from ..core.failure import FailureSimulator
from ..core.generator import custom_graph, grid, mesh, random_graph, ring, star, tree
from ..core.graph import Graph, edge_key


def _load_sample_networks() -> dict[str, Path]:
    """Return bundled sample JSON files in O(S) time."""
    base = Path(__file__).resolve().parents[1] / "data" / "sample_networks"
    return {path.stem: path for path in sorted(base.glob("*.json"))} if base.is_dir() else {}


def _read_graph(upload: Any) -> Graph:
    """Read and validate an uploaded graph."""
    try:
        data = json.loads(upload.getvalue().decode("utf-8"))
        return Graph.from_dict(data)
    except (UnicodeDecodeError, json.JSONDecodeError, TypeError, ValueError) as exc:
        st.sidebar.error(f"Invalid graph JSON: {exc}")
        st.stop()
        raise AssertionError("st.stop did not stop execution")


def sidebar_controls() -> dict[str, Any]:
    """Render controls and return the current graph configuration."""
    st.sidebar.title("NetRescue Controls")
    topology = st.sidebar.selectbox(
        "Topology",
        ["Random", "Ring", "Star", "Mesh", "Grid", "Tree", "Sample JSON", "Upload JSON"],
    )
    seed = int(st.sidebar.number_input("Seed", value=42, step=1))
    if topology == "Random":
        n = st.sidebar.slider("Number of nodes", 1, 200, 20)
        density = st.sidebar.slider("Average degree", 0.0, 20.0, 4.0)
        graph = random_graph(n, avg_degree=density, seed=seed)
    elif topology == "Ring":
        graph = ring(st.sidebar.slider("Number of nodes", 3, 200, 10))
    elif topology == "Star":
        graph = star(st.sidebar.slider("Number of nodes", 2, 200, 10))
    elif topology == "Mesh":
        graph = mesh(st.sidebar.slider("Number of nodes", 1, 100, 5))
    elif topology == "Grid":
        graph = grid(st.sidebar.slider("Rows", 1, 20, 3), st.sidebar.slider("Columns", 1, 20, 3))
    elif topology == "Tree":
        graph = tree(st.sidebar.slider("Number of nodes", 1, 200, 20), seed=seed)
    elif topology == "Sample JSON":
        samples = _load_sample_networks()
        if not samples:
            st.sidebar.error("No sample JSON files found.")
            st.stop()
        path = samples[st.sidebar.selectbox("Sample", list(samples))]
        graph = Graph.from_dict(json.loads(path.read_text(encoding="utf-8")))
    else:
        upload = st.sidebar.file_uploader("Upload graph JSON", type="json")
        graph = _read_graph(upload) if upload else mesh(1)

    nodes = list(graph.nodes())
    src = st.sidebar.selectbox("Source", nodes, index=0)
    dst = st.sidebar.selectbox("Destination", nodes, index=max(0, len(nodes) - 1))
    failed_nodes = set(st.sidebar.multiselect("Failed routers", nodes))
    edge_labels = {f"{u} - {v}": (u, v) for u, v, _ in graph.edges()}
    selected = st.sidebar.multiselect("Failed links", list(edge_labels))
    failed_edges = {edge_key(*edge_labels[label]) for label in selected}
    if graph.adj:
        node_count = st.sidebar.slider("# random failed routers", 0, len(nodes), 0)
        edge_count = st.sidebar.slider("# random failed links", 0, max(len(list(graph.edges())), 1), 0)
        if st.sidebar.checkbox("Use seeded random failures"):
            sim = FailureSimulator(graph)
            sim.random_failure(graph, node_count, min(edge_count, len(list(graph.edges()))), seed=seed, protect=frozenset({src, dst}))
            failed_nodes, failed_edges = set(sim.failed_nodes), set(sim.failed_edges)
    algorithm = st.sidebar.selectbox("Algorithm", ["BFS", "DFS", "Dijkstra", "All (compare)"])
    st.session_state.update(
        graph=graph, src=src, dst=dst, failed_nodes=failed_nodes,
        failed_edges=failed_edges, algorithm=algorithm,
    )
    return {
        "graph": graph, "src": src, "dst": dst,
        "failed_nodes": failed_nodes, "failed_edges": failed_edges,
        "algorithm": algorithm,
    }


def reset_state() -> None:
    """Clear UI state in O(K), where K is the number of stored keys."""
    for key in ["graph", "src", "dst", "failed_nodes", "failed_edges", "algorithm"]:
        st.session_state.pop(key, None)
