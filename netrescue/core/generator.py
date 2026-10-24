"""Topology generators for NetRescue.

Each function returns a :class:`netrescue.core.graph.Graph` instance.
"""

from __future__ import annotations

import random
from typing import Iterable, Mapping, Sequence

from .graph import Graph

def random_graph(
    num_nodes: int,
    edge_prob: float | None = 0.2,
    seed: int | None = None,
    *,
    avg_degree: float | None = None,
    weighted: bool = True,
) -> Graph:
    """Generate a connected random undirected weighted graph.

    Parameters
    ----------
    num_nodes: int
        Number of nodes (must be >= 1).
    edge_prob: float, optional
        Probability of adding an extra non‑spanning edge between any two nodes
        after the initial spanning tree is built. Must be between 0 and 1.
    seed: int | None, optional
        Random seed for reproducibility.

    Returns
    -------
    Graph
        A connected random graph with weights in the range [1, 10].

    Notes
    -----
    The algorithm first creates a random spanning tree to guarantee connectivity
    and then adds extra edges according to ``edge_prob``.
    """
    if num_nodes <= 0:
        raise ValueError("num_nodes must be positive")
    if avg_degree is not None:
        if avg_degree < 0:
            raise ValueError("avg_degree must be non-negative")
        edge_prob = min(1.0, avg_degree / max(num_nodes - 1, 1))
    if edge_prob is None or not 0 <= edge_prob <= 1:
        raise ValueError("edge_prob must be between 0 and 1")
    rnd = random.Random(seed)
    g = Graph()
    # Add nodes
    for i in range(num_nodes):
        g.add_node(f"R{i}")
    # Create a random spanning tree using a shuffled node list
    nodes = list(g.nodes())
    rnd.shuffle(nodes)
    for i in range(1, len(nodes)):
        u = nodes[i]
        v = rnd.choice(nodes[:i])
        w = rnd.randint(1, 10) if weighted else 1
        g.add_edge(u, v, w)
    # Possibly add extra edges
    for i in range(num_nodes):
        for j in range(i + 1, num_nodes):
            if rnd.random() < edge_prob and not g.has_edge(f"R{i}", f"R{j}"):
                w = rnd.randint(1, 10) if weighted else 1
                g.add_edge(f"R{i}", f"R{j}", w)
    return g

def custom_graph(
    nodes: Iterable[str],
    edges: Iterable[Sequence[object]],
) -> Graph:
    """Build a graph from node identifiers and ``(u, v[, weight])`` edges.

    Complexity: O(V + E).
    """
    g = Graph()
    for node in nodes:
        g.add_node(node)
    for edge in edges:
        if len(edge) == 2:
            u, v = edge
            weight = 1
        elif len(edge) == 3:
            u, v, weight = edge
        else:
            raise ValueError("Each edge must contain two or three values.")
        g.add_edge(u, v, weight)
    return g

def ring(num_nodes: int) -> Graph:
    """Create a ring (cycle) topology with equal weight edges.
    """
    if num_nodes < 3:
        raise ValueError("Ring needs at least 3 nodes")
    g = Graph()
    for i in range(num_nodes):
        g.add_node(f"R{i}")
    for i in range(num_nodes):
        u = f"R{i}"
        v = f"R{(i + 1) % num_nodes}"
        g.add_edge(u, v, 1)
    return g

def star(num_nodes: int) -> Graph:
    """Create a star topology – one central hub connected to all leaves.
    """
    if num_nodes < 2:
        raise ValueError("Star needs at least 2 nodes")
    g = Graph()
    hub = "R0"
    g.add_node(hub)
    for i in range(1, num_nodes):
        leaf = f"R{i}"
        g.add_node(leaf)
        g.add_edge(hub, leaf, 1)
    return g

def mesh(num_nodes: int) -> Graph:
    """Complete graph (every node connected to every other).
    """
    if num_nodes < 1:
        raise ValueError("num_nodes must be >= 1")
    g = Graph()
    for i in range(num_nodes):
        g.add_node(f"R{i}")
    for i in range(num_nodes):
        for j in range(i + 1, num_nodes):
            g.add_edge(f"R{i}", f"R{j}", 1)
    return g

def grid(rows: int, cols: int) -> Graph:
    """Create a 2‑D grid topology.
    """
    if rows <= 0 or cols <= 0:
        raise ValueError("rows and cols must be positive")
    g = Graph()
    def node(r, c):
        return f"R{r}_{c}"
    for r in range(rows):
        for c in range(cols):
            g.add_node(node(r, c))
    for r in range(rows):
        for c in range(cols):
            if r + 1 < rows:
                g.add_edge(node(r, c), node(r + 1, c), 1)
            if c + 1 < cols:
                g.add_edge(node(r, c), node(r, c + 1), 1)
    return g

def tree(num_nodes: int, seed: int | None = None) -> Graph:
    """Generate a random tree using the Prüfer sequence method.
    """
    if num_nodes < 1:
        raise ValueError("num_nodes must be >= 1")
    rnd = random.Random(seed)
    if num_nodes == 1:
        g = Graph()
        g.add_node("R0")
        return g
    # Prüfer sequence of length n-2
    prufer = [rnd.randint(0, num_nodes - 1) for _ in range(num_nodes - 2)]
    degree = [1] * num_nodes
    for v in prufer:
        degree[v] += 1
    leaves = [i for i in range(num_nodes) if degree[i] == 1]
    leaves.sort()
    g = Graph()
    for i in range(num_nodes):
        g.add_node(f"R{i}")
    for v in prufer:
        leaf = leaves.pop(0)
        g.add_edge(f"R{leaf}", f"R{v}", 1)
        degree[leaf] -= 1
        degree[v] -= 1
        if degree[v] == 1:
            # insert maintaining sorted order
            leaves.append(v)
            leaves.sort()
    # connect the last two remaining nodes
    leaf1, leaf2 = leaves
    g.add_edge(f"R{leaf1}", f"R{leaf2}", 1)
    return g
