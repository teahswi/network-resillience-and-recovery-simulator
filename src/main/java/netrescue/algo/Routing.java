package netrescue.algo;

import netrescue.ds.Deque;
import netrescue.ds.DynamicArray;
import netrescue.ds.HashSet;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * Extra routing modes built from the basic algorithms.
 */
public final class Routing {
    private Routing() {}

    /**
     * SAFEST route: the path that crosses the fewest critical links (bridges).
     *
     * Give every bridge weight 1 and every other link weight 0. Shortest paths
     * with only 0/1 weights can be found by "0-1 BFS" using a DEQUE:
     * a 0-weight neighbour goes to the FRONT (same distance), a 1-weight
     * neighbour to the BACK (distance + 1). The deque then always pops routers
     * in non-decreasing distance order, like Dijkstra's heap, but in O(V + E).
     */
    public static PathResult safest(Graph g, int src, int dst, HashSet<Integer> bridges) {
        long start = System.nanoTime();
        int n = g.nodeSlots();
        int[] dist = new int[n];
        int[] parentEdge = new int[n];
        for (int i = 0; i < n; i++) { dist[i] = Integer.MAX_VALUE; parentEdge[i] = -1; }
        Deque<Integer> dq = new Deque<>();
        dist[src] = 0;
        dq.addFirst(src);
        int visited = 0;
        while (!dq.isEmpty()) {
            int u = dq.pollFirst();
            visited++;
            for (Edge e : g.adjacent(u)) {
                if (!g.edgeUsable(e)) continue;
                int v = e.other(u);
                int w = bridges.contains(e.id) ? 1 : 0;
                if (dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                    parentEdge[v] = e.id;
                    if (w == 0) dq.addFirst(v); else dq.addLast(v);
                }
            }
        }
        PathResult r = PathResult.fromParents("Safest (0-1 BFS)", g, src, dst, parentEdge, dist[dst] != Integer.MAX_VALUE);
        r.visited = visited;
        r.nanos = System.nanoTime() - start;
        return r;
    }

    /**
     * Up to k ALTERNATIVE routes by the edge-penalty method: find the cheapest
     * route, multiply the weight of the links it used by `penalty`, and search
     * again. Each round is pushed away from links already used, so the routes
     * come out mostly different. Heuristic (not guaranteed to be the exact
     * k shortest), but uses only Dijkstra. Real costs are reported.
     */
    public static DynamicArray<PathResult> alternatives(Graph g, int src, int dst, int k, double penalty) {
        DynamicArray<PathResult> out = new DynamicArray<>();
        double[] factor = new double[g.edgeSlots()];
        for (int i = 0; i < factor.length; i++) factor[i] = 1;
        HashSet<String> seen = new HashSet<>();
        for (int round = 0; round < k * 3 && out.size() < k; round++) {
            PathResult p = Dijkstra.shortestPath(g, src, dst, null, e -> e.cost * factor[e.id], null);
            if (!p.found()) break;
            double real = 0;
            for (int eid : p.edges) { real += g.edge(eid).cost; factor[eid] *= penalty; }
            p.cost = real;
            String sig = p.edges.toString();
            if (seen.add(sig)) out.add(p);
        }
        out.sort((a, b) -> Double.compare(a.cost, b.cost));
        DynamicArray<PathResult> named = new DynamicArray<>();
        for (int i = 0; i < out.size(); i++) named.add(out.get(i).renamed("Route " + (i + 1)));
        return named;
    }
}
