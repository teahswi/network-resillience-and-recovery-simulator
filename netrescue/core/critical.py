"""Tarjan critical-router and critical-link analysis."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Set, Tuple

from .dfs import connected_components
from .graph import Graph, Node, edge_key


def tarjan_articulation_and_bridges(
    g: Graph,
) -> Tuple[Set[Node], Set[Tuple[Node, Node]]]:
    """Return articulation points and bridges using iterative Tarjan DFS.

    Complexity: **O(V + E)** time and **O(V)** auxiliary space.
    """
    discovery: Dict[Node, int] = {}
    low: Dict[Node, int] = {}
    parent: Dict[Node, Node | None] = {}
    articulation: Set[Node] = set()
    bridges: Set[Tuple[Node, Node]] = set()
    timestamp = 0

    for root in g.nodes():
        if root in discovery:
            continue
        timestamp += 1
        discovery[root] = low[root] = timestamp
        parent[root] = None
        child_count: Dict[Node, int] = {root: 0}
        stack: list[tuple[Node, List[Node], int]] = [
            (root, list(g.neighbors(root)), 0)
        ]

        while stack:
            u, neighbours, index = stack[-1]
            if index < len(neighbours):
                v = neighbours[index]
                stack[-1] = (u, neighbours, index + 1)
                if v == parent[u]:
                    continue
                if v not in discovery:
                    parent[v] = u
                    child_count[u] = child_count.get(u, 0) + 1
                    child_count[v] = 0
                    timestamp += 1
                    discovery[v] = low[v] = timestamp
                    stack.append((v, list(g.neighbors(v)), 0))
                else:
                    low[u] = min(low[u], discovery[v])
                continue

            stack.pop()
            p = parent[u]
            if p is None:
                if child_count.get(u, 0) > 1:
                    articulation.add(u)
                continue
            low[p] = min(low[p], low[u])
            if low[u] > discovery[p]:
                bridges.add(edge_key(p, u))
            if parent[p] is not None and low[u] >= discovery[p]:
                articulation.add(p)

    return articulation, bridges


@dataclass(frozen=True)
class Impact:
    """Ranked impact of removing a critical router or link."""

    kind: str
    id: Node | Tuple[Node, Node]
    nodes_cut_off: int
    islands_created: int


def vulnerability_report(g: Graph) -> List[Impact]:
    """Return ranked router and bridge impacts.

    Router impact is measured only within the router's original connected
    component. Link impact is the smaller side of the split component.

    Complexity: **O(k·(V + E))** time for ``k`` critical items and O(V + E)
    space.
    """
    articulation, bridges = tarjan_articulation_and_bridges(g)
    report: List[Impact] = []
    before_components = connected_components(g)
    component_by_node = {
        node: component for component in before_components for node in component
    }

    for node in articulation:
        original = component_by_node[node]
        reduced = g.copy()
        reduced.remove_node(node)
        pieces = [
            component & original
            for component in connected_components(reduced)
            if component & original
        ]
        largest = max((len(piece) for piece in pieces), default=0)
        report.append(
            Impact(
                kind="router",
                id=node,
                nodes_cut_off=max(0, len(original) - 1 - largest),
                islands_created=max(0, len(pieces) - 1),
            )
        )

    for u, v in bridges:
        original = component_by_node[u]
        reduced = g.copy()
        reduced.remove_edge(u, v)
        pieces = [
            component & original
            for component in connected_components(reduced)
            if component & original
        ]
        side_sizes = sorted((len(piece) for piece in pieces), reverse=True)
        report.append(
            Impact(
                kind="link",
                id=edge_key(u, v),
                nodes_cut_off=side_sizes[-1] if len(side_sizes) > 1 else 0,
                islands_created=max(0, len(pieces) - 1),
            )
        )

    report.sort(
        key=lambda impact: (
            -impact.nodes_cut_off,
            -impact.islands_created,
            str(impact.id),
        )
    )
    return report
