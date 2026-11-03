package dev.egor201.physizen.denizen.save;

import com.denizenscript.denizen.objects.ItemTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.objects.core.QuaternionTag;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.model.VehicleDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.bukkit.display.DisplayBinder;
import dev.egor201.physizen.denizen.DenizenServices;
import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeCodec;
import dev.egor201.physizen.denizen.args.ShapeParser;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.core.model.Snapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SaveManager {

    private SaveManager() {
    }

    public static MapTag serializeBody(String worldId, PhysBody body) {
        return serializeBody(worldId, body, null);
    }

    public static MapTag serializeBody(String worldId, PhysBody body, Entity only) {
        MapTag map = new MapTag();
        map.putObject("name", new ElementTag(body.name));
        map.putObject("type", new ElementTag(body.def.type.name()));
        map.putObject("shape", ShapeCodec.toMap(body.def.shape));

        Snapshot s = body.snapshot;
        map.putObject("position", new ElementTag(s.px + "," + s.py + "," + s.pz));
        map.putObject("rotation", new QuaternionTag(s.qx, s.qy, s.qz, s.qw));
        map.putObject("velocity", new ElementTag(s.vx + "," + s.vy + "," + s.vz));
        map.putObject("angular_velocity", new ElementTag(s.wx + "," + s.wy + "," + s.wz));

        map.putObject("mass", new ElementTag(body.def.mass > 0 ? body.def.mass : body.mass()));
        map.putObject("friction", new ElementTag(body.def.friction));
        map.putObject("restitution", new ElementTag(body.def.restitution));
        map.putObject("linear_damping", new ElementTag(body.def.linearDamping));
        map.putObject("angular_damping", new ElementTag(body.def.angularDamping));
        map.putObject("gravity", new ElementTag(body.def.gravityFactor));
        map.putObject("group", new ElementTag(body.def.group));
        map.putObject("sensor", new ElementTag(body.def.sensor));
        map.putObject("detect_static", new ElementTag(body.def.detectStatic));
        map.putObject("notify_persist", new ElementTag(body.def.notifyPersist));
        map.putObject("continuous", new ElementTag(body.def.continuous));
        map.putObject("allow_sleeping", new ElementTag(body.def.allowSleeping));
        map.putObject("notify", new ElementTag(body.def.notify));
        map.putObject("remove_on_fall", new ElementTag(body.def.removeOnFallOut));
        map.putObject("tag", new ElementTag(body.tag != null ? body.tag : ""));
        if (body.def.ragdoll != null && !body.def.ragdoll.isEmpty()) {
            map.putObject("ragdoll", new ElementTag(body.def.ragdoll));
            map.putObject("ragdoll_index", new ElementTag(body.def.ragdollIndex));
        }
        map.putObject("max_velocity", new ElementTag(body.def.maxLinearVelocity));
        map.putObject("max_angular_velocity", new ElementTag(body.def.maxAngularVelocity));

        List<DisplayBinder.Binding> bindings = PhysizenServices.get().displays.getBindings(worldId, body.name);
        if (!bindings.isEmpty()) {
            ListTag displaysList = new ListTag();
            for (DisplayBinder.Binding b : bindings) {
                if (b.entity == null || !b.entity.isValid()) continue;
                if (only != null && !b.entity.getUniqueId().equals(only.getUniqueId())) continue;
                MapTag dispMap = new MapTag();
                dispMap.putObject("offset", new ElementTag(b.offset.x + "," + b.offset.y + "," + b.offset.z));
                dispMap.putObject("scale", new ElementTag(b.scale.x + "," + b.scale.y + "," + b.scale.z));
                dispMap.putObject("interpolation_duration", new ElementTag(b.interpolationDuration));
                dispMap.putObject("teleport_duration", new ElementTag(b.teleportDuration));
                dispMap.putObject("interpolation_start", new ElementTag(b.interpolationStart));
                dispMap.putObject("rotation_offset", new QuaternionTag(b.rotationOffset.x, b.rotationOffset.y, b.rotationOffset.z, b.rotationOffset.w));
                dispMap.putObject("remove_with_body", new ElementTag(b.removeWithBody));

                if (only != null) {
                    dispMap.putObject("display_type", new ElementTag("bound"));
                }
                else if (b.entity instanceof BlockDisplay) {
                    BlockDisplay bd = (BlockDisplay) b.entity;
                    dispMap.putObject("display_type", new ElementTag("block"));
                    dispMap.putObject("block_data", new ElementTag(bd.getBlock().getAsString()));
                }
                else if (b.entity instanceof ItemDisplay) {
                    ItemDisplay id = (ItemDisplay) b.entity;
                    dispMap.putObject("display_type", new ElementTag("item"));
                    if (id.getItemStack() != null) {
                        dispMap.putObject("item", new ItemTag(id.getItemStack()));
                    }
                }
                else if (b.entity instanceof TextDisplay) {
                    TextDisplay td = (TextDisplay) b.entity;
                    dispMap.putObject("display_type", new ElementTag("text"));
                    dispMap.putObject("text", new ElementTag(td.getText() != null ? td.getText() : ""));
                }
                displaysList.addObject(dispMap);
            }
            map.putObject("displays", displaysList);
        }
        return map;
    }

    public static MapTag serializePrefab(String worldId, List<String> bodyNames) {
        PhysWorld pw = Args.world(worldId);
        MapTag root = new MapTag();
        ListTag bodiesList = new ListTag();
        Set<String> included = new HashSet<>();

        for (String bName : bodyNames) {
            PhysBody pb = pw.body(bName);
            if (pb != null && !pb.def.internal && !pb.def.managed) {
                bodiesList.addObject(serializeBody(worldId, pb));
                included.add(bName);
            }
        }
        root.putObject("bodies", bodiesList);

        ListTag constraintsList = new ListTag();
        for (String cName : pw.constraintNames()) {
            ConstraintDef def = pw.constraintDef(cName);
            if (def == null) continue;
            boolean aIn = def.bodyA == null || included.contains(def.bodyA);
            boolean bIn = def.bodyB == null || included.contains(def.bodyB);
            if (aIn && bIn && (def.bodyA != null || def.bodyB != null)) {
                MapTag cMap = ConstraintCodec.toMap(def);
                constraintsList.addObject(cMap);
            }
        }
        root.putObject("constraints", constraintsList);

        ListTag vehiclesList = new ListTag();
        for (String vName : pw.vehicleNames()) {
            VehicleDef vd = pw.vehicleDef(vName);
            if (vd != null && included.contains(vd.body)) {
                vehiclesList.addObject(VehicleCodec.toMap(vd));
            }
        }
        root.putObject("vehicles", vehiclesList);
        return root;
    }

    public static BodyDef defFromMap(MapTag bMap, String name) {
        BodyDef def = new BodyDef();
        def.name = name;
        def.shape = ShapeParser.shape(bMap.getObject("shape").asType(MapTag.class, CoreUtilities.noDebugContext), CoreUtilities.noDebugContext);
        if (bMap.containsKey("type")) {
            def.type = MotionKind.valueOf(bMap.getElement("type").asString().toUpperCase());
        }
        def.position = Args.vec(bMap.getObject("position"), CoreUtilities.noDebugContext);
        def.rotation = Args.rotation(bMap.getObject("rotation"), CoreUtilities.noDebugContext);
        if (bMap.containsKey("velocity")) {
            def.linearVelocity = Args.vec(bMap.getObject("velocity"), CoreUtilities.noDebugContext);
        }
        if (bMap.containsKey("angular_velocity")) {
            def.angularVelocity = Args.vec(bMap.getObject("angular_velocity"), CoreUtilities.noDebugContext);
        }
        def.mass = Args.num(bMap.getObject("mass"), 0);
        def.friction = Args.num(bMap.getObject("friction"), 0.5);
        def.restitution = Args.num(bMap.getObject("restitution"), 0.0);
        def.linearDamping = Args.num(bMap.getObject("linear_damping"), 0.05);
        def.angularDamping = Args.num(bMap.getObject("angular_damping"), 0.05);
        def.gravityFactor = Args.num(bMap.getObject("gravity"), 1.0);
        def.group = (int) Args.num(bMap.getObject("group"), 0);
        def.sensor = Args.bool(bMap.getObject("sensor"), false);
        def.detectStatic = Args.bool(bMap.getObject("detect_static"), false);
        def.notifyPersist = Args.bool(bMap.getObject("notify_persist"), false);
        def.continuous = Args.bool(bMap.getObject("continuous"), false);
        def.allowSleeping = Args.bool(bMap.getObject("allow_sleeping"), true);
        def.notify = Args.bool(bMap.getObject("notify"), true);
        def.removeOnFallOut = Args.bool(bMap.getObject("remove_on_fall"), true);
        def.tag = Args.str(bMap.getObject("tag"), "");
        def.ragdoll = Args.str(bMap.getObject("ragdoll"), "");
        def.ragdollIndex = (int) Args.num(bMap.getObject("ragdoll_index"), -1);
        def.maxLinearVelocity = Args.num(bMap.getObject("max_velocity"), 500);
        def.maxAngularVelocity = Args.num(bMap.getObject("max_angular_velocity"), 47.1238898);
        return def;
    }

    public static List<String> spawnPrefab(String worldId, MapTag root, double[] targetPos, double[] targetRot, String prefix, String overrideName) {
        PhysWorld pw = Args.world(worldId);
        World bukkitWorld = PhysizenServices.get().worlds.get(worldId);
        if (prefix == null) prefix = "";

        ListTag bodiesList;
        if (root.containsKey("bodies")) {
            bodiesList = root.getObject("bodies").asType(ListTag.class, CoreUtilities.noDebugContext);
        }
        else {
            bodiesList = new ListTag();
            bodiesList.addObject(root);
        }

        if (bodiesList == null || bodiesList.isEmpty()) {
            return new ArrayList<>();
        }

        double[] origin = null;
        if (targetPos != null) {
            MapTag firstBody = bodiesList.objectForms.get(0).asType(MapTag.class, CoreUtilities.noDebugContext);
            if (firstBody != null && firstBody.containsKey("position")) {
                origin = Args.vec(firstBody.getObject("position"), CoreUtilities.noDebugContext);
            }
        }

        Quaternionf rotQuat = null;
        if (targetRot != null) {
            rotQuat = new Quaternionf((float) targetRot[0], (float) targetRot[1], (float) targetRot[2], (float) targetRot[3]);
        }

        List<String> createdNames = new ArrayList<>();
        Map<String, Snapshot> placed = new HashMap<>();

        for (ObjectTag bodyTag : bodiesList.objectForms) {
            MapTag bMap = bodyTag.asType(MapTag.class, CoreUtilities.noDebugContext);
            if (bMap == null) continue;

            String baseName = bMap.getElement("name").asString();
            String finalName = (overrideName != null && bodiesList.size() == 1) ? overrideName : prefix + baseName;

            BodyDef def = defFromMap(bMap, finalName);
            if (def.ragdoll != null && !def.ragdoll.isEmpty()) {
                def.ragdoll = prefix + def.ragdoll;
            }
            double[] savedPos = def.position;
            double[] savedRot = def.rotation;
            def.linearVelocity = new double[3];
            def.angularVelocity = new double[3];

            if (targetPos != null && origin != null) {
                double dx = savedPos[0] - origin[0];
                double dy = savedPos[1] - origin[1];
                double dz = savedPos[2] - origin[2];
                if (rotQuat != null) {
                    Vector3f rel = rotQuat.transform(new Vector3f((float) dx, (float) dy, (float) dz));
                    def.position = new double[]{targetPos[0] + rel.x, targetPos[1] + rel.y, targetPos[2] + rel.z};
                    Quaternionf bRot = new Quaternionf(rotQuat).mul(new Quaternionf((float) savedRot[0], (float) savedRot[1], (float) savedRot[2], (float) savedRot[3]));
                    def.rotation = new double[]{bRot.x, bRot.y, bRot.z, bRot.w};
                }
                else {
                    def.position = new double[]{targetPos[0] + dx, targetPos[1] + dy, targetPos[2] + dz};
                    def.rotation = savedRot;
                }
            }
            else {
                def.position = savedPos;
                def.rotation = savedRot;
            }

            pw.createBody(def);
            createdNames.add(finalName);
            placed.put(finalName, new Snapshot(def.position[0], def.position[1], def.position[2],
                    (float) def.rotation[0], (float) def.rotation[1], (float) def.rotation[2], (float) def.rotation[3],
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0L));

            if (bMap.containsKey("displays") && bukkitWorld != null) {
                ListTag displays = bMap.getObject("displays").asType(ListTag.class, CoreUtilities.noDebugContext);
                for (ObjectTag dispObj : displays.objectForms) {
                    MapTag dMap = dispObj.asType(MapTag.class, CoreUtilities.noDebugContext);
                    if (dMap == null) continue;
                    Location spawnLoc = new Location(bukkitWorld, def.position[0], def.position[1], def.position[2]);
                    Entity spawned = null;
                    String dType = dMap.getElement("display_type").asString();

                    if ("block".equals(dType)) {
                        BlockData bd = Bukkit.createBlockData(dMap.getElement("block_data").asString());
                        spawned = bukkitWorld.spawn(spawnLoc, BlockDisplay.class, ent -> ent.setBlock(bd));
                    }
                    else if ("item".equals(dType)) {
                        ItemTag it = ItemTag.valueOf(dMap.getElement("item").asString(), CoreUtilities.noDebugContext);
                        spawned = bukkitWorld.spawn(spawnLoc, ItemDisplay.class, ent -> {
                            if (it != null) ent.setItemStack(it.getItemStack());
                        });
                    }
                    else if ("text".equals(dType)) {
                        String txt = dMap.getElement("text").asString();
                        spawned = bukkitWorld.spawn(spawnLoc, TextDisplay.class, ent -> ent.setText(txt));
                    }

                    if (spawned != null) {
                        DisplayBinder.Binding binding = bindingFromMap(dMap, spawned);
                        PhysizenServices.get().displays.bind(worldId, finalName, binding);
                        DenizenServices.persistence().persist(spawned, worldId, finalName);
                    }
                }
            }
        }

        if (root.containsKey("constraints")) {
            ListTag constraints = root.getObject("constraints").asType(ListTag.class, CoreUtilities.noDebugContext);
            for (ObjectTag cObj : constraints.objectForms) {
                MapTag cMap = cObj.asType(MapTag.class, CoreUtilities.noDebugContext);
                if (cMap == null) continue;

                ConstraintDef def = new ConstraintDef();
                def.name = prefix + cMap.getElement("name").asString();
                def.kind = ConstraintDef.Kind.valueOf(cMap.getElement("type").asString().toUpperCase());
                if (cMap.containsKey("body_a")) def.bodyA = prefix + cMap.getElement("body_a").asString();
                if (cMap.containsKey("body_b")) def.bodyB = prefix + cMap.getElement("body_b").asString();

                ConstraintCodec.applyCommon(def, cMap);

                Snapshot snapA = def.bodyA == null ? null : placed.get(def.bodyA);
                Snapshot snapB = def.bodyB == null ? null : placed.get(def.bodyB);
                if (cMap.containsKey("local_a") && cMap.containsKey("local_b")
                        && snapA != null && snapB != null) {
                    def.pointA = ConstraintDef.toWorld(snapA, Args.vec(cMap.getObject("local_a"), CoreUtilities.noDebugContext));
                    def.pointB = ConstraintDef.toWorld(snapB, Args.vec(cMap.getObject("local_b"), CoreUtilities.noDebugContext));
                    if (cMap.containsKey("local_axis")) {
                        def.axis = ConstraintDef.rotate(snapA, Args.vec(cMap.getObject("local_axis"), CoreUtilities.noDebugContext), false);
                    }
                    if (cMap.containsKey("local_normal")) {
                        def.normalAxis = ConstraintDef.rotate(snapA, Args.vec(cMap.getObject("local_normal"), CoreUtilities.noDebugContext), false);
                    }
                }
                else {
                    double[] pa = Args.vec(cMap.getObject("point_a"), CoreUtilities.noDebugContext);
                    double[] pb = cMap.containsKey("point_b") ? Args.vec(cMap.getObject("point_b"), CoreUtilities.noDebugContext) : pa;
                    double[] ax = cMap.containsKey("axis") ? Args.vec(cMap.getObject("axis"), CoreUtilities.noDebugContext) : def.axis;
                    double[] nm = cMap.containsKey("normal") ? Args.vec(cMap.getObject("normal"), CoreUtilities.noDebugContext) : def.normalAxis;
                    if (targetPos != null && origin != null) {
                        def.pointA = transformPoint(pa, origin, targetPos, rotQuat);
                        def.pointB = transformPoint(pb, origin, targetPos, rotQuat);
                        def.axis = transformDir(ax, rotQuat);
                        def.normalAxis = transformDir(nm, rotQuat);
                    }
                    else {
                        def.pointA = pa;
                        def.pointB = pb;
                        def.axis = ax;
                        def.normalAxis = nm;
                    }
                }

                pw.addConstraint(def);
            }
        }

        if (root.containsKey("vehicles")) {
            ListTag vehicles = root.getObject("vehicles").asType(ListTag.class, CoreUtilities.noDebugContext);
            for (ObjectTag vObj : vehicles.objectForms) {
                MapTag vMap = vObj.asType(MapTag.class, CoreUtilities.noDebugContext);
                if (vMap == null) continue;
                pw.addVehicle(VehicleCodec.fromMap(vMap, prefix));
            }
        }
        return createdNames;
    }

    private static double[] transformPoint(double[] p, double[] origin, double[] target, Quaternionf rot) {
        double dx = p[0] - origin[0];
        double dy = p[1] - origin[1];
        double dz = p[2] - origin[2];
        if (rot != null) {
            Vector3f r = rot.transform(new Vector3f((float) dx, (float) dy, (float) dz));
            return new double[]{target[0] + r.x, target[1] + r.y, target[2] + r.z};
        }
        return new double[]{target[0] + dx, target[1] + dy, target[2] + dz};
    }

    private static double[] transformDir(double[] v, Quaternionf rot) {
        if (rot == null) {
            return v.clone();
        }
        Vector3f r = rot.transform(new Vector3f((float) v[0], (float) v[1], (float) v[2]));
        return new double[]{r.x, r.y, r.z};
    }

    public static DisplayBinder.Binding bindingFromMap(MapTag dMap, Entity entity) {
        DisplayBinder.Binding binding = new DisplayBinder.Binding(entity);
        if (dMap.containsKey("offset")) {
            double[] o = Args.vec(dMap.getObject("offset"), CoreUtilities.noDebugContext);
            binding.offset = new Vector3f((float) o[0], (float) o[1], (float) o[2]);
        }
        if (dMap.containsKey("scale")) {
            double[] s = Args.vec(dMap.getObject("scale"), CoreUtilities.noDebugContext);
            binding.scale = new Vector3f((float) s[0], (float) s[1], (float) s[2]);
        }
        if (dMap.containsKey("rotation_offset")) {
            double[] ro = Args.rotation(dMap.getObject("rotation_offset"), CoreUtilities.noDebugContext);
            binding.rotationOffset = new Quaternionf((float) ro[0], (float) ro[1], (float) ro[2], (float) ro[3]);
        }
        binding.interpolationDuration = (int) Args.num(dMap.getObject("interpolation_duration"), 3);
        binding.teleportDuration = (int) Args.num(dMap.getObject("teleport_duration"), 3);
        binding.interpolationStart = (int) Args.num(dMap.getObject("interpolation_start"), 0);
        binding.removeWithBody = Args.bool(dMap.getObject("remove_with_body"), true);
        return binding;
    }

}