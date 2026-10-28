package dev.egor201.physizen.denizen;

import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.DisplayBinder;
import dev.egor201.physizen.Physizen;
import dev.egor201.physizen.core.BodyDef;
import dev.egor201.physizen.core.MotionKind;
import dev.egor201.physizen.core.PhysBody;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.PhysWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

// <--[command]
// @Name physbody
// @Syntax physbody [create/remove/teleport/move/set/bind/unbind/activate/deactivate] (world:<world>) (name:<name>) ...
// @Required 1
// @Maximum 12
// @Short Controls Physizen physical bodies.
// @Group Physizen
// -->
public class PhysBodyCommand extends AbstractCommand {

    public PhysBodyCommand() {
        setName("physbody");
        setSyntax("physbody [create/remove/teleport/move/set/bind/unbind/activate/deactivate] (world:<world>) (name:<name>) ...");
        setRequiredArguments(1, 12);
        isProcedural = false;
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action")) {
                scriptEntry.addObject("action", arg.asElement());
            }
            else if (!scriptEntry.hasObject("name") && (arg.matchesPrefix("name", "body") || !arg.hasPrefix())) {
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
            Debug.echoError(scriptEntry, "Missing action for physbody command.");
            return;
        }
        String act = action.asString().toLowerCase();
        String worldId = scriptEntry.hasObject("world") ? scriptEntry.getElement("world").asString() : "default";
        String name = scriptEntry.hasObject("name") ? scriptEntry.getElement("name").asString() : null;

