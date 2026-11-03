package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Constraint;
import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.constraint.ConstraintFactory;
import dev.egor201.physizen.core.constraint.ConstraintMotors;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.validate.Guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ConstraintService {

    private static final class Entry {

        final ConstraintDef def;
        volatile Constraint constraint;

        Entry(ConstraintDef def) {
            this.def = def;
        }
    }

    private final PhysWorld world;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    ConstraintService(PhysWorld world) {
        this.world = world;
    }

    int count() {
        return entries.size();
    }

    List<String> names() {
        return new ArrayList<>(entries.keySet());
    }

    ConstraintDef def(String name) {
        Entry entry = entries.get(name);
        return entry == null ? null : entry.def;
    }

    void add(ConstraintDef def) {
        if (def.name == null || def.name.isEmpty()) {
            throw new PhysException("Constraint needs a name.");
        }
        if (entries.containsKey(def.name)) {
            throw new PhysException("Constraint " + def.name + " already exists.");
        }
        PhysBody a = def.bodyA == null ? null : world.body(def.bodyA);
        PhysBody b = def.bodyB == null ? null : world.body(def.bodyB);
        if (def.bodyA != null && a == null) {
            throw new PhysException("Unknown body " + def.bodyA + ".");
        }
        if (def.bodyB != null && b == null) {
            throw new PhysException("Unknown body " + def.bodyB + ".");
        }
        if (a == null && b == null) {
            throw new PhysException("A constraint needs at least one body.");
        }
        if ((a != null && a.character != null) || (b != null && b.character != null)) {
            throw new PhysException("Characters cannot be used in constraints.");
        }
        Guard.validateConstraint(def);
        int limit = world.engine.config().maxConstraintsPerWorld;
        if (entries.size() >= limit) {
            throw new PhysException("World " + world.id + " reached the constraint limit (" + limit + ").");
        }
        def.captureLocal(a == null ? null : a.snapshot, b == null ? null : b.snapshot);
        Entry entry = new Entry(def);
        entries.put(def.name, entry);
        world.post(() -> instantiate(entry, a, b));
    }

    private static boolean unusable(PhysBody pb) {
        return pb != null && (pb.removed || !pb.ready || pb.body == null);
    }

    private void instantiate(Entry entry, PhysBody a, PhysBody b) {
        ConstraintDef def = entry.def;
        if (unusable(a) || unusable(b)) {
            entries.remove(def.name, entry);
            world.log.warning("Constraint " + def.name + " was dropped because one of its bodies is gone.");
            return;
        }
        try {
            Body body1 = a != null ? a.body : Body.sFixedToWorld();
            Body body2 = b != null ? b.body : Body.sFixedToWorld();
            TwoBodyConstraint made = ConstraintFactory.create(def, body1, body2);
            world.system.addConstraint(made);
            entry.constraint = made;
            world.disableRagdollCollision(a, b);
        }
        catch (Throwable t) {
            entries.remove(def.name, entry);
            world.log.warning("Failed to create constraint " + def.name + ": " + t.getMessage());
        }
    }

    void setMotor(String name, ConstraintDef.MotorState state, Double target, Double maxForceOrTorque, Double springFreq, Double springDamp) {
        world.post(() -> {
            Entry entry = entries.get(name);
            if (entry == null || entry.constraint == null) {
                return;
            }
            ConstraintMotors.update(entry.constraint, entry.def, state, target, maxForceOrTorque, springFreq, springDamp);
            wakeBodies(entry.def);
        });
    }

    private void wakeBodies(ConstraintDef def) {
        for (String bodyName : new String[]{def.bodyA, def.bodyB}) {
            if (bodyName == null) {
                continue;
            }
            PhysBody pb = world.body(bodyName);
            if (pb != null && pb.ready && !pb.removed && pb.def.type != MotionKind.STATIC) {
                world.bodyInterface.activateBody(pb.joltId);
            }
        }
    }

    Double hingeAngle(String name) {
        Entry entry = entries.get(name);
        if (entry == null || !(entry.constraint instanceof HingeConstraint)) {
            return null;
        }
        world.queryLock.readLock().lock();
        try {
            return (double) ((HingeConstraint) entry.constraint).getCurrentAngle();
        }
        catch (Throwable t) {
            return null;
        }
        finally {
            world.queryLock.readLock().unlock();
        }
    }

    Double sliderPosition(String name) {
        Entry entry = entries.get(name);
        if (entry == null || !(entry.constraint instanceof SliderConstraint)) {
            return null;
        }
        world.queryLock.readLock().lock();
        try {
            return (double) ((SliderConstraint) entry.constraint).getCurrentPosition();
        }
        catch (Throwable t) {
            return null;
        }
        finally {
            world.queryLock.readLock().unlock();
        }
    }

    boolean remove(String name) {
        if (!entries.containsKey(name)) {
            return false;
        }
        world.post(() -> drop(name));
        return true;
    }

    void removeFor(String bodyName) {
        for (Map.Entry<String, Entry> e : new ArrayList<>(entries.entrySet())) {
            ConstraintDef d = e.getValue().def;
            if (bodyName.equals(d.bodyA) || bodyName.equals(d.bodyB)) {
                remove(e.getKey());
            }
        }
    }

    void checkBreaks() {
        for (Entry entry : entries.values()) {
            ConstraintDef d = entry.def;
            if (d.breakDistance <= 0 || entry.constraint == null || d.kind == ConstraintDef.Kind.DISTANCE || d.localA == null || d.localB == null) {
                continue;
            }
            PhysBody a = d.bodyA == null ? null : world.body(d.bodyA);
            PhysBody b = d.bodyB == null ? null : world.body(d.bodyB);
            if (unusable(a) || unusable(b)) {
                continue;
            }
            double[] pa = ConstraintDef.toWorld(a == null ? null : a.snapshot, d.localA);
            double[] pb = ConstraintDef.toWorld(b == null ? null : b.snapshot, d.localB);
            double dx = pa[0] - pb[0];
            double dy = pa[1] - pb[1];
            double dz = pa[2] - pb[2];
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= d.breakDistance) {
                continue;
            }
            drop(d.name);
            PhysBody self = a != null ? a : b;
            PhysBody other = a != null ? b : null;
            if (self != null) {
                world.offerEvent(new PhysEvent(PhysEvent.Kind.CONSTRAINT_BREAK, world, self, other, new double[]{(pa[0] + pb[0]) * 0.5, (pa[1] + pb[1]) * 0.5, (pa[2] + pb[2]) * 0.5}, null, 0).withOwner(d.name));
            }
            wakeBodies(d);
        }
    }

    void drop(String name) {
        Entry entry = entries.remove(name);
        if (entry != null && entry.constraint != null) {
            try {
                world.system.removeConstraint(entry.constraint);
            }
            catch (Throwable t) {
                world.log.warning("Failed to remove constraint " + name + ": " + t.getMessage());
            }
        }
    }

    void dropAll() {
        for (String name : new ArrayList<>(entries.keySet())) {
            drop(name);
        }
    }
}
