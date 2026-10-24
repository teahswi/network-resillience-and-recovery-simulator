"""Depth‑first Search utilities for NetRescue.

Both an iterative and a recursive implementation are provided. The functions
operate on :class:`netrescue.core.graph.Graph` and ignore any failure sets – the
caller should supply a filtered copy if needed.
"""

from __future__ import annotations

from typing import List, Set

from .graph import Graph, Node

def dfs_iterative(g: Graph, start: Node) -> List[Node]:
    """Iterative DFS that returns the order of node visitation.

    Complexity: **O(V + E)** time, **O(V)** space.
    """
    if start not in g.adj:
        return []
    visited: Set[Node] = set()
    stack: List[Node] = [start]
    order: List[Node] = []
    while stack:
        u = stack.pop()
        if u in visited:
            continue
        visited.add(u)
        order.append(u)
        # push neighbours in reverse order for deterministic output
        for v in sorted(g.neighbors(u), reverse=True):
            if v not in visited:
                stack.append(v)
    return order

def dfs_recursive(g: Graph, start: Node, visited: Set[Node] | None = None, order: List[Node] | None = None) -> List[Node]:
    """Recursive DFS that returns the visitation order.

    Parameters
    ----------
    g : Graph
        The graph.
    start : Node
        Starting node.
    visited : set, optional
        Internal set used during recursion.
    order : list, optional
        Internal list collecting visitation order.

    Returns
    -------
    list of nodes
        Visitation order. Python's recursion limit applies; use
        :func:`dfs_iterative` for deep graphs.

    Complexity: **O(V + E)** time and **O(V)** space, excluding call-stack
    depth.
    """
    if visited is None:
        visited = set()
    if order is None:
        order = []
    if start not in g.adj or start in visited:
        return order
    visited.add(start)
    order.append(start)
    for v in sorted(g.neighbors(start)):
        if v not in visited:
            dfs_recursive(g, v, visited, order)
    return order

def connected_components(g: Graph) -> List[Set[Node]]:
    """Return a list of sets, each set containing the nodes of a connected component.

    Uses iterative DFS internally.
    Complexity: **O(V + E)**.
    """
    seen: Set[Node] = set()
    components: List[Set[Node]] = []
    for node in g.nodes():
        if node in seen:
            continue
        comp: Set[Node] = set()
        stack: List[Node] = [node]
        while stack:
            u = stack.pop()
            if u in seen:
                continue
            seen.add(u)
            comp.add(u)
            for v in g.neighbors(u):
                if v not in seen:
                    stack.append(v)
        components.append(comp)
    components.sort(key=lambda component: (-len(component), min(map(str, component))))
    return components
