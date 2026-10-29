package netrescue.bench;

import java.util.Random;

import netrescue.algo.BFS;
import netrescue.algo.BellmanFord;
import netrescue.algo.Dijkstra;
import netrescue.algo.FloydWarshall;
import netrescue.ds.DynamicArray;
import netrescue.ds.HashMap;
import netrescue.ds.LinearProbingHashMap;
import netrescue.ds.Queue;
import netrescue.ds.Sorting;
import netrescue.graph.Generators;
import netrescue.graph.Graph;
import netrescue.io.Json;

/**
 * Measured running times, to check theory against reality.
 *
 * Method: build the input, warm up the JIT compiler, repeat short jobs in
 * batches long enough to time, and report the MEDIAN of 5 batches
 * (System.nanoTime). The median ignores an odd slow batch caused by garbage
 * collection.
 *
 * Absolute numbers depend on the machine; the SHAPE of each curve
 * (linear, n log n, quadratic, cubic) is what to compare with the theory.
 */
public final class Benchmarks {
    private Benchmarks() {}

    public static final String[][] SUITES = {
        {"queue", "Queue: circular buffer vs shifting array", "Dequeue from a ring buffer is O(1); removing index 0 of an array shifts every element, O(n). Draining n items: O(n) vs O(n^2)."},
        {"sorting", "Sorting: merge sort vs quicksort", "Both O(n log n) on random data. Merge sort is stable and never worse; quicksort is in-place and usually faster."},
        {"hashing", "Hash maps: chaining vs linear probing", "Both O(1) expected per operation, so total time grows linearly with n. Probing has better cache locality; chaining degrades more gracefully."},
        {"sssp", "Single-source shortest paths: BFS, Dijkstra, Bellman-Ford", "BFS O(V+E) (hops only), Dijkstra O((V+E) log V), Bellman-Ford O(V E). Sparse graphs, average degree 4."},
        {"dijkstra", "Dijkstra: binary heap vs plain array", "Heap version O((V+E) log V), array version O(V^2). On sparse graphs the heap wins by a growing margin."},
        {"allpairs", "All pairs: Floyd-Warshall vs V x Dijkstra", "Floyd-Warshall O(V^3) with tiny constants; running Dijkstra from every router O(V (V+E) log V). Sparse graphs favour Dijkstra as V grows."},
    };

    public static void run(String suite, Json j) {
        switch (suite) {
            case "queue" -> queue(j);
            case "sorting" -> sorting(j);
            case "hashing" -> hashing(j);
            case "sssp" -> sssp(j);
            case "dijkstra" -> dijkstra(j);
            case "allpairs" -> allPairs(j);
            default -> throw new IllegalArgumentException("unknown benchmark suite '" + suite + "'");
        }
    }

    interface Job { void run(); }

    /**
     * Milliseconds for ONE run of job, measured robustly:
     *  1. warm up (lets the JIT compile the hot code),
     *  2. pick a repeat count r so one batch takes at least ~15 ms (tiny jobs
     *     are too short to time alone),
     *  3. time 5 batches and take the median batch, divided by r.
     */
    static double time(Job job) {
        long t0 = System.nanoTime();
        int warm = 0;
        while (warm < 3 || (System.nanoTime() - t0 < 100_000_000L && warm < 50)) { job.run(); warm++; }
        double one = (System.nanoTime() - t0) / 1e6 / warm;
        int reps = (int) Math.max(1, Math.min(10_000, Math.ceil(15.0 / Math.max(one, 1e-4))));
        double[] t = new double[5];
        for (int b = 0; b < 5; b++) {
            long s = System.nanoTime();
            for (int r = 0; r < reps; r++) job.run();
            t[b] = (System.nanoTime() - s) / 1e6 / reps;
        }
        // median of 5 by insertion sort (tiny array)
        for (int i = 1; i < 5; i++) for (int j = i; j > 0 && t[j] < t[j - 1]; j--) { double x = t[j]; t[j] = t[j - 1]; t[j - 1] = x; }
        return t[2];
    }

    private static Json header(Json j, String id, String x, String... series) {
        String[] meta = null;
        for (String[] s : SUITES) if (s[0].equals(id)) meta = s;
        j.obj().put("suite", id).put("title", meta[1]).put("note", meta[2]).put("x", x).put("y", "time (ms)").key("series").arr();
        for (String s : series) j.val(s);
        j.end().key("rows").arr();
        return j;
    }

