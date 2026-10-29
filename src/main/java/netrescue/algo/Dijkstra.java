package netrescue.algo;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import netrescue.ds.DynamicArray;
import netrescue.ds.IndexedMinHeap;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * Dijkstra's shortest-path algorithm (Dijkstra, 1959, "A note on two problems
 * in connexion with graphs").
 *
 * Keep a tentative distance dist[v] for every router (infinity at first,
 * 0 at the source). Repeatedly take the unsettled router with the smallest
 * dist (from the min-heap), settle it, and RELAX its links:
 *     if dist[u] + w(u,v) < dist[v]:  dist[v] = dist[u] + w(u,v)
 * With non-negative weights, the router taken from the heap can never get a
 * shorter distance later, which is why the greedy choice is correct.
 *
 *   With an indexed binary heap:  O((V + E) log V)
 *   With a plain array scan:      O(V^2)          (arrayVersion, for the benchmark)
 */
public final class Dijkstra {
    private Dijkstra() {}

    public static final ToDoubleFunction<Edge> COST = e -> e.cost;

    public static PathResult shortestPath(Graph g, int src, int dst, Predicate<Edge> ok, Trace t) {
        return shortestPath(g, src, dst, ok, COST, t);
    }

    public static PathResult shortestPath(Graph g, int src, int dst, Predicate<Edge> ok, ToDoubleFunction<Edge> w, Trace t) {
        long start = System.nanoTime();
        int n = g.nodeSlots();
        double[] dist = new double[n];
        int[] parentEdge = new int[n];
        boolean[] settled = new boolean[n];
        for (int i = 0; i < n; i++) { dist[i] = Double.POSITIVE_INFINITY; parentEdge[i] = -1; }
        IndexedMinHeap pq = new IndexedMinHeap(n);
        dist[src] = 0;
        pq.insert(src, 0);
        int visited = 0;
        if (t != null) t.step("dist[" + g.name(src) + "] = 0, all others = infinity. Insert " + g.name(src) + " into the heap.")
                .node(src, "frontier").label(src, "0").ds("heap", "Min-heap (router : dist)", heapItems(g, pq));

        while (!pq.isEmpty()) {
            int u = pq.extractMin();
            settled[u] = true;
            visited++;
            if (t != null) t.step("Extract-min: " + g.name(u) + " (dist " + fmt(dist[u]) + ") is now final.")
                    .node(u, "current").ds("heap", "Min-heap (router : dist)", heapItems(g, pq));
            if (u == dst) break;
            for (Edge e : g.adjacent(u)) {
                if (!g.edgeUsable(e) || (ok != null && !ok.test(e))) continue;
                int v = e.other(u);
                if (settled[v]) continue;
                double nd = dist[u] + w.applyAsDouble(e);
                if (nd < dist[v]) {
                    String before = fmt(dist[v]);
                    dist[v] = nd;
                    parentEdge[v] = e.id;
                    pq.insertOrDecrease(v, nd);
                    if (t != null) t.step("Relax " + g.name(u) + "-" + g.name(v) + ": " + fmt(dist[u]) + " + " + fmt(w.applyAsDouble(e)) + " = " + fmt(nd) + " < " + before + ", update.")
                            .node(v, "frontier").edge(e.id, "tree").label(v, fmt(nd)).ds("heap", "Min-heap (router : dist)", heapItems(g, pq));
                } else if (t != null) {
                    t.step("Relax " + g.name(u) + "-" + g.name(v) + ": " + fmt(nd) + " >= " + fmt(dist[v]) + ", no change.").edge(e.id, "considered");
                }
            }
            if (t != null) t.step("Done with " + g.name(u) + ".").node(u, "visited");
        }
        boolean reach = dist[dst] < Double.POSITIVE_INFINITY;
        PathResult r = PathResult.fromParents("Dijkstra", g, src, dst, parentEdge, reach);
        r.visited = visited;
        r.nanos = System.nanoTime() - start;
        if (t != null) BFS.markPath(t, r, g, reach ? "Shortest route cost " + fmt(r.cost) + " over " + r.hops() + " hops." : g.name(dst) + " is unreachable.");
        return r;
    }

    /** All distances from src (heap version). parentEdge is filled if non-null. */
    public static double[] allFrom(Graph g, int src, int[] parentEdge) {
        int n = g.nodeSlots();
        double[] dist = new double[n];
        for (int i = 0; i < n; i++) dist[i] = Double.POSITIVE_INFINITY;
        if (parentEdge != null) for (int i = 0; i < n; i++) parentEdge[i] = -1;
        IndexedMinHeap pq = new IndexedMinHeap(n);
        dist[src] = 0;
        pq.insert(src, 0);
        while (!pq.isEmpty()) {
            int u = pq.extractMin();
            for (Edge e : g.adjacent(u)) {
                if (!g.edgeUsable(e)) continue;
                int v = e.other(u);
                double nd = dist[u] + e.cost;
                if (nd < dist[v]) {
                    dist[v] = nd;
                    if (parentEdge != null) parentEdge[v] = e.id;
                    pq.insertOrDecrease(v, nd);
                }
            }
        }
        return dist;
    }

    /**
     * Dijkstra's original O(V^2) form: no heap, just scan all routers for the
     * smallest unsettled distance each round. Faster than the heap version on
     * very dense graphs (E close to V^2), slower on sparse ones.
     */
    public static double[] arrayVersion(Graph g, int src) {
        int n = g.nodeSlots();
        double[] dist = new double[n];
        boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) dist[i] = Double.POSITIVE_INFINITY;
        dist[src] = 0;
        for (int round = 0; round < n; round++) {
            int u = -1;
            for (int i = 0; i < n; i++) if (!done[i] && (u == -1 || dist[i] < dist[u])) u = i;
            if (u == -1 || dist[u] == Double.POSITIVE_INFINITY) break;
            done[u] = true;
            for (Edge e : g.adjacent(u)) {
                if (!g.edgeUsable(e)) continue;
                int v = e.other(u);
                if (dist[u] + e.cost < dist[v]) dist[v] = dist[u] + e.cost;
            }
        }
        return dist;
    }

    private static DynamicArray<String> heapItems(Graph g, IndexedMinHeap pq) {
        DynamicArray<String> out = new DynamicArray<>();
        for (int k : pq.keysInHeapOrder()) out.add(g.name(k) + " : " + fmt(pq.priorityOf(k)));
        return out;
    }

    static String fmt(double d) {
        if (d == Double.POSITIVE_INFINITY) return "inf";
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(Math.round(d * 10) / 10.0);
    }
}
