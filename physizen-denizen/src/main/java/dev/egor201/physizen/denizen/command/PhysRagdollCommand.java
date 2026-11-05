package dev.egor201.physizen.denizen.command;

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
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.core.world.RagdollBuilder;
import dev.egor201.physizen.core.world.RagdollSpec;
import dev.egor201.physizen.denizen.args.Args;
import com.denizenscript.denizen.objects.EntityTag;
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.denizen.args.ShapeParser;
import java.util.List;

// <--[command]
// @Name physragdoll
// @Syntax physragdoll [create/remove/bind/unbind] (world:<world>) name:<name> (preset:<humanoid>) (bones:<list of maps>) (position:<loc>) (rotation:<rotation>) (scale:<number>) (mass:<number>) (velocity:<vec>) (friction:<number>) (restitution:<number>) (linear_damping:<number>) (angular_damping:<number>) (group:<0-15>) (joint_friction:<number>) (entities:<map of bone to entity>) (offset:<vec>) (scale:<vec>) (rotation_offset:<rotation>) (interpolation:<ticks>) (teleport_duration:<ticks>) (remove_with_body:<boolean>)
// @Required 2
// @Maximum 24
// @Short Creates or removes a ragdoll made of bodies connected by swing-twist joints, or binds display entities to its bones.
// @Group Physizen
// -->
public class PhysRagdollCommand extends AbstractCommand {

    public PhysRagdollCommand() {
        setName("physragdoll");
        setSyntax("physragdoll [create/remove/bind/unbind] (world:<world>) name:<name> (preset:<humanoid>) (bones:<list of maps>) (position:<loc>) (rotation:<rotation>) (scale:<number>) (mass:<number>) (velocity:<vec>) (friction:<number>) (restitution:<number>) (linear_damping:<number>) (angular_damping:<number>) (group:<0-15>) (joint_friction:<number>) (entities:<map of bone to entity>) (offset:<vec>) (scale:<vec>) (rotation_offset:<rotation>) (interpolation:<ticks>) (teleport_duration:<ticks>) (remove_with_body:<boolean>)");
        setRequiredArguments(2, 24);
        isProcedural = false;
        autoCompile();
        addRemappedPrefixes("name", "id");
    }

    private static double[] pairOrDefault(MapTag map, String key, double a, double b) {
        if (!map.containsKey(key)) {
            return new double[]{a, b};
        }
        return Args.pair(map.getObject(key));
    }

    private static RagdollSpec customSpec(ListTag bones, ScriptEntry scriptEntry) {
        RagdollSpec spec = new RagdollSpec();
        spec.preset = "custom";
        for (ObjectTag obj : bones.objectForms) {
            MapTag m = obj.asType(MapTag.class, scriptEntry.context);
            if (m == null) {
                throw new PhysException("Every bone must be a map.");
            }
            RagdollSpec.Bone b = new RagdollSpec.Bone();
            b.name = Args.str(m.getObject("name"), null);
            b.parent = m.containsKey("parent") ? Args.str(m.getObject("parent"), null) : null;
            if (!m.containsKey("shape")) {
                throw new PhysException("Bone '" + b.name + "' needs a shape.");
            }
            b.shape = ShapeParser.shape(m.getObject("shape").asType(MapTag.class, scriptEntry.context), scriptEntry.context);
            b.center = Args.vec(m.getObject("center"), scriptEntry.context);
            b.anchor = m.containsKey("anchor") ? Args.vec(m.getObject("anchor"), scriptEntry.context) : b.center.clone();
            if (m.containsKey("twist_axis")) b.twistAxis = Args.vec(m.getObject("twist_axis"), scriptEntry.context);
            if (m.containsKey("plane_axis")) b.planeAxis = Args.vec(m.getObject("plane_axis"), scriptEntry.context);
            double[] swing = pairOrDefault(m, "swing", b.swingNormal, b.swingPlane);
            b.swingNormal = swing[0];
            b.swingPlane = swing[1];
            double[] twist = pairOrDefault(m, "twist", b.twistMin, b.twistMax);
            b.twistMin = twist[0];
            b.twistMax = twist[1];
            b.massWeight = Args.num(m.getObject("mass_weight"), 1);
            b.jointFriction = Args.num(m.getObject("joint_friction"), 0);
            spec.bones.add(b);
        }
        return spec;
    }

    public enum Action {
        REMOVE, UNBIND, BIND, CREATE
    }

