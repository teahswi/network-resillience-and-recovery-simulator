package netrescue.server;

import netrescue.ds.Deque;
import netrescue.ds.DoublyLinkedList;
import netrescue.ds.DynamicArray;
import netrescue.ds.HashMap;
import netrescue.ds.LRUCache;
import netrescue.ds.MinHeap;
import netrescue.ds.Queue;
import netrescue.ds.SinglyLinkedList;
import netrescue.ds.Stack;
import netrescue.ds.Trie;
import netrescue.ds.UnionFind;
import netrescue.io.Json;

/**
 * The "DS Lab" tab: one live instance of each data structure that the user
 * can poke (push, pop, insert, ...) and watch change. Every operation returns
 * a message and a snapshot of the structure's insides for drawing.
 */
public class Lab {
    private DynamicArray<String> array;
    private SinglyLinkedList<String> slist;
    private DoublyLinkedList<String> dlist;
    private Stack<String> stack;
    private Queue<String> queue;
    private Deque<String> deque;
    private MinHeap<Integer> heap;
    private HashMap<String, String> map;
    private Trie trie;
    private UnionFind uf;
    private LRUCache<String, String> lru;
    private String lastFound = "";

    public Lab() { resetAll(); }

    private void resetAll() {
        array = new DynamicArray<>(2);
        slist = new SinglyLinkedList<>();
        dlist = new DoublyLinkedList<>();
        stack = new Stack<>();
        queue = new Queue<>(4);
        deque = new Deque<>();
        heap = new MinHeap<>(Integer::compare);
        map = new HashMap<>(8);
        trie = new Trie();
        for (String w : new String[]{"router", "route", "routing", "rollback", "queue", "quick"}) trie.insert(w);
        uf = new UnionFind(10);
        lru = new LRUCache<>(4);
    }

    /** Runs one operation and writes {msg, ok, snapshot} into j. */
    public void op(String ds, String op, String a, String b, Json j) {
        String msg;
        boolean ok = true;
        try {
            msg = apply(ds, op, a == null ? "" : a.trim(), b == null ? "" : b.trim());
        } catch (RuntimeException ex) {
            msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            ok = false;
        }
        j.obj().put("ok", ok).put("msg", msg).key("snap");
        snapshot(ds, j);
        j.end();
    }

    private String apply(String ds, String op, String a, String b) {
        switch (ds + "." + op) {
            // dynamic array
            case "array.add" -> { array.add(need(a)); return "add(" + a + "): size " + array.size() + ", capacity " + array.capacity(); }
            case "array.insert" -> { int i = idx(b); array.insertAt(i, need(a)); return "insertAt(" + i + ", " + a + "): shifted " + (array.size() - 1 - i) + " element(s) right"; }
            case "array.removeAt" -> { int i = idx(b); String v = array.removeAt(i); return "removeAt(" + i + ") = " + v + ": shifted " + (array.size() - i) + " element(s) left"; }
            case "array.get" -> { int i = idx(b); return "get(" + i + ") = " + array.get(i) + "  (O(1): direct index)"; }
            // singly linked list
            case "slist.addFirst" -> { slist.addFirst(need(a)); return "addFirst(" + a + ")  O(1)"; }
            case "slist.addLast" -> { slist.addLast(need(a)); return "addLast(" + a + ")  O(1) thanks to the tail pointer"; }
            case "slist.removeFirst" -> { return "removeFirst() = " + slist.removeFirst(); }
            case "slist.remove" -> { return slist.remove(need(a)) ? "removed " + a : a + " not found"; }
            case "slist.reverse" -> { slist.reverse(); return "reversed by flipping every next pointer"; }
            // doubly linked list
            case "dlist.addFirst" -> { dlist.addFirst(need(a)); return "addFirst(" + a + ")"; }
            case "dlist.addLast" -> { dlist.addLast(need(a)); return "addLast(" + a + ")"; }
            case "dlist.removeFirst" -> { return "removeFirst() = " + dlist.removeFirst(); }
            case "dlist.removeLast" -> { return "removeLast() = " + dlist.removeLast() + "  (O(1): prev pointer)"; }
            // stack
            case "stack.push" -> { stack.push(need(a)); return "push(" + a + ")"; }
            case "stack.pop" -> { return "pop() = " + stack.pop(); }
            case "stack.peek" -> { return "peek() = " + stack.peek(); }
            // queue
            case "queue.enqueue" -> { int cap = queue.capacity(); queue.enqueue(need(a)); return "enqueue(" + a + ")" + (queue.capacity() > cap ? ": buffer was full, doubled to " + queue.capacity() : ""); }
            case "queue.dequeue" -> { return "dequeue() = " + queue.dequeue() + ": head moves forward"; }
            case "queue.peek" -> { return "peek() = " + queue.peek(); }
            // deque
            case "deque.addFirst" -> { deque.addFirst(need(a)); return "addFirst(" + a + ")"; }
            case "deque.addLast" -> { deque.addLast(need(a)); return "addLast(" + a + ")"; }
            case "deque.pollFirst" -> { return "pollFirst() = " + deque.pollFirst(); }
            case "deque.pollLast" -> { return "pollLast() = " + deque.pollLast(); }
            // heap
            case "heap.insert" -> { int v = num(a); heap.insert(v); return "insert(" + v + "): appended, then sifted up"; }
            case "heap.extract" -> { return "extractMin() = " + heap.extractMin() + ": last element moved to root, then sifted down"; }
            case "heap.random" -> {
                java.util.Random r = new java.util.Random();
                DynamicArray<Integer> items = new DynamicArray<>();
                for (int i = 0; i < 10; i++) items.add(1 + r.nextInt(99));
                heap = MinHeap.heapify(items, Integer::compare);
                return "heapify " + items + " in O(n) (bottom-up sift-down)";
            }
            // hash map
            case "map.put" -> {
                int cap = map.capacity();
                String old = map.put(need(a), b.isEmpty() ? a.toUpperCase() : b);
                String s = "put(" + a + "): bucket " + bucket(a);
                if (old != null) s += ", replaced old value " + old;
                if (map.capacity() > cap) s += ". Load factor passed 0.75: RESIZED " + cap + " -> " + map.capacity() + " and rehashed every key";
                return s;
            }
            case "map.get" -> { String v = map.get(need(a)); return "get(" + a + ") = " + v + "  (bucket " + bucket(a) + ")"; }
            case "map.remove" -> { String v = map.remove(need(a)); return v == null ? a + " not found" : "remove(" + a + ") = " + v; }
            case "map.fill" -> {
                for (String w : new String[]{"Delhi", "Mumbai", "Pune", "Agra", "Goa", "Surat", "Indore", "Kochi", "Vizag", "Ooty"}) map.put(w, w.substring(0, 3).toUpperCase());
                return "inserted 10 city keys";
            }
            // trie
            case "trie.insert" -> { trie.insert(need(a)); return "insert(" + a + ")"; }
            case "trie.remove" -> { return trie.remove(need(a)) ? "removed " + a : a + " not in trie"; }
            case "trie.search" -> {
                DynamicArray<String> res = trie.startsWith(a, 20);
                lastFound = res.toString();
                return "words starting with '" + a + "': " + res + "  (walked " + a.length() + " node(s), then collected)";
            }
            // union-find
            case "uf.union" -> {
                int x = element(a), y = element(b);
                return uf.union(x, y) ? "union(" + x + ", " + y + "): merged; " + uf.components() + " set(s) left" : x + " and " + y + " were already in the same set";
            }
            case "uf.find" -> { int x = element(a); return "find(" + x + ") = " + uf.find(x) + " (paths compressed on the way)"; }
            case "uf.connected" -> { int x = element(a), y = element(b); return "connected(" + x + ", " + y + ") = " + uf.connected(x, y); }
            // LRU
            case "lru.put" -> {
                long ev = lru.evictions();
                lru.put(need(a), b.isEmpty() ? a.toUpperCase() : b);
                return "put(" + a + ")" + (lru.evictions() > ev ? ": cache full, evicted the least recently used key" : "");
            }
            case "lru.get" -> { String v = lru.get(need(a)); return v == null ? "get(" + a + "): MISS" : "get(" + a + ") = " + v + ": HIT, moved to front"; }
            default -> {
                if (op.equals("clear")) { clear(ds); return "cleared"; }
                throw new IllegalArgumentException("unknown operation " + ds + "." + op);
            }
        }
    }

    private void clear(String ds) {
        switch (ds) {
            case "array" -> array = new DynamicArray<>(2);
            case "slist" -> slist = new SinglyLinkedList<>();
            case "dlist" -> dlist = new DoublyLinkedList<>();
            case "stack" -> stack = new Stack<>();
            case "queue" -> queue = new Queue<>(4);
            case "deque" -> deque = new Deque<>();
            case "heap" -> heap = new MinHeap<>(Integer::compare);
            case "map" -> map = new HashMap<>(8);
            case "trie" -> trie = new Trie();
            case "uf" -> uf = new UnionFind(10);
            case "lru" -> lru = new LRUCache<>(4);
            default -> throw new IllegalArgumentException("unknown structure " + ds);
        }
    }

    public void snapshot(String ds, Json j) {
        j.obj().put("ds", ds);
        switch (ds) {
            case "array" -> { j.put("capacity", array.capacity()); items(j, array); }
            case "slist" -> items(j, iter(slist));
            case "dlist" -> items(j, iter(dlist));
            case "stack" -> items(j, stack.topToBottom());
            case "queue" -> {
                j.put("head", queue.headIndex()).put("size", queue.size()).put("capacity", queue.capacity()).key("slots").arr();
                for (Object o : queue.rawSlots()) { if (o == null) j.nul(); else j.val(o.toString()); }
                j.end();
            }
            case "deque" -> { DynamicArray<String> a = new DynamicArray<>(); for (int i = 0; i < deque.size(); i++) a.add(deque.get(i)); items(j, a); }
            case "heap" -> { j.key("items").arr(); for (int v : heap.array()) j.val(v); j.end(); }
            case "map" -> {
                j.put("size", map.size()).put("capacity", map.capacity()).put("load", map.loadFactor()).put("resizes", map.resizeCount()).key("buckets").arr();
                for (int i = 0; i < map.capacity(); i++) {
                    j.arr();
                    for (String k : map.bucketKeys(i)) j.val(k + "=" + map.get(k));
                    j.end();
                }
                j.end();
            }
            case "trie" -> {
                j.put("words", trie.size()).put("nodes", trie.nodeCount()).put("found", lastFound).key("edges").arr();
                for (String[] e : trie.edgesForDrawing(120)) j.arr().val(Integer.parseInt(e[0])).val(Integer.parseInt(e[1])).val(e[2]).val(e[3].equals("1")).end();
                j.end();
            }
            case "uf" -> {
                j.put("components", uf.components()).key("parent").arr();
                for (int p : uf.parents()) j.val(p);
                j.end();
            }
            case "lru" -> {
                j.put("capacity", lru.capacity()).put("hits", lru.hits()).put("misses", lru.misses()).put("evictions", lru.evictions());
                items(j, lru.keysByRecency());
            }
            default -> throw new IllegalArgumentException("unknown structure " + ds);
        }
        j.end();
    }

    private static <T> DynamicArray<T> iter(Iterable<T> it) {
        DynamicArray<T> a = new DynamicArray<>();
        for (T t : it) a.add(t);
        return a;
    }

    private static void items(Json j, DynamicArray<String> a) {
        j.key("items").arr();
        for (String s : a) j.val(s);
        j.end();
    }

    private int bucket(String key) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return h & (map.capacity() - 1);
    }

    private static String need(String a) {
        if (a.isEmpty()) throw new IllegalArgumentException("enter a value first");
        if (a.length() > 24) throw new IllegalArgumentException("keep values under 24 characters");
        return a;
    }

    private static int num(String a) {
        try { return Integer.parseInt(a.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("'" + a + "' is not a whole number"); }
    }

    private static int idx(String a) {
        if (a.isBlank()) throw new IllegalArgumentException("enter an index first");
        return num(a);
    }

    private int element(String a) {
        int x = num(a);
        if (x < 0 || x >= uf.size()) throw new IllegalArgumentException("elements are 0 to " + (uf.size() - 1));
        return x;
    }
}
