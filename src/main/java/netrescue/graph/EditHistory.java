package netrescue.graph;

import netrescue.ds.DynamicArray;
import netrescue.ds.Stack;

/**
 * Undo / redo for network edits with TWO STACKS (the Command pattern).
 *
 * Every edit is an object that knows how to apply() and revert() itself.
 *   do(c):   c.apply();  push c on UNDO;  clear REDO   (a new edit forks history)
 *   undo():  c = pop UNDO;  c.revert();  push c on REDO
 *   redo():  c = pop REDO;  c.apply();   push c on UNDO
 * All O(1) apart from the edit itself.
 */
public class EditHistory {
    public interface Command {
        void apply(Graph g);
        void revert(Graph g);
        String label();
    }

    private final Stack<Command> undo = new Stack<>();
    private final Stack<Command> redo = new Stack<>();

    public void run(Graph g, Command c) {
        c.apply(g);
        undo.push(c);
        redo.clear();
    }

    public String undo(Graph g) {
        if (undo.isEmpty()) throw new IllegalStateException("nothing to undo");
        Command c = undo.pop();
        c.revert(g);
        redo.push(c);
        return c.label();
    }

    public String redo(Graph g) {
        if (redo.isEmpty()) throw new IllegalStateException("nothing to redo");
        Command c = redo.pop();
        c.apply(g);
        undo.push(c);
        return c.label();
    }

    public void clear() { undo.clear(); redo.clear(); }
    public int undoSize() { return undo.size(); }
    public int redoSize() { return redo.size(); }

    public DynamicArray<String> undoLabels() {
        DynamicArray<String> out = new DynamicArray<>();
        for (Command c : undo.topToBottom()) out.add(c.label());
        return out;
    }

    public DynamicArray<String> redoLabels() {
        DynamicArray<String> out = new DynamicArray<>();
        for (Command c : redo.topToBottom()) out.add(c.label());
        return out;
    }

    // ------------------------------------------------------------------ the edits

    public static final class AddNode implements Command {
        final String name; final double x, y; int id = -1;
        public AddNode(String name, double x, double y) { this.name = name; this.x = x; this.y = y; }
        public void apply(Graph g) {
            if (id < 0) id = g.addNode(name, x, y);
            else g.undeleteNode(id, new DynamicArray<>());
        }
        public void revert(Graph g) { g.deleteNode(id); }
        public String label() { return "add router " + name; }
        public int id() { return id; }
    }

    public static final class DeleteNode implements Command {
        final int id; final String name; DynamicArray<Integer> removedEdges;
        public DeleteNode(Graph g, int id) { this.id = id; this.name = g.name(id); }
        public void apply(Graph g) { removedEdges = g.deleteNode(id); }
        public void revert(Graph g) { g.undeleteNode(id, removedEdges); }
        public String label() { return "delete router " + name; }
    }

    public static final class MoveNode implements Command {
        final int id; final double ox, oy, nx, ny; final String name;
        public MoveNode(Graph g, int id, double nx, double ny) {
            Node n = g.node(id);
            this.id = id; this.ox = n.x; this.oy = n.y; this.nx = nx; this.ny = ny; this.name = n.name;
        }
        public void apply(Graph g) { g.moveNode(id, nx, ny); }
        public void revert(Graph g) { g.moveNode(id, ox, oy); }
        public String label() { return "move " + name; }
    }

    public static final class AddEdge implements Command {
        final int u, v; final double cost; final int cap; int id = -1; final String desc;
        public AddEdge(Graph g, int u, int v, double cost, int cap) {
            this.u = u; this.v = v; this.cost = cost; this.cap = cap; this.desc = g.name(u) + "-" + g.name(v);
        }
        public void apply(Graph g) {
            if (id < 0) id = g.addEdge(u, v, cost, cap);
            else g.undeleteEdge(id);
        }
        public void revert(Graph g) { g.deleteEdge(id); }
        public String label() { return "add link " + desc; }
        public int id() { return id; }
    }

    public static final class DeleteEdge implements Command {
        final int id; final String desc;
        public DeleteEdge(Graph g, int id) { Edge e = g.edge(id); this.id = id; this.desc = g.name(e.u) + "-" + g.name(e.v); }
        public void apply(Graph g) { g.deleteEdge(id); }
        public void revert(Graph g) { g.undeleteEdge(id); }
        public String label() { return "delete link " + desc; }
    }

    public static final class UpdateEdge implements Command {
        final int id; final double oc, nc; final int ocap, ncap; final String desc;
        public UpdateEdge(Graph g, int id, double cost, int cap) {
            Edge e = g.edge(id);
            if (cost <= 0 || cap <= 0) throw new IllegalArgumentException("cost and capacity must be positive");
            this.id = id; this.oc = e.cost; this.ocap = e.capacity; this.nc = cost; this.ncap = cap;
            this.desc = g.name(e.u) + "-" + g.name(e.v);
        }
        public void apply(Graph g) { Edge e = g.edge(id); e.cost = nc; e.capacity = ncap; }
        public void revert(Graph g) { Edge e = g.edge(id); e.cost = oc; e.capacity = ocap; }
        public String label() { return "edit link " + desc; }
    }

    /** Manual failure / repair of a router (isNode) or link. */
    public static final class SetUp implements Command {
        final boolean isNode, up; final int id; boolean before; final String desc;
        public SetUp(Graph g, boolean isNode, int id, boolean up) {
            this.isNode = isNode; this.id = id; this.up = up;
            if (isNode) desc = "router " + g.name(id);
            else { Edge e = g.edge(id); desc = "link " + g.name(e.u) + "-" + g.name(e.v); }
        }
        public void apply(Graph g) {
            if (isNode) { before = g.node(id).up; g.setNodeUp(id, up); }
            else { before = g.edge(id).up; g.setEdgeUp(id, up); }
        }
        public void revert(Graph g) { if (isNode) g.setNodeUp(id, before); else g.setEdgeUp(id, before); }
        public String label() { return (up ? "repair " : "fail ") + desc; }
    }
}
