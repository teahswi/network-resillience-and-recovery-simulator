package netrescue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.TreeSet;

import netrescue.algo.BFS;
import netrescue.algo.BellmanFord;
import netrescue.algo.DFS;
import netrescue.algo.Dijkstra;
import netrescue.algo.FloydWarshall;
import netrescue.algo.MST;
import netrescue.algo.PathResult;
import netrescue.algo.Resilience;
import netrescue.algo.Routing;
import netrescue.algo.Tarjan;
import netrescue.algo.Trace;
import netrescue.ds.*;
import netrescue.graph.Edge;
import netrescue.graph.EditHistory;
import netrescue.graph.Generators;
import netrescue.graph.Graph;
import netrescue.io.GmlParser;
import netrescue.io.Json;
import netrescue.io.Samples;
import netrescue.io.TextFormat;
import netrescue.sim.Simulator;
import netrescue.sim.Transfer;

/**
 * Self-contained test suite (no JUnit needed).
 *
 * Strategy: every data structure is driven with thousands of random
 * operations side by side with the matching java.util class, and every
 * graph algorithm is cross-checked against a different algorithm or a brute
 * force answer. java.util is used ONLY here, as the reference.
 *
 * Run:  java -cp build/classes:build/test-classes netrescue.Tests
 */
public final class Tests {
    static int passed, failed;
    static String section = "";

    public static void main(String[] args) {
        long start = System.nanoTime();
        run("DynamicArray", Tests::dynamicArray);
        run("SinglyLinkedList", Tests::singlyLinkedList);
        run("DoublyLinkedList", Tests::doublyLinkedList);
        run("Stack", Tests::stack);
        run("Queue (ring buffer)", Tests::queue);
        run("Deque", Tests::deque);
        run("MinHeap", Tests::minHeap);
        run("IndexedMinHeap", Tests::indexedHeap);
        run("HashMap (chaining)", Tests::hashMap);
        run("LinearProbingHashMap", Tests::probingMap);
        run("HashSet", Tests::hashSet);
        run("UnionFind", Tests::unionFind);
        run("Trie", Tests::trie);
        run("LRUCache", Tests::lru);
        run("Sorting", Tests::sorting);
        run("Json writer", Tests::json);
        run("Shortest paths agree", Tests::shortestPaths);
        run("Bellman-Ford negative weights", Tests::bellmanNegative);
        run("DFS and components", Tests::dfs);
        run("Tarjan vs brute force", Tests::tarjan);
        run("MST: Kruskal = Prim", Tests::mst);
        run("Routing modes", Tests::routing);
        run("Traces", Tests::traces);
        run("File formats", Tests::formats);
        run("Undo / redo", Tests::undoRedo);
        run("Simulator: rollback and reroute", Tests::simRollback);
        run("Simulator: invariants under random load", Tests::simInvariants);
        System.out.printf("%n%d checks passed, %d failed  (%.1f s)%n", passed, failed, (System.nanoTime() - start) / 1e9);
        if (failed > 0) System.exit(1);
    }

    interface Body { void run() throws Exception; }

    static void run(String name, Body b) {
        section = name;
        int before = failed;
        try { b.run(); }
        catch (Throwable t) { failed++; System.out.println("  FAIL " + name + ": threw " + t); t.printStackTrace(System.out); }
        System.out.println((failed == before ? "  ok   " : "  FAIL ") + name);
    }

    static void check(boolean cond, String what) {
        if (cond) passed++;
        else { failed++; System.out.println("    x " + section + ": " + what); }
    }

    static void eq(Object expected, Object actual, String what) {
        check(expected == null ? actual == null : expected.equals(actual), what + " (expected " + expected + ", got " + actual + ")");
    }

    static void throwsEx(Runnable r, String what) {
        try { r.run(); check(false, what + " should throw"); }
        catch (RuntimeException e) { passed++; }
    }

    // ================================================================== data structures
    static void dynamicArray() {
        Random r = new Random(1);
        DynamicArray<Integer> a = new DynamicArray<>(1);
        ArrayList<Integer> ref = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            int op = r.nextInt(6);
            if (op <= 1 || ref.isEmpty()) { int v = r.nextInt(); a.add(v); ref.add(v); }
            else if (op == 2) { int k = r.nextInt(ref.size() + 1); int v = r.nextInt(); a.insertAt(k, v); ref.add(k, v); }
            else if (op == 3) { int k = r.nextInt(ref.size()); eq(ref.remove(k), a.removeAt(k), "removeAt"); }
            else if (op == 4) { eq(ref.remove(ref.size() - 1), a.removeLast(), "removeLast"); }
            else { int k = r.nextInt(ref.size()); eq(ref.get(k), a.get(k), "get"); }
        }
        eq(ref.size(), a.size(), "size");
        boolean same = true;
        int i = 0;
        for (int v : a) same &= v == ref.get(i++);
        check(same, "iteration order matches");
        throwsEx(() -> a.get(a.size()), "get out of range");
        throwsEx(() -> new DynamicArray<Integer>().removeLast(), "removeLast on empty");
        DynamicArray<Integer> rv = new DynamicArray<>();
        for (int k = 0; k < 5; k++) rv.add(k);
        rv.reverse();
        eq("[4, 3, 2, 1, 0]", rv.toString(), "reverse");
    }

    static void singlyLinkedList() {
        Random r = new Random(2);
        SinglyLinkedList<Integer> l = new SinglyLinkedList<>();
        LinkedList<Integer> ref = new LinkedList<>();
        for (int i = 0; i < 20000; i++) {
            int op = r.nextInt(5), v = r.nextInt(50);
            switch (op) {
                case 0 -> { l.addFirst(v); ref.addFirst(v); }
                case 1 -> { l.addLast(v); ref.addLast(v); }
                case 2 -> { if (!ref.isEmpty()) eq(ref.removeFirst(), l.removeFirst(), "removeFirst"); }
                case 3 -> eq(ref.remove((Integer) v), l.remove(v), "remove(value)");
                default -> eq(ref.contains(v), l.contains(v), "contains");
            }
        }
        eq(ref.size(), l.size(), "size");
        eq(ref.toString(), toList(l).toString(), "contents");
        java.util.Collections.reverse(ref);
        l.reverse();
        eq(ref.toString(), toList(l).toString(), "reverse");
        l.addLast(999); ref.addLast(999);
        eq(ref.toString(), toList(l).toString(), "tail pointer valid after reverse");
    }

    static <T> ArrayList<T> toList(Iterable<T> it) { ArrayList<T> a = new ArrayList<>(); for (T t : it) a.add(t); return a; }

    static void doublyLinkedList() {
        Random r = new Random(3);
        DoublyLinkedList<Integer> l = new DoublyLinkedList<>();
        ArrayDeque<Integer> ref = new ArrayDeque<>();
        for (int i = 0; i < 20000; i++) {
            int op = r.nextInt(4), v = r.nextInt();
            if (ref.isEmpty() || op < 2) { if (op % 2 == 0) { l.addFirst(v); ref.addFirst(v); } else { l.addLast(v); ref.addLast(v); } }
            else if (op == 2) eq(ref.removeFirst(), l.removeFirst(), "removeFirst");
            else eq(ref.removeLast(), l.removeLast(), "removeLast");
        }
        eq(new ArrayList<>(ref).toString(), toList(l).toString(), "contents");
        ArrayList<Integer> refList = new ArrayList<>(ref);
        for (int k = 0; k < refList.size(); k += Math.max(1, refList.size() / 7)) eq(refList.get(k), l.get(k), "get(" + k + ")");
        DoublyLinkedList<String> m = new DoublyLinkedList<>();
        DoublyLinkedList.Node<String> a = m.addLast("a");
        m.addLast("b");
        DoublyLinkedList.Node<String> c = m.addLast("c");
        m.moveToFront(c);
        eq("[c, a, b]", toList(m).toString(), "moveToFront");
        m.unlink(a);
        eq("[c, b]", toList(m).toString(), "unlink");
        eq(2, m.size(), "size after unlink");
    }

    static void stack() {
        Stack<Integer> s = new Stack<>();
        ArrayDeque<Integer> ref = new ArrayDeque<>();
        Random r = new Random(4);
        for (int i = 0; i < 20000; i++) {
            if (ref.isEmpty() || r.nextBoolean()) { int v = r.nextInt(); s.push(v); ref.push(v); }
            else eq(ref.pop(), s.pop(), "pop");
            if (!ref.isEmpty()) eq(ref.peek(), s.peek(), "peek");
        }
        eq(new ArrayList<>(ref).toString(), s.topToBottom().toString(), "top-to-bottom order");
        throwsEx(() -> new Stack<Integer>().pop(), "pop on empty");
    }

    static void queue() {
        Queue<Integer> q = new Queue<>(1);
        ArrayDeque<Integer> ref = new ArrayDeque<>();
        Random r = new Random(5);
        for (int i = 0; i < 30000; i++) {
            if (ref.isEmpty() || r.nextInt(3) > 0) { int v = r.nextInt(); q.enqueue(v); ref.addLast(v); }
            else eq(ref.pollFirst(), q.dequeue(), "dequeue");
        }
        eq(new ArrayList<>(ref).toString(), q.frontToBack().toString(), "front-to-back after wrap-arounds");
        // force wrap-around then growth
        Queue<Integer> w = new Queue<>(4);
        w.enqueue(1); w.enqueue(2); w.enqueue(3); w.dequeue(); w.dequeue(); w.enqueue(4); w.enqueue(5); w.enqueue(6); w.enqueue(7);
        eq("[3, 4, 5, 6, 7]", w.frontToBack().toString(), "grow while wrapped");
        throwsEx(() -> new Queue<Integer>().dequeue(), "dequeue on empty");
    }

    static void deque() {
        Deque<Integer> d = new Deque<>();
        ArrayDeque<Integer> ref = new ArrayDeque<>();
        Random r = new Random(6);
        for (int i = 0; i < 30000; i++) {
            int op = r.nextInt(4), v = r.nextInt();
            if (ref.isEmpty() || op < 2) { if (op == 0) { d.addFirst(v); ref.addFirst(v); } else { d.addLast(v); ref.addLast(v); } }
            else if (op == 2) eq(ref.pollFirst(), d.pollFirst(), "pollFirst");
            else eq(ref.pollLast(), d.pollLast(), "pollLast");
        }
        eq(ref.size(), d.size(), "size");
        int k = 0; boolean same = true;
        for (int v : ref) same &= d.get(k++) == v;
        check(same, "contents");
    }

    static void minHeap() {
        MinHeap<Integer> h = new MinHeap<>(Integer::compare);
        PriorityQueue<Integer> ref = new PriorityQueue<>();
        Random r = new Random(7);
        for (int i = 0; i < 30000; i++) {
            if (ref.isEmpty() || r.nextInt(3) > 0) { int v = r.nextInt(1000); h.insert(v); ref.add(v); }
            else eq(ref.poll(), h.extractMin(), "extractMin");
            if (i % 997 == 0) check(h.isValid(), "heap property");
        }
        DynamicArray<Integer> items = new DynamicArray<>();
        for (int i = 0; i < 1000; i++) items.add(r.nextInt());
        MinHeap<Integer> hh = MinHeap.heapify(items, Integer::compare);
        check(hh.isValid(), "heapify gives a valid heap");
        int prev = Integer.MIN_VALUE; boolean sorted = true;
        while (!hh.isEmpty()) { int v = hh.extractMin(); sorted &= v >= prev; prev = v; }
        check(sorted, "heap sort order");
    }

    static void indexedHeap() {
        Random r = new Random(8);
        int n = 500;
        IndexedMinHeap h = new IndexedMinHeap(n);
        double[] pr = new double[n];
        boolean[] in = new boolean[n];
        for (int i = 0; i < 20000; i++) {
            int k = r.nextInt(n), op = r.nextInt(3);
            if (op == 0 && !in[k]) { pr[k] = r.nextDouble() * 1000; h.insert(k, pr[k]); in[k] = true; }
            else if (op == 1 && in[k]) { pr[k] -= r.nextDouble() * 50; h.decreaseKey(k, pr[k]); }
            else if (op == 2 && !h.isEmpty()) {
                double best = Double.MAX_VALUE;
                for (int x = 0; x < n; x++) if (in[x]) best = Math.min(best, pr[x]);
                int got = h.extractMin();
                eq(best, pr[got], "extractMin returns smallest priority");
                in[got] = false;
            }
            eq(in[k], h.contains(k), "contains");
        }
        throwsEx(() -> { IndexedMinHeap x = new IndexedMinHeap(2); x.insert(0, 1); x.decreaseKey(0, 5); }, "decreaseKey to a larger value");
    }

    /** A key class whose hash codes collide a lot, to stress the chains. */
    record BadKey(int v) { @Override public int hashCode() { return v % 7; } }

    static void hashMap() {
        Random r = new Random(9);
        HashMap<Integer, Integer> m = new HashMap<>(2);
        java.util.HashMap<Integer, Integer> ref = new java.util.HashMap<>();
        for (int i = 0; i < 50000; i++) {
            int k = r.nextInt(5000), op = r.nextInt(3);
            if (op == 0) eq(ref.put(k, i), m.put(k, i), "put returns old value");
            else if (op == 1) eq(ref.get(k), m.get(k), "get");
            else eq(ref.remove(k), m.remove(k), "remove");
        }
        eq(ref.size(), m.size(), "size");
        check(m.loadFactor() <= 0.75, "load factor kept under 0.75");
        check(m.resizeCount() > 0, "table resized as it grew");
        TreeSet<Integer> a = new TreeSet<>(ref.keySet()), b = new TreeSet<>();
        for (int k : m.keys()) b.add(k);
        eq(a, b, "key set");
        HashMap<BadKey, String> bad = new HashMap<>();
        for (int i = 0; i < 200; i++) bad.put(new BadKey(i), "v" + i);
        boolean all = true;
        for (int i = 0; i < 200; i++) all &= ("v" + i).equals(bad.get(new BadKey(i)));
        check(all, "colliding keys all retrievable");
        HashMap<String, Integer> nul = new HashMap<>();
        nul.put(null, 1);
        eq(1, nul.get(null), "null key");
    }

    static void probingMap() {
        Random r = new Random(10);
        LinearProbingHashMap<Integer, Integer> m = new LinearProbingHashMap<>(2);
        java.util.HashMap<Integer, Integer> ref = new java.util.HashMap<>();
        for (int i = 0; i < 50000; i++) {
            int k = r.nextInt(3000), op = r.nextInt(3);
            if (op == 0) { m.put(k, i); ref.put(k, i); }
            else if (op == 1) eq(ref.get(k), m.get(k), "get (after tombstones)");
            else eq(ref.remove(k), m.remove(k), "remove");
        }
        eq(ref.size(), m.size(), "size");
        LinearProbingHashMap<BadKey, Integer> bad = new LinearProbingHashMap<>();
        for (int i = 0; i < 300; i++) bad.put(new BadKey(i), i);
        for (int i = 0; i < 300; i += 2) bad.remove(new BadKey(i));
        boolean ok = true;
        for (int i = 0; i < 300; i++) ok &= (i % 2 == 0) ? bad.get(new BadKey(i)) == null : bad.get(new BadKey(i)) == i;
        check(ok, "probing past tombstones with colliding keys");
    }

    static void hashSet() {
        HashSet<String> s = new HashSet<>();
        check(s.add("a"), "add new");
        check(!s.add("a"), "add duplicate returns false");
        check(s.contains("a"), "contains");
        check(s.remove("a"), "remove");
        check(!s.contains("a"), "gone");
    }

    static void unionFind() {
        Random r = new Random(11);
        int n = 300;
        UnionFind uf = new UnionFind(n);
        int[] label = new int[n];
        for (int i = 0; i < n; i++) label[i] = i;
        for (int i = 0; i < 2000; i++) {
            int a = r.nextInt(n), b = r.nextInt(n);
            boolean merged = uf.union(a, b);
            eq(label[a] != label[b], merged, "union reports a merge only for different sets");
            if (label[a] != label[b]) { int from = label[b], to = label[a]; for (int k = 0; k < n; k++) if (label[k] == from) label[k] = to; }
            int x = r.nextInt(n), y = r.nextInt(n);
            eq(label[x] == label[y], uf.connected(x, y), "connected");
        }
        java.util.HashSet<Integer> distinct = new java.util.HashSet<>();
        for (int l : label) distinct.add(l);
        eq(distinct.size(), uf.components(), "component count");
    }

    static void trie() {
        Trie t = new Trie();
        TreeSet<String> ref = new TreeSet<>();
        Random r = new Random(12);
        String alphabet = "abcde";
        for (int i = 0; i < 3000; i++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + r.nextInt(5);
            for (int k = 0; k < len; k++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
            String w = sb.toString();
            if (r.nextInt(4) == 0) { eq(ref.remove(w), t.remove(w), "remove " + w); }
            else { t.insert(w); ref.add(w); }
        }
        eq(ref.size(), t.size(), "word count");
        for (String p : new String[]{"", "a", "ab", "cde", "eee", "zz"}) {
            ArrayList<String> exp = new ArrayList<>();
            for (String w : ref) if (w.startsWith(p)) exp.add(w);
            eq(exp.toString(), toList(t.startsWith(p, 100000)).toString(), "prefix '" + p + "'");
            eq(exp.size(), t.countWithPrefix(p), "count with prefix '" + p + "'");
        }
        Trie names = new Trie();
        names.insert("New York");
        names.insert("Newark");
        eq("[New York]", names.startsWith("new ", 5).toString(), "case-insensitive with spaces");
    }

    static void lru() {
        int cap = 5;
        LRUCache<Integer, Integer> c = new LRUCache<>(cap);
        LinkedHashMap<Integer, Integer> ref = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer, Integer> e) { return size() > cap; }
        };
        Random r = new Random(13);
        for (int i = 0; i < 20000; i++) {
            int k = r.nextInt(12);
            if (r.nextBoolean()) { c.put(k, i); ref.put(k, i); }
            else eq(ref.get(k), c.get(k), "get");
        }
        ArrayList<Integer> order = new ArrayList<>(ref.keySet());
        java.util.Collections.reverse(order);   // LinkedHashMap is oldest-first
        eq(order.toString(), c.keysByRecency().toString(), "recency order");
        int removed = c.invalidateIf((k, v) -> k % 2 == 0);
        check(removed >= 0 && c.size() <= cap, "invalidateIf");
        for (int k : c.keysByRecency()) check(k % 2 == 1, "only odd keys remain");
    }

    static void sorting() {
        Random r = new Random(14);
        for (int n : new int[]{0, 1, 2, 3, 10, 100, 5000}) {
            Integer[] ref = new Integer[n];
            DynamicArray<Integer> a = new DynamicArray<>(), b = new DynamicArray<>();
            for (int i = 0; i < n; i++) { ref[i] = r.nextInt(50); a.add(ref[i]); b.add(ref[i]); }
            Arrays.sort(ref);
            Sorting.mergeSort(a, Integer::compare);
            Sorting.quickSort(b, Integer::compare);
            eq(Arrays.toString(ref), a.toString(), "merge sort n=" + n);
            eq(Arrays.toString(ref), b.toString(), "quicksort n=" + n);
        }
        DynamicArray<int[]> pairs = new DynamicArray<>();
        for (int i = 0; i < 2000; i++) pairs.add(new int[]{r.nextInt(10), i});
        Sorting.mergeSort(pairs, (x, y) -> Integer.compare(x[0], y[0]));
        boolean stable = true;
        for (int i = 1; i < pairs.size(); i++) if (pairs.get(i)[0] == pairs.get(i - 1)[0] && pairs.get(i)[1] < pairs.get(i - 1)[1]) stable = false;
        check(stable, "merge sort is stable");
        DynamicArray<Integer> sortedIn = new DynamicArray<>();
        for (int i = 0; i < 20000; i++) sortedIn.add(i);
        Sorting.quickSort(sortedIn, Integer::compare);
        check(Sorting.lastComparisons < 20000L * 40, "quicksort on sorted input stays n log n (median of three)");
    }

    static void json() {
        Json j = new Json();
        j.obj().put("a", "x\"y\\z\n").key("b").arr().val(1).val(2.5).val(true).nul().obj().end().end().key("c").obj().put("d", Double.NaN).end().end();
        eq("{\"a\":\"x\\\"y\\\\z\\n\",\"b\":[1,2.5,true,null,{}],\"c\":{\"d\":null}}", j.toString(), "structure and escaping");
    }

    // ================================================================== graph algorithms
    static Graph randomGraph(Random r, int n, double p, boolean connected) {
        Graph g = new Graph();
        for (int i = 0; i < n; i++) g.addNode("N" + i, r.nextInt(900), r.nextInt(600));
        for (int i = 0; i < n; i++) for (int k = i + 1; k < n; k++) if (r.nextDouble() < p) g.addEdge(i, k, 1 + r.nextInt(20), 10 + r.nextInt(30));
        if (connected) Generators.connect(g, r);
        return g;
    }

    static void shortestPaths() {
        Random r = new Random(15);
        for (int trial = 0; trial < 60; trial++) {
            Graph g = randomGraph(r, 3 + r.nextInt(25), 0.15, trial % 3 != 0);
            if (trial % 4 == 0) for (Edge e : g.allEdges()) if (r.nextInt(5) == 0) e.up = false;
            int n = g.nodeSlots();
            FloydWarshall.Result fw = FloydWarshall.run(g);
            for (int s = 0; s < n; s++) {
                double[] dj = Dijkstra.allFrom(g, s, null);
                double[] arr = Dijkstra.arrayVersion(g, s);
                BellmanFord.Result bf = BellmanFord.run(g, s, null);
                for (int t = 0; t < n; t++) {
                    double f = fw.dist[s][t];
                    check(close(f, dj[t]), "Dijkstra = Floyd-Warshall");
                    check(close(f, arr[t]), "array Dijkstra = Floyd-Warshall");
                    check(close(f, bf.dist[t]), "Bellman-Ford = Floyd-Warshall");
                }
                int t = r.nextInt(n);
                PathResult p = Dijkstra.shortestPath(g, s, t, null, null);
                eq(fw.dist[s][t] < Double.POSITIVE_INFINITY, p.found(), "path found iff reachable");
                if (p.found()) {
                    check(close(fw.dist[s][t], p.cost), "path cost = distance");
                    check(validPath(g, p), "Dijkstra path is a real walk over usable links");
                    // BFS: hop count must equal unit-weight Floyd
                    PathResult b = BFS.fewestHops(g, s, t, null, null);
                    check(validPath(g, b), "BFS path valid");
                    eq(hopsBrute(g, s, t), b.hops(), "BFS = fewest hops");
                    if (fw.diameterFrom >= 0 && fw.connected) check(fw.diameter >= fw.dist[s][t] - 1e-9, "diameter is the maximum distance");
                }
            }
        }
    }

    static boolean close(double a, double b) {
        if (Double.isInfinite(a) || Double.isInfinite(b)) return a == b;
        return Math.abs(a - b) < 1e-6;
    }

    static boolean validPath(Graph g, PathResult p) {
        if (p.nodes.size() != p.edges.size() + 1) return false;
        if (p.nodes.get(0) != p.src || p.nodes.last() != p.dst) return false;
        double c = 0;
        for (int i = 0; i < p.edges.size(); i++) {
            Edge e = g.edge(p.edges.get(i));
            if (!g.edgeUsable(e)) return false;
            int a = p.nodes.get(i), b = p.nodes.get(i + 1);
            if (!((e.u == a && e.v == b) || (e.u == b && e.v == a))) return false;
            c += e.cost;
        }
        return Math.abs(c - p.cost) < 1e-6;
    }

    /** Hop distance by Bellman-Ford style relaxation with unit weights (brute force). */
    static int hopsBrute(Graph g, int s, int t) {
        int n = g.nodeSlots();
        int[] d = new int[n];
        Arrays.fill(d, Integer.MAX_VALUE / 2);
        d[s] = 0;
        for (int round = 0; round < n; round++)
            for (Edge e : g.allEdges()) if (g.edgeUsable(e)) {
                d[e.v] = Math.min(d[e.v], d[e.u] + 1);
                d[e.u] = Math.min(d[e.u], d[e.v] + 1);
            }
        return d[t];
    }

    static void bellmanNegative() {
        // 0->1 (4), 0->2 (5), 2->1 (-3): true shortest to 1 is 2 via 2.
        BellmanFord.Result r = BellmanFord.directed(3, new int[]{0, 0, 2}, new int[]{1, 2, 1}, new double[]{4, 5, -3}, 0);
        check(!r.negativeCycle, "no negative cycle");
        eq(2.0, r.dist[1], "negative edge handled");
        BellmanFord.Result c = BellmanFord.directed(3, new int[]{0, 1, 2}, new int[]{1, 2, 1}, new double[]{1, -2, 1}, 0);
        check(c.negativeCycle, "negative cycle detected");
    }

    static void dfs() {
        Random r = new Random(16);
        for (int trial = 0; trial < 40; trial++) {
            Graph g = randomGraph(r, 2 + r.nextInt(30), 0.08, false);
            int[] comp = DFS.components(g);
            UnionFind uf = new UnionFind(g.nodeSlots());
            for (Edge e : g.allEdges()) if (g.edgeUsable(e)) uf.union(e.u, e.v);
            boolean agree = true;
            for (int a = 0; a < g.nodeSlots(); a++) for (int b = 0; b < g.nodeSlots(); b++)
                agree &= (comp[a] == comp[b]) == uf.connected(a, b);
            check(agree, "DFS components = union-find components");
            int s = r.nextInt(g.nodeSlots());
            DynamicArray<Integer> it = DFS.iterative(g, s, null), rec = DFS.recursive(g, s);
            TreeSet<Integer> a = new TreeSet<>(), b = new TreeSet<>();
            for (int x : it) a.add(x);
            for (int x : rec) b.add(x);
            eq(a, b, "iterative and recursive DFS reach the same routers");
            boolean[] reach = BFS.reachable(g, s);
            int cnt = 0;
            for (boolean x : reach) if (x) cnt++;
            eq(cnt, it.size(), "BFS and DFS reach the same number of routers");
        }
    }

    static void tarjan() {
        Random r = new Random(17);
        for (int trial = 0; trial < 60; trial++) {
            Graph g = randomGraph(r, 2 + r.nextInt(18), 0.18, trial % 2 == 0);
            Tarjan.Result t = Tarjan.run(g, null);
            TreeSet<Integer> aps = new TreeSet<>(), br = new TreeSet<>();
            for (int x : t.articulationPoints) aps.add(x);
            for (int x : t.bridges) br.add(x);
            int base = Resilience.componentsWithout(g, -1, -1)[0];
            TreeSet<Integer> apBrute = new TreeSet<>(), brBrute = new TreeSet<>();
            for (int v = 0; v < g.nodeSlots(); v++) {
                // removing an isolated router lowers the count; only a rise means it was critical
                if (Resilience.componentsWithout(g, v, -1)[0] > base) apBrute.add(v);
            }
            for (Edge e : g.allEdges()) if (Resilience.componentsWithout(g, -1, e.id)[0] > base) brBrute.add(e.id);
            eq(apBrute, aps, "articulation points");
            eq(brBrute, br, "bridges");
        }
        Graph c = Samples.load("classroom");
        Tarjan.Result t = Tarjan.run(c, null);
        TreeSet<String> names = new TreeSet<>();
        for (int x : t.articulationPoints) names.add(c.name(x));
        eq("[D, E, G, H]", names.toString(), "classroom critical routers");
        eq(3, t.bridges.size(), "classroom critical links");
        Resilience.Report rep = Resilience.analyse(c);
        // removing E leaves {A,B,C,D} and {F,G,H,I,J}: 4 routers cut off from the larger piece
        eq("E", c.name(rep.routerImpact.get(0).id), "highest-impact router");
        eq(4, rep.routerImpact.get(0).cutOff, "E cuts off 4 routers");
        check(Resilience.analyse(Samples.load("mesh")).tarjan.articulationPoints.isEmpty(), "mesh has no critical routers");
    }

    static void mst() {
        Random r = new Random(18);
        for (int trial = 0; trial < 50; trial++) {
            Graph g = randomGraph(r, 2 + r.nextInt(30), 0.2, trial % 3 != 0);
            MST.Result k = MST.kruskal(g, false, null), kq = MST.kruskal(g, true, null), p = MST.prim(g, null);
            check(close(k.totalCost, p.totalCost), "Kruskal cost = Prim cost");
            check(close(k.totalCost, kq.totalCost), "merge vs quicksort Kruskal agree");
            eq(k.edges.size(), p.edges.size(), "same number of links");
            int comps = Resilience.componentsWithout(g, -1, -1)[0];
            eq(g.upNodeCount() - comps, k.edges.size(), "spanning forest has V - components links");
            UnionFind uf = new UnionFind(g.nodeSlots());
            boolean acyclic = true;
            for (int e : k.edges) acyclic &= uf.union(g.edge(e).u, g.edge(e).v);
            check(acyclic, "MST has no cycle");
        }
        // brute force on tiny graphs: try every subset of V-1 edges
        for (int trial = 0; trial < 30; trial++) {
            Graph g = randomGraph(r, 3 + r.nextInt(4), 0.6, true);
            int m = g.edgeSlots(), n = g.nodeSlots();
            double best = Double.MAX_VALUE;
            for (int mask = 0; mask < (1 << m); mask++) {
                if (Integer.bitCount(mask) != n - 1) continue;
                UnionFind uf = new UnionFind(n);
                double c = 0;
                boolean ok = true;
                for (int e = 0; e < m; e++) if ((mask >> e & 1) == 1) { ok &= uf.union(g.edge(e).u, g.edge(e).v); c += g.edge(e).cost; }
                if (ok) best = Math.min(best, c);
            }
            check(close(best, MST.kruskal(g, false, null).totalCost), "Kruskal = brute-force minimum");
        }
    }

    static void routing() {
        Graph g = Samples.load("india");
        int a = g.requireId("Srinagar"), b = g.requireId("Guwahati");
        PathResult best = Dijkstra.shortestPath(g, a, b, null, null);
        DynamicArray<PathResult> alts = Routing.alternatives(g, a, b, 3, 3.0);
        check(alts.size() >= 2, "found alternatives");
        check(close(best.cost, alts.get(0).cost), "first alternative is the cheapest route");
        java.util.HashSet<String> sigs = new java.util.HashSet<>();
        for (PathResult p : alts) { check(validPath(g, p), "alternative is a valid route"); check(sigs.add(p.edges.toString()), "alternatives are distinct"); }
        Tarjan.Result t = Tarjan.run(g, null);
        HashSet<Integer> bridges = new HashSet<>();
        for (int x : t.bridges) bridges.add(x);
        PathResult safe = Routing.safest(g, g.requireId("Mumbai"), g.requireId("Chennai"), bridges);
        check(safe.found() && validPath(g, safe), "safest route valid");
        int crossed = 0;
        for (int e : safe.edges) if (bridges.contains(e)) crossed++;
        eq(0, crossed, "Mumbai-Chennai can avoid all critical links");
        // capacity filter
        PathResult none = Dijkstra.shortestPath(g, a, b, e -> e.capacity >= 1000, null);
        check(!none.found(), "no route when no link has room");
    }

    static void traces() {
        Graph g = Samples.load("classroom");
        for (String algo : new String[]{"bfs", "dijkstra", "bellman", "dfs", "tarjan", "kruskal", "prim"}) {
            Trace t = new Trace(algo);
            switch (algo) {
                case "bfs" -> BFS.fewestHops(g, 0, 9, null, t);
                case "dijkstra" -> Dijkstra.shortestPath(g, 0, 9, null, t);
                case "bellman" -> BellmanFord.path(g, 0, 9, t);
                case "dfs" -> DFS.iterative(g, 0, t);
                case "tarjan" -> Tarjan.run(g, t);
                case "kruskal" -> MST.kruskal(g, false, t);
                default -> MST.prim(g, t);
            }
            check(t.size() > 3, algo + " recorded steps");
            Json j = new Json();
            t.toJson(j);
            check(j.toString().startsWith("{\"algorithm\""), algo + " trace serialises");
        }
        // results with tracing on must equal results with tracing off
        check(close(Dijkstra.shortestPath(g, 0, 9, null, new Trace("x")).cost, Dijkstra.shortestPath(g, 0, 9, null, null).cost), "tracing doesn't change Dijkstra");
    }

    static void formats() {
        for (String[] s : Samples.LIST) {
            Graph g = Samples.load(s[0]);
            check(g.liveNodeCount() > 0 && g.liveEdgeCount() > 0, "sample " + s[0] + " loads");
            Graph back = TextFormat.parse(TextFormat.write(g));
            eq(g.liveNodeCount(), back.liveNodeCount(), s[0] + " round trip routers");
            eq(g.liveEdgeCount(), back.liveEdgeCount(), s[0] + " round trip links");
            check(close(MST.kruskal(g, false, null).totalCost, MST.kruskal(back, false, null).totalCost), s[0] + " round trip costs");
        }
        Graph ab = Samples.load("abilene");
        eq(11, ab.liveNodeCount(), "Abilene routers");
        eq(14, ab.liveEdgeCount(), "Abilene links");
        check(ab.idOf("New York") != null, "quoted labels with spaces");
        throwsEx(() -> GmlParser.parse("graph [ node [ id 0 ]"), "missing ]");
        throwsEx(() -> GmlParser.parse("graph [ node [ id 0 ] ] ]"), "extra ]");
        throwsEx(() -> GmlParser.parse("graph [ edge [ source 0 target 1 ] ]"), "no nodes");
        Graph noCoords = GmlParser.parse("graph [ node [ id 1 label \"x\" ] node [ id 2 ] edge [ source 1 target 2 ] edge [ source 2 target 1 ] ]");
        eq(1, noCoords.liveEdgeCount(), "parallel edge skipped");
        throwsEx(() -> TextFormat.parse("node A 1 1\nlink A B 3 10"), "link to unknown router");
        throwsEx(() -> TextFormat.parse("node A 1 1\nnode A 2 2"), "duplicate router");
        Graph q = TextFormat.parse("node \"New Delhi\" 1 2\nnode B 3 4\nlink \"New Delhi\" B 2.5 10");
        eq(2.5, q.edge(0).cost, "quoted names in text format");
        check(Math.abs(GmlParser.haversineKm(28.61, 77.21, 17.39, 78.49) - 1253) < 15, "Delhi-Hyderabad ~1253 km");
    }

    static void undoRedo() {
        Graph g = new Graph();
        EditHistory h = new EditHistory();
        EditHistory.AddNode a = new EditHistory.AddNode("A", 0, 0), b = new EditHistory.AddNode("B", 10, 0);
        h.run(g, a); h.run(g, b);
        EditHistory.AddEdge ab = new EditHistory.AddEdge(g, a.id(), b.id(), 5, 10);
        h.run(g, ab);
        h.run(g, new EditHistory.UpdateEdge(g, ab.id(), 7, 20));
        h.run(g, new EditHistory.DeleteNode(g, b.id()));
        eq(1, g.liveNodeCount(), "B deleted");
        eq(0, g.liveEdgeCount(), "its link deleted too");
        h.undo(g);
        eq(2, g.liveNodeCount(), "undo delete restores B");
        eq(1, g.liveEdgeCount(), "and its link");
        eq(7.0, g.edge(ab.id()).cost, "edit kept");
        h.undo(g);
        eq(5.0, g.edge(ab.id()).cost, "undo edit");
        h.redo(g);
        eq(7.0, g.edge(ab.id()).cost, "redo edit");
        h.undo(g); h.undo(g); h.undo(g); h.undo(g);
        eq(0, g.liveNodeCount(), "undo everything");
        throwsEx(() -> h.undo(g), "undo with empty history");
        h.redo(g); h.redo(g); h.redo(g);
        eq(2, g.liveNodeCount(), "redo routers");
        eq(1, g.liveEdgeCount(), "redo link");
        check(g.searchNames("a", 5).contains("A"), "trie updated by undo/redo");
        h.run(g, new EditHistory.SetUp(g, false, ab.id(), false));
        check(!g.edge(ab.id()).up, "fail link");
        eq(0, h.redoSize(), "new edit clears redo stack");
        h.undo(g);
        check(g.edge(ab.id()).up, "undo failure");
    }

    // ================================================================== simulator
    static void simRollback() {
        Graph g = Samples.load("mesh");
        Simulator.Settings cfg = new Simulator.Settings();
        cfg.hopDelay = 1.0;
        Simulator s = new Simulator(g, cfg);
        int src = g.requireId("Gate"), dst = g.requireId("Workshop");
        int id = s.submit(src, dst, 5, 1, 0, 3);
        s.runEvents(3);   // arrive, route, first hop reserved at t=0
        Transfer t = s.transfer(id);
        eq(Transfer.State.SETUP, t.state, "walking the route");
        check(t.route.hops() >= 3, "route long enough to fail mid-way");
        int third = t.route.edges.get(2);
        s.scheduleLinkFailure(third, 1.5, 0);   // fails after 2 hops are reserved, before the 3rd
        s.runToEnd(10000);
        check(t.rollbacks >= 1, "rolled back at least once");
        eq(Transfer.State.DONE, t.state, "rerouted and finished");
        check(!t.route.edges.contains(third), "new route avoids the failed link");
        for (Edge e : g.allEdges()) eq(0, e.used, "all capacity released");
        boolean sawRollback = false;
        for (Simulator.Entry en : s.log()) sawRollback |= en.kind.equals("rollback");
        check(sawRollback, "rollback steps appear in the log");

        // unreachable destination: fails after retries
        Graph c = Samples.load("classroom");
        Simulator s2 = new Simulator(c, new Simulator.Settings());
        int bridge = c.findEdge(c.requireId("D"), c.requireId("E")).id;
        s2.scheduleLinkFailure(bridge, 0, 0);
        int id2 = s2.submit(c.requireId("A"), c.requireId("J"), 5, 1, 0.1, 2);
        s2.runToEnd(10000);
        eq(Transfer.State.FAILED, s2.transfer(id2).state, "unreachable transfer fails");
        check(s2.transfer(id2).reason.contains("unreachable"), "reason mentions unreachable");

        // destination repaired in time: waits with backoff, then succeeds
        Graph c3 = Samples.load("classroom");
        Simulator s3 = new Simulator(c3, new Simulator.Settings());
        s3.scheduleLinkFailure(c3.findEdge(c3.requireId("D"), c3.requireId("E")).id, 0, 2.5);
        int id3 = s3.submit(c3.requireId("A"), c3.requireId("J"), 5, 1, 0.1, 2);
        s3.runToEnd(10000);
        eq(Transfer.State.DONE, s3.transfer(id3).state, "waits for repair, then succeeds");
        check(s3.transfer(id3).retries >= 1, "retried with backoff");
    }

    static void simInvariants() {
        for (String sample : new String[]{"classroom", "india", "mesh", "abilene"}) {
            for (int variant = 0; variant < 4; variant++) {
                Graph g = Samples.load(sample);
                Simulator.Settings cfg = new Simulator.Settings();
                cfg.strategy = variant % 2 == 0 ? Simulator.Strategy.CHEAPEST : Simulator.Strategy.FEWEST_HOPS;
                cfg.priorityMode = variant >= 2;
                cfg.useCache = variant != 1;
                cfg.maxActive = 3 + variant;
                Simulator s = new Simulator(g, cfg);
                s.randomTransfers(120, 40, 100 + variant);
                s.randomFailures(25, 40, 5, 200 + variant);
                boolean capOk = true;
                int steps = 0;
                while (s.step()) {
                    steps++;
                    for (Edge e : g.allEdges()) capOk &= e.used >= 0 && e.used <= e.capacity;
                }
                check(capOk, sample + "/" + variant + ": 0 <= used <= capacity at every step");
                int done = 0, failedT = 0;
                boolean allFinished = true;
                for (Transfer t : s.transfers()) {
                    allFinished &= t.finished();
                    if (t.state == Transfer.State.DONE) done++; else failedT++;
                    allFinished &= t.undo.isEmpty();
                }
                check(allFinished, sample + "/" + variant + ": every transfer ended and released its stack");
                eq(120, done + failedT, sample + "/" + variant + ": done + failed = submitted");
                for (Edge e : g.allEdges()) check(e.used == 0, sample + "/" + variant + ": capacity fully released");
                check(done > 0, sample + "/" + variant + ": some transfers succeed");
                check(steps > 120, sample + "/" + variant + ": events processed");
            }
        }
    }
}
