package netrescue.server;

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
import netrescue.bench.Benchmarks;
import netrescue.ds.DynamicArray;
import netrescue.ds.HashMap;
import netrescue.ds.HashSet;
import netrescue.graph.Edge;
import netrescue.graph.EditHistory;
import netrescue.graph.Generators;
import netrescue.graph.Graph;
import netrescue.graph.Node;
import netrescue.io.GmlParser;
import netrescue.io.Json;
import netrescue.io.Samples;
import netrescue.io.TextFormat;
import netrescue.sim.Experiments;
import netrescue.sim.SimView;
import netrescue.sim.Simulator;
import netrescue.sim.Transfer;

/**
 * All /api endpoints. Holds the session state: the network being edited,
 * its undo/redo history, the simulator (which runs on its own COPY of the
 * network, so simulated failures never touch the editor), and the DS lab.
 *
 * Methods touching shared state are synchronized; long-running read-only
 * work (benchmarks, experiments) runs outside the lock.
 */
public class Api {
    public static final class Response {
        final int status; final String contentType; final String body; String downloadName;
        Response(int s, String type, String b) { status = s; contentType = type; body = b; }
        static Response json(String b) { return new Response(200, "application/json; charset=utf-8", b); }
        static Response error(int s, String msg) { return new Response(s, "application/json; charset=utf-8", WebServer.errorJson(msg)); }
    }

    private Graph graph;
    private final EditHistory history = new EditHistory();
    private int version;                 // bumps on every edit so the simulator knows to rebuild
    private Simulator sim;
    private int simVersion = -1;
    private Simulator.Settings simSettings = new Simulator.Settings();
    private final Lab lab = new Lab();

    public Api() { graph = Samples.load("classroom"); }

    public Response handle(String method, String path, HashMap<String, String> q, String body) {
        return switch (path) {
            case "/api/samples" -> Response.json(samples());
            case "/api/graph" -> Response.json(graphJson());
            case "/api/load" -> Response.json(load(q, body));
            case "/api/generate" -> Response.json(generate(q));
            case "/api/export" -> export();
            case "/api/edit" -> Response.json(edit(q));
            case "/api/search" -> Response.json(search(q));
            case "/api/route" -> Response.json(route(q));
            case "/api/algo" -> Response.json(algo(q));
            case "/api/analysis" -> Response.json(analysis());
            case "/api/sim/reset" -> Response.json(simReset(q));
            case "/api/sim/transfer" -> Response.json(method.equals("POST") ? simTransfer(q) : simTransferInfo(q));
            case "/api/sim/failure" -> Response.json(simFailure(q));
            case "/api/sim/random" -> Response.json(simRandom(q));
            case "/api/sim/step" -> Response.json(simStep(q));
            case "/api/sim/state" -> Response.json(simState(q));
            case "/api/sim/entry" -> Response.json(simEntry(q));
            case "/api/experiment" -> Response.json(experiment(q));
            case "/api/bench/list" -> Response.json(benchList());
            case "/api/bench" -> Response.json(bench(q));
            case "/api/lab" -> Response.json(labOp(q));
            default -> Response.error(404, "no such endpoint " + path);
        };
    }

    // ================================================================== network
    private String samples() {
        Json j = new Json().arr();
        for (String[] s : Samples.LIST) j.obj().put("key", s[0]).put("name", s[1]).end();
        return j.end().toString();
    }

    synchronized String graphJson() {
        Json j = new Json().obj();
        j.put("title", graph.title()).put("version", version);
        j.key("nodes").arr();
        for (Node n : graph.allNodes()) {
            if (n.deleted) continue;
            j.obj().put("id", n.id).put("name", n.name).put("x", n.x).put("y", n.y).put("up", n.up).end();
        }
        j.end().key("edges").arr();
        for (Edge e : graph.allEdges()) {
            if (e.deleted) continue;
            j.obj().put("id", e.id).put("u", e.u).put("v", e.v).put("cost", e.cost).put("cap", e.capacity).put("up", e.up).end();
        }
        j.end();
        j.key("undo").arr(); int k = 0; for (String s : history.undoLabels()) { if (k++ < 8) j.val(s); } j.end();
        j.key("redo").arr(); k = 0; for (String s : history.redoLabels()) { if (k++ < 8) j.val(s); } j.end();
        return j.end().toString();
    }

