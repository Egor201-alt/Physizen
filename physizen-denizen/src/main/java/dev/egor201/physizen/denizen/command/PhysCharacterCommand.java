package dev.egor201.physizen.denizen.command;

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
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.CharacterDef;
import dev.egor201.physizen.core.world.PhysCharacter;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;
import org.bukkit.entity.Player;

// <--[command]
// @Name physcharacter
// @Syntax physcharacter [create/attach/detach/remove/move/jump/set/teleport] (world:<physworld>) (name:<name>) (player:<player>) (at:<vector>) (shape:<shape>) (velocity:<vector>) (speed:<number>) (impulse:<vector>) (mode:<driven/track>) (mass:<number>) (max_slope:<degrees>) (step_height:<number>) (walk_stairs:<true/false>) (stick_to_floor:<true/false>) (push:<true/false>) (inner_body:<true/false>) (gravity:<number>) (group:<0-15>) (notify:<true/false>) (tag:<text>)
// @Required 1
// @Maximum 24
// @Short Controls CharacterVirtual character controllers.
// @Group Physizen
//
// @Description
// A character is a Jolt CharacterVirtual: a capsule (or any convex shape) that slides along terrain and bodies, climbs stairs, sticks to the floor, stands on moving platforms and pushes dynamic bodies.
// Its position is the point between its feet. The default shape is capsule:0.3,1.8 (radius, total height).
// With inner_body:true (default) the character also exists as a kinematic body with the same name, so sensor zones, queries and contact events see it.
//
// create makes a driven character. Use move to set the horizontal velocity it should walk with (re-send it every tick, or send a zero vector to stop), jump to make it jump, set to override velocity, add an impulse or change gravity, and teleport to move it instantly.
// attach creates a tracking character that shadows a player: the character follows the real player position, pushes bodies the player walks into, reports ground state and triggers sensor zones. The player itself is still moved by Minecraft.
// detach removes the tracking character of a player (all of the player's characters when no name is given). remove deletes any character by name.
//
// @Tags
// <physcharacter[<world>/<name>]>
// <PhysCharacterTag.on_ground>
// <PhysCharacterTag.ground_state>
//
// @Usage
// Create a walking NPC controller and make it walk forward.
// - physcharacter create world:arena name:npc at:<location[0,70,0]> shape:capsule:0.3,1.8
// - physcharacter move world:arena name:npc velocity:0,0,3
//
// @Usage
// Make the linked player trigger sensor zones and push bodies.
// - physcharacter attach
//
// @Usage
// Make a driven character jump.
// - physcharacter jump world:arena name:npc speed:7
// -->
public class PhysCharacterCommand extends AbstractCommand {

    public PhysCharacterCommand() {
        setName("physcharacter");
        setSyntax("physcharacter [create/attach/detach/remove/move/jump/set/teleport] (world:<physworld>) (name:<name>) (player:<player>) (at:<vector>) (shape:<shape>) (velocity:<vector>) (speed:<number>) (impulse:<vector>) (mode:<driven/track>) (mass:<number>) (max_slope:<degrees>) (step_height:<number>) (walk_stairs:<true/false>) (stick_to_floor:<true/false>) (push:<true/false>) (inner_body:<true/false>) (gravity:<number>) (group:<0-15>) (notify:<true/false>) (tag:<text>)");
        setRequiredArguments(1, 24);
        isProcedural = false;
        autoCompile();
        addRemappedPrefixes("name", "character");
    }

    public enum Action {
        CREATE, ATTACH, DETACH, REMOVE, MOVE, JUMP, SET, TELEPORT
    }

