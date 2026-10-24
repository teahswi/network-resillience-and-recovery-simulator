"""Dijkstra's shortest‑path algorithm for NetRescue.

All operations are performed on the custom :class:`netrescue.core.graph.Graph`
instance. The caller may supply ``failed_nodes`` and ``failed_edges`` sets – any
node or edge present in those sets is ignored during the search.
"""

from __future__ import annotations

import heapq
from typing import Dict, List, Tuple, Set, Any

from .graph import Graph, Node, Weight

def dijkstra(
    g: Graph,
    src: Node,
    dst: Node,
    failed_nodes: Set[Node] | None = None,
    failed_edges: Set[Tuple[Node, Node]] | None = None,
) -> Tuple[float, List[Node], int]:
    """Return the minimum‑cost path from ``src`` to ``dst``.

    Parameters
    ----------
    g : Graph
        The graph.
    src, dst : Node
        Source and destination nodes.
    failed_nodes : set, optional
        Nodes that should be treated as removed.
    failed_edges : set of (u, v), optional
        Undirected edges that should be ignored.

    Returns
    -------
    distance : float
        Total cost of the shortest path, or ``float('inf')`` if unreachable.
    path : list of Node
        The sequence of nodes from ``src`` to ``dst`` (empty if unreachable).
    explored : int
        Number of nodes popped from the priority queue (useful for metrics).

    Complexity
    ----------
    **Time:** O((V + E) log V) using a binary heap.
    **Space:** O(V) for distance / predecessor tables.
    """
    if src not in g.adj or dst not in g.adj:
        return float('inf'), [], 0
    failed_nodes = failed_nodes or set()
    failed_edges = failed_edges or set()
    # Helper to check if an edge should be ignored
    def edge_blocked(u: Node, v: Node) -> bool:
        return (u, v) in failed_edges or (v, u) in failed_edges

    dist: Dict[Node, float] = {node: float('inf') for node in g.nodes() if node not in failed_nodes}
    prev: Dict[Node, Node | None] = {node: None for node in dist}
    dist[src] = 0.0

    heap: List[Tuple[float, Node]] = [(0.0, src)]
    explored = 0
    while heap:
        cur_dist, u = heapq.heappop(heap)
        if u in failed_nodes:
            continue
        if cur_dist != dist[u]:
            # stale entry
            continue
        explored += 1
        if u == dst:
            break
        for v, w in g.adj[u].items():
            if v in failed_nodes or edge_blocked(u, v):
                continue
            alt = cur_dist + w
            if alt < dist[v]:
                dist[v] = alt
                prev[v] = u
                heapq.heappush(heap, (alt, v))
    if dist.get(dst, float('inf')) == float('inf'):
        return float('inf'), [], explored
    # reconstruct path
    path: List[Node] = []
    cur: Node | None = dst
    while cur is not None:
        path.append(cur)
        cur = prev[cur]
    path.reverse()
    return dist[dst], path, explored