    private synchronized String load(HashMap<String, String> q, String body) {
        String sample = q.get("sample");
        Graph g;
        if (sample != null && !sample.isEmpty()) g = Samples.load(sample);
        else {
            if (body == null || body.isBlank()) throw new IllegalArgumentException("the file is empty");
            String fmt = q.getOrDefault("format", "txt");
            g = fmt.equals("gml") || body.contains("graph [") || body.contains("graph[") ? GmlParser.parse(body) : TextFormat.parse(body);
        }
        replaceGraph(g);
        return graphJson();
    }

    private synchronized String generate(HashMap<String, String> q) {
        int n = intParam(q, "n", 20);
        if (n < 3 || n > 300) throw new IllegalArgumentException("router count must be between 3 and 300");
        double alpha = dblParam(q, "alpha", 0.15), beta = dblParam(q, "beta", 0.6);
        long seed = (long) dblParam(q, "seed", System.currentTimeMillis() % 100000);
        replaceGraph(Generators.waxman(n, alpha, beta, seed));
        return graphJson();
    }

    private void replaceGraph(Graph g) {
        graph = g;
        history.clear();
        version++;
    }

    private synchronized Response export() {
        Response r = new Response(200, "text/plain; charset=utf-8", TextFormat.write(graph.compacted()));
        r.downloadName = graph.title().replaceAll("[^A-Za-z0-9_-]+", "_") + ".txt";
        return r;
    }

    private synchronized String edit(HashMap<String, String> q) {
        String op = req(q, "op");
        String msg;
        switch (op) {
            case "addNode" -> {
                String name = q.getOrDefault("name", "").trim();
                if (name.isEmpty()) name = freshName();
                EditHistory.AddNode c = new EditHistory.AddNode(name, dblParam(q, "x", 500), dblParam(q, "y", 320));
                history.run(graph, c);
                msg = "Added router " + name + ".";
            }
            case "deleteNode" -> { int id = node(q, "id"); String nm = graph.name(id); history.run(graph, new EditHistory.DeleteNode(graph, id)); msg = "Deleted router " + nm + " and its links."; }
            case "moveNode" -> { int id = node(q, "id"); history.run(graph, new EditHistory.MoveNode(graph, id, dblParam(q, "x", 0), dblParam(q, "y", 0))); msg = "Moved " + graph.name(id) + "."; }
            case "addEdge" -> {
                int u = node(q, "u"), v = node(q, "v");
                double cost = dblParam(q, "cost", -1);
                if (cost <= 0) {
                    Node a = graph.node(u), b = graph.node(v);
                    cost = Generators.costFor(Math.hypot(a.x - b.x, a.y - b.y));
                }
                history.run(graph, new EditHistory.AddEdge(graph, u, v, cost, intParam(q, "cap", 20)));
                msg = "Linked " + graph.name(u) + " and " + graph.name(v) + ".";
            }
            case "deleteEdge" -> { int id = edge(q, "id"); history.run(graph, new EditHistory.DeleteEdge(graph, id)); msg = "Deleted a link."; }
            case "updateEdge" -> { int id = edge(q, "id"); history.run(graph, new EditHistory.UpdateEdge(graph, id, dblParam(q, "cost", 1), intParam(q, "cap", 20))); msg = "Updated link."; }
            case "fail", "repair" -> {
                boolean isNode = req(q, "kind").equals("node");
                int id = isNode ? node(q, "id") : edge(q, "id");
                EditHistory.SetUp c = new EditHistory.SetUp(graph, isNode, id, op.equals("repair"));
                history.run(graph, c);
                msg = Character.toUpperCase(c.label().charAt(0)) + c.label().substring(1) + ".";
            }
            case "repairAll" -> { graph.restoreAll(); msg = "Every router and link is up again."; }
            case "undo" -> msg = "Undid: " + history.undo(graph) + ".";
            case "redo" -> msg = "Redid: " + history.redo(graph) + ".";
            case "layout" -> { Generators.forceLayout(graph, 300, version); history.clear(); msg = "Re-arranged with a force-directed layout (undo history cleared)."; }
            case "rename" -> { graph.setTitle(req(q, "title")); msg = "Renamed."; }
            default -> throw new IllegalArgumentException("unknown edit '" + op + "'");
        }
        version++;
        String g = graphJson();
        return new Json().obj().put("msg", msg).key("graph").raw(g).end().toString();
    }

    private String freshName() {
        for (int i = graph.nodeSlots() + 1; ; i++) if (graph.idOf("R" + i) == null) return "R" + i;
    }

