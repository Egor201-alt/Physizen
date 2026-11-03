package dev.egor201.physizen.denizen.command;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.ObjectTag;
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
import dev.egor201.physizen.denizen.args.ShapeParser;

// <--[command]
// @Name physragdoll
// @Syntax physragdoll [create/remove] (world:<world>) name:<name> (preset:<humanoid>) (bones:<list of maps>) (position:<loc>) (rotation:<rotation>) (scale:<number>) (mass:<number>) (velocity:<vec>) (friction:<number>) (restitution:<number>) (linear_damping:<number>) (angular_damping:<number>) (group:<0-15>) (joint_friction:<number>)
// @Required 2
// @Maximum 16
// @Short Creates or removes a ragdoll made of bodies connected by swing-twist joints.
// @Group Physizen
// -->
public class PhysRagdollCommand extends AbstractCommand {

    public PhysRagdollCommand() {
        setName("physragdoll");
        setSyntax("physragdoll [create/remove] (world:<world>) name:<name> (preset:<humanoid>) (bones:<list of maps>) (position:<loc>) (rotation:<rotation>) (scale:<number>) (mass:<number>) (velocity:<vec>) (friction:<number>) (restitution:<number>) (linear_damping:<number>) (angular_damping:<number>) (group:<0-15>) (joint_friction:<number>)");
        setRequiredArguments(2, 16);
        isProcedural = false;
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action") && arg.prefix == null) {
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

    @Override
    public void execute(ScriptEntry scriptEntry) {
        ElementTag action = scriptEntry.getElement("action");
        ElementTag nameTag = scriptEntry.getElement("name");
        if (action == null || nameTag == null) {
            Debug.echoError(scriptEntry, "physragdoll needs an action and name:<name>.");
            return;
        }
        String act = CoreUtilities.toLowerCase(action.asString());
        String worldId = scriptEntry.hasObject("world") ? scriptEntry.getElement("world").asString() : "default";
        try {
            PhysWorld w = Args.world(worldId);
            if (act.equals("remove")) {
                w.removeRagdoll(nameTag.asString());
                return;
            }
            if (!act.equals("create")) {
                throw new PhysException("Unknown physragdoll action '" + act + "', expected create or remove.");
            }
            RagdollSpec spec;
            if (scriptEntry.hasObject("bones")) {
                ListTag bones = scriptEntry.getObjectTag("bones").asType(ListTag.class, scriptEntry.context);
                if (bones == null || bones.isEmpty()) {
                    throw new PhysException("bones must be a non-empty list of maps.");
                }
                spec = customSpec(bones, scriptEntry);
            }
            else {
                String preset = scriptEntry.hasObject("preset") ? CoreUtilities.toLowerCase(scriptEntry.getElement("preset").asString()) : "humanoid";
                if (!preset.equals("humanoid")) {
                    throw new PhysException("Unknown ragdoll preset '" + preset + "', expected humanoid.");
                }
                spec = RagdollSpec.humanoid();
            }
            RagdollBuilder.Options o = new RagdollBuilder.Options();
            o.id = nameTag.asString();
            if (scriptEntry.hasObject("position")) o.position = Args.vec(scriptEntry.getObjectTag("position"), scriptEntry.context);
            if (scriptEntry.hasObject("rotation")) o.rotation = Args.rotation(scriptEntry.getObjectTag("rotation"), scriptEntry.context);
            if (scriptEntry.hasObject("velocity")) o.velocity = Args.vec(scriptEntry.getObjectTag("velocity"), scriptEntry.context);
            o.scale = Args.num(scriptEntry.getObjectTag("scale"), o.scale);
            o.totalMass = Args.num(scriptEntry.getObjectTag("mass"), o.totalMass);
            o.friction = Args.num(scriptEntry.getObjectTag("friction"), o.friction);
            o.restitution = Args.num(scriptEntry.getObjectTag("restitution"), o.restitution);
            o.linearDamping = Args.num(scriptEntry.getObjectTag("linear_damping"), o.linearDamping);
            o.angularDamping = Args.num(scriptEntry.getObjectTag("angular_damping"), o.angularDamping);
            o.jointFriction = Args.num(scriptEntry.getObjectTag("joint_friction"), o.jointFriction);
            o.group = (int) Args.num(scriptEntry.getObjectTag("group"), o.group);
            w.spawnRagdoll(spec, o);
        }
        catch (PhysException | IllegalArgumentException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }
}
