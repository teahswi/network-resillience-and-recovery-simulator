"""Recovery analysis for failed network routes."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any, Literal

from .bfs import bfs
from .dfs import connected_components
from .dijkstra import dijkstra
from .failure import FailureSimulator
from .graph import Graph, Node, edge_key

Status = Literal["UNAFFECTED", "RECOVERED", "DEGRADED", "DISCONNECTED"]


@dataclass(frozen=True)
class RecoveryReport:
    """Complete route and connectivity analysis."""

    status: Status
    reason: str | None
    affected: bool
    old_path: list[Node]
    old_cost: float
    old_hops: int
    dijkstra_path: list[Node]
    dijkstra_cost: float
    dijkstra_hops: int
    dijkstra_explored: int
    bfs_path: list[Node]
    bfs_hops: int
    bfs_explored: int
    cost_increase_pct: float | None
    extra_hops: int | None
    components: list[list[Node]]
    src_island_size: int | None


class RecoveryEngine:
    """Compare pristine and failed-network routes."""

    def __init__(self, graph: Graph, degraded_threshold_pct: float = 25.0) -> None:
        if degraded_threshold_pct < 0:
            raise ValueError("degraded_threshold_pct must be non-negative.")
        self.graph = graph
        self.degraded_threshold_pct = degraded_threshold_pct

    def analyze(
        self,
        src: Node,
        dst: Node,
        failed_nodes: frozenset[Node] | set[Node] = frozenset(),
        failed_edges: frozenset[tuple[Node, Node]] | set[tuple[Node, Node]] = frozenset(),
    ) -> RecoveryReport:
        """Analyze route recovery in O(V + E) plus shortest-path costs."""
        old_cost, old_path, _ = dijkstra(self.graph, src, dst)
        old_hops = max(0, len(old_path) - 1)
        failed_nodes = frozenset(failed_nodes)
        failed_edges = frozenset(edge_key(u, v) for u, v in failed_edges)
        affected = any(node in failed_nodes for node in old_path) or any(
            edge_key(u, v) in failed_edges
            for u, v in zip(old_path, old_path[1:])
        )
        if src in failed_nodes or dst in failed_nodes:
            return self._disconnected(
                old_path, old_cost, old_hops, affected, "endpoint_failed", []
            )

        simulator = FailureSimulator(self.graph)
        simulator._failed_nodes = failed_nodes
        simulator._failed_edges = failed_edges
        effective = simulator.apply()
        reachable, bfs_path, bfs_visited = bfs(effective, src, dst)
        if not reachable:
            components = [
                sorted(component, key=str)
                for component in connected_components(effective)
            ]
            island_size = next(
                (len(component) for component in components if src in component),
                None,
            )
            return self._disconnected(
                old_path, old_cost, old_hops, affected, "no_path",
                components, island_size, bfs_visited
            )

        new_cost, new_path, explored = dijkstra(effective, src, dst)
        new_hops = max(0, len(new_path) - 1)
        increase = None
        if old_cost != float("inf") and old_cost > 0:
            increase = ((new_cost - old_cost) / old_cost) * 100
        extra_hops = new_hops - old_hops
        if not affected:
            status: Status = "UNAFFECTED"
        elif increase is not None and increase <= self.degraded_threshold_pct:
            status = "RECOVERED"
        else:
            status = "DEGRADED"
        return RecoveryReport(
            status, None, affected, old_path, old_cost, old_hops,
            new_path, new_cost, new_hops, explored,
            bfs_path, max(0, len(bfs_path) - 1), len(bfs_visited),
            increase, extra_hops, [], None,
        )

    def _disconnected(
        self,
        old_path: list[Node],
        old_cost: float,
        old_hops: int,
        affected: bool,
        reason: str,
        components: list[list[Node]],
        src_island_size: int | None = None,
        bfs_visited: list[Node] | None = None,
    ) -> RecoveryReport:
        return RecoveryReport(
            "DISCONNECTED", reason, affected, old_path, old_cost, old_hops,
            [], float("inf"), 0, 0, [], 0, len(bfs_visited or []),
            None, None, components, src_island_size,
        )


def recover(
    original_graph: Graph,
    src: Node,
    dst: Node,
    failure_sim: FailureSimulator,
) -> dict[str, Any]:
    """Compatibility wrapper returning a dictionary report."""
    result = asdict(
        RecoveryEngine(original_graph).analyze(
            src, dst, failure_sim.failed_nodes, failure_sim.failed_edges
        )
    )
    result["new_path"] = result["dijkstra_path"]
    result["new_cost"] = result["dijkstra_cost"]
    result["new_explored"] = result["dijkstra_explored"]
    result["hop_delta"] = result["extra_hops"]
    result["old_explored"] = None
    return result
