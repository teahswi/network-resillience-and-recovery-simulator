package netrescue.algo;

import netrescue.ds.DynamicArray;
import netrescue.graph.Edge;
import netrescue.graph.Graph;
import netrescue.io.Json;

/** A route from src to dst: routers in order, the links between them, total cost. */
public class PathResult {
    public final String algorithm;
    public final int src, dst;
    public final DynamicArray<Integer> nodes = new DynamicArray<>();
    public final DynamicArray<Integer> edges = new DynamicArray<>();
    public double cost;
    public int visited;          // routers settled/visited while searching
    public long nanos;

    public PathResult(String algorithm, int src, int dst) {
        this.algorithm = algorithm; this.src = src; this.dst = dst;
    }

    public boolean found() { return !nodes.isEmpty(); }

    public PathResult renamed(String newAlgorithmName) {
        PathResult c = new PathResult(newAlgorithmName, src, dst);
        for (int x : nodes) c.nodes.add(x);
        for (int x : edges) c.edges.add(x);
        c.cost = cost; c.visited = visited; c.nanos = nanos;
        return c;
    }
    public int hops() { return edges.size(); }

    /**
     * Rebuilds the path by walking parent links backwards from dst, then
     * reversing (the classic "follow the breadcrumbs home" step).
     */
    public static PathResult fromParents(String algo, Graph g, int src, int dst, int[] parentEdge, boolean reachable) {
        PathResult r = new PathResult(algo, src, dst);
        if (!reachable) return r;
        int cur = dst;
        r.nodes.add(cur);
        while (cur != src) {
            Edge e = g.edge(parentEdge[cur]);
            r.edges.add(e.id);
            r.cost += e.cost;
            cur = e.other(cur);
            r.nodes.add(cur);
        }
        r.nodes.reverse();
        r.edges.reverse();
        return r;
    }

    public String describe(Graph g) {
        if (!found()) return "no route";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) { if (i > 0) sb.append(" -> "); sb.append(g.name(nodes.get(i))); }
        return sb.toString();
    }

    public void toJson(Json j, Graph g) {
        j.obj().put("algorithm", algorithm).put("found", found()).put("cost", cost).put("hops", hops())
                .put("visited", visited).put("micros", nanos / 1000.0).put("text", describe(g));
        j.key("nodes").arr(); for (int n : nodes) j.val(n); j.end();
        j.key("edges").arr(); for (int e : edges) j.val(e); j.end();
        j.end();
    }
}
