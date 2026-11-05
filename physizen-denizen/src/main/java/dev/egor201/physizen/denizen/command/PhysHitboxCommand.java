package dev.egor201.physizen.denizen.command;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.Utilities;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultNull;
import com.denizenscript.denizencore.scripts.commands.generator.ArgLinear;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.denizen.DenizenServices;
import dev.egor201.physizen.bukkit.hitbox.HitboxManager;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.core.model.ShapeSpec;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import com.denizenscript.denizen.objects.EntityTag;

import java.util.UUID;

// <--[command]
// @Name physhitbox
// @Syntax physhitbox [attach/detach] (player:<player>) (entity:<entity>) (push:<number>) (world:<physworld>) (name:<name>) (shape:<shape>) (offset:<vector>) (follow_rotation:<true/false>) (group:<0-15>) (sensor:<true/false>) (notify:<true/false>) (friction:<number>) (restitution:<number>) (tag:<text>)
// @Required 1
// @Maximum 18
// @Short Attaches a Jolt hitbox to a player, mob or item entity, or removes it.
// @Group Physizen
// @Description
// Attach creates a kinematic Jolt body that follows the player every tick and pushes dynamic bodies it touches.
// The player defaults to the linked player. The physics world defaults to the one attached to the player's world.
// The shape defaults to capsule:0.3,1.8. The body name defaults to hitbox_<player name>.
// The offset is measured from the player's feet and defaults to the middle of the shape's height.
// Pass entity:<entity> to follow any mob, item or other entity instead of a player. The default name is then hitbox_<uuid>.
// Pass push:<number> to let dynamic bodies that touch the hitbox push the entity (0 disables, 1 is a normal push).
// Detach removes the named hitbox, or all hitboxes of the player if no name is given.
// Hitboxes are removed automatically when the player leaves, changes world, or the physics world is removed.
// @Usage
// Give the linked player a default hitbox.
// - physhitbox attach
// @Usage
// Give a player a box hitbox in a specific physics world.
// - physhitbox attach player:<[p]> world:arena shape:box:0.8,1.8,0.8 follow_rotation:true
// @Usage
// Remove all hitboxes of the linked player.
// - physhitbox detach
// -->
public class PhysHitboxCommand extends AbstractCommand {

    public PhysHitboxCommand() {
        setName("physhitbox");
        setSyntax("physhitbox [attach/detach] (player:<player>) (entity:<entity>) (push:<number>) (world:<physworld>) (name:<name>) (shape:<shape>) (offset:<vector>) (follow_rotation:<true/false>) (group:<0-15>) (sensor:<true/false>) (notify:<true/false>) (friction:<number>) (restitution:<number>) (tag:<text>)");
        setRequiredArguments(1, 18);
        isProcedural = false;
        autoCompile();
        addRemappedPrefixes("name", "body");
    }

    public enum Action {
        ATTACH, DETACH
    }

