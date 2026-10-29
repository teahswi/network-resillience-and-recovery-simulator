package netrescue.algo;

import netrescue.ds.DynamicArray;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * Articulation points (critical routers) and bridges (critical links) in one
 * DFS (Tarjan 1972; Hopcroft & Tarjan 1973).
 *
 * During DFS give each router a discovery time disc[u]. Define
 *   low[u] = smallest disc[] reachable from u's DFS subtree using
 *            tree links downward plus at most ONE back link upward.
 * Then, for a tree link u -> v (v is u's child):
 *   - BRIDGE            if low[v] >  disc[u]   (v's subtree cannot reach u or above
 *                                                 without this link)
 *   - u is an ARTICULATION POINT if low[v] >= disc[u]   and u is not the root
 *   - the DFS root is an articulation point iff it has 2 or more DFS children.
 *
 *   Time O(V + E).
 *
 * In network terms: removing an articulation point or a bridge splits the
 * network -- they are single points of failure.
 */
public final class Tarjan {
    private Tarjan() {}

    public static final class Result {
        public final DynamicArray<Integer> articulationPoints = new DynamicArray<>();
        public final DynamicArray<Integer> bridges = new DynamicArray<>();
        public int[] disc, low;
    }

    public static Result run(Graph g, Trace t) {
        int n = g.nodeSlots();
        Result r = new Result();
        r.disc = new int[n];
        r.low = new int[n];
        boolean[] isAP = new boolean[n];
        for (int i = 0; i < n; i++) r.disc[i] = -1;
        int[] timer = {0};
        for (int s = 0; s < n; s++) {
            if (!g.nodeUp(s) || r.disc[s] != -1) continue;
            if (t != null) t.step("Start a DFS at " + g.name(s) + " (root of this DFS tree).").node(s, "frontier");
            int children = dfs(g, s, -1, r, isAP, timer, t);
            if (children >= 2) {
                isAP[s] = true;
                if (t != null) t.step(g.name(s) + " is the DFS root with " + children + " children: articulation point.").node(s, "ap");
            }
        }
        for (int i = 0; i < n; i++) if (isAP[i]) r.articulationPoints.add(i);
        if (t != null) {
            Trace.Step s = t.step("Done: " + r.articulationPoints.size() + " critical router(s), " + r.bridges.size() + " critical link(s).");
            for (int id : r.articulationPoints) s.node(id, "ap");
            for (int id : r.bridges) s.edge(id, "bridge");
        }
        return r;
    }

    /** Returns the number of DFS children of u. */
    private static int dfs(Graph g, int u, int parentEdge, Result r, boolean[] isAP, int[] timer, Trace t) {
        r.disc[u] = r.low[u] = timer[0]++;
        if (t != null) t.step("Visit " + g.name(u) + ": disc = low = " + r.disc[u] + ".").node(u, "current").label(u, r.disc[u] + "/" + r.low[u]);
        int children = 0;
        for (Edge e : g.adjacent(u)) {
            if (!g.edgeUsable(e) || e.id == parentEdge) continue;
            int v = e.other(u);
            if (r.disc[v] == -1) {
                children++;
                if (t != null) t.step("Tree link " + g.name(u) + " -> " + g.name(v) + ".").edge(e.id, "tree");
                dfs(g, v, e.id, r, isAP, timer, t);
                r.low[u] = Math.min(r.low[u], r.low[v]);
                if (t != null) t.step("Back at " + g.name(u) + ": low[" + g.name(u) + "] = min(low, low[" + g.name(v) + "]=" + r.low[v] + ") = " + r.low[u] + ".")
                        .node(u, "current").label(u, r.disc[u] + "/" + r.low[u]);
                if (r.low[v] > r.disc[u]) {
                    r.bridges.add(e.id);
                    if (t != null) t.step("low[" + g.name(v) + "]=" + r.low[v] + " > disc[" + g.name(u) + "]=" + r.disc[u] + ": link " + g.name(u) + "-" + g.name(v) + " is a BRIDGE.").edge(e.id, "bridge");
                }
                if (parentEdge != -1 && r.low[v] >= r.disc[u] && !isAP[u]) {
                    isAP[u] = true;
                    if (t != null) t.step("low[" + g.name(v) + "]=" + r.low[v] + " >= disc[" + g.name(u) + "]=" + r.disc[u] + ": " + g.name(u) + " is an ARTICULATION POINT.").node(u, "ap");
                }
            } else {
                // back link to an ancestor
                if (r.disc[v] < r.low[u]) {
                    r.low[u] = r.disc[v];
                    if (t != null) t.step("Back link " + g.name(u) + " -> " + g.name(v) + ": low[" + g.name(u) + "] = disc[" + g.name(v) + "] = " + r.low[u] + ".")
                            .edge(e.id, "considered").label(u, r.disc[u] + "/" + r.low[u]);
                }
            }
        }
        if (t != null && !isAP[u]) t.step("Finished " + g.name(u) + ".").node(u, "visited");
        return children;
    }
}
