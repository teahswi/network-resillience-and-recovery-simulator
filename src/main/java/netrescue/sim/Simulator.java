package netrescue.sim;

import java.util.Random;
import java.util.function.Predicate;

import netrescue.algo.BFS;
import netrescue.algo.Dijkstra;
import netrescue.algo.PathResult;
import netrescue.ds.Deque;
import netrescue.ds.DoublyLinkedList;
import netrescue.ds.DynamicArray;
import netrescue.ds.HashMap;
import netrescue.ds.LRUCache;
import netrescue.ds.MinHeap;
import netrescue.ds.Queue;
import netrescue.ds.UnionFind;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * Discrete-event simulator for transfers over a failing network.
 *
 * The heart is the EVENT HEAP: a min-heap of future events ordered by time.
 * The loop is simply
 *     while heap not empty:  e = extractMin();  clock = e.time;  handle(e)
 * and handlers may schedule more events in the future. Nothing "ticks";
 * the clock jumps from one event to the next.
 *
 * Data structures and their jobs here:
 *   MinHeap<Event>            event clock
 *   Queue<Transfer>           admission queue (FIFO mode)
 *   MinHeap<Transfer>         admission queue (priority mode)
 *   Stack (inside Transfer)   per-transfer undo log for rollback
 *   HashMap<Integer,Transfer> transfer id -> transfer
 *   LRUCache<String,Path>     route cache, invalidated on failures
 *   UnionFind                 fast "is dst reachable at all?" check before routing
 *   Deque<Double>             sliding window of recent setup times
 *   DoublyLinkedList<Entry>   event log / timeline (oldest dropped from the front)
 */
public class Simulator {

    public enum Strategy { CHEAPEST, FEWEST_HOPS }

    public static final class Settings {
        public Strategy strategy = Strategy.CHEAPEST;
        public boolean useCache = true;
        public boolean priorityMode = false;
        public int cacheSize = 32;
        public int maxActive = 8;        // transfers allowed in SETUP/ACTIVE at once
        public int maxRetries = 4;
        public double hopDelay = 0.4;    // time to reserve one link
    }

    /** One line of the event log, with a snapshot of the network for the timeline. */
    public static final class Entry {
        public final int index;
        public final double time;
        public final String kind, text;
        final boolean[] nodeUp, edgeUp;
        final int[] edgeUsed;
        final int[][] reservations;   // per active transfer: {id, edge ids...}
        final int focusTransfer;

        Entry(int index, double time, String kind, String text, boolean[] nodeUp, boolean[] edgeUp, int[] edgeUsed, int[][] res, int focus) {
            this.index = index; this.time = time; this.kind = kind; this.text = text;
            this.nodeUp = nodeUp; this.edgeUp = edgeUp; this.edgeUsed = edgeUsed; this.reservations = res; this.focusTransfer = focus;
        }
    }

    public static final int LOG_CAPACITY = 4000;

    final Graph g;
    final Settings cfg;
    double clock;
    private long seq;
    final MinHeap<Event> events = new MinHeap<>(Event::compare);
    private final Queue<Transfer> fifo = new Queue<>();
    private final MinHeap<Transfer> prio = new MinHeap<>(Transfer::byPriority);
    final HashMap<Integer, Transfer> transfers = new HashMap<>();
    final DynamicArray<Transfer> transferList = new DynamicArray<>();
    final LRUCache<String, PathResult> cache;
    final DoublyLinkedList<Entry> log = new DoublyLinkedList<>();
    private int logCounter;
    private int nextTransferId = 1;
    int slotsInUse;

    // metrics
    int submitted, done, failed, rollbacks, retries, reroutes, routeComputations;
    double setupTimeSum, routeCostSum;
    int routeHopsSum, committedCount;
    final Deque<Double> recentSetup = new Deque<>();
    static final int WINDOW = 20;
    final DynamicArray<double[]> series = new DynamicArray<>();   // {time, done, failed, active, rollbacks}

    public Simulator(Graph g, Settings cfg) {
        this.g = g;
        this.cfg = cfg;
        this.cache = new LRUCache<>(cfg.cacheSize);
        record("info", "Simulator ready on '" + g.title() + "'.", -1);
    }

