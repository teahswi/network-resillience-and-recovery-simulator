"""Tarjan's algorithm for articulation points and bridges.

The implementation works on the custom :class:`netrescue.core.graph.Graph` and
returns two sets:
    * articulation points (critical routers)
    * bridges (critical links)
Both are computed in a single DFS traversal.
"""

from __future__ import annotations

from typing import Set, Tuple, List, Dict, Any

from .graph import Graph, Node

def tarjan_articulation_and_bridges(g: Graph) -> Tuple[Set[Node], Set[Tuple[Node, Node]]]:
    """Return articulation points and bridges of an undirected graph.

    Parameters
    ----------
    g : Graph
        The graph to analyse.

    Returns
    -------
    articulation_points : set of Node
        Nodes whose removal increases the number of connected components.
    bridges : set of (Node, Node)
        Undirected edges whose removal increases the number of connected components.

    Complexity
    ----------
    **Time:** O(V + E)
    **Space:** O(V) for discovery/low arrays and recursion stack.
    """
    time = 0
    disc: Dict[Node, int] = {}
    low: Dict[Node, int] = {}
    parent: Dict[Node, Node | None] = {}
    articulation: Set[Node] = set()
    bridges: Set[Tuple[Node, Node]] = set()

    def dfs(u: Node):
        nonlocal time
        time += 1
        disc[u] = low[u] = time
        children = 0
        for v in g.neighbors(u):
            if v not in disc:
                parent[v] = u
                children += 1
                dfs(v)
                low[u] = min(low[u], low[v])
                # Bridge condition
                if low[v] > disc[u]:
                    bridges.add((u, v) if u < v else (v, u))
                # Articulation point condition (non‑root)
                if parent.get(u) is not None and low[v] >= disc[u]:
                    articulation.add(u)
            elif v != parent.get(u):
                # Back edge
                low[u] = min(low[u], disc[v])
        # Root articulation point check
        if parent.get(u) is None and children > 1:
            articulation.add(u)

    for node in g.nodes():
        if node not in disc:
            parent[node] = None
            dfs(node)
    return articulation, bridges

def vulnerability_report(g: Graph) -> List[Tuple[Node, int]]:
    """Rank articulation points by the number of nodes that become isolated
    when the point is removed.

    Returns a list of ``(node, lost_nodes)`` sorted descending by ``lost_nodes``.
    """
    articulation, _ = tarjan_articulation_and_bridges(g)
    report: List[Tuple[Node, int]] = []
    for ap in articulation:
        # Remove the articulation point and count size of the component that
        # contained it (excluding the removed node).
        g_copy = g.copy()
        g_copy.remove_node(ap)
        # After removal, compute the size of the largest component – the nodes
        # that got cut off are total_nodes - size_of_largest_component - 1 (the removed node).
        from .dfs import connected_components
        comps = connected_components(g_copy)
        if not comps:
            lost = len(g) - 1
        else:
            largest = max(len(c) for c in comps)
            lost = len(g) - 1 - largest
        report.append((ap, lost))
    report.sort(key=lambda x: x[1], reverse=True)
    return report
