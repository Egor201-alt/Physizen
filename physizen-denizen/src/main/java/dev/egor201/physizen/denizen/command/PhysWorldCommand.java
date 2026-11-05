package dev.egor201.physizen.denizen.command;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultNull;
import com.denizenscript.denizencore.scripts.commands.generator.ArgLinear;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.denizen.DenizenServices;
import dev.egor201.physizen.denizen.save.PrefabFiles;
import dev.egor201.physizen.denizen.save.SaveManager;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.world.PhysWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

// <--[command]
// @Name physworld
// @Syntax physworld [create/remove/pause/resume/step/clear/gravity/time_scale/rate/collision/explosion/terrain/refresh/save/load] (id:<id>) ...
// @Required 1
// @Maximum 12
// @Short Controls Physizen physics worlds.
// @Group Physizen
// -->
public class PhysWorldCommand extends AbstractCommand {

    public PhysWorldCommand() {
        setName("physworld");
        setSyntax("physworld [create/remove/pause/resume/step/clear/gravity/time_scale/rate/collision/explosion/terrain/refresh/save/load] (id:<id>) ...");
        setRequiredArguments(1, 12);
        isProcedural = false;
        autoCompile();
        addRemappedPrefixes("id", "world");
    }

    public enum Action {
        CREATE, REMOVE, PAUSE, RESUME, STEP, CLEAR, GRAVITY, TIME_SCALE, RATE, COLLISION, EXPLOSION, TERRAIN, REFRESH, SAVE, LOAD
    }

    public static void autoExecute(ScriptEntry scriptEntry,
            @ArgName("action") @ArgLinear Action action,
            @ArgName("id_linear") @ArgLinear @ArgDefaultNull ElementTag idLinear,
            @ArgName("id") @ArgPrefixed @ArgDefaultNull ElementTag idArg,
            @ArgName("rate") @ArgPrefixed @ArgDefaultNull ObjectTag rateArg,
            @ArgName("gravity") @ArgPrefixed @ArgDefaultNull ObjectTag gravityArg,
            @ArgName("attach") @ArgPrefixed @ArgDefaultNull ObjectTag attachArg,
            @ArgName("count") @ArgPrefixed @ArgDefaultNull ObjectTag countArg,
            @ArgName("value") @ArgPrefixed @ArgDefaultNull ObjectTag valueArg,
            @ArgName("group_a") @ArgPrefixed @ArgDefaultNull ObjectTag groupAArg,
            @ArgName("group_b") @ArgPrefixed @ArgDefaultNull ObjectTag groupBArg,
            @ArgName("enable") @ArgPrefixed @ArgDefaultNull ObjectTag enableArg,
            @ArgName("center") @ArgPrefixed @ArgDefaultNull ObjectTag centerArg,
            @ArgName("radius") @ArgPrefixed @ArgDefaultNull ObjectTag radiusArg,
            @ArgName("impulse") @ArgPrefixed @ArgDefaultNull ObjectTag impulseArg,
            @ArgName("upward") @ArgPrefixed @ArgDefaultNull ObjectTag upwardArg,
            @ArgName("min") @ArgPrefixed @ArgDefaultNull ObjectTag minArg,
            @ArgName("max") @ArgPrefixed @ArgDefaultNull ObjectTag maxArg,
            @ArgName("file") @ArgPrefixed @ArgDefaultNull ElementTag fileArg) {
        ElementTag idValue = idArg != null ? idArg : idLinear;
        ElementTag idTag = idValue;
        if (action == null) {
            Debug.echoError(scriptEntry, "Missing action for physworld command.");
            return;
        }
        String act = action.name().toLowerCase();
        String id = idTag != null ? idTag.asString() : "default";

        try {
            switch (action) {
                case CREATE: {
                    PhysEngine engine = PhysEngine.get();
                    PhysWorld w = engine.world(id);
                    if (w == null) {
                        int rate = (rateArg != null) ? (int) Args.num(rateArg, engine.config().tickRate) : engine.config().tickRate;
                        w = engine.createWorld(id, rate);
                    }
                    else if (rateArg != null) {
                        w.setStepRate((int) Args.num(rateArg, engine.config().tickRate));
                    }
                    if (gravityArg != null) {
                        w.setGravity(Args.vec(gravityArg, scriptEntry.context));
                    }
                    if (attachArg != null) {
                        World bw = Bukkit.getWorld(attachArg.toString());
                        if (bw != null) {
                            PhysizenServices.get().worlds.attach(id, bw);
                        }
                    }
                    break;
                }
                case REMOVE: {
                    PhysEngine.get().removeWorld(id);
                    break;
                }
                case PAUSE: {
                    Args.world(id).setPaused(true);
                    break;
                }
                case RESUME: {
                    Args.world(id).setPaused(false);
                    break;
                }
                case STEP: {
                    int count = (countArg != null) ? (int) Args.num(countArg, 1) : 1;
                    Args.world(id).singleStep(count);
                    break;
                }
                case CLEAR: {
                    Args.world(id).clear();
                    break;
                }
                case GRAVITY: {
                    if (!(valueArg != null)) {
                        Debug.echoError(scriptEntry, "physworld gravity needs value:<vector>.");
                        return;
                    }
                    Args.world(id).setGravity(Args.vec(valueArg, scriptEntry.context));
                    break;
                }
                case TIME_SCALE: {
                    if (!(valueArg != null)) {
                        Debug.echoError(scriptEntry, "physworld time_scale needs value:<number>.");
                        return;
                    }
                    Args.world(id).setTimeScale(Args.num(valueArg, 1.0));
                    break;
                }
                case RATE: {
                    if (!(valueArg != null)) {
                        Debug.echoError(scriptEntry, "physworld rate needs value:<number>.");
                        return;
                    }
                    Args.world(id).setStepRate((int) Args.num(valueArg, 60));
                    break;
                }
                case COLLISION: {
                    int a = (int) Args.num(groupAArg, 0);
                    int b = (int) Args.num(groupBArg, 0);
                    boolean enable = Args.bool(enableArg, true);
                    Args.world(id).setGroupCollision(a, b, enable);
                    break;
                }
                case EXPLOSION: {
                    if (!(centerArg != null)) {
                        Debug.echoError(scriptEntry, "physworld explosion needs center:<location>.");
                        return;
                    }
                    double[] c = Args.vec(centerArg, scriptEntry.context);
                    double r = Args.num(radiusArg, 5.0);
                    double imp = Args.num(impulseArg, 50.0);
                    double up = Args.num(upwardArg, 0.2);
                    Args.world(id).explosion(c, r, imp, up);
                    break;
                }
                case TERRAIN: {
                    if (attachArg != null) {
                        World bw = Bukkit.getWorld(attachArg.toString());
                        if (bw != null) {
                            PhysizenServices.get().worlds.attach(id, bw);
                        }
                    }
                    break;
                }
                case REFRESH: {
                    Args.world(id);
                    double[] min;
                    double[] max;
                    if ((minArg != null) && (maxArg != null)) {
                        double[] a = Args.vec(minArg, scriptEntry.context);
                        double[] b = Args.vec(maxArg, scriptEntry.context);
                        min = new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
                        max = new double[]{Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
                    }
                    else if (centerArg != null) {
                        double[] c = Args.vec(centerArg, scriptEntry.context);
                        double r = Args.num(radiusArg, 1.0);
                        min = new double[]{c[0] - r, c[1] - r, c[2] - r};
                        max = new double[]{c[0] + r, c[1] + r, c[2] + r};
                    }
                    else {
                        Debug.echoError(scriptEntry, "physworld refresh needs center:<location> (radius:<n>) or min:<location> max:<location>.");
                        return;
                    }
                    PhysizenServices.get().terrain.invalidateArea(id, min, max);
                    break;
                }
                case SAVE: {
                    if (!(fileArg != null)) {
                        Debug.echoError(scriptEntry, "physworld save needs file:<filename>.");
                        return;
                    }
                    String fileName = fileArg.asString();
                    PhysWorld pw = Args.world(id);
                    List<String> bodyNames = new ArrayList<>();
                    for (PhysBody pb : pw.bodies()) {
                        if (PhysizenServices.get().hitboxes.ownerOf(pw.id, pb.name) == null) {
                            bodyNames.add(pb.name);
                        }
                    }
                    MapTag root = SaveManager.serializePrefab(id, bodyNames);
                    PrefabFiles.saveToFile(root, fileName);
                    break;
                }
                case LOAD: {
                    if (!(fileArg != null)) {
                        Debug.echoError(scriptEntry, "physworld load needs file:<filename>.");
                        return;
                    }
                    String fileName = fileArg.asString();
                    MapTag root = PrefabFiles.loadFromFile(fileName);
                    SaveManager.spawnPrefab(id, root, null, null, "", null);
                    break;
                }
                default:
                    Debug.echoError(scriptEntry, "Unknown physworld action: " + act);
                    break;
            }
        }
        catch (PhysException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }

}