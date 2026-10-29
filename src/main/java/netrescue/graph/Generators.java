package netrescue.graph;

import java.util.Random;

import netrescue.ds.DynamicArray;
import netrescue.ds.UnionFind;

/**
 * Random network generators and an automatic layout.
 *
 * Waxman model (Waxman, 1988, "Routing of multipoint connections"):
 * scatter n routers in a plane and link each pair with probability
 *     P(u, v) = beta * exp( -d(u, v) / (alpha * L) )
 * where d is their distance and L the largest possible distance. Near routers
 * are more likely to be linked, as in real networks. We then join any
 * disconnected pieces with their shortest bridging link (found with
 * union-find) so the result is always connected.
 */
public final class Generators {
    private Generators() {}

    public static final double W = 1000, H = 640;   // drawing area in world units

    public static Graph waxman(int n, double alpha, double beta, long seed) {
        if (n < 2) throw new IllegalArgumentException("need at least 2 routers");
        Random rnd = new Random(seed);
        Graph g = new Graph();
        g.setTitle("Random Waxman network (" + n + " routers)");
        for (int i = 0; i < n; i++)
            g.addNode("R" + (i + 1), 40 + rnd.nextDouble() * (W - 80), 40 + rnd.nextDouble() * (H - 80));
        double L = Math.hypot(W, H);
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                double d = dist(g, i, j);
                if (rnd.nextDouble() < beta * Math.exp(-d / (alpha * L)))
                    g.addEdge(i, j, costFor(d), capacityFor(rnd));
            }
        connect(g, rnd);
        return g;
    }

    /**
     * Fast random connected graph for benchmarks: a random spanning tree
     * (each new router links to a random earlier one) plus extra random links
     * until the average degree is about avgDegree. O(V + E).
     */
    public static Graph randomSparse(int n, double avgDegree, long seed) {
        Random rnd = new Random(seed);
        Graph g = new Graph();
        for (int i = 0; i < n; i++) g.addNode("R" + i, rnd.nextDouble() * W, rnd.nextDouble() * H);
        for (int i = 1; i < n; i++) g.addEdge(i, rnd.nextInt(i), 1 + rnd.nextInt(50), 20);
        long target = Math.round(n * avgDegree / 2);
        long attempts = 0;
        while (g.edgeSlots() < target && attempts++ < target * 10) {
            int a = rnd.nextInt(n), b = rnd.nextInt(n);
            if (a != b && g.findEdge(a, b) == null) g.addEdge(a, b, 1 + rnd.nextInt(50), 20);
        }
        return g;
    }

    /** Joins every disconnected piece to the rest using the shortest possible link. */
    public static void connect(Graph g, Random rnd) {
        while (true) {
            UnionFind uf = new UnionFind(g.nodeSlots());
            for (Edge e : g.allEdges()) if (!e.deleted) uf.union(e.u, e.v);
            DynamicArray<Integer> live = g.liveNodeIds();
            if (live.isEmpty()) return;
            int root = uf.find(live.get(0));
            boolean allConnected = true;
            for (int id : live) if (uf.find(id) != root) { allConnected = false; break; }
            if (allConnected) return;
            // shortest pair that crosses from the first piece to any other piece
            int bu = -1, bv = -1; double best = Double.MAX_VALUE;
            for (int a : live) {
                if (uf.find(a) != root) continue;
                for (int b : live) {
                    if (uf.find(b) == root) continue;
                    double d = dist(g, a, b);
                    if (d < best) { best = d; bu = a; bv = b; }
                }
            }
            g.addEdge(bu, bv, costFor(best), capacityFor(rnd));
        }
    }

    static double dist(Graph g, int a, int b) {
        Node x = g.node(a), y = g.node(b);
        return Math.hypot(x.x - y.x, x.y - y.y);
    }

    /** Cost proportional to length, rounded to one decimal place. */
    public static double costFor(double d) { return Math.max(1, Math.round(d / 10.0 * 10) / 10.0); }

    static int capacityFor(Random rnd) {
        int[] options = {10, 20, 40};
        return options[rnd.nextInt(options.length)];
    }

    /**
     * Force-directed layout (Fruchterman & Reingold, 1991). Linked routers
     * attract like springs, all routers repel like charges; the "temperature"
     * caps how far a router moves per step and cools down every iteration.
     */
    public static void forceLayout(Graph g, int iterations, long seed) {
        Random rnd = new Random(seed);
        DynamicArray<Integer> ids = g.liveNodeIds();
        int n = ids.size();
        if (n == 0) return;
        for (int id : ids) { Node v = g.node(id); v.x = rnd.nextDouble() * W; v.y = rnd.nextDouble() * H; }
        double k = Math.sqrt(W * H / n);
        double t = W / 10;
        int slots = g.nodeSlots();
        double[] dx = new double[slots], dy = new double[slots];
        for (int it = 0; it < iterations; it++) {
            for (int id : ids) { dx[id] = 0; dy[id] = 0; }
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                Node a = g.node(ids.get(i)), b = g.node(ids.get(j));
                double ddx = a.x - b.x, ddy = a.y - b.y, d = Math.max(0.01, Math.hypot(ddx, ddy));
                double f = k * k / d;
                dx[a.id] += ddx / d * f; dy[a.id] += ddy / d * f;
                dx[b.id] -= ddx / d * f; dy[b.id] -= ddy / d * f;
            }
            for (Edge e : g.allEdges()) {
                if (e.deleted) continue;
                Node a = g.node(e.u), b = g.node(e.v);
                double ddx = a.x - b.x, ddy = a.y - b.y, d = Math.max(0.01, Math.hypot(ddx, ddy));
                double f = d * d / k;
                dx[a.id] -= ddx / d * f; dy[a.id] -= ddy / d * f;
                dx[b.id] += ddx / d * f; dy[b.id] += ddy / d * f;
            }
            for (int id : ids) {
                Node v = g.node(id);
                double d = Math.max(0.01, Math.hypot(dx[id], dy[id]));
                v.x += dx[id] / d * Math.min(d, t);
                v.y += dy[id] / d * Math.min(d, t);
            }
            t *= 0.95;
        }
        fitToCanvas(g);
    }

    /** Scales and shifts positions so the network fills the drawing area with a margin. */
    public static void fitToCanvas(Graph g) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Node v : g.allNodes()) {
            if (v.deleted) continue;
            minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
            minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
        }
        if (minX == Double.MAX_VALUE) return;
        double m = 50;
        double sx = (W - 2 * m) / Math.max(1e-9, maxX - minX), sy = (H - 2 * m) / Math.max(1e-9, maxY - minY);
        double s = Math.min(sx, sy);
        double ox = (W - (maxX - minX) * s) / 2, oy = (H - (maxY - minY) * s) / 2;
        for (Node v : g.allNodes()) {
            if (v.deleted) continue;
            v.x = Math.round(ox + (v.x - minX) * s);
            v.y = Math.round(oy + (v.y - minY) * s);
        }
    }
}
