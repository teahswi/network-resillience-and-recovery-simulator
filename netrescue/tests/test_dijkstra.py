"""Tests for Dijkstra implementation."""

import random

import networkx as nx
import pytest

from netrescue.core.graph import Graph
from netrescue.core.generator import random_graph
from netrescue.core.dijkstra import dijkstra

@pytest.mark.parametrize("seed", [0, 1, 42, 123])
def test_dijkstra_matches_networkx(seed):
    g = random_graph(num_nodes=15, edge_prob=0.3, seed=seed)
    nx_g = nx.Graph()
    for u, v, w in g.edges():
        nx_g.add_edge(u, v, weight=w)
    nodes = list(g.nodes())
    src, dst = nodes[0], nodes[-1]
    # compute using our implementation
    dist, path, _ = dijkstra(g, src, dst)
    # compute using networkx
    if nx.has_path(nx_g, src, dst):
        nx_path = nx.shortest_path(nx_g, source=src, target=dst, weight="weight")
        nx_dist = nx.shortest_path_length(nx_g, source=src, target=dst, weight="weight")
        assert dist == nx_dist
        assert path == nx_path
    else:
        assert dist == float('inf')
        assert path == []