    private synchronized String search(HashMap<String, String> q) {
        Json j = new Json().arr();
        for (String s : graph.searchNames(q.getOrDefault("q", ""), 8)) j.obj().put("name", s).put("id", graph.idOf(s)).end();
        return j.end().toString();
    }

    // ================================================================== routing & algorithms
    private synchronized String route(HashMap<String, String> q) {
        int src = node(q, "src"), dst = node(q, "dst");
        requireUp(src); requireUp(dst);
        String mode = q.getOrDefault("mode", "cheapest");
        Json j = new Json().obj().put("mode", mode).key("routes").arr();
        switch (mode) {
            case "cheapest" -> Dijkstra.shortestPath(graph, src, dst, null, null).toJson(j, graph);
            case "hops" -> BFS.fewestHops(graph, src, dst, null, null).toJson(j, graph);
            case "bellman" -> BellmanFord.path(graph, src, dst, null).toJson(j, graph);
            case "safest" -> {
                Tarjan.Result t = Tarjan.run(graph, null);
                HashSet<Integer> bridges = new HashSet<>();
                for (int b : t.bridges) bridges.add(b);
                PathResult p = Routing.safest(graph, src, dst, bridges);
                int crossed = 0;
                for (int e : p.edges) if (bridges.contains(e)) crossed++;
                p.visited = crossed;
                p.toJson(j, graph);
            }
            case "alternatives" -> {
                for (PathResult p : Routing.alternatives(graph, src, dst, Math.max(1, Math.min(6, intParam(q, "k", 3))), 3.0)) p.toJson(j, graph);
            }
            case "compare" -> {
                Dijkstra.shortestPath(graph, src, dst, null, null).toJson(j, graph);
                BFS.fewestHops(graph, src, dst, null, null).toJson(j, graph);
                BellmanFord.path(graph, src, dst, null).toJson(j, graph);
            }
            default -> throw new IllegalArgumentException("unknown routing mode '" + mode + "'");
        }
        return j.end().end().toString();
    }

    private synchronized String algo(HashMap<String, String> q) {
        String name = req(q, "name");
        Trace t = new Trace(name);
        Json j = new Json().obj().put("name", name);
        switch (name) {
            case "bfs" -> { int s = node(q, "src"), d = node(q, "dst"); requireUp(s); j.key("path"); BFS.fewestHops(graph, s, d, null, t).toJson(j, graph); }
            case "dfs" -> { int s = node(q, "src"); requireUp(s); DynamicArray<Integer> order = DFS.iterative(graph, s, t); j.key("order").arr(); for (int id : order) j.val(graph.name(id)); j.end(); }
            case "dijkstra" -> { int s = node(q, "src"), d = node(q, "dst"); requireUp(s); j.key("path"); Dijkstra.shortestPath(graph, s, d, null, t).toJson(j, graph); }
            case "bellman" -> { int s = node(q, "src"), d = node(q, "dst"); requireUp(s); j.key("path"); BellmanFord.path(graph, s, d, t).toJson(j, graph); }
            case "tarjan" -> { Tarjan.Result r = Tarjan.run(graph, t); j.put("articulationPoints", r.articulationPoints.size()).put("bridges", r.bridges.size()); }
            case "kruskal" -> { MST.Result r = MST.kruskal(graph, q.getOrDefault("sort", "merge").equals("quick"), t); j.put("cost", r.totalCost).put("links", r.edges.size()); }
            case "prim" -> { MST.Result r = MST.prim(graph, t); j.put("cost", r.totalCost).put("links", r.edges.size()); }
            default -> throw new IllegalArgumentException("unknown algorithm '" + name + "'");
        }
        j.key("trace");
        t.toJson(j);
        return j.end().toString();
    }

