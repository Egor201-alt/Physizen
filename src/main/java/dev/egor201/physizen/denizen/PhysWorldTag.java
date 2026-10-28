package dev.egor201.physizen.denizen;

import com.denizenscript.denizencore.objects.Fetchable;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import dev.egor201.physizen.core.FieldDef;
import dev.egor201.physizen.core.PhysBody;
import dev.egor201.physizen.core.PhysEngine;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.PhysWorld;
import dev.egor201.physizen.core.RayHit;

public class PhysWorldTag implements ObjectTag {

    // <--[ObjectType]
    // @name PhysWorldTag
    // @prefix physworld
    // @base ElementTag
    // @format
    // The identity format for PhysWorldTag is the id of the physics world, for example "physworld@arena".
    //
    // @description
    // A PhysWorldTag represents a Physizen physics world, a Jolt simulation that runs on its own thread.
    // Tags on this object only read published snapshots and are safe to use from async queues.
    // -->

    public final String id;
    private String prefix = "PhysWorld";

    public PhysWorldTag(String id) {
        this.id = id;
    }

    public PhysWorld world() {
        PhysEngine engine = PhysEngine.get();
        return engine == null ? null : engine.world(id);
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
        PhysEngine engine = PhysEngine.get();
        if (engine == null || engine.world(string) == null) {
            return null;
        }
        return new PhysWorldTag(string);
    }

    public static boolean matches(String string) {
        if (CoreUtilities.toLowerCase(string).startsWith("physworld@")) {
            return true;
        }
        return valueOf(string, CoreUtilities.noDebugContext) != null;
    }

    @Override
    public String identify() {
        return "physworld@" + id;
    }

    @Override
    public String identifySimple() {
        return identify();
    }

    @Override
    public String toString() {
        return identify();
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public ObjectTag setPrefix(String prefix) {
        this.prefix = prefix;
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

    public static ObjectTagProcessor<PhysWorldTag> tagProcessor = new ObjectTagProcessor<>();

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
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
        tagProcessor.registerTag(ObjectTag.class, "gravity", (attribute, object) -> Util.tagVec(need(object).gravity()));

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
        // @attribute <PhysWorldTag.bodies_in_radius[<center>|<radius>]>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns all bodies whose bounds touch the sphere with the given center and radius.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, ListTag.class, "bodies_in_radius", (attribute, object, param) -> {
            if (param.size() < 2) {
                attribute.echoError("Expected [<center>|<radius>].");
                return null;
            }
            double[] c = Util.vec(param.objectForms.get(0), attribute.context);
            double r = Util.num(param.objectForms.get(1), 1);
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
            if (param.size() < 2) {
                attribute.echoError("Expected [<min>|<max>].");
                return null;
            }
            double[] a = Util.vec(param.objectForms.get(0), attribute.context);
            double[] b = Util.vec(param.objectForms.get(1), attribute.context);
            double[] min = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
            double[] max = {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
            ListTag list = new ListTag();
            for (PhysBody body : need(object).inBox(min, max)) {
                list.addObject(new PhysBodyTag(object.id, body.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysWorldTag.ray_cast[<origin>|<direction>|(<max_distance>)]>
        // @returns MapTag
        // @description
        // Casts a ray through this world and returns a map with the keys "hit" (boolean), and when hit "body", "point", "normal", "distance" and "fraction".
        // The max distance defaults to 100.
        // @group Physizen
        // -->
        tagProcessor.registerTag(MapTag.class, ListTag.class, "ray_cast", (attribute, object, param) -> {
            if (param.size() < 2) {
                attribute.echoError("Expected [<origin>|<direction>|(<max_distance>)].");
                return null;
            }
            double[] from = Util.vec(param.objectForms.get(0), attribute.context);
            double[] dir = Util.vec(param.objectForms.get(1), attribute.context);
            double max = param.size() >= 3 ? Util.num(param.objectForms.get(2), 100) : 100;
            RayHit hit = need(object).rayCast(from, dir, max);
            MapTag map = new MapTag();
            map.putObject("hit", new ElementTag(hit != null));
            if (hit != null) {
                if (hit.body != null && !hit.body.def.internal) {
                    map.putObject("body", new PhysBodyTag(object.id, hit.body.name));
                }
                map.putObject("point", Util.tagVec(hit.point));
                map.putObject("normal", Util.tagVec(hit.normal));
                map.putObject("distance", new ElementTag(hit.distance));
                map.putObject("fraction", new ElementTag(hit.fraction));
            }
            return map;
        });
    }
}
