package dev.egor201.physizen.denizen.command;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultNull;
import com.denizenscript.denizencore.scripts.commands.generator.ArgLinear;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
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
        autoCompile();
        addRemappedPrefixes("name", "body");
    }

    public enum Action {
        CREATE, REMOVE, TELEPORT, MOVE, ACTIVATE, DEACTIVATE, SET, BIND, UNBIND, SAVE, LOAD, PASTE, SPAWN
    }

    public static void autoExecute(ScriptEntry scriptEntry,
            @ArgName("action") @ArgLinear Action action,
            @ArgName("name_linear") @ArgLinear @ArgDefaultNull ElementTag nameLinear,
            @ArgName("name") @ArgPrefixed @ArgDefaultNull ElementTag nameArg,
            @ArgName("world") @ArgPrefixed @ArgDefaultNull ElementTag worldArg,
            @ArgName("shape") @ArgPrefixed @ArgDefaultNull ObjectTag shapeArg,
            @ArgName("at") @ArgPrefixed @ArgDefaultNull ObjectTag atArg,
            @ArgName("position") @ArgPrefixed @ArgDefaultNull ObjectTag positionArg,
            @ArgName("rotation") @ArgPrefixed @ArgDefaultNull ObjectTag rotationArg,
            @ArgName("euler") @ArgPrefixed @ArgDefaultNull ObjectTag eulerArg,
            @ArgName("type") @ArgPrefixed @ArgDefaultNull MotionKind typeArg,
            @ArgName("mass") @ArgPrefixed @ArgDefaultNull ObjectTag massArg,
            @ArgName("friction") @ArgPrefixed @ArgDefaultNull ObjectTag frictionArg,
            @ArgName("restitution") @ArgPrefixed @ArgDefaultNull ObjectTag restitutionArg,
            @ArgName("linear_damping") @ArgPrefixed @ArgDefaultNull ObjectTag linearDampingArg,
            @ArgName("angular_damping") @ArgPrefixed @ArgDefaultNull ObjectTag angularDampingArg,
            @ArgName("gravity") @ArgPrefixed @ArgDefaultNull ObjectTag gravityArg,
            @ArgName("group") @ArgPrefixed @ArgDefaultNull ObjectTag groupArg,
            @ArgName("sensor") @ArgPrefixed @ArgDefaultNull ObjectTag sensorArg,
            @ArgName("detect_static") @ArgPrefixed @ArgDefaultNull ObjectTag detectStaticArg,
            @ArgName("notify_persist") @ArgPrefixed @ArgDefaultNull ObjectTag notifyPersistArg,
            @ArgName("continuous") @ArgPrefixed @ArgDefaultNull ObjectTag continuousArg,
            @ArgName("allow_sleeping") @ArgPrefixed @ArgDefaultNull ObjectTag allowSleepingArg,
            @ArgName("notify") @ArgPrefixed @ArgDefaultNull ObjectTag notifyArg,
            @ArgName("remove_on_fall") @ArgPrefixed @ArgDefaultNull ObjectTag removeOnFallArg,
            @ArgName("tag") @ArgPrefixed @ArgDefaultNull ObjectTag tagArg,
            @ArgName("max_velocity") @ArgPrefixed @ArgDefaultNull ObjectTag maxVelocityArg,
            @ArgName("max_angular_velocity") @ArgPrefixed @ArgDefaultNull ObjectTag maxAngularVelocityArg,
            @ArgName("velocity") @ArgPrefixed @ArgDefaultNull ObjectTag velocityArg,
            @ArgName("angular_velocity") @ArgPrefixed @ArgDefaultNull ObjectTag angularVelocityArg,
            @ArgName("to") @ArgPrefixed @ArgDefaultNull ObjectTag toArg,
            @ArgName("seconds") @ArgPrefixed @ArgDefaultNull ObjectTag secondsArg,
            @ArgName("scale") @ArgPrefixed @ArgDefaultNull ObjectTag scaleArg,
            @ArgName("scale_displays") @ArgPrefixed @ArgDefaultNull ObjectTag scaleDisplaysArg,
            @ArgName("display_scale") @ArgPrefixed @ArgDefaultNull ObjectTag displayScaleArg,
            @ArgName("damping") @ArgPrefixed @ArgDefaultNull ObjectTag dampingArg,
            @ArgName("entities") @ArgPrefixed @ArgDefaultNull ObjectTag entitiesArg,
            @ArgName("offset") @ArgPrefixed @ArgDefaultNull ObjectTag offsetArg,
            @ArgName("interpolation_duration") @ArgPrefixed @ArgDefaultNull ObjectTag interpolationDurationArg,
            @ArgName("interpolation") @ArgPrefixed @ArgDefaultNull ObjectTag interpolationArg,
            @ArgName("teleport_duration") @ArgPrefixed @ArgDefaultNull ObjectTag teleportDurationArg,
            @ArgName("interpolation_start") @ArgPrefixed @ArgDefaultNull ObjectTag interpolationStartArg,
            @ArgName("interpolation_delay") @ArgPrefixed @ArgDefaultNull ObjectTag interpolationDelayArg,
            @ArgName("remove_with_body") @ArgPrefixed @ArgDefaultNull ObjectTag removeWithBodyArg,
            @ArgName("file") @ArgPrefixed @ArgDefaultNull ElementTag fileArg,
            @ArgName("bodies") @ArgPrefixed @ArgDefaultNull ObjectTag bodiesArg,
            @ArgName("prefix") @ArgPrefixed @ArgDefaultNull ElementTag prefixArg) {
        ElementTag nameValue = nameArg != null ? nameArg : nameLinear;
        ElementTag action = actionArg;
        if (action == null) {
            Debug.echoError(scriptEntry, "Missing action for physbody command.");
            return;
        }
        String act = action.name().toLowerCase();
        String worldId = (worldArg != null) ? worldArg.asString() : "default";
        String name = (nameValue != null) ? nameValue.asString() : null;

        if (name == null && !action == Action.LOAD && !action == Action.PASTE && !action == Action.SPAWN && !action == Action.SAVE) {
            Debug.echoError(scriptEntry, "physbody " + act + " needs name:<id>.");
            return;
        }

        try {
            PhysWorld w = Args.world(worldId);

            switch (action) {
                case CREATE: {
                    if (name == null) {
                        Debug.echoError(scriptEntry, "physbody create needs name:<id>.");
                        return;
                    }
                    BodyDef def = new BodyDef();
                    def.name = name;
                    ObjectTag shapeObj = shapeArg;
                    def.shape = ShapeParser.shape((ObjectTag) shapeObj, scriptEntry.context);
                    if ((atArg != null) || (positionArg != null)) {
                        def.position = Args.vec((atArg != null ? atArg : positionArg), scriptEntry.context);
                    }
                    if (rotationArg != null) {
                        def.rotation = Args.rotation(rotationArg, scriptEntry.context);
                    }
                    else if (eulerArg != null) {
                        def.rotation = Args.euler(Args.vec(eulerArg, scriptEntry.context));
                    }
                    if (typeArg != null) {
                        def.type = typeArg;
                    }
                    def.mass = Args.num(massArg, 0);
                    def.friction = Args.num(frictionArg, 0.5);
                    def.restitution = Args.num(restitutionArg, 0.0);
                    def.linearDamping = Args.num(linearDampingArg, 0.05);
                    def.angularDamping = Args.num(angularDampingArg, 0.05);
                    def.gravityFactor = Args.num(gravityArg, 1.0);
                    def.group = (int) Args.num(groupArg, 0);
                    def.sensor = Args.bool(sensorArg, false);
                    def.detectStatic = Args.bool(detectStaticArg, false);
                    def.notifyPersist = Args.bool(notifyPersistArg, false);
                    def.continuous = Args.bool(continuousArg, false);
                    def.allowSleeping = Args.bool(allowSleepingArg, true);
                    def.notify = Args.bool(notifyArg, true);
                    def.removeOnFallOut = Args.bool(removeOnFallArg, true);
                    def.tag = Args.str(tagArg, "");
                    def.maxLinearVelocity = Args.num(maxVelocityArg, 500);
                    def.maxAngularVelocity = Args.num(maxAngularVelocityArg, 47.1238898);

                    if (velocityArg != null) {
                        def.linearVelocity = Args.vec(velocityArg, scriptEntry.context);
                    }
                    if (angularVelocityArg != null) {
                        def.angularVelocity = Args.vec(angularVelocityArg, scriptEntry.context);
                    }

                    w.createBody(def);
                    break;
                }
                case REMOVE: {
                    if (name == null) return;
                    w.removeBody(name);
                    PhysizenServices.get().displays.unbindAll(worldId, name);
                    break;
                }
                case TELEPORT: {
                    PhysBody b = w.body(name);
                    if (b == null) return;
                    double[] pos = Args.vec(toArg, scriptEntry.context);
                    double[] rot = readRotation(scriptEntry, rotationArg, eulerArg);
                    b.teleport(pos, rot);
                    break;
                }
                case MOVE: {
                    PhysBody b = w.body(name);
                    if (b == null) return;
                    double[] pos = Args.vec(toArg, scriptEntry.context);
                    double[] rot = readRotation(scriptEntry, rotationArg, eulerArg);
                    double seconds = Args.num(secondsArg, 1.0 / 20.0);
                    b.moveKinematic(pos, rot, seconds);
                    break;
                }
                case ACTIVATE: {
                    PhysBody b = w.body(name);
                    if (b != null) b.activate();
                    break;
                }
                case DEACTIVATE: {
                    PhysBody b = w.body(name);
                    if (b != null) b.deactivate();
                    break;
                }
                case SET: {
                    PhysBody b = w.body(name);
                    if (b == null) return;
                    if (b.def.internal) {
                        throw new PhysException("Terrain bodies cannot be modified.");
                    }
                    MotionKind newType = typeArg;
                    ShapeSpec newShape = null;
                    if (shapeArg != null) {
                        newShape = ShapeParser.shape(shapeArg, scriptEntry.context);
                    }
                    MotionKind targetType = newType != null ? newType : b.def.type;
                    ShapeSpec targetShape = newShape != null ? newShape : b.def.shape;
                    if (targetType == MotionKind.DYNAMIC && targetShape.requiresNonDynamic()) {
                        throw new PhysException("Mesh shapes can only be used by static or kinematic bodies.");
                    }
                    double scaleFactor = 1.0;
                    boolean hasScale = (scaleArg != null);
                    if (hasScale) {
                        scaleFactor = Args.num(scaleArg, 1.0);
                        if (!(scaleFactor > 0)) {
                            throw new PhysException("Scale must be positive.");
                        }
                    }
                    int newGroup = -1;
                    if (groupArg != null) {
                        newGroup = (int) Args.num(groupArg, 0);
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
                        if (Args.bool(scaleDisplaysArg, true)) {
                            PhysizenServices.get().displays.rescale(worldId, name, scaleFactor);
                        }
                    }
                    if (newType == MotionKind.DYNAMIC) {
                        b.setType(newType);
                    }
                    if (massArg != null) {
                        b.setMass(Args.num(massArg, 0));
                    }
                    if (newGroup >= 0) {
                        b.setGroup(newGroup);
                    }
                    if (sensorArg != null) {
                        b.setSensor(Args.bool(sensorArg, false));
                    }
                    if (continuousArg != null) {
                        b.setContinuous(Args.bool(continuousArg, false));
                    }
                    if (allowSleepingArg != null) {
                        b.setAllowSleeping(Args.bool(allowSleepingArg, true));
                    }
                    if (maxVelocityArg != null) {
                        b.setMaxVelocities(Args.num(maxVelocityArg, 500), null);
                    }
                    if (maxAngularVelocityArg != null) {
                        b.setMaxVelocities(null, Args.num(maxAngularVelocityArg, 47.1238898));
                    }
                    if (notifyArg != null) {
                        b.setNotify(Args.bool(notifyArg, true));
                    }
                    if (notifyPersistArg != null) {
                        b.setNotifyPersist(Args.bool(notifyPersistArg, false));
                    }
                    if (removeOnFallArg != null) {
                        b.setRemoveOnFallOut(Args.bool(removeOnFallArg, true));
                    }
                    if (displayScaleArg != null) {
                        double[] ds = Args.vec(displayScaleArg, scriptEntry.context);
                        PhysizenServices.get().displays.setScale(worldId, name, new Vector3f((float) ds[0], (float) ds[1], (float) ds[2]));
                    }
                    if (frictionArg != null) b.setFriction(Args.num(frictionArg, 0.5));
                    if (restitutionArg != null) b.setRestitution(Args.num(restitutionArg, 0));
                    if (gravityArg != null) b.setGravityFactor(Args.num(gravityArg, 1));
                    if (dampingArg != null) {
                        double[] d = Args.pair(dampingArg);
                        b.setDamping(d[0], d[1]);
                    }
                    if (linearDampingArg != null) {
                        b.setDamping(Args.num(linearDampingArg, 0.05), null);
                    }
                    if (angularDampingArg != null) {
                        b.setDamping(null, Args.num(angularDampingArg, 0.05));
                    }
                    if (tagArg != null) {
                        b.tag = Args.str(tagArg, "");
                    }
                    break;
                }
                case BIND: {
                    List<EntityTag> ents = Args.entities(entitiesArg, scriptEntry.context);
                    for (EntityTag et : ents) {
                        DisplayBinder.Binding binding = new DisplayBinder.Binding(et.getBukkitEntity());
                        if (offsetArg != null) {
                            double[] o = Args.vec(offsetArg, scriptEntry.context);
                            binding.offset = new Vector3f((float) o[0], (float) o[1], (float) o[2]);
                        }
                        if (scaleArg != null) {
                            double[] s = Args.vec(scaleArg, scriptEntry.context);
                            binding.scale = new Vector3f((float) s[0], (float) s[1], (float) s[2]);
                        }

                        ObjectTag interp = (interpolationDurationArg != null) 
                                ? interpolationDurationArg 
                                : interpolationArg;
                        binding.interpolationDuration = (int) Args.num(interp, 3);

                        binding.teleportDuration = (int) Args.num(teleportDurationArg, 3);

                        ObjectTag start = (interpolationStartArg != null) 
                                ? interpolationStartArg 
                                : interpolationDelayArg;
                        binding.interpolationStart = (int) Args.num(start, 0);

                        binding.removeWithBody = Args.bool(removeWithBodyArg, true);
                        PhysizenServices.get().displays.bind(worldId, name, binding);
                        DenizenServices.persistence().persist(et.getBukkitEntity(), worldId, name);
                    }
                    break;
                }
                case UNBIND: {
                    List<EntityTag> ents = Args.entities(entitiesArg, scriptEntry.context);
                    for (EntityTag et : ents) {
                        PhysizenServices.get().displays.unbind(worldId, name, et.getBukkitEntity().getUniqueId());
                    }
                    break;
                }
                case SAVE: {
                    if (!(fileArg != null)) {
                        Debug.echoError(scriptEntry, "physbody save needs file:<name>.");
                        return;
                    }
                    String fileName = fileArg.asString();
                    MapTag result;
                    if (bodiesArg != null) {
                        ListTag bList = ((ObjectTag) bodiesArg).asType(ListTag.class, scriptEntry.context);
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
                case LOAD:
                case PASTE:
                case SPAWN: {
                    if (!(fileArg != null)) {
                        Debug.echoError(scriptEntry, "physbody load needs file:<name>.");
                        return;
                    }
                    String fileName = fileArg.asString();
                    MapTag root = PrefabFiles.loadFromFile(fileName);
                    double[] targetPos = (atArg != null) || (positionArg != null)
                            ? Args.vec((atArg != null ? atArg : positionArg), scriptEntry.context)
                            : null;
                    double[] targetRot = readRotation(scriptEntry, rotationArg, eulerArg);
                    String prefix = (prefixArg != null) ? prefixArg.asString() : "";
                    String overrideName = (nameValue != null) ? nameValue.asString() : null;

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


    private static double[] readRotation(ScriptEntry scriptEntry, ObjectTag rotationArg, ObjectTag eulerArg) {
        if (rotationArg != null) {
            return Args.rotation(rotationArg, scriptEntry.context);
        }
        if (eulerArg != null) {
            return Args.euler(Args.vec(eulerArg, scriptEntry.context));
        }
        return null;
    }
}