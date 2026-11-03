package dev.egor201.physizen.denizen.tag;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.Fetchable;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagContext;
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.core.world.CharacterState;
import dev.egor201.physizen.core.world.PhysCharacter;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import org.bukkit.World;

import java.util.UUID;

// <--[ObjectType]
// @name PhysCharacterTag
// @prefix physcharacter
// @base ElementTag
// @format
// The identity format for a PhysCharacterTag is <world_id>/<name>, for example 'main/hero'.
//
// @description
// Represents a CharacterVirtual controller (a capsule that walks over terrain and physics bodies, climbs stairs, stands on moving platforms and pushes dynamic bodies) in a Physizen world.
// A character that has an inner body is also available as a PhysBodyTag with the same name, so it triggers sensor zones.
// -->
public class PhysCharacterTag implements ObjectTag {

    public static ObjectTagProcessor<PhysCharacterTag> tagProcessor = new ObjectTagProcessor<>();

    public final String worldId;
    public final String name;
    private String prefix = "PhysCharacter";

    public PhysCharacterTag(String worldId, String name) {
        this.worldId = worldId;
        this.name = name;
    }

    public static boolean matches(String arg) {
        return arg != null && arg.startsWith("physcharacter@");
    }

    @Fetchable("physcharacter")
    public static PhysCharacterTag valueOf(String string, TagContext context) {
        if (string == null) {
            return null;
        }
        if (string.startsWith("physcharacter@")) {
            string = string.substring("physcharacter@".length());
        }
        int slash = string.indexOf('/');
        if (slash <= 0 || slash == string.length() - 1) {
            return null;
        }
        return new PhysCharacterTag(string.substring(0, slash), string.substring(slash + 1));
    }

