package dev.egor201.physizen.denizen;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.core.ConstraintDef;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.PhysWorld;

// <--[command]
// @Name physconstraint
// @Syntax physconstraint [create/remove] (world:<world>) name:<name> (type:<type>) (body_a:<a>) (body_b:<b>) (point_a:<loc>) (point_b:<loc>) (axis:<vec>) ...
// @Required 2
// @Maximum 14
// @Short Creates or removes physical constraints (joints, hinges, sliders).
// @Group Physizen
// -->
public class PhysConstraintCommand extends AbstractCommand {

    public PhysConstraintCommand() {
        setName("physconstraint");
        setSyntax("physconstraint [create/remove] (world:<world>) name:<name> (type:<type>) (body_a:<a>) (body_b:<b>) (point_a:<loc>) (point_b:<loc>) (axis:<vec>) ...");
        setRequiredArguments(2, 14);
        isProcedural = false;
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action")) {
                scriptEntry.addObject("action", arg.asElement());
            }
            else if (!scriptEntry.hasObject("name") && arg.matchesPrefix("name", "id")) {
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
        ElementTag nameTag = scriptEntry.getElement("name");
        if (action == null || nameTag == null) {
            Debug.echoError(scriptEntry, "physconstraint needs action and name:<name>.");
            return;
        }

        String act = action.asString().toLowerCase();
        String worldId = scriptEntry.hasObject("world") ? scriptEntry.getElement("world").asString() : "default";

        try {
            PhysWorld w = Util.world(worldId);

            if (act.equals("remove")) {
                w.removeConstraint(nameTag.asString());
                return;
            }

            if (act.equals("create")) {
                ConstraintDef def = new ConstraintDef();
                def.name = nameTag.asString();
                if (scriptEntry.hasObject("type")) {
                    def.kind = ConstraintDef.Kind.valueOf(scriptEntry.getElement("type").asString().toUpperCase());
                }
                if (scriptEntry.hasObject("body_a")) def.bodyA = scriptEntry.getElement("body_a").asString();
                if (scriptEntry.hasObject("body_b")) def.bodyB = scriptEntry.getElement("body_b").asString();

                if (scriptEntry.hasObject("point_a")) def.pointA = Util.vec(scriptEntry.getObjectTag("point_a"), scriptEntry.context);
                if (scriptEntry.hasObject("point_b")) def.pointB = Util.vec(scriptEntry.getObjectTag("point_b"), scriptEntry.context);
                if (scriptEntry.hasObject("axis")) def.axis = Util.vec(scriptEntry.getObjectTag("axis"), scriptEntry.context);
                if (scriptEntry.hasObject("normal")) def.normalAxis = Util.vec(scriptEntry.getObjectTag("normal"), scriptEntry.context);

                if (scriptEntry.hasObject("limits")) {
                    double[] lim = Util.vec(scriptEntry.getObjectTag("limits"), scriptEntry.context);
                    def.limitMin = lim[0];
                    def.limitMax = lim[1];
                    def.limited = true;
                }
                def.maxFriction = Util.num(scriptEntry.getObjectTag("friction"), 0);
                def.springFrequency = Util.num(scriptEntry.getObjectTag("spring_freq"), 0);
                def.springDamping = Util.num(scriptEntry.getObjectTag("spring_damp"), 0.5);

                w.addConstraint(def);
            }
        }
        catch (PhysException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }
}