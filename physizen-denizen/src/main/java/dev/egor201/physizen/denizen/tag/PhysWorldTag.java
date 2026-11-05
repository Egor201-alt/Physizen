package dev.egor201.physizen.denizen.tag;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizencore.objects.Fetchable;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagContext;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.model.FieldDef;
import dev.egor201.physizen.core.model.QueryFilter;
import dev.egor201.physizen.core.model.ShapeSpec;
import dev.egor201.physizen.core.model.VehicleTelemetry;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.world.PhysCharacter;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.core.world.QueryHit;
import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import java.util.List;

// <--[ObjectType]
// @name PhysWorldTag
// @prefix physworld
// @base ElementTag
// @format
// The identity format for PhysWorldTag is the world id, for example "physworld@arena".
//
// @description
// A PhysWorldTag represents a Physizen physics world, a Jolt simulation that runs on its own thread.
// Tags on this object only read published snapshots and are safe to use from async queues.
// -->
public class PhysWorldTag implements ObjectTag {

    public static final ObjectTagProcessor<PhysWorldTag> tagProcessor = new ObjectTagProcessor<>();

    public final String id;

    public PhysWorldTag(String id) {
        this.id = id;
    }

    public PhysWorld world() {
        PhysEngine engine = PhysEngine.get();
        return engine == null ? null : engine.world(id);
    }

    public static boolean matches(String string) {
        return string != null && string.startsWith("physworld@");
    }

    @Fetchable("physworld")
    public static PhysWorldTag valueOf(String string, TagContext context) {
        if (string == null) {
            return null;
        }
        if (string.startsWith("physworld@")) {
            string = string.substring("physworld@".length());
        }
        if (string.isEmpty() || string.contains("/")) {
            return null;
        }
        return new PhysWorldTag(string);
    }

    @Override
    public String identify() {
        return "physworld@" + id;
    }

    @Override
    public String toString() {
        return identify();
    }

    @Override
    public String getPrefix() {
        return "PhysWorld";
    }

    @Override
    public ObjectTag setPrefix(String prefix) {
        return this;
    }

    @Override
    public String debuggable() {
        return "<LG>physworld@<Y>" + id;
    }