    public static void autoExecute(ScriptEntry scriptEntry,
            @ArgName("action") @ArgLinear Action action,
            @ArgName("name_linear") @ArgLinear @ArgDefaultNull ElementTag nameLinear,
            @ArgName("name") @ArgPrefixed @ArgDefaultNull ElementTag nameArg,
            @ArgName("world") @ArgPrefixed @ArgDefaultNull ElementTag worldArg,
            @ArgName("at") @ArgPrefixed @ArgDefaultNull ObjectTag atArg,
            @ArgName("position") @ArgPrefixed @ArgDefaultNull ObjectTag positionArg,
            @ArgName("mode") @ArgPrefixed @ArgDefaultNull CharacterDef.Mode modeArg,
            @ArgName("velocity") @ArgPrefixed @ArgDefaultNull ObjectTag velocityArg,
            @ArgName("speed") @ArgPrefixed @ArgDefaultNull ObjectTag speedArg,
            @ArgName("impulse") @ArgPrefixed @ArgDefaultNull ObjectTag impulseArg,
            @ArgName("gravity") @ArgPrefixed @ArgDefaultNull ObjectTag gravityArg,
            @ArgName("rotation") @ArgPrefixed @ArgDefaultNull ObjectTag rotationArg,
            @ArgName("shape") @ArgPrefixed @ArgDefaultNull ObjectTag shapeArg,
            @ArgName("mass") @ArgPrefixed @ArgDefaultNull ObjectTag massArg,
            @ArgName("max_slope") @ArgPrefixed @ArgDefaultNull ObjectTag maxSlopeArg,
            @ArgName("step_height") @ArgPrefixed @ArgDefaultNull ObjectTag stepHeightArg,
            @ArgName("walk_stairs") @ArgPrefixed @ArgDefaultNull ObjectTag walkStairsArg,
            @ArgName("stick_to_floor") @ArgPrefixed @ArgDefaultNull ObjectTag stickToFloorArg,
            @ArgName("push") @ArgPrefixed @ArgDefaultNull ObjectTag pushArg,
            @ArgName("inner_body") @ArgPrefixed @ArgDefaultNull ObjectTag innerBodyArg,
            @ArgName("group") @ArgPrefixed @ArgDefaultNull ObjectTag groupArg,
            @ArgName("notify") @ArgPrefixed @ArgDefaultNull ObjectTag notifyArg,
            @ArgName("tag") @ArgPrefixed @ArgDefaultNull ObjectTag tagArg,
            @ArgName("player") @ArgPrefixed @ArgDefaultNull ObjectTag playerArg) {
        ElementTag nameValue = nameArg != null ? nameArg : nameLinear;
        ElementTag action = actionArg;
        if (action == null) {
            Debug.echoError(scriptEntry, "Missing action for physcharacter command.");
            return;
        }
        String act = action.name().toLowerCase();
        String worldId = (worldArg != null) ? worldArg.asString() : null;
        String name = (nameValue != null) ? nameValue.asString() : null;
        try {
            switch (action) {
                case CREATE: {
                    if (name == null) {
                        throw new PhysException("physcharacter create needs name:<id>.");
                    }
                    PhysWorld world = Args.world(worldId);
                    CharacterDef def = buildDef(scriptEntry, name, shapeArg, massArg, maxSlopeArg, stepHeightArg, walkStairsArg, stickToFloorArg, pushArg, innerBodyArg, gravityArg, groupArg, notifyArg, tagArg);
                    if ((atArg != null) || (positionArg != null)) {
                        def.position = Args.vec((atArg != null ? atArg : positionArg), scriptEntry.context);
                    }
                    if (modeArg != null) {
                        def.mode = modeArg;
                    }
                    world.createCharacter(def);
                    break;
                }
                case ATTACH: {
                    PlayerTag playerTag = resolvePlayer(scriptEntry, playerArg);
                    if (playerTag == null) {
                        throw new PhysException("physcharacter attach needs player:<player> or a linked player.");
                    }
                    Player player = playerTag.getPlayerEntity();
                    if (player == null || !player.isOnline()) {
                        throw new PhysException("Player is not online.");
                    }
                    PhysizenServices services = PhysizenServices.get();
                    if (worldId == null) {
                        worldId = services.hitboxes.defaultWorldFor(player);
                        if (worldId == null) {
                            throw new PhysException("No physics world is attached to the player's world. Pass world:<id>.");
                        }
                    }
                    if (name == null) {
                        name = "controller_" + player.getName();
                    }
                    CharacterDef def = buildDef(scriptEntry, name, shapeArg, massArg, maxSlopeArg, stepHeightArg, walkStairsArg, stickToFloorArg, pushArg, innerBodyArg, gravityArg, groupArg, notifyArg, tagArg);
                    def.walkStairs = false;
                    def.stickToFloor = false;
                    services.controllers.attach(player, worldId, def);
                    break;
                }
                case DETACH: {
                    PlayerTag playerTag = resolvePlayer(scriptEntry, playerArg);
                    if (playerTag == null) {
                        throw new PhysException("physcharacter detach needs player:<player> or a linked player.");
                    }
                    PhysizenServices.get().controllers.detach(playerTag.getUUID(), worldId, name);
                    break;
                }
                case REMOVE: {
                    if (name == null) {
                        throw new PhysException("physcharacter remove needs name:<id>.");
                    }
                    Args.world(worldId).removeCharacter(name);
                    break;
                }
                case MOVE: {
                    character(worldId, name).move(Args.vec(velocityArg, scriptEntry.context));
                    break;
                }
                case JUMP: {
                    character(worldId, name).jump(Args.num(speedArg, 6.0));
                    break;
                }
                case SET: {
                    PhysCharacter c = character(worldId, name);
                    if (velocityArg != null) {
                        c.setVelocity(Args.vec(velocityArg, scriptEntry.context));
                    }
                    if (impulseArg != null) {
                        c.addImpulse(Args.vec(impulseArg, scriptEntry.context));
                    }
                    if (gravityArg != null) {
                        c.setGravityFactor(Args.num(gravityArg, 1.0));
                    }
                    break;
                }
                case TELEPORT: {
                    double[] at = Args.vec((atArg != null ? atArg : positionArg), scriptEntry.context);
                    double[] rot = (rotationArg != null) ? Args.rotation(rotationArg, scriptEntry.context) : null;
                    character(worldId, name).teleport(at, rot);
                    break;
                }
                default:
                    Debug.echoError(scriptEntry, "Unknown physcharacter action: " + act);
            }
        }
        catch (PhysException | IllegalArgumentException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }


    private static PhysCharacter character(String worldId, String name) {
        if (name == null) {
            throw new PhysException("This physcharacter action needs name:<id>.");
        }
        PhysCharacter c = Args.world(worldId).character(name);
        if (c == null) {
            throw new PhysException("Unknown character " + name + ".");
        }
        return c;
    }

    private static CharacterDef buildDef(ScriptEntry scriptEntry, String name, ObjectTag shapeArg, ObjectTag massArg, ObjectTag maxSlopeArg, ObjectTag stepHeightArg, ObjectTag walkStairsArg, ObjectTag stickToFloorArg, ObjectTag pushArg, ObjectTag innerBodyArg, ObjectTag gravityArg, ObjectTag groupArg, ObjectTag notifyArg, ObjectTag tagArg) {
        CharacterDef def = new CharacterDef();
        def.name = name;
        if (shapeArg != null) {
            def.shape = ShapeParser.shape(shapeArg, scriptEntry.context);
        }
        def.mass = Args.num(massArg, 70);
        def.maxSlopeDegrees = Args.num(maxSlopeArg, 50);
        def.stepHeight = Args.num(stepHeightArg, 0.6);
        def.walkStairs = Args.bool(walkStairsArg, true);
        def.stickToFloor = Args.bool(stickToFloorArg, true);
        def.pushBodies = Args.bool(pushArg, true);
        def.innerBody = Args.bool(innerBodyArg, true);
        def.gravityFactor = Args.num(gravityArg, 1.0);
        def.group = (int) Args.num(groupArg, 0);
        def.notify = Args.bool(notifyArg, true);
        def.tag = Args.str(tagArg, "");
        return def;
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
