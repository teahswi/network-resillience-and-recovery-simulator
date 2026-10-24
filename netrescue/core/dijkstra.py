"""Dijkstra's shortest-path algorithm for NetRescue."""

from __future__ import annotations

import heapq
from typing import List, Set, Tuple

from .graph import Graph, Node, edge_key


def dijkstra(
    g: Graph,
    src: Node,
    dst: Node,
    failed_nodes: Set[Node] | None = None,
    failed_edges: Set[Tuple[Node, Node]] | None = None,
    trace: List[dict] | None = None,
) -> Tuple[float, List[Node], int]:
    """Return the minimum-cost path from ``src`` to ``dst``.

    Failed endpoints and missing endpoints are unreachable. If ``trace`` is
    supplied, visit and relaxation events are appended for replay.

    Complexity: **O((V + E) log V)** time and **O(V)** space.
    """
    failed_nodes = failed_nodes or set()
    failed_edges = failed_edges or set()
    if (
        src not in g.adj
        or dst not in g.adj
        or src in failed_nodes
        or dst in failed_nodes
    ):
        return float("inf"), [], 0

    dist = {node: float("inf") for node in g.nodes() if node not in failed_nodes}
    previous: dict[Node, Node | None] = {node: None for node in dist}
    dist[src] = 0.0
    heap: list[tuple[float, int, Node]] = [(0.0, 0, src)]
    sequence = 0
    explored = 0

    while heap:
        current_distance, _, u = heapq.heappop(heap)
        if current_distance != dist[u]:
            continue
        explored += 1
        if trace is not None:
            trace.append({
                "step": len(trace),
                "node": u,
                "kind": "visit",
                "frontier": [entry[2] for entry in heap],
                "dist": dict(dist),
            })
        if u == dst:
            break
        for v, weight in g.adj[u].items():
            if v in failed_nodes or edge_key(u, v) in failed_edges:
                continue
            candidate = current_distance + weight
            if candidate < dist[v]:
                dist[v] = candidate
                previous[v] = u
                sequence += 1
                heapq.heappush(heap, (candidate, sequence, v))
                if trace is not None:
                    trace.append({
                        "step": len(trace),
                        "node": v,
                        "kind": "relax",
                        "frontier": [entry[2] for entry in heap],
                        "dist": dict(dist),
                    })

    if dist[dst] == float("inf"):
        return float("inf"), [], explored
    path: list[Node] = []
    current: Node | None = dst
    while current is not None:
        path.append(current)
        current = previous[current]
    path.reverse()
    return dist[dst], path, explored