    public Graph graph() { return g; }
    public Settings settings() { return cfg; }
    public double clock() { return clock; }
    public int pendingEvents() { return events.size(); }

    // ================================================================== scheduling
    void schedule(double time, Event.Type type, int target, int epoch) {
        events.insert(new Event(time, seq++, type, target, epoch));
    }

    public int submit(int src, int dst, int amount, int priority, double at, double holdTime) {
        if (src == dst) throw new IllegalArgumentException("source and destination must differ");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        g.node(src); g.node(dst);
        int id = nextTransferId++;
        Transfer t = new Transfer(id, src, dst, amount, priority, Math.max(at, clock), holdTime);
        transfers.put(id, t);
        transferList.add(t);
        submitted++;
        schedule(t.createdAt, Event.Type.TRANSFER_ARRIVE, id, 0);
        return id;
    }

    public void scheduleLinkFailure(int edgeId, double at, double duration) {
        g.edge(edgeId);
        schedule(Math.max(at, clock), Event.Type.LINK_DOWN, edgeId, 0);
        if (duration > 0) schedule(Math.max(at, clock) + duration, Event.Type.LINK_UP, edgeId, 0);
    }

    public void scheduleNodeFailure(int nodeId, double at, double duration) {
        g.node(nodeId);
        schedule(Math.max(at, clock), Event.Type.NODE_DOWN, nodeId, 0);
        if (duration > 0) schedule(Math.max(at, clock) + duration, Event.Type.NODE_UP, nodeId, 0);
    }

    /** Random transfers between random up routers, arriving over [clock, clock + span]. */
    public void randomTransfers(int count, double span, long seed) {
        Random rnd = new Random(seed);
        DynamicArray<Integer> ids = g.liveNodeIds();
        if (ids.size() < 2) throw new IllegalArgumentException("need at least 2 routers");
        int[] amounts = {5, 5, 5, 10, 10};
        for (int i = 0; i < count; i++) {
            int a = ids.get(rnd.nextInt(ids.size())), b;
            do { b = ids.get(rnd.nextInt(ids.size())); } while (b == a);
            submit(a, b, amounts[rnd.nextInt(amounts.length)], 1 + rnd.nextInt(3), clock + rnd.nextDouble() * span, 3 + rnd.nextDouble() * 5);
        }
    }

    /** Random link (80%) and router (20%) failures over [clock, clock + span]. */
    public void randomFailures(int count, double span, double meanDowntime, long seed) {
        Random rnd = new Random(seed);
        DynamicArray<Integer> nodeIds = g.liveNodeIds();
        DynamicArray<Integer> edgeIds = new DynamicArray<>();
        for (Edge e : g.allEdges()) if (!e.deleted) edgeIds.add(e.id);
        if (edgeIds.isEmpty()) return;
        for (int i = 0; i < count; i++) {
            double at = clock + rnd.nextDouble() * span;
            double dur = meanDowntime * (0.5 + rnd.nextDouble());
            if (rnd.nextDouble() < 0.8) scheduleLinkFailure(edgeIds.get(rnd.nextInt(edgeIds.size())), at, dur);
            else scheduleNodeFailure(nodeIds.get(rnd.nextInt(nodeIds.size())), at, dur);
        }
    }

    // ================================================================== running
    /** Processes one event. Returns false if there was nothing to do. */
    public boolean step() {
        if (events.isEmpty()) return false;
        Event e = events.extractMin();
        clock = e.time;
        handle(e);
        sample();
        return true;
    }

    public int runEvents(int n) {
        int c = 0;
        while (c < n && step()) c++;
        return c;
    }

    public int runUntil(double time) {
        int c = 0;
        while (!events.isEmpty() && events.peek().time <= time) { step(); c++; }
        if (time > clock) clock = time;
        return c;
    }

    public int runToEnd(int safetyLimit) {
        int c = 0;
        while (c < safetyLimit && step()) c++;
        return c;
    }

