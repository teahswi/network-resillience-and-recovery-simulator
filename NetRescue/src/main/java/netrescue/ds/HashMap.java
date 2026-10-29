package netrescue.ds;

/**
 * Hash table with separate chaining (Luhn, 1953).
 *
 * buckets[i] is the head of a small linked list of entries whose key hashes to i.
 *   index = spread(key.hashCode()) & (capacity - 1)      (capacity is a power of 2)
 * When size / capacity exceeds the load factor (0.75), the table doubles and
 * every entry is re-inserted ("rehashing"). With a good hash, chains stay
 * short on average, so:
 *
 *   put / get / remove ... O(1) expected, O(n) worst case (all keys collide)
 *
 * Used for: router name -> router id, transfer id -> transfer, the LRU index,
 * and as the base of HashSet.
 */
public class HashMap<K, V> {
    static final class Entry<K, V> {
        final K key; V value; Entry<K, V> next; final int hash;
        Entry(K k, V v, int h, Entry<K, V> n) { key = k; value = v; hash = h; next = n; }
    }

    private static final double LOAD_FACTOR = 0.75;
    private Entry<K, V>[] buckets;
    private int size;
    private int resizes;

    public HashMap() { this(16); }

    @SuppressWarnings("unchecked")
    public HashMap(int capacity) {
        int c = 1;
        while (c < capacity) c <<= 1;
        buckets = (Entry<K, V>[]) new Entry<?, ?>[c];
    }

    /** Mixes high bits into low bits so "& (capacity-1)" uses all of the hash. */
    static int spread(Object key) {
        int h = key == null ? 0 : key.hashCode();
        return h ^ (h >>> 16);
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public int capacity() { return buckets.length; }
    public int resizeCount() { return resizes; }
    public double loadFactor() { return (double) size / buckets.length; }

    public V put(K key, V value) {
        int h = spread(key);
        int i = h & (buckets.length - 1);
        for (Entry<K, V> e = buckets[i]; e != null; e = e.next) {
            if (e.hash == h && eq(e.key, key)) { V old = e.value; e.value = value; return old; }
        }
        buckets[i] = new Entry<>(key, value, h, buckets[i]);   // insert at chain head
        if (++size > LOAD_FACTOR * buckets.length) resize();
        return null;
    }

    public V get(Object key) {
        Entry<K, V> e = find(key);
        return e == null ? null : e.value;
    }

    public V getOrDefault(Object key, V def) {
        Entry<K, V> e = find(key);
        return e == null ? def : e.value;
    }

    public boolean containsKey(Object key) { return find(key) != null; }

    public V remove(Object key) {
        int h = spread(key);
        int i = h & (buckets.length - 1);
        Entry<K, V> prev = null;
        for (Entry<K, V> e = buckets[i]; e != null; prev = e, e = e.next) {
            if (e.hash == h && eq(e.key, key)) {
                if (prev == null) buckets[i] = e.next; else prev.next = e.next;
                size--;
                return e.value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void clear() { buckets = (Entry<K, V>[]) new Entry<?, ?>[16]; size = 0; }

    public DynamicArray<K> keys() {
        DynamicArray<K> out = new DynamicArray<>(Math.max(1, size));
        for (Entry<K, V> b : buckets) for (Entry<K, V> e = b; e != null; e = e.next) out.add(e.key);
        return out;
    }

    public DynamicArray<V> values() {
        DynamicArray<V> out = new DynamicArray<>(Math.max(1, size));
        for (Entry<K, V> b : buckets) for (Entry<K, V> e = b; e != null; e = e.next) out.add(e.value);
        return out;
    }

    /** Length of each chain, for the "bucket view" in the DS lab and for statistics. */
    public int[] chainLengths() {
        int[] out = new int[buckets.length];
        for (int i = 0; i < buckets.length; i++) {
            int c = 0;
            for (Entry<K, V> e = buckets[i]; e != null; e = e.next) c++;
            out[i] = c;
        }
        return out;
    }

    /** Keys stored in bucket i, in chain order. */
    public DynamicArray<K> bucketKeys(int i) {
        DynamicArray<K> out = new DynamicArray<>();
        for (Entry<K, V> e = buckets[i]; e != null; e = e.next) out.add(e.key);
        return out;
    }

    public int longestChain() {
        int m = 0;
        for (int c : chainLengths()) m = Math.max(m, c);
        return m;
    }

    private Entry<K, V> find(Object key) {
        int h = spread(key);
        for (Entry<K, V> e = buckets[h & (buckets.length - 1)]; e != null; e = e.next)
            if (e.hash == h && eq(e.key, key)) return e;
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Entry<K, V>[] old = buckets;
        buckets = (Entry<K, V>[]) new Entry<?, ?>[old.length * 2];
        resizes++;
        for (Entry<K, V> b : old) {
            Entry<K, V> e = b;
            while (e != null) {
                Entry<K, V> next = e.next;
                int i = e.hash & (buckets.length - 1);
                e.next = buckets[i];
                buckets[i] = e;
                e = next;
            }
        }
    }

    private static boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }
}
