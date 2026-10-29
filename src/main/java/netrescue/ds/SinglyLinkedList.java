package netrescue.ds;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Singly linked list with head and tail pointers.
 * Used for adjacency lists (each router's list of links) and path hops.
 *
 *   addFirst / addLast / removeFirst ... O(1)
 *   remove(value) / contains ........... O(n)
 */
public class SinglyLinkedList<T> implements Iterable<T> {
    private static final class Node<T> {
        T value; Node<T> next;
        Node(T v) { value = v; }
    }

    private Node<T> head, tail;
    private int size;

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    public void addFirst(T v) {
        Node<T> n = new Node<>(v);
        n.next = head;
        head = n;
        if (tail == null) tail = n;
        size++;
    }

    public void addLast(T v) {
        Node<T> n = new Node<>(v);
        if (tail == null) head = tail = n;
        else { tail.next = n; tail = n; }
        size++;
    }

    public T first() {
        if (head == null) throw new NoSuchElementException("list is empty");
        return head.value;
    }

    public T removeFirst() {
        if (head == null) throw new NoSuchElementException("list is empty");
        T v = head.value;
        head = head.next;
        if (head == null) tail = null;
        size--;
        return v;
    }

    /** Removes the first node whose value equals v. Returns true if found. */
    public boolean remove(Object v) {
        Node<T> prev = null, cur = head;
        while (cur != null) {
            if (cur.value == null ? v == null : cur.value.equals(v)) {
                if (prev == null) head = cur.next; else prev.next = cur.next;
                if (cur == tail) tail = prev;
                size--;
                return true;
            }
            prev = cur;
            cur = cur.next;
        }
        return false;
    }

    public boolean contains(Object v) {
        for (Node<T> c = head; c != null; c = c.next)
            if (c.value == null ? v == null : c.value.equals(v)) return true;
        return false;
    }

    /** Reverses the list in place by flipping every next pointer. O(n). */
    public void reverse() {
        Node<T> prev = null, cur = head;
        tail = head;
        while (cur != null) {
            Node<T> next = cur.next;
            cur.next = prev;
            prev = cur;
            cur = next;
        }
        head = prev;
    }

    public void clear() { head = tail = null; size = 0; }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            Node<T> cur = head;
            public boolean hasNext() { return cur != null; }
            public T next() {
                if (cur == null) throw new NoSuchElementException();
                T v = cur.value; cur = cur.next; return v;
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (Node<T> c = head; c != null; c = c.next) { sb.append(c.value); if (c.next != null) sb.append(" -> "); }
        return sb.append("]").toString();
    }
}
