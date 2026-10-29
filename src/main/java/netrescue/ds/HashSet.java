package netrescue.ds;

/** A set is a hash map whose values we ignore. O(1) expected add / contains / remove. */
public class HashSet<T> {
    private static final Object PRESENT = new Object();
    private final HashMap<T, Object> map = new HashMap<>();

    public boolean add(T v) { return map.put(v, PRESENT) == null; }
    public boolean contains(Object v) { return map.containsKey(v); }
    public boolean remove(Object v) { return map.remove(v) != null; }
    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public void clear() { map.clear(); }
    public DynamicArray<T> items() { return map.keys(); }
}
