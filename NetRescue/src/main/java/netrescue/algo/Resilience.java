package netrescue.algo;

import netrescue.ds.DynamicArray;
import netrescue.ds.Sorting;
import netrescue.ds.UnionFind;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * "How fragile is this network?" -- built on Tarjan + union-find.
 *
 * For each critical router / link we measure its IMPACT: fail it, rebuild the
 * connectivity with union-find, and count how many routers are cut off from
 * the largest remaining piece. The lists are then sorted by impact
 * (quicksort) so the most dangerous single failures come first.
 *
 * Resilience scores:
 *   router score = 1 - (#articulation points / #routers)
 *   link score   = 1 - (#bridges / #links)
 * 1.0 means no single failure can disconnect anything.
 */
public final class Resilience {
    private Resilience() {}

    public static final class Impact {
        public final int id;          // router id or link id
        public final int cutOff;      // routers separated from the main piece
        Impact(int id, int cutOff) { this.id = id; this.cutOff = cutOff; }
    }

    public static final class Report {
        public Tarjan.Result tarjan;
        public final DynamicArray<Impact> routerImpact = new DynamicArray<>();
        public final DynamicArray<Impact> linkImpact = new DynamicArray<>();
        public double routerScore, linkScore;
        public int components;
        public int largestComponent;
    }

    public static Report analyse(Graph g) {
        Report r = new Report();
        r.tarjan = Tarjan.run(g, null);
        int routers = g.upNodeCount(), links = g.usableEdgeCount();
        r.routerScore = routers == 0 ? 1 : 1 - (double) r.tarjan.articulationPoints.size() / routers;
        r.linkScore = links == 0 ? 1 : 1 - (double) r.tarjan.bridges.size() / links;
        int[] comp = componentsWithout(g, -1, -1);
        r.components = comp[0];
        r.largestComponent = comp[1];
        int base = routers - comp[1];
        for (int ap : r.tarjan.articulationPoints) {
            int[] c = componentsWithout(g, ap, -1);
            r.routerImpact.add(new Impact(ap, (routers - 1 - c[1]) - base));
        }
        for (int b : r.tarjan.bridges) {
            int[] c = componentsWithout(g, -1, b);
            r.linkImpact.add(new Impact(b, (routers - c[1]) - base));
        }
        Sorting.quickSort(r.routerImpact, (a, b) -> Integer.compare(b.cutOff, a.cutOff));
        Sorting.quickSort(r.linkImpact, (a, b) -> Integer.compare(b.cutOff, a.cutOff));
        return r;
    }

    /**
     * Union-find over usable links, pretending router `skipNode` and link
     * `skipEdge` are down. Returns {number of pieces, size of largest piece}.
     */
    public static int[] componentsWithout(Graph g, int skipNode, int skipEdge) {
        UnionFind uf = new UnionFind(g.nodeSlots());
        for (Edge e : g.allEdges()) {
            if (!g.edgeUsable(e) || e.id == skipEdge || e.u == skipNode || e.v == skipNode) continue;
            uf.union(e.u, e.v);
        }
        int pieces = 0, largest = 0;
        for (int i = 0; i < g.nodeSlots(); i++) {
            if (!g.nodeUp(i) || i == skipNode) continue;
            if (uf.find(i) == i) pieces++;
            largest = Math.max(largest, uf.sizeOf(i));
        }
        return new int[]{pieces, largest};
    }
}
