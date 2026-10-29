package netrescue.io;

import netrescue.ds.DynamicArray;
import netrescue.ds.HashMap;
import netrescue.ds.Stack;
import netrescue.graph.Generators;
import netrescue.graph.Graph;

/**
 * Reader for GML (Graph Modelling Language), the format used by the Internet
 * Topology Zoo (topology-zoo.org) for real ISP and research networks.
 *
 *   graph [
 *     label "Abilene"
 *     node [ id 0 label "Seattle" Latitude 47.6 Longitude -122.3 ]
 *     edge [ source 0 target 1 ]
 *   ]
 *
 * Parsing is a two-step job:
 *  1. TOKENIZE: split the text into  key, number, "string", '[' and ']'.
 *  2. BUILD A TREE with a STACK: '[' pushes a new list, ']' pops back to the
 *     parent. This is the same bracket-matching idea as checking balanced
 *     parentheses -- an unmatched ']' or a missing ']' is a syntax error.
 *
 * Routers with coordinates are placed by an equirectangular projection
 * (x = longitude, y = -latitude); otherwise a force-directed layout is used.
 * Link cost = great-circle distance in units of 100 km (haversine formula).
 */
public final class GmlParser {
    private GmlParser() {}

    /** A GML list: ordered key/value pairs where a value is a String or another Block. */
    static final class Block {
        final DynamicArray<String> keys = new DynamicArray<>();
        final DynamicArray<Object> values = new DynamicArray<>();
        String str(String k) { int i = keys.indexOf(k); return i < 0 || !(values.get(i) instanceof String s) ? null : s; }
        Double num(String k) {
            String s = str(k);
            if (s == null) return null;
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
    }

    public static Graph parse(String text) {
        Block root = buildTree(tokenize(text));
        Block graph = null;
        for (int i = 0; i < root.keys.size(); i++)
            if (root.keys.get(i).equalsIgnoreCase("graph") && root.values.get(i) instanceof Block b) graph = b;
        if (graph == null) throw new IllegalArgumentException("no 'graph [ ... ]' block found");

        Graph g = new Graph();
        String label = graph.str("label");
        if (label == null) label = graph.str("Network");
        g.setTitle(label == null ? "Imported GML network" : label);

        HashMap<String, Integer> gmlIdToNode = new HashMap<>();
        DynamicArray<double[]> latLon = new DynamicArray<>();
        boolean allHaveCoords = true;
        for (int i = 0; i < graph.keys.size(); i++) {
            if (!graph.keys.get(i).equals("node") || !(graph.values.get(i) instanceof Block nb)) continue;
            String gid = nb.str("id");
            if (gid == null) throw new IllegalArgumentException("a node has no id");
            String name = nb.str("label");
            if (name == null || name.isBlank()) name = "N" + gid;
            String unique = name;
            for (int k = 2; g.idOf(unique) != null; k++) unique = name + " (" + k + ")";
            Double lat = nb.num("Latitude"), lon = nb.num("Longitude");
            if (lat == null || lon == null) allHaveCoords = false;
            latLon.add(new double[]{lat == null ? Double.NaN : lat, lon == null ? Double.NaN : lon});
            int id = g.addNode(unique, lon == null ? 0 : lon, lat == null ? 0 : -lat);
            gmlIdToNode.put(gid, id);
        }
        if (g.liveNodeCount() == 0) throw new IllegalArgumentException("the graph has no nodes");

        for (int i = 0; i < graph.keys.size(); i++) {
            if (!graph.keys.get(i).equals("edge") || !(graph.values.get(i) instanceof Block eb)) continue;
            Integer a = gmlIdToNode.get(eb.str("source")), b = gmlIdToNode.get(eb.str("target"));
            if (a == null || b == null) throw new IllegalArgumentException("edge refers to an unknown node id");
            if (a.equals(b) || g.findEdge(a, b) != null) continue;   // skip self-loops and parallel links
            double cost;
            double[] p = latLon.get(a), q = latLon.get(b);
            if (!Double.isNaN(p[0]) && !Double.isNaN(q[0])) cost = Math.max(1, Math.round(haversineKm(p[0], p[1], q[0], q[1]) / 10.0) / 10.0);
            else cost = 10;
            int capacity = 20;
            Double speed = eb.num("LinkSpeedRaw");
            if (speed != null && speed > 0) capacity = speed >= 9e9 ? 40 : speed >= 2e9 ? 20 : 10;
            g.addEdge(a, b, cost, capacity);
        }
        if (allHaveCoords) Generators.fitToCanvas(g); else Generators.forceLayout(g, 300, 7);
        return g;
    }

    /** Great-circle distance between two lat/lon points, in km. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    static DynamicArray<String> tokenize(String s) {
        DynamicArray<String> out = new DynamicArray<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '#') { while (i < n && s.charAt(i) != '\n') i++; continue; }   // comment line
            if (c == '[' || c == ']') { out.add(String.valueOf(c)); i++; continue; }
            if (c == '"') {
                int j = s.indexOf('"', i + 1);
                if (j < 0) throw new IllegalArgumentException("unclosed string");
                out.add("\u0000" + s.substring(i + 1, j));   // marker: this token was a string
                i = j + 1;
                continue;
            }
            int j = i;
            while (j < n && !Character.isWhitespace(s.charAt(j)) && s.charAt(j) != '[' && s.charAt(j) != ']') j++;
            out.add(s.substring(i, j));
            i = j;
        }
        return out;
    }

    static Block buildTree(DynamicArray<String> tokens) {
        Stack<Block> stack = new Stack<>();
        Block root = new Block();
        stack.push(root);
        int i = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (t.equals("]")) {
                if (stack.size() == 1) throw new IllegalArgumentException("unexpected ']' (no matching '[')");
                stack.pop();
                i++;
                continue;
            }
            if (t.equals("[")) throw new IllegalArgumentException("'[' without a key before it");
            String key = t.startsWith("\u0000") ? t.substring(1) : t;
            if (i + 1 >= tokens.size()) throw new IllegalArgumentException("key '" + key + "' has no value");
            String v = tokens.get(i + 1);
            if (v.equals("[")) {
                Block child = new Block();
                stack.peek().keys.add(key);
                stack.peek().values.add(child);
                stack.push(child);
            } else if (v.equals("]")) {
                throw new IllegalArgumentException("key '" + key + "' has no value");
            } else {
                stack.peek().keys.add(key);
                stack.peek().values.add(v.startsWith("\u0000") ? v.substring(1) : v);
            }
            i += 2;
        }
        if (stack.size() != 1) throw new IllegalArgumentException("missing ']' -- " + (stack.size() - 1) + " list(s) left open");
        return root;
    }
}
