package dev.egor201.physizen.denizen;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.core.FieldDef;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.PhysWorld;

// <--[command]
// @Name physfield
// @Syntax physfield [create/remove] (world:<world>) name:<name> (type:<type>) (center:<loc>) (radius:<r>) (strength:<s>) ...
// @Required 2
// @Maximum 16
// @Short Creates or removes physical force fields (wind, vortex, buoyancy, radial gravity).
// @Group Physizen
// -->
public class PhysFieldCommand extends AbstractCommand {

    public PhysFieldCommand() {
        setName("physfield");
        setSyntax("physfield [create/remove] (world:<world>) name:<name> (type:<type>) (center:<loc>) (radius:<r>) (strength:<s>) ...");
        setRequiredArguments(2, 16);
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
            Debug.echoError(scriptEntry, "physfield needs action and name:<name>.");
            return;
        }

        String act = action.asString().toLowerCase();
        String worldId = scriptEntry.hasObject("world") ? scriptEntry.getElement("world").asString() : "default";

        try {
            PhysWorld w = Util.world(worldId);

            if (act.equals("remove")) {
                w.removeField(nameTag.asString());
                return;
            }

            if (act.equals("create")) {
                FieldDef def = new FieldDef();
                def.name = nameTag.asString();
                if (scriptEntry.hasObject("type")) {
                    def.kind = FieldDef.Kind.valueOf(scriptEntry.getElement("type").asString().toUpperCase());
                }
                if (scriptEntry.hasObject("center")) def.center = Util.vec(scriptEntry.getObjectTag("center"), scriptEntry.context);
                if (scriptEntry.hasObject("direction")) def.direction = Util.vec(scriptEntry.getObjectTag("direction"), scriptEntry.context);
                def.radius = Util.num(scriptEntry.getObjectTag("radius"), 10.0);
                def.strength = Util.num(scriptEntry.getObjectTag("strength"), 1.0);
                def.surfaceY = Util.num(scriptEntry.getObjectTag("surface_y"), 0.0);
                def.density = Util.num(scriptEntry.getObjectTag("density"), 1000.0);
                def.drag = Util.num(scriptEntry.getObjectTag("drag"), 0.5);
                def.massIndependent = Util.bool(scriptEntry.getObjectTag("mass_independent"), true);
                def.group = (int) Util.num(scriptEntry.getObjectTag("group"), -1);

                w.addField(def);
            }
        }
        catch (PhysException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }
}