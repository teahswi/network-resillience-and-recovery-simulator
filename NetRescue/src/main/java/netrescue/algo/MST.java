package netrescue.algo;

import netrescue.ds.DynamicArray;
import netrescue.ds.HashMap;
import netrescue.ds.MinHeap;
import netrescue.ds.Sorting;
import netrescue.ds.UnionFind;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * Minimum spanning tree: the cheapest set of links that keeps every router
 * connected (the cheapest possible "backbone"). If the network is in pieces
 * we get a minimum spanning FOREST, one tree per piece.
 *
 * Both algorithms rely on the CUT PROPERTY: for any split of the routers into
 * two groups, the cheapest link crossing the split belongs to some MST.
 *
 * Kruskal (1956): sort all links by cost; take each link unless it would make
 * a cycle. "Would it make a cycle?" = "are both ends already in the same set?"
 * -> union-find.       O(E log E)  (dominated by the sort)
 *
 * Prim (1957; Jarnik 1930): grow one tree from a start router; always add the
 * cheapest link leaving the tree. A min-heap holds the candidate links.
 * (Lazy version: stale links are skipped when popped.)   O(E log E)
 */
public final class MST {
    private MST() {}

    public static final class Result {
        public final DynamicArray<Integer> edges = new DynamicArray<>();
        public double totalCost;
        public double networkCost;       // cost of all usable links, for comparison
        public int trees;
        public long comparisons;
        public String algorithm;
    }

    public static Result kruskal(Graph g, boolean useQuickSort, Trace t) {
        Result r = new Result();
        r.algorithm = "Kruskal";
        DynamicArray<Edge> list = new DynamicArray<>();
        for (Edge e : g.allEdges()) if (g.edgeUsable(e)) { list.add(e); r.networkCost += e.cost; }
        if (useQuickSort) Sorting.quickSort(list, (a, b) -> Double.compare(a.cost, b.cost));
        else Sorting.mergeSort(list, (a, b) -> Double.compare(a.cost, b.cost));
        r.comparisons = Sorting.lastComparisons;
        if (t != null) {
            DynamicArray<String> items = new DynamicArray<>();
            for (Edge e : list) items.add(g.name(e.u) + "-" + g.name(e.v) + " : " + Dijkstra.fmt(e.cost));
            t.step("Sort all " + list.size() + " links by cost (" + (useQuickSort ? "quicksort" : "merge sort") + ").").ds("list", "Links sorted by cost", items);
        }
        UnionFind uf = new UnionFind(g.nodeSlots());
        int up = g.upNodeCount();
        for (Edge e : list) {
            if (uf.union(e.u, e.v)) {
                r.edges.add(e.id);
                r.totalCost += e.cost;
                if (t != null) t.step("Take " + g.name(e.u) + "-" + g.name(e.v) + " (" + Dijkstra.fmt(e.cost) + "): joins two different sets.")
                        .edge(e.id, "mst").node(e.u, "visited").node(e.v, "visited").ds("sets", "Union-find sets", sets(g, uf));
            } else if (t != null) {
                t.step("Skip " + g.name(e.u) + "-" + g.name(e.v) + " (" + Dijkstra.fmt(e.cost) + "): both ends already in one set, it would form a cycle.").edge(e.id, "rejected");
            }
            if (r.edges.size() == up - 1) break;
        }
        r.trees = up - r.edges.size();
        if (t != null) t.step("MST done: " + r.edges.size() + " links, total cost " + Dijkstra.fmt(r.totalCost) + ".");
        return r;
    }

    public static Result prim(Graph g, Trace t) {
        Result r = new Result();
        r.algorithm = "Prim";
        for (Edge e : g.allEdges()) if (g.edgeUsable(e)) r.networkCost += e.cost;
        boolean[] inTree = new boolean[g.nodeSlots()];
        MinHeap<Edge> heap = new MinHeap<>((a, b) -> Double.compare(a.cost, b.cost));
        for (int s = 0; s < g.nodeSlots(); s++) {
            if (!g.nodeUp(s) || inTree[s]) continue;
            r.trees++;
            add(g, s, inTree, heap);
            if (t != null) t.step("Start a tree at " + g.name(s) + "; push its links into the heap.").node(s, "visited").ds("heap", "Min-heap of candidate links", heapItems(g, heap));
            while (!heap.isEmpty()) {
                Edge e = heap.extractMin();
                if (inTree[e.u] && inTree[e.v]) {
                    if (t != null) t.step("Pop " + g.name(e.u) + "-" + g.name(e.v) + ": both ends in tree, discard.").edge(e.id, "rejected").ds("heap", "Min-heap of candidate links", heapItems(g, heap));
                    continue;
                }
                int v = inTree[e.u] ? e.v : e.u;
                r.edges.add(e.id);
                r.totalCost += e.cost;
                add(g, v, inTree, heap);
                if (t != null) t.step("Pop cheapest " + g.name(e.u) + "-" + g.name(e.v) + " (" + Dijkstra.fmt(e.cost) + "): add " + g.name(v) + " to the tree.")
                        .edge(e.id, "mst").node(v, "visited").ds("heap", "Min-heap of candidate links", heapItems(g, heap));
            }
        }
        if (t != null) t.step("MST done: " + r.edges.size() + " links, total cost " + Dijkstra.fmt(r.totalCost) + ".");
        return r;
    }

    private static void add(Graph g, int v, boolean[] inTree, MinHeap<Edge> heap) {
        inTree[v] = true;
        for (Edge e : g.adjacent(v)) if (g.edgeUsable(e) && !inTree[e.other(v)]) heap.insert(e);
    }

    private static DynamicArray<String> heapItems(Graph g, MinHeap<Edge> h) {
        DynamicArray<String> out = new DynamicArray<>();
        for (Edge e : h.array()) out.add(g.name(e.u) + "-" + g.name(e.v) + " : " + Dijkstra.fmt(e.cost));
        return out;
    }

    /** Groups routers by their union-find root, e.g. "{A, B, C}". */
    static DynamicArray<String> sets(Graph g, UnionFind uf) {
        HashMap<Integer, StringBuilder> groups = new HashMap<>();
        DynamicArray<Integer> rootsInOrder = new DynamicArray<>();
        for (int i = 0; i < g.nodeSlots(); i++) {
            if (!g.nodeUp(i)) continue;
            int root = uf.find(i);
            StringBuilder sb = groups.get(root);
            if (sb == null) { sb = new StringBuilder(); groups.put(root, sb); rootsInOrder.add(root); }
            else sb.append(", ");
            sb.append(g.name(i));
        }
        DynamicArray<String> out = new DynamicArray<>();
        for (int root : rootsInOrder) out.add("{" + groups.get(root) + "}");
        return out;
    }
}
