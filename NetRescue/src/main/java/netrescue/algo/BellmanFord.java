package netrescue.algo;

import netrescue.ds.DynamicArray;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * Bellman-Ford shortest paths (Bellman 1958, "On a routing problem";
 * Ford 1956; Moore 1959).
 *
 * No heap and no greedy choice: just relax EVERY link, V-1 times.
 * After round k, dist[v] is correct for every shortest path that uses at
 * most k links; a shortest path has at most V-1 links, so V-1 rounds suffice.
 * If a V-th round can still improve something, a negative-cost cycle exists.
 *
 *   Time O(V * E), space O(V).
 *
 * It is slower than Dijkstra but works with negative weights, and its
 * "ask your neighbours, improve, repeat" structure is exactly how
 * distance-vector routing protocols such as RIP work.
 */
public final class BellmanFord {
    private BellmanFord() {}

    public static final class Result {
        public double[] dist;
        public int[] parentEdge;
        public int rounds;                // rounds actually needed (stops early when nothing changes)
        public boolean negativeCycle;
        public long relaxations;
    }

    /** On the network: each undirected link is relaxed in both directions. */
    public static Result run(Graph g, int src, Trace t) {
        int n = g.nodeSlots();
        Result r = new Result();
        r.dist = new double[n];
        r.parentEdge = new int[n];
        for (int i = 0; i < n; i++) { r.dist[i] = Double.POSITIVE_INFINITY; r.parentEdge[i] = -1; }
        r.dist[src] = 0;
        if (t != null) t.step("dist[" + g.name(src) + "] = 0, all others = infinity.").node(src, "current").label(src, "0");
        int live = g.upNodeCount();
        for (int round = 1; round <= Math.max(1, live - 1); round++) {
            boolean changed = false;
            Trace.Step s = t != null ? t.step("Round " + round + ": relax every link in both directions.") : null;
            for (Edge e : g.allEdges()) {
                if (!g.edgeUsable(e)) continue;
                for (int dir = 0; dir < 2; dir++) {
                    int a = dir == 0 ? e.u : e.v, b = e.other(a);
                    r.relaxations++;
                    if (r.dist[a] + e.cost < r.dist[b]) {
                        r.dist[b] = r.dist[a] + e.cost;
                        r.parentEdge[b] = e.id;
                        changed = true;
                        if (s != null) s.node(b, "frontier").edge(e.id, "tree").label(b, Dijkstra.fmt(r.dist[b]));
                    }
                }
            }
            r.rounds = round;
            if (t != null) {
                DynamicArray<String> items = new DynamicArray<>();
                for (int i = 0; i < n; i++) if (g.nodeUp(i)) items.add(g.name(i) + " : " + Dijkstra.fmt(r.dist[i]));
                t.step(changed ? "End of round " + round + ": distances table." : "Round " + round + " changed nothing: stop early.").ds("list", "dist[] after round " + round, items);
            }
            if (!changed) break;
        }
        return r;
    }

    public static PathResult path(Graph g, int src, int dst, Trace t) {
        long start = System.nanoTime();
        Result res = run(g, src, t);
        PathResult p = PathResult.fromParents("Bellman-Ford", g, src, dst, res.parentEdge, res.dist[dst] < Double.POSITIVE_INFINITY);
        p.visited = res.rounds;
        p.nanos = System.nanoTime() - start;
        if (t != null) BFS.markPath(t, p, g, p.found() ? "Shortest route cost " + Dijkstra.fmt(p.cost) + " after " + res.rounds + " round(s)." : g.name(dst) + " is unreachable.");
        return p;
    }

    /**
     * General form on a DIRECTED edge list (from[i] -> to[i], weight w[i]),
     * which may have negative weights. Used by the tests and the
     * "why Dijkstra fails with negative edges" demo in the DS lab.
     */
    public static Result directed(int n, int[] from, int[] to, double[] w, int src) {
        Result r = new Result();
        r.dist = new double[n];
        r.parentEdge = new int[n];
        for (int i = 0; i < n; i++) { r.dist[i] = Double.POSITIVE_INFINITY; r.parentEdge[i] = -1; }
        r.dist[src] = 0;
        for (int round = 1; round < n; round++) {
            boolean changed = false;
            for (int i = 0; i < from.length; i++) {
                r.relaxations++;
                if (r.dist[from[i]] + w[i] < r.dist[to[i]]) {
                    r.dist[to[i]] = r.dist[from[i]] + w[i];
                    r.parentEdge[to[i]] = i;
                    changed = true;
                }
            }
            r.rounds = round;
            if (!changed) return r;
        }
        // one more round: any improvement now means a negative cycle
        for (int i = 0; i < from.length; i++)
            if (r.dist[from[i]] + w[i] < r.dist[to[i]]) { r.negativeCycle = true; break; }
        return r;
    }
}
