"""Visualization utilities for NetRescue.

Creates a PyVis network (HTML) from the custom :class:`netrescue.core.graph.Graph`
instance, applying colour coding for node/edge states (healthy, failed, bridge,
critical, etc.). The generated HTML is intended to be displayed inside Streamlit
via ``st.components.v1.html``.
"""

from __future__ import annotations

import json
from typing import Set, Tuple, Dict, Any

import networkx as nx
from pyvis.network import Network

from ..core.graph import Graph, Node
from ..core.critical import tarjan_articulation_and_bridges

# ---------------------------------------------------------------------------
def _to_networkx(g: Graph) -> nx.Graph:
    """Convert the custom Graph to a NetworkX graph for layout utilities.
    Edge weights are stored as the ``weight`` attribute.
    """
    nx_g = nx.Graph()
    for u in g.nodes():
        nx_g.add_node(u)
    for u, v, w in g.edges():
        nx_g.add_edge(u, v, weight=w)
    return nx_g

# ---------------------------------------------------------------------------
def build_pyvis_html(
    g: Graph,
    failed_nodes: Set[Node] | None = None,
    failed_edges: Set[Tuple[Node, Node]] | None = None,
    highlight_path: Tuple[Node, ...] | None = None,
) -> str:
    """Return an HTML string rendering the network.

    Parameters
    ----------
    g : Graph
        The underlying topology.
    failed_nodes, failed_edges : sets, optional
        Nodes/edges to colour red (failed) or gray (cut).
    highlight_path : tuple of nodes, optional
        The current active route – rendered in blue.
    """
    failed_nodes = failed_nodes or set()
    failed_edges = failed_edges or set()
    ap, bridges = tarjan_articulation_and_bridges(g)

    net = Network(height="600px", width="100%", directed=False)
    net.barnes_hut()

    # Add nodes with colour based on state
    for n in g.nodes():
        color = "green"
        if n in failed_nodes:
            color = "red"
        elif n in ap:
            color = "purple"
        title = f"Router {n}"
        net.add_node(n, label=n, color=color, title=title)

    # Helper to decide edge colour/style
    def edge_style(u: Node, v: Node) -> Dict[str, Any]:
        edge_key = (u, v) if u < v else (v, u)
        style = {"color": "black", "width": 1, "dashes": False}
        if edge_key in failed_edges:
            style.update({"color": "gray", "width": 2, "dashes": True})
        elif edge_key in bridges:
            style.update({"color": "red", "width": 3})
        if highlight_path and edge_key in {
            (highlight_path[i], highlight_path[i + 1])
            for i in range(len(highlight_path) - 1)
        }:
            style.update({"color": "blue", "width": 4})
        return style

    # Add edges
    for u, v, w in g.edges():
        style = edge_style(u, v)
        net.add_edge(u, v, value=w, title=f"weight={w}", color=style["color"], width=style["width"], dashes=style["dashes"])

    # Generate HTML
    net.set_options(json.dumps({
        "physics": {"enabled": True},
        "nodes": {"shape": "dot", "size": 15},
        "edges": {"smooth": {"type": "continuous"}}
    }))
    return net.generate_html()

# ---------------------------------------------------------------------------
def save_visualization(html: str, path: str) -> None:
    """Write the HTML to *path*.
    Used by the UI for caching.
    """
    with open(path, "w", encoding="utf-8") as f:
        f.write(html)

"""End of visualizer module."""
