package dev.egor201.physizen.core;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PhysBody {

    public final String name;
    public final PhysWorld world;
    public final BodyDef def;
    public volatile Snapshot snapshot;
    public volatile String tag;
    public final List<Object> attachments = new CopyOnWriteArrayList<>();
    volatile int joltId = -1;
    volatile boolean removed = false;
    volatile boolean ready = false;
    Body body;
    float cachedMass = 0;

    PhysBody(PhysWorld world, BodyDef def) {
        this.world = world;
        this.def = def;
        this.name = def.name;
        this.tag = def.tag;
        this.snapshot = Snapshot.initial(def.position, def.rotation);
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isReady() {
        return ready;
    }

    public MotionKind type() {
        return def.type;
    }

    public double mass() {
        return cachedMass;
    }

    public void remove() {
        world.removeBody(name);
    }

    public void teleport(double[] pos, double[] rot) {
        world.post(() -> {
            if (!ready) {
                return;
            }
            RVec3 p = new RVec3(pos[0], pos[1], pos[2]);
            if (rot != null) {
                world.bodyInterface.setPositionAndRotation(joltId, p, new Quat((float) rot[0], (float) rot[1], (float) rot[2], (float) rot[3]), EActivation.Activate);
            }
            else {
                world.bodyInterface.setPosition(joltId, p, EActivation.Activate);
            }
        });
    }

    public void moveKinematic(double[] pos, double[] rot, double seconds) {
        world.post(() -> {
            if (!ready) {
                return;
            }
            RVec3 p = new RVec3(pos[0], pos[1], pos[2]);
            Quat q = rot == null ? world.bodyInterface.getRotation(joltId) : new Quat((float) rot[0], (float) rot[1], (float) rot[2], (float) rot[3]);
            world.bodyInterface.moveKinematic(joltId, p, q, (float) Math.max(seconds, 1.0 / 240.0));
        });
    }

    public void setLinearVelocity(double[] v) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.setLinearVelocity(joltId, new Vec3((float) v[0], (float) v[1], (float) v[2]));
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void setAngularVelocity(double[] v) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.setAngularVelocity(joltId, new Vec3((float) v[0], (float) v[1], (float) v[2]));
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void addForce(double[] f) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.addForce(joltId, new Vec3((float) f[0], (float) f[1], (float) f[2]));
            }
        });
    }

    public void addForceAt(double[] f, double[] point) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.addForce(joltId, new Vec3((float) f[0], (float) f[1], (float) f[2]), new RVec3(point[0], point[1], point[2]));
            }
        });
    }

    public void addImpulse(double[] i) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.addImpulse(joltId, new Vec3((float) i[0], (float) i[1], (float) i[2]));
            }
        });
    }

    public void addImpulseAt(double[] i, double[] point) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.addImpulse(joltId, new Vec3((float) i[0], (float) i[1], (float) i[2]), new RVec3(point[0], point[1], point[2]));
            }
        });
    }

    public void addTorque(double[] t) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.addTorque(joltId, new Vec3((float) t[0], (float) t[1], (float) t[2]));
            }
        });
    }

    public void addAngularImpulse(double[] t) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.addAngularImpulse(joltId, new Vec3((float) t[0], (float) t[1], (float) t[2]));
            }
        });
    }

    public void setFriction(double value) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.setFriction(joltId, (float) value);
            }
        });
    }

    public void setRestitution(double value) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.setRestitution(joltId, (float) value);
            }
        });
    }

    public void setGravityFactor(double value) {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.setGravityFactor(joltId, (float) value);
            }
        });
    }

    public void setDamping(Double linear, Double angular) {
        world.post(() -> {
            if (ready && body != null && body.getMotionProperties() != null) {
                if (linear != null) {
                    body.getMotionProperties().setLinearDamping(linear.floatValue());
                }
                if (angular != null) {
                    body.getMotionProperties().setAngularDamping(angular.floatValue());
                }
            }
        });
    }

    public void activate() {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void deactivate() {
        world.post(() -> {
            if (ready) {
                world.bodyInterface.deactivateBody(joltId);
            }
        });
    }
}
