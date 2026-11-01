package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.BodyFilter;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.CharacterContactSettings;
import com.github.stephengold.joltjni.CharacterVirtual;
import com.github.stephengold.joltjni.CharacterVirtualSettings;
import com.github.stephengold.joltjni.CustomCharacterContactListener;
import com.github.stephengold.joltjni.ExtendedUpdateSettings;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import com.github.stephengold.joltjni.Plane;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.RotatedTranslatedShapeSettings;
import com.github.stephengold.joltjni.ShapeFilter;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EBackFaceMode;
import com.github.stephengold.joltjni.enumerate.EGroundState;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.CharacterDef;
import dev.egor201.physizen.core.shape.ShapeBuilder;
import dev.egor201.physizen.core.validate.Guard;

import java.util.concurrent.ConcurrentLinkedQueue;

public final class PhysCharacter {

    public final String name;
    public final PhysWorld world;
    public final CharacterDef def;
    public volatile CharacterState state;
    final PhysBody bodyRef;
    private CharacterVirtual character;
    private ShapeRefC shape;
    private ExtendedUpdateSettings walkSettings;
    private ExtendedUpdateSettings floatSettings;
    private final BroadPhaseLayerFilter bpFilter = new BroadPhaseLayerFilter();
    private final ObjectLayerFilter olFilter = new ObjectLayerFilter();
    private final BodyFilter bodyFilter = new BodyFilter();
    private final ShapeFilter shapeFilter = new ShapeFilter();
    private final ConcurrentLinkedQueue<double[]> impulses = new ConcurrentLinkedQueue<>();
    private volatile double[] desired = new double[3];
    private volatile double[] forcedVelocity;
    private volatile double jumpSpeed = Double.NaN;
    private volatile double gravityFactor;
    private volatile double[] trackPosition;
    private volatile double[] trackVelocity = new double[3];
    private volatile long trackNanos;
    private volatile boolean ready;
    private volatile boolean removed;

