package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.CollisionGroup;
import com.github.stephengold.joltjni.GroupFilterTable;
import com.github.stephengold.joltjni.GroupFilterTableRef;
import com.github.stephengold.joltjni.CustomBodyActivationListener;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Vec3;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.engine.EngineConfig;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.CharacterDef;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.model.FieldDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.validate.Guard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public final class PhysWorld {

    public static final int GROUPS = WorldLayers.GROUPS;

    public final String id;
    public final PhysEngine engine;
    final PhysicsSystem system;
    final BodyInterface bodyInterface;
    final Logger log;
    final ReentrantReadWriteLock queryLock = new ReentrantReadWriteLock();
    final ConcurrentHashMap<Integer, PhysBody> byJoltId = new ConcurrentHashMap<>();
    private final WorldLayers layers = new WorldLayers();
    private final ContactTracker contacts;
    private final CustomBodyActivationListener activationListener;
    private final EventQueue events;
    private final ConcurrentLinkedQueue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, PhysBody> bodies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PhysCharacter> characters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FieldDef> fields = new ConcurrentHashMap<>();
    private final ConstraintService constraintService;
    private final ConcurrentHashMap<String, RagdollFilter> ragdollFilters = new ConcurrentHashMap<>();
    private int nextRagdollGroupId = 1;
    final VehicleService vehicleService;
    private final WorldSpatial spatial;
    private final StepLoop loop;
    private final WorldQueries queries;
    private volatile double[] gravity = {0, -9.81, 0};
    private volatile double timeScale = 1.0;
    private volatile boolean paused = false;
    private volatile int stepRate;
    private volatile boolean destroyed = false;
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
        constraintService = new ConstraintService(this);
        vehicleService = new VehicleService(this);
        spatial = new WorldSpatial(this);
        loop = new StepLoop(this);
        queries = new WorldQueries(this);
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
        return loop.stepCount();
    }

    public double lastStepMillis() {
        return loop.lastStepNanos() / 1_000_000.0;
    }

    public int activeBodyCount() {
        return loop.activeCount();
    }

    public void singleStep(int count) {
        loop.requestSteps(count);
    }

    public WorldQueries queries() {
        return queries;
    }

    public boolean destroyed() {
        return destroyed;
    }

    public PhysicsSystem physicsSystem() {
        return system;
    }

    public void post(Runnable task) {
        inbox.add(task);
    }

    void drainInbox() {
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

    void advance(long elapsedNanos) {
        loop.advance(elapsedNanos);
    }

    void publish(PhysBody pb, boolean force) {
        loop.publish(pb, force);
    }

    Collection<PhysBody> allBodies() {
        return bodies.values();
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

    public List<PhysBody> sensors() {
        List<PhysBody> list = new ArrayList<>();
        for (PhysBody body : bodies.values()) {
            if (!body.def.internal && body.def.sensor) {
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

    public List<PhysBody> sensorContents(PhysBody sensor) {
        return contacts.occupants(sensor);
    }

    public List<PhysBody> sensorsHolding(PhysBody body) {
        return contacts.sensorsHolding(body);
    }

    public List<PhysBody> touching(PhysBody body) {
        return contacts.touching(body);
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
            BodyFactory.instantiate(bodyInterface, pb, ragdollGroup(pb.def));
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
        vehicleService.removeForBody(name);
        constraintService.removeFor(name);
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

    public PhysCharacter character(String name) {
        return characters.get(name);
    }

    public List<PhysCharacter> characters() {
        return new ArrayList<>(characters.values());
    }

    public int characterCount() {
        return characters.size();
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
            ref = new PhysBody(this, innerBodyDef(def));
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
        post(() -> instantiateCharacter(character, refFinal));
        return character;
    }

    private static BodyDef innerBodyDef(CharacterDef def) {
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
        return bd;
    }

    private void instantiateCharacter(PhysCharacter character, PhysBody ref) {
        if (character.isRemoved()) {
            return;
        }
        try {
            character.instantiate(WorldLayers.layerFor(MotionKind.KINEMATIC, false, character.def.group));
            if (ref != null) {
                publish(ref, true);
            }
            character.publish(loop.stepCount());
        }
        catch (Throwable t) {
            characters.remove(character.name, character);
            if (ref != null) {
                bodies.remove(character.name, ref);
                ref.removed = true;
            }
            character.markRemoved();
            log.warning("Failed to create character " + character.name + " in world " + id + ": " + t.getMessage());
        }
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

    public List<FieldDef> fieldDefs() {
        return new ArrayList<>(fields.values());
    }

    static final class RagdollFilter {

        final GroupFilterTable table;
        final GroupFilterTableRef ref;
        final int groupId;

        RagdollFilter(int groupId) {
            this.table = new GroupFilterTable(RagdollSpec.MAX_BONES);
            this.ref = table.toRef();
            this.groupId = groupId;
        }
    }

    CollisionGroup ragdollGroup(BodyDef def) {
        if (def.ragdoll == null || def.ragdoll.isEmpty() || def.ragdollIndex < 0 || def.ragdollIndex >= RagdollSpec.MAX_BONES) {
            return null;
        }
        RagdollFilter f = ragdollFilters.computeIfAbsent(def.ragdoll, k -> new RagdollFilter(nextRagdollGroupId++));
        return new CollisionGroup(f.ref, f.groupId, def.ragdollIndex);
    }

    void disableRagdollCollision(PhysBody a, PhysBody b) {
        if (a == null || b == null || a.def.ragdoll == null || a.def.ragdoll.isEmpty() || !a.def.ragdoll.equals(b.def.ragdoll)) {
            return;
        }
        RagdollFilter f = ragdollFilters.get(a.def.ragdoll);
        if (f == null || a.def.ragdollIndex < 0 || b.def.ragdollIndex < 0) {
            return;
        }
        f.table.disableCollision(a.def.ragdollIndex, b.def.ragdollIndex);
    }

    public List<String> spawnRagdoll(RagdollSpec spec, RagdollBuilder.Options options) {
        checkAlive();
        RagdollBuilder.Result built = RagdollBuilder.build(spec, options);
        for (BodyDef d : built.bodies) {
            if (bodies.containsKey(d.name)) {
                throw new PhysException("Ragdoll part " + d.name + " already exists.");
            }
        }
        List<String> names = new ArrayList<>();
        try {
            for (BodyDef d : built.bodies) {
                createBody(d);
                names.add(d.name);
            }
            for (ConstraintDef c : built.constraints) {
                addConstraint(c);
            }
        }
        catch (RuntimeException e) {
            for (String n : names) {
                removeBody(n);
            }
            throw e;
        }
        return names;
    }

    public List<String> ragdollParts(String id) {
        List<String> names = new ArrayList<>();
        for (PhysBody pb : bodies.values()) {
            if (id.equals(pb.def.ragdoll)) {
                names.add(pb.name);
            }
        }
        java.util.Collections.sort(names);
        return names;
    }

    public List<String> ragdollIds() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (PhysBody pb : bodies.values()) {
            if (pb.def.ragdoll != null && !pb.def.ragdoll.isEmpty()) {
                ids.add(pb.def.ragdoll);
            }
        }
        return new ArrayList<>(ids);
    }

    public int removeRagdoll(String id) {
        List<String> parts = ragdollParts(id);
        for (String n : parts) {
            removeBody(n);
        }
        return parts.size();
    }

    public void addVehicle(dev.egor201.physizen.core.model.VehicleDef def) {
        checkAlive();
        vehicleService.add(def);
    }

    public boolean removeVehicle(String name) {
        return vehicleService.remove(name);
    }

    public dev.egor201.physizen.core.model.VehicleDef vehicleDef(String name) {
        return vehicleService.def(name);
    }

    public List<String> vehicleNames() {
        return vehicleService.names();
    }

    public void setVehicleInput(String name, double forward, double steer, double brake, double handbrake) {
        vehicleService.setInput(name, forward, steer, brake, handbrake);
    }

    public dev.egor201.physizen.core.model.VehicleTelemetry vehicleTelemetry(String name) {
        return vehicleService.telemetry(name);
    }

    public void addConstraint(ConstraintDef def) {
        checkAlive();
        constraintService.add(def);
    }

    public boolean removeConstraint(String name) {
        return constraintService.remove(name);
    }

    public int constraintCount() {
        return constraintService.count();
    }

    public List<String> constraintNames() {
        return constraintService.names();
    }

    public ConstraintDef constraintDef(String name) {
        return constraintService.def(name);
    }

    public void setConstraintMotor(String name, ConstraintDef.MotorState state, Double target, Double maxForceOrTorque, Double springFreq, Double springDamp) {
        constraintService.setMotor(name, state, target, maxForceOrTorque, springFreq, springDamp);
    }

    public Double constraintAngle(String name) {
        return constraintService.hingeAngle(name);
    }

    public Double constraintPosition(String name) {
        return constraintService.sliderPosition(name);
    }

    public void explosion(double[] center, double radius, double impulse, double upward) {
        spatial.explosion(center, radius, impulse, upward);
    }

    public List<PhysBody> inSphere(double[] c, double r) {
        return spatial.inSphere(c, r);
    }

    public List<PhysBody> inBox(double[] min, double[] max) {
        return spatial.inBox(min, max);
    }

    public RayHit rayCast(double[] from, double[] dir, double maxDistance) {
        return spatial.rayCast(from, dir, maxDistance);
    }

    public PhysEvent pollEvent() {
        return events.poll();
    }

    void offerEvent(PhysEvent event) {
        events.offer(event);
    }

    public int pendingEvents() {
        return events.pending();
    }

    public long droppedEvents() {
        return events.droppedTotal();
    }

    public long takeNewDroppedEvents() {
        return events.takeNewDropped();
    }

    private void handleActivation(int joltId, boolean active) {
        PhysBody pb = byJoltId.get(joltId);
        if (pb == null || !pb.def.notify || pb.character != null) {
            return;
        }
        events.offer(new PhysEvent(active ? PhysEvent.Kind.WAKE : PhysEvent.Kind.SLEEP, this, pb, null, null, null, 0));
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
                vehicleService.dropAll();
                constraintService.dropAll();
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
