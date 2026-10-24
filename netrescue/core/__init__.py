"""NetRescue core package exports."""

from .graph import Graph
from .bfs import bfs
from .dfs import dfs_iterative, dfs_recursive, connected_components
from .dijkstra import dijkstra
from .critical import tarjan_articulation_and_bridges, vulnerability_report
from .failure import FailureSimulator
from .recovery import recover
from .generator import random_graph, ring, star, mesh, grid, tree
from .benchmark import run_benchmarks, compute_resilience_score
