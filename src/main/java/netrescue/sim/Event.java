package netrescue.sim;

/**
 * Something that happens at a simulated time. Events wait in a min-heap
 * ordered by (time, seq): the earliest event is always processed next, and
 * seq (insertion order) breaks ties so equal-time events stay in FIFO order.
 */
public class Event {
    public enum Type {
        LINK_DOWN, LINK_UP, NODE_DOWN, NODE_UP,   // failures and recoveries
        TRANSFER_ARRIVE,                          // a transfer request arrives
        HOP,                                      // a transfer tries to reserve its next link
        TRANSFER_END,                             // a committed transfer finishes and frees capacity
        RETRY                                     // a waiting transfer tries to find a route again
    }

    public final double time;
    public final long seq;
    public final Type type;
    public final int target;      // link id, router id, or transfer id depending on type
    public final int epoch;       // for HOP/RETRY: the transfer attempt it belongs to (stale ones are ignored)

    public Event(double time, long seq, Type type, int target, int epoch) {
        this.time = time; this.seq = seq; this.type = type; this.target = target; this.epoch = epoch;
    }

    public static int compare(Event a, Event b) {
        int c = Double.compare(a.time, b.time);
        return c != 0 ? c : Long.compare(a.seq, b.seq);
    }
}
