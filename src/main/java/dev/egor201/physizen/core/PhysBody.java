package dev.egor201.physizen.core;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EAllowedDofs;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;

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
    volatile boolean fellOut = false;
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

    public void reshape(ShapeSpec spec) {
        world.post(() -> {
            if (!ready || body == null || def.internal) {
                return;
            }
            if (spec.requiresNonDynamic() && def.type == MotionKind.DYNAMIC) {
                return;
            }
            applyShape(spec);
        });
    }

    public void scale(double factor) {
        world.post(() -> {
            if (!ready || body == null || def.internal) {
                return;
            }
            applyShape(def.shape.scaled(factor));
        });
    }

    private void applyShape(ShapeSpec spec) {
        ShapeRefC shape = spec.build();
        world.bodyInterface.setShape(joltId, shape, true, EActivation.Activate);
        def.shape = spec;
        reapplyMass();
        world.publish(this, true);
    }

    private void reapplyMass() {
        if (def.type != MotionKind.DYNAMIC || body == null || body.getMotionProperties() == null) {
            return;
        }
        ConstShape shape = body.getShape();
        MassProperties properties = shape.getMassProperties();
        if (def.mass > 0) {
            properties.scaleToMass((float) def.mass);
        }
        body.getMotionProperties().setMassProperties(EAllowedDofs.All, properties);
        float inverse = body.getMotionProperties().getInverseMass();
        cachedMass = inverse > 0 ? 1f / inverse : 0f;
    }

    public void setMass(double mass) {
        world.post(() -> {
            if (!ready || body == null || def.internal) {
                return;
            }
            def.mass = Math.max(0, mass);
            if (def.type == MotionKind.DYNAMIC) {
                reapplyMass();
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void setType(MotionKind kind) {
        world.post(() -> {
            if (!ready || body == null || def.internal || kind == def.type) {
                return;
            }
            if (kind == MotionKind.DYNAMIC && def.shape.requiresNonDynamic()) {
                return;
            }
            EMotionType motion = kind == MotionKind.STATIC ? EMotionType.Static : kind == MotionKind.KINEMATIC ? EMotionType.Kinematic : EMotionType.Dynamic;
            EActivation activation = kind == MotionKind.STATIC ? EActivation.DontActivate : EActivation.Activate;
            world.bodyInterface.setMotionType(joltId, motion, activation);
            def.type = kind;
            world.bodyInterface.setObjectLayer(joltId, kind == MotionKind.STATIC ? PhysWorld.LAYER_STATIC : def.group + 1);
            if (kind == MotionKind.DYNAMIC) {
                reapplyMass();
            }
            else {
                cachedMass = 0f;
            }
            world.publish(this, true);
        });
    }

    public void setGroup(int group) {
        world.post(() -> {
            if (!ready || body == null || def.internal) {
                return;
            }
            def.group = group;
            if (def.type != MotionKind.STATIC) {
                world.bodyInterface.setObjectLayer(joltId, group + 1);
            }
        });
    }

    public void setSensor(boolean value) {
        world.post(() -> {
            if (!ready || body == null || def.internal) {
                return;
            }
            def.sensor = value;
            body.setIsSensor(value);
            if (def.type != MotionKind.STATIC) {
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void setContinuous(boolean value) {
        world.post(() -> {
            if (!ready || body == null || def.internal || body.getMotionProperties() == null) {
                return;
            }
            def.continuous = value;
            world.bodyInterface.setMotionQuality(joltId, value ? EMotionQuality.LinearCast : EMotionQuality.Discrete);
        });
    }

    public void setAllowSleeping(boolean value) {
        world.post(() -> {
            if (!ready || body == null || def.internal) {
                return;
            }
            def.allowSleeping = value;
            body.setAllowSleeping(value);
            if (!value && def.type != MotionKind.STATIC) {
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void setMaxVelocities(Double linear, Double angular) {
        world.post(() -> {
            if (!ready || body == null || body.getMotionProperties() == null) {
                return;
            }
            if (linear != null) {
                def.maxLinearVelocity = linear;
                body.getMotionProperties().setMaxLinearVelocity(linear.floatValue());
            }
            if (angular != null) {
                def.maxAngularVelocity = angular;
                body.getMotionProperties().setMaxAngularVelocity(angular.floatValue());
            }
        });
    }

    public void setNotify(boolean value) {
        world.post(() -> def.notify = value);
    }

    public void setRemoveOnFallOut(boolean value) {
        world.post(() -> def.removeOnFallOut = value);
    }
}