    public static void autoExecute(ScriptEntry scriptEntry,
            @ArgName("action") @ArgLinear Action action,
            @ArgName("name") @ArgPrefixed @ArgDefaultNull ElementTag nameArg,
            @ArgName("world") @ArgPrefixed @ArgDefaultNull ElementTag worldArg,
            @ArgName("shape") @ArgPrefixed @ArgDefaultNull ObjectTag shapeArg,
            @ArgName("offset") @ArgPrefixed @ArgDefaultNull ObjectTag offsetArg,
            @ArgName("follow_rotation") @ArgPrefixed @ArgDefaultNull ObjectTag followRotationArg,
            @ArgName("friction") @ArgPrefixed @ArgDefaultNull ObjectTag frictionArg,
            @ArgName("restitution") @ArgPrefixed @ArgDefaultNull ObjectTag restitutionArg,
            @ArgName("group") @ArgPrefixed @ArgDefaultNull ObjectTag groupArg,
            @ArgName("sensor") @ArgPrefixed @ArgDefaultNull ObjectTag sensorArg,
            @ArgName("notify") @ArgPrefixed @ArgDefaultNull ObjectTag notifyArg,
            @ArgName("detect_static") @ArgPrefixed @ArgDefaultNull ObjectTag detectStaticArg,
            @ArgName("notify_persist") @ArgPrefixed @ArgDefaultNull ObjectTag notifyPersistArg,
            @ArgName("tag") @ArgPrefixed @ArgDefaultNull ObjectTag tagArg,
            @ArgName("push") @ArgPrefixed @ArgDefaultNull ObjectTag pushArg,
            @ArgName("entity") @ArgPrefixed @ArgDefaultNull ObjectTag entityArg,
            @ArgName("player") @ArgPrefixed @ArgDefaultNull ObjectTag playerArg) {
        ElementTag action = actionArg;
        if (action == null) {
            Debug.echoError(scriptEntry, "Missing action for physhitbox command.");
            return;
        }
        String act = action.name().toLowerCase();

        try {
            Entity subject = resolveSubject(scriptEntry, entityArg, playerArg);
            if (subject == null) {
                Debug.echoError(scriptEntry, "physhitbox needs player:<player>, entity:<entity> or a linked player.");
                return;
            }
            HitboxManager manager = PhysizenServices.get().hitboxes;
            String worldId = (worldArg != null) ? worldArg.asString() : null;
            String name = (nameArg != null) ? nameArg.asString() : null;

            switch (action) {
                case ATTACH: {
                    Entity player = subject;
                    if (!player.isValid() || (player instanceof Player && !((Player) player).isOnline())) {
                        throw new PhysException("The entity is not available.");
                    }
                    if (worldId == null) {
                        worldId = manager.defaultWorldFor(player);
                        if (worldId == null) {
                            throw new PhysException("No physics world is attached to the player's world. Pass world:<id> or create one with attach:<bukkit world>.");
                        }
                    }
                    PhysWorld world = Args.world(worldId);
                    if (name == null) {
                        name = "hitbox_" + (player instanceof Player ? player.getName() : player.getUniqueId().toString().substring(0, 8));
                    }

                    ShapeSpec shape = (shapeArg != null)
                            ? ShapeParser.shape(shapeArg, scriptEntry.context)
                            : ShapeParser.shape(new ElementTag("capsule:0.3,1.8"), scriptEntry.context);
                    double[] offset;
                    if (offsetArg != null) {
                        offset = Args.vec(offsetArg, scriptEntry.context);
                    }
                    else {
                        offset = new double[]{0, shape.halfExtentsEstimate()[1], 0};
                    }
                    boolean follow = Args.bool(followRotationArg, false);

                    Location loc = player.getLocation();
                    BodyDef def = new BodyDef();
                    def.name = name;
                    def.shape = shape;
                    def.type = MotionKind.KINEMATIC;
                    def.position = new double[]{loc.getX() + offset[0], loc.getY() + offset[1], loc.getZ() + offset[2]};
                    def.friction = Args.num(frictionArg, 0.5);
                    def.restitution = Args.num(restitutionArg, 0.0);
                    def.group = (int) Args.num(groupArg, 0);
                    def.sensor = Args.bool(sensorArg, false);
                    def.notify = Args.bool(notifyArg, true);
                    def.detectStatic = Args.bool(detectStaticArg, false);
                    def.notifyPersist = Args.bool(notifyPersistArg, false);
                    def.managed = true;
                    def.allowSleeping = false;
                    def.removeOnFallOut = false;
                    def.tag = Args.str(tagArg, "");

                    manager.detach(player.getUniqueId(), worldId, name);
                    world.createBody(def);
                    manager.register(player.getUniqueId(), worldId, name, offset, follow, Args.num(pushArg, 0));
                    break;
                }
                case DETACH: {
                    UUID uuid = subject.getUniqueId();
                    manager.detach(uuid, worldId, name);
                    break;
                }
                default:
                    Debug.echoError(scriptEntry, "Unknown physhitbox action: " + act);
            }
        }
        catch (PhysException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }


    private static Entity resolveSubject(ScriptEntry scriptEntry, ObjectTag entityArg, ObjectTag playerArg) {
        if (entityArg != null) {
            ObjectTag tag = entityArg;
            EntityTag et = tag instanceof EntityTag ? (EntityTag) tag : EntityTag.valueOf(tag.toString(), scriptEntry.context);
            return et == null ? null : et.getBukkitEntity();
        }
        PlayerTag playerTag = resolvePlayer(scriptEntry, playerArg);
        return playerTag == null ? null : playerTag.getPlayerEntity();
    }

    private static PlayerTag resolvePlayer(ScriptEntry scriptEntry, ObjectTag playerArg) {
        if (playerArg != null) {
            ObjectTag tag = playerArg;
            if (tag instanceof PlayerTag) {
                return (PlayerTag) tag;
            }
            return PlayerTag.valueOf(tag.toString(), scriptEntry.context);
        }
        if (Utilities.entryHasPlayer(scriptEntry)) {
            return Utilities.getEntryPlayer(scriptEntry);
        }
        return null;
    }
}
