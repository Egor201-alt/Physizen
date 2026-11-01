package dev.egor201.physizen.denizen.save;

import com.denizenscript.denizen.objects.ItemTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.objects.core.QuaternionTag;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.bukkit.display.DisplayBinder;
import dev.egor201.physizen.core.io.PhysBinary;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SaveManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
                MapTag cMap = constraintToMap(def);
                constraintsList.addObject(cMap);
            }
        }
        root.putObject("constraints", constraintsList);
        return root;
    }

    private static File resolveFile(String fileName, boolean write) {
        if (fileName == null || fileName.trim().isEmpty() || fileName.contains("..") || fileName.startsWith("/") || fileName.startsWith("\\") || fileName.contains(":")) {
            throw new PhysException("Invalid save file name.");
        }
        String n = fileName.trim();
        String lower = n.toLowerCase();
        File folder = DenizenServices.saveFolder();
        if (write) {
            if (!lower.endsWith(".json") && !lower.endsWith(".phys")) {
                n += ".phys";
            }
            File f = new File(folder, n);
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            return f;
        }
        if (lower.endsWith(".json") || lower.endsWith(".phys")) {
            return new File(folder, n);
        }
        File phys = new File(folder, n + ".phys");
        return phys.isFile() ? phys : new File(folder, n + ".json");
    }

    public static void saveToFile(MapTag root, String fileName) {
        File file = resolveFile(fileName, true);
        try {
            Object unpacked = unpack(root);
            if (file.getName().toLowerCase().endsWith(".json")) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
                    GSON.toJson(unpacked, writer);
                }
            }
            else {
                Files.write(file.toPath(), PhysBinary.encodeFile(unpacked));
            }
        }
        catch (Exception e) {
            throw new PhysException("Failed to save prefab to " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    public static MapTag loadFromFile(String fileName) {
        File file = resolveFile(fileName, false);
        if (!file.isFile()) {
            throw new PhysException("Save file not found: " + file.getName());
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            Object raw;
            if (PhysBinary.isFile(data)) {
                raw = PhysBinary.decodeFile(data);
            }
            else {
                raw = GSON.fromJson(new String(data, StandardCharsets.UTF_8), Object.class);
            }
            return (MapTag) pack(raw);
        }
        catch (Exception e) {
            throw new PhysException("Failed to load prefab from " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    public static List<String> listSavedFiles() {
        File folder = DenizenServices.saveFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json") || name.endsWith(".phys"));
        Set<String> names = new LinkedHashSet<>();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                names.add(name.substring(0, name.length() - 5));
            }
        }
        return new ArrayList<>(names);
    }

    public static byte[] toBlob(MapTag map) throws IOException {
        return PhysBinary.encodeRaw(unpack(map));
    }

    public static MapTag fromBlob(byte[] data) throws IOException {
        return (MapTag) pack(PhysBinary.decodeRaw(data));
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

        for (ObjectTag bodyTag : bodiesList.objectForms) {
            MapTag bMap = bodyTag.asType(MapTag.class, CoreUtilities.noDebugContext);
            if (bMap == null) continue;

            String baseName = bMap.getElement("name").asString();
            String finalName = (overrideName != null && bodiesList.size() == 1) ? overrideName : prefix + baseName;

            BodyDef def = defFromMap(bMap, finalName);
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

                double[] pa = Args.vec(cMap.getObject("point_a"), CoreUtilities.noDebugContext);
                double[] pb = cMap.containsKey("point_b") ? Args.vec(cMap.getObject("point_b"), CoreUtilities.noDebugContext) : pa;

                if (targetPos != null && origin != null) {
                    double dxA = pa[0] - origin[0];
                    double dyA = pa[1] - origin[1];
                    double dzA = pa[2] - origin[2];
                    double dxB = pb[0] - origin[0];
                    double dyB = pb[1] - origin[1];
                    double dzB = pb[2] - origin[2];
                    if (rotQuat != null) {
                        Vector3f rA = rotQuat.transform(new Vector3f((float) dxA, (float) dyA, (float) dzA));
                        Vector3f rB = rotQuat.transform(new Vector3f((float) dxB, (float) dyB, (float) dzB));
                        def.pointA = new double[]{targetPos[0] + rA.x, targetPos[1] + rA.y, targetPos[2] + rA.z};
                        def.pointB = new double[]{targetPos[0] + rB.x, targetPos[1] + rB.y, targetPos[2] + rB.z};
                        Vector3f ax = rotQuat.transform(new Vector3f((float) def.axis[0], (float) def.axis[1], (float) def.axis[2]));
                        def.axis = new double[]{ax.x, ax.y, ax.z};
                    }
                    else {
                        def.pointA = new double[]{targetPos[0] + dxA, targetPos[1] + dyA, targetPos[2] + dzA};
                        def.pointB = new double[]{targetPos[0] + dxB, targetPos[1] + dyB, targetPos[2] + dzB};
                    }
                }
                else {
                    def.pointA = pa;
                    def.pointB = pb;
                    if (cMap.containsKey("axis")) def.axis = Args.vec(cMap.getObject("axis"), CoreUtilities.noDebugContext);
                }

                applyConstraintCommon(def, cMap);

                pw.addConstraint(def);
            }
        }
        return createdNames;
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

    private static String join3(double[] v) {
        return v[0] + "," + v[1] + "," + v[2];
    }

    public static MapTag constraintToMap(ConstraintDef def) {
        MapTag cMap = new MapTag();
        cMap.putObject("name", new ElementTag(def.name));
        cMap.putObject("type", new ElementTag(def.kind.name()));
        if (def.bodyA != null) cMap.putObject("body_a", new ElementTag(def.bodyA));
        if (def.bodyB != null) cMap.putObject("body_b", new ElementTag(def.bodyB));
        cMap.putObject("point_a", new ElementTag(join3(def.pointA)));
        if (def.pointB != null) cMap.putObject("point_b", new ElementTag(join3(def.pointB)));
        cMap.putObject("axis", new ElementTag(join3(def.axis)));
        cMap.putObject("normal", new ElementTag(join3(def.normalAxis)));
        cMap.putObject("limited", new ElementTag(def.limited));
        cMap.putObject("limits", new ElementTag(def.limitMin + "," + def.limitMax));
        cMap.putObject("friction", new ElementTag(def.maxFriction));
        cMap.putObject("spring_freq", new ElementTag(def.springFrequency));
        cMap.putObject("spring_damp", new ElementTag(def.springDamping));
        cMap.putObject("cone_angle", new ElementTag(Math.toDegrees(def.halfConeAngle)));
        cMap.putObject("motor_state", new ElementTag(def.motorState.name().toLowerCase()));
        cMap.putObject("motor_target", new ElementTag(def.motorTarget));
        cMap.putObject("motor_torque", new ElementTag(def.motorMaxTorque));
        cMap.putObject("motor_force", new ElementTag(def.motorMaxForce));
        cMap.putObject("motor_spring_freq", new ElementTag(def.motorSpringFrequency));
        cMap.putObject("motor_spring_damp", new ElementTag(def.motorSpringDamping));
        if (def.localA != null) {
            cMap.putObject("local_a", new ElementTag(join3(def.localA)));
            cMap.putObject("local_b", new ElementTag(join3(def.localB)));
            cMap.putObject("local_axis", new ElementTag(join3(def.localAxis)));
            cMap.putObject("local_normal", new ElementTag(join3(def.localNormal)));
        }
        return cMap;
    }

    public static List<MapTag> constraintsOf(PhysWorld pw, String bodyName) {
        List<MapTag> list = new ArrayList<>();
        for (String cName : pw.constraintNames()) {
            ConstraintDef def = pw.constraintDef(cName);
            if (def != null && (bodyName.equals(def.bodyA) || bodyName.equals(def.bodyB))) {
                list.add(constraintToMap(def));
            }
        }
        return list;
    }

    private static void applyConstraintCommon(ConstraintDef def, MapTag cMap) {
        if (cMap.containsKey("normal")) def.normalAxis = Args.vec(cMap.getObject("normal"), CoreUtilities.noDebugContext);
        if (cMap.containsKey("limits")) {
            double[] lim = Args.pair(cMap.getObject("limits"));
            def.limitMin = lim[0];
            def.limitMax = lim[1];
            def.limited = Args.bool(cMap.getObject("limited"), true);
        }
        def.maxFriction = Args.num(cMap.getObject("friction"), 0);
        def.springFrequency = Args.num(cMap.getObject("spring_freq"), 0);
        def.springDamping = Args.num(cMap.getObject("spring_damp"), 0.5);
        if (cMap.containsKey("cone_angle")) {
            def.halfConeAngle = Math.toRadians(Args.num(cMap.getObject("cone_angle"), 45.0));
        }

        if (cMap.containsKey("motor_state")) {
            String stateStr = cMap.getElement("motor_state").asString().toLowerCase();
            if (stateStr.equals("velocity")) def.motorState = ConstraintDef.MotorState.VELOCITY;
            else if (stateStr.equals("position")) def.motorState = ConstraintDef.MotorState.POSITION;
            else def.motorState = ConstraintDef.MotorState.OFF;
        }
        def.motorTarget = Args.num(cMap.getObject("motor_target"), 0);
        def.motorMaxTorque = Args.num(cMap.getObject("motor_torque"), 0);
        def.motorMaxForce = Args.num(cMap.getObject("motor_force"), 0);
        def.motorSpringFrequency = Args.num(cMap.getObject("motor_spring_freq"), 2.0);
        def.motorSpringDamping = Args.num(cMap.getObject("motor_spring_damp"), 1.0);
    }

    public static ConstraintDef constraintFromBlob(PhysWorld pw, MapTag cMap) {
        ConstraintDef def = new ConstraintDef();
        def.name = cMap.getElement("name").asString();
        def.kind = ConstraintDef.Kind.valueOf(cMap.getElement("type").asString().toUpperCase());
        if (cMap.containsKey("body_a")) def.bodyA = cMap.getElement("body_a").asString();
        if (cMap.containsKey("body_b")) def.bodyB = cMap.getElement("body_b").asString();
        applyConstraintCommon(def, cMap);
        PhysBody a = def.bodyA == null ? null : pw.body(def.bodyA);
        PhysBody b = def.bodyB == null ? null : pw.body(def.bodyB);
        if (cMap.containsKey("local_a")) {
            def.pointA = ConstraintDef.toWorld(a == null ? null : a.snapshot, Args.vec(cMap.getObject("local_a"), CoreUtilities.noDebugContext));
            def.pointB = ConstraintDef.toWorld(b == null ? null : b.snapshot, Args.vec(cMap.getObject("local_b"), CoreUtilities.noDebugContext));
            def.axis = ConstraintDef.rotate(a == null ? null : a.snapshot, Args.vec(cMap.getObject("local_axis"), CoreUtilities.noDebugContext), false);
            def.normalAxis = ConstraintDef.rotate(a == null ? null : a.snapshot, Args.vec(cMap.getObject("local_normal"), CoreUtilities.noDebugContext), false);
        }
        else {
            def.pointA = Args.vec(cMap.getObject("point_a"), CoreUtilities.noDebugContext);
            def.pointB = cMap.containsKey("point_b") ? Args.vec(cMap.getObject("point_b"), CoreUtilities.noDebugContext) : def.pointA;
            if (cMap.containsKey("axis")) def.axis = Args.vec(cMap.getObject("axis"), CoreUtilities.noDebugContext);
        }
        return def;
    }

    private static Object unpack(ObjectTag tag) {
        if (tag instanceof MapTag) {
            Map<String, Object> map = new LinkedHashMap<>();
            MapTag mt = (MapTag) tag;
            for (String key : mt.keys()) {
                map.put(key, unpack(mt.getObject(key)));
            }
            return map;
        }
        else if (tag instanceof ListTag) {
            List<Object> list = new ArrayList<>();
            ListTag lt = (ListTag) tag;
            for (ObjectTag obj : lt.objectForms) {
                list.add(unpack(obj));
            }
            return list;
        }
        else {
            return tag.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectTag pack(Object obj) {
        if (obj instanceof Map) {
            MapTag map = new MapTag();
            Map<String, Object> m = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : m.entrySet()) {
                map.putObject(entry.getKey(), pack(entry.getValue()));
            }
            return map;
        }
        else if (obj instanceof List) {
            ListTag list = new ListTag();
            List<Object> l = (List<Object>) obj;
            for (Object item : l) {
                list.addObject(pack(item));
            }
            return list;
        }
        else {
            return new ElementTag(String.valueOf(obj));
        }
    }
}