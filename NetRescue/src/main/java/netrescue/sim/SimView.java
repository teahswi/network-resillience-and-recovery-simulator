package netrescue.sim;

import netrescue.ds.DoublyLinkedList;
import netrescue.ds.DynamicArray;
import netrescue.graph.Edge;
import netrescue.graph.Graph;
import netrescue.io.Json;

/** Turns simulator state into JSON for the browser. */
public final class SimView {
    private SimView() {}

    public static void state(Simulator s, Json j, int logTail) {
        Graph g = s.g;
        j.obj();
        j.put("clock", s.clock).put("pendingEvents", s.events.size()).put("queued", s.queuedCount()).put("slotsInUse", s.slotsInUse);
        settings(s.cfg, j.key("settings"));
        metrics(s, j.key("metrics"));

        j.key("transfers").arr();
        for (Transfer t : s.transferList) transfer(g, t, j, false);
        j.end();

        j.key("queue").arr();
        for (Transfer t : s.queueSnapshot()) j.val("T" + t.id + " (p" + t.priority + ")");
        j.end();

        j.key("events").arr();
        for (Event e : s.upcomingEvents(12)) j.obj().put("time", e.time).put("type", e.type.name()).put("target", describeTarget(s, e)).end();
        j.end();

        j.key("cache").obj().put("size", s.cache.size()).put("capacity", s.cache.capacity())
                .put("hits", s.cache.hits()).put("misses", s.cache.misses()).put("hitRate", s.cache.hitRate())
                .put("evictions", s.cache.evictions()).put("invalidations", s.cache.invalidations()).key("keys").arr();
        for (String k : s.cache.keysByRecency()) {
            String[] p = k.split(">");
            j.val(g.name(Integer.parseInt(p[0])) + " > " + g.name(Integer.parseInt(p[1])));
        }
        j.end().end();

        j.key("links").arr();
        for (Edge e : g.allEdges()) j.arr().val(e.id).val(e.used).val(e.capacity).val(!e.deleted && e.up).end();
        j.end();
        j.key("nodesUp").arr();
        for (int i = 0; i < g.nodeSlots(); i++) j.val(g.nodeUp(i));
        j.end();

        j.put("logSize", s.log.size());
        j.put("logFirstIndex", s.log.isEmpty() ? 0 : s.log.first().index);
        j.key("log").arr();
        DynamicArray<Simulator.Entry> tail = tail(s.log, logTail);
        for (Simulator.Entry en : tail) j.obj().put("i", en.index).put("time", en.time).put("kind", en.kind).put("text", en.text).end();
        j.end();

        j.key("series").arr();
        int step = Math.max(1, s.series.size() / 400);   // downsample for the chart
        for (int i = 0; i < s.series.size(); i += step) {
            double[] r = s.series.get(i);
            j.arr().val(r[0]).val(r[1]).val(r[2]).val(r[3]).val(r[4]).end();
        }
        j.end();
        j.end();
    }

    public static void settings(Simulator.Settings c, Json j) {
        j.obj().put("strategy", c.strategy.name()).put("useCache", c.useCache).put("priorityMode", c.priorityMode)
                .put("cacheSize", c.cacheSize).put("maxActive", c.maxActive).put("maxRetries", c.maxRetries).put("hopDelay", c.hopDelay).end();
    }

    public static void metrics(Simulator s, Json j) {
        int finished = s.done + s.failed;
        j.obj().put("submitted", s.submitted).put("done", s.done).put("failed", s.failed)
                .put("successRate", finished == 0 ? 0 : (double) s.done / finished)
                .put("rollbacks", s.rollbacks).put("retries", s.retries).put("reroutes", s.reroutes)
                .put("routeComputations", s.routeComputations)
                .put("avgSetup", s.committedCount == 0 ? 0 : s.setupTimeSum / s.committedCount)
                .put("recentSetup", s.recentAverageSetup())
                .put("avgCost", s.committedCount == 0 ? 0 : s.routeCostSum / s.committedCount)
                .put("avgHops", s.committedCount == 0 ? 0 : (double) s.routeHopsSum / s.committedCount)
                .put("cacheHitRate", s.cache.hitRate())
                .end();
    }

    public static void transfer(Graph g, Transfer t, Json j, boolean withHistory) {
        j.obj().put("id", t.id).put("src", g.name(t.src)).put("dst", g.name(t.dst)).put("srcId", t.src).put("dstId", t.dst)
                .put("amount", t.amount).put("priority", t.priority).put("state", t.state.name())
                .put("retries", t.retries).put("rollbacks", t.rollbacks).put("reroutes", t.reroutes)
                .put("created", t.createdAt).put("committed", t.committedAt).put("finished", t.finishedAt)
                .put("reason", t.reason).put("route", t.route == null ? "" : t.route.describe(g)).put("cached", t.fromCache);
        j.key("routeEdges").arr();
        if (t.route != null) for (int e : t.route.edges) j.val(e);
        j.end();
        j.key("undoStack").arr();                       // top first
        for (int eid : t.undo.topToBottom()) { Edge e = g.edge(eid); j.val(g.name(e.u) + "-" + g.name(e.v)); }
        j.end();
        if (withHistory) { j.key("history").arr(); for (String h : t.history) j.val(h); j.end(); }
        j.end();
    }

    /** Snapshot at a log entry, for the timeline scrubber. */
    public static boolean entry(Simulator s, int index, Json j) {
        for (Simulator.Entry en : s.log) {
            if (en.index != index) continue;
            j.obj().put("i", en.index).put("time", en.time).put("kind", en.kind).put("text", en.text).put("focus", en.focusTransfer);
            j.key("nodesUp").arr(); for (boolean b : en.nodeUp) j.val(b); j.end();
            j.key("links").arr();
            for (int i = 0; i < en.edgeUp.length; i++) j.arr().val(i).val(en.edgeUsed[i]).val(s.g.edge(i).capacity).val(en.edgeUp[i]).end();
            j.end();
            j.key("reservations").arr();
            for (int[] r : en.reservations) { j.arr(); for (int x : r) j.val(x); j.end(); }
            j.end();
            j.end();
            return true;
        }
        return false;
    }

    private static DynamicArray<Simulator.Entry> tail(DoublyLinkedList<Simulator.Entry> log, int n) {
        DynamicArray<Simulator.Entry> all = new DynamicArray<>();
        int skip = Math.max(0, log.size() - n), k = 0;
        for (Simulator.Entry e : log) if (k++ >= skip) all.add(e);
        return all;
    }

    private static String describeTarget(Simulator s, Event e) {
        Graph g = s.g;
        return switch (e.type) {
            case LINK_DOWN, LINK_UP -> s.linkName(g.edge(e.target));
            case NODE_DOWN, NODE_UP -> g.name(e.target);
            default -> "T" + e.target;
        };
    }
}
