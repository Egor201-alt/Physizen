package dev.egor201.physizen.denizen.command;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.Utilities;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.ObjectTag;
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
import org.bukkit.entity.Player;

import java.util.UUID;

// <--[command]
// @Name physhitbox
// @Syntax physhitbox [attach/detach] (player:<player>) (world:<physworld>) (name:<name>) (shape:<shape>) (offset:<vector>) (follow_rotation:<true/false>) (group:<0-15>) (sensor:<true/false>) (notify:<true/false>) (friction:<number>) (restitution:<number>) (tag:<text>)
// @Required 1
// @Maximum 16
// @Short Attaches a Jolt hitbox to a player or removes it.
// @Group Physizen
// @Description
// Attach creates a kinematic Jolt body that follows the player every tick and pushes dynamic bodies it touches.
// The player defaults to the linked player. The physics world defaults to the one attached to the player's world.
// The shape defaults to capsule:0.3,1.8. The body name defaults to hitbox_<player name>.
// The offset is measured from the player's feet and defaults to the middle of the shape's height.
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
        setSyntax("physhitbox [attach/detach] (player:<player>) (world:<physworld>) (name:<name>) (shape:<shape>) (offset:<vector>) (follow_rotation:<true/false>) (group:<0-15>) (sensor:<true/false>) (notify:<true/false>) (friction:<number>) (restitution:<number>) (tag:<text>)");
        setRequiredArguments(1, 16);
        isProcedural = false;
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action")) {
                scriptEntry.addObject("action", arg.asElement());
            }
            else if (!scriptEntry.hasObject("name") && arg.matchesPrefix("name", "body")) {
                scriptEntry.addObject("name", arg.asElement());
            }
            else if (!scriptEntry.hasObject("world") && arg.matchesPrefix("world")) {
                scriptEntry.addObject("world", arg.asElement());
            }
            else {
                scriptEntry.addObject(arg.prefix != null ? arg.prefix : arg.getValue(), arg.object);
            }
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        ElementTag action = scriptEntry.getElement("action");
        if (action == null) {
            Debug.echoError(scriptEntry, "Missing action for physhitbox command.");
            return;
        }
        String act = action.asString().toLowerCase();

        try {
            PlayerTag playerTag = resolvePlayer(scriptEntry);
            if (playerTag == null) {
                Debug.echoError(scriptEntry, "physhitbox needs player:<player> or a linked player.");
                return;
            }
            HitboxManager manager = PhysizenServices.get().hitboxes;
            String worldId = scriptEntry.hasObject("world") ? scriptEntry.getElement("world").asString() : null;
            String name = scriptEntry.hasObject("name") ? scriptEntry.getElement("name").asString() : null;

            switch (act) {
                case "attach": {
                    Player player = playerTag.getPlayerEntity();
                    if (player == null || !player.isOnline()) {
                        throw new PhysException("Player is not online.");
                    }
                    if (worldId == null) {
                        worldId = manager.defaultWorldFor(player);
                        if (worldId == null) {
                            throw new PhysException("No physics world is attached to the player's world. Pass world:<id> or create one with attach:<bukkit world>.");
                        }
                    }
                    PhysWorld world = Args.world(worldId);
                    if (name == null) {
                        name = "hitbox_" + player.getName();
                    }

                    ShapeSpec shape = scriptEntry.hasObject("shape")
                            ? ShapeParser.shape(scriptEntry.getObjectTag("shape"), scriptEntry.context)
                            : ShapeParser.shape(new ElementTag("capsule:0.3,1.8"), scriptEntry.context);
                    double[] offset;
                    if (scriptEntry.hasObject("offset")) {
                        offset = Args.vec(scriptEntry.getObjectTag("offset"), scriptEntry.context);
                    }
                    else {
                        offset = new double[]{0, shape.halfExtentsEstimate()[1], 0};
                    }
                    boolean follow = Args.bool(scriptEntry.getObjectTag("follow_rotation"), false);

                    Location loc = player.getLocation();
                    BodyDef def = new BodyDef();
                    def.name = name;
                    def.shape = shape;
                    def.type = MotionKind.KINEMATIC;
                    def.position = new double[]{loc.getX() + offset[0], loc.getY() + offset[1], loc.getZ() + offset[2]};
                    def.friction = Args.num(scriptEntry.getObjectTag("friction"), 0.5);
                    def.restitution = Args.num(scriptEntry.getObjectTag("restitution"), 0.0);
                    def.group = (int) Args.num(scriptEntry.getObjectTag("group"), 0);
                    def.sensor = Args.bool(scriptEntry.getObjectTag("sensor"), false);
                    def.notify = Args.bool(scriptEntry.getObjectTag("notify"), true);
                    def.detectStatic = Args.bool(scriptEntry.getObjectTag("detect_static"), false);
                    def.notifyPersist = Args.bool(scriptEntry.getObjectTag("notify_persist"), false);
                    def.managed = true;
                    def.allowSleeping = false;
                    def.removeOnFallOut = false;
                    def.tag = Args.str(scriptEntry.getObjectTag("tag"), "");

                    manager.detach(player.getUniqueId(), worldId, name);
                    world.createBody(def);
                    manager.register(player.getUniqueId(), worldId, name, offset, follow);
                    break;
                }
                case "detach": {
                    UUID uuid = playerTag.getUUID();
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

    private static PlayerTag resolvePlayer(ScriptEntry scriptEntry) {
        if (scriptEntry.hasObject("player")) {
            ObjectTag tag = scriptEntry.getObjectTag("player");
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
