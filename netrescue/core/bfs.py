"""Breadth‑First Search utilities for NetRescue.

All functions operate on :class:`netrescue.core.graph.Graph` and ignore any
failed nodes/edges – the caller should provide a filtered graph if needed.
"""

from __future__ import annotations

from collections import deque
from typing import List, Tuple, Any

from .graph import Graph, Node

def bfs(g: Graph, src: Node, dst: Node) -> Tuple[bool, List[Node], List[Node]]:
    """Breadth‑first search.

    Returns
    -------
    reachable: bool
        Whether ``dst`` is reachable from ``src``.
    path: list of nodes
        The shortest‑hop path from ``src`` to ``dst`` (empty if unreachable).
    visited_order: list of nodes
        Order in which nodes were dequeued (useful for UI replay).

    Complexity: **O(V + E)** time, **O(V)** space.
    """
    if src not in g.adj or dst not in g.adj:
        return False, [], []
    visited: set[Node] = set([src])
    parent: dict[Node, Node | None] = {src: None}
    queue = deque([src])
    visited_order: List[Node] = []
    while queue:
        u = queue.popleft()
        visited_order.append(u)
        if u == dst:
            # reconstruct path
            path: List[Node] = []
            cur: Node | None = dst
            while cur is not None:
                path.append(cur)
                cur = parent[cur]
            path.reverse()
            return True, path, visited_order
        for v in g.neighbors(u):
            if v not in visited:
                visited.add(v)
                parent[v] = u
                queue.append(v)
    return False, [], visited_order