    PhysCharacter(PhysWorld world, CharacterDef def, PhysBody bodyRef) {
        this.world = world;
        this.def = def;
        this.name = def.name;
        this.bodyRef = bodyRef;
        this.gravityFactor = def.gravityFactor;
        this.trackPosition = def.position.clone();
        this.trackNanos = System.nanoTime();
        this.state = new CharacterState(def.position.clone(), new double[3], CharacterState.Ground.IN_AIR, new double[]{0, 1, 0}, new double[3], null, false, 0);
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isRemoved() {
        return removed;
    }

    public PhysBody body() {
        return bodyRef;
    }

    public void remove() {
        world.removeCharacter(name);
    }

    public void move(double[] velocity) {
        Guard.vec(velocity, "velocity");
        desired = velocity.clone();
    }

    public void jump(double speed) {
        Guard.number(speed, "speed");
        jumpSpeed = speed;
    }

    public void setVelocity(double[] velocity) {
        Guard.vec(velocity, "velocity");
        forcedVelocity = velocity.clone();
    }

    public void addImpulse(double[] impulse) {
        Guard.vec(impulse, "impulse");
        impulses.add(impulse.clone());
    }

    public void setGravityFactor(double value) {
        Guard.number(value, "value");
        gravityFactor = value;
    }

    public void track(double[] position, double[] velocity) {
        Guard.position(position, "position");
        Guard.vec(velocity, "velocity");
        trackVelocity = velocity.clone();
        trackPosition = position.clone();
        trackNanos = System.nanoTime();
    }

    public void teleport(double[] position, double[] rotation) {
        Guard.position(position, "position");
        if (rotation != null) {
            Guard.quat(rotation, "rotation");
            Guard.normalizeQuat(rotation);
        }
        trackPosition = position.clone();
        trackNanos = System.nanoTime();
        world.post(() -> {
            if (!ready || removed) {
                return;
            }
            character.setPosition(new RVec3(position[0], position[1], position[2]));
            if (rotation != null) {
                character.setRotation(new Quat((float) rotation[0], (float) rotation[1], (float) rotation[2], (float) rotation[3]));
            }
            character.setLinearVelocity(new Vec3(0f, 0f, 0f));
        });
    }

    void instantiate(int objectLayer) {
        ShapeRefC base = ShapeBuilder.build(def.shape);
        try {
            float offset = (float) def.footOffset();
            ShapeResult result = new RotatedTranslatedShapeSettings(new Vec3(0f, offset, 0f), new Quat(0f, 0f, 0f, 1f), base).create();
            if (result.hasError()) {
                throw new PhysException("Failed to build character shape: " + result.getError());
            }
            shape = result.get();
        }
        finally {
            base.close();
        }
        Vec3 up = new Vec3((float) def.up[0], (float) def.up[1], (float) def.up[2]);
        CharacterVirtualSettings settings = new CharacterVirtualSettings();
        settings.setShape(shape);
        settings.setUp(up);
        settings.setSupportingVolume(new Plane(up, (float) -def.supportRadius()));
        settings.setMaxSlopeAngle((float) Math.toRadians(def.maxSlopeDegrees));
        settings.setMass((float) def.mass);
        settings.setMaxStrength((float) def.maxStrength);
        settings.setCharacterPadding((float) def.padding);
        settings.setPenetrationRecoverySpeed((float) def.penetrationRecoverySpeed);
        settings.setPredictiveContactDistance((float) def.predictiveContactDistance);
        settings.setBackFaceMode(EBackFaceMode.IgnoreBackFaces);
        settings.setEnhancedInternalEdgeRemoval(true);
        if (def.innerBody) {
            settings.setInnerBodyShape(shape);
            settings.setInnerBodyLayer(objectLayer);
        }
        character = new CharacterVirtual(settings, new RVec3(def.position[0], def.position[1], def.position[2]),
                new Quat((float) def.rotation[0], (float) def.rotation[1], (float) def.rotation[2], (float) def.rotation[3]), 0L, world.system);
        boolean push = def.pushBodies;
        character.setListener(new CustomCharacterContactListener() {
            @Override
            public void onContactAdded(long characterVa, long contactVa, long settingsVa) {
                new CharacterContactSettings(settingsVa).setCanReceiveImpulses(push);
            }

            @Override
            public void onContactPersisted(long characterVa, long contactVa, long settingsVa) {
                new CharacterContactSettings(settingsVa).setCanReceiveImpulses(push);
            }
        });
        walkSettings = new ExtendedUpdateSettings();
        if (def.walkStairs && def.stepHeight > 0) {
            walkSettings.setWalkStairsStepUp(new Vec3(0f, (float) def.stepHeight, 0f));
        }
        else {
            walkSettings.setWalkStairsStepUp(new Vec3(0f, 0f, 0f));
        }
        if (def.stickToFloor && def.stickDistance > 0) {
            walkSettings.setStickToFloorStepDown(new Vec3(0f, (float) -def.stickDistance, 0f));
        }
        else {
            walkSettings.setStickToFloorStepDown(new Vec3(0f, 0f, 0f));
        }
        floatSettings = new ExtendedUpdateSettings();
        floatSettings.setWalkStairsStepUp(new Vec3(0f, 0f, 0f));
        floatSettings.setStickToFloorStepDown(new Vec3(0f, 0f, 0f));
        if (def.innerBody) {
            int innerId = character.getInnerBodyId();
            bodyRef.joltId = innerId;
            world.byJoltId.put(innerId, bodyRef);
            bodyRef.ready = true;
        }
        ready = true;
    }

    void prePhysics(float dt, double[] gravity) {
        if (!ready || removed) {
            return;
        }
        if (def.mode == CharacterDef.Mode.TRACK) {
            double age = Math.min((System.nanoTime() - trackNanos) / 1.0e9, 0.1);
            double[] p = trackPosition;
            double[] v = trackVelocity;
            character.setPosition(new RVec3(p[0] + v[0] * age, p[1] + v[1] * age, p[2] + v[2] * age));
            character.setLinearVelocity(new Vec3((float) v[0], (float) v[1], (float) v[2]));
            character.extendedUpdate(dt, new Vec3(0f, 0f, 0f), floatSettings, bpFilter, olFilter, bodyFilter, shapeFilter, world.engine.tempAllocator());
            return;
        }
        Vec3 up = character.getUp();
        Vec3 current = character.getLinearVelocity();
        Vec3 groundVelocity = character.getGroundVelocity();
        Vec3 next;
        double[] forced = forcedVelocity;
        if (forced != null) {
            forcedVelocity = null;
            next = new Vec3((float) forced[0], (float) forced[1], (float) forced[2]);
        }
        else {
            float vertical = current.dot(up);
            float groundVertical = groundVelocity.dot(up);
            if (character.getGroundState() == EGroundState.OnGround && vertical - groundVertical < 0.1f) {
                next = new Vec3(groundVelocity);
                double jump = jumpSpeed;
                if (!Double.isNaN(jump)) {
                    next = new Vec3(next.getX() + up.getX() * (float) jump, next.getY() + up.getY() * (float) jump, next.getZ() + up.getZ() * (float) jump);
                }
            }
            else {
                next = new Vec3(up.getX() * vertical, up.getY() * vertical, up.getZ() * vertical);
            }
        }
        jumpSpeed = Double.NaN;
        double gf = gravityFactor;
        float gx = (float) (gravity[0] * gf);
        float gy = (float) (gravity[1] * gf);
        float gz = (float) (gravity[2] * gf);
        double[] d = desired;
        float ix = 0;
        float iy = 0;
        float iz = 0;
        double[] imp;
        while ((imp = impulses.poll()) != null) {
            ix += (float) (imp[0] / def.mass);
            iy += (float) (imp[1] / def.mass);
            iz += (float) (imp[2] / def.mass);
        }
        Vec3 gravityVec = new Vec3(gx, gy, gz);
        character.setLinearVelocity(new Vec3(
                next.getX() + gx * dt + (float) d[0] + ix,
                next.getY() + gy * dt + (float) d[1] + iy,
                next.getZ() + gz * dt + (float) d[2] + iz));
        character.extendedUpdate(dt, gravityVec, walkSettings, bpFilter, olFilter, bodyFilter, shapeFilter, world.engine.tempAllocator());
    }

    void publish(long step) {
        if (!ready || removed) {
            return;
        }
        RVec3 p = character.getPosition();
        Vec3 v = character.getLinearVelocity();
        Vec3 n = character.getGroundNormal();
        Vec3 gv = character.getGroundVelocity();
        EGroundState gs = character.getGroundState();
        CharacterState.Ground ground = gs == EGroundState.OnGround ? CharacterState.Ground.ON_GROUND
                : gs == EGroundState.OnSteepGround ? CharacterState.Ground.ON_STEEP_GROUND
                : gs == EGroundState.NotSupported ? CharacterState.Ground.NOT_SUPPORTED : CharacterState.Ground.IN_AIR;
        PhysBody groundBody = ground == CharacterState.Ground.IN_AIR ? null : world.byJoltId.get(character.getGroundBodyId());
        state = new CharacterState(new double[]{p.xx(), p.yy(), p.zz()}, new double[]{v.getX(), v.getY(), v.getZ()}, ground,
                new double[]{n.getX(), n.getY(), n.getZ()}, new double[]{gv.getX(), gv.getY(), gv.getZ()}, groundBody, character.isSupported(), step);
    }

    void markRemoved() {
        removed = true;
    }

    void destroy() {
        removed = true;
        ready = false;
        try {
            if (bodyRef != null && bodyRef.joltId >= 0) {
                world.byJoltId.remove(bodyRef.joltId);
            }
            if (character != null) {
                character.close();
            }
        }
        catch (Throwable ignored) {
        }
        character = null;
    }
}
