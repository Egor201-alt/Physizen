package dev.egor201.physizen.denizen;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.Physizen;
import dev.egor201.physizen.SaveManager;
import dev.egor201.physizen.core.PhysBody;
import dev.egor201.physizen.core.PhysEngine;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.PhysWorld;
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
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action")) {
                scriptEntry.addObject("action", arg.asElement());
            }
            else if (!scriptEntry.hasObject("id") && (arg.matchesPrefix("id", "world") || !arg.hasPrefix())) {
                scriptEntry.addObject("id", arg.asElement());
            }
            else {
                scriptEntry.addObject(arg.prefix != null ? arg.prefix : arg.getValue(), arg.object);
            }
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        ElementTag action = scriptEntry.getElement("action");
        ElementTag idTag = scriptEntry.getElement("id");
        if (action == null) {
            Debug.echoError(scriptEntry, "Missing action for physworld command.");
            return;
        }
        String act = action.asString().toLowerCase();
        String id = idTag != null ? idTag.asString() : "default";

        try {
            switch (act) {
                case "create": {
                    PhysEngine engine = PhysEngine.get();
                    PhysWorld w = engine.world(id);
                    if (w == null) {
                        int rate = scriptEntry.hasObject("rate") ? (int) Util.num(scriptEntry.getObjectTag("rate"), engine.config().tickRate) : engine.config().tickRate;
                        w = engine.createWorld(id, rate);
                    }
                    else if (scriptEntry.hasObject("rate")) {
                        w.setStepRate((int) Util.num(scriptEntry.getObjectTag("rate"), engine.config().tickRate));
                    }
                    if (scriptEntry.hasObject("gravity")) {
                        w.setGravity(Util.vec(scriptEntry.getObjectTag("gravity"), scriptEntry.context));
                    }
                    if (scriptEntry.hasObject("attach")) {
                        World bw = Bukkit.getWorld(scriptEntry.getObjectTag("attach").toString());
                        if (bw != null) {
                            Physizen.getInstance().attachWorld(id, bw);
                        }
                    }
                    break;
                }
                case "remove": {
                    PhysEngine.get().removeWorld(id);
                    break;
                }
                case "pause": {
                    Util.world(id).setPaused(true);
                    break;
                }
                case "resume": {
                    Util.world(id).setPaused(false);
                    break;
                }
                case "step": {
                    int count = scriptEntry.hasObject("count") ? (int) Util.num(scriptEntry.getObjectTag("count"), 1) : 1;
                    Util.world(id).singleStep(count);
                    break;
                }
                case "clear": {
                    Util.world(id).clear();
                    break;
                }
                case "gravity": {
                    if (!scriptEntry.hasObject("value")) {
                        Debug.echoError(scriptEntry, "physworld gravity needs value:<vector>.");
                        return;
                    }
                    Util.world(id).setGravity(Util.vec(scriptEntry.getObjectTag("value"), scriptEntry.context));
                    break;
                }
                case "time_scale": {
                    if (!scriptEntry.hasObject("value")) {
                        Debug.echoError(scriptEntry, "physworld time_scale needs value:<number>.");
                        return;
                    }
                    Util.world(id).setTimeScale(Util.num(scriptEntry.getObjectTag("value"), 1.0));
                    break;
                }
                case "rate": {
                    if (!scriptEntry.hasObject("value")) {
                        Debug.echoError(scriptEntry, "physworld rate needs value:<number>.");
                        return;
                    }
                    Util.world(id).setStepRate((int) Util.num(scriptEntry.getObjectTag("value"), 60));
                    break;
                }
                case "collision": {
                    int a = (int) Util.num(scriptEntry.getObjectTag("group_a"), 0);
                    int b = (int) Util.num(scriptEntry.getObjectTag("group_b"), 0);
                    boolean enable = Util.bool(scriptEntry.getObjectTag("enable"), true);
                    Util.world(id).setGroupCollision(a, b, enable);
                    break;
                }
                case "explosion": {
                    if (!scriptEntry.hasObject("center")) {
                        Debug.echoError(scriptEntry, "physworld explosion needs center:<location>.");
                        return;
                    }
                    double[] c = Util.vec(scriptEntry.getObjectTag("center"), scriptEntry.context);
                    double r = Util.num(scriptEntry.getObjectTag("radius"), 5.0);
                    double imp = Util.num(scriptEntry.getObjectTag("impulse"), 50.0);
                    double up = Util.num(scriptEntry.getObjectTag("upward"), 0.2);
                    Util.world(id).explosion(c, r, imp, up);
                    break;
                }
                case "terrain": {
                    if (scriptEntry.hasObject("attach")) {
                        World bw = Bukkit.getWorld(scriptEntry.getObjectTag("attach").toString());
                        if (bw != null) {
                            Physizen.getInstance().attachWorld(id, bw);
                        }
                    }
                    break;
                }
                case "refresh": {
                    Util.world(id);
                    double[] min;
                    double[] max;
                    if (scriptEntry.hasObject("min") && scriptEntry.hasObject("max")) {
                        double[] a = Util.vec(scriptEntry.getObjectTag("min"), scriptEntry.context);
                        double[] b = Util.vec(scriptEntry.getObjectTag("max"), scriptEntry.context);
                        min = new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
                        max = new double[]{Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
                    }
                    else if (scriptEntry.hasObject("center")) {
                        double[] c = Util.vec(scriptEntry.getObjectTag("center"), scriptEntry.context);
                        double r = Util.num(scriptEntry.getObjectTag("radius"), 1.0);
                        min = new double[]{c[0] - r, c[1] - r, c[2] - r};
                        max = new double[]{c[0] + r, c[1] + r, c[2] + r};
                    }
                    else {
                        Debug.echoError(scriptEntry, "physworld refresh needs center:<location> (radius:<n>) or min:<location> max:<location>.");
                        return;
                    }
                    Physizen.getInstance().getTerrainManager().invalidateArea(id, min, max);
                    break;
                }
                case "save": {
                    if (!scriptEntry.hasObject("file")) {
                        Debug.echoError(scriptEntry, "physworld save needs file:<filename>.");
                        return;
                    }
                    String fileName = scriptEntry.getElement("file").asString();
                    PhysWorld pw = Util.world(id);
                    List<String> bodyNames = new ArrayList<>();
                    for (PhysBody pb : pw.bodies()) {
                        bodyNames.add(pb.name);
                    }
                    MapTag root = SaveManager.serializePrefab(id, bodyNames);
                    SaveManager.saveToFile(root, fileName);
                    break;
                }
                case "load": {
                    if (!scriptEntry.hasObject("file")) {
                        Debug.echoError(scriptEntry, "physworld load needs file:<filename>.");
                        return;
                    }
                    String fileName = scriptEntry.getElement("file").asString();
                    MapTag root = SaveManager.loadFromFile(fileName);
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