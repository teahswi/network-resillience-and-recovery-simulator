# NetRescue

NetRescue is a Python 3.10+ network-resilience simulator built around
hand-written graph algorithms. It models weighted undirected networks, router
and link failures, recovery routes, critical infrastructure, and benchmark
metrics.

## Install and run

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r netrescue\requirements.txt
pip install -e .
python -m pytest -q
streamlit run netrescue\app.py
```

The sidebar supports generated topologies, bundled sample JSON, and uploaded
adjacency-list JSON. PyVis uses select boxes and multiselects for failures;
click-to-fail interactions are intentionally not required.

## Repository layout

```text
netrescue/
  app.py
  core/
    bfs.py dfs.py dijkstra.py critical.py
    failure.py recovery.py benchmark.py generator.py graph.py
  data/sample_networks/
  tests/
  ui/
pyproject.toml
```

## Algorithms

| Algorithm | Use | Complexity |
| --- | --- | --- |
| BFS | Minimum-hop route and replay | O(V + E) |
| Iterative DFS | Components and deep traversal | O(V + E) |
| Dijkstra | Minimum-cost weighted route | O((V + E) log V) |
| Iterative Tarjan | Articulation routers and bridges | O(V + E) |
| Monte Carlo | Resilience and recovery-rate estimates | Depends on trials |

## Resilience formula

```text
score = 100 * (0.5 * connectivity
             + 0.3 * (1 - articulation_points / nodes)
             + 0.2 * 1 / (1 + average_cost_inflation))
```

Connectivity counts surviving pairs inside the damaged graph. Cost inflation
is averaged only over surviving pairs sampled for the trial.

## Two-minute demo

1. Choose **Sample JSON → campus**.
2. Set source to `R0` and destination to `R19`.
3. In Network, use the replay slider to show BFS or Dijkstra exploration.
4. Select a router on the active route in **Failed routers** and observe the
   alternate route and recovery badge.
5. Select an articulation router such as `R3` or `R7`; inspect islands in
   Recovery and the ranked impact table.
6. Open Benchmark and run sizes 10, 50, and 100.
7. Open Resilience, compute the live score, and review its three contribution
   terms.

## Viva questions

1. **Why use an adjacency list?** Sparse networks use O(V + E) storage rather
   than the O(V²) storage of an adjacency matrix.
2. **Why does Dijkstra use a heap?** The heap selects the next minimum tentative
   distance efficiently.
3. **Why are negative edges rejected?** Dijkstra's greedy invariant fails with
   negative weights.
4. **When does BFS beat Dijkstra?** BFS is simpler and faster when every edge
   has equal cost and only hop count matters.
5. **What does the trap graph demonstrate?** BFS can choose two expensive hops
   while Dijkstra chooses four inexpensive hops.
6. **What is `low[u]`?** The earliest discovery time reachable from a subtree
   using tree edges and at most one back edge.
7. **When is an edge a bridge?** For tree edge `(u,v)`, when `low[v] > disc[u]`.
8. **When is a non-root articulation point found?** When a child has
   `low[child] >= disc[node]`.
9. **Why is Tarjan iterative?** An explicit stack avoids Python recursion limits
   on large networks.
10. **Why are failures applied to a copy?** Simulation must not corrupt the
    pristine topology used for before/after comparisons.
11. **What does recovery measure?** Reachability, cost change, hop change, and
    BFS/Dijkstra alternatives.
12. **Why is resilience Monte Carlo?** It estimates behavior over many random
    failure scenarios without enumerating every possible failure set.
13. **What is a weakness of Monte Carlo?** Results depend on trial count,
    distributions, and seed; rare failures may be missed.
14. **What does the cascade model assume?** Load starts at degree and failed
    nodes split their current load equally among surviving neighbors.
15. **Why canonicalize edge keys?** An undirected edge must match regardless of
    whether it is written `(u,v)` or `(v,u)`.
16. **What does a connected component represent?** An island whose nodes can
    still reach each other.
17. **Why compare BFS and Dijkstra?** Hop-optimal and cost-optimal routes are
    different objectives in weighted networks.

## Known limitations and extensions

The current UI uses selection controls instead of direct PyVis click events.
The cascade model is intentionally simplified. Possible extensions include
Yen's K-shortest paths, A*, minimum spanning tree analysis, max-flow,
auto-healing link suggestions, directed graphs, and PDF reports.
