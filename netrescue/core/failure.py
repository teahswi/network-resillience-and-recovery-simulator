"""Failure simulation, replay, and cascade modeling."""

from __future__ import annotations

from dataclasses import dataclass
import random
from typing import Any, Iterable

from .graph import Graph, Node, edge_key


@dataclass(frozen=True)
class Step:
    """Immutable failure snapshot recorded in the timeline."""

    label: str
    nodes: frozenset[Node]
    edges: frozenset[tuple[Node, Node]]


class FailureSimulator:
    """Manage validated failures without mutating the source graph.

    Complexity of applying a state is O(V + E). When a graph is supplied at
    construction, mutation methods validate node and edge identifiers.
    """

    def __init__(self, graph: Graph | None = None) -> None:
        self.graph = graph
        self._failed_nodes: frozenset[Node] = frozenset()
        self._failed_edges: frozenset[tuple[Node, Node]] = frozenset()
        self._steps: list[Step] = [
            Step("initial", self._failed_nodes, self._failed_edges)
        ]
        self._cursor = 0

    @property
    def failed_nodes(self) -> frozenset[Node]:
        """Currently failed nodes as an immutable set."""
        return self._failed_nodes

    @property
    def failed_edges(self) -> frozenset[tuple[Node, Node]]:
        """Currently failed edges as canonical immutable keys."""
        return self._failed_edges

    def _validate_node(self, node: Node, graph: Graph | None = None) -> None:
        target = graph or self.graph
        if target is not None and node not in target.adj:
            raise KeyError(f"Unknown node: {node!r}")

    def _validate_edge(self, u: Node, v: Node, graph: Graph | None = None) -> tuple[Node, Node]:
        target = graph or self.graph
        key = edge_key(u, v)
        if target is not None and not target.has_edge(u, v):
            raise KeyError(f"Unknown edge: {u!r}, {v!r}")
        return key

    def _record(self, label: str) -> None:
        self._steps = self._steps[: self._cursor + 1]
        self._steps.append(Step(label, self._failed_nodes, self._failed_edges))
        self._cursor += 1

    def fail_node(self, node: Node) -> None:
        """Fail a node and record one timeline step in O(1)."""
        self._validate_node(node)
        self._failed_nodes = self._failed_nodes | {node}
        self._record(f"fail node {node}")

    def restore_node(self, node: Node) -> None:
        """Restore a node and record one timeline step in O(1)."""
        self._validate_node(node)
        self._failed_nodes = self._failed_nodes - {node}
        self._record(f"restore node {node}")

    def fail_edge(self, u: Node, v: Node) -> None:
        """Fail an existing edge and record one timeline step in O(1)."""
        key = self._validate_edge(u, v)
        self._failed_edges = self._failed_edges | {key}
        self._record(f"fail edge {key[0]}-{key[1]}")

    def restore_edge(self, u: Node, v: Node) -> None:
        """Restore an edge and record one timeline step in O(1)."""
        key = self._validate_edge(u, v)
        self._failed_edges = self._failed_edges - {key}
        self._record(f"restore edge {key[0]}-{key[1]}")

    def restore_all(self) -> None:
        """Restore every failure and record a timeline step in O(1)."""
        self._failed_nodes = frozenset()
        self._failed_edges = frozenset()
        self._record("restore all")

    reset = restore_all

    def undo(self) -> None:
        """Move one step backward in O(1)."""
        if self._cursor > 0:
            self._cursor -= 1
            self._load(self._steps[self._cursor])

    def redo(self) -> None:
        """Move one step forward in O(1)."""
        if self._cursor + 1 < len(self._steps):
            self._cursor += 1
            self._load(self._steps[self._cursor])

    def jump(self, index: int) -> None:
        """Jump to a timeline index in O(1)."""
        if not 0 <= index < len(self._steps):
            raise IndexError("Timeline index out of range.")
        self._cursor = index
        self._load(self._steps[index])

    def _load(self, step: Step) -> None:
        self._failed_nodes = step.nodes
        self._failed_edges = step.edges

    def timeline(self) -> list[str]:
        """Return timeline labels in O(T)."""
        return [step.label for step in self._steps]

    def get_history(self) -> list[dict[str, Any]]:
        """Return compatibility history snapshots in O(T)."""
        return [
            {"label": step.label, "nodes": set(step.nodes), "edges": set(step.edges)}
            for step in self._steps
        ]

    def random_failure(
        self,
        graph: Graph | None = None,
        node_count: int = 0,
        edge_count: int = 0,
        seed: int | None = None,
        protect: frozenset[Node] = frozenset(),
    ) -> None:
        """Apply one reproducible random-failure step in O(V + E)."""
        target = graph or self.graph
        if target is None:
            raise ValueError("A graph is required for random failures.")
        rnd = random.Random(seed)
        nodes = [n for n in target.nodes() if n not in protect]
        edges = [edge_key(u, v) for u, v, _ in target.edges()]
        selected_nodes = frozenset(rnd.sample(nodes, min(node_count, len(nodes))))
        selected_edges = frozenset(rnd.sample(edges, min(edge_count, len(edges))))
        self._failed_nodes = self._failed_nodes | selected_nodes
        self._failed_edges = self._failed_edges | selected_edges
        self._record("random failure")

    def cascade(
        self,
        triggers: Iterable[Node],
        capacity_factor: float = 1.5,
        seed: int | None = None,
    ) -> list[list[Node]]:
        """Simulate deterministic load redistribution until stable.

        Complexity: O(V·(V + E)) in the worst case and O(V + E) space.
        """
        if self.graph is None:
            raise ValueError("A graph is required for cascades.")
        if capacity_factor <= 0:
            raise ValueError("capacity_factor must be positive.")
        for node in triggers:
            self._validate_node(node)
        del seed  # Reserved for future stochastic cascade models.
        load = {node: float(len(list(self.graph.neighbors(node)))) for node in self.graph.nodes()}
        capacity = {node: capacity_factor * value for node, value in load.items()}
        failed = set(self._failed_nodes)
        rounds: list[list[Node]] = []
        pending = set(triggers)
        while pending:
            newly_failed = sorted(pending - failed, key=str)
            if not newly_failed:
                break
            failed.update(newly_failed)
            redistributed: dict[Node, float] = {}
            for node in newly_failed:
                neighbours = [
                    neighbour for neighbour in self.graph.neighbors(node)
                    if neighbour not in failed
                ]
                share = load[node] / len(neighbours) if neighbours else 0.0
                for neighbour in neighbours:
                    redistributed[neighbour] = redistributed.get(neighbour, 0.0) + share
            for node, amount in redistributed.items():
                load[node] += amount
            pending = {
                node for node, value in load.items()
                if node not in failed and value > capacity[node]
            }
            rounds.append(newly_failed)
            self._failed_nodes = frozenset(failed)
            self._record(f"cascade round {len(rounds)}")
        return rounds

    def apply(self, graph: Graph | None = None) -> Graph:
        """Return a failed copy of the graph in O(V + E)."""
        target = graph or self.graph
        if target is None:
            raise ValueError("A graph is required to apply failures.")
        result = target.copy()
        for node in self._failed_nodes:
            result.remove_node(node)
        for u, v in self._failed_edges:
            result.remove_edge(u, v)
        return result

    apply_to = apply
