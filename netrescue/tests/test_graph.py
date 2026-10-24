"""Tests for the Graph class and generators."""

import json

import pytest

from netrescue.core.graph import Graph
from netrescue.core.generator import random_graph, ring, star, mesh, grid, tree

def test_add_remove_node_edge():
    g = Graph()
    g.add_node("A")
    assert "A" in g.nodes()
    g.add_edge("A", "B", 5)
    assert g.has_edge("A", "B")
    assert g.adj["A"]["B"] == 5
    g.remove_edge("A", "B")
    assert not g.has_edge("A", "B")
    g.remove_node("A")
    assert "A" not in g.nodes()

def test_json_roundtrip():
    g = random_graph(5, edge_prob=0.3, seed=1)
    data = g.to_dict()
    g2 = Graph.from_dict(data)
    assert g.to_dict() == g2.to_dict()

def test_generators_nonempty():
    for gen in [ring, star, mesh, grid, tree]:
        if gen.__name__ == "grid":
            g = gen(2, 3)
        else:
            g = gen(5)
        assert len(g) > 0
        # at least one edge for non‑trivial generators
        assert sum(len(nbrs) for nbrs in g.adj.values()) > 0
