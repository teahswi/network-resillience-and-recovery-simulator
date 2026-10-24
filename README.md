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
streamlit run app.py
```

You can also launch the package entry point directly:

```powershell
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


## Known limitations and extensions

The current UI uses selection controls instead of direct PyVis click events.
The cascade model is intentionally simplified. Possible extensions include
Yen's K-shortest paths, A*, minimum spanning tree analysis, max-flow,
auto-healing link suggestions, directed graphs, and PDF reports.
