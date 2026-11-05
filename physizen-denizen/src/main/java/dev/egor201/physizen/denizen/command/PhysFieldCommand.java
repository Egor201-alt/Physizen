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
import dev.egor201.physizen.core.model.FieldDef;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.world.PhysWorld;

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
        autoCompile();
        addRemappedPrefixes("name", "id");
    }

    public enum Action {
        REMOVE, CREATE
    }

    public static void autoExecute(ScriptEntry scriptEntry,
            @ArgName("action") @ArgLinear Action action,
            @ArgName("name") @ArgPrefixed @ArgDefaultNull ElementTag nameArg,
            @ArgName("world") @ArgPrefixed @ArgDefaultNull ElementTag worldArg,
            @ArgName("type") @ArgPrefixed @ArgDefaultNull FieldDef.Kind typeArg,
            @ArgName("center") @ArgPrefixed @ArgDefaultNull ObjectTag centerArg,
            @ArgName("direction") @ArgPrefixed @ArgDefaultNull ObjectTag directionArg,
            @ArgName("radius") @ArgPrefixed @ArgDefaultNull ObjectTag radiusArg,
            @ArgName("strength") @ArgPrefixed @ArgDefaultNull ObjectTag strengthArg,
            @ArgName("surface_y") @ArgPrefixed @ArgDefaultNull ObjectTag surfaceYArg,
            @ArgName("density") @ArgPrefixed @ArgDefaultNull ObjectTag densityArg,
            @ArgName("drag") @ArgPrefixed @ArgDefaultNull ObjectTag dragArg,
            @ArgName("mass_independent") @ArgPrefixed @ArgDefaultNull ObjectTag massIndependentArg,
            @ArgName("group") @ArgPrefixed @ArgDefaultNull ObjectTag groupArg) {
        ElementTag action = actionArg;
        ElementTag nameTag = nameArg;
        if (action == null || nameTag == null) {
            Debug.echoError(scriptEntry, "physfield needs action and name:<name>.");
            return;
        }

        String worldId = (worldArg != null) ? worldArg.asString() : "default";

        try {
            PhysWorld w = Args.world(worldId);

            if (action == Action.REMOVE) {
                w.removeField(nameTag.asString());
                return;
            }

            if (action == Action.CREATE) {
                FieldDef def = new FieldDef();
                def.name = nameTag.asString();
                if (typeArg != null) {
                    def.kind = typeArg;
                }
                if (centerArg != null) def.center = Args.vec(centerArg, scriptEntry.context);
                if (directionArg != null) def.direction = Args.vec(directionArg, scriptEntry.context);
                def.radius = Args.num(radiusArg, 10.0);
                def.strength = Args.num(strengthArg, 1.0);
                def.surfaceY = Args.num(surfaceYArg, 0.0);
                def.density = Args.num(densityArg, 1000.0);
                def.drag = Args.num(dragArg, 0.5);
                def.massIndependent = Args.bool(massIndependentArg, true);
                def.group = (int) Args.num(groupArg, -1);

                w.addField(def);
            }
        }
        catch (PhysException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }

}