package netrescue.graph;

/** A router. Position (x, y) is in "world" units used only for drawing. */
public class Node {
    public final int id;
    public final String name;
    public double x, y;
    public boolean up = true;        // false = failed
    public boolean deleted = false;  // true = removed by the user (kept for undo)

    public Node(int id, String name, double x, double y) {
        this.id = id; this.name = name; this.x = x; this.y = y;
    }

    @Override public String toString() { return name; }
}
