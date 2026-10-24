"""Tests for BFS and DFS utilities."""

import pytest

from netrescue.core.graph import Graph
from netrescue.core.bfs import bfs
from netrescue.core.dfs import dfs_iterative, dfs_recursive, connected_components

def build_simple_graph():
    g = Graph()
    edges = [
        ("A", "B", 1),
        ("B", "C", 1),
        ("C", "D", 1),
        ("E", "F", 1),
    ]
    for u, v, w in edges:
        g.add_edge(u, v, w)
    return g

def test_bfs_reachable():
    g = build_simple_graph()
    reachable, path, visited = bfs(g, "A", "D")
    assert reachable
    assert path == ["A", "B", "C", "D"]
    assert visited[0] == "A"

def test_bfs_unreachable():
    g = build_simple_graph()
    reachable, path, visited = bfs(g, "A", "F")
    assert not reachable
    assert path == []

def test_dfs_iterative():
    g = build_simple_graph()
    order = dfs_iterative(g, "A")
    # Order should contain A,B,C,D (order of neighbours sorted descending)
    assert set(order) == {"A", "B", "C", "D"}

def test_dfs_recursive():
    g = build_simple_graph()
    order = dfs_recursive(g, "A")
    assert set(order) == {"A", "B", "C", "D"}

def test_connected_components():
    g = build_simple_graph()
    comps = connected_components(g)
    # Should be two components: {A,B,C,D} and {E,F}
    comp_sets = [set(c) for c in comps]
    assert {"A", "B", "C", "D"} in comp_sets
    assert {"E", "F"} in comp_sets
