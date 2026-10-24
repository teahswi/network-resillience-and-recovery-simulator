# netrescue/core/graph.py
"""Graph data structure for NetRescue.

Provides an undirected weighted graph implemented via an adjacency list.
All core algorithms operate on this class; failures are applied on top
of a copy rather than mutating the original graph.

The adjacency list format is:
    {
        node: { neighbor: weight, ... },
        ...
    }

Node identifiers are strings (e.g., "R1") but any hashable can be used.
"""

from __future__ import annotations

import copy
import json
import math
from numbers import Real
from typing import Dict, Iterable, Tuple, Set, Any

Node = Any
Weight = float

def edge_key(u: Node, v: Node) -> tuple[Node, Node]:
    """Return a canonical key for an undirected edge in O(1).

    String node identifiers are ordered lexicographically. Other comparable
    identifiers are ordered naturally; mixed, non-comparable identifiers use
    their representations to retain a deterministic key.
    """
    try:
        return (u, v) if u <= v else (v, u)
    except TypeError:
        return (u, v) if repr(u) <= repr(v) else (v, u)

class Graph:
    """Undirected weighted graph using an adjacency‑list representation.

    Attributes
    ----------
    adj : Dict[Node, Dict[Node, Weight]]
        Mapping from a node to its neighbours and edge weights.

    Methods
    -------
    add_node(node)
        Add a node if it does not already exist.
    add_edge(u, v, w)
        Add an undirected edge with weight ``w``.
    remove_node(node)
        Remove ``node`` and all incident edges.
    remove_edge(u, v)
        Remove the undirected edge between ``u`` and ``v``.
    has_edge(u, v) -> bool
        Return ``True`` if an edge exists.
    neighbors(node) -> Iterable[Node]
        Iterate over neighbours of ``node``.
    nodes() -> Iterable[Node]
        Iterate over all nodes.
    edges() -> Iterable[Tuple[Node, Node, Weight]]
        Iterate over all edges (each edge appears once).
    copy() -> "Graph"
        Deep copy of the graph.
    to_dict() -> Dict
        JSON‑serialisable dict representation.
    @staticmethod
    from_dict(data) -> "Graph"
        Re‑create a Graph from a dict produced by ``to_dict``.
    """

    def __init__(self) -> None:
        self.adj: Dict[Node, Dict[Node, Weight]] = {}

    # ---------------------------------------------------------------------
    # Basic mutators
    # ---------------------------------------------------------------------
    def add_node(self, node: Node) -> None:
        """Add ``node`` to the graph.

        Complexity: **O(1)**.
        """
        self.adj.setdefault(node, {})

    def add_edge(self, u: Node, v: Node, w: Weight) -> None:
        """Add an undirected edge ``(u, v)`` with weight ``w``.

        If the edge already exists, its weight is overwritten.
        Complexity: **O(1)**.
        """
        if u == v:
            raise ValueError("Self-loops are not supported.")
        if isinstance(w, bool) or not isinstance(w, Real):
            raise TypeError("Edge weight must be a real number.")
        if not math.isfinite(float(w)) or w < 0:
            raise ValueError("Edge weight must be finite and non-negative.")
        self.add_node(u)
        self.add_node(v)
        self.adj[u][v] = w
        self.adj[v][u] = w

    def remove_node(self, node: Node) -> None:
        """Remove ``node`` and all incident edges.

        Complexity: **O(deg(node))**.
        """
        if node not in self.adj:
            return
        # Remove this node from its neighbours' adjacency lists
        for neighbour in list(self.adj[node].keys()):
            self.adj[neighbour].pop(node, None)
        # Finally delete the node entry
        self.adj.pop(node, None)

    def remove_edge(self, u: Node, v: Node) -> None:
        """Remove the undirected edge ``(u, v)`` if it exists.

        Complexity: **O(1)**.
        """
        self.adj.get(u, {}).pop(v, None)
        self.adj.get(v, {}).pop(u, None)

    # ---------------------------------------------------------------------
    # Queries
    # ---------------------------------------------------------------------
    def has_edge(self, u: Node, v: Node) -> bool:
        """Return ``True`` if an edge ``(u, v)`` exists.

        Complexity: **O(1)**.
        """
        return v in self.adj.get(u, {})

    def neighbors(self, node: Node) -> Iterable[Node]:
        """Yield neighbours of ``node``.

        Complexity: **O(deg(node))**.
        """
        return self.adj.get(node, {}).keys()

    def nodes(self) -> Iterable[Node]:
        """Yield all nodes in the graph.

        Complexity: **O(V)**.
        """
        return self.adj.keys()

    def edges(self) -> Iterable[Tuple[Node, Node, Weight]]:
        """Yield all undirected edges exactly once.

        Complexity: **O(V + E)**.
        """
        seen: Set[Tuple[Node, Node]] = set()
        for u, nbrs in self.adj.items():
            for v, w in nbrs.items():
                if (v, u) not in seen:
                    seen.add((u, v))
                    yield (u, v, w)

    # ---------------------------------------------------------------------
    # Utility / I/O
    # ---------------------------------------------------------------------
    def copy(self) -> "Graph":
        """Return a deep copy of the graph.

        Complexity: **O(V + E)**.
        """
        new = Graph()
        # ``dict`` copy of a nested dict is shallow; we need a deep copy.
        new.adj = copy.deepcopy(self.adj)
        return new

    def to_dict(self) -> Dict[Node, Dict[Node, Weight]]:
        """Return a JSON‑serialisable representation.

        The structure matches the internal ``adj`` dict.
        """
        return copy.deepcopy(self.adj)

    def to_json(self) -> str:
        """Serialize the graph as JSON in O(V + E)."""
        return json.dumps(self.to_dict(), sort_keys=True)

    @staticmethod
    def from_dict(data: Dict[Node, Dict[Node, Weight]]) -> "Graph":
        """Create a ``Graph`` from a dict produced by ``to_dict``.
        """
        if not isinstance(data, dict):
            raise TypeError("Graph data must be a dictionary.")
        g = Graph()
        for node, neighbours in data.items():
            if not isinstance(neighbours, dict):
                raise TypeError("Each node must map to a neighbour dictionary.")
            g.add_node(node)
            for neighbour, weight in neighbours.items():
                g.add_edge(node, neighbour, weight)
        for u, neighbours in data.items():
            for v, weight in neighbours.items():
                if v not in data or data[v].get(u) != weight:
                    raise ValueError("Graph adjacency data must be symmetric.")
        return g

    @staticmethod
    def from_json(value: str) -> "Graph":
        """Deserialize a JSON graph representation in O(V + E)."""
        return Graph.from_dict(json.loads(value))

    # ---------------------------------------------------------------------
    # Convenience helpers
    # ---------------------------------------------------------------------
    def __len__(self) -> int:
        """Number of nodes in the graph (``len(graph)``)."""
        return len(self.adj)

    def __str__(self) -> str:
        return f"Graph(nodes={len(self)}, edges={sum(len(nbrs) for nbrs in self.adj.values()) // 2})"

    def __repr__(self) -> str:
        return self.__str__()