    private synchronized String analysis() {
        Json j = new Json().obj();
        Resilience.Report r = Resilience.analyse(graph);
        j.put("routers", graph.upNodeCount()).put("links", graph.usableEdgeCount())
         .put("components", r.components).put("largest", r.largestComponent)
         .put("routerScore", r.routerScore).put("linkScore", r.linkScore);
        j.key("articulationPoints").arr();
        for (Resilience.Impact im : r.routerImpact) j.obj().put("id", im.id).put("name", graph.name(im.id)).put("cutOff", im.cutOff).end();
        j.end();
        j.key("bridges").arr();
        for (Resilience.Impact im : r.linkImpact) {
            Edge e = graph.edge(im.id);
            j.obj().put("id", im.id).put("name", graph.name(e.u) + " - " + graph.name(e.v)).put("cutOff", im.cutOff).end();
        }
        j.end();

        MST.Result k = MST.kruskal(graph, false, null);
        MST.Result p = MST.prim(graph, null);
        j.key("mst").obj().put("cost", k.totalCost).put("primCost", p.totalCost).put("networkCost", k.networkCost)
         .put("links", k.edges.size()).put("trees", k.trees).put("saving", k.networkCost == 0 ? 0 : 1 - k.totalCost / k.networkCost).key("edges").arr();
        for (int e : k.edges) j.val(e);
        j.end().end();

        FloydWarshall.Result f = FloydWarshall.run(graph);
        j.key("floyd").obj().put("connected", f.connected).put("diameter", f.diameter)
         .put("diameterFrom", f.diameterFrom < 0 ? "" : graph.name(f.diameterFrom))
         .put("diameterTo", f.diameterTo < 0 ? "" : graph.name(f.diameterTo))
         .put("center", f.center < 0 ? "" : graph.name(f.center)).put("centerId", f.center)
         .put("centerEccentricity", f.centerEccentricity);
        if (f.diameterFrom >= 0) {
            int a = -1, b = -1;
            for (int i = 0; i < f.ids.length; i++) { if (f.ids[i] == f.diameterFrom) a = i; if (f.ids[i] == f.diameterTo) b = i; }
            j.key("diameterPath").arr(); for (int id : f.path(a, b)) j.val(id); j.end();
        }
        // a small slice of the distance matrix for display (up to 12 routers)
        int show = Math.min(12, f.ids.length);
        j.key("matrixNames").arr(); for (int i = 0; i < show; i++) j.val(graph.name(f.ids[i])); j.end();
        j.key("matrix").arr();
        for (int i = 0; i < show; i++) { j.arr(); for (int c = 0; c < show; c++) j.val(f.dist[i][c]); j.end(); }
        j.end().end();

        int[] comp = DFS.components(graph);
        j.key("componentOf").arr(); for (int c : comp) j.val(c); j.end();
        return j.end().toString();
    }

    // ================================================================== simulation
    private Simulator sim() {
        if (sim == null || simVersion != version) {
            sim = new Simulator(graph.copy(), simSettings);
            simVersion = version;
        }
        return sim;
    }

    private synchronized String simReset(HashMap<String, String> q) {
        Simulator.Settings s = new Simulator.Settings();
        s.strategy = q.getOrDefault("strategy", "CHEAPEST").equals("FEWEST_HOPS") ? Simulator.Strategy.FEWEST_HOPS : Simulator.Strategy.CHEAPEST;
        s.useCache = !q.getOrDefault("cache", "true").equals("false");
        s.priorityMode = q.getOrDefault("priority", "false").equals("true");
        s.maxActive = clamp(intParam(q, "maxActive", 8), 1, 50);
        s.maxRetries = clamp(intParam(q, "maxRetries", 4), 0, 20);
        s.hopDelay = Math.max(0.01, dblParam(q, "hopDelay", 0.4));
        s.cacheSize = clamp(intParam(q, "cacheSize", 32), 1, 1000);
        simSettings = s;
        sim = null;
        return simState(q);
    }

    private synchronized String simTransfer(HashMap<String, String> q) {
        Simulator s = sim();
        int src = node(q, "src"), dst = node(q, "dst");
        int amount = clamp(intParam(q, "amount", 10), 1, 1000);
        int id = s.submit(src, dst, amount, clamp(intParam(q, "priority", 1), 1, 9), s.clock() + dblParam(q, "delay", 0), Math.max(0.1, dblParam(q, "hold", 5)));
        return new Json().obj().put("msg", "Transfer T" + id + " submitted.").put("id", id).end().toString();
    }

    private synchronized String simTransferInfo(HashMap<String, String> q) {
        Simulator s = sim();
        Transfer t = s.transfer(intParam(q, "id", -1));
        if (t == null) throw new IllegalArgumentException("no such transfer");
        Json j = new Json();
        SimView.transfer(s.graph(), t, j, true);
        return j.toString();
    }

    private synchronized String simFailure(HashMap<String, String> q) {
        Simulator s = sim();
        boolean isNode = req(q, "kind").equals("node");
        double at = s.clock() + Math.max(0, dblParam(q, "delay", 0)), dur = Math.max(0, dblParam(q, "duration", 0));
        if (isNode) s.scheduleNodeFailure(node(q, "id"), at, dur); else s.scheduleLinkFailure(edge(q, "id"), at, dur);
        return new Json().obj().put("msg", "Failure scheduled at t=" + Math.round(at * 100) / 100.0 + (dur > 0 ? ", repaired " + dur + " later." : " (permanent).")).end().toString();
    }

