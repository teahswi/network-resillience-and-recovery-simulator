package netrescue.io;

/**
 * Minimal streaming JSON writer (no library). Commas are inserted
 * automatically: per open object/array we remember whether the next item is
 * the first one and which bracket closes it. Those live in small arrays used
 * as a stack (depth = stack pointer).
 *
 *   Json j = new Json();
 *   j.obj().key("name").val("A").key("ids").arr().val(1).val(2).end().end();
 *   j.toString()  ->  {"name":"A","ids":[1,2]}
 */
public class Json {
    private final StringBuilder sb = new StringBuilder();
    private boolean[] first = new boolean[32];   // per open level: is the next item the first?
    private char[] closer = new char[32];        // per open level: '}' or ']'
    private int depth = 0;
    private boolean afterKey = false;

    public Json obj() { comma(); sb.append('{'); push('}'); return this; }
    public Json arr() { comma(); sb.append('['); push(']'); return this; }

    public Json end() {
        if (depth == 0) throw new IllegalStateException("nothing to close");
        sb.append(closer[--depth]);
        return this;
    }

    public Json key(String k) {
        comma();
        string(k);
        sb.append(':');
        afterKey = true;
        return this;
    }

    public Json val(String s) { comma(); if (s == null) sb.append("null"); else string(s); return this; }
    public Json val(long n) { comma(); sb.append(n); return this; }
    public Json val(int n) { comma(); sb.append(n); return this; }
    public Json val(boolean b) { comma(); sb.append(b); return this; }

    public Json val(double d) {
        comma();
        if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
        else if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
        else sb.append(Math.round(d * 1e4) / 1e4);
        return this;
    }

    public Json nul() { comma(); sb.append("null"); return this; }

    /** Inserts already-encoded JSON. */
    public Json raw(String json) { comma(); sb.append(json); return this; }

    // convenience: key + value
    public Json put(String k, String v) { return key(k).val(v); }
    public Json put(String k, long v) { return key(k).val(v); }
    public Json put(String k, int v) { return key(k).val(v); }
    public Json put(String k, double v) { return key(k).val(v); }
    public Json put(String k, boolean v) { return key(k).val(v); }

    @Override public String toString() { return sb.toString(); }

    private void push(char close) {
        if (depth == first.length) {
            boolean[] b = new boolean[first.length * 2];
            char[] c = new char[first.length * 2];
            for (int i = 0; i < first.length; i++) { b[i] = first[i]; c[i] = closer[i]; }
            first = b;
            closer = c;
        }
        closer[depth] = close;
        first[depth++] = true;
    }

    private void comma() {
        if (afterKey) { afterKey = false; return; }
        if (depth > 0) {
            if (!first[depth - 1]) sb.append(',');
            first[depth - 1] = false;
        }
    }

    private void string(String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
