"""Tests for Tarjan articulation points and bridges.

We cross‑check with NetworkX's implementations.
"""

import networkx as nx
import pytest

from netrescue.core.graph import Graph
from netrescue.core.generator import random_graph
from netrescue.core.critical import tarjan_articulation_and_bridges

@pytest.mark.parametrize("seed", [0, 1, 42])
def test_tarjan_matches_networkx(seed):
    g = random_graph(num_nodes=12, edge_prob=0.25, seed=seed)
    nx_g = nx.Graph()
    for u, v, w in g.edges():
        nx_g.add_edge(u, v)
    ap, bridges = tarjan_articulation_and_bridges(g)
    nx_ap = set(nx.articulation_points(nx_g))
    nx_br = set(tuple(sorted(e)) for e in nx.bridges(nx_g))
    assert set(ap) == nx_ap
    assert set(tuple(sorted(e)) for e in bridges) == nx_br
