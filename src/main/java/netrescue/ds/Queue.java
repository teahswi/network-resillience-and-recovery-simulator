package netrescue.ds;

import java.util.NoSuchElementException;

/**
 * FIFO queue in a circular array (ring buffer).
 *
 * head = index of the front element, size = number of elements.
 * The back slot is (head + size) mod capacity, so both ends move forward and
 * wrap around; nothing is ever shifted.
 *
 *   enqueue ... O(1) amortised (doubles when full)
 *   dequeue ... O(1)
 *
 * Used for: BFS frontier, the incoming-transfer queue.
 */
public class Queue<T> {
    private Object[] buf;
    private int head, size;

    public Queue() { this(8); }
    public Queue(int capacity) { buf = new Object[Math.max(1, capacity)]; }

    public void enqueue(T v) {
        if (size == buf.length) grow();
        buf[(head + size) % buf.length] = v;
        size++;
    }

    @SuppressWarnings("unchecked")
    public T dequeue() {
        if (size == 0) throw new NoSuchElementException("queue is empty");
        T v = (T) buf[head];
        buf[head] = null;
        head = (head + 1) % buf.length;
        size--;
        return v;
    }

    @SuppressWarnings("unchecked")
    public T peek() {
        if (size == 0) throw new NoSuchElementException("queue is empty");
        return (T) buf[head];
    }

    public boolean isEmpty() { return size == 0; }
    public int size() { return size; }
    public int capacity() { return buf.length; }
    public int headIndex() { return head; }

    public void clear() { buf = new Object[buf.length]; head = size = 0; }

    /** Snapshot from front to back, for visualisation. */
    @SuppressWarnings("unchecked")
    public DynamicArray<T> frontToBack() {
        DynamicArray<T> out = new DynamicArray<>(Math.max(1, size));
        for (int i = 0; i < size; i++) out.add((T) buf[(head + i) % buf.length]);
        return out;
    }

    /** Raw slot contents (null = empty slot), to show the ring buffer itself. */
    public Object[] rawSlots() {
        Object[] c = new Object[buf.length];
        for (int i = 0; i < buf.length; i++) c[i] = buf[i];
        return c;
    }

    private void grow() {
        Object[] bigger = new Object[buf.length * 2];
        // unwrap: copy in logical order starting at index 0
        for (int i = 0; i < size; i++) bigger[i] = buf[(head + i) % buf.length];
        buf = bigger;
        head = 0;
    }

    @Override
    public String toString() { return "front-> " + frontToBack(); }
}
