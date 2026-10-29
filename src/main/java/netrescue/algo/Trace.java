package netrescue.algo;

import netrescue.ds.DynamicArray;
import netrescue.io.Json;

/**
 * A recording of an algorithm run, step by step, for the animation.
 *
 * Each step says: a message, which routers/links changed state (colour),
 * labels to show next to routers (e.g. current distance), and a snapshot of
 * the algorithm's main data structure (the queue in BFS, the heap in
 * Dijkstra, ...). The browser replays steps in order; the state shown at
 * step k is the result of applying steps 0..k.
 *
 * Passing null instead of a Trace turns recording off (used by benchmarks).
 */
public class Trace {
    public static final int MAX_STEPS = 5000;

    public static final class Step {
        final String msg;
        final DynamicArray<Integer> nodeIds = new DynamicArray<>(4);
        final DynamicArray<String> nodeStates = new DynamicArray<>(4);
        final DynamicArray<Integer> edgeIds = new DynamicArray<>(4);
        final DynamicArray<String> edgeStates = new DynamicArray<>(4);
        final DynamicArray<Integer> labelIds = new DynamicArray<>(4);
        final DynamicArray<String> labels = new DynamicArray<>(4);
        String dsKind, dsTitle;
        DynamicArray<String> dsItems;

        Step(String msg) { this.msg = msg; }

        public Step node(int id, String state) { nodeIds.add(id); nodeStates.add(state); return this; }
        public Step edge(int id, String state) { edgeIds.add(id); edgeStates.add(state); return this; }
        public Step label(int id, String text) { labelIds.add(id); labels.add(text); return this; }

        /** kind: queue | stack | heap | sets | list */
        public Step ds(String kind, String title, DynamicArray<String> items) {
            dsKind = kind; dsTitle = title; dsItems = items; return this;
        }
    }

    private final DynamicArray<Step> steps = new DynamicArray<>();
    private boolean truncated;
    private final String algorithm;

    public Trace(String algorithm) { this.algorithm = algorithm; }

    /** Starts a new step. Past the step limit, steps are discarded (but still safe to call). */
    public Step step(String msg) {
        Step s = new Step(msg);
        if (steps.size() < MAX_STEPS) steps.add(s); else truncated = true;
        return s;
    }

    public int size() { return steps.size(); }
    public boolean truncated() { return truncated; }

    public void toJson(Json j) {
        j.obj().put("algorithm", algorithm).put("truncated", truncated).key("steps").arr();
        for (Step s : steps) {
            j.obj().put("msg", s.msg);
            if (!s.nodeIds.isEmpty()) {
                j.key("n").arr();
                for (int i = 0; i < s.nodeIds.size(); i++) j.arr().val(s.nodeIds.get(i)).val(s.nodeStates.get(i)).end();
                j.end();
            }
            if (!s.edgeIds.isEmpty()) {
                j.key("e").arr();
                for (int i = 0; i < s.edgeIds.size(); i++) j.arr().val(s.edgeIds.get(i)).val(s.edgeStates.get(i)).end();
                j.end();
            }
            if (!s.labelIds.isEmpty()) {
                j.key("l").arr();
                for (int i = 0; i < s.labelIds.size(); i++) j.arr().val(s.labelIds.get(i)).val(s.labels.get(i)).end();
                j.end();
            }
            if (s.dsKind != null) {
                j.key("ds").obj().put("kind", s.dsKind).put("title", s.dsTitle).key("items").arr();
                for (String it : s.dsItems) j.val(it);
                j.end().end();
            }
            j.end();
        }
        j.end().end();
    }
}