    public static void autoExecute(ScriptEntry scriptEntry,
            @ArgName("action") @ArgLinear Action action,
            @ArgName("name") @ArgPrefixed @ArgDefaultNull ElementTag nameArg,
            @ArgName("world") @ArgPrefixed @ArgDefaultNull ElementTag worldArg,
            @ArgName("shape") @ArgPrefixed @ArgDefaultNull ObjectTag shapeArg,
            @ArgName("center") @ArgPrefixed @ArgDefaultNull ObjectTag centerArg,
            @ArgName("anchor") @ArgPrefixed @ArgDefaultNull ObjectTag anchorArg,
            @ArgName("twist_axis") @ArgPrefixed @ArgDefaultNull ObjectTag twistAxisArg,
            @ArgName("plane_axis") @ArgPrefixed @ArgDefaultNull ObjectTag planeAxisArg,
            @ArgName("entities") @ArgPrefixed @ArgDefaultNull ObjectTag entitiesArg,
            @ArgName("bones") @ArgPrefixed @ArgDefaultNull ObjectTag bonesArg,
            @ArgName("preset") @ArgPrefixed @ArgDefaultNull ElementTag presetArg,
            @ArgName("humanoid") @ArgPrefixed @ArgDefaultNull ObjectTag humanoidArg,
            @ArgName("position") @ArgPrefixed @ArgDefaultNull ObjectTag positionArg,
            @ArgName("rotation") @ArgPrefixed @ArgDefaultNull ObjectTag rotationArg,
            @ArgName("velocity") @ArgPrefixed @ArgDefaultNull ObjectTag velocityArg,
            @ArgName("scale") @ArgPrefixed @ArgDefaultNull ObjectTag scaleArg,
            @ArgName("mass") @ArgPrefixed @ArgDefaultNull ObjectTag massArg,
            @ArgName("friction") @ArgPrefixed @ArgDefaultNull ObjectTag frictionArg,
            @ArgName("restitution") @ArgPrefixed @ArgDefaultNull ObjectTag restitutionArg,
            @ArgName("linear_damping") @ArgPrefixed @ArgDefaultNull ObjectTag linearDampingArg,
            @ArgName("angular_damping") @ArgPrefixed @ArgDefaultNull ObjectTag angularDampingArg,
            @ArgName("joint_friction") @ArgPrefixed @ArgDefaultNull ObjectTag jointFrictionArg,
            @ArgName("group") @ArgPrefixed @ArgDefaultNull ObjectTag groupArg,
            @ArgName("offset") @ArgPrefixed @ArgDefaultNull ObjectTag offsetArg,
            @ArgName("rotation_offset") @ArgPrefixed @ArgDefaultNull ObjectTag rotationOffsetArg,
            @ArgName("interpolation_duration") @ArgPrefixed @ArgDefaultNull ObjectTag interpolationDurationArg,
            @ArgName("interpolation") @ArgPrefixed @ArgDefaultNull ObjectTag interpolationArg,
            @ArgName("teleport_duration") @ArgPrefixed @ArgDefaultNull ObjectTag teleportDurationArg,
            @ArgName("remove_with_body") @ArgPrefixed @ArgDefaultNull ObjectTag removeWithBodyArg) {
        ElementTag action = actionArg;
        ElementTag nameTag = nameArg;
        if (action == null || nameTag == null) {
            Debug.echoError(scriptEntry, "physragdoll needs an action and name:<name>.");
            return;
        }
        String act = action.name().toLowerCase();
        String worldId = (worldArg != null) ? worldArg.asString() : "default";
        try {
            PhysWorld w = Args.world(worldId);
            if (action == Action.REMOVE) {
                w.removeRagdoll(nameTag.asString());
                return;
            }
            if (action == Action.UNBIND) {
                for (String part : w.ragdollParts(nameTag.asString())) {
                    PhysizenServices.get().displays.unbindAll(worldId, part);
                }
                return;
            }
            if (action == Action.BIND) {
                if (!(entitiesArg != null)) {
                    throw new PhysException("physragdoll bind needs entities:<map of bone to entity>.");
                }
                MapTag map = ((ObjectTag) entitiesArg).asType(MapTag.class, scriptEntry.context);
                if (map == null || map.map.isEmpty()) {
                    throw new PhysException("entities must be a non-empty map of bone to entity.");
                }
                List<String> parts = w.ragdollParts(nameTag.asString());
                for (Object keyTag : map.map.keySet()) {
                    String bone = keyTag.toString();
                    String part = RagdollBuilder.partName(nameTag.asString(), bone);
                    if (!parts.contains(part)) {
                        throw new PhysException("Ragdoll '" + nameTag.asString() + "' has no bone '" + bone + "'.");
                    }
                    for (EntityTag et : Args.entities(map.getObject(bone), scriptEntry.context)) {
                        PhysizenServices.get().displays.bind(worldId, part, BindingArgs.create(et, scriptEntry, offsetArg, scaleArg, rotationOffsetArg, interpolationDurationArg, interpolationArg, teleportDurationArg, removeWithBodyArg));
                    }
                }
                return;
            }
            if (!action == Action.CREATE) {
                throw new PhysException("Unknown physragdoll action '" + act + "', expected create, remove, bind or unbind.");
            }
            RagdollSpec spec;
            if (bonesArg != null) {
                ListTag bones = ((ObjectTag) bonesArg).asType(ListTag.class, scriptEntry.context);
                if (bones == null || bones.isEmpty()) {
                    throw new PhysException("bones must be a non-empty list of maps.");
                }
                spec = customSpec(bones, scriptEntry);
            }
            else {
                String preset = (presetArg != null) ? CoreUtilities.toLowerCase(presetArg.asString()) : "humanoid";
                if (!preset.equals("humanoid")) {
                    throw new PhysException("Unknown ragdoll preset '" + preset + "', expected humanoid.");
                }
                spec = RagdollSpec.humanoid();
            }
            RagdollBuilder.Options o = new RagdollBuilder.Options();
            o.id = nameTag.asString();
            if (positionArg != null) o.position = Args.vec(positionArg, scriptEntry.context);
            if (rotationArg != null) o.rotation = Args.rotation(rotationArg, scriptEntry.context);
            if (velocityArg != null) o.velocity = Args.vec(velocityArg, scriptEntry.context);
            o.scale = Args.num(scaleArg, o.scale);
            o.totalMass = Args.num(massArg, o.totalMass);
            o.friction = Args.num(frictionArg, o.friction);
            o.restitution = Args.num(restitutionArg, o.restitution);
            o.linearDamping = Args.num(linearDampingArg, o.linearDamping);
            o.angularDamping = Args.num(angularDampingArg, o.angularDamping);
            o.jointFriction = Args.num(jointFrictionArg, o.jointFriction);
            o.group = (int) Args.num(groupArg, o.group);
            w.spawnRagdoll(spec, o);
        }
        catch (PhysException | IllegalArgumentException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }

}
