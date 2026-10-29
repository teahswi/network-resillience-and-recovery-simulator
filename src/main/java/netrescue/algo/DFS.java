package netrescue.algo;

import netrescue.ds.DynamicArray;
import netrescue.ds.Stack;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * Depth-first search (Tremaux's maze method, 19th century; Tarjan 1972 for graphs).
 *
 * Go as deep as possible along one branch, back up only when stuck.
 * The recursive version uses the call stack; the iterative version below
 * uses our own Stack explicitly, which is the same thing made visible.
 *
 *   Time O(V + E), space O(V).
 */
public final class DFS {
    private DFS() {}

    /** Iterative DFS from src. Returns routers in the order they were first visited. */
    public static DynamicArray<Integer> iterative(Graph g, int src, Trace t) {
        boolean[] seen = new boolean[g.nodeSlots()];
        DynamicArray<Integer> order = new DynamicArray<>();
        Stack<int[]> stack = new Stack<>();        // {router, edge used to reach it (-1 for start)}
        stack.push(new int[]{src, -1});
        if (t != null) t.step("Push " + g.name(src) + " onto the stack.").node(src, "frontier").ds("stack", "Stack (top first)", names(g, stack));
        while (!stack.isEmpty()) {
            int[] top = stack.pop();
            int u = top[0];
            if (seen[u]) {
                if (t != null) t.step("Pop " + g.name(u) + ": already visited, skip.").ds("stack", "Stack (top first)", names(g, stack));
                continue;
            }
            seen[u] = true;
            order.add(u);
            if (t != null) {
                Trace.Step s = t.step("Pop " + g.name(u) + ": visit it (#" + order.size() + ").").node(u, "current").label(u, "#" + order.size());
                if (top[1] >= 0) s.edge(top[1], "tree");
                s.ds("stack", "Stack (top first)", names(g, stack));
            }
            // push neighbours in reverse so the first neighbour ends on top (visited first)
            DynamicArray<Edge> nbrs = new DynamicArray<>();
            for (Edge e : g.adjacent(u)) if (g.edgeUsable(e) && !seen[e.other(u)]) nbrs.add(e);
            for (int i = nbrs.size() - 1; i >= 0; i--) {
                Edge e = nbrs.get(i);
                stack.push(new int[]{e.other(u), e.id});
            }
            if (t != null) {
                Trace.Step s = t.step(nbrs.isEmpty() ? g.name(u) + " has no unvisited neighbours: backtrack." : "Push " + nbrs.size() + " unvisited neighbour(s) of " + g.name(u) + ".").node(u, "visited");
                for (Edge e : nbrs) s.node(e.other(u), "frontier");
                s.ds("stack", "Stack (top first)", names(g, stack));
            }
        }
        if (t != null) t.step("DFS finished: visited " + order.size() + " routers.");
        return order;
    }

    /** Recursive DFS (same visit order as the iterative one). */
    public static DynamicArray<Integer> recursive(Graph g, int src) {
        boolean[] seen = new boolean[g.nodeSlots()];
        DynamicArray<Integer> order = new DynamicArray<>();
        visit(g, src, seen, order);
        return order;
    }

    private static void visit(Graph g, int u, boolean[] seen, DynamicArray<Integer> order) {
        seen[u] = true;
        order.add(u);
        for (Edge e : g.adjacent(u)) {
            if (!g.edgeUsable(e)) continue;
            int v = e.other(u);
            if (!seen[v]) visit(g, v, seen, order);
        }
    }

    /**
     * Connected components: run DFS from every unvisited up router; each run
     * labels one component. comp[id] = component number, -1 for down/deleted.
     */
    public static int[] components(Graph g) {
        int n = g.nodeSlots();
        int[] comp = new int[n];
        for (int i = 0; i < n; i++) comp[i] = -1;
        int c = 0;
        Stack<Integer> st = new Stack<>();
        for (int s = 0; s < n; s++) {
            if (!g.nodeUp(s) || comp[s] != -1) continue;
            comp[s] = c;
            st.push(s);
            while (!st.isEmpty()) {
                int u = st.pop();
                for (Edge e : g.adjacent(u)) {
                    if (!g.edgeUsable(e)) continue;
                    int v = e.other(u);
                    if (comp[v] == -1) { comp[v] = c; st.push(v); }
                }
            }
            c++;
        }
        return comp;
    }

    private static DynamicArray<String> names(Graph g, Stack<int[]> st) {
        DynamicArray<String> out = new DynamicArray<>();
        for (int[] it : st.topToBottom()) out.add(g.name(it[0]));
        return out;
    }
}
