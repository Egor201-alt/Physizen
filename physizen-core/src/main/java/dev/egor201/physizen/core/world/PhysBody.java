package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EAllowedDofs;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.model.ShapeSpec;
import dev.egor201.physizen.core.model.Snapshot;
import dev.egor201.physizen.core.shape.ShapeBuilder;
import dev.egor201.physizen.core.validate.Guard;

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
    PhysCharacter character;

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

    public PhysCharacter character() {
        return character;
    }

    public boolean isSensor() {
        return def.sensor;
    }

    public double[] momentum() {
        Snapshot s = snapshot;
        double m = cachedMass;
        return new double[]{s.vx * m, s.vy * m, s.vz * m};
    }

    public double kineticEnergy() {
        Snapshot s = snapshot;
        double v = s.speed();
        return 0.5 * cachedMass * v * v;
    }

    public List<PhysBody> touching() {
        return world.touching(this);
    }

    private boolean usable() {
        return ready && !removed && character == null;
    }

    private boolean editable() {
        return usable() && body != null && !def.internal;
    }

    public void remove() {
        world.removeBody(name);
    }

    public void teleport(double[] pos, double[] rot) {
        Guard.position(pos, "pos");
        if (rot != null) {
            Guard.quat(rot, "rotation");
            Guard.normalizeQuat(rot);
        }
        if (character != null) {
            character.teleport(pos, rot);
            return;
        }
        world.post(() -> {
            if (!usable()) {
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
        Guard.position(pos, "pos");
        if (rot != null) {
            Guard.quat(rot, "rotation");
            Guard.normalizeQuat(rot);
        }
        Guard.number(seconds, "seconds");
        world.post(() -> {
            if (!usable()) {
                return;
            }
            RVec3 p = new RVec3(pos[0], pos[1], pos[2]);
            Quat q = rot == null ? world.bodyInterface.getRotation(joltId) : new Quat((float) rot[0], (float) rot[1], (float) rot[2], (float) rot[3]);
            world.bodyInterface.moveKinematic(joltId, p, q, (float) Math.max(seconds, 1.0 / 240.0));
        });
    }

    public void setLinearVelocity(double[] v) {
        Guard.vec(v, "v");
        if (character != null) {
            character.setVelocity(v);
            return;
        }
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.setLinearVelocity(joltId, new Vec3((float) v[0], (float) v[1], (float) v[2]));
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void setAngularVelocity(double[] v) {
        Guard.vec(v, "v");
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.setAngularVelocity(joltId, new Vec3((float) v[0], (float) v[1], (float) v[2]));
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void addForce(double[] f) {
        Guard.vec(f, "f");
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.addForce(joltId, new Vec3((float) f[0], (float) f[1], (float) f[2]));
            }
        });
    }

    public void addForceAt(double[] f, double[] point) {
        Guard.vec(f, "f");
        Guard.position(point, "point");
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.addForce(joltId, new Vec3((float) f[0], (float) f[1], (float) f[2]), new RVec3(point[0], point[1], point[2]));
            }
        });
    }

    public void addImpulse(double[] i) {
        Guard.vec(i, "i");
        if (character != null) {
            character.addImpulse(i);
            return;
        }
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.addImpulse(joltId, new Vec3((float) i[0], (float) i[1], (float) i[2]));
            }
        });
    }

    public void addImpulseAt(double[] i, double[] point) {
        Guard.vec(i, "i");
        Guard.position(point, "point");
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.addImpulse(joltId, new Vec3((float) i[0], (float) i[1], (float) i[2]), new RVec3(point[0], point[1], point[2]));
            }
        });
    }

    public void addTorque(double[] t) {
        Guard.vec(t, "t");
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.addTorque(joltId, new Vec3((float) t[0], (float) t[1], (float) t[2]));
            }
        });
    }

    public void addAngularImpulse(double[] t) {
        Guard.vec(t, "t");
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.addAngularImpulse(joltId, new Vec3((float) t[0], (float) t[1], (float) t[2]));
            }
        });
    }

    public void setFriction(double value) {
        Guard.number(value, "value");
        world.post(() -> {
            if (usable()) {
                def.friction = value;
                world.bodyInterface.setFriction(joltId, (float) value);
            }
        });
    }

    public void setRestitution(double value) {
        Guard.number(value, "value");
        world.post(() -> {
            if (usable()) {
                def.restitution = value;
                world.bodyInterface.setRestitution(joltId, (float) value);
            }
        });
    }

    public void setGravityFactor(double value) {
        Guard.number(value, "value");
        if (character != null) {
            character.setGravityFactor(value);
            return;
        }
        world.post(() -> {
            if (usable()) {
                def.gravityFactor = value;
                world.bodyInterface.setGravityFactor(joltId, (float) value);
            }
        });
    }

    public void setDamping(Double linear, Double angular) {
        world.post(() -> {
            if (usable() && body != null && body.getMotionProperties() != null) {
                if (linear != null) {
                    def.linearDamping = linear;
                    body.getMotionProperties().setLinearDamping(linear.floatValue());
                }
                if (angular != null) {
                    def.angularDamping = angular;
                    body.getMotionProperties().setAngularDamping(angular.floatValue());
                }
            }
        });
    }

    public void activate() {
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void deactivate() {
        world.post(() -> {
            if (usable()) {
                world.bodyInterface.deactivateBody(joltId);
            }
        });
    }

    public void reshape(ShapeSpec spec) {
        world.post(() -> {
            if (!editable()) {
                return;
            }
            if (spec.requiresNonDynamic() && def.type == MotionKind.DYNAMIC) {
                return;
            }
            applyShape(spec);
        });
    }

    public void scale(double factor) {
        Guard.number(factor, "factor");
        world.post(() -> {
            if (!editable()) {
                return;
            }
            applyShape(def.shape.scaled(factor));
        });
    }

    private void applyShape(ShapeSpec spec) {
        ShapeRefC shape = ShapeBuilder.build(spec);
        try {
            world.bodyInterface.setShape(joltId, shape, true, EActivation.Activate);
        }
        finally {
            shape.close();
        }
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
        Guard.number(mass, "mass");
        world.post(() -> {
            if (!editable()) {
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
            if (!editable() || kind == def.type) {
                return;
            }
            if (kind == MotionKind.DYNAMIC && def.shape.requiresNonDynamic()) {
                return;
            }
            EActivation activation = kind == MotionKind.STATIC ? EActivation.DontActivate : EActivation.Activate;
            world.bodyInterface.setMotionType(joltId, BodyFactory.motion(kind), activation);
            def.type = kind;
            world.bodyInterface.setObjectLayer(joltId, WorldLayers.layerFor(def));
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
        WorldLayers.checkGroup(group);
        world.post(() -> {
            if (!editable()) {
                return;
            }
            def.group = group;
            world.bodyInterface.setObjectLayer(joltId, WorldLayers.layerFor(def));
        });
    }

    public void setSensor(boolean value) {
        world.post(() -> {
            if (!editable()) {
                return;
            }
            def.sensor = value;
            body.setIsSensor(value);
            world.bodyInterface.setObjectLayer(joltId, WorldLayers.layerFor(def));
            if (def.type != MotionKind.STATIC) {
                world.bodyInterface.activateBody(joltId);
            }
        });
    }

    public void setContinuous(boolean value) {
        world.post(() -> {
            if (!editable() || body.getMotionProperties() == null) {
                return;
            }
            def.continuous = value;
            world.bodyInterface.setMotionQuality(joltId, value ? EMotionQuality.LinearCast : EMotionQuality.Discrete);
        });
    }

    public void setAllowSleeping(boolean value) {
        world.post(() -> {
            if (!editable()) {
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
            if (!usable() || body == null || body.getMotionProperties() == null) {
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

    public void setNotifyPersist(boolean value) {
        world.post(() -> def.notifyPersist = value);
    }

    public void setRemoveOnFallOut(boolean value) {
        world.post(() -> def.removeOnFallOut = value);
    }

    public List<PhysBody> sensorContents() {
        return world.sensorContents(this);
    }

    public List<PhysBody> sensorsHolding() {
        return world.sensorsHolding(this);
    }
}
