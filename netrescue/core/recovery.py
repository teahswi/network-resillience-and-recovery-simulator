"""Recovery engine for NetRescue.

Given a source, destination and a :class:`~netrescue.core.failure.FailureSimulator`
instance, the engine computes the original optimal path, applies failures, checks
connectivity, and (if possible) computes alternative routes. The result is a
dictionary suitable for UI display.
"""

from __future__ import annotations

from typing import Dict, List, Tuple, Any, Set

from .graph import Graph, Node
from .dijkstra import dijkstra
from .bfs import bfs
from .failure import FailureSimulator

def recover(
    original_graph: Graph,
    src: Node,
    dst: Node,
    failure_sim: FailureSimulator,
) -> Dict[str, Any]:
    """Execute the full recovery workflow.

    Parameters
    ----------
    original_graph : Graph
        The pristine network topology.
    src, dst : Node
        Source and destination routers.
    failure_sim : FailureSimulator
        Holds the current failure set.

    Returns
    -------
    dict
        Keys:
        * ``status`` – one of ``RECOVERED``, ``DEGRADED``, ``DISCONNECTED``,
          ``UNAFFECTED``.
        * ``old_path`` – list of nodes before failure (may be empty if unreachable).
        * ``old_cost`` – total weight of ``old_path`` (``inf`` if unreachable).
        * ``new_path`` – list of nodes after failure (empty if disconnected).
        * ``new_cost`` – total weight after failure.
        * ``cost_increase_pct`` – ``None`` if not applicable.
        * ``hop_delta`` – difference in hop count between new and old path.
        * ``old_explored`` – nodes explored by Dijkstra for the original path.
        * ``new_explored`` – nodes explored by Dijkstra after failure.
        * ``affected`` – whether the original path contains a failed node/edge.
        * ``components`` – list of sets of nodes for each island when disconnected.
    """
    # 1. Original best path (ignoring failures)
    old_cost, old_path, old_explored = dijkstra(original_graph, src, dst)
    # 2. Apply failures to get effective graph
    eff_graph = failure_sim.apply_to(original_graph)
    # 3. Did the original path get impacted?
    affected = False
    if old_path:
        # check if any node or edge of old_path is failed
        failed_nodes = failure_sim.failed_nodes
        failed_edges = failure_sim.failed_edges
        for n in old_path:
            if n in failed_nodes:
                affected = True
                break
        if not affected:
            # check edges
            for u, v in zip(old_path, old_path[1:]):
                e = (u, v) if u < v else (v, u)
                if e in failed_edges:
                    affected = True
                    break
    # 4. Connectivity check (BFS) on effective graph
    reachable, bfs_path, bfs_visited = bfs(eff_graph, src, dst)
    if not reachable:
        # Disconnected – compute islands via DFS on effective graph
        from .dfs import connected_components
        comps = connected_components(eff_graph)
        return {
            "status": "DISCONNECTED",
            "old_path": old_path,
            "old_cost": old_cost,
            "new_path": [],
            "new_cost": float('inf'),
            "cost_increase_pct": None,
            "hop_delta": None,
            "old_explored": old_explored,
            "new_explored": None,
            "affected": affected,
            "components": comps,
        }
    # 5. Connected – compute alternative routes
    new_cost, new_path, new_explored = dijkstra(eff_graph, src, dst)
    # Compute metrics
    cost_increase = None
    hop_delta = None
    if old_path and new_path:
        if old_cost != float('inf'):
            if new_cost == float('inf'):
                cost_increase = None
            else:
                cost_increase = ((new_cost - old_cost) / old_cost) * 100 if old_cost > 0 else None
        hop_delta = len(new_path) - len(old_path)
    status = "UNAFFECTED" if not affected else ("RECOVERED" if old_path == new_path else "DEGRADED")
    return {
        "status": status,
        "old_path": old_path,
        "old_cost": old_cost,
        "new_path": new_path,
        "new_cost": new_cost,
        "cost_increase_pct": cost_increase,
        "hop_delta": hop_delta,
        "old_explored": old_explored,
        "new_explored": new_explored,
        "affected": affected,
        "components": None,
    }