    public PhysCharacter character() {
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            return null;
        }
        PhysWorld w = engine.world(worldId);
        return w == null ? null : w.character(name);
    }

    public static void register() {

        // <--[tag]
        // @attribute <PhysCharacterTag.name>
        // @returns ElementTag
        // @description
        // Returns the identifier name of this character.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "name", (attribute, object) -> new ElementTag(object.name));

        // <--[tag]
        // @attribute <PhysCharacterTag.exists>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this character currently exists and is ready.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "exists", (attribute, object) -> {
            PhysCharacter c = object.character();
            return new ElementTag(c != null && c.isReady() && !c.isRemoved());
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.location>
        // @returns LocationTag
        // @description
        // Returns the feet position of this character.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "location", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            CharacterState s = c.state;
            World world = PhysizenServices.get().worlds.get(object.worldId);
            return new LocationTag(world, s.px, s.py, s.pz);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.velocity>
        // @returns LocationTag
        // @description
        // Returns the current velocity vector of this character.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "velocity", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            CharacterState s = c.state;
            return new LocationTag((World) null, s.vx, s.vy, s.vz);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.ground_state>
        // @returns ElementTag
        // @description
        // Returns the ground state: ON_GROUND, ON_STEEP_GROUND, NOT_SUPPORTED or IN_AIR.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "ground_state", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            return new ElementTag(c.state.ground.name());
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.on_ground>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this character stands on walkable ground.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "on_ground", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            return new ElementTag(c.state.onGround());
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.is_supported>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this character is supported by any surface (steep slopes included).
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_supported", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            return new ElementTag(c.state.supported);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.ground_normal>
        // @returns LocationTag
        // @description
        // Returns the normal of the surface under this character.
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "ground_normal", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            double[] n = c.state.groundNormal;
            return new LocationTag((World) null, n[0], n[1], n[2]);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.ground_velocity>
        // @returns LocationTag
        // @description
        // Returns the velocity of the surface under this character (non-zero on moving platforms).
        // @group Physizen
        // -->
        tagProcessor.registerTag(LocationTag.class, "ground_velocity", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            double[] v = c.state.groundVelocity;
            return new LocationTag((World) null, v[0], v[1], v[2]);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.ground_body>
        // @returns PhysBodyTag
        // @description
        // Returns the body this character stands on, or null when standing on terrain or in the air.
        // @group Physizen
        // -->
        tagProcessor.registerTag(PhysBodyTag.class, "ground_body", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null || c.state.groundBody == null) return null;
            return new PhysBodyTag(object.worldId, c.state.groundBody.name);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.mode>
        // @returns ElementTag
        // @description
        // Returns DRIVEN (moved by physcharacter move/jump) or TRACK (follows a player).
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "mode", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            return new ElementTag(c.def.mode.name());
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.body>
        // @returns PhysBodyTag
        // @description
        // Returns the inner body of this character, or null when it was created with inner_body:false.
        // @group Physizen
        // -->
        tagProcessor.registerTag(PhysBodyTag.class, "body", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null || c.body() == null) return null;
            return new PhysBodyTag(object.worldId, object.name);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.player>
        // @returns PlayerTag
        // @description
        // Returns the player this character follows, or null for driven characters.
        // @group Physizen
        // -->
        tagProcessor.registerTag(PlayerTag.class, "player", (attribute, object) -> {
            UUID owner = PhysizenServices.get().controllers.ownerOf(object.worldId, object.name);
            if (owner == null) return null;
            return new PlayerTag(owner);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.speed>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the full speed of this character.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "speed", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            return new ElementTag(c.state.speed());
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.horizontal_speed>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the speed of this character on the horizontal plane.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "horizontal_speed", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            CharacterState s = c.state;
            return new ElementTag(Math.sqrt(s.vx * s.vx + s.vz * s.vz));
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.is_falling>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this character is in the air and moving downwards.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_falling", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            CharacterState s = c.state;
            return new ElementTag(s.ground == CharacterState.Ground.IN_AIR && s.vy < -0.01);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.is_rising>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this character is in the air and moving upwards (for example during a jump).
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_rising", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            CharacterState s = c.state;
            return new ElementTag(s.ground == CharacterState.Ground.IN_AIR && s.vy > 0.01);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.is_tracking>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this character follows a player (mode TRACK) instead of being driven by scripts.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "is_tracking", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            return new ElementTag(c.def.mode == dev.egor201.physizen.core.model.CharacterDef.Mode.TRACK);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.mass>
        // @returns ElementTag(Decimal)
        // @description
        // Returns the mass used when this character pushes bodies.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "mass", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            return new ElementTag(c.def.mass);
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.shape>
        // @returns ElementTag
        // @description
        // Returns a short description of the character's shape, for example capsule[h=1.800,r=0.300].
        // @group Physizen
        // -->
        tagProcessor.registerTag(ElementTag.class, "shape", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null) return null;
            return new ElementTag(c.def.shape.describe());
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.sensors>
        // @returns ListTag(PhysBodyTag)
        // @description
        // Returns the sensor zones this character is currently inside. Needs inner_body:true.
        // @group Physizen
        // -->
        tagProcessor.registerTag(ListTag.class, "sensors", (attribute, object) -> {
            PhysCharacter c = object.character();
            if (c == null || c.body() == null) return null;
            ListTag list = new ListTag();
            for (dev.egor201.physizen.core.world.PhysBody zone : c.body().sensorsHolding()) {
                list.addObject(new PhysBodyTag(object.worldId, zone.name));
            }
            return list;
        });

        // <--[tag]
        // @attribute <PhysCharacterTag.world>
        // @returns PhysWorldTag
        // @description
        // Returns the physics world this character lives in.
        // @group Physizen
        // -->
        tagProcessor.registerTag(PhysWorldTag.class, "world", (attribute, object) -> new PhysWorldTag(object.worldId));
    }

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
    }

    @Override
    public String identify() {
        return "physcharacter@" + worldId + "/" + name;
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
        return "<LG>physcharacter@<Y>" + worldId + "<LG>/<Y>" + name;
    }
}
