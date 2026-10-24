"""Performance and resilience measurements for NetRescue."""

from __future__ import annotations

from dataclasses import dataclass
import random
import time
from itertools import combinations
from statistics import mean, stdev
from typing import Any, Callable, Sequence

from .bfs import bfs
from .critical import tarjan_articulation_and_bridges
from .dfs import connected_components, dfs_iterative
from .dijkstra import dijkstra
from .failure import FailureSimulator
from .generator import random_graph
from .graph import Graph
from .recovery import RecoveryEngine

RESILIENCE_WEIGHTS = {
    "connectivity": 0.5,
    "critical": 0.3,
    "inflation": 0.2,
}


@dataclass(frozen=True)
class ResilienceReport:
    """Resilience score and its normalized contribution terms."""

    score: float
    connectivity: float
    critical_term: float
    inflation_term: float
    avg_inflation: float
    n_articulation: int
    n_nodes: int
    trials: int
    weights: dict[str, float]

    def __getitem__(self, key: str) -> Any:
        """Provide mapping-style compatibility for UI consumers."""
        return getattr(self, key)


def _measure(
    function: Callable[..., Any],
    *args: Any,
    repeats: int,
) -> tuple[float, float]:
    """Return average and standard-deviation runtime in milliseconds."""
    samples: list[float] = []
    for _ in range(repeats):
        started = time.perf_counter()
        function(*args)
        samples.append((time.perf_counter() - started) * 1000)
    return mean(samples), stdev(samples) if len(samples) > 1 else 0.0


def run_benchmarks(
    sizes: Sequence[int] = (10, 50, 100, 500, 1000),
    repeats: int = 20,
    pairs: int = 5,
    avg_degree: float = 4,
    seed: int = 42,
) -> list[dict[str, Any]]:
    """Benchmark BFS, DFS, Dijkstra, and Tarjan.

    Complexity depends on the requested graph sizes and repetitions.
    """
    if repeats <= 0 or pairs <= 0:
        raise ValueError("repeats and pairs must be positive.")
    rng = random.Random(seed)
    rows: list[dict[str, Any]] = []
    for size in sizes:
        if size <= 0:
            raise ValueError("sizes must contain positive integers.")
        graph = random_graph(size, avg_degree=avg_degree, seed=rng.randrange(1_000_000))
        nodes = list(graph.nodes())
        selected_pairs = list(combinations(nodes, 2))[:pairs]
        for algorithm in ("BFS", "DFS", "Dijkstra", "Tarjan"):
            times: list[float] = []
            explored: list[int] = []
            costs: list[float] = []
            hops: list[int] = []
            successes: list[bool] = []
            for src, dst in selected_pairs:
                for _ in range(repeats):
                    started = time.perf_counter()
                    if algorithm == "BFS":
                        success, path, visited = bfs(graph, src, dst)
                        node_count = len(visited)
                        cost = float(len(path) - 1) if success else float("inf")
                    elif algorithm == "DFS":
                        order = dfs_iterative(graph, src)
                        success = dst in order
                        path = order[: order.index(dst) + 1] if success else []
                        node_count = len(order)
                        cost = float(len(path) - 1) if success else float("inf")
                    elif algorithm == "Dijkstra":
                        cost, path, node_count = dijkstra(graph, src, dst)
                        success = bool(path)
                    else:
                        tarjan_articulation_and_bridges(graph)
                        success, path, node_count = True, [], len(nodes)
                        cost = 0.0
                    times.append((time.perf_counter() - started) * 1000)
                    explored.append(node_count)
                    costs.append(cost)
                    hops.append(max(0, len(path) - 1))
                    successes.append(success)
            rows.append({
                "size": size,
                "algorithm": algorithm,
                "avg_time_ms": mean(times) if times else 0.0,
                "std_ms": stdev(times) if len(times) > 1 else 0.0,
                "nodes_explored": mean(explored) if explored else 0,
                "path_cost": mean(costs) if costs else float("inf"),
                "hops": mean(hops) if hops else 0,
                "success": all(successes) if successes else False,
            })
    return rows