    private synchronized String simRandom(HashMap<String, String> q) {
        Simulator s = sim();
        int n = clamp(intParam(q, "transfers", 20), 0, 2000), f = clamp(intParam(q, "failures", 5), 0, 500);
        double span = Math.max(1, dblParam(q, "span", 30));
        long seed = (long) dblParam(q, "seed", 42);
        if (n > 0) s.randomTransfers(n, span, seed);
        if (f > 0) s.randomFailures(f, span, Math.max(0.5, dblParam(q, "downtime", 6)), seed + 1);
        return new Json().obj().put("msg", "Scheduled " + n + " transfers and " + f + " failures over " + span + " time units.").end().toString();
    }

    private synchronized String simStep(HashMap<String, String> q) {
        Simulator s = sim();
        String mode = q.getOrDefault("mode", "events");
        int processed = switch (mode) {
            case "until" -> s.runUntil(s.clock() + Math.max(0.01, dblParam(q, "dt", 1)));
            case "finish" -> s.runToEnd(500_000);
            default -> s.runEvents(clamp(intParam(q, "n", 1), 1, 100000));
        };
        Json j = new Json().obj().put("processed", processed).key("state");
        SimView.state(s, j, clamp(intParam(q, "log", 60), 1, 500));
        return j.end().toString();
    }

    private synchronized String simState(HashMap<String, String> q) {
        Json j = new Json();
        SimView.state(sim(), j, clamp(intParam(q, "log", 60), 1, 500));
        return j.toString();
    }

    private synchronized String simEntry(HashMap<String, String> q) {
        Json j = new Json();
        if (!SimView.entry(sim(), intParam(q, "i", -1), j)) throw new IllegalArgumentException("that log entry is no longer kept");
        return j.toString();
    }

    private String experiment(HashMap<String, String> q) {
        Graph copy;
        synchronized (this) { copy = graph.copy(); }
        Json j = new Json();
        Experiments.run(copy, clamp(intParam(q, "transfers", 60), 1, 2000), clamp(intParam(q, "failures", 10), 0, 500),
                Math.max(1, dblParam(q, "span", 40)), Math.max(0.5, dblParam(q, "downtime", 8)), (long) dblParam(q, "seed", 7), j);
        return j.toString();
    }

    // ================================================================== benchmarks & lab
    private String benchList() {
        Json j = new Json().arr();
        for (String[] s : Benchmarks.SUITES) j.obj().put("key", s[0]).put("title", s[1]).put("note", s[2]).end();
        return j.end().toString();
    }

    private String bench(HashMap<String, String> q) {
        Json j = new Json();
        Benchmarks.run(req(q, "suite"), j);
        return j.toString();
    }

    private synchronized String labOp(HashMap<String, String> q) {
        Json j = new Json();
        String op = q.getOrDefault("op", "");
        if (op.isEmpty()) { j.obj().put("ok", true).put("msg", "").key("snap"); lab.snapshot(req(q, "ds"), j); j.end(); }
        else lab.op(req(q, "ds"), op, q.get("a"), q.get("b"), j);
        return j.toString();
    }

    // ================================================================== helpers
    private static String req(HashMap<String, String> q, String k) {
        String v = q.get(k);
        if (v == null || v.isEmpty()) throw new IllegalArgumentException("missing parameter '" + k + "'");
        return v;
    }

    private int node(HashMap<String, String> q, String k) { return graph.requireId(req(q, k)); }

    private int edge(HashMap<String, String> q, String k) {
        int id = intParam(q, k, -1);
        if (id < 0 || id >= graph.edgeSlots() || graph.edge(id).deleted) throw new IllegalArgumentException("unknown link");
        return id;
    }

    private void requireUp(int id) {
        if (!graph.nodeUp(id)) throw new IllegalArgumentException("router " + graph.name(id) + " is down");
    }

    private static int intParam(HashMap<String, String> q, String k, int def) {
        String v = q.get(k);
        if (v == null || v.isBlank()) return def;
        try { return (int) Math.round(Double.parseDouble(v.trim())); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("'" + k + "' must be a number"); }
    }

    private static double dblParam(HashMap<String, String> q, String k, double def) {
        String v = q.get(k);
        if (v == null || v.isBlank()) return def;
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("'" + k + "' must be a number"); }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
