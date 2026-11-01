package dev.egor201.physizen.core.world;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class EventQueue {

    private final ConcurrentLinkedQueue<PhysEvent> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger backlog = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong droppedReported = new AtomicLong();
    private final int limit;

    EventQueue(int limit) {
        this.limit = limit;
    }

    void offer(PhysEvent event) {
        if (backlog.get() >= limit) {
            dropped.incrementAndGet();
            return;
        }
        backlog.incrementAndGet();
        events.add(event);
    }

    PhysEvent poll() {
        PhysEvent e = events.poll();
        if (e != null) {
            backlog.decrementAndGet();
        }
        return e;
    }

    int pending() {
        return backlog.get();
    }

    long droppedTotal() {
        return dropped.get();
    }

    long takeNewDropped() {
        long total = dropped.get();
        long previous = droppedReported.getAndSet(total);
        return total - previous;
    }

    void clear() {
        while (events.poll() != null) {
            backlog.decrementAndGet();
        }
    }
}
