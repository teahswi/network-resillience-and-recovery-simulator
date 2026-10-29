package netrescue.graph;

/**
 * An undirected link between routers u and v.
 * cost     = routing weight (think latency or distance); always positive.
 * capacity = how many bandwidth units it can carry at once.
 * used     = units currently reserved by transfers.
 */
public class Edge {
    public final int id, u, v;
    public double cost;
    public int capacity;
    public int used;
    public boolean up = true;
    public boolean deleted = false;

    public Edge(int id, int u, int v, double cost, int capacity) {
        this.id = id; this.u = u; this.v = v; this.cost = cost; this.capacity = capacity;
    }

    /** The router at the other end from x. */
    public int other(int x) {
        if (x == u) return v;
        if (x == v) return u;
        throw new IllegalArgumentException("router " + x + " is not on link " + id);
    }

    public int free() { return capacity - used; }

    @Override public String toString() { return "e" + id + "(" + u + "-" + v + ")"; }
}
