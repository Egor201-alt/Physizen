package dev.egor201.physizen.denizen.command;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.world.PhysWorld;

// <--[command]
// @Name physforce
// @Syntax physforce [force/impulse/torque/angular_impulse/velocity/angular_velocity/point_force/point_impulse] (world:<world>) body:<name> vector:<vector> (point:<location>)
// @Required 2
// @Maximum 8
// @Short Applies physical forces, impulses, or velocity to a body.
// @Group Physizen
// -->
public class PhysForceCommand extends AbstractCommand {

    public PhysForceCommand() {
        setName("physforce");
        setSyntax("physforce [force/impulse/torque/angular_impulse/velocity/angular_velocity/point_force/point_impulse] (world:<world>) body:<name> vector:<vector> (point:<location>)");
        setRequiredArguments(2, 8);
        isProcedural = false;
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action")) {
                scriptEntry.addObject("action", arg.asElement());
            }
            else if (!scriptEntry.hasObject("body") && (arg.matchesPrefix("body", "name") || !arg.hasPrefix())) {
                scriptEntry.addObject("body", arg.asElement());
            }
            else if (!scriptEntry.hasObject("world") && arg.matchesPrefix("world")) {
                scriptEntry.addObject("world", arg.asElement());
            }
            else if (!scriptEntry.hasObject("vector") && arg.matchesPrefix("vector", "v", "vec")) {
                scriptEntry.addObject("vector", arg.object);
            }
            else if (!scriptEntry.hasObject("point") && arg.matchesPrefix("point", "at")) {
                scriptEntry.addObject("point", arg.object);
            }
            else {
                scriptEntry.addObject(arg.prefix != null ? arg.prefix : arg.getValue(), arg.object);
            }
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        ElementTag action = scriptEntry.getElement("action");
        ElementTag bodyName = scriptEntry.getElement("body");
        if (action == null || bodyName == null) {
            Debug.echoError(scriptEntry, "physforce needs action and body:<name>.");
            return;
        }
        if (!scriptEntry.hasObject("vector")) {
            Debug.echoError(scriptEntry, "physforce needs vector:<vector>.");
            return;
        }

        String act = action.asString().toLowerCase();
        String worldId = scriptEntry.hasObject("world") ? scriptEntry.getElement("world").asString() : "default";

        try {
            PhysWorld w = Args.world(worldId);
            PhysBody b = w.body(bodyName.asString());
            if (b == null) {
                Debug.echoError(scriptEntry, "Unknown body: " + bodyName.asString());
                return;
            }

            double[] v = Args.vec(scriptEntry.getObjectTag("vector"), scriptEntry.context);

            switch (act) {
                case "force":
                    b.addForce(v);
                    break;
                case "impulse":
                    b.addImpulse(v);
                    break;
                case "torque":
                    b.addTorque(v);
                    break;
                case "angular_impulse":
                    b.addAngularImpulse(v);
                    break;
                case "velocity":
                    b.setLinearVelocity(v);
                    break;
                case "angular_velocity":
                    b.setAngularVelocity(v);
                    break;
                case "point_force": {
                    if (!scriptEntry.hasObject("point")) {
                        Debug.echoError(scriptEntry, "point_force requires point:<location>.");
                        return;
                    }
                    double[] pt = Args.vec(scriptEntry.getObjectTag("point"), scriptEntry.context);
                    b.addForceAt(v, pt);
                    break;
                }
                case "point_impulse": {
                    if (!scriptEntry.hasObject("point")) {
                        Debug.echoError(scriptEntry, "point_impulse requires point:<location>.");
                        return;
                    }
                    double[] pt = Args.vec(scriptEntry.getObjectTag("point"), scriptEntry.context);
                    b.addImpulseAt(v, pt);
                    break;
                }
                default:
                    Debug.echoError(scriptEntry, "Unknown physforce action: " + act);
                    break;
            }
        }
        catch (PhysException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }
}
