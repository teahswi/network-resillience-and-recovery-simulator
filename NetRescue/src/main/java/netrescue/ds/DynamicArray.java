package netrescue.ds;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Resizable array (our own version of ArrayList).
 *
 * Idea: keep elements in a fixed-size Object[]; when it is full, allocate an
 * array twice as large and copy. Each doubling costs O(n) but happens only
 * after n cheap appends, so append is O(1) amortised.
 *
 *   get / set ............ O(1)
 *   add (append) ......... O(1) amortised
 *   removeLast ........... O(1)
 *   removeAt / insertAt .. O(n)   (elements shift)
 */
public class DynamicArray<T> implements Iterable<T> {
    private Object[] data;
    private int size;

    public DynamicArray() { this(8); }

    public DynamicArray(int initialCapacity) {
        data = new Object[Math.max(1, initialCapacity)];
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public int capacity() { return data.length; }

    public void add(T value) {
        if (size == data.length) grow();
        data[size++] = value;
    }

    public void insertAt(int index, T value) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        if (size == data.length) grow();
        for (int i = size; i > index; i--) data[i] = data[i - 1];
        data[index] = value;
        size++;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        check(index);
        return (T) data[index];
    }

    public void set(int index, T value) {
        check(index);
        data[index] = value;
    }

    @SuppressWarnings("unchecked")
    public T removeLast() {
        if (size == 0) throw new NoSuchElementException("array is empty");
        T v = (T) data[--size];
        data[size] = null;               // let the garbage collector reclaim it
        return v;
    }

    @SuppressWarnings("unchecked")
    public T removeAt(int index) {
        check(index);
        T v = (T) data[index];
        for (int i = index; i < size - 1; i++) data[i] = data[i + 1];
        data[--size] = null;
        return v;
    }

    @SuppressWarnings("unchecked")
    public T last() {
        if (size == 0) throw new NoSuchElementException("array is empty");
        return (T) data[size - 1];
    }

    public int indexOf(Object value) {
        for (int i = 0; i < size; i++) if (eq(data[i], value)) return i;
        return -1;
    }

    public boolean contains(Object value) { return indexOf(value) >= 0; }

    public void swap(int i, int j) {
        check(i); check(j);
        Object t = data[i]; data[i] = data[j]; data[j] = t;
    }

    public void clear() {
        for (int i = 0; i < size; i++) data[i] = null;
        size = 0;
    }

    public void reverse() {
        for (int i = 0, j = size - 1; i < j; i++, j--) swap(i, j);
    }

    /** Stable O(n log n) sort using our own merge sort. */
    public void sort(Comparator<? super T> cmp) { Sorting.mergeSort(this, cmp); }

    public DynamicArray<T> copy() {
        DynamicArray<T> c = new DynamicArray<>(Math.max(1, size));
        for (int i = 0; i < size; i++) c.add(get(i));
        return c;
    }

    private void grow() {
        Object[] bigger = new Object[data.length * 2];
        for (int i = 0; i < size; i++) bigger[i] = data[i];
        data = bigger;
    }

    private void check(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("index " + index + ", size " + size);
    }

    private static boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            int i = 0;
            public boolean hasNext() { return i < size; }
            @SuppressWarnings("unchecked")
            public T next() {
                if (i >= size) throw new NoSuchElementException();
                return (T) data[i++];
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) { if (i > 0) sb.append(", "); sb.append(data[i]); }
        return sb.append("]").toString();
    }
}
