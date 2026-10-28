package dev.egor201.physizen.denizen;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.Physizen;
import dev.egor201.physizen.core.PhysEngine;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.PhysWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;

// <--[command]
// @Name physworld
// @Syntax physworld [create/remove/pause/resume/step/clear/gravity/time_scale/rate/collision/explosion/terrain] (id:<id>) ...
// @Required 1
// @Maximum 8
// @Short Controls Physizen physics worlds.
// @Group Physizen
// -->
public class PhysWorldCommand extends AbstractCommand {

    public PhysWorldCommand() {
        setName("physworld");
        setSyntax("physworld [create/remove/pause/resume/step/clear/gravity/time_scale/rate/collision/explosion/terrain] (id:<id>) ...");
        setRequiredArguments(1, 8);
        isProcedural = false;
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action")) {
                scriptEntry.addObject("action", arg.asElement());
            }
            else if (!scriptEntry.hasObject("id") && arg.matchesPrefix("id", "world")) {
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
                    if (PhysEngine.get().world(id) != null) {
                        break;
                    }
                    int rate = scriptEntry.hasObject("rate") ? (int) Util.num(scriptEntry.getObjectTag("rate"), 60) : 60;
                    PhysWorld w = PhysEngine.get().createWorld(id, rate);
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
