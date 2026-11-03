package dev.egor201.physizen.denizen.command;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.bukkit.display.DisplayBinder;
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.denizen.DenizenServices;
import dev.egor201.physizen.denizen.save.PrefabFiles;
import dev.egor201.physizen.denizen.save.SaveManager;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.core.model.ShapeSpec;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

// <--[command]
// @Name physbody
// @Syntax physbody [create/remove/teleport/move/set/bind/unbind/activate/deactivate/save/load] (world:<world>) (name:<name>) ...
// @Required 1
// @Maximum 24
// @Short Controls Physizen physical bodies.
// @Group Physizen
// -->
public class PhysBodyCommand extends AbstractCommand {

    public PhysBodyCommand() {
        setName("physbody");
        setSyntax("physbody [create/remove/teleport/move/set/bind/unbind/activate/deactivate/save/load] (world:<world>) (name:<name>) ...");
        setRequiredArguments(1, 24);
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

        if (name == null && !act.equals("load") && !act.equals("paste") && !act.equals("spawn") && !act.equals("save")) {
            Debug.echoError(scriptEntry, "physbody " + act + " needs name:<id>.");
            return;
        }

        try {
            PhysWorld w = Args.world(worldId);

            switch (act) {
                case "create": {
                    if (name == null) {
                        Debug.echoError(scriptEntry, "physbody create needs name:<id>.");
                        return;
                    }
                    BodyDef def = new BodyDef();
                    def.name = name;
                    ObjectTag shapeObj = scriptEntry.getObjectTag("shape");
                    def.shape = ShapeParser.shape((ObjectTag) shapeObj, scriptEntry.context);
                    if (scriptEntry.hasObject("at") || scriptEntry.hasObject("position")) {
                        def.position = Args.vec(scriptEntry.getObjectTag(scriptEntry.hasObject("at") ? "at" : "position"), scriptEntry.context);
                    }
                    if (scriptEntry.hasObject("rotation")) {
                        def.rotation = Args.rotation(scriptEntry.getObjectTag("rotation"), scriptEntry.context);
                    }
                    else if (scriptEntry.hasObject("euler")) {
                        def.rotation = Args.euler(Args.vec(scriptEntry.getObjectTag("euler"), scriptEntry.context));
                    }
                    if (scriptEntry.hasObject("type")) {
                        def.type = MotionKind.valueOf(scriptEntry.getElement("type").asString().toUpperCase());
                    }
                    def.mass = Args.num(scriptEntry.getObjectTag("mass"), 0);
                    def.friction = Args.num(scriptEntry.getObjectTag("friction"), 0.5);
                    def.restitution = Args.num(scriptEntry.getObjectTag("restitution"), 0.0);
                    def.linearDamping = Args.num(scriptEntry.getObjectTag("linear_damping"), 0.05);
                    def.angularDamping = Args.num(scriptEntry.getObjectTag("angular_damping"), 0.05);
                    def.gravityFactor = Args.num(scriptEntry.getObjectTag("gravity"), 1.0);
                    def.group = (int) Args.num(scriptEntry.getObjectTag("group"), 0);
                    def.sensor = Args.bool(scriptEntry.getObjectTag("sensor"), false);
                    def.detectStatic = Args.bool(scriptEntry.getObjectTag("detect_static"), false);
                    def.notifyPersist = Args.bool(scriptEntry.getObjectTag("notify_persist"), false);
                    def.continuous = Args.bool(scriptEntry.getObjectTag("continuous"), false);
                    def.allowSleeping = Args.bool(scriptEntry.getObjectTag("allow_sleeping"), true);
                    def.notify = Args.bool(scriptEntry.getObjectTag("notify"), true);
                    def.removeOnFallOut = Args.bool(scriptEntry.getObjectTag("remove_on_fall"), true);
                    def.tag = Args.str(scriptEntry.getObjectTag("tag"), "");
                    def.maxLinearVelocity = Args.num(scriptEntry.getObjectTag("max_velocity"), 500);
                    def.maxAngularVelocity = Args.num(scriptEntry.getObjectTag("max_angular_velocity"), 47.1238898);

                    if (scriptEntry.hasObject("velocity")) {
                        def.linearVelocity = Args.vec(scriptEntry.getObjectTag("velocity"), scriptEntry.context);
                    }
                    if (scriptEntry.hasObject("angular_velocity")) {
                        def.angularVelocity = Args.vec(scriptEntry.getObjectTag("angular_velocity"), scriptEntry.context);
                    }

                    w.createBody(def);
                    break;
                }
                case "remove": {
                    if (name == null) return;
                    w.removeBody(name);
                    PhysizenServices.get().displays.unbindAll(worldId, name);
                    break;
                }
                case "teleport": {
                    PhysBody b = w.body(name);
                    if (b == null) return;
                    double[] pos = Args.vec(scriptEntry.getObjectTag("to"), scriptEntry.context);
                    double[] rot = readRotation(scriptEntry);
                    b.teleport(pos, rot);
                    break;
                }
                case "move": {
                    PhysBody b = w.body(name);
                    if (b == null) return;
                    double[] pos = Args.vec(scriptEntry.getObjectTag("to"), scriptEntry.context);
                    double[] rot = readRotation(scriptEntry);
                    double seconds = Args.num(scriptEntry.getObjectTag("seconds"), 1.0 / 20.0);
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
                    if (b.def.internal) {
                        throw new PhysException("Terrain bodies cannot be modified.");
                    }
                    MotionKind newType = null;
                    if (scriptEntry.hasObject("type")) {
                        try {
                            newType = MotionKind.valueOf(scriptEntry.getElement("type").asString().toUpperCase());
                        }
                        catch (IllegalArgumentException e) {
                            throw new PhysException("Unknown body type '" + scriptEntry.getElement("type").asString() + "', expected static, kinematic or dynamic.");
                        }
                    }
                    ShapeSpec newShape = null;
                    if (scriptEntry.hasObject("shape")) {
                        newShape = ShapeParser.shape(scriptEntry.getObjectTag("shape"), scriptEntry.context);
                    }
                    MotionKind targetType = newType != null ? newType : b.def.type;
                    ShapeSpec targetShape = newShape != null ? newShape : b.def.shape;
                    if (targetType == MotionKind.DYNAMIC && targetShape.requiresNonDynamic()) {
                        throw new PhysException("Mesh shapes can only be used by static or kinematic bodies.");
                    }
                    double scaleFactor = 1.0;
                    boolean hasScale = scriptEntry.hasObject("scale");
                    if (hasScale) {
                        scaleFactor = Args.num(scriptEntry.getObjectTag("scale"), 1.0);
                        if (!(scaleFactor > 0)) {
                            throw new PhysException("Scale must be positive.");
                        }
                    }
                    int newGroup = -1;
                    if (scriptEntry.hasObject("group")) {
                        newGroup = (int) Args.num(scriptEntry.getObjectTag("group"), 0);
                        if (newGroup < 0 || newGroup >= PhysWorld.GROUPS) {
                            throw new PhysException("Group must be between 0 and " + (PhysWorld.GROUPS - 1) + ".");
                        }
                    }
                    if (newType != null && newType != MotionKind.DYNAMIC) {
                        b.setType(newType);
                    }
                    if (newShape != null) {
                        b.reshape(newShape);
                    }
                    if (hasScale) {
                        b.scale(scaleFactor);
                        if (Args.bool(scriptEntry.getObjectTag("scale_displays"), true)) {
                            PhysizenServices.get().displays.rescale(worldId, name, scaleFactor);
                        }
                    }
                    if (newType == MotionKind.DYNAMIC) {
                        b.setType(newType);
                    }
                    if (scriptEntry.hasObject("mass")) {
                        b.setMass(Args.num(scriptEntry.getObjectTag("mass"), 0));
                    }
                    if (newGroup >= 0) {
                        b.setGroup(newGroup);
                    }
                    if (scriptEntry.hasObject("sensor")) {
                        b.setSensor(Args.bool(scriptEntry.getObjectTag("sensor"), false));
                    }
                    if (scriptEntry.hasObject("continuous")) {
                        b.setContinuous(Args.bool(scriptEntry.getObjectTag("continuous"), false));
                    }
                    if (scriptEntry.hasObject("allow_sleeping")) {
                        b.setAllowSleeping(Args.bool(scriptEntry.getObjectTag("allow_sleeping"), true));
                    }
                    if (scriptEntry.hasObject("max_velocity")) {
                        b.setMaxVelocities(Args.num(scriptEntry.getObjectTag("max_velocity"), 500), null);
                    }
                    if (scriptEntry.hasObject("max_angular_velocity")) {
                        b.setMaxVelocities(null, Args.num(scriptEntry.getObjectTag("max_angular_velocity"), 47.1238898));
                    }
                    if (scriptEntry.hasObject("notify")) {
                        b.setNotify(Args.bool(scriptEntry.getObjectTag("notify"), true));
                    }
                    if (scriptEntry.hasObject("notify_persist")) {
                        b.setNotifyPersist(Args.bool(scriptEntry.getObjectTag("notify_persist"), false));
                    }
                    if (scriptEntry.hasObject("remove_on_fall")) {
                        b.setRemoveOnFallOut(Args.bool(scriptEntry.getObjectTag("remove_on_fall"), true));
                    }
                    if (scriptEntry.hasObject("display_scale")) {
                        double[] ds = Args.vec(scriptEntry.getObjectTag("display_scale"), scriptEntry.context);
                        PhysizenServices.get().displays.setScale(worldId, name, new Vector3f((float) ds[0], (float) ds[1], (float) ds[2]));
                    }
                    if (scriptEntry.hasObject("friction")) b.setFriction(Args.num(scriptEntry.getObjectTag("friction"), 0.5));
                    if (scriptEntry.hasObject("restitution")) b.setRestitution(Args.num(scriptEntry.getObjectTag("restitution"), 0));
                    if (scriptEntry.hasObject("gravity")) b.setGravityFactor(Args.num(scriptEntry.getObjectTag("gravity"), 1));
                    if (scriptEntry.hasObject("damping")) {
                        double[] d = Args.pair(scriptEntry.getObjectTag("damping"));
                        b.setDamping(d[0], d[1]);
                    }
                    if (scriptEntry.hasObject("linear_damping")) {
                        b.setDamping(Args.num(scriptEntry.getObjectTag("linear_damping"), 0.05), null);
                    }
                    if (scriptEntry.hasObject("angular_damping")) {
                        b.setDamping(null, Args.num(scriptEntry.getObjectTag("angular_damping"), 0.05));
                    }
                    if (scriptEntry.hasObject("tag")) {
                        b.tag = Args.str(scriptEntry.getObjectTag("tag"), "");
                    }
                    break;
                }
                case "bind": {
                    List<EntityTag> ents = Args.entities(scriptEntry.getObjectTag("entities"), scriptEntry.context);
                    for (EntityTag et : ents) {
                        DisplayBinder.Binding binding = new DisplayBinder.Binding(et.getBukkitEntity());
                        if (scriptEntry.hasObject("offset")) {
                            double[] o = Args.vec(scriptEntry.getObjectTag("offset"), scriptEntry.context);
                            binding.offset = new Vector3f((float) o[0], (float) o[1], (float) o[2]);
                        }
                        if (scriptEntry.hasObject("scale")) {
                            double[] s = Args.vec(scriptEntry.getObjectTag("scale"), scriptEntry.context);
                            binding.scale = new Vector3f((float) s[0], (float) s[1], (float) s[2]);
                        }

                        ObjectTag interp = scriptEntry.hasObject("interpolation_duration") 
                                ? scriptEntry.getObjectTag("interpolation_duration") 
                                : scriptEntry.getObjectTag("interpolation");
                        binding.interpolationDuration = (int) Args.num(interp, 3);

                        binding.teleportDuration = (int) Args.num(scriptEntry.getObjectTag("teleport_duration"), 3);

                        ObjectTag start = scriptEntry.hasObject("interpolation_start") 
                                ? scriptEntry.getObjectTag("interpolation_start") 
                                : scriptEntry.getObjectTag("interpolation_delay");
                        binding.interpolationStart = (int) Args.num(start, 0);

                        binding.removeWithBody = Args.bool(scriptEntry.getObjectTag("remove_with_body"), true);
                        PhysizenServices.get().displays.bind(worldId, name, binding);
                        DenizenServices.persistence().persist(et.getBukkitEntity(), worldId, name);
                    }
                    break;
                }
                case "unbind": {
                    List<EntityTag> ents = Args.entities(scriptEntry.getObjectTag("entities"), scriptEntry.context);
                    for (EntityTag et : ents) {
                        PhysizenServices.get().displays.unbind(worldId, name, et.getBukkitEntity().getUniqueId());
                    }
                    break;
                }
                case "save": {
                    if (!scriptEntry.hasObject("file")) {
                        Debug.echoError(scriptEntry, "physbody save needs file:<name>.");
                        return;
                    }
                    String fileName = scriptEntry.getElement("file").asString();
                    MapTag result;
                    if (scriptEntry.hasObject("bodies")) {
                        ListTag bList = ((ObjectTag) scriptEntry.getObjectTag("bodies")).asType(ListTag.class, scriptEntry.context);
                        List<String> names = new ArrayList<>();
                        for (ObjectTag obj : bList.objectForms) {
                            names.add(obj.toString());
                        }
                        result = SaveManager.serializePrefab(worldId, names);
                    }
                    else if (name != null) {
                        PhysBody pb = w.body(name);
                        if (pb == null) {
                            Debug.echoError(scriptEntry, "Unknown body: " + name);
                            return;
                        }
                        result = SaveManager.serializeBody(worldId, pb);
                    }
                    else {
                        Debug.echoError(scriptEntry, "physbody save needs name:<name> or bodies:<list>.");
                        return;
                    }
                    PrefabFiles.saveToFile(result, fileName);
                    break;
                }
                case "load":
                case "paste":
                case "spawn": {
                    if (!scriptEntry.hasObject("file")) {
                        Debug.echoError(scriptEntry, "physbody load needs file:<name>.");
                        return;
                    }
                    String fileName = scriptEntry.getElement("file").asString();
                    MapTag root = PrefabFiles.loadFromFile(fileName);
                    double[] targetPos = scriptEntry.hasObject("at") || scriptEntry.hasObject("position")
                            ? Args.vec(scriptEntry.getObjectTag(scriptEntry.hasObject("at") ? "at" : "position"), scriptEntry.context)
                            : null;
                    double[] targetRot = readRotation(scriptEntry);
                    String prefix = scriptEntry.hasObject("prefix") ? scriptEntry.getElement("prefix").asString() : "";
                    String overrideName = scriptEntry.hasObject("name") ? scriptEntry.getElement("name").asString() : null;

                    SaveManager.spawnPrefab(worldId, root, targetPos, targetRot, prefix, overrideName);
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

    private static double[] readRotation(ScriptEntry scriptEntry) {
        if (scriptEntry.hasObject("rotation")) {
            return Args.rotation(scriptEntry.getObjectTag("rotation"), scriptEntry.context);
        }
        if (scriptEntry.hasObject("euler")) {
            return Args.euler(Args.vec(scriptEntry.getObjectTag("euler"), scriptEntry.context));
        }
        return null;
    }
}