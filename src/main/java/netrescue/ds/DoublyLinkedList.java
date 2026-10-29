package netrescue.ds;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Doubly linked list with sentinel head/tail nodes.
 *
 * The sentinels mean we never special-case "empty list" or "first node":
 * every real node always has a non-null prev and next.
 *
 * Nodes are exposed (Node) so callers that keep a reference can unlink a node
 * in O(1); the LRU cache depends on this.
 *
 *   addFirst / addLast / removeFirst / removeLast / unlink(node) / moveToFront ... O(1)
 */
public class DoublyLinkedList<T> implements Iterable<T> {
    public static final class Node<T> {
        public T value;
        Node<T> prev, next;
        Node(T v) { value = v; }
    }

    private final Node<T> head = new Node<>(null);   // sentinel before first
    private final Node<T> tail = new Node<>(null);   // sentinel after last
    private int size;

    public DoublyLinkedList() { head.next = tail; tail.prev = head; }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    public Node<T> addFirst(T v) { return insertAfter(head, v); }
    public Node<T> addLast(T v) { return insertAfter(tail.prev, v); }

    private Node<T> insertAfter(Node<T> p, T v) {
        Node<T> n = new Node<>(v);
        n.prev = p; n.next = p.next;
        p.next.prev = n; p.next = n;
        size++;
        return n;
    }

    public void unlink(Node<T> n) {
        n.prev.next = n.next;
        n.next.prev = n.prev;
        n.prev = n.next = null;
        size--;
    }

    public void moveToFront(Node<T> n) {
        // unlink without changing size, then splice after head
        n.prev.next = n.next; n.next.prev = n.prev;
        n.prev = head; n.next = head.next;
        head.next.prev = n; head.next = n;
    }

    public T first() { if (size == 0) throw new NoSuchElementException(); return head.next.value; }
    public T last() { if (size == 0) throw new NoSuchElementException(); return tail.prev.value; }
    public Node<T> lastNode() { return size == 0 ? null : tail.prev; }

    public T removeFirst() {
        if (size == 0) throw new NoSuchElementException("list is empty");
        Node<T> n = head.next; unlink(n); return n.value;
    }

    public T removeLast() {
        if (size == 0) throw new NoSuchElementException("list is empty");
        Node<T> n = tail.prev; unlink(n); return n.value;
    }

    /** Element at position i, walking from whichever end is closer. O(min(i, n-i)). */
    public T get(int i) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException("index " + i + ", size " + size);
        Node<T> c;
        if (i < size / 2) { c = head.next; for (int k = 0; k < i; k++) c = c.next; }
        else { c = tail.prev; for (int k = size - 1; k > i; k--) c = c.prev; }
        return c.value;
    }

    public void clear() { head.next = tail; tail.prev = head; size = 0; }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            Node<T> cur = head.next;
            public boolean hasNext() { return cur != tail; }
            public T next() {
                if (cur == tail) throw new NoSuchElementException();
                T v = cur.value; cur = cur.next; return v;
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (Node<T> c = head.next; c != tail; c = c.next) { sb.append(c.value); if (c.next != tail) sb.append(" <-> "); }
        return sb.append("]").toString();
    }
}
