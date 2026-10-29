package netrescue.ds;

import java.util.Comparator;
import java.util.NoSuchElementException;

/**
 * Binary min-heap (Williams, 1964) stored in an array.
 *
 * For the element at index i:
 *   parent = (i - 1) / 2,   left child = 2i + 1,   right child = 2i + 2
 * Heap property: every parent <= its children, so the minimum is at index 0.
 *
 *   insert ...... O(log n)  append at the end, then "sift up"
 *   extractMin .. O(log n)  move last to root, then "sift down"
 *   peek ........ O(1)
 *   heapify ..... O(n)      Floyd's bottom-up build (1964)
 *
 * Used for: the simulator's event clock (earliest event first), lazy Prim,
 * and ranked lists of candidates.
 */
public class MinHeap<T> {
    private final DynamicArray<T> a = new DynamicArray<>();
    private final Comparator<? super T> cmp;

    public MinHeap(Comparator<? super T> cmp) { this.cmp = cmp; }

    public int size() { return a.size(); }
    public boolean isEmpty() { return a.isEmpty(); }

    public void insert(T v) {
        a.add(v);
        siftUp(a.size() - 1);
    }

    public T peek() {
        if (a.isEmpty()) throw new NoSuchElementException("heap is empty");
        return a.get(0);
    }

    public T extractMin() {
        if (a.isEmpty()) throw new NoSuchElementException("heap is empty");
        T min = a.get(0);
        T last = a.removeLast();
        if (!a.isEmpty()) {
            a.set(0, last);
            siftDown(0);
        }
        return min;
    }

    /** Builds a heap from arbitrary items in O(n) by sifting down from the last parent. */
    public static <T> MinHeap<T> heapify(DynamicArray<T> items, Comparator<? super T> cmp) {
        MinHeap<T> h = new MinHeap<>(cmp);
        for (T t : items) h.a.add(t);
        for (int i = h.a.size() / 2 - 1; i >= 0; i--) h.siftDown(i);
        return h;
    }

    public void clear() { a.clear(); }

    /** The raw array in heap order (index 0 = root), for drawing the tree. */
    public DynamicArray<T> array() { return a.copy(); }

    private void siftUp(int i) {
        while (i > 0) {
            int p = (i - 1) / 2;
            if (cmp.compare(a.get(i), a.get(p)) >= 0) break;
            a.swap(i, p);
            i = p;
        }
    }

    private void siftDown(int i) {
        int n = a.size();
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i;
            if (l < n && cmp.compare(a.get(l), a.get(m)) < 0) m = l;
            if (r < n && cmp.compare(a.get(r), a.get(m)) < 0) m = r;
            if (m == i) return;
            a.swap(i, m);
            i = m;
        }
    }

    /** Checks the heap property for every parent/child pair (used by tests). */
    public boolean isValid() {
        for (int i = 1; i < a.size(); i++)
            if (cmp.compare(a.get((i - 1) / 2), a.get(i)) > 0) return false;
        return true;
    }
}
