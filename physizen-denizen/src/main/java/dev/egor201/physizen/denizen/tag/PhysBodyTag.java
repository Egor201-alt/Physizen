package dev.egor201.physizen.denizen.tag;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

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
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.denizen.DenizenServices;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.core.model.Snapshot;
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
            World w = PhysizenServices.get().worlds.get(object.worldId);
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
            World w = PhysizenServices.get().worlds.get(object.worldId);
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
            World w = PhysizenServices.get().worlds.get(object.worldId);
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
            List<Entity> list = PhysizenServices.get().displays.getBoundDisplays(object.worldId, object.name);
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
            UUID owner = PhysizenServices.get().hitboxes.ownerOf(object.worldId, object.name);
            if (owner == null) owner = PhysizenServices.get().controllers.ownerOf(object.worldId, object.name);
            if (owner == null) return null;
            return new PlayerTag(owner);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.is_sensor>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this body is a sensor zone.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_sensor", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.isSensor());
        });

        // <--[tag]
        // @attribute <PhysBodyTag.sensor_contents>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns the bodies currently inside this sensor zone (characters and player hitboxes included).
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "sensor_contents", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            ListTag list = new ListTag();
            for (PhysBody inside : b.sensorContents()) {
                list.addObject(new PhysBodyTag(object.worldId, inside.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysBodyTag.in_sensors>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns the sensor zones this body is currently inside.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "in_sensors", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            ListTag list = new ListTag();
            for (PhysBody zone : b.sensorsHolding()) {
                list.addObject(new PhysBodyTag(object.worldId, zone.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysBodyTag.is_character>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this body is the inner body of a CharacterVirtual controller (see physcharacter).
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_character", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.character() != null);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.kinetic_energy>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the linear kinetic energy of the body (0.5 * mass * speed^2). Static and kinematic bodies have no mass, so this is 0 for them.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "kinetic_energy", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.kineticEnergy());
        });

        // <--[tag]
        // @attribute <PhysBodyTag.momentum>
        // @returns LocationTag
        // @description
        // Returns the linear momentum vector of the body (mass * velocity).
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "momentum", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            double[] m = b.momentum();
            return new LocationTag((World) null, m[0], m[1], m[2]);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.touching>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns the bodies this body is currently in contact with. Terrain and sensor zones are not listed.
        // Contacts are only tracked for bodies created with notify:true, so this is empty for all other bodies.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "touching", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            ListTag list = new ListTag();
            for (PhysBody other : b.touching()) {
                list.addObject(new PhysBodyTag(object.worldId, other.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysBodyTag.is_touching[<name>]>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this body is currently in contact with the body of the given name in the same world. Requires notify:true on this body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, ElementTag.class, "is_touching", (attribute, object, param) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            String wanted = param.asString();
            for (PhysBody other : b.touching()) {
                if (other.name.equals(wanted)) {
                    return new ElementTag(true);
                }
            }
            return new ElementTag(false);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.friction>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the friction coefficient of the body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "friction", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.def.friction);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.restitution>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the restitution (bounciness) of the body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "restitution", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.def.restitution);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.gravity_factor>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the gravity multiplier of the body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "gravity_factor", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            return new ElementTag(b.def.gravityFactor);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.damping>
        // @returns MapTag
        // @description
        // Returns a map with the 'linear' and 'angular' damping of the body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(MapTag.class, "damping", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            MapTag map = new MapTag();
            map.putObject("linear", new ElementTag(b.def.linearDamping));
            map.putObject("angular", new ElementTag(b.def.angularDamping));
            return map;
        });

        // <--[tag]
        // @attribute <PhysBodyTag.notifies>
        // @returns MapTag
        // @description
        // Returns a map showing which events the body reports: 'contacts' (notify), 'persisted' (notify_persist) and 'sensor' (is a sensor zone).
        // @group Physizen
        // -->
        tagProcessor.registerTag(MapTag.class, "notifies", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            MapTag map = new MapTag();
            map.putObject("contacts", new ElementTag(b.def.notify));
            map.putObject("persisted", new ElementTag(b.def.notifyPersist));
            map.putObject("sensor", new ElementTag(b.def.sensor));
            return map;
        });

        // <--[tag]
        // @attribute <PhysBodyTag.half_extents>
        // @returns LocationTag
        // @description
        // Returns the approximate half size of the body's shape as a vector.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "half_extents", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null) return null;
            double[] h = b.def.shape.halfExtentsEstimate();
            return new LocationTag((World) null, h[0], h[1], h[2]);
        });

        // <--[tag]
        // @attribute <PhysBodyTag.character>
        // @returns PhysCharacterTag
        // @description
        // Returns the character controller this body belongs to, or null if it is a normal body.
        // @group Physizen
        // -->
        tagProcessor.registerTag(PhysCharacterTag.class, "character", (attribute, object) -> {
            PhysBody b = object.body();
            if (b == null || b.character() == null) return null;
            return new PhysCharacterTag(object.worldId, object.name);
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
            return dev.egor201.physizen.denizen.save.SaveManager.serializeBody(object.worldId, b);
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