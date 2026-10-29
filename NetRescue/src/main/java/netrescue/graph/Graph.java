package netrescue.graph;

import netrescue.ds.DynamicArray;
import netrescue.ds.HashMap;
import netrescue.ds.SinglyLinkedList;
import netrescue.ds.Trie;

/**
 * Undirected weighted network stored as ADJACENCY LISTS.
 *
 *   nodes[id]  -> Node (router)
 *   edges[id]  -> Edge (link)
 *   adj[id]    -> singly linked list of the edges touching router id
 *   nameIndex  -> hash map: router name -> id   (O(1) lookup by name)
 *   names      -> trie of router names          (autocomplete)
 *
 * Space is O(V + E), and iterating a router's neighbours costs O(degree),
 * which is what BFS, DFS and Dijkstra need. (An adjacency MATRIX would cost
 * O(V^2) space and O(V) per neighbour scan.)
 *
 * Deleting is done with a "deleted" flag (a tombstone) so ids never change;
 * that makes undo trivial: un-delete by clearing the flag.
 * Failing is separate: a failed router/link still exists, it is just "down".
 */
public class Graph {
    private final DynamicArray<Node> nodes = new DynamicArray<>();
    private final DynamicArray<Edge> edges = new DynamicArray<>();
    private final DynamicArray<SinglyLinkedList<Edge>> adj = new DynamicArray<>();
    private final HashMap<String, Integer> nameIndex = new HashMap<>();
    private final Trie names = new Trie();
    private String title = "Untitled network";

    public String title() { return title; }
    public void setTitle(String t) { title = t; }

    // ------------------------------------------------------------------ routers
    public int addNode(String name, double x, double y) {
        name = name.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("router name is empty");
        if (nameIndex.containsKey(name)) throw new IllegalArgumentException("a router named '" + name + "' already exists");
        int id = nodes.size();
        nodes.add(new Node(id, name, x, y));
        adj.add(new SinglyLinkedList<>());
        nameIndex.put(name, id);
        names.insert(name);
        return id;
    }

    /** Marks a router (and its links) deleted. Returns the ids of links deleted with it. */
    public DynamicArray<Integer> deleteNode(int id) {
        Node n = node(id);
        if (n.deleted) throw new IllegalArgumentException("router already deleted");
        DynamicArray<Integer> removed = new DynamicArray<>();
        for (Edge e : adj.get(id)) if (!e.deleted) { e.deleted = true; removed.add(e.id); }
        n.deleted = true;
        nameIndex.remove(n.name);
        names.remove(n.name);
        return removed;
    }

    /** Undo of deleteNode. */
    public void undeleteNode(int id, DynamicArray<Integer> edgeIds) {
        Node n = node(id);
        if (nameIndex.containsKey(n.name)) throw new IllegalArgumentException("name '" + n.name + "' is taken");
        n.deleted = false;
        nameIndex.put(n.name, id);
        names.insert(n.name);
        for (int eid : edgeIds) edge(eid).deleted = false;
    }

    public void moveNode(int id, double x, double y) { Node n = node(id); n.x = x; n.y = y; }

    // ------------------------------------------------------------------ links
    public int addEdge(int u, int v, double cost, int capacity) {
        if (u == v) throw new IllegalArgumentException("a link must join two different routers");
        if (node(u).deleted || node(v).deleted) throw new IllegalArgumentException("router is deleted");
        if (cost <= 0) throw new IllegalArgumentException("cost must be positive");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        if (findEdge(u, v) != null) throw new IllegalArgumentException("these routers are already linked");
        int id = edges.size();
        Edge e = new Edge(id, u, v, cost, capacity);
        edges.add(e);
        adj.get(u).addLast(e);
        adj.get(v).addLast(e);
        return id;
    }

    public void deleteEdge(int id) { edge(id).deleted = true; }
    public void undeleteEdge(int id) {
        Edge e = edge(id);
        if (findEdge(e.u, e.v) != null) throw new IllegalArgumentException("these routers are already linked");
        e.deleted = false;
    }

    /** The live (not deleted) link between u and v, or null. O(degree(u)). */
    public Edge findEdge(int u, int v) {
        for (Edge e : adj.get(u)) if (!e.deleted && e.other(u) == v) return e;
        return null;
    }

    // ------------------------------------------------------------------ queries
    public Node node(int id) {
        if (id < 0 || id >= nodes.size()) throw new IllegalArgumentException("no router with id " + id);
        return nodes.get(id);
    }

    public Edge edge(int id) {
        if (id < 0 || id >= edges.size()) throw new IllegalArgumentException("no link with id " + id);
        return edges.get(id);
    }

    public Integer idOf(String name) { return nameIndex.get(name.trim()); }

    public int requireId(String nameOrId) {
        Integer id = idOf(nameOrId);
        if (id != null) return id;
        try {
            int n = Integer.parseInt(nameOrId.trim());
            if (n >= 0 && n < nodes.size() && !nodes.get(n).deleted) return n;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("unknown router '" + nameOrId + "'");
    }

    public DynamicArray<String> searchNames(String prefix, int limit) { return names.startsWith(prefix, limit); }

    /** Total slots, including deleted ones (algorithms size their arrays by this). */
    public int nodeSlots() { return nodes.size(); }
    public int edgeSlots() { return edges.size(); }

    public SinglyLinkedList<Edge> adjacent(int id) { return adj.get(id); }
    public DynamicArray<Node> allNodes() { return nodes; }
    public DynamicArray<Edge> allEdges() { return edges; }

    public boolean exists(int nodeId) { return !node(nodeId).deleted; }
    public boolean nodeUp(int id) { Node n = node(id); return !n.deleted && n.up; }

    /** A link can carry traffic if it exists, is up, and both its routers are up. */
    public boolean edgeUsable(Edge e) { return !e.deleted && e.up && nodeUp(e.u) && nodeUp(e.v); }

    public int liveNodeCount() { int c = 0; for (Node n : nodes) if (!n.deleted) c++; return c; }
    public int liveEdgeCount() { int c = 0; for (Edge e : edges) if (!e.deleted) c++; return c; }
    public int upNodeCount() { int c = 0; for (Node n : nodes) if (!n.deleted && n.up) c++; return c; }
    public int usableEdgeCount() { int c = 0; for (Edge e : edges) if (edgeUsable(e)) c++; return c; }

    /** Live router ids. */
    public DynamicArray<Integer> liveNodeIds() {
        DynamicArray<Integer> out = new DynamicArray<>();
        for (Node n : nodes) if (!n.deleted) out.add(n.id);
        return out;
    }

    public String name(int id) { return node(id).name; }

    // ------------------------------------------------------------------ state
    public void setNodeUp(int id, boolean up) { node(id).up = up; }
    public void setEdgeUp(int id, boolean up) { edge(id).up = up; }

    public void restoreAll() {
        for (Node n : nodes) n.up = true;
        for (Edge e : edges) { e.up = true; e.used = 0; }
    }

    public void releaseAllCapacity() { for (Edge e : edges) e.used = 0; }

    /** Deep copy with fresh state (used by benchmarks/experiments so they never touch the live network). */
    public Graph copy() {
        Graph g = new Graph();
        g.title = title;
        // keep ids identical, including deleted slots
        for (Node n : nodes) {
            Node c = new Node(n.id, n.name, n.x, n.y);
            c.deleted = n.deleted; c.up = n.up;
            g.nodes.add(c);
            g.adj.add(new SinglyLinkedList<>());
            if (!n.deleted) { g.nameIndex.put(n.name, n.id); g.names.insert(n.name); }
        }
        for (Edge e : edges) {
            Edge c = new Edge(e.id, e.u, e.v, e.cost, e.capacity);
            c.deleted = e.deleted; c.up = e.up; c.used = e.used;
            g.edges.add(c);
            g.adj.get(e.u).addLast(c);
            g.adj.get(e.v).addLast(c);
        }
        return g;
    }

    /** A clean compacted copy (no deleted slots), used when saving. */
    public Graph compacted() {
        Graph g = new Graph();
        g.title = title;
        HashMap<Integer, Integer> map = new HashMap<>();
        for (Node n : nodes) if (!n.deleted) map.put(n.id, g.addNode(n.name, n.x, n.y));
        for (Edge e : edges) if (!e.deleted) g.addEdge(map.get(e.u), map.get(e.v), e.cost, e.capacity);
        return g;
    }
}
