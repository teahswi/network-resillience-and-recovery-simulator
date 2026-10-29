package netrescue.ds;

import java.util.function.BiPredicate;

/**
 * Least-Recently-Used cache.
 *
 * Two structures working together:
 *  - a doubly linked list ordered by recency (front = most recent)
 *  - a hash map from key to that key's list node
 * get(k): hash lookup O(1), then move the node to the front O(1).
 * put(k): insert at the front; if over capacity, drop the node at the back
 *         (the least recently used) and remove its key from the map.
 *
 * Used for: caching computed routes ("A->B" -> path). When a link fails, every
 * cached route that uses it is invalidated.
 */
public class LRUCache<K, V> {
    private static final class Item<K, V> { final K key; V value; Item(K k, V v) { key = k; value = v; } }

    private final int capacity;
    private final HashMap<K, DoublyLinkedList.Node<Item<K, V>>> index = new HashMap<>();
    private final DoublyLinkedList<Item<K, V>> order = new DoublyLinkedList<>();
    private long hits, misses, evictions, invalidations;

    public LRUCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
    }

    public V get(K key) {
        DoublyLinkedList.Node<Item<K, V>> n = index.get(key);
        if (n == null) { misses++; return null; }
        hits++;
        order.moveToFront(n);
        return n.value.value;
    }

    public void put(K key, V value) {
        DoublyLinkedList.Node<Item<K, V>> n = index.get(key);
        if (n != null) {
            n.value.value = value;
            order.moveToFront(n);
            return;
        }
        if (order.size() == capacity) {
            Item<K, V> lru = order.removeLast();
            index.remove(lru.key);
            evictions++;
        }
        index.put(key, order.addFirst(new Item<>(key, value)));
    }

    public boolean remove(K key) {
        DoublyLinkedList.Node<Item<K, V>> n = index.remove(key);
        if (n == null) return false;
        order.unlink(n);
        return true;
    }

    /** Removes every entry for which test(key, value) is true. O(n). Returns how many were removed. */
    public int invalidateIf(BiPredicate<K, V> test) {
        DynamicArray<K> doomed = new DynamicArray<>();
        for (Item<K, V> it : order) if (test.test(it.key, it.value)) doomed.add(it.key);
        for (K k : doomed) remove(k);
        invalidations += doomed.size();
        return doomed.size();
    }

    public void clear() { index.clear(); order.clear(); }

    public int size() { return order.size(); }
    public int capacity() { return capacity; }
    public long hits() { return hits; }
    public long misses() { return misses; }
    public long evictions() { return evictions; }
    public long invalidations() { return invalidations; }
    public double hitRate() { long t = hits + misses; return t == 0 ? 0 : (double) hits / t; }
    public void resetStats() { hits = misses = evictions = invalidations = 0; }

    /** Keys from most to least recently used. */
    public DynamicArray<K> keysByRecency() {
        DynamicArray<K> out = new DynamicArray<>();
        for (Item<K, V> it : order) out.add(it.key);
        return out;
    }
}
