"""Pure PyVis rendering helpers for the Streamlit UI."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterable

import networkx as nx
from pyvis.network import Network

from ..core.graph import Graph, Node, edge_key


def _to_networkx(g: Graph) -> nx.Graph:
    """Convert a core graph for layout only in O(V + E)."""
    result = nx.Graph()
    result.add_nodes_from(g.nodes())
    result.add_edges_from((u, v, {"weight": w}) for u, v, w in g.edges())
    return result


def build_pyvis_html(
    g: Graph,
    failed_nodes: Iterable[Node] = (),
    failed_edges: Iterable[tuple[Node, Node]] = (),
    route: Iterable[Node] = (),
    alt_route: Iterable[Node] = (),
    critical: Iterable[Node] = (),
    bridges: Iterable[tuple[Node, Node]] = (),
    positions: dict[Node, tuple[float, float]] | None = None,
    replay_state: dict[str, Any] | None = None,
    highlight_path: tuple[Node, ...] | None = None,
) -> str:
    """Build inline, deterministic PyVis HTML without running algorithms."""
    failed_nodes = set(failed_nodes)
    failed_edges = {edge_key(u, v) for u, v in failed_edges}
    critical = set(critical)
    bridges = {edge_key(u, v) for u, v in bridges}
    route_set = {
        edge_key(route_list[i], route_list[i + 1])
        for route_list in [list(route) if route else list(highlight_path or ())]
        for i in range(len(route_list) - 1)
    }
    alt_list = list(alt_route)
    alt_set = {edge_key(alt_list[i], alt_list[i + 1]) for i in range(len(alt_list) - 1)}
    visited = set((replay_state or {}).get("visited", ()))
    frontier = set((replay_state or {}).get("frontier", ()))
    current = (replay_state or {}).get("current")

    net = Network(
        height="600px", width="100%", directed=False,
        bgcolor="#080d14", font_color="#dbeafe", cdn_resources="in_line",
    )
    layout = positions or {
        node: (float(x), float(y))
        for node, (x, y) in nx.spring_layout(_to_networkx(g), seed=42).items()
    }
    for node in g.nodes():
        if node in failed_nodes:
            color = "#e53935"
        elif node in critical:
            color = "#8e44ad"
        elif node in visited:
            color = "#3498db"
        else:
            color = "#2ecc71"
        options: dict[str, Any] = {
            "label": str(node),
            "color": {
                "background": color,
                "border": "#67e8f9" if node in route else ("#f87171" if node in failed_nodes else "#526579"),
                "highlight": {"background": "#164e63", "border": "#a5f3fc"},
                "hover": {"background": "#164e63", "border": "#67e8f9"},
            },
            "x": layout[node][0] * 500,
            "y": layout[node][1] * 500,
            "title": f"Router {node}",
        }
        if node in critical:
            options.update({"borderWidth": 6, "shadow": {"color": "#8e44ad"}})
        if node in frontier:
            options["shape"] = "diamond"
        if node == current:
            options["size"] = 25
        net.add_node(node, **options)

    for u, v, weight in g.edges():
        key = edge_key(u, v)
        style = {"color": {"color": "#344256", "highlight": "#67e8f9", "hover": "#67e8f9"}, "width": 1, "dashes": False}
        if key in failed_edges or u in failed_nodes or v in failed_nodes:
            style = {"color": "#999", "width": 2, "dashes": True}
        elif key in route_set:
            style = {"color": "#1976d2", "width": 5, "dashes": False}
        elif key in alt_set:
            style = {"color": "#f39c12", "width": 5, "dashes": False}
        elif key in bridges:
            style = {"color": "#c62828", "width": 4, "dashes": False}
        net.add_edge(u, v, value=weight, title=f"weight={weight}", **style)
    net.set_options(json.dumps({
        "physics": {"enabled": False},
        "interaction": {"hover": True, "navigationButtons": True, "keyboard": True},
        "edges": {"smooth": {"enabled": True, "type": "continuous"}},
        "nodes": {"font": {"color": "#dbeafe", "size": 16, "face": "monospace"}},
    }))
    return net.generate_html() + (
        "<div><b>Legend:</b> "
        "<span style='color:#2ecc71'>healthy</span> · "
        "<span style='color:#e53935'>failed</span> · "
        "<span style='color:#1976d2'>route</span> · "
        "<span style='color:#f39c12'>alternate</span> · "
        "<span style='color:#c62828'>bridge</span></div>"
    )


def save_visualization(html: str, path: str) -> None:
    """Write visualization HTML to a file in O(n) time."""
    Path(path).write_text(html, encoding="utf-8")
