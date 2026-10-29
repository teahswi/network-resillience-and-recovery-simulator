package netrescue.sim;

import netrescue.ds.DynamicArray;
import netrescue.graph.Graph;
import netrescue.io.Json;

/**
 * Runs the SAME random scenario (same seed: same transfers, same failures)
 * under different settings, each on a fresh copy of the network, and
 * compares the results. Same input, one change at a time = a fair comparison.
 */
public final class Experiments {
    private Experiments() {}

    public static final class Variant {
        final String name;
        final Simulator.Settings settings;
        Variant(String name, Simulator.Settings s) { this.name = name; this.settings = s; }
    }

    public static DynamicArray<Variant> standardVariants() {
        DynamicArray<Variant> v = new DynamicArray<>();
        Simulator.Settings a = new Simulator.Settings();
        v.add(new Variant("Cheapest (Dijkstra) + cache", a));
        Simulator.Settings b = new Simulator.Settings(); b.strategy = Simulator.Strategy.FEWEST_HOPS;
        v.add(new Variant("Fewest hops (BFS) + cache", b));
        Simulator.Settings c = new Simulator.Settings(); c.useCache = false;
        v.add(new Variant("Cheapest, no cache", c));
        Simulator.Settings d = new Simulator.Settings(); d.priorityMode = true;
        v.add(new Variant("Cheapest + priority queue", d));
        Simulator.Settings e = new Simulator.Settings(); e.maxRetries = 0;
        v.add(new Variant("Cheapest, no retries", e));
        return v;
    }

    public static void run(Graph base, int transfers, int failures, double span, double downtime, long seed, Json j) {
        j.obj().put("transfers", transfers).put("failures", failures).put("span", span).put("seed", seed).key("rows").arr();
        for (Variant v : standardVariants()) {
            Graph g = base.copy();
            g.restoreAll();
            long start = System.nanoTime();
            Simulator s = new Simulator(g, v.settings);
            s.randomTransfers(transfers, span, seed);
            s.randomFailures(failures, span, downtime, seed + 1);
            s.runToEnd(2_000_000);
            double ms = (System.nanoTime() - start) / 1e6;
            j.obj().put("name", v.name);
            SimView.metrics(s, j.key("metrics"));
            j.put("ms", ms).put("endTime", s.clock).end();
        }
        j.end().end();
    }
}
