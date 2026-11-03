package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.ContactManifold;
import com.github.stephengold.joltjni.CustomContactListener;
import com.github.stephengold.joltjni.SubShapeIdPair;
import com.github.stephengold.joltjni.Vec3;
import dev.egor201.physizen.core.engine.EngineConfig;
import dev.egor201.physizen.core.model.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class ContactTracker extends CustomContactListener {

    private static final class Pair {

        final PhysBody first;
        final PhysBody second;
        final PhysBody sensor;
        final PhysBody occupant;
        final AtomicInteger count = new AtomicInteger();

        Pair(PhysBody first, PhysBody second, PhysBody sensor, PhysBody occupant) {
            this.first = first;
            this.second = second;
            this.sensor = sensor;
            this.occupant = occupant;
        }
    }

    private final PhysWorld world;
    private final EngineConfig config;
    private final EventQueue events;
    private static final int DERIVED_COOLDOWN_STEPS = 12;
    private final ConcurrentHashMap<Long, Pair> pairs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastDerived = new ConcurrentHashMap<>();

    ContactTracker(PhysWorld world, EngineConfig config, EventQueue events) {
        this.world = world;
        this.config = config;
        this.events = events;
    }

    private static long key(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    @Override
    public void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        try {
            Body b1 = new Body(body1Va);
            Body b2 = new Body(body2Va);
            PhysBody p1 = world.byJoltId.get(b1.getId());
            PhysBody p2 = world.byJoltId.get(b2.getId());
            if (p1 == null || p2 == null) {
                return;
            }
            ContactManifold manifold = new ContactManifold(manifoldVa);
            Vec3 n = manifold.getWorldSpaceNormal();
            double[] n12 = ContactGeometry.unit(n.getX(), n.getY(), n.getZ(), null);
            double depth = manifold.getPenetrationDepth();
            Vec3 v1 = b1.getLinearVelocity();
            Vec3 v2 = b2.getLinearVelocity();
            double rx = v1.getX() - v2.getX();
            double ry = v1.getY() - v2.getY();
            double rz = v1.getZ() - v2.getZ();
            double speed = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (p1.def.sensor != p2.def.sensor) {
                sensorAdded(p1.def.sensor ? p1 : p2, p1.def.sensor ? p2 : p1, key(b1.getId(), b2.getId()), speed, depth);
                return;
            }
            derived(b1, b2, p1, p2, n12, depth, speed);
            if (!p1.def.notify && !p2.def.notify) {
                return;
            }
            Pair pair = pairs.computeIfAbsent(key(b1.getId(), b2.getId()), k -> new Pair(p1, p2, null, null));
            if (pair.count.incrementAndGet() != 1 || speed < config.contactMinSpeed) {
                return;
            }
            double[] exact = exactPoint(b1, b2, p1, p2);
            emitContact(PhysEvent.Kind.COLLIDE, p1, p2, n12, depth, speed, exact);
            emitContact(PhysEvent.Kind.COLLIDE, p2, p1, n12 == null ? null : ContactGeometry.negate(n12), depth, speed, exact);
        }
        catch (Throwable ignored) {
        }
    }

    @Override
    public void onContactPersisted(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        try {
            long step = world.stepCount();
            Body b1 = new Body(body1Va);
            Body b2 = new Body(body2Va);
            PhysBody p1 = world.byJoltId.get(b1.getId());
            PhysBody p2 = world.byJoltId.get(b2.getId());
            if (p1 == null || p2 == null) {
                return;
            }
            if (p1.def.sensor != p2.def.sensor) {
                int interval = config.sensorStayIntervalSteps;
                PhysBody sensor = p1.def.sensor ? p1 : p2;
                PhysBody occupant = p1.def.sensor ? p2 : p1;
                if (interval > 0 && step % interval == 0 && sensor.def.notify && !occupant.def.internal) {
                    events.offer(new PhysEvent(PhysEvent.Kind.SENSOR_STAY, world, sensor, occupant, midpoint(sensor, occupant), null, 0));
                }
                return;
            }
            int interval = config.contactPersistIntervalSteps;
            if (interval <= 0 || step % interval != 0 || (!p1.def.notifyPersist && !p2.def.notifyPersist)) {
                return;
            }
            ContactManifold manifold = new ContactManifold(manifoldVa);
            Vec3 n = manifold.getWorldSpaceNormal();
            double[] n12 = ContactGeometry.unit(n.getX(), n.getY(), n.getZ(), null);
            double depth = manifold.getPenetrationDepth();
            Vec3 v1 = b1.getLinearVelocity();
            Vec3 v2 = b2.getLinearVelocity();
            double speed = Math.sqrt(Math.pow(v1.getX() - v2.getX(), 2) + Math.pow(v1.getY() - v2.getY(), 2) + Math.pow(v1.getZ() - v2.getZ(), 2));
            double[] exact = exactPoint(b1, b2, p1, p2);
            if (p1.def.notifyPersist) {
                emitContact(PhysEvent.Kind.CONTACT_PERSISTED, p1, p2, n12, depth, speed, exact);
            }
            if (p2.def.notifyPersist) {
                emitContact(PhysEvent.Kind.CONTACT_PERSISTED, p2, p1, n12 == null ? null : ContactGeometry.negate(n12), depth, speed, exact);
            }
        }
        catch (Throwable ignored) {
        }
    }

    @Override
    public void onContactRemoved(long pairVa) {
        try {
            SubShapeIdPair ids = new SubShapeIdPair(pairVa);
            long key = key(ids.getBody1Id(), ids.getBody2Id());
            Pair pair = pairs.get(key);
            if (pair == null) {
                return;
            }
            if (pair.count.decrementAndGet() <= 0 && pairs.remove(key, pair)) {
                finish(pair);
            }
        }
        catch (Throwable ignored) {
        }
    }

    private final java.util.concurrent.atomic.AtomicLong budgetStep = new java.util.concurrent.atomic.AtomicLong(-1);
    private final AtomicInteger budgetUsed = new AtomicInteger();

    private double[] exactPoint(Body b1, Body b2, PhysBody p1, PhysBody p2) {
        int limit = config.exactContactsPerStep;
        if (limit <= 0) {
            return midpoint(p1, p2);
        }
        long step = world.stepCount();
        if (budgetStep.get() != step) {
            budgetStep.set(step);
            budgetUsed.set(0);
        }
        if (budgetUsed.incrementAndGet() > limit) {
            return midpoint(p1, p2);
        }
        double[] exact = ContactPoints.between(b1, b2);
        return exact != null ? exact : midpoint(p1, p2);
    }

    private void derived(Body b1, Body b2, PhysBody p1, PhysBody p2, double[] n12, double depth, double speed) {
        if (p1.def.internal && p2.def.internal) {
            return;
        }
        boolean r1 = p1.def.ragdoll != null && !p1.def.ragdoll.isEmpty();
        boolean r2 = p2.def.ragdoll != null && !p2.def.ragdoll.isEmpty();
        if (r1 && r2 && p1.def.ragdoll.equals(p2.def.ragdoll)) {
            return;
        }
        String v1 = world.vehicleService.vehicleOfBody(p1.name);
        String v2 = world.vehicleService.vehicleOfBody(p2.name);
        boolean ragdollHit = (r1 || r2) && speed >= config.ragdollHitMinSpeed;
        boolean vehicleHit = (v1 != null || v2 != null) && speed >= config.vehicleHitMinSpeed;
        if (!ragdollHit && !vehicleHit) {
            return;
        }
        long step = world.stepCount();
        Long last = lastDerived.get(key(b1.getId(), b2.getId()));
        if (last != null && step - last < DERIVED_COOLDOWN_STEPS) {
            return;
        }
        if (lastDerived.size() > 4096) {
            lastDerived.clear();
        }
        lastDerived.put(key(b1.getId(), b2.getId()), step);
        double[] point = exactPoint(b1, b2, p1, p2);
        double[] n21 = n12 == null ? null : ContactGeometry.negate(n12);
        if (ragdollHit) {
            if (r1) {
                emitDerived(PhysEvent.Kind.RAGDOLL_HIT, p1.def.ragdoll, p1, p2, n12, point, depth, speed);
            }
            if (r2) {
                emitDerived(PhysEvent.Kind.RAGDOLL_HIT, p2.def.ragdoll, p2, p1, n21, point, depth, speed);
            }
        }
        if (vehicleHit) {
            if (v1 != null) {
                emitDerived(PhysEvent.Kind.VEHICLE_HIT, v1, p1, p2, n12, point, depth, speed);
            }
            if (v2 != null) {
                emitDerived(PhysEvent.Kind.VEHICLE_HIT, v2, p2, p1, n21, point, depth, speed);
            }
        }
    }

    private void emitDerived(PhysEvent.Kind kind, String owner, PhysBody self, PhysBody other, double[] normalSelfToOther, double[] point, double depth, double speed) {
        if (self.def.internal) {
            return;
        }
        boolean terrain = other.def.internal;
        Snapshot s = self.snapshot;
        double[] normal = normalSelfToOther == null ? ContactGeometry.fallbackNormal(s, other.snapshot, terrain) : ContactGeometry.negate(normalSelfToOther);
        double[] p = point != null ? point : terrain ? ContactGeometry.boundaryPoint(s, ContactGeometry.negate(normal)) : ContactGeometry.overlapPoint(s, other.snapshot);
        events.offer(new PhysEvent(kind, world, self, terrain ? null : other, p, normal, speed, depth).withOwner(owner));
    }

    private void sensorAdded(PhysBody sensor, PhysBody occupant, long key, double speed, double depth) {
        if (occupant.def.internal) {
            return;
        }
        Pair pair = pairs.computeIfAbsent(key, k -> new Pair(sensor, occupant, sensor, occupant));
        if (pair.count.incrementAndGet() == 1 && sensor.def.notify) {
            events.offer(new PhysEvent(PhysEvent.Kind.SENSOR_ENTER, world, sensor, occupant, midpoint(sensor, occupant), null, speed, depth));
        }
    }

    private void finish(Pair pair) {
        if (pair.sensor != null) {
            if (pair.sensor.def.notify) {
                events.offer(new PhysEvent(PhysEvent.Kind.SENSOR_EXIT, world, pair.sensor, pair.occupant, midpoint(pair.sensor, pair.occupant), null, 0));
            }
            return;
        }
        emitContact(PhysEvent.Kind.CONTACT_REMOVED, pair.first, pair.second, null, 0, 0, null);
        emitContact(PhysEvent.Kind.CONTACT_REMOVED, pair.second, pair.first, null, 0, 0, null);
    }

    private static double[] midpoint(PhysBody a, PhysBody b) {
        return ContactGeometry.overlapPoint(a.snapshot, b.snapshot);
    }

    private void emitContact(PhysEvent.Kind kind, PhysBody self, PhysBody other, double[] normalSelfToOther, double depth, double speed, double[] exactPoint) {
        boolean wants = kind == PhysEvent.Kind.CONTACT_PERSISTED ? self.def.notifyPersist : self.def.notify;
        if (!wants || self.def.internal) {
            return;
        }
        boolean terrain = other.def.internal;
        Snapshot s = self.snapshot;
        double[] normal = normalSelfToOther == null ? ContactGeometry.fallbackNormal(s, other.snapshot, terrain) : ContactGeometry.negate(normalSelfToOther);
        double[] point = exactPoint != null ? exactPoint : terrain ? ContactGeometry.boundaryPoint(s, ContactGeometry.negate(normal)) : ContactGeometry.overlapPoint(s, other.snapshot);
        events.offer(new PhysEvent(kind, world, self, terrain ? null : other, point, normal, speed, depth));
    }

    List<PhysBody> occupants(PhysBody sensor) {
        List<PhysBody> out = new ArrayList<>();
        for (Pair pair : pairs.values()) {
            if (pair.sensor == sensor && !pair.occupant.isRemoved()) {
                out.add(pair.occupant);
            }
        }
        return out;
    }

    List<PhysBody> sensorsHolding(PhysBody body) {
        List<PhysBody> out = new ArrayList<>();
        for (Pair pair : pairs.values()) {
            if (pair.occupant == body && !pair.sensor.isRemoved()) {
                out.add(pair.sensor);
            }
        }
        return out;
    }

    List<PhysBody> touching(PhysBody body) {
        List<PhysBody> out = new ArrayList<>();
        for (Pair pair : pairs.values()) {
            if (pair.sensor != null) {
                continue;
            }
            PhysBody other = pair.first == body ? pair.second : pair.second == body ? pair.first : null;
            if (other != null && !other.def.internal && !other.isRemoved()) {
                out.add(other);
            }
        }
        return out;
    }

    void purge(PhysBody body) {
        for (Map.Entry<Long, Pair> entry : new ArrayList<>(pairs.entrySet())) {
            Pair pair = entry.getValue();
            if ((pair.first == body || pair.second == body) && pairs.remove(entry.getKey(), pair)) {
                finish(pair);
            }
        }
    }

    void clear() {
        pairs.clear();
        lastDerived.clear();
    }
}
