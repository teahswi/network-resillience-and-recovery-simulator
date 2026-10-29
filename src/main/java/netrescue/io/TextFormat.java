package netrescue.io;

import netrescue.ds.DynamicArray;
import netrescue.graph.Edge;
import netrescue.graph.Graph;
import netrescue.graph.Node;

/**
 * NetRescue's own plain-text network format, one item per line:
 *
 *   # comment
 *   title My network
 *   node <name> <x> <y>
 *   link <name1> <name2> <cost> <capacity>
 *
 * Names containing spaces are written in double quotes: node "New York" 820 140
 */
public final class TextFormat {
    private TextFormat() {}

    public static Graph parse(String text) {
        Graph g = new Graph();
        String[] lines = text.split("\r?\n");
        for (int ln = 0; ln < lines.length; ln++) {
            String line = lines[ln].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                if (line.startsWith("title ")) { g.setTitle(line.substring(6).trim()); continue; }
                DynamicArray<String> tok = tokenize(line);
                switch (tok.get(0).toLowerCase()) {
                    case "node" -> {
                        need(tok, 4);
                        g.addNode(tok.get(1), Double.parseDouble(tok.get(2)), Double.parseDouble(tok.get(3)));
                    }
                    case "link" -> {
                        need(tok, 5);
                        int a = g.requireId(tok.get(1)), b = g.requireId(tok.get(2));
                        g.addEdge(a, b, Double.parseDouble(tok.get(3)), Integer.parseInt(tok.get(4)));
                    }
                    default -> throw new IllegalArgumentException("unknown keyword '" + tok.get(0) + "'");
                }
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("line " + (ln + 1) + ": " + ex.getMessage(), ex);
            }
        }
        if (g.liveNodeCount() == 0) throw new IllegalArgumentException("no routers found in the file");
        return g;
    }

    public static String write(Graph g) {
        StringBuilder sb = new StringBuilder("# NetRescue network file\n");
        sb.append("title ").append(g.title()).append("\n\n");
        for (Node n : g.allNodes()) {
            if (n.deleted) continue;
            sb.append("node ").append(quote(n.name)).append(' ').append(num(n.x)).append(' ').append(num(n.y)).append('\n');
        }
        sb.append('\n');
        for (Edge e : g.allEdges()) {
            if (e.deleted) continue;
            sb.append("link ").append(quote(g.name(e.u))).append(' ').append(quote(g.name(e.v)))
              .append(' ').append(num(e.cost)).append(' ').append(e.capacity).append('\n');
        }
        return sb.toString();
    }

    /** Splits on spaces, keeping "quoted strings" together. */
    static DynamicArray<String> tokenize(String line) {
        DynamicArray<String> out = new DynamicArray<>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '"') {
                int j = line.indexOf('"', i + 1);
                if (j < 0) throw new IllegalArgumentException("unclosed quote");
                out.add(line.substring(i + 1, j));
                i = j + 1;
            } else {
                int j = i;
                while (j < line.length() && !Character.isWhitespace(line.charAt(j))) j++;
                out.add(line.substring(i, j));
                i = j;
            }
        }
        return out;
    }

    private static void need(DynamicArray<String> tok, int n) {
        if (tok.size() < n) throw new IllegalArgumentException("expected " + (n - 1) + " values after '" + tok.get(0) + "'");
    }

    private static String quote(String s) { return s.indexOf(' ') >= 0 ? "\"" + s + "\"" : s; }

    private static String num(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
}
