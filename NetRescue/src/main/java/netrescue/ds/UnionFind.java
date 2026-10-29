package netrescue.ds;

/**
 * Disjoint-set union (union-find) over elements 0..n-1.
 *
 * Each set is a tree; the root is the set's representative.
 *  - find(x): follow parent pointers to the root. PATH COMPRESSION points
 *    every node on the way directly at the root, so later finds are short.
 *  - union(a, b): attach the root of the shorter tree under the taller one
 *    (UNION BY RANK), which keeps trees shallow.
 *
 * With both tricks, m operations cost O(m * alpha(n)), where alpha is the
 * inverse Ackermann function (Tarjan, 1975) -- at most 4 for any realistic n,
 * so effectively O(1) each.
 *
 * Used for: "are these two routers still connected?", counting the pieces a
 * network splits into after failures, and Kruskal's MST.
 */
public class UnionFind {
    private final int[] parent;
    private final int[] rank;
    private final int[] setSize;
    private int components;

    public UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        setSize = new int[n];
        for (int i = 0; i < n; i++) { parent[i] = i; setSize[i] = 1; }
        components = n;
    }

    public int find(int x) {
        int root = x;
        while (parent[root] != root) root = parent[root];
        while (parent[x] != root) {          // path compression, second pass
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    /** Merges the sets of a and b. Returns false if they were already together. */
    public boolean union(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra == rb) return false;
        if (rank[ra] < rank[rb]) { int t = ra; ra = rb; rb = t; }
        parent[rb] = ra;
        setSize[ra] += setSize[rb];
        if (rank[ra] == rank[rb]) rank[ra]++;
        components--;
        return true;
    }

    public boolean connected(int a, int b) { return find(a) == find(b); }
    public int components() { return components; }
    public int size() { return parent.length; }
    public int sizeOf(int x) { return setSize[find(x)]; }

    /** Removes an element from the component count (used for routers that are down). */
    public void excludeFromCount() { components--; }

    /** Copy of the raw parent array, for drawing the forest. */
    public int[] parents() { return parent.clone(); }
}
