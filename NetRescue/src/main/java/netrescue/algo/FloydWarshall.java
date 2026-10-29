package netrescue.algo;

import netrescue.ds.DynamicArray;
import netrescue.graph.Edge;
import netrescue.graph.Graph;

/**
 * Floyd-Warshall all-pairs shortest paths (Floyd 1962, "Algorithm 97";
 * Warshall 1962). A dynamic-programming algorithm:
 *
 *   d_k[i][j] = shortest i->j distance using only routers 0..k as stops
 *   d_k[i][j] = min( d_{k-1}[i][j],  d_{k-1}[i][k] + d_{k-1}[k][j] )
 *
 * Three nested loops over k, i, j:  O(V^3) time, O(V^2) space.
 * next[i][j] records the first hop on the best i->j route, so any path can
 * be rebuilt afterwards.
 *
 * From the distance matrix we get:
 *   eccentricity(v) = distance to the farthest router from v
 *   diameter        = largest eccentricity  (worst-case route length)
 *   center          = router with the smallest eccentricity (best place for a server)
 */
public final class FloydWarshall {
    private FloydWarshall() {}

    public static final class Result {
        public int[] ids;            // matrix row -> router id (only routers that are up)
        public double[][] dist;
        public int[][] next;         // next[i][j] = matrix index of first hop, -1 if none
        public double diameter;
        public int diameterFrom = -1, diameterTo = -1;
        public int center = -1;
        public double centerEccentricity;
        public boolean connected;

        public DynamicArray<Integer> path(int fromRow, int toRow) {
            DynamicArray<Integer> p = new DynamicArray<>();
            if (next[fromRow][toRow] == -1 && fromRow != toRow) return p;
            int cur = fromRow;
            p.add(ids[cur]);
            while (cur != toRow) { cur = next[cur][toRow]; p.add(ids[cur]); }
            return p;
        }
    }

    public static Result run(Graph g) {
        DynamicArray<Integer> live = new DynamicArray<>();
        for (int i = 0; i < g.nodeSlots(); i++) if (g.nodeUp(i)) live.add(i);
        int n = live.size();
        int[] row = new int[g.nodeSlots()];
        for (int i = 0; i < row.length; i++) row[i] = -1;
        Result r = new Result();
        r.ids = new int[n];
        for (int i = 0; i < n; i++) { r.ids[i] = live.get(i); row[live.get(i)] = i; }
        double[][] d = new double[n][n];
        int[][] nx = new int[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            d[i][j] = i == j ? 0 : Double.POSITIVE_INFINITY;
            nx[i][j] = i == j ? i : -1;
        }
        for (Edge e : g.allEdges()) {
            if (!g.edgeUsable(e)) continue;
            int a = row[e.u], b = row[e.v];
            if (e.cost < d[a][b]) { d[a][b] = d[b][a] = e.cost; nx[a][b] = b; nx[b][a] = a; }
        }
        for (int k = 0; k < n; k++) {
            double[] dk = d[k];
            for (int i = 0; i < n; i++) {
                double dik = d[i][k];
                if (dik == Double.POSITIVE_INFINITY) continue;
                double[] di = d[i];
                for (int j = 0; j < n; j++) {
                    double via = dik + dk[j];
                    if (via < di[j]) { di[j] = via; nx[i][j] = nx[i][k]; }
                }
            }
        }
        r.dist = d;
        r.next = nx;
        r.connected = true;
        r.centerEccentricity = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double ecc = 0;
            for (int j = 0; j < n; j++) {
                if (d[i][j] == Double.POSITIVE_INFINITY) { r.connected = false; continue; }
                if (d[i][j] > ecc) ecc = d[i][j];
                if (d[i][j] > r.diameter) { r.diameter = d[i][j]; r.diameterFrom = r.ids[i]; r.diameterTo = r.ids[j]; }
            }
            if (ecc < r.centerEccentricity) { r.centerEccentricity = ecc; r.center = r.ids[i]; }
        }
        return r;
    }
}