def recovery_rate(
    graph: Graph,
    trials: int = 200,
    node_failures: int = 1,
    edge_failures: int = 1,
    seed: int = 0,
) -> dict[str, int | float]:
    """Estimate route outcomes over reproducible random failure trials."""
    if trials < 0:
        raise ValueError("trials must be non-negative.")
    nodes = list(graph.nodes())
    if len(nodes) < 2:
        return {"rate": 0.0, "recovered": 0, "degraded": 0,
                "disconnected": 0, "unaffected": 0}
    rng = random.Random(seed)
    counts = {"RECOVERED": 0, "DEGRADED": 0, "DISCONNECTED": 0, "UNAFFECTED": 0}
    for _ in range(trials):
        src, dst = rng.sample(nodes, 2)
        simulator = FailureSimulator(graph)
        simulator.random_failure(
            graph, node_failures, edge_failures,
            seed=rng.randrange(1_000_000), protect=frozenset({src, dst}),
        )
        status = RecoveryEngine(graph).analyze(
            src, dst, simulator.failed_nodes, simulator.failed_edges
        ).status
        counts[status] += 1
    recovered = counts["RECOVERED"]
    return {
        "rate": recovered / trials if trials else 0.0,
        "recovered": recovered,
        "degraded": counts["DEGRADED"],
        "disconnected": counts["DISCONNECTED"],
        "unaffected": counts["UNAFFECTED"],
    }


def compute_resilience(
    graph: Graph,
    trials: int = 200,
    seed: int = 0,
    node_frac: float = 0.05,
    edge_frac: float = 0.05,
) -> ResilienceReport:
    """Compute resilience from connectivity, criticality, and inflation.

    Complexity: O(trials · (V + E) · shortest-path work).
    """
    if trials < 0 or not 0 <= node_frac <= 1 or not 0 <= edge_frac <= 1:
        raise ValueError("Invalid trials or failure fractions.")
    nodes = list(graph.nodes())
    n = len(nodes)
    rng = random.Random(seed)
    total_connectivity = 0.0
    inflations: list[float] = []
    node_failures = int(node_frac * n)
    edge_count = sum(1 for _ in graph.edges())
    edge_failures = int(edge_frac * edge_count)
    for _ in range(trials):
        simulator = FailureSimulator(graph)
        simulator.random_failure(
            graph, node_failures, edge_failures,
            seed=rng.randrange(1_000_000),
        )
        damaged = simulator.apply()
        alive = list(damaged.nodes())
        possible = len(alive) * (len(alive) - 1) // 2
        connected = sum(
            len(component) * (len(component) - 1) // 2
            for component in connected_components(damaged)
        )
        total_connectivity += connected / possible if possible else 0.0
        for src, dst in rng.sample(
            list(combinations(alive, 2)), min(20, possible)
        ):
            old_cost, _, _ = dijkstra(graph, src, dst)
            new_cost, _, _ = dijkstra(damaged, src, dst)
            if old_cost not in (0.0, float("inf")) and new_cost != float("inf"):
                inflations.append(max(0.0, (new_cost - old_cost) / old_cost))
    connectivity = total_connectivity / trials if trials else 0.0
    avg_inflation = mean(inflations) if inflations else 0.0
    articulation, _ = tarjan_articulation_and_bridges(graph)
    critical_term = 1.0 - len(articulation) / n if n else 1.0
    inflation_term = 1.0 / (1.0 + avg_inflation)
    score = 100 * (
        RESILIENCE_WEIGHTS["connectivity"] * connectivity
        + RESILIENCE_WEIGHTS["critical"] * critical_term
        + RESILIENCE_WEIGHTS["inflation"] * inflation_term
    )
    return ResilienceReport(
        round(score, 2), connectivity, critical_term, inflation_term,
        avg_inflation, len(articulation), n, trials, RESILIENCE_WEIGHTS.copy(),
    )


def compute_resilience_score(graph: Graph, trials: int = 200, seed: int = 0) -> float:
    """Return only the numeric resilience score for legacy callers."""
    return compute_resilience(graph, trials=trials, seed=seed).score


def trap_graph() -> Graph:
    """Return a graph where BFS minimizes hops but not weighted cost."""
    graph = Graph()
    graph.add_edge("A", "B", 50)
    graph.add_edge("B", "D", 50)
    graph.add_edge("A", "C", 5)
    graph.add_edge("C", "E", 5)
    graph.add_edge("E", "F", 5)
    graph.add_edge("F", "D", 5)
    return graph
