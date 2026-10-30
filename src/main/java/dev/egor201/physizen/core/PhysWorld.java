package dev.egor201.physizen.core;

import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.ConeConstraintSettings;
import com.github.stephengold.joltjni.Constraint;
import com.github.stephengold.joltjni.CustomBodyActivationListener;
import com.github.stephengold.joltjni.CustomContactListener;
import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.HingeConstraintSettings;
import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.ObjVsBpFilter;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.RRayCast;
import com.github.stephengold.joltjni.RayCastResult;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.SliderConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.ESpringMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public final class PhysWorld {

    public static final int GROUPS = 16;
    static final int LAYER_STATIC = 0;
    private static final int BP_STATIC = 0;
    private static final int BP_MOVING = 1;

    public final String id;
    public final PhysEngine engine;
    final PhysicsSystem system;
    final BodyInterface bodyInterface;
    private final BroadPhaseLayerInterfaceTable layerMap;
    private final ObjVsBpFilter objVsBp;
    private final ObjectLayerPairFilterTable pairFilter;
    private final CustomContactListener contactListener;
    private final CustomBodyActivationListener activationListener;
    private final ConcurrentLinkedQueue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PhysEvent> events = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, PhysBody> bodies = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Integer, PhysBody> byJoltId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> constraints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String[]> constraintBodies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConstraintDef> constraintDefs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FieldDef> fields = new ConcurrentHashMap<>();
    final ReentrantReadWriteLock queryLock = new ReentrantReadWriteLock();
    private final Logger log;
    private volatile double[] gravity = {0, -9.81, 0};
    private volatile double timeScale = 1.0;
    private volatile boolean paused = false;
    private volatile int stepRate;
    private volatile long stepCount = 0;
    private volatile long lastStepNanos = 0;
    private volatile int activeCount = 0;
    private final WorldQueries queries = new WorldQueries(this);
    private volatile boolean destroyed = false;
    private double accumulator = 0;
    private int pendingSingleSteps = 0;
    private int eventBacklog = 0;
    public volatile Object attachment;

    PhysWorld(String id, PhysEngine engine, Logger log, int stepRate) {
        this.id = id;
        this.engine = engine;
        this.log = log;
        this.stepRate = stepRate;
        int objLayers = 1 + GROUPS;
        pairFilter = new ObjectLayerPairFilterTable(objLayers);
        for (int g = 0; g < GROUPS; g++) {
            pairFilter.enableCollision(LAYER_STATIC, g + 1);
            for (int h = g; h < GROUPS; h++) {
                pairFilter.enableCollision(g + 1, h + 1);
            }
        }
        layerMap = new BroadPhaseLayerInterfaceTable(objLayers, 2);
        layerMap.mapObjectToBroadPhaseLayer(LAYER_STATIC, BP_STATIC);
        for (int g = 0; g < GROUPS; g++) {
            layerMap.mapObjectToBroadPhaseLayer(g + 1, BP_MOVING);
        }
        objVsBp = new ObjVsBpFilter(objLayers, 2);
        system = new PhysicsSystem();
        EngineConfig cfg = engine.config();
        system.init(cfg.maxBodies, 0, cfg.maxBodyPairs, cfg.maxContactConstraints, layerMap, objVsBp, pairFilter);
        system.setGravity(new Vec3(0, -9.81f, 0));
        bodyInterface = system.getBodyInterface();
        contactListener = new CustomContactListener() {
            @Override
            public void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
                handleContact(body1Va, body2Va, manifoldVa);
            }
        };
        system.setContactListener(contactListener);
        activationListener = new CustomBodyActivationListener() {
            @Override
            public void onBodyActivated(int bodyId, long userData) {
                handleActivation(bodyId, true);
            }

            @Override
            public void onBodyDeactivated(int bodyId, long userData) {
                handleActivation(bodyId, false);
            }
        };
        system.setBodyActivationListener(activationListener);
    }

    public double[] gravity() {
        return gravity.clone();
    }

    public void setGravity(double[] g) {
        gravity = g.clone();
        post(() -> system.setGravity(new Vec3((float) g[0], (float) g[1], (float) g[2])));
    }

    public double timeScale() {
        return timeScale;
    }

    public void setTimeScale(double scale) {
        timeScale = Math.max(0, scale);
    }

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean value) {
        paused = value;
    }

    public int stepRate() {
        return stepRate;
    }

    public void setStepRate(int rate) {
        stepRate = Math.max(1, Math.min(480, rate));
    }

    public long stepCount() {
        return stepCount;
    }

    public double lastStepMillis() {
        return lastStepNanos / 1_000_000.0;
    }

    public WorldQueries queries() {
        return queries;
    }

    public int activeBodyCount() {
        return activeCount;
    }

    public int constraintCount() {
        return constraints.size();
    }

    public boolean destroyed() {
        return destroyed;
    }

    public void singleStep(int count) {
        pendingSingleSteps += Math.max(1, count);
    }

    public void post(Runnable task) {
        inbox.add(task);
    }

    public int bodyCount() {
        int count = 0;
        for (PhysBody body : bodies.values()) {
            if (!body.def.internal) {
                count++;
            }
        }
        return count;
    }

    public List<PhysBody> bodies() {
        List<PhysBody> list = new ArrayList<>();
        for (PhysBody body : bodies.values()) {
            if (!body.def.internal) {
                list.add(body);
            }
        }
        return list;
    }

    public PhysBody body(String name) {
        return bodies.get(name);
    }

    public List<String> constraintNames() {
        return new ArrayList<>(constraints.keySet());
    }

    public ConstraintDef constraintDef(String name) {
        return constraintDefs.get(name);
    }

    public List<FieldDef> fieldDefs() {
        return new ArrayList<>(fields.values());
    }

    public void setGroupCollision(int a, int b, boolean enabled) {
        if (a < 0 || b < 0 || a >= GROUPS || b >= GROUPS) {
            throw new PhysException("Group must be between 0 and " + (GROUPS - 1) + ".");
        }
        post(() -> {
            if (enabled) {
                pairFilter.enableCollision(a + 1, b + 1);
            }
            else {
                pairFilter.disableCollision(a + 1, b + 1);
            }
        });
    }

    public PhysBody createBody(BodyDef def) {
        if (destroyed) {
            throw new PhysException("World " + id + " was removed.");
        }
        if (def.name == null || def.name.isEmpty()) {
            throw new PhysException("Body needs a name.");
        }
        if (def.shape == null) {
            throw new PhysException("Body needs a shape.");
        }
        if (def.shape.requiresNonDynamic() && def.type == MotionKind.DYNAMIC) {
            throw new PhysException("Mesh shapes can only be used by static or kinematic bodies.");
        }
        if (def.group < 0 || def.group >= GROUPS) {
            throw new PhysException("Group must be between 0 and " + (GROUPS - 1) + ".");
        }
        PhysBody body = new PhysBody(this, def);
        if (bodies.putIfAbsent(def.name, body) != null) {
            throw new PhysException("Body " + def.name + " already exists in world " + id + ".");
        }
        post(() -> instantiate(body));
        return body;
    }

    private void instantiate(PhysBody pb) {
        if (pb.removed) {
            return;
        }
        try {
            BodyDef def = pb.def;
            ShapeRefC shape = def.shape.build();
            BodyCreationSettings bcs = new BodyCreationSettings();
            bcs.setShape(shape);
            bcs.setPosition(new RVec3(def.position[0], def.position[1], def.position[2]));
            bcs.setRotation(new Quat((float) def.rotation[0], (float) def.rotation[1], (float) def.rotation[2], (float) def.rotation[3]));
            EMotionType motion = def.type == MotionKind.STATIC ? EMotionType.Static : def.type == MotionKind.KINEMATIC ? EMotionType.Kinematic : EMotionType.Dynamic;
            bcs.setMotionType(motion);
            bcs.setAllowDynamicOrKinematic(!def.internal);
            bcs.setObjectLayer(def.type == MotionKind.STATIC ? LAYER_STATIC : def.group + 1);
            bcs.setFriction((float) def.friction);
            bcs.setRestitution((float) def.restitution);
            bcs.setLinearDamping((float) def.linearDamping);
            bcs.setAngularDamping((float) def.angularDamping);
            bcs.setGravityFactor((float) def.gravityFactor);
            bcs.setMaxLinearVelocity((float) def.maxLinearVelocity);
            bcs.setMaxAngularVelocity((float) def.maxAngularVelocity);
            bcs.setIsSensor(def.sensor);
            bcs.setAllowSleeping(def.allowSleeping);
            bcs.setLinearVelocity(new Vec3((float) def.linearVelocity[0], (float) def.linearVelocity[1], (float) def.linearVelocity[2]));
            bcs.setAngularVelocity(new Vec3((float) def.angularVelocity[0], (float) def.angularVelocity[1], (float) def.angularVelocity[2]));
            if (def.continuous) {
                bcs.setMotionQuality(EMotionQuality.LinearCast);
            }
            if (def.mass > 0 && def.type == MotionKind.DYNAMIC) {
                bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
                bcs.getMassPropertiesOverride().setMass((float) def.mass);
            }
            Body body = bodyInterface.createBody(bcs);
            if (body == null) {
                throw new PhysException("Jolt could not create the body (body limit reached?).");
            }
            pb.body = body;
            pb.joltId = body.getId();
            byJoltId.put(pb.joltId, pb);
            EActivation activation = def.type == MotionKind.STATIC ? EActivation.DontActivate : EActivation.Activate;
            bodyInterface.addBody(body, activation);
            if (def.type == MotionKind.DYNAMIC && body.getMotionProperties() != null) {
                float inverse = body.getMotionProperties().getInverseMass();
                pb.cachedMass = inverse > 0 ? 1f / inverse : 0f;
            }
            pb.ready = true;
            publish(pb, true);
        }
        catch (Throwable t) {
            bodies.remove(pb.name, pb);
            pb.removed = true;
            log.warning("Failed to create body " + pb.name + " in world " + id + ": " + t.getMessage());
        }
    }

    public void removeBody(String name) {
        PhysBody pb = bodies.remove(name);
        if (pb == null) {
            return;
        }
        pb.removed = true;
        for (Map.Entry<String, String[]> entry : new ArrayList<>(constraintBodies.entrySet())) {
            String[] ends = entry.getValue();
            if (name.equals(ends[0]) || name.equals(ends[1])) {
                removeConstraint(entry.getKey());
            }
        }
        post(() -> destroyBody(pb));
    }

    private void destroyBody(PhysBody pb) {
        if (pb.joltId >= 0 && pb.ready) {
            pb.ready = false;
            byJoltId.remove(pb.joltId);
            bodyInterface.removeBody(pb.joltId);
            bodyInterface.destroyBody(pb.joltId);
        }
        pb.body = null;
    }

    public void clear() {
        for (String name : new ArrayList<>(bodies.keySet())) {
            PhysBody pb = bodies.get(name);
            if (pb != null && !pb.def.internal) {
                removeBody(name);
            }
        }
    }

    public void addField(FieldDef def) {
        fields.put(def.name, def);
    }

    public boolean removeField(String name) {
        return fields.remove(name) != null;
    }

    public void addConstraint(ConstraintDef def) {
        if (def.name == null || def.name.isEmpty()) {
            throw new PhysException("Constraint needs a name.");
        }
        if (constraints.containsKey(def.name)) {
            throw new PhysException("Constraint " + def.name + " already exists.");
        }
        PhysBody a = def.bodyA == null ? null : bodies.get(def.bodyA);
        PhysBody b = def.bodyB == null ? null : bodies.get(def.bodyB);
        if (def.bodyA != null && a == null) {
            throw new PhysException("Unknown body " + def.bodyA + ".");
        }
        if (def.bodyB != null && b == null) {
            throw new PhysException("Unknown body " + def.bodyB + ".");
        }
        if (a == null && b == null) {
            throw new PhysException("A constraint needs at least one body.");
        }
        def.captureLocal(a, b);
        constraints.put(def.name, Boolean.TRUE);
        constraintBodies.put(def.name, new String[]{def.bodyA, def.bodyB});
        constraintDefs.put(def.name, def);
        post(() -> instantiateConstraint(def, a, b));
    }

    private void instantiateConstraint(ConstraintDef def, PhysBody a, PhysBody b) {
        try {
            Body body1 = a != null && a.body != null ? a.body : Body.sFixedToWorld();
            Body body2 = b != null && b.body != null ? b.body : Body.sFixedToWorld();
            double[] p1 = def.pointA;
            double[] p2 = def.pointB == null ? def.pointA : def.pointB;
            RVec3 point1 = new RVec3(p1[0], p1[1], p1[2]);
            RVec3 point2 = new RVec3(p2[0], p2[1], p2[2]);
            Vec3 axis = new Vec3((float) def.axis[0], (float) def.axis[1], (float) def.axis[2]);
            Vec3 normal = new Vec3((float) def.normalAxis[0], (float) def.normalAxis[1], (float) def.normalAxis[2]);
            TwoBodyConstraint made;
            switch (def.kind) {
                case FIXED: {
                    FixedConstraintSettings s = new FixedConstraintSettings();
                    s.setSpace(EConstraintSpace.WorldSpace);
                    s.setAutoDetectPoint(true);
                    made = s.create(body1, body2);
                    break;
                }
                case HINGE: {
                    HingeConstraintSettings s = new HingeConstraintSettings();
                    s.setSpace(EConstraintSpace.WorldSpace);
                    s.setPoint1(point1);
                    s.setPoint2(point2);
                    s.setHingeAxis1(axis);
                    s.setHingeAxis2(axis);
                    s.setNormalAxis1(normal);
                    s.setNormalAxis2(normal);
                    if (def.limited) {
                        s.setLimitsMin((float) def.limitMin);
                        s.setLimitsMax((float) def.limitMax);
                    }
                    s.setMaxFrictionTorque((float) def.maxFriction);
                    made = s.create(body1, body2);

                    if (made instanceof HingeConstraint) {
                        HingeConstraint hinge = (HingeConstraint) made;
                        if (def.motorMaxTorque > 0) {
                            hinge.getMotorSettings().setTorqueLimit((float) def.motorMaxTorque);
                        }
                        if (def.motorSpringFrequency > 0) {
                            SpringSettings ss = hinge.getMotorSettings().getSpringSettings();
                            ss.setMode(ESpringMode.FrequencyAndDamping);
                            ss.setFrequency((float) def.motorSpringFrequency);
                            ss.setDamping((float) def.motorSpringDamping);
                        }
                        if (def.motorState == ConstraintDef.MotorState.VELOCITY) {
                            hinge.setMotorState(EMotorState.Velocity);
                            hinge.setTargetAngularVelocity((float) def.motorTarget);
                        }
                        else if (def.motorState == ConstraintDef.MotorState.POSITION) {
                            hinge.setMotorState(EMotorState.Position);
                            hinge.setTargetAngle((float) def.motorTarget);
                        }
                    }
                    break;
                }
                case SLIDER: {
                    SliderConstraintSettings s = new SliderConstraintSettings();
                    s.setSpace(EConstraintSpace.WorldSpace);
                    s.setAutoDetectPoint(true);
                    s.setSliderAxis(axis);
                    if (def.limited) {
                        s.setLimitsMin((float) def.limitMin);
                        s.setLimitsMax((float) def.limitMax);
                    }
                    s.setMaxFrictionForce((float) def.maxFriction);
                    made = s.create(body1, body2);

                    if (made instanceof SliderConstraint) {
                        SliderConstraint slider = (SliderConstraint) made;
                        if (def.motorMaxForce > 0) {
                            slider.getMotorSettings().setForceLimit((float) def.motorMaxForce);
                        }
                        if (def.motorSpringFrequency > 0) {
                            SpringSettings ss = slider.getMotorSettings().getSpringSettings();
                            ss.setMode(ESpringMode.FrequencyAndDamping);
                            ss.setFrequency((float) def.motorSpringFrequency);
                            ss.setDamping((float) def.motorSpringDamping);
                        }
                        if (def.motorState == ConstraintDef.MotorState.VELOCITY) {
                            slider.setMotorState(EMotorState.Velocity);
                            slider.setTargetVelocity((float) def.motorTarget);
                        }
                        else if (def.motorState == ConstraintDef.MotorState.POSITION) {
                            slider.setMotorState(EMotorState.Position);
                            slider.setTargetPosition((float) def.motorTarget);
                        }
                    }
                    break;
                }
                case DISTANCE: {
                    DistanceConstraintSettings s = new DistanceConstraintSettings();
                    s.setSpace(EConstraintSpace.WorldSpace);
                    s.setPoint1(point1);
                    s.setPoint2(point2);
                    float min = def.limited ? (float) def.limitMin : -1f;
                    float max = def.limited ? (float) def.limitMax : -1f;
                    s.setMinDistance(min);
                    s.setMaxDistance(max);
                    if (def.springFrequency > 0) {
                        SpringSettings spring = s.getLimitsSpringSettings();
                        spring.setMode(ESpringMode.FrequencyAndDamping);
                        spring.setFrequency((float) def.springFrequency);
                        spring.setDamping((float) def.springDamping);
                    }
                    made = s.create(body1, body2);
                    break;
                }
                case CONE: {
                    ConeConstraintSettings s = new ConeConstraintSettings();
                    s.setSpace(EConstraintSpace.WorldSpace);
                    s.setPoint1(point1);
                    s.setPoint2(point2);
                    s.setTwistAxis1(axis);
                    s.setTwistAxis2(axis);
                    s.setHalfConeAngle((float) def.halfConeAngle);
                    made = s.create(body1, body2);
                    break;
                }
                default: {
                    PointConstraintSettings s = new PointConstraintSettings();
                    s.setSpace(EConstraintSpace.WorldSpace);
                    s.setPoint1(point1);
                    s.setPoint2(point2);
                    made = s.create(body1, body2);
                    break;
                }
            }
            system.addConstraint(made);
            constraints.put(def.name, made);
        }
        catch (Throwable t) {
            constraints.remove(def.name);
            constraintBodies.remove(def.name);
            constraintDefs.remove(def.name);
            log.warning("Failed to create constraint " + def.name + ": " + t.getMessage());
        }
    }

    public void setConstraintMotor(String name, ConstraintDef.MotorState state, Double target, Double maxForceOrTorque, Double springFreq, Double springDamp) {
        post(() -> {
            Object c = constraints.get(name);
            ConstraintDef def = constraintDefs.get(name);
            if (c == null || def == null) {
                return;
            }

            if (def.kind == ConstraintDef.Kind.HINGE && c instanceof HingeConstraint) {
                HingeConstraint hinge = (HingeConstraint) c;
                MotorSettings ms = hinge.getMotorSettings();
                if (maxForceOrTorque != null && maxForceOrTorque > 0) {
                    ms.setTorqueLimit(maxForceOrTorque.floatValue());
                    def.motorMaxTorque = maxForceOrTorque;
                }
                if (springFreq != null && springFreq > 0) {
                    SpringSettings ss = ms.getSpringSettings();
                    ss.setMode(ESpringMode.FrequencyAndDamping);
                    ss.setFrequency(springFreq.floatValue());
                    if (springDamp != null) {
                        ss.setDamping(springDamp.floatValue());
                        def.motorSpringDamping = springDamp;
                    }
                    def.motorSpringFrequency = springFreq;
                }
                if (state != null) {
                    def.motorState = state;
                }
                if (target != null) {
                    def.motorTarget = target;
                }
                if (def.motorState == ConstraintDef.MotorState.VELOCITY) {
                    hinge.setMotorState(EMotorState.Velocity);
                    hinge.setTargetAngularVelocity((float) def.motorTarget);
                }
                else if (def.motorState == ConstraintDef.MotorState.POSITION) {
                    hinge.setMotorState(EMotorState.Position);
                    hinge.setTargetAngle((float) def.motorTarget);
                }
                else {
                    hinge.setMotorState(EMotorState.Off);
                }
                wakeConstraintBodies(name);
            }
            else if (def.kind == ConstraintDef.Kind.SLIDER && c instanceof SliderConstraint) {
                SliderConstraint slider = (SliderConstraint) c;
                MotorSettings ms = slider.getMotorSettings();
                if (maxForceOrTorque != null && maxForceOrTorque > 0) {
                    ms.setForceLimit(maxForceOrTorque.floatValue());
                    def.motorMaxForce = maxForceOrTorque;
                }
                if (springFreq != null && springFreq > 0) {
                    SpringSettings ss = ms.getSpringSettings();
                    ss.setMode(ESpringMode.FrequencyAndDamping);
                    ss.setFrequency(springFreq.floatValue());
                    if (springDamp != null) {
                        ss.setDamping(springDamp.floatValue());
                        def.motorSpringDamping = springDamp;
                    }
                    def.motorSpringFrequency = springFreq;
                }
                if (state != null) {
                    def.motorState = state;
                }
                if (target != null) {
                    def.motorTarget = target;
                }
                if (def.motorState == ConstraintDef.MotorState.VELOCITY) {
                    slider.setMotorState(EMotorState.Velocity);
                    slider.setTargetVelocity((float) def.motorTarget);
                }
                else if (def.motorState == ConstraintDef.MotorState.POSITION) {
                    slider.setMotorState(EMotorState.Position);
                    slider.setTargetPosition((float) def.motorTarget);
                }
                else {
                    slider.setMotorState(EMotorState.Off);
                }
                wakeConstraintBodies(name);
            }
        });
    }

    private void wakeConstraintBodies(String constraintName) {
        String[] ends = constraintBodies.get(constraintName);
        if (ends != null) {
            for (String bName : ends) {
                if (bName != null) {
                    PhysBody pb = bodies.get(bName);
                    if (pb != null && pb.ready && !pb.removed && pb.def.type != MotionKind.STATIC) {
                        bodyInterface.activateBody(pb.joltId);
                    }
                }
            }
        }
    }

    public Double constraintAngle(String name) {
        Object c = constraints.get(name);
        if (c == null) return null;
        queryLock.readLock().lock();
        try {
            if (c instanceof HingeConstraint) {
                HingeConstraint hinge = (HingeConstraint) c;
                return (double) hinge.getCurrentAngle();
            }
            return null;
        }
        catch (Throwable t) {
            return null;
        }
        finally {
            queryLock.readLock().unlock();
        }
    }

    public Double constraintPosition(String name) {
        Object c = constraints.get(name);
        if (c == null) return null;
        queryLock.readLock().lock();
        try {
            if (c instanceof SliderConstraint) {
                SliderConstraint slider = (SliderConstraint) c;
                return (double) slider.getCurrentPosition();
            }
            return null;
        }
        catch (Throwable t) {
            return null;
        }
        finally {
            queryLock.readLock().unlock();
        }
    }

    public boolean removeConstraint(String name) {
        constraintBodies.remove(name);
        constraintDefs.remove(name);
        Object c = constraints.remove(name);
        if (c == null) {
            return false;
        }
        post(() -> {
            if (c instanceof Constraint) {
                system.removeConstraint((Constraint) c);
            }
        });
        return true;
    }

    public void explosion(double[] center, double radius, double impulse, double upward) {
        for (PhysBody pb : bodies.values()) {
            if (pb.def.type != MotionKind.DYNAMIC || !pb.ready) {
                continue;
            }
            Snapshot s = pb.snapshot;
            double dx = s.px - center[0];
            double dy = s.py - center[1];
            double dz = s.pz - center[2];
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > radius) {
                continue;
            }
            double falloff = 1.0 - dist / radius;
            if (dist < 1.0e-6) {
                dx = 0;
                dy = 1;
                dz = 0;
                dist = 1;
            }
            double k = impulse * falloff / dist;
            double[] imp = {dx * k, dy * k + upward * impulse * falloff, dz * k};
            pb.addImpulse(imp);
        }
    }

    public List<PhysBody> inSphere(double[] c, double r) {
        List<PhysBody> out = new ArrayList<>();
        for (PhysBody pb : bodies.values()) {
            if (pb.def.internal) {
                continue;
            }
            Snapshot s = pb.snapshot;
            double nx = Math.max(s.minX, Math.min(c[0], s.maxX));
            double ny = Math.max(s.minY, Math.min(c[1], s.maxY));
            double nz = Math.max(s.minZ, Math.min(c[2], s.maxZ));
            double dx = nx - c[0];
            double dy = ny - c[1];
            double dz = nz - c[2];
            if (dx * dx + dy * dy + dz * dz <= r * r) {
                out.add(pb);
            }
        }
        return out;
    }

    public List<PhysBody> inBox(double[] min, double[] max) {
        List<PhysBody> out = new ArrayList<>();
        for (PhysBody pb : bodies.values()) {
            if (pb.def.internal) {
                continue;
            }
            Snapshot s = pb.snapshot;
            if (s.maxX >= min[0] && s.minX <= max[0] && s.maxY >= min[1] && s.minY <= max[1] && s.maxZ >= min[2] && s.minZ <= max[2]) {
                out.add(pb);
            }
        }
        return out;
    }

    public RayHit rayCast(double[] from, double[] dir, double maxDistance) {
        QueryFilter filter = new QueryFilter();
        filter.sensors = true;
        List<QueryHit> hits = queries.rayCast(from, dir, maxDistance, filter, false);
        if (hits.isEmpty()) {
            return null;
        }
        QueryHit h = hits.get(0);
        return new RayHit(h.body, h.point, h.normal, h.distance, h.fraction);
    }

    public PhysEvent pollEvent() {
        PhysEvent e = events.poll();
        if (e != null) {
            eventBacklog--;
        }
        return e;
    }

    private void offer(PhysEvent e) {
        if (eventBacklog > engine.config().contactEventQueueLimit) {
            return;
        }
        eventBacklog++;
        events.add(e);
    }

    private void handleContact(long body1Va, long body2Va, long manifoldVa) {
        try {
            Body b1 = new Body(body1Va);
            Body b2 = new Body(body2Va);
            PhysBody p1 = byJoltId.get(b1.getId());
            PhysBody p2 = byJoltId.get(b2.getId());
            if (p1 == null || p2 == null) {
                return;
            }
            if (!p1.def.notify && !p2.def.notify) {
                return;
            }
            Vec3 v1 = b1.getLinearVelocity();
            Vec3 v2 = b2.getLinearVelocity();
            double rx = v1.getX() - v2.getX();
            double ry = v1.getY() - v2.getY();
            double rz = v1.getZ() - v2.getZ();
            double speed = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (speed < engine.config().contactMinSpeed) {
                return;
            }
            Snapshot s1 = p1.snapshot;
            Snapshot s2 = p2.snapshot;
            if (p1.def.notify) {
                boolean terrain = p2.def.internal;
                double[] normal = contactNormal(s1, s2, terrain);
                double[] point = terrain ? boundaryPoint(s1, new double[]{-normal[0], -normal[1], -normal[2]}) : overlapPoint(s1, s2);
                offer(new PhysEvent(PhysEvent.Kind.COLLIDE, this, p1, terrain ? null : p2, point, normal, speed));
            }
            if (p2.def.notify && !p2.def.internal) {
                boolean terrain = p1.def.internal;
                double[] normal = contactNormal(s2, s1, terrain);
                double[] point = terrain ? boundaryPoint(s2, new double[]{-normal[0], -normal[1], -normal[2]}) : overlapPoint(s1, s2);
                offer(new PhysEvent(PhysEvent.Kind.COLLIDE, this, p2, terrain ? null : p1, point, normal, speed));
            }
        }
        catch (Throwable ignored) {
        }
    }

    private static double[] unit(double x, double y, double z, double[] fallback) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-6) {
            return fallback;
        }
        return new double[]{x / len, y / len, z / len};
    }

    private static double[] contactNormal(Snapshot self, Snapshot other, boolean otherIsTerrain) {
        double[] up = {0, 1, 0};
        if (otherIsTerrain) {
            return unit(-self.vx, -self.vy, -self.vz, up);
        }
        return unit(self.px - other.px, self.py - other.py, self.pz - other.pz, up);
    }

    private static double[] boundaryPoint(Snapshot s, double[] dir) {
        double cx = (s.minX + s.maxX) * 0.5;
        double cy = (s.minY + s.maxY) * 0.5;
        double cz = (s.minZ + s.maxZ) * 0.5;
        double hx = (s.maxX - s.minX) * 0.5;
        double hy = (s.maxY - s.minY) * 0.5;
        double hz = (s.maxZ - s.minZ) * 0.5;
        double t = Double.MAX_VALUE;
        if (Math.abs(dir[0]) > 1.0e-6) {
            t = Math.min(t, hx / Math.abs(dir[0]));
        }
        if (Math.abs(dir[1]) > 1.0e-6) {
            t = Math.min(t, hy / Math.abs(dir[1]));
        }
        if (Math.abs(dir[2]) > 1.0e-6) {
            t = Math.min(t, hz / Math.abs(dir[2]));
        }
        if (t == Double.MAX_VALUE) {
            t = 0;
        }
        return new double[]{cx + dir[0] * t, cy + dir[1] * t, cz + dir[2] * t};
    }

    private static double[] overlapPoint(Snapshot a, Snapshot b) {
        double x0 = Math.max(a.minX, b.minX);
        double x1 = Math.min(a.maxX, b.maxX);
        double y0 = Math.max(a.minY, b.minY);
        double y1 = Math.min(a.maxY, b.maxY);
        double z0 = Math.max(a.minZ, b.minZ);
        double z1 = Math.min(a.maxZ, b.maxZ);
        if (x0 <= x1 && y0 <= y1 && z0 <= z1) {
            return new double[]{(x0 + x1) * 0.5, (y0 + y1) * 0.5, (z0 + z1) * 0.5};
        }
        return new double[]{(a.px + b.px) * 0.5, (a.py + b.py) * 0.5, (a.pz + b.pz) * 0.5};
    }

    private void handleActivation(int joltId, boolean active) {
        PhysBody pb = byJoltId.get(joltId);
        if (pb == null || !pb.def.notify) {
            return;
        }
        offer(new PhysEvent(active ? PhysEvent.Kind.WAKE : PhysEvent.Kind.SLEEP, this, pb, null, null, null, 0));
    }

    void advance(long elapsedNanos) {
        if (destroyed) {
            return;
        }
        drainInbox();
        double dt = 1.0 / stepRate;
        int steps = 0;
        if (pendingSingleSteps > 0) {
            steps = pendingSingleSteps;
            pendingSingleSteps = 0;
            accumulator = 0;
        }
        else if (!paused && timeScale > 0) {
            accumulator += elapsedNanos / 1.0e9 * timeScale;
            int max = engine.config().maxCatchupSteps;
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
            drainInbox();
        }
    }

    private void drainInbox() {
        Runnable task;
        int guard = 0;
        while ((task = inbox.poll()) != null && guard++ < 100000) {
            try {
                task.run();
            }
            catch (Throwable t) {
                log.warning("Physizen task failed in world " + id + ": " + t);
            }
        }
    }

    private void step(float dt) {
        long start = System.nanoTime();
        applyFields(dt);
        queryLock.writeLock().lock();
        try {
            system.update(dt, engine.config().collisionSteps, engine.tempAllocator(), engine.jobSystem());
        }
        finally {
            queryLock.writeLock().unlock();
        }
        stepCount++;
        int active = 0;
        for (PhysBody pb : bodies.values()) {
            publish(pb, false);
            if (pb.snapshot.active) {
                active++;
            }
        }
        activeCount = active;
        lastStepNanos = System.nanoTime() - start;
    }

    void publish(PhysBody pb, boolean force) {
        if (!pb.ready || pb.removed) {
            return;
        }
        boolean active = bodyInterface.isActive(pb.joltId);
        Snapshot previous = pb.snapshot;
        if (!force && !active && !previous.active) {
            return;
        }
        RVec3 p = bodyInterface.getPosition(pb.joltId);
        Quat q = bodyInterface.getRotation(pb.joltId);
        Vec3 v = bodyInterface.getLinearVelocity(pb.joltId);
        Vec3 w = bodyInterface.getAngularVelocity(pb.joltId);
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
        pb.snapshot = new Snapshot(p.xx(), p.yy(), p.zz(), q.getX(), q.getY(), q.getZ(), q.getW(),
                v.getX(), v.getY(), v.getZ(), w.getX(), w.getY(), w.getZ(),
                minX, minY, minZ, maxX, maxY, maxZ, active, stepCount);
        if (pb.def.type != MotionKind.STATIC) {
            if (p.yy() < engine.config().fallOutY) {
                if (!pb.fellOut) {
                    pb.fellOut = true;
                    offer(new PhysEvent(PhysEvent.Kind.FALL_OUT, this, pb, null, new double[]{p.xx(), p.yy(), p.zz()}, null, 0));
                    if (pb.def.removeOnFallOut) {
                        removeBody(pb.name);
                    }
                }
            }
            else {
                pb.fellOut = false;
            }
        }
    }

    private void applyFields(float dt) {
        if (fields.isEmpty()) {
            return;
        }
        for (FieldDef f : fields.values()) {
            for (PhysBody pb : bodies.values()) {
                if (!pb.ready || pb.def.type != MotionKind.DYNAMIC || pb.def.internal) {
                    continue;
                }
                if (f.group >= 0 && pb.def.group != f.group) {
                    continue;
                }
                Snapshot s = pb.snapshot;
                if (!s.active) {
                    continue;
                }
                double mass = f.massIndependent ? Math.max(pb.cachedMass, 0.0001) : 1.0;
                double dx = f.center[0] - s.px;
                double dy = f.center[1] - s.py;
                double dz = f.center[2] - s.pz;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (f.kind != FieldDef.Kind.GRAVITY && f.kind != FieldDef.Kind.BUOYANCY && f.radius > 0 && dist > f.radius) {
                    continue;
                }
                double falloff = f.radius > 0 ? Math.max(0, 1.0 - dist / f.radius) : 1.0;
                double fx = 0;
                double fy = 0;
                double fz = 0;
                switch (f.kind) {
                    case RADIAL: {
                        if (dist < 1.0e-6) {
                            continue;
                        }
                        double k = f.strength * falloff * mass / dist;
                        fx = dx * k;
                        fy = dy * k;
                        fz = dz * k;
                        break;
                    }
                    case GRAVITY: {
                        fx = f.direction[0] * f.strength * mass;
                        fy = f.direction[1] * f.strength * mass;
                        fz = f.direction[2] * f.strength * mass;
                        break;
                    }
                    case WIND: {
                        double wx = f.direction[0] * f.strength;
                        double wy = f.direction[1] * f.strength;
                        double wz = f.direction[2] * f.strength;
                        fx = (wx - s.vx) * f.drag * mass * falloff;
                        fy = (wy - s.vy) * f.drag * mass * falloff;
                        fz = (wz - s.vz) * f.drag * mass * falloff;
                        break;
                    }
                    case VORTEX: {
                        double rx = s.px - f.center[0];
                        double rz = s.pz - f.center[2];
                        double rl = Math.sqrt(rx * rx + rz * rz);
                        if (rl < 1.0e-6) {
                            continue;
                        }
                        double k = f.strength * falloff * mass / rl;
                        fx = -rz * k;
                        fz = rx * k;
                        fx += -rx / rl * f.drag * falloff * mass;
                        fz += -rz / rl * f.drag * falloff * mass;
                        fy = f.direction[1] * f.strength * falloff * mass * 0.25;
                        break;
                    }
                    case DRAG: {
                        double speed = s.speed();
                        fx = -s.vx * speed * f.strength * falloff * mass;
                        fy = -s.vy * speed * f.strength * falloff * mass;
                        fz = -s.vz * speed * f.strength * falloff * mass;
                        break;
                    }
                    case BUOYANCY: {
                        double height = Math.max(s.maxY - s.minY, 0.0001);
                        double submerged = Math.max(0, Math.min(1, (f.surfaceY - s.minY) / height));
                        if (submerged <= 0) {
                            continue;
                        }
                        double volume = Math.max((s.maxX - s.minX) * height * (s.maxZ - s.minZ) * 0.6, 0.0001);
                        double g = Math.abs(gravity[1]);
                        fy = f.density * g * volume * submerged * f.strength;
                        fx = -s.vx * f.drag * submerged * mass;
                        fy += -s.vy * f.drag * submerged * mass;
                        fz = -s.vz * f.drag * submerged * mass;
                        break;
                    }
                    default:
                        break;
                }
                bodyInterface.addForce(pb.joltId, new Vec3((float) fx, (float) fy, (float) fz));
            }
        }
    }

    void destroy() {
        destroyed = true;
        post(() -> {
            for (PhysBody pb : new ArrayList<>(bodies.values())) {
                pb.removed = true;
                destroyBody(pb);
            }
            bodies.clear();
            byJoltId.clear();
            for (Map.Entry<String, Object> entry : constraints.entrySet()) {
                if (entry.getValue() instanceof Constraint) {
                    system.removeConstraint((Constraint) entry.getValue());
                }
            }
            constraints.clear();
            constraintBodies.clear();
            constraintDefs.clear();
        });
        drainInbox();
    }
}