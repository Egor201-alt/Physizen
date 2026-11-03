package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.model.Snapshot;
import dev.egor201.physizen.core.validate.Guard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class StepLoop {

    private final PhysWorld world;
    private final AtomicInteger pendingSingleSteps = new AtomicInteger();
    private volatile long stepCount = 0;
    private volatile long lastStepNanos = 0;
    private volatile int activeCount = 0;
    private int stepFailures = 0;
    private double accumulator = 0;

    StepLoop(PhysWorld world) {
        this.world = world;
    }

    long stepCount() {
        return stepCount;
    }

    long lastStepNanos() {
        return lastStepNanos;
    }

    int activeCount() {
        return activeCount;
    }

    void requestSteps(int count) {
        pendingSingleSteps.addAndGet(Math.max(1, count));
    }

    void advance(long elapsedNanos) {
        if (world.destroyed()) {
            return;
        }
        world.drainInbox();
        double dt = 1.0 / world.stepRate();
        int steps = pendingSingleSteps.getAndSet(0);
        if (steps > 0) {
            accumulator = 0;
        }
        else if (!world.paused() && world.timeScale() > 0) {
            accumulator += elapsedNanos / 1.0e9 * world.timeScale();
            int max = world.engine.config().maxCatchupSteps;
            while (accumulator >= dt && steps < max) {
                accumulator -= dt;
                steps++;
            }
            if (accumulator > dt * max) {
                accumulator = 0;
            }
        }
        for (int i = 0; i < steps; i++) {
            step((float) dt);
        }
        if (steps == 0) {
            world.drainInbox();
        }
    }

    private void step(float dt) {
        long start = System.nanoTime();
        double[] gravity = world.gravity();
        try {
            FieldSolver.apply(world.fieldDefs(), world.allBodies(), world.bodyInterface, gravity[1]);
        }
        catch (Throwable t) {
            world.log.warning("Field update failed in world " + world.id + ": " + t);
        }
        boolean failed = false;
        world.queryLock.writeLock().lock();
        try {
            for (PhysCharacter c : world.characters()) {
                try {
                    c.prePhysics(dt, gravity);
                }
                catch (Throwable t) {
                    world.log.warning("Character " + c.name + " update failed in world " + world.id + ": " + t);
                }
            }
            world.vehicleService.applyInputs();
            world.system.update(dt, world.engine.config().collisionSteps, world.engine.tempAllocator(), world.engine.jobSystem());
        }
        catch (Throwable t) {
            failed = true;
            world.log.severe("Physics step failed in world " + world.id + ": " + t);
        }
        finally {
            world.queryLock.writeLock().unlock();
        }
        if (failed) {
            if (++stepFailures >= 3) {
                world.setPaused(true);
                stepFailures = 0;
                world.log.severe("World " + world.id + " was paused after repeated physics errors. Fix the cause and use physworld resume.");
            }
            return;
        }
        stepFailures = 0;
        stepCount++;
        publishAll();
        world.vehicleService.publish();
        world.checkConstraintBreaks();
        lastStepNanos = System.nanoTime() - start;
    }

    private void publishAll() {
        int active = 0;
        List<String> broken = null;
        for (PhysBody pb : world.allBodies()) {
            publish(pb, false);
            Snapshot s = pb.snapshot;
            if (pb.ready && !Guard.inWorld(s.px, s.py, s.pz)) {
                if (broken == null) {
                    broken = new ArrayList<>();
                }
                broken.add(pb.name);
            }
            if (s.active) {
                active++;
            }
        }
        for (PhysCharacter c : world.characters()) {
            c.publish(stepCount);
        }
        activeCount = active;
        if (broken != null) {
            for (String name : broken) {
                world.log.warning("Body " + name + " in world " + world.id + " reached an invalid position and was removed.");
                world.removeBody(name);
            }
        }
    }

    void publish(PhysBody pb, boolean force) {
        if (!pb.ready || pb.removed) {
            return;
        }
        boolean active = world.bodyInterface.isActive(pb.joltId);
        Snapshot previous = pb.snapshot;
        if (!force && !active && !previous.active) {
            return;
        }
        RVec3 p = world.bodyInterface.getPosition(pb.joltId);
        Quat q = world.bodyInterface.getRotation(pb.joltId);
        Vec3 v = world.bodyInterface.getLinearVelocity(pb.joltId);
        Vec3 w = world.bodyInterface.getAngularVelocity(pb.joltId);
        float minX = (float) p.xx();
        float minY = (float) p.yy();
        float minZ = (float) p.zz();
        float maxX = minX;
        float maxY = minY;
        float maxZ = minZ;
        Body body = pb.body;
        if (body != null) {
            ConstAaBox box = body.getWorldSpaceBounds();
            Vec3 lo = box.getMin();
            Vec3 hi = box.getMax();
            minX = lo.getX();
            minY = lo.getY();
            minZ = lo.getZ();
            maxX = hi.getX();
            maxY = hi.getY();
            maxZ = hi.getZ();
        }
        else if (pb.character != null) {
            double[] half = pb.def.shape.halfExtentsEstimate();
            minX -= (float) half[0];
            minY -= (float) half[1];
            minZ -= (float) half[2];
            maxX += (float) half[0];
            maxY += (float) half[1];
            maxZ += (float) half[2];
            CharacterState cs = pb.character.state;
            v = new Vec3((float) cs.vx, (float) cs.vy, (float) cs.vz);
        }
        pb.snapshot = new Snapshot(p.xx(), p.yy(), p.zz(), q.getX(), q.getY(), q.getZ(), q.getW(),
                v.getX(), v.getY(), v.getZ(), w.getX(), w.getY(), w.getZ(),
                minX, minY, minZ, maxX, maxY, maxZ, active, stepCount);
        if (pb.def.type != MotionKind.STATIC) {
            if (p.yy() < world.engine.config().fallOutY) {
                if (!pb.fellOut) {
                    pb.fellOut = true;
                    world.offerEvent(new PhysEvent(PhysEvent.Kind.FALL_OUT, world, pb, null, new double[]{p.xx(), p.yy(), p.zz()}, null, 0));
                    if (pb.def.removeOnFallOut) {
                        world.removeBody(pb.name);
                    }
                }
            }
            else {
                pb.fellOut = false;
            }
        }
    }
}
