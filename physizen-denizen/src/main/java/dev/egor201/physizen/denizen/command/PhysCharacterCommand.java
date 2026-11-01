package dev.egor201.physizen.denizen.command;

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
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action")) {
                scriptEntry.addObject("action", arg.asElement());
            }
            else if (!scriptEntry.hasObject("name") && (arg.matchesPrefix("name", "character") || !arg.hasPrefix())) {
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
            Debug.echoError(scriptEntry, "Missing action for physcharacter command.");
            return;
        }
        String act = action.asString().toLowerCase();
        String worldId = scriptEntry.hasObject("world") ? scriptEntry.getElement("world").asString() : null;
        String name = scriptEntry.hasObject("name") ? scriptEntry.getElement("name").asString() : null;
        try {
            switch (act) {
                case "create": {
                    if (name == null) {
                        throw new PhysException("physcharacter create needs name:<id>.");
                    }
                    PhysWorld world = Args.world(worldId);
                    CharacterDef def = buildDef(scriptEntry, name);
                    if (scriptEntry.hasObject("at") || scriptEntry.hasObject("position")) {
                        def.position = Args.vec(scriptEntry.getObjectTag(scriptEntry.hasObject("at") ? "at" : "position"), scriptEntry.context);
                    }
                    if (scriptEntry.hasObject("mode")) {
                        def.mode = CharacterDef.Mode.valueOf(scriptEntry.getElement("mode").asString().toUpperCase());
                    }
                    world.createCharacter(def);
                    break;
                }
                case "attach": {
                    PlayerTag playerTag = resolvePlayer(scriptEntry);
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
                    CharacterDef def = buildDef(scriptEntry, name);
                    def.walkStairs = false;
                    def.stickToFloor = false;
                    services.controllers.attach(player, worldId, def);
                    break;
                }
                case "detach": {
                    PlayerTag playerTag = resolvePlayer(scriptEntry);
                    if (playerTag == null) {
                        throw new PhysException("physcharacter detach needs player:<player> or a linked player.");
                    }
                    PhysizenServices.get().controllers.detach(playerTag.getUUID(), worldId, name);
                    break;
                }
                case "remove": {
                    if (name == null) {
                        throw new PhysException("physcharacter remove needs name:<id>.");
                    }
                    Args.world(worldId).removeCharacter(name);
                    break;
                }
                case "move": {
                    character(worldId, name).move(Args.vec(scriptEntry.getObjectTag("velocity"), scriptEntry.context));
                    break;
                }
                case "jump": {
                    character(worldId, name).jump(Args.num(scriptEntry.getObjectTag("speed"), 6.0));
                    break;
                }
                case "set": {
                    PhysCharacter c = character(worldId, name);
                    if (scriptEntry.hasObject("velocity")) {
                        c.setVelocity(Args.vec(scriptEntry.getObjectTag("velocity"), scriptEntry.context));
                    }
                    if (scriptEntry.hasObject("impulse")) {
                        c.addImpulse(Args.vec(scriptEntry.getObjectTag("impulse"), scriptEntry.context));
                    }
                    if (scriptEntry.hasObject("gravity")) {
                        c.setGravityFactor(Args.num(scriptEntry.getObjectTag("gravity"), 1.0));
                    }
                    break;
                }
                case "teleport": {
                    double[] at = Args.vec(scriptEntry.getObjectTag(scriptEntry.hasObject("at") ? "at" : "position"), scriptEntry.context);
                    double[] rot = scriptEntry.hasObject("rotation") ? Args.rotation(scriptEntry.getObjectTag("rotation"), scriptEntry.context) : null;
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

    private static CharacterDef buildDef(ScriptEntry scriptEntry, String name) {
        CharacterDef def = new CharacterDef();
        def.name = name;
        if (scriptEntry.hasObject("shape")) {
            def.shape = ShapeParser.shape(scriptEntry.getObjectTag("shape"), scriptEntry.context);
        }
        def.mass = Args.num(scriptEntry.getObjectTag("mass"), 70);
        def.maxSlopeDegrees = Args.num(scriptEntry.getObjectTag("max_slope"), 50);
        def.stepHeight = Args.num(scriptEntry.getObjectTag("step_height"), 0.6);
        def.walkStairs = Args.bool(scriptEntry.getObjectTag("walk_stairs"), true);
        def.stickToFloor = Args.bool(scriptEntry.getObjectTag("stick_to_floor"), true);
        def.pushBodies = Args.bool(scriptEntry.getObjectTag("push"), true);
        def.innerBody = Args.bool(scriptEntry.getObjectTag("inner_body"), true);
        def.gravityFactor = Args.num(scriptEntry.getObjectTag("gravity"), 1.0);
        def.group = (int) Args.num(scriptEntry.getObjectTag("group"), 0);
        def.notify = Args.bool(scriptEntry.getObjectTag("notify"), true);
        def.tag = Args.str(scriptEntry.getObjectTag("tag"), "");
        return def;
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
