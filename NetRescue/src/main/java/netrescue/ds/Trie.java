package netrescue.ds;

/**
 * Prefix tree (trie; de la Briandais 1959, named by Fredkin 1960).
 *
 * Each node represents a prefix; following the edge labelled c appends c.
 * A word is stored by walking/creating one node per character and marking
 * the last node as the end of a word.
 *
 *   insert / contains .... O(L)       L = word length, independent of how many words
 *   prefix search ........ O(L + k)   k = number of results visited
 *
 * Children are kept in our own HashMap so any character (including spaces,
 * digits and non-English letters in real router names) works.
 * Search is case-insensitive: words are stored lower-cased, and the original
 * spelling is kept at the end node.
 *
 * Used for: router name autocomplete.
 */
public class Trie {
    private static final class Node {
        final HashMap<Character, Node> children = new HashMap<>(4);
        String word;        // original spelling if a word ends here, else null
        int passCount;      // how many words pass through this node
    }

    private final Node root = new Node();
    private int words;
    private int nodes = 1;

    public int size() { return words; }
    public int nodeCount() { return nodes; }

    public void insert(String word) {
        if (contains(word)) return;
        Node cur = root;
        cur.passCount++;
        String key = word.toLowerCase();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            Node next = cur.children.get(c);
            if (next == null) { next = new Node(); cur.children.put(c, next); nodes++; }
            cur = next;
            cur.passCount++;
        }
        cur.word = word;
        words++;
    }

    public boolean contains(String word) {
        Node n = walk(word.toLowerCase());
        return n != null && n.word != null;
    }

    /** Removes a word; prunes nodes no other word uses. */
    public boolean remove(String word) {
        if (!contains(word)) return false;
        String key = word.toLowerCase();
        Node cur = root;
        cur.passCount--;
        for (int i = 0; i < key.length(); i++) {
            Node next = cur.children.get(key.charAt(i));
            if (--next.passCount == 0) {
                cur.children.remove(key.charAt(i));
                nodes -= countNodes(next);
                words--;
                return true;
            }
            cur = next;
        }
        cur.word = null;
        words--;
        return true;
    }

    /** Up to `limit` words starting with prefix, in alphabetical order (depth-first). */
    public DynamicArray<String> startsWith(String prefix, int limit) {
        DynamicArray<String> out = new DynamicArray<>();
        Node n = walk(prefix.toLowerCase());
        if (n != null) collect(n, out, limit);
        return out;
    }

    public int countWithPrefix(String prefix) {
        Node n = walk(prefix.toLowerCase());
        return n == null ? 0 : n.passCount;
    }

    private Node walk(String key) {
        Node cur = root;
        for (int i = 0; i < key.length() && cur != null; i++) cur = cur.children.get(key.charAt(i));
        return cur;
    }

    private void collect(Node n, DynamicArray<String> out, int limit) {
        if (out.size() >= limit) return;
        if (n.word != null) out.add(n.word);
        DynamicArray<Character> keys = n.children.keys();
        keys.sort((a, b) -> Character.compare(a, b));
        for (Character c : keys) {
            if (out.size() >= limit) return;
            collect(n.children.get(c), out, limit);
        }
    }

    private int countNodes(Node n) {
        int c = 1;
        for (Node ch : n.children.values()) c += countNodes(ch);
        return c;
    }

    /** Edges of the trie as (parentId, childId, char) plus end-of-word flags, for drawing in the DS lab. */
    public DynamicArray<String[]> edgesForDrawing(int maxNodes) {
        DynamicArray<String[]> out = new DynamicArray<>();
        Queue<Object[]> q = new Queue<>();
        q.enqueue(new Object[]{root, 0});
        int[] nextId = {1};
        out.add(new String[]{"-1", "0", "", "0"});
        while (!q.isEmpty() && nextId[0] < maxNodes) {
            Object[] item = q.dequeue();
            Node n = (Node) item[0];
            int id = (Integer) item[1];
            DynamicArray<Character> keys = n.children.keys();
            keys.sort((a, b) -> Character.compare(a, b));
            for (Character c : keys) {
                if (nextId[0] >= maxNodes) break;
                Node ch = n.children.get(c);
                int cid = nextId[0]++;
                out.add(new String[]{String.valueOf(id), String.valueOf(cid), String.valueOf(c), ch.word != null ? "1" : "0"});
                q.enqueue(new Object[]{ch, cid});
            }
        }
        return out;
    }
}