    private void handle(Event e) {
        switch (e.type) {
            case TRANSFER_ARRIVE -> {
                Transfer t = transfers.get(e.target);
                note(t, "arrived (" + t.amount + " units, priority " + t.priority + ")");
                enqueue(t);
                record("info", "T" + t.id + " arrives: " + g.name(t.src) + " -> " + g.name(t.dst) + ", " + t.amount + " units. Queued.", t.id);
                dispatch();
            }
            case HOP -> {
                Transfer t = transfers.get(e.target);
                if (t.epoch == e.epoch && t.state == Transfer.State.SETUP) doHop(t);
            }
            case TRANSFER_END -> {
                Transfer t = transfers.get(e.target);
                if (t.epoch != e.epoch || t.state != Transfer.State.ACTIVE) return;
                releaseAll(t);
                slotsInUse--;
                t.state = Transfer.State.DONE;
                t.finishedAt = clock;
                done++;
                note(t, "finished, capacity released");
                record("done", "T" + t.id + " finished and released its links.", t.id);
                dispatch();
            }
            case RETRY -> {
                Transfer t = transfers.get(e.target);
                if (t.epoch != e.epoch || t.state != Transfer.State.WAITING) return;
                note(t, "retrying");
                enqueue(t);
                dispatch();
            }
            case LINK_DOWN -> {
                Edge ed = g.edge(e.target);
                if (ed.deleted || !ed.up) return;
                ed.up = false;
                record("fail", "Link " + linkName(ed) + " FAILED.", -1);
                afterLoss(ed.id, -1);
            }
            case LINK_UP -> {
                Edge ed = g.edge(e.target);
                if (ed.deleted || ed.up) return;
                ed.up = true;
                record("recover", "Link " + linkName(ed) + " recovered.", -1);
                dispatch();
            }
            case NODE_DOWN -> {
                if (!g.nodeUp(e.target)) return;
                g.setNodeUp(e.target, false);
                record("fail", "Router " + g.name(e.target) + " FAILED.", -1);
                afterLoss(-1, e.target);
            }
            case NODE_UP -> {
                if (g.node(e.target).deleted || g.node(e.target).up) return;
                g.setNodeUp(e.target, true);
                record("recover", "Router " + g.name(e.target) + " recovered.", -1);
                dispatch();
            }
        }
    }

    // ================================================================== admission
    private void enqueue(Transfer t) {
        t.state = Transfer.State.QUEUED;
        if (cfg.priorityMode) prio.insert(t); else fifo.enqueue(t);
    }

    private int queued() { return cfg.priorityMode ? prio.size() : fifo.size(); }

    private void dispatch() {
        while (slotsInUse < cfg.maxActive && queued() > 0) {
            Transfer t = cfg.priorityMode ? prio.extractMin() : fifo.dequeue();
            slotsInUse++;
            startAttempt(t);
        }
    }

    // ================================================================== one attempt
    /** Called with a slot already taken. Either starts SETUP, or releases the slot. */
    private void startAttempt(Transfer t) {
        t.epoch++;
        if (!reachable(t.src, t.dst)) {
            slotsInUse--;
            waitOrFail(t, "destination unreachable (union-find: different pieces)");
            return;
        }
        PathResult route = findRoute(t);
        if (route == null || !route.found()) {
            slotsInUse--;
            waitOrFail(t, "no route with " + t.amount + " free units");
            return;
        }
        t.route = route;
        t.hopIndex = 0;
        t.state = Transfer.State.SETUP;
        note(t, "route " + route.describe(g) + (t.fromCache ? " (from cache)" : ""));
        record("route", "T" + t.id + " routing via " + route.describe(g) + (t.fromCache ? " [cache hit]" : "") + ".", t.id);
        schedule(clock, Event.Type.HOP, t.id, t.epoch);
    }

