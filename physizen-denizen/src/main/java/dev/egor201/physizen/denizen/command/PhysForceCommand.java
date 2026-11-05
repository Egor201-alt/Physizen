package dev.egor201.physizen.denizen.command;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultNull;
import com.denizenscript.denizencore.scripts.commands.generator.ArgLinear;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
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
        autoCompile();
        addRemappedPrefixes("body", "name");
        addRemappedPrefixes("vector", "v", "vec");
        addRemappedPrefixes("point", "at");
    }

    public enum Action {
        FORCE, IMPULSE, TORQUE, ANGULAR_IMPULSE, VELOCITY, ANGULAR_VELOCITY, POINT_FORCE, POINT_IMPULSE
    }

    public static void autoExecute(ScriptEntry scriptEntry,
            @ArgName("action") @ArgLinear Action action,
            @ArgName("body_linear") @ArgLinear @ArgDefaultNull ElementTag bodyLinear,
            @ArgName("body") @ArgPrefixed @ArgDefaultNull ElementTag bodyArg,
            @ArgName("world") @ArgPrefixed @ArgDefaultNull ElementTag worldArg,
            @ArgName("vector") @ArgPrefixed @ArgDefaultNull ObjectTag vectorArg,
            @ArgName("point") @ArgPrefixed @ArgDefaultNull ObjectTag pointArg) {
        ElementTag bodyValue = bodyArg != null ? bodyArg : bodyLinear;
        ElementTag bodyName = bodyValue;
        if (action == null || bodyName == null) {
            Debug.echoError(scriptEntry, "physforce needs action and body:<name>.");
            return;
        }
        if (!(vectorArg != null)) {
            Debug.echoError(scriptEntry, "physforce needs vector:<vector>.");
            return;
        }

        String act = action.name().toLowerCase();
        String worldId = (worldArg != null) ? worldArg.asString() : "default";

        try {
            PhysWorld w = Args.world(worldId);
            PhysBody b = w.body(bodyName.asString());
            if (b == null) {
                Debug.echoError(scriptEntry, "Unknown body: " + bodyName.asString());
                return;
            }

            double[] v = Args.vec(vectorArg, scriptEntry.context);

            switch (action) {
                case FORCE:
                    b.addForce(v);
                    break;
                case IMPULSE:
                    b.addImpulse(v);
                    break;
                case TORQUE:
                    b.addTorque(v);
                    break;
                case ANGULAR_IMPULSE:
                    b.addAngularImpulse(v);
                    break;
                case VELOCITY:
                    b.setLinearVelocity(v);
                    break;
                case ANGULAR_VELOCITY:
                    b.setAngularVelocity(v);
                    break;
                case POINT_FORCE: {
                    if (!(pointArg != null)) {
                        Debug.echoError(scriptEntry, "point_force requires point:<location>.");
                        return;
                    }
                    double[] pt = Args.vec(pointArg, scriptEntry.context);
                    b.addForceAt(v, pt);
                    break;
                }
                case POINT_IMPULSE: {
                    if (!(pointArg != null)) {
                        Debug.echoError(scriptEntry, "point_impulse requires point:<location>.");
                        return;
                    }
                    double[] pt = Args.vec(pointArg, scriptEntry.context);
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
