package netrescue.algo;

import java.util.function.Predicate;

import netrescue.ds.DynamicArray;
import netrescue.ds.Queue;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * Breadth-first search (Moore, 1959, "The shortest path through a maze").
 *
 * Explore in rings: first every router 1 hop away, then 2 hops, ... A FIFO
 * queue enforces this order: routers are dequeued in the order they were
 * discovered, so the first time we reach dst we have used the fewest hops.
 *
 *   Time  O(V + E): each router is enqueued once, each link examined twice.
 *   Space O(V) for the queue, visited flags and parent links.
 */
public final class BFS {
    private BFS() {}

    /** Fewest-hop route from src to dst over links accepted by `ok` (null = any usable link). */
    public static PathResult fewestHops(Graph g, int src, int dst, Predicate<Edge> ok, Trace t) {
        long start = System.nanoTime();
        int n = g.nodeSlots();
        boolean[] seen = new boolean[n];
        int[] parentEdge = new int[n];
        int[] depth = new int[n];
        Queue<Integer> q = new Queue<>();
        int visited = 0;

        seen[src] = true;
        q.enqueue(src);
        if (t != null) t.step("Start at " + g.name(src) + ": enqueue it.").node(src, "frontier").label(src, "0").ds("queue", "Queue (front -> back)", names(g, q));

        boolean found = false;
        while (!q.isEmpty()) {
            int u = q.dequeue();
            visited++;
            if (t != null) t.step("Dequeue " + g.name(u) + " (" + depth[u] + " hops from start).").node(u, "current").ds("queue", "Queue (front -> back)", names(g, q));
            if (u == dst) { found = true; break; }
            for (Edge e : g.adjacent(u)) {
                if (!g.edgeUsable(e) || (ok != null && !ok.test(e))) continue;
                int v = e.other(u);
                if (seen[v]) continue;
                seen[v] = true;
                parentEdge[v] = e.id;
                depth[v] = depth[u] + 1;
                q.enqueue(v);
                if (t != null) t.step("Discover " + g.name(v) + " via " + g.name(u) + ": enqueue.")
                        .node(v, "frontier").edge(e.id, "tree").label(v, String.valueOf(depth[v]))
                        .ds("queue", "Queue (front -> back)", names(g, q));
            }
            if (t != null) t.step("Finished " + g.name(u) + ".").node(u, "visited");
        }
        PathResult r = PathResult.fromParents("BFS", g, src, dst, parentEdge, found || (src == dst));
        r.cost = 0;
        for (int eid : r.edges) r.cost += g.edge(eid).cost;
        r.visited = visited;
        r.nanos = System.nanoTime() - start;
        if (t != null) markPath(t, r, g, found ? "Reached " + g.name(dst) + " in " + r.hops() + " hops." : g.name(dst) + " is unreachable.");
        return r;
    }

    /** Which routers can src reach over usable links? */
    public static boolean[] reachable(Graph g, int src) {
        boolean[] seen = new boolean[g.nodeSlots()];
        if (!g.nodeUp(src)) return seen;
        Queue<Integer> q = new Queue<>();
        seen[src] = true;
        q.enqueue(src);
        while (!q.isEmpty()) {
            int u = q.dequeue();
            for (Edge e : g.adjacent(u)) {
                if (!g.edgeUsable(e)) continue;
                int v = e.other(u);
                if (!seen[v]) { seen[v] = true; q.enqueue(v); }
            }
        }
        return seen;
    }

    static DynamicArray<String> names(Graph g, Queue<Integer> q) {
        DynamicArray<String> out = new DynamicArray<>();
        for (int id : q.frontToBack()) out.add(g.name(id));
        return out;
    }

    static void markPath(Trace t, PathResult r, Graph g, String msg) {
        Trace.Step s = t.step(msg);
        for (int id : r.nodes) s.node(id, "path");
        for (int id : r.edges) s.edge(id, "path");
    }
}