        try {
            PhysWorld w = Util.world(worldId);

            switch (act) {
                case "create": {
                    if (name == null) {
                        Debug.echoError(scriptEntry, "physbody create needs name:<id>.");
                        return;
                    }
                    BodyDef def = new BodyDef();
                    def.name = name;
                    ObjectTag shapeObj = scriptEntry.getObjectTag("shape");
                    def.shape = Util.shape((ObjectTag) shapeObj, scriptEntry.context)
                    if (scriptEntry.hasObject("at") || scriptEntry.hasObject("position")) {
                        def.position = Util.vec(scriptEntry.getObjectTag(scriptEntry.hasObject("at") ? "at" : "position"), scriptEntry.context);
                    }
                    if (scriptEntry.hasObject("rotation")) {
                        MapTag rotMap = new MapTag();
                        rotMap.putObject("rotation", scriptEntry.getObjectTag("rotation"));
                        def.rotation = Util.quat(rotMap, scriptEntry.context);
                    }
                    else if (scriptEntry.hasObject("euler")) {
                        def.rotation = Util.euler(Util.vec(scriptEntry.getObjectTag("euler"), scriptEntry.context));
                    }
                    if (scriptEntry.hasObject("type")) {
                        def.type = MotionKind.valueOf(scriptEntry.getElement("type").asString().toUpperCase());
                    }
                    def.mass = Util.num(scriptEntry.getObjectTag("mass"), 0);
                    def.friction = Util.num(scriptEntry.getObjectTag("friction"), 0.5);
                    def.restitution = Util.num(scriptEntry.getObjectTag("restitution"), 0.0);
                    def.linearDamping = Util.num(scriptEntry.getObjectTag("linear_damping"), 0.05);
                    def.angularDamping = Util.num(scriptEntry.getObjectTag("angular_damping"), 0.05);
                    def.gravityFactor = Util.num(scriptEntry.getObjectTag("gravity"), 1.0);
                    def.group = (int) Util.num(scriptEntry.getObjectTag("group"), 0);
                    def.sensor = Util.bool(scriptEntry.getObjectTag("sensor"), false);
                    def.continuous = Util.bool(scriptEntry.getObjectTag("continuous"), false);
                    def.allowSleeping = Util.bool(scriptEntry.getObjectTag("allow_sleeping"), true);
                    def.notify = Util.bool(scriptEntry.getObjectTag("notify"), true);
                    def.removeOnFallOut = Util.bool(scriptEntry.getObjectTag("remove_on_fall"), true);
                    def.tag = Util.str(scriptEntry.getObjectTag("tag"), "");

                    if (scriptEntry.hasObject("velocity")) {
                        def.linearVelocity = Util.vec(scriptEntry.getObjectTag("velocity"), scriptEntry.context);
                    }
                    if (scriptEntry.hasObject("angular_velocity")) {
                        def.angularVelocity = Util.vec(scriptEntry.getObjectTag("angular_velocity"), scriptEntry.context);
                    }

                    w.createBody(def);
                    break;
                }
                case "remove": {
                    if (name == null) return;
                    w.removeBody(name);
                    Physizen.getInstance().getDisplayBinder().unbindAll(worldId, name);
                    break;
                }
                case "teleport": {
                    PhysBody b = w.body(name);
                    if (b == null) return;
                    double[] pos = Util.vec(scriptEntry.getObjectTag("to"), scriptEntry.context);
                    double[] rot = scriptEntry.hasObject("rotation") ? Util.vec(scriptEntry.getObjectTag("rotation"), scriptEntry.context) : null;
                    b.teleport(pos, rot);
                    break;
                }
                case "move": {
                    PhysBody b = w.body(name);
                    if (b == null) return;
                    double[] pos = Util.vec(scriptEntry.getObjectTag("to"), scriptEntry.context);
                    double[] rot = scriptEntry.hasObject("rotation") ? Util.vec(scriptEntry.getObjectTag("rotation"), scriptEntry.context) : null;
                    double seconds = Util.num(scriptEntry.getObjectTag("seconds"), 1.0 / 20.0);
                    b.moveKinematic(pos, rot, seconds);
                    break;
                }
                case "activate": {
                    PhysBody b = w.body(name);
                    if (b != null) b.activate();
                    break;
                }
                case "deactivate": {
                    PhysBody b = w.body(name);
                    if (b != null) b.deactivate();
                    break;
                }
                case "set": {
                    PhysBody b = w.body(name);
                    if (b == null) return;
                    if (scriptEntry.hasObject("friction")) b.setFriction(Util.num(scriptEntry.getObjectTag("friction"), 0.5));
                    if (scriptEntry.hasObject("restitution")) b.setRestitution(Util.num(scriptEntry.getObjectTag("restitution"), 0));
                    if (scriptEntry.hasObject("gravity")) b.setGravityFactor(Util.num(scriptEntry.getObjectTag("gravity"), 1));
                    if (scriptEntry.hasObject("damping")) {
                        double[] d = Util.vec(scriptEntry.getObjectTag("damping"), scriptEntry.context);
                        b.setDamping(d[0], d[1]);
                    }
                    break;
                }
                case "bind": {
                    List<EntityTag> ents = Util.entities(scriptEntry.getObjectTag("entities"), scriptEntry.context);
                    for (EntityTag et : ents) {
                        DisplayBinder.Binding binding = new DisplayBinder.Binding(et.getBukkitEntity());
                        if (scriptEntry.hasObject("offset")) {
                            double[] o = Util.vec(scriptEntry.getObjectTag("offset"), scriptEntry.context);
                            binding.offset = new Vector3f((float) o[0], (float) o[1], (float) o[2]);
                        }
                        if (scriptEntry.hasObject("scale")) {
                            double[] s = Util.vec(scriptEntry.getObjectTag("scale"), scriptEntry.context);
                            binding.scale = new Vector3f((float) s[0], (float) s[1], (float) s[2]);
                        }
                        binding.interpolationDuration = (int) Util.num(scriptEntry.getObjectTag("interpolation"), 1);
                        binding.teleportDuration = (int) Util.num(scriptEntry.getObjectTag("teleport_duration"), 1);
                        binding.removeWithBody = Util.bool(scriptEntry.getObjectTag("remove_with_body"), true);
                        Physizen.getInstance().getDisplayBinder().bind(worldId, name, binding);
                    }
                    break;
                }
                case "unbind": {
                    List<EntityTag> ents = Util.entities(scriptEntry.getObjectTag("entities"), scriptEntry.context);
                    for (EntityTag et : ents) {
                        Physizen.getInstance().getDisplayBinder().unbind(worldId, name, et.getBukkitEntity().getUniqueId());
                    }
                    break;
                }
                default:
                    Debug.echoError(scriptEntry, "Unknown physbody action: " + act);
                    break;
            }
        }
        catch (PhysException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }
}
