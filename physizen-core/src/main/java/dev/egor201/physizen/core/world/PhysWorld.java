package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Constraint;
import com.github.stephengold.joltjni.CustomBodyActivationListener;
import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.constraint.ConstraintFactory;
import dev.egor201.physizen.core.constraint.ConstraintMotors;
import dev.egor201.physizen.core.engine.EngineConfig;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.CharacterDef;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.model.FieldDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.model.QueryFilter;
import dev.egor201.physizen.core.model.ShapeSpec;
import dev.egor201.physizen.core.model.Snapshot;
import dev.egor201.physizen.core.validate.Guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public final class PhysWorld {

    public static final int GROUPS = WorldLayers.GROUPS;

    private static final class ConstraintEntry {

        final ConstraintDef def;
        volatile Constraint constraint;

        ConstraintEntry(ConstraintDef def) {
            this.def = def;
        }
    }

    public final String id;
    public final PhysEngine engine;
    final PhysicsSystem system;
    final BodyInterface bodyInterface;
    private final WorldLayers layers = new WorldLayers();
    private final ContactTracker contacts;
    private final CustomBodyActivationListener activationListener;
    private final EventQueue events;
    private final ConcurrentLinkedQueue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, PhysBody> bodies = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Integer, PhysBody> byJoltId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConstraintEntry> constraints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PhysCharacter> characters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FieldDef> fields = new ConcurrentHashMap<>();
    final ReentrantReadWriteLock queryLock = new ReentrantReadWriteLock();
    private final WorldQueries queries = new WorldQueries(this);
    private final Logger log;
    private volatile double[] gravity = {0, -9.81, 0};
    private volatile double timeScale = 1.0;
    private volatile boolean paused = false;
    private volatile int stepRate;
    private volatile long stepCount = 0;
    private volatile long lastStepNanos = 0;
    private volatile int activeCount = 0;
    private volatile boolean destroyed = false;
    private int stepFailures = 0;
    private double accumulator = 0;
    private volatile int pendingSingleSteps = 0;
    public volatile Object attachment;

    PhysWorld(String id, PhysEngine engine, Logger log, int stepRate) {
        this.id = id;
        this.engine = engine;
        this.log = log;
        this.stepRate = stepRate;
        EngineConfig cfg = engine.config();
        events = new EventQueue(cfg.contactEventQueueLimit);
        system = new PhysicsSystem();
        system.init(cfg.maxBodies, 0, cfg.maxBodyPairs, cfg.maxContactConstraints, layers.layerMap, layers.objVsBp, layers.pairFilter);
        system.setGravity(new Vec3(0, -9.81f, 0));
        bodyInterface = system.getBodyInterface();
        contacts = new ContactTracker(this, cfg, events);
        system.setContactListener(contacts);
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
        Guard.vec(g, "g");
        double[] copy = g.clone();
        gravity = copy;
        post(() -> system.setGravity(new Vec3((float) copy[0], (float) copy[1], (float) copy[2])));
    }

    public double timeScale() {
        return timeScale;
    }

    public void setTimeScale(double scale) {
        Guard.number(scale, "scale");
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

    public int characterCount() {
        return characters.size();
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

    public PhysicsSystem physicsSystem() {
        return system;
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

    public PhysBody bodyByJoltId(int joltId) {
        return byJoltId.get(joltId);
    }

    public List<String> constraintNames() {
        return new ArrayList<>(constraints.keySet());
    }

    public ConstraintDef constraintDef(String name) {
        ConstraintEntry entry = constraints.get(name);
        return entry == null ? null : entry.def;
    }

    public List<FieldDef> fieldDefs() {
        return new ArrayList<>(fields.values());
    }

    public PhysCharacter character(String name) {
        return characters.get(name);
    }

    public List<PhysCharacter> characters() {
        return new ArrayList<>(characters.values());
    }

    public List<PhysBody> sensorContents(PhysBody sensor) {
        return contacts.occupants(sensor);
    }

    public List<PhysBody> sensorsHolding(PhysBody body) {
        return contacts.sensorsHolding(body);
    }

    public void setGroupCollision(int a, int b, boolean enabled) {
        WorldLayers.checkGroup(a);
        WorldLayers.checkGroup(b);
        post(() -> layers.setCollision(a, b, enabled));
    }

    private void checkAlive() {
        if (destroyed) {
            throw new PhysException("World " + id + " was removed.");
        }
    }

    public PhysBody createBody(BodyDef def) {
        checkAlive();
        if (def.name == null || def.name.isEmpty()) {
            throw new PhysException("Body needs a name.");
        }
        if (def.shape == null) {
            throw new PhysException("Body needs a shape.");
        }
        if (def.shape.requiresNonDynamic() && def.type == MotionKind.DYNAMIC) {
            throw new PhysException("Mesh shapes can only be used by static or kinematic bodies.");
        }
        WorldLayers.checkGroup(def.group);
        EngineConfig cfg = engine.config();
        Guard.validateDef(def, def.internal ? cfg.maxInternalCompoundChildren : cfg.maxCompoundChildren);
        if (!def.internal && bodies.size() >= cfg.maxBodiesPerWorld) {
            throw new PhysException("World " + id + " reached the body limit (" + cfg.maxBodiesPerWorld + ").");
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
            BodyFactory.instantiate(bodyInterface, pb);
            byJoltId.put(pb.joltId, pb);
            BodyFactory.activate(bodyInterface, pb);
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
        PhysBody pb = bodies.get(name);
        if (pb == null) {
            return;
        }
        if (pb.character != null) {
            removeCharacter(name);
            return;
        }
        if (!bodies.remove(name, pb)) {
            return;
        }
        pb.removed = true;
        for (Map.Entry<String, ConstraintEntry> entry : new ArrayList<>(constraints.entrySet())) {
            ConstraintDef d = entry.getValue().def;
            if (name.equals(d.bodyA) || name.equals(d.bodyB)) {
                removeConstraint(entry.getKey());
            }
        }
        post(() -> destroyBody(pb));
    }

    private void destroyBody(PhysBody pb) {
        contacts.purge(pb);
        try {
            if (pb.joltId >= 0 && pb.ready) {
                pb.ready = false;
                byJoltId.remove(pb.joltId);
                bodyInterface.removeBody(pb.joltId);
                bodyInterface.destroyBody(pb.joltId);
            }
        }
        catch (Throwable t) {
            log.warning("Failed to destroy body " + pb.name + ": " + t.getMessage());
        }
        pb.body = null;
    }

    public void clear() {
        for (PhysBody pb : new ArrayList<>(bodies.values())) {
            if (!pb.def.internal) {
                removeBody(pb.name);
            }
        }
    }

    public PhysCharacter createCharacter(CharacterDef def) {
        checkAlive();
        EngineConfig cfg = engine.config();
        Guard.validateCharacter(def, cfg.maxCompoundChildren);
        WorldLayers.checkGroup(def.group);
        if (characters.size() >= cfg.maxCharactersPerWorld) {
            throw new PhysException("World " + id + " reached the character limit (" + cfg.maxCharactersPerWorld + ").");
        }
        PhysBody ref = null;
        if (def.innerBody) {
            BodyDef bd = new BodyDef();
            bd.name = def.name;
            bd.shape = def.shape;
            bd.type = MotionKind.KINEMATIC;
            bd.position = new double[]{def.position[0], def.position[1] + def.footOffset(), def.position[2]};
            bd.rotation = def.rotation.clone();
            bd.group = def.group;
            bd.notify = def.notify;
            bd.managed = true;
            bd.removeOnFallOut = false;
            bd.allowSleeping = false;
            bd.tag = def.tag;
            ref = new PhysBody(this, bd);
            if (bodies.putIfAbsent(def.name, ref) != null) {
                throw new PhysException("Body " + def.name + " already exists in world " + id + ".");
            }
        }
        else if (bodies.containsKey(def.name)) {
            throw new PhysException("Body " + def.name + " already exists in world " + id + ".");
        }
        PhysCharacter character = new PhysCharacter(this, def, ref);
        if (ref != null) {
            ref.character = character;
        }
        if (characters.putIfAbsent(def.name, character) != null) {
            if (ref != null) {
                bodies.remove(def.name, ref);
            }
            throw new PhysException("Character " + def.name + " already exists in world " + id + ".");
        }
        PhysBody refFinal = ref;
        post(() -> {
            if (character.isRemoved()) {
                return;
            }
            try {
                character.instantiate(WorldLayers.layerFor(MotionKind.KINEMATIC, false, def.group));
                if (refFinal != null) {
                    publish(refFinal, true);
                }
                character.publish(stepCount);
            }
            catch (Throwable t) {
                characters.remove(def.name, character);
                if (refFinal != null) {
                    bodies.remove(def.name, refFinal);
                    refFinal.removed = true;
                }
                character.markRemoved();
                log.warning("Failed to create character " + def.name + " in world " + id + ": " + t.getMessage());
            }
        });
        return character;
    }

    public void removeCharacter(String name) {
        PhysCharacter c = characters.remove(name);
        if (c == null) {
            return;
        }
        c.markRemoved();
        PhysBody ref = c.body();
        if (ref != null) {
            bodies.remove(name, ref);
            ref.removed = true;
        }
        post(() -> {
            if (ref != null) {
                contacts.purge(ref);
            }
            c.destroy();
        });
    }

    public void addField(FieldDef def) {
        fields.put(def.name, def);
    }

    public boolean removeField(String name) {
        return fields.remove(name) != null;
    }

    public void addConstraint(ConstraintDef def) {
        checkAlive();
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
        if ((a != null && a.character != null) || (b != null && b.character != null)) {
            throw new PhysException("Characters cannot be used in constraints.");
        }
        Guard.validateConstraint(def);
        if (constraints.size() >= engine.config().maxConstraintsPerWorld) {
            throw new PhysException("World " + id + " reached the constraint limit (" + engine.config().maxConstraintsPerWorld + ").");
        }
        def.captureLocal(a == null ? null : a.snapshot, b == null ? null : b.snapshot);
        ConstraintEntry entry = new ConstraintEntry(def);
        constraints.put(def.name, entry);
        post(() -> instantiateConstraint(entry, a, b));
    }

    private static boolean unusable(PhysBody pb) {
        return pb != null && (pb.removed || !pb.ready || pb.body == null);
    }

    private void instantiateConstraint(ConstraintEntry entry, PhysBody a, PhysBody b) {
        ConstraintDef def = entry.def;
        if (unusable(a) || unusable(b)) {
            constraints.remove(def.name, entry);
            log.warning("Constraint " + def.name + " was dropped because one of its bodies is gone.");
            return;
        }
        try {
            Body body1 = a != null ? a.body : Body.sFixedToWorld();
            Body body2 = b != null ? b.body : Body.sFixedToWorld();
            TwoBodyConstraint made = ConstraintFactory.create(def, body1, body2);
            system.addConstraint(made);
            entry.constraint = made;
        }
        catch (Throwable t) {
            constraints.remove(def.name, entry);
            log.warning("Failed to create constraint " + def.name + ": " + t.getMessage());
        }
    }

    public void setConstraintMotor(String name, ConstraintDef.MotorState state, Double target, Double maxForceOrTorque, Double springFreq, Double springDamp) {
        post(() -> {
            ConstraintEntry entry = constraints.get(name);
            if (entry == null || entry.constraint == null) {
                return;
            }
            ConstraintMotors.update(entry.constraint, entry.def, state, target, maxForceOrTorque, springFreq, springDamp);
            wakeConstraintBodies(entry.def);
        });
    }

    private void wakeConstraintBodies(ConstraintDef def) {
        for (String bName : new String[]{def.bodyA, def.bodyB}) {
            if (bName == null) {
                continue;
            }
            PhysBody pb = bodies.get(bName);
            if (pb != null && pb.ready && !pb.removed && pb.def.type != MotionKind.STATIC) {
                bodyInterface.activateBody(pb.joltId);
            }
        }
    }

    public Double constraintAngle(String name) {
        ConstraintEntry entry = constraints.get(name);
        if (entry == null || !(entry.constraint instanceof HingeConstraint)) {
            return null;
        }
        queryLock.readLock().lock();
        try {
            return (double) ((HingeConstraint) entry.constraint).getCurrentAngle();
        }
        catch (Throwable t) {
            return null;
        }
        finally {
            queryLock.readLock().unlock();
        }
    }

    public Double constraintPosition(String name) {
        ConstraintEntry entry = constraints.get(name);
        if (entry == null || !(entry.constraint instanceof SliderConstraint)) {
            return null;
        }
        queryLock.readLock().lock();
        try {
            return (double) ((SliderConstraint) entry.constraint).getCurrentPosition();
        }
        catch (Throwable t) {
            return null;
        }
        finally {
            queryLock.readLock().unlock();
        }
    }

    public boolean removeConstraint(String name) {
        if (!constraints.containsKey(name)) {
            return false;
        }
        post(() -> dropConstraint(name));
        return true;
    }

    private void dropConstraint(String name) {
        ConstraintEntry entry = constraints.remove(name);
        if (entry != null && entry.constraint != null) {
            try {
                system.removeConstraint(entry.constraint);
            }
            catch (Throwable t) {
                log.warning("Failed to remove constraint " + name + ": " + t.getMessage());
            }
        }
    }

    public void explosion(double[] center, double radius, double impulse, double upward) {
        Guard.position(center, "center");
        Guard.number(radius, "radius");
        Guard.number(impulse, "impulse");
        Guard.number(upward, "upward");
        for (PhysBody pb : bodies.values()) {
            if (!pb.ready || pb.def.internal || (pb.def.type != MotionKind.DYNAMIC && pb.character == null)) {
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
            pb.addImpulse(new double[]{dx * k, dy * k + upward * impulse * falloff, dz * k});
        }
    }

    public List<PhysBody> inSphere(double[] c, double r) {
        Guard.position(c, "c");
        Guard.number(r, "r");
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
        Guard.position(min, "min");
        Guard.position(max, "max");
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
        return events.poll();
    }

    private void handleActivation(int joltId, boolean active) {
        PhysBody pb = byJoltId.get(joltId);
        if (pb == null || !pb.def.notify || pb.character != null) {
            return;
        }
        events.offer(new PhysEvent(active ? PhysEvent.Kind.WAKE : PhysEvent.Kind.SLEEP, this, pb, null, null, null, 0));
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
        try {
            FieldSolver.apply(fields.values(), bodies.values(), bodyInterface, gravity[1]);
        }
        catch (Throwable t) {
            log.warning("Field update failed in world " + id + ": " + t);
        }
        boolean failed = false;
        double[] g = gravity;
        queryLock.writeLock().lock();
        try {
            for (PhysCharacter c : characters.values()) {
                try {
                    c.prePhysics(dt, g);
                }
                catch (Throwable t) {
                    log.warning("Character " + c.name + " update failed in world " + id + ": " + t);
                }
            }
            system.update(dt, engine.config().collisionSteps, engine.tempAllocator(), engine.jobSystem());
        }
        catch (Throwable t) {
            failed = true;
            log.severe("Physics step failed in world " + id + ": " + t);
        }
        finally {
            queryLock.writeLock().unlock();
        }
        if (failed) {
            if (++stepFailures >= 3) {
                paused = true;
                stepFailures = 0;
                log.severe("World " + id + " was paused after repeated physics errors. Fix the cause and use physworld resume.");
            }
            return;
        }
        stepFailures = 0;
        stepCount++;
        publishAll();
        lastStepNanos = System.nanoTime() - start;
    }

    private void publishAll() {
        int active = 0;
        List<String> broken = null;
        for (PhysBody pb : bodies.values()) {
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
        for (PhysCharacter c : characters.values()) {
            c.publish(stepCount);
        }
        activeCount = active;
        if (broken != null) {
            for (String name : broken) {
                log.warning("Body " + name + " in world " + id + " reached an invalid position and was removed.");
                removeBody(name);
            }
        }
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
            if (p.yy() < engine.config().fallOutY) {
                if (!pb.fellOut) {
                    pb.fellOut = true;
                    events.offer(new PhysEvent(PhysEvent.Kind.FALL_OUT, this, pb, null, new double[]{p.xx(), p.yy(), p.zz()}, null, 0));
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

    void destroy() {
        destroyed = true;
        post(() -> {
            queryLock.writeLock().lock();
            try {
                for (PhysCharacter c : new ArrayList<>(characters.values())) {
                    c.markRemoved();
                    c.destroy();
                }
                characters.clear();
                for (String name : new ArrayList<>(constraints.keySet())) {
                    dropConstraint(name);
                }
                for (PhysBody pb : new ArrayList<>(bodies.values())) {
                    pb.removed = true;
                    if (pb.character == null) {
                        destroyBody(pb);
                    }
                }
                bodies.clear();
                byJoltId.clear();
                contacts.clear();
                events.clear();
            }
            finally {
                queryLock.writeLock().unlock();
            }
        });
        drainInbox();
    }
}