    private void doHop(Transfer t) {
        Edge e = g.edge(t.route.edges.get(t.hopIndex));
        if (g.edgeUsable(e) && e.free() >= t.amount) {
            e.used += t.amount;
            t.undo.push(e.id);
            t.hopIndex++;
            record("reserve", "T" + t.id + " reserved " + linkName(e) + " (" + e.used + "/" + e.capacity + "). Pushed onto undo stack (size " + t.undo.size() + ").", t.id);
            if (t.hopIndex == t.route.edges.size()) {
                t.state = Transfer.State.ACTIVE;
                t.committedAt = clock;
                double setup = clock - t.createdAt;
                setupTimeSum += setup;
                committedCount++;
                routeCostSum += t.route.cost;
                routeHopsSum += t.route.hops();
                recentSetup.addLast(setup);
                if (recentSetup.size() > WINDOW) recentSetup.pollFirst();
                note(t, "COMMITTED: all " + t.route.hops() + " links reserved");
                record("commit", "T" + t.id + " COMMITTED: every link reserved. Data flows for " + fmt(t.holdTime) + " time units.", t.id);
                schedule(clock + t.holdTime, Event.Type.TRANSFER_END, t.id, t.epoch);
            } else {
                schedule(clock + cfg.hopDelay, Event.Type.HOP, t.id, t.epoch);
            }
        } else {
            String why = !g.edgeUsable(e) ? "link " + linkName(e) + " is down" : "link " + linkName(e) + " is full (" + e.free() + " free, need " + t.amount + ")";
            note(t, "hop failed: " + why);
            record("error", "T" + t.id + " cannot reserve: " + why + ".", t.id);
            rollbackAndRetry(t, why);
        }
    }

    /** Pops the undo stack, releasing links in reverse order, then tries another route. */
    private void rollbackAndRetry(Transfer t, String why) {
        int undone = 0;
        while (!t.undo.isEmpty()) {
            Edge e = g.edge(t.undo.pop());
            e.used -= t.amount;
            undone++;
            record("rollback", "T" + t.id + " ROLLBACK: pop " + linkName(e) + " and release " + t.amount + " units (stack size " + t.undo.size() + ").", t.id);
        }
        t.rollbacks++;
        rollbacks++;
        note(t, "rolled back " + undone + " reservation(s)");
        // this attempt is over: give up the slot, then go again
        slotsInUse--;
        t.retries++;
        retries++;
        if (t.retries > cfg.maxRetries) {
            fail(t, "gave up after " + cfg.maxRetries + " retries (" + why + ")");
            dispatch();
            return;
        }
        t.reroutes++;
        reroutes++;
        slotsInUse++;
        startAttempt(t);
        dispatch();
    }

    private void waitOrFail(Transfer t, String why) {
        if (t.retries >= cfg.maxRetries) { fail(t, "gave up: " + why); return; }
        t.retries++;
        retries++;
        double backoff = Math.pow(2, t.retries - 1);         // 1, 2, 4, 8 ...: exponential backoff
        t.state = Transfer.State.WAITING;
        t.reason = why;
        note(t, "waiting " + fmt(backoff) + " (" + why + ")");
        record("wait", "T" + t.id + " waits " + fmt(backoff) + " time units: " + why + ".", t.id);
        schedule(clock + backoff, Event.Type.RETRY, t.id, t.epoch);
    }

    private void fail(Transfer t, String why) {
        t.state = Transfer.State.FAILED;
        t.reason = why;
        t.finishedAt = clock;
        failed++;
        note(t, "FAILED: " + why);
        record("error", "T" + t.id + " FAILED: " + why + ".", t.id);
    }

    private void releaseAll(Transfer t) {
        while (!t.undo.isEmpty()) g.edge(t.undo.pop()).used -= t.amount;
    }

    /** A link or router went down: drop cached routes through it, roll back transfers holding it. */
    private void afterLoss(int edgeId, int nodeId) {
        int dropped = cache.invalidateIf((k, p) -> {
            for (int eid : p.edges) if (eid == edgeId) return true;
            for (int nid : p.nodes) if (nid == nodeId) return true;
            return false;
        });
        if (dropped > 0) record("info", "Route cache: invalidated " + dropped + " route(s) through the failed element.", -1);
        for (Transfer t : transferList) {
            if (t.state != Transfer.State.SETUP && t.state != Transfer.State.ACTIVE) continue;
            boolean holds = false;
            for (int eid : t.undo.topToBottom()) {
                Edge e = g.edge(eid);
                if (eid == edgeId || e.u == nodeId || e.v == nodeId) { holds = true; break; }
            }
            boolean endpointDown = t.src == nodeId || t.dst == nodeId;
            if (holds || endpointDown) {
                record("error", "T" + t.id + " lost a link it was holding.", t.id);
                rollbackAndRetry(t, "a reserved link/router failed");
            }
        }
    }

    // ================================================================== routing
    private PathResult findRoute(Transfer t) {
        t.fromCache = false;
        String key = t.src + ">" + t.dst;
        if (cfg.useCache) {
            PathResult cached = cache.get(key);
            if (cached != null && stillValid(cached, t.amount)) { t.fromCache = true; return cached; }
        }
        Predicate<Edge> hasRoom = e -> e.free() >= t.amount;
        routeComputations++;
        PathResult p = cfg.strategy == Strategy.CHEAPEST
                ? Dijkstra.shortestPath(g, t.src, t.dst, hasRoom, null)
                : BFS.fewestHops(g, t.src, t.dst, hasRoom, null);
        if (cfg.useCache && p.found()) cache.put(key, p);
        return p;
    }

    private boolean stillValid(PathResult p, int amount) {
        for (int eid : p.edges) {
            Edge e = g.edge(eid);
            if (!g.edgeUsable(e) || e.free() < amount) return false;
        }
        return true;
    }

    /** Union-find over usable links: are a and b in the same piece? Nearly O(E). */
    private boolean reachable(int a, int b) {
        if (!g.nodeUp(a) || !g.nodeUp(b)) return false;
        UnionFind uf = new UnionFind(g.nodeSlots());
        for (Edge e : g.allEdges()) if (g.edgeUsable(e)) uf.union(e.u, e.v);
        return uf.connected(a, b);
    }

    // ================================================================== logging
    private void note(Transfer t, String s) { t.history.add("t=" + fmt(clock) + "  " + s); }

    private void record(String kind, String text, int focus) {
        int n = g.nodeSlots(), m = g.edgeSlots();
        boolean[] nu = new boolean[n];
        boolean[] eu = new boolean[m];
        int[] used = new int[m];
        for (int i = 0; i < n; i++) nu[i] = g.nodeUp(i);
        for (int i = 0; i < m; i++) { Edge e = g.edge(i); eu[i] = !e.deleted && e.up; used[i] = e.used; }
        DynamicArray<int[]> res = new DynamicArray<>();
        for (Transfer t : transferList) {
            if (t.undo.isEmpty()) continue;
            DynamicArray<Integer> held = t.undo.topToBottom();
            int[] row = new int[held.size() + 1];
            row[0] = t.id;
            for (int i = 0; i < held.size(); i++) row[i + 1] = held.get(held.size() - 1 - i);
            res.add(row);
        }
        int[][] resArr = new int[res.size()][];
        for (int i = 0; i < res.size(); i++) resArr[i] = res.get(i);
        log.addLast(new Entry(logCounter++, clock, kind, text, nu, eu, used, resArr, focus));
        if (log.size() > LOG_CAPACITY) log.removeFirst();
    }

    private void sample() {
        int active = 0;
        for (Transfer t : transferList) if (t.state == Transfer.State.SETUP || t.state == Transfer.State.ACTIVE) active++;
        if (series.size() < 5000) series.add(new double[]{clock, done, failed, active, rollbacks});
    }

    String linkName(Edge e) { return g.name(e.u) + "-" + g.name(e.v); }

    static String fmt(double d) { return String.valueOf(Math.round(d * 100) / 100.0); }

    // ================================================================== read access
    public DynamicArray<Transfer> transfers() { return transferList; }
    public Transfer transfer(int id) { return transfers.get(id); }
    public DoublyLinkedList<Entry> log() { return log; }
    public LRUCache<String, PathResult> cache() { return cache; }
    public int queuedCount() { return queued(); }

    public DynamicArray<Transfer> queueSnapshot() {
        DynamicArray<Transfer> out = new DynamicArray<>();
        if (cfg.priorityMode) for (Transfer t : prio.array()) out.add(t);
        else for (Transfer t : fifo.frontToBack()) out.add(t);
        return out;
    }

    public DynamicArray<Event> upcomingEvents(int limit) {
        // copy the heap and pop in order, so the real heap is untouched
        MinHeap<Event> copy = MinHeap.heapify(events.array(), Event::compare);
        DynamicArray<Event> out = new DynamicArray<>();
        while (!copy.isEmpty() && out.size() < limit) out.add(copy.extractMin());
        return out;
    }

    public double recentAverageSetup() {
        if (recentSetup.isEmpty()) return 0;
        double s = 0;
        for (int i = 0; i < recentSetup.size(); i++) s += recentSetup.get(i);
        return s / recentSetup.size();
    }
}
