"""Benchmarking utilities for NetRescue.

Provides functions to time the core algorithms on graphs of various sizes and
compute the overall resilience score as described in the spec.
"""

from __future__ import annotations

import time
from typing import List, Dict, Any

import random


from .graph import Graph
from .generator import random_graph
from .bfs import bfs
from .dijkstra import dijkstra
from .critical import tarjan_articulation_and_bridges
from .failure import FailureSimulator

def _time_func(func, *args, repeats: int = 5, **kwargs) -> float:
    """Run *func* *repeats* times and return average elapsed ms."""
    total = 0.0
    for _ in range(repeats):
        start = time.perf_counter()
        func(*args, **kwargs)
        total += (time.perf_counter() - start) * 1000.0
    return total / repeats

def run_benchmarks(sizes: List[int] = [10, 50, 100, 500, 1000], repeats: int = 5, seed: int = 42) -> pd.DataFrame:
    """Benchmark BFS and Dijkstra on random connected graphs.

    Returns a pandas DataFrame with columns:
        size, algorithm, avg_time_ms, nodes_explored
    """
    rows = []
    for n in sizes:
        g = random_graph(num_nodes=n, edge_prob=0.2, seed=seed + n)
        # pick random src/dst
        nodes = list(g.nodes())
        src, dst = nodes[0], nodes[-1]
        # BFS
        bfs_time = _time_func(bfs, g, src, dst, repeats=repeats)
        _, _, bfs_visited = bfs(g, src, dst)
        rows.append({"size": n, "algorithm": "BFS", "avg_time_ms": bfs_time, "nodes_explored": len(bfs_visited)})
        # Dijkstra
        dijkstra_time = _time_func(dijkstra, g, src, dst, repeats=repeats)
        _, _, dijkstra_explored = dijkstra(g, src, dst)
        rows.append({"size": n, "algorithm": "Dijkstra", "avg_time_ms": dijkstra_time, "nodes_explored": dijkstra_explored})
    return pd.DataFrame(rows)

def compute_resilience_score(g: Graph, trials: int = 200, seed: int = 0) -> float:
    """Monte‑Carlo resilience score.

    The formula (from the spec) is:
        score = 100 * (0.5 * connectivity + 0.3 * (1 - crit_frac) + 0.2 * (1 / (1 + avg_cost_inflation)))
    where:
        * connectivity = fraction of node pairs still connected after random failures.
        * crit_frac = number_of_articulation_points / total_nodes.
        * avg_cost_inflation = average % increase in shortest‑path cost across all reachable pairs.
    """
    rnd = random.Random(seed)
    nodes = list(g.nodes())
    total_pairs = len(nodes) * (len(nodes) - 1) / 2
    connected_count = 0
    cost_inflation_sum = 0.0
    for _ in range(trials):
        sim = FailureSimulator()
        # random failure of 5% of nodes + 5% of edges
        node_fail = max(1, int(0.05 * len(nodes)))
        edge_fail = max(1, int(0.05 * sum(len(nb) for nb in g.adj.values()) // 2))
        sim.random_failure(g, node_count=node_fail, edge_count=edge_fail, seed=rnd.randint(0, 1_000_000))
        eff = sim.apply_to(g)
        # pick a random pair
        src, dst = rnd.sample(nodes, 2)
        old_dist, _, _ = dijkstra(g, src, dst)
        new_dist, _, _ = dijkstra(eff, src, dst)
        if new_dist != float('inf'):
            connected_count += 1
            if old_dist != float('inf') and old_dist > 0:
                cost_inflation_sum += (new_dist - old_dist) / old_dist
    connectivity = connected_count / trials
    crit_pts, _ = tarjan_articulation_and_bridges(g)
    crit_frac = len(crit_pts) / len(nodes) if nodes else 0
    avg_cost_inflation = (cost_inflation_sum / trials) if trials else 0
    score = 100 * (0.5 * connectivity + 0.3 * (1 - crit_frac) + 0.2 * (1 / (1 + avg_cost_inflation)))
    return round(score, 2)