    @Override
    public boolean isUnique() {
        return true;
    }

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
    }

    @Override
    public String identifySimple() {
        return identify();
    }

    private static QueryFilter filterOf(ListTag param, int index, TagContext context) {
        QueryFilter filter = new QueryFilter();
        if (param.size() <= index) {
            return filter;
        }
        MapTag options = param.objectForms.get(index).asType(MapTag.class, context);
        if (options == null) {
            return filter;
        }
        if (options.containsKey("ignore")) {
            for (ObjectTag o : options.getObject("ignore").asType(ListTag.class, context).objectForms) {
                filter.ignore.add(o.toString());
            }
        }
        if (options.containsKey("groups")) {
            for (ObjectTag o : options.getObject("groups").asType(ListTag.class, context).objectForms) {
                filter.groups.add((int) Args.num(o, 0));
            }
        }
        filter.sensors = Args.bool(options.getObject("sensors"), false);
        filter.terrain = Args.bool(options.getObject("terrain"), true);
        filter.limit = Math.max(1, (int) Args.num(options.getObject("limit"), 64));
        return filter;
    }

    private static double[] rotationOf(ListTag param, int index, TagContext context) {
        if (param.size() <= index) {
            return null;
        }
        MapTag options = param.objectForms.get(index).asType(MapTag.class, context);
        if (options == null || !options.containsKey("rotation")) {
            return null;
        }
        return Args.rotation(options.getObject("rotation"), context);
    }

    private static MapTag hitMap(String worldId, QueryHit hit) {
        MapTag map = new MapTag();
        if (hit.terrain()) {
            map.putObject("terrain", new ElementTag(true));
        }
        else {
            map.putObject("body", new PhysBodyTag(worldId, hit.body.name));
        }
        map.putObject("point", Args.tagVec(hit.point));
        map.putObject("normal", Args.tagVec(hit.normal));
        map.putObject("distance", new ElementTag(hit.distance));
        map.putObject("fraction", new ElementTag(hit.fraction));
        map.putObject("depth", new ElementTag(hit.depth));
        map.putObject("inside", new ElementTag(hit.inside));
        return map;
    }

    private static ListTag hitList(String worldId, List<QueryHit> hits) {
        ListTag list = new ListTag();
        for (QueryHit hit : hits) {
            list.addObject(hitMap(worldId, hit));
        }
        return list;
    }

    private static PhysWorld need(PhysWorldTag tag) {
        PhysWorld w = tag.world();
        if (w == null) {
            throw new PhysException("Physics world " + tag.id + " no longer exists.");
        }
        return w;
    }

    public static void register() {

        // <--[tag]
        // @attribute <PhysWorldTag.id>
        // @returns ElementTag
        // @description
        // Returns the id of this physics world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "id", (attribute, object) -> new ElementTag(object.id, true));

        // <--[tag]
        // @attribute <PhysWorldTag.exists>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this physics world still exists.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "exists", (attribute, object) -> new ElementTag(object.world() != null));

        // <--[tag]
        // @attribute <PhysWorldTag.paused>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether the simulation of this world is paused.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "paused", (attribute, object) -> new ElementTag(need(object).paused()));

        // <--[tag]
        // @attribute <PhysWorldTag.gravity>
        // @returns LocationTag
        // @description
        // Returns the gravity vector of this world in blocks per second squared.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "gravity", (attribute, object) -> (LocationTag) Args.tagVec(need(object).gravity()));

        // <--[tag]
        // @attribute <PhysWorldTag.time_scale>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the time scale multiplier of this world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "time_scale", (attribute, object) -> new ElementTag(need(object).timeScale()));

        // <--[tag]
        // @attribute <PhysWorldTag.step_rate>
        // @returns ElementTag(Number)
        // @description
        // Returns how many fixed simulation steps per second this world runs.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "step_rate", (attribute, object) -> new ElementTag(need(object).stepRate()));

        // <--[tag]
        // @attribute <PhysWorldTag.step_count>
        // @returns ElementTag(Number)
        // @description
        // Returns how many simulation steps this world has executed.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "step_count", (attribute, object) -> new ElementTag(need(object).stepCount()));

        // <--[tag]
        // @attribute <PhysWorldTag.step_millis>
        // @returns ElementTag(Decimal)
        // @description
        // Returns how many milliseconds the last simulation step took.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "step_millis", (attribute, object) -> new ElementTag(need(object).lastStepMillis()));

        // <--[tag]
        // @attribute <PhysWorldTag.body_count>
        // @returns ElementTag(Number)
        // @description
        // Returns the number of bodies in this world, excluding internal terrain bodies.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "body_count", (attribute, object) -> new ElementTag(need(object).bodyCount()));

        // <--[tag]
        // @attribute <PhysWorldTag.bodies>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns all bodies of this world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "bodies", (attribute, object) -> {
            ListTag list = new ListTag();
            for (PhysBody body : need(object).bodies()) {
                list.addObject(new PhysBodyTag(object.id, body.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.body[<name>]>
        // @returns PhysBodyTag
        // @description
        // Returns the body with the given name in this world, if it exists.
        // @group Physizen
        // -->
        tagProcessor.registerTag(PhysBodyTag.class, ElementTag.class, "body", (attribute, object, param) -> {
            if (param == null) return null;
            PhysBody body = need(object).body(param.asString());
            return body == null || body.isRemoved() ? null : new PhysBodyTag(object.id, body.name);
        });

        // <--[tag]
        // @attribute <PhysWorldTag.constraints>
        // @returns ListTag
        // @description
        // Returns the names of all constraints in this world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "constraints", (attribute, object) -> new ListTag(need(object).constraintNames()));

        // <--[tag]
        // @attribute <PhysWorldTag.constraint_type[<name>]>
        // @returns ElementTag
        // @description
        // Returns the kind of constraint (FIXED, POINT, HINGE, SLIDER, DISTANCE, CONE), or null if not found.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, ElementTag.class, "constraint_type", (attribute, object, param) -> {
            if (param == null) return null;
            ConstraintDef def = need(object).constraintDef(param.asString());
            return def == null ? null : new ElementTag(def.kind.name());
        });

        // <--[tag]
        // @attribute <PhysWorldTag.constraint_angle[<name>]>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the current angle in radians for a hinge constraint, or null if not found/not a hinge.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, ElementTag.class, "constraint_angle", (attribute, object, param) -> {
            if (param == null) return null;
            Double angle = need(object).constraintAngle(param.asString());
            return angle == null ? null : new ElementTag(angle);
        });

        // <--[tag]
        // @attribute <PhysWorldTag.constraint_position[<name>]>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the current translation position in meters for a slider constraint, or null if not found/not a slider.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, ElementTag.class, "constraint_position", (attribute, object, param) -> {
            if (param == null) return null;
            Double pos = need(object).constraintPosition(param.asString());
            return pos == null ? null : new ElementTag(pos);
        });

        // <--[tag]
        // @attribute <PhysWorldTag.constraint_motor[<name>]>
        // @returns MapTag
        // @description
        // Returns motor information (state, target, max_torque/max_force, spring_frequency, spring_damping) for a hinge or slider constraint.
        // @group Physizen
        // -->
        tagProcessor.registerTag(MapTag.class, ElementTag.class, "constraint_motor", (attribute, object, param) -> {
            if (param == null) return null;
            ConstraintDef def = need(object).constraintDef(param.asString());
            if (def == null || (def.kind != ConstraintDef.Kind.HINGE && def.kind != ConstraintDef.Kind.SLIDER)) {
                return null;
            }
            MapTag map = new MapTag();
            map.putObject("state", new ElementTag(def.motorState.name().toLowerCase()));
            map.putObject("target", new ElementTag(def.motorTarget));
            if (def.kind == ConstraintDef.Kind.HINGE) {
                map.putObject("max_torque", new ElementTag(def.motorMaxTorque));
            }
            else {
                map.putObject("max_force", new ElementTag(def.motorMaxForce));
            }
            map.putObject("spring_frequency", new ElementTag(def.motorSpringFrequency));
            map.putObject("spring_damping", new ElementTag(def.motorSpringDamping));
            return map;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.fields>
        // @returns ListTag
        // @description
        // Returns the names of all force fields in this world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "fields", (attribute, object) -> {
            ListTag list = new ListTag();
            for (FieldDef def : need(object).fieldDefs()) {
                list.add(def.name);
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.characters>
        // @returns ListTag(PhysCharacterTag)
        // @description
        // Returns all character controllers in this world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "characters", (attribute, object) -> {
            ListTag list = new ListTag();
            for (PhysCharacter c : need(object).characters()) {
                list.addObject(new PhysCharacterTag(object.id, c.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.character[<name>]>
        // @returns PhysCharacterTag
        // @description
        // Returns the character controller with the given name, or null if there is none.
        // @group Physizen
        // -->
        tagProcessor.registerTag(PhysCharacterTag.class, ElementTag.class, "character", (attribute, object, param) -> {
            if (param == null) return null;
            PhysCharacter c = need(object).character(param.asString());
            if (c == null) return null;
            return new PhysCharacterTag(object.id, c.name);
        });

        // <--[tag]
        // @attribute <PhysWorldTag.sensors>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns all sensor zones in this world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "sensors", (attribute, object) -> {
            ListTag list = new ListTag();
            for (PhysBody body : need(object).sensors()) {
                list.addObject(new PhysBodyTag(object.id, body.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.ragdolls>
        // @returns ListTag
        // @description
        // Returns the ids of all ragdolls in this world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "ragdolls", (attribute, object) -> new ListTag(need(object).ragdollIds()));

        // <--[tag]
        // @attribute <PhysWorldTag.ragdoll_parts[<ragdoll_id>]>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns the bodies that make up the given ragdoll.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ElementTag.class, "ragdoll_parts", (attribute, object, param) -> {
            if (param == null) return null;
            ListTag list = new ListTag();
            for (String name : need(object).ragdollParts(param.asString())) {
                list.addObject(new PhysBodyTag(object.id, name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.vehicles>
        // @returns ListTag
        // @description
        // Returns the names of all vehicles in this world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "vehicles", (attribute, object) -> new ListTag(need(object).vehicleNames()));

        // <--[tag]
        // @attribute <PhysWorldTag.vehicle_info[<vehicle_name>]>
        // @returns MapTag
        // @description
        // Returns live telemetry of the given vehicle: speed, rpm, gear, wheel_count and per-wheel lists wheel_positions (vectors), wheel_rotations (quaternions), steer_angles, spin_angles (radians), suspension_lengths and contacts.
        // @group Physizen
        // -->
        tagProcessor.registerTag(MapTag.class, ElementTag.class, "vehicle_info", (attribute, object, param) -> {
            if (param == null) return null;
            PhysWorld world = need(object);
            VehicleTelemetry t = world.vehicleTelemetry(param.asString());
            if (t == null) {
                return null;
            }
            MapTag map = new MapTag();
            map.putObject("speed", new ElementTag(t.speed));
            map.putObject("rpm", new ElementTag(t.rpm));
            map.putObject("gear", new ElementTag(t.gear));
            map.putObject("wheel_count", new ElementTag(t.wheelCount));
            ListTag positions = new ListTag();
            ListTag rotations = new ListTag();
            ListTag steer = new ListTag();
            ListTag spin = new ListTag();
            ListTag suspension = new ListTag();
            ListTag contacts = new ListTag();
            for (int i = 0; i < t.wheelCount; i++) {
                positions.addObject(Args.tagVec(t.wheelPos[i]));
                double[] q = t.wheelRot[i];
                rotations.addObject(Args.tagQuat(q[0], q[1], q[2], q[3]));
                steer.addObject(new ElementTag(t.steerAngle[i]));
                spin.addObject(new ElementTag(t.spinAngle[i]));
                suspension.addObject(new ElementTag(t.suspensionLength[i]));
                contacts.addObject(new ElementTag(t.contact[i]));
            }
            map.putObject("wheel_positions", positions);
            map.putObject("wheel_rotations", rotations);
            map.putObject("steer_angles", steer);
            map.putObject("spin_angles", spin);
            map.putObject("suspension_lengths", suspension);
            map.putObject("contacts", contacts);
            return map;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.bodies_with_tag[<text>]>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns all bodies whose tag text (the tag: argument of physbody create) equals the given text.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ElementTag.class, "bodies_with_tag", (attribute, object, param) -> {
            if (param == null) return null;
            String wanted = param.asString();
            ListTag list = new ListTag();
            for (PhysBody body : need(object).bodies()) {
                if (wanted.equals(body.tag)) {
                    list.addObject(new PhysBodyTag(object.id, body.name));
                }
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.active_body_count>
        // @returns ElementTag(Number)
        // @description
        // Returns how many bodies are awake in the last simulated step.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "active_body_count", (attribute, object) -> new ElementTag(need(object).activeBodyCount()));

        // <--[tag]
        // @attribute <PhysWorldTag.dropped_events>
        // @returns ElementTag(Number)
        // @description
        // Returns how many physics events were dropped because the event queue was full (see contact_event_queue_limit). A non-zero value means scripts are missing events.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "dropped_events", (attribute, object) -> new ElementTag(need(object).droppedEvents()));

        // <--[tag]
        // @attribute <PhysWorldTag.pending_events>
        // @returns ElementTag(Number)
        // @description
        // Returns how many physics events are waiting to be delivered to scripts.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "pending_events", (attribute, object) -> new ElementTag(need(object).pendingEvents()));

        // <--[tag]
        // @attribute <PhysWorldTag.bodies_in_radius[<center>|<radius>]>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns all bodies whose bounds touch the sphere with the given center and radius.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ListTag.class, "bodies_in_radius", (attribute, object, param) -> {
            if (param == null || param.size() < 2) {
                attribute.echoError("Expected [<center>|<radius>].");
                return null;
            }
            double[] c = Args.vec(param.objectForms.get(0), attribute.context);
            double r = Args.num(param.objectForms.get(1), 1);
            ListTag list = new ListTag();
            for (PhysBody body : need(object).inSphere(c, r)) {
                list.addObject(new PhysBodyTag(object.id, body.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.bodies_in_box[<min>|<max>]>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns all bodies whose bounds overlap the axis-aligned box between the two corners.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ListTag.class, "bodies_in_box", (attribute, object, param) -> {
            if (param == null || param.size() < 2) {
                attribute.echoError("Expected [<min>|<max>].");
                return null;
            }
            double[] a = Args.vec(param.objectForms.get(0), attribute.context);
            double[] b = Args.vec(param.objectForms.get(1), attribute.context);
            double[] min = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
            double[] max = {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
            ListTag list = new ListTag();
            for (PhysBody body : need(object).inBox(min, max)) {
                list.addObject(new PhysBodyTag(object.id, body.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.ray_cast[<origin>|<direction>|(<max_distance>)|(<options>)]>
        // @returns MapTag
        // @description
        // Casts a ray and returns the closest hit as a map with "hit" (boolean) and, when hit, "body" (or "terrain" for terrain), "point", "normal" (real surface normal), "distance", "fraction" and "inside".
        // The max distance defaults to 100. Options is a map with: ignore (list of body names), groups (list of collision groups to hit), sensors (default false), terrain (default true).
        // @group Physizen
        // -->
        tagProcessor.registerTag(MapTag.class, ListTag.class, "ray_cast", (attribute, object, param) -> {
            if (param == null || param.size() < 2) {
                attribute.echoError("Expected [<origin>|<direction>|(<max_distance>)|(<options>)].");
                return null;
            }
            double[] from = Args.vec(param.objectForms.get(0), attribute.context);
            double[] dir = Args.vec(param.objectForms.get(1), attribute.context);
            double max = param.size() >= 3 ? Args.num(param.objectForms.get(2), 100) : 100;
            List<QueryHit> hits = need(object).queries().rayCast(from, dir, max, filterOf(param, 3, attribute.context), false);
            MapTag map = hits.isEmpty() ? new MapTag() : hitMap(object.id, hits.get(0));
            map.putObject("hit", new ElementTag(!hits.isEmpty()));
            return map;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.ray_cast_all[<origin>|<direction>|(<max_distance>)|(<options>)]>
        // @returns ListTag(MapTag)
        // @description
        // Casts a ray and returns every hit along it, nearest first, each as a map with the same keys as ray_cast.
        // Options are the same as ray_cast, plus limit (default 64).
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ListTag.class, "ray_cast_all", (attribute, object, param) -> {
            if (param == null || param.size() < 2) {
                attribute.echoError("Expected [<origin>|<direction>|(<max_distance>)|(<options>)].");
                return null;
            }
            double[] from = Args.vec(param.objectForms.get(0), attribute.context);
            double[] dir = Args.vec(param.objectForms.get(1), attribute.context);
            double max = param.size() >= 3 ? Args.num(param.objectForms.get(2), 100) : 100;
            return hitList(object.id, need(object).queries().rayCast(from, dir, max, filterOf(param, 3, attribute.context), true));
        });

        // <--[tag]
        // @attribute <PhysWorldTag.shape_cast[<shape>|<origin>|<direction>|(<max_distance>)|(<options>)]>
        // @returns MapTag
        // @description
        // Sweeps a shape along a direction and returns the first thing it touches as a map.
        // Options are the same as ray_cast, plus rotation (quaternion). Mesh shapes are not allowed.
        // @group Physizen
        // -->
        tagProcessor.registerTag(MapTag.class, ListTag.class, "shape_cast", (attribute, object, param) -> {
            if (param == null || param.size() < 3) {
                attribute.echoError("Expected [<shape>|<origin>|<direction>|(<max_distance>)|(<options>)].");
                return null;
            }
            try {
                ShapeSpec spec = ShapeParser.shape(param.objectForms.get(0), attribute.context);
                double[] from = Args.vec(param.objectForms.get(1), attribute.context);
                double[] dir = Args.vec(param.objectForms.get(2), attribute.context);
                double max = param.size() >= 4 ? Args.num(param.objectForms.get(3), 100) : 100;
                List<QueryHit> hits = need(object).queries().shapeCast(spec, from, rotationOf(param, 4, attribute.context), dir, max, filterOf(param, 4, attribute.context), false);
                MapTag map = hits.isEmpty() ? new MapTag() : hitMap(object.id, hits.get(0));
                map.putObject("hit", new ElementTag(!hits.isEmpty()));
                return map;
            }
            catch (PhysException e) {
                attribute.echoError(e.getMessage());
                return null;
            }
        });

        // <--[tag]
        // @attribute <PhysWorldTag.shape_cast_all[<shape>|<origin>|<direction>|(<max_distance>)|(<options>)]>
        // @returns ListTag(MapTag)
        // @description
        // Like shape_cast, but returns every hit along the sweep, nearest first.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ListTag.class, "shape_cast_all", (attribute, object, param) -> {
            if (param == null || param.size() < 3) {
                attribute.echoError("Expected [<shape>|<origin>|<direction>|(<max_distance>)|(<options>)].");
                return null;
            }
            try {
                ShapeSpec spec = ShapeParser.shape(param.objectForms.get(0), attribute.context);
                double[] from = Args.vec(param.objectForms.get(1), attribute.context);
                double[] dir = Args.vec(param.objectForms.get(2), attribute.context);
                double max = param.size() >= 4 ? Args.num(param.objectForms.get(3), 100) : 100;
                return hitList(object.id, need(object).queries().shapeCast(spec, from, rotationOf(param, 4, attribute.context), dir, max, filterOf(param, 4, attribute.context), true));
            }
            catch (PhysException e) {
                attribute.echoError(e.getMessage());
                return null;
            }
        });

        // <--[tag]
        // @attribute <PhysWorldTag.overlap[<shape>|<position>|(<options>)]>
        // @returns ListTag(MapTag)
        // @description
        // Returns every body overlapping the shape placed at the position (its center), deepest first.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ListTag.class, "overlap", (attribute, object, param) -> {
            if (param == null || param.size() < 2) {
                attribute.echoError("Expected [<shape>|<position>|(<options>)].");
                return null;
            }
            try {
                ShapeSpec spec = ShapeParser.shape(param.objectForms.get(0), attribute.context);
                double[] pos = Args.vec(param.objectForms.get(1), attribute.context);
                return hitList(object.id, need(object).queries().overlap(spec, pos, rotationOf(param, 2, attribute.context), filterOf(param, 2, attribute.context)));
            }
            catch (PhysException e) {
                attribute.echoError(e.getMessage());
                return null;
            }
        });

        // <--[tag]
        // @attribute <PhysWorldTag.overlap_sphere[<center>|<radius>|(<options>)]>
        // @returns ListTag(MapTag)
        // @description
        // Shortcut for overlap with a sphere. Returns every body touching the sphere, deepest first.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ListTag.class, "overlap_sphere", (attribute, object, param) -> {
            if (param == null || param.size() < 2) {
                attribute.echoError("Expected [<center>|<radius>|(<options>)].");
                return null;
            }
            ShapeSpec spec = new ShapeSpec();
            spec.kind = ShapeSpec.Kind.SPHERE;
            spec.radius = Args.num(param.objectForms.get(1), 1);
            double[] pos = Args.vec(param.objectForms.get(0), attribute.context);
            return hitList(object.id, need(object).queries().overlap(spec, pos, null, filterOf(param, 2, attribute.context)));
        });

        // <--[tag]
        // @attribute <PhysWorldTag.overlap_box[<center>|<size>|(<options>)]>
        // @returns ListTag(MapTag)
        // @description
        // Shortcut for overlap with a box of the given full size. Options may include rotation.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ListTag.class, "overlap_box", (attribute, object, param) -> {
            if (param == null || param.size() < 2) {
                attribute.echoError("Expected [<center>|<size>|(<options>)].");
                return null;
            }
            double[] size = Args.vec(param.objectForms.get(1), attribute.context);
            ShapeSpec spec = new ShapeSpec();
            spec.kind = ShapeSpec.Kind.BOX;
            spec.sizeX = size[0];
            spec.sizeY = size[1];
            spec.sizeZ = size[2];
            double[] pos = Args.vec(param.objectForms.get(0), attribute.context);
            return hitList(object.id, need(object).queries().overlap(spec, pos, rotationOf(param, 2, attribute.context), filterOf(param, 2, attribute.context)));
        });

        // <--[tag]
        // @attribute <PhysWorldTag.saved_prefabs>
        // @returns ListTag
        // @description
        // Returns the list of all saved prefab/body files in plugins/Physizen/saves/.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "saved_prefabs", (attribute, object) -> {
            return new ListTag(dev.egor201.physizen.denizen.save.PrefabFiles.listSavedFiles());
        });
    }
}