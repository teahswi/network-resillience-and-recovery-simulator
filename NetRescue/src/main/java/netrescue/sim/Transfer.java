package netrescue.sim;

import netrescue.algo.PathResult;
import netrescue.ds.DynamicArray;
import netrescue.ds.Stack;

/**
 * A request to move `amount` bandwidth units from src to dst.
 *
 * Life cycle:
 *   QUEUED  -> waiting in the admission queue
 *   SETUP   -> walking its route hop by hop, reserving each link
 *   ACTIVE  -> every link reserved (committed); data flows for holdTime
 *   DONE    -> finished, capacity released
 *   WAITING -> no route with enough free capacity right now; retry later
 *   FAILED  -> unreachable, or gave up after too many retries
 *
 * The UNDO STACK holds the ids of links reserved so far. If any hop fails,
 * the stack is popped and each reservation released in reverse order:
 * the last thing done is the first thing undone. That is the rollback.
 */
public class Transfer {
    public enum State { QUEUED, SETUP, ACTIVE, WAITING, DONE, FAILED }

    public final int id, src, dst, amount, priority;
    public final double createdAt, holdTime;
    public State state = State.QUEUED;
    public PathResult route;
    public int hopIndex;
    public final Stack<Integer> undo = new Stack<>();
    public int retries, rollbacks, reroutes;
    public int epoch;                 // bumped on every new attempt, so stale HOP events are ignored
    public double committedAt = -1, finishedAt = -1;
    public String reason = "";
    public boolean fromCache;
    public final DynamicArray<String> history = new DynamicArray<>();

    public Transfer(int id, int src, int dst, int amount, int priority, double createdAt, double holdTime) {
        this.id = id; this.src = src; this.dst = dst; this.amount = amount;
        this.priority = priority; this.createdAt = createdAt; this.holdTime = holdTime;
    }

    public boolean finished() { return state == State.DONE || state == State.FAILED; }

    /** Comparator for the priority mode: higher priority first, then earlier arrival. */
    public static int byPriority(Transfer a, Transfer b) {
        if (a.priority != b.priority) return Integer.compare(b.priority, a.priority);
        return Integer.compare(a.id, b.id);
    }
}
