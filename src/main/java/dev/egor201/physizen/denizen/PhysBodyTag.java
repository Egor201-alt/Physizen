package dev.egor201.physizen.denizen;

import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.Fetchable;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.QuaternionTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagContext;
import dev.egor201.physizen.Physizen;
import dev.egor201.physizen.core.PhysBody;
import dev.egor201.physizen.core.PhysEngine;
import dev.egor201.physizen.core.PhysWorld;
import dev.egor201.physizen.core.Snapshot;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.UUID;

// <--[ObjectType]
// @name PhysBodyTag
// @prefix physbody
// @base ElementTag
// @format physbody@<world>/<name>
// @description
// Represents a simulated physical body within a Physizen physics world.
// -->
public class PhysBodyTag implements ObjectTag {

    public static ObjectTagProcessor<PhysBodyTag> tagProcessor = new ObjectTagProcessor<>();

    public final String worldId;
    public final String name;
    private String prefix = "PhysBody";

    public PhysBodyTag(String worldId, String name) {
        this.worldId = worldId;
        this.name = name;
    }

    public static boolean matches(String arg) {
        return arg != null && arg.startsWith("physbody@");
    }

    @Fetchable("physbody")
    public static PhysBodyTag valueOf(String string, TagContext context) {
        if (string == null) {
            return null;
        }
        if (string.startsWith("physbody@")) {
            string = string.substring("physbody@".length());
        }
        int slash = string.indexOf('/');
        if (slash == -1) {
            return null;
        }
        String world = string.substring(0, slash);
        String name = string.substring(slash + 1);
        if (world.isEmpty() || name.isEmpty()) {
            return null;
        }
        return new PhysBodyTag(world, name);
    }

    public PhysBody body() {
        PhysEngine engine = PhysEngine.get();
        if (engine == null) return null;
        PhysWorld w = engine.world(worldId);
        return w != null ? w.body(name) : null;
    }

    public static void register() {

        // <--[tag]
        // @attribute <PhysBodyTag.name>
        // @returns ElementTag
        // @description
        // Returns the identifier name of this physics body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "name", (attribute, object) -> {
            return new ElementTag(object.name, true);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.world>
        // @returns PhysWorldTag
        // @description
        // Returns the physics world containing this body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(PhysWorldTag.class, "world", (attribute, object) -> {
            return new PhysWorldTag(object.worldId);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.exists>
        // @returns ElementTag(Boolean)
        // @description
        // Returns true if the body currently exists in its simulation world.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "exists", (attribute, object) -> {
            PhysBody b = object.body();
            return new ElementTag(b != null && !b.isRemoved());
        });

        // <--[tag]
        // @attribute <PhysBodyTag.type>
        // @returns ElementTag
        // @description
        // Returns the motion type of the body: STATIC, KINEMATIC, or DYNAMIC.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "type", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.type().name());
        });

        // <--[tag]
        // @attribute <PhysBodyTag.shape>
        // @returns ElementTag
        // @description
        // Returns a string description of the body's collision shape.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "shape", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.def.shape.describe());
        });

        // <--[tag]
        // @attribute <PhysBodyTag.location>
        // @returns LocationTag
        // @description
        // Returns the current simulated position of the body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "location", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            Snapshot s = b.snapshot;
            World w = Physizen.getInstance().getWorld(object.worldId);
            return new LocationTag(w, s.px, s.py, s.pz);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.rotation>
        // @returns QuaternionTag
        // @description
        // Returns the current orientation of the body as a quaternion.
        // @group Physizen
        // -->
        tagProcessor.registerTag(QuaternionTag.class, "rotation", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            Snapshot s = b.snapshot;
            return new QuaternionTag(s.qx, s.qy, s.qz, s.qw);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.velocity>
        // @returns LocationTag
        // @description
        // Returns the current linear velocity vector of the body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "velocity", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            Snapshot s = b.snapshot;
            return new LocationTag((World) null, s.vx, s.vy, s.vz);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.angular_velocity>
        // @returns LocationTag
        // @description
        // Returns the current angular velocity vector of the body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "angular_velocity", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            Snapshot s = b.snapshot;
            return new LocationTag((World) null, s.wx, s.wy, s.wz);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.speed>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the current scalar linear speed of the body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "speed", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.snapshot.speed());
        });

        // <--[tag]
        // @attribute <PhysBodyTag.is_active>
        // @returns ElementTag(Boolean)
        // @description
        // Returns true if the body is actively simulating (awake), false if sleeping.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_active", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.snapshot.active);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.mass>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the mass of the body in kilograms.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "mass", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.mass());
        });

        // <--[tag]
        // @attribute <PhysBodyTag.tag>
        // @returns ElementTag
        // @description
        // Returns the custom user string tag associated with this body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "tag", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null || b.tag == null) return null;
            return new ElementTag(b.tag);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.bounds_min>
        // @returns LocationTag
        // @description
        // Returns the minimum corner of the body's bounding box.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "bounds_min", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            Snapshot s = b.snapshot;
            World w = Physizen.getInstance().getWorld(object.worldId);
            return new LocationTag(w, s.minX, s.minY, s.minZ);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.bounds_max>
        // @returns LocationTag
        // @description
        // Returns the maximum corner of the body's bounding box.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "bounds_max", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            Snapshot s = b.snapshot;
            World w = Physizen.getInstance().getWorld(object.worldId);
            return new LocationTag(w, s.maxX, s.maxY, s.maxZ);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.group>
        // @returns ElementTag(Number)
        // @description
        // Returns the collision layer/group assigned to this body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "group", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.def.group);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.displays>
        // @returns ListTag
        // @description
        // Returns a list of all display entities bound to this body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "displays", (attribute, object) -> {
            List<Entity> list = Physizen.getInstance().getDisplayBinder().getBoundDisplays(object.worldId, object.name);
            ListTag result = new ListTag();
            for (Entity ent : list) {
                result.addObject(new EntityTag(ent));
            }
            return result;
        });

        // <--[tag]
        // @attribute <PhysBodyTag.player>
        // @returns PlayerTag
        // @description
        // Returns the player this body is attached to as a hitbox (see physhitbox), or null if it is not a player hitbox.
        // @group Physizen
        // -->
        tagProcessor.registerTag(PlayerTag.class, "player", (attribute, object) -> {
            UUID owner = Physizen.getInstance().getHitboxManager().ownerOf(object.worldId, object.name);
            if (owner == null) return null;
            return new PlayerTag(owner);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.to_map>
        // @returns MapTag
        // @description
        // Returns a serializable MapTag containing all definition properties and bound displays of this body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(MapTag.class, "to_map", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return dev.egor201.physizen.SaveManager.serializeBody(object.worldId, b);
        });
    }

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
    }

    @Override
    public String identify() {
        return "physbody@" + worldId + "/" + name;
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
    public boolean isUnique() {
        return true;
    }

    @Override
    public String debuggable() {
        return "<LG>physbody@<Y>" + worldId + "<LG>/<Y>" + name;
    }
}