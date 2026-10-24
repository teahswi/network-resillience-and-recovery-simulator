"""Failure simulation for NetRescue.

The simulator tracks failed nodes and edges without mutating the original
graph. It keeps a history stack so the UI can provide undo/redo and a timeline.
"""

from __future__ import annotations

import copy
import random
from typing import Set, Tuple, List, Dict, Any

from .graph import Graph, Node

class FailureSimulator:
    """Manage node and edge failures.

    Attributes
    ----------
    failed_nodes : set of Node
        Nodes currently marked as failed.
    failed_edges : set of (Node, Node)
        Undirected edges currently marked as cut.
    history : list of dict
        Each entry records ``{'nodes': set, 'edges': set}`` after an operation.
    """

    def __init__(self) -> None:
        self.failed_nodes: Set[Node] = set()
        self.failed_edges: Set[Tuple[Node, Node]] = set()
        self.history: List[Dict[str, Any]] = []
        self._record_state()

    # ------------------------------------------------------------------
    def _record_state(self) -> None:
        self.history.append({
            'nodes': copy.deepcopy(self.failed_nodes),
            'edges': copy.deepcopy(self.failed_edges)
        })

    # ------------------------------------------------------------------
    def fail_node(self, node: Node) -> None:
        self.failed_nodes.add(node)
        self._record_state()

    def restore_node(self, node: Node) -> None:
        self.failed_nodes.discard(node)
        self._record_state()

    def fail_edge(self, u: Node, v: Node) -> None:
        edge = (u, v) if u < v else (v, u)
        self.failed_edges.add(edge)
        self._record_state()

    def restore_edge(self, u: Node, v: Node) -> None:
        edge = (u, v) if u < v else (v, u)
        self.failed_edges.discard(edge)
        self._record_state()

    def reset(self) -> None:
        self.failed_nodes.clear()
        self.failed_edges.clear()
        self._record_state()

    # ------------------------------------------------------------------
    def apply_to(self, g: Graph) -> Graph:
        """Return a copy of *g* with current failures applied.
        Nodes in ``failed_nodes`` are removed, and edges in ``failed_edges`` are
        omitted.
        """
        g_copy = g.copy()
        for n in self.failed_nodes:
            g_copy.remove_node(n)
        for u, v in self.failed_edges:
            if g_copy.has_edge(u, v):
                g_copy.remove_edge(u, v)
        return g_copy

    # ------------------------------------------------------------------
    def random_failure(self, g: Graph, node_count: int = 0, edge_count: int = 0, seed: int | None = None) -> None:
        """Fail ``node_count`` random nodes and ``edge_count`` random edges.
        ``seed`` makes the selection reproducible.
        """
        rnd = random.Random(seed)
        available_nodes = [n for n in g.nodes() if n not in self.failed_nodes]
        available_edges = [(u, v) for u, v, _ in g.edges() if (u, v) not in self.failed_edges and (v, u) not in self.failed_edges]
        rnd.shuffle(available_nodes)
        rnd.shuffle(available_edges)
        for n in available_nodes[:node_count]:
            self.fail_node(n)
        for u, v in available_edges[:edge_count]:
            self.fail_edge(u, v)

    # ------------------------------------------------------------------
    def get_history(self) -> List[Dict[str, Any]]:
        """Return the list of failure states for timeline playback."""
        return self.history
