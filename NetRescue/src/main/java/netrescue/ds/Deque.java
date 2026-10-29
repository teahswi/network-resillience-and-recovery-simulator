package netrescue.ds;

import java.util.NoSuchElementException;

/**
 * Double-ended queue in a circular array: add/remove at both ends in O(1).
 *
 * Used for: the sliding window of recent transfer latencies in the simulator
 * metrics (add at the back, drop from the front once the window is full),
 * and for the 0-1 BFS used in fewest-failed-links routing.
 */
public class Deque<T> {
    private Object[] buf = new Object[8];
    private int head, size;

    public void addFirst(T v) {
        if (size == buf.length) grow();
        head = (head - 1 + buf.length) % buf.length;
        buf[head] = v;
        size++;
    }

    public void addLast(T v) {
        if (size == buf.length) grow();
        buf[(head + size) % buf.length] = v;
        size++;
    }

    @SuppressWarnings("unchecked")
    public T pollFirst() {
        if (size == 0) throw new NoSuchElementException("deque is empty");
        T v = (T) buf[head];
        buf[head] = null;
        head = (head + 1) % buf.length;
        size--;
        return v;
    }

    @SuppressWarnings("unchecked")
    public T pollLast() {
        if (size == 0) throw new NoSuchElementException("deque is empty");
        int idx = (head + size - 1) % buf.length;
        T v = (T) buf[idx];
        buf[idx] = null;
        size--;
        return v;
    }

    @SuppressWarnings("unchecked")
    public T peekFirst() { if (size == 0) throw new NoSuchElementException(); return (T) buf[head]; }

    @SuppressWarnings("unchecked")
    public T peekLast() { if (size == 0) throw new NoSuchElementException(); return (T) buf[(head + size - 1) % buf.length]; }

    @SuppressWarnings("unchecked")
    public T get(int i) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException();
        return (T) buf[(head + i) % buf.length];
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    private void grow() {
        Object[] b = new Object[buf.length * 2];
        for (int i = 0; i < size; i++) b[i] = buf[(head + i) % buf.length];
        buf = b;
        head = 0;
    }
}
