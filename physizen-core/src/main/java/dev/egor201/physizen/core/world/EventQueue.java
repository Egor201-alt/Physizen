package dev.egor201.physizen.core.world;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

final class EventQueue {

    private final ConcurrentLinkedQueue<PhysEvent> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger backlog = new AtomicInteger();
    private final int limit;

    EventQueue(int limit) {
        this.limit = limit;
    }

    void offer(PhysEvent event) {
        if (backlog.get() >= limit) {
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

    void clear() {
        while (events.poll() != null) {
            backlog.decrementAndGet();
        }
    }
}
