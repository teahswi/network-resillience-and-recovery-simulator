package netrescue.ds;

import java.util.NoSuchElementException;

/**
 * Indexed binary min-heap over integer keys 0..n-1 with double priorities.
 *
 * A plain heap cannot find "where is key k?" quickly, so it cannot lower a
 * key's priority in place. We add a position array:
 *   heap[i] = which key sits at heap slot i
 *   pos[k]  = which heap slot holds key k (-1 if absent)
 * and keep both in sync on every swap. Now decreaseKey is: look up pos[k],
 * lower its priority, sift up -- O(log n).
 *
 * This is exactly what Dijkstra (1959) needs: each router is a key, its
 * tentative distance is its priority.
 *
 *   insert / decreaseKey / extractMin ... O(log n)
 *   contains / priorityOf ............... O(1)
 */
public class IndexedMinHeap {
    private final int[] heap;     // heap slot -> key
    private final int[] pos;      // key -> heap slot, -1 if not in heap
    private final double[] prio;  // key -> priority
    private int size;

    public IndexedMinHeap(int capacity) {
        heap = new int[capacity];
        pos = new int[capacity];
        prio = new double[capacity];
        for (int i = 0; i < capacity; i++) pos[i] = -1;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public boolean contains(int key) { return pos[key] != -1; }
    public double priorityOf(int key) { return prio[key]; }

    public void insert(int key, double priority) {
        if (contains(key)) throw new IllegalArgumentException("key " + key + " already in heap");
        prio[key] = priority;
        heap[size] = key;
        pos[key] = size;
        siftUp(size++);
    }

    public void decreaseKey(int key, double priority) {
        if (!contains(key)) throw new NoSuchElementException("key " + key + " not in heap");
        if (priority > prio[key]) throw new IllegalArgumentException("new priority is larger");
        prio[key] = priority;
        siftUp(pos[key]);
    }

    /** Insert if absent, otherwise decrease if smaller. Returns true if anything changed. */
    public boolean insertOrDecrease(int key, double priority) {
        if (!contains(key)) { insert(key, priority); return true; }
        if (priority < prio[key]) { decreaseKey(key, priority); return true; }
        return false;
    }

    public int peekKey() {
        if (size == 0) throw new NoSuchElementException("heap is empty");
        return heap[0];
    }

    public int extractMin() {
        if (size == 0) throw new NoSuchElementException("heap is empty");
        int min = heap[0];
        swap(0, --size);
        pos[min] = -1;
        siftDown(0);
        return min;
    }

    /** Keys in heap-array order, for drawing. */
    public int[] keysInHeapOrder() {
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = heap[i];
        return out;
    }

    private void siftUp(int i) {
        while (i > 0) {
            int p = (i - 1) / 2;
            if (prio[heap[i]] >= prio[heap[p]]) break;
            swap(i, p);
            i = p;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i;
            if (l < size && prio[heap[l]] < prio[heap[m]]) m = l;
            if (r < size && prio[heap[r]] < prio[heap[m]]) m = r;
            if (m == i) return;
            swap(i, m);
            i = m;
        }
    }

    private void swap(int i, int j) {
        int t = heap[i]; heap[i] = heap[j]; heap[j] = t;
        pos[heap[i]] = i;
        pos[heap[j]] = j;
    }
}
