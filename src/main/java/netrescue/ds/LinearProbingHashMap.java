package netrescue.ds;

/**
 * Hash table with open addressing and linear probing (Peterson, 1957; analysed
 * by Knuth, 1963).
 *
 * All entries live in one array. On a collision we try the next slot,
 * then the next, wrapping around, until we find the key or an empty slot.
 * Deleting leaves a TOMBSTONE so later searches don't stop early.
 *
 * Expected probes for a successful search at load factor a:
 *   (1/2) * (1 + 1/(1 - a))
 * which blows up as a -> 1, so we resize at a = 0.5.
 *
 * Exists only for the benchmark comparison against separate chaining.
 */
public class LinearProbingHashMap<K, V> {
    private static final Object TOMBSTONE = new Object();
    private Object[] keys;
    private Object[] vals;
    private int size, used;            // used = live entries + tombstones
    private long probes;               // total slots inspected (for benchmarks)

    public LinearProbingHashMap() { this(16); }

    public LinearProbingHashMap(int capacity) {
        int c = 1;
        while (c < capacity) c <<= 1;
        keys = new Object[c];
        vals = new Object[c];
    }

    public int size() { return size; }
    public long probes() { return probes; }

    public void put(K key, V value) {
        if (key == null) throw new IllegalArgumentException("null keys not supported");
        if (used + 1 > keys.length / 2) resize(keys.length * 2);
        int mask = keys.length - 1;
        int i = HashMap.spread(key) & mask;
        int firstTomb = -1;
        while (keys[i] != null) {
            probes++;
            if (keys[i] == TOMBSTONE) { if (firstTomb < 0) firstTomb = i; }
            else if (keys[i].equals(key)) { vals[i] = value; return; }
            i = (i + 1) & mask;
        }
        probes++;
        if (firstTomb >= 0) i = firstTomb; else used++;
        keys[i] = key;
        vals[i] = value;
        size++;
    }

    @SuppressWarnings("unchecked")
    public V get(Object key) {
        int mask = keys.length - 1;
        int i = HashMap.spread(key) & mask;
        while (keys[i] != null) {
            probes++;
            if (keys[i] != TOMBSTONE && keys[i].equals(key)) return (V) vals[i];
            i = (i + 1) & mask;
        }
        probes++;
        return null;
    }

    public boolean containsKey(Object key) { return get(key) != null; }

    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        int mask = keys.length - 1;
        int i = HashMap.spread(key) & mask;
        while (keys[i] != null) {
            if (keys[i] != TOMBSTONE && keys[i].equals(key)) {
                V v = (V) vals[i];
                keys[i] = TOMBSTONE;
                vals[i] = null;
                size--;
                return v;
            }
            i = (i + 1) & mask;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize(int cap) {
        Object[] ok = keys, ov = vals;
        keys = new Object[cap];
        vals = new Object[cap];
        size = used = 0;
        for (int i = 0; i < ok.length; i++)
            if (ok[i] != null && ok[i] != TOMBSTONE) put((K) ok[i], (V) ov[i]);
    }
}
