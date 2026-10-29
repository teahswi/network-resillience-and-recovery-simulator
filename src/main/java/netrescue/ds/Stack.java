package netrescue.ds;

import java.util.NoSuchElementException;

/**
 * LIFO stack backed by our DynamicArray. The top is the end of the array,
 * so push and pop are O(1) amortised and never shift elements.
 *
 * Used for: transfer rollback (undo log), editor undo/redo, iterative DFS,
 * and matching brackets in the GML parser.
 */
public class Stack<T> {
    private final DynamicArray<T> items = new DynamicArray<>();

    public void push(T v) { items.add(v); }

    public T pop() {
        if (items.isEmpty()) throw new NoSuchElementException("stack underflow");
        return items.removeLast();
    }

    public T peek() {
        if (items.isEmpty()) throw new NoSuchElementException("stack is empty");
        return items.last();
    }

    public boolean isEmpty() { return items.isEmpty(); }
    public int size() { return items.size(); }
    public void clear() { items.clear(); }

    /** Snapshot from top to bottom, for visualisation. */
    public DynamicArray<T> topToBottom() {
        DynamicArray<T> out = new DynamicArray<>(Math.max(1, items.size()));
        for (int i = items.size() - 1; i >= 0; i--) out.add(items.get(i));
        return out;
    }

    @Override
    public String toString() { return "top-> " + topToBottom(); }
}