    // ------------------------------------------------------------------ queue
    static void queue(Json j) {
        header(j, "queue", "items n", "Queue (ring buffer)", "DynamicArray.removeAt(0)");
        for (int n : new int[]{2000, 4000, 8000, 16000, 32000}) {
            double ring = time(() -> {
                Queue<Integer> q = new Queue<>();
                for (int i = 0; i < n; i++) q.enqueue(i);
                while (!q.isEmpty()) q.dequeue();
            });
            double shift = time(() -> {
                DynamicArray<Integer> a = new DynamicArray<>();
                for (int i = 0; i < n; i++) a.add(i);
                while (!a.isEmpty()) a.removeAt(0);
            });
            j.arr().val(n).val(ring).val(shift).end();
        }
        j.end().end();
    }

    // ------------------------------------------------------------------ sorting
    static void sorting(Json j) {
        header(j, "sorting", "elements n", "Merge sort", "Quicksort", "Merge comparisons / n lg n", "Quick comparisons / n lg n");
        for (int n : new int[]{10000, 50000, 100000, 200000, 400000}) {
            Random r = new Random(n);
            DynamicArray<Double> base = new DynamicArray<>(n);
            for (int i = 0; i < n; i++) base.add(r.nextDouble());
            long[] mc = new long[1], qc = new long[1];
            double m = time(() -> { DynamicArray<Double> a = base.copy(); Sorting.mergeSort(a, Double::compare); mc[0] = Sorting.lastComparisons; });
            double q = time(() -> { DynamicArray<Double> a = base.copy(); Sorting.quickSort(a, Double::compare); qc[0] = Sorting.lastComparisons; });
            double nlogn = n * (Math.log(n) / Math.log(2));
            j.arr().val(n).val(m).val(q).val(mc[0] / nlogn).val(qc[0] / nlogn).end();
        }
        j.end().end();
    }

    // ------------------------------------------------------------------ hashing
    static void hashing(Json j) {
        header(j, "hashing", "keys n (insert + 2 lookups each)", "Separate chaining", "Linear probing", "Longest chain", "Avg probes per op");
        for (int n : new int[]{25000, 50000, 100000, 200000, 400000}) {
            Random r = new Random(n);
            int[] keys = new int[n];
            for (int i = 0; i < n; i++) keys[i] = r.nextInt();
            int[] longest = new int[1];
            double[] probes = new double[1];
            double chain = time(() -> {
                HashMap<Integer, Integer> m = new HashMap<>();
                for (int k : keys) m.put(k, k);
                for (int k : keys) m.get(k);
                for (int k : keys) m.get(k + 1);
                longest[0] = m.longestChain();
            });
            double probe = time(() -> {
                LinearProbingHashMap<Integer, Integer> m = new LinearProbingHashMap<>();
                for (int k : keys) m.put(k, k);
                for (int k : keys) m.get(k);
                for (int k : keys) m.get(k + 1);
                probes[0] = (double) m.probes() / (3.0 * n);
            });
            j.arr().val(n).val(chain).val(probe).val(longest[0]).val(probes[0]).end();
        }
        j.end().end();
    }

    // ------------------------------------------------------------------ graphs
    static void sssp(Json j) {
        header(j, "sssp", "routers V (E = 2V)", "BFS", "Dijkstra (heap)", "Bellman-Ford");
        for (int n : new int[]{250, 500, 1000, 2000, 4000}) {
            Graph g = Generators.randomSparse(n, 4, n);
            double bfs = time(() -> BFS.fewestHops(g, 0, n - 1, null, null));
            double dij = time(() -> Dijkstra.allFrom(g, 0, null));
            double bf = time(() -> BellmanFord.run(g, 0, null));
            j.arr().val(n).val(bfs).val(dij).val(bf).end();
        }
        j.end().end();
    }

    static void dijkstra(Json j) {
        header(j, "dijkstra", "routers V (E = 2V)", "Indexed binary heap", "Array scan (O(V^2))");
        for (int n : new int[]{500, 1000, 2000, 4000, 8000}) {
            Graph g = Generators.randomSparse(n, 4, n + 1);
            double heap = time(() -> Dijkstra.allFrom(g, 0, null));
            double arr = time(() -> Dijkstra.arrayVersion(g, 0));
            j.arr().val(n).val(heap).val(arr).end();
        }
        j.end().end();
    }

    static void allPairs(Json j) {
        header(j, "allpairs", "routers V (E = 2V)", "Floyd-Warshall", "Dijkstra from every router");
        for (int n : new int[]{50, 100, 200, 300, 400}) {
            Graph g = Generators.randomSparse(n, 4, n + 2);
            double fw = time(() -> FloydWarshall.run(g));
            double dj = time(() -> { for (int s = 0; s < n; s++) Dijkstra.allFrom(g, s, null); });
            j.arr().val(n).val(fw).val(dj).end();
        }
        j.end().end();
    }
}
