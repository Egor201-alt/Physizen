package dev.egor201.physizen.denizen;

import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.objects.core.QuaternionTag;
import com.denizenscript.denizencore.objects.core.VectorObject;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import dev.egor201.physizen.core.ObjParser;
import dev.egor201.physizen.core.PhysEngine;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.PhysWorld;
import dev.egor201.physizen.core.ShapeSpec;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Util {

    private static File modelFolder;

    private Util() {
    }

    public static void init(JavaPlugin plugin) {
        modelFolder = new File(plugin.getDataFolder(), "models");
        modelFolder.mkdirs();
    }

    public static ObjectTag tagVec(double[] v) {
        return new LocationTag((World) null, v[0], v[1], v[2]);
    }

    public static QuaternionTag tagQuat(double x, double y, double z, double w) {
        return new QuaternionTag(x, y, z, w);
    }

    public static double[] vec(ObjectTag tag, TagContext context) {
        if (tag == null) {
            throw new PhysException("Missing vector value.");
        }
        if (tag instanceof VectorObject) {
            VectorObject v = (VectorObject) tag;
            return new double[]{v.getX(), v.getY(), v.getZ()};
        }
        String text = tag.toString();
        if (text.startsWith("l@") || text.startsWith("v@")) {
            text = text.substring(2);
        }
        String[] parts = text.split(",");
        if (parts.length < 3) {
            throw new PhysException("Invalid vector '" + tag + "'.");
        }
        try {
            return new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim())};
        }
        catch (NumberFormatException e) {
            throw new PhysException("Invalid vector '" + tag + "'.");
        }
    }

    public static double num(ObjectTag tag, double fallback) {
        if (tag == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(tag.toString().trim());
        }
        catch (NumberFormatException e) {
            throw new PhysException("Invalid number '" + tag + "'.");
        }
    }

    public static boolean bool(ObjectTag tag, boolean fallback) {
        if (tag == null) {
            return fallback;
        }
        String s = CoreUtilities.toLowerCase(tag.toString().trim());
        if (s.equals("true")) {
            return true;
        }
        if (s.equals("false")) {
            return false;
        }
        throw new PhysException("Invalid boolean '" + tag + "'.");
    }

    public static String str(ObjectTag tag, String fallback) {
        return tag == null ? fallback : tag.toString();
    }

    public static double[] quat(MapTag named, TagContext context) {
        ObjectTag q = named.getObject("rotation");
        if (q != null) {
            QuaternionTag qt = q.asType(QuaternionTag.class, context);
            if (qt == null) {
                throw new PhysException("Invalid rotation '" + q + "'.");
            }
            return normalize(new double[]{qt.x, qt.y, qt.z, qt.w});
        }
        ObjectTag e = named.getObject("euler");
        if (e != null) {
            return euler(vec(e, context));
        }
        return new double[]{0, 0, 0, 1};
    }

    public static double[] euler(double[] degrees) {
        double pitch = Math.toRadians(degrees[0]) / 2;
        double yaw = Math.toRadians(degrees[1]) / 2;
        double roll = Math.toRadians(degrees[2]) / 2;
        double cx = Math.cos(pitch);
        double sx = Math.sin(pitch);
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        double cz = Math.cos(roll);
        double sz = Math.sin(roll);
        double x = sx * cy * cz + cx * sy * sz;
        double y = cx * sy * cz - sx * cy * sz;
        double z = cx * cy * sz - sx * sy * cz;
        double w = cx * cy * cz + sx * sy * sz;
        return normalize(new double[]{x, y, z, w});
    }

    public static double[] normalize(double[] q) {
        double len = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (len < 1.0e-9) {
            return new double[]{0, 0, 0, 1};
        }
        return new double[]{q[0] / len, q[1] / len, q[2] / len, q[3] / len};
    }

    public static PhysWorld world(String id) {
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            throw new PhysException("Physizen engine is not running.");
        }
        PhysWorld world = engine.world(id);
        if (world == null) {
            throw new PhysException("Unknown physics world '" + id + "'.");
        }
        return world;
    }

    public static ShapeSpec shape(ObjectTag tag, TagContext context) {
        if (tag == null) {
            throw new PhysException("Missing shape.");
        }
        MapTag map = null;
        String raw = tag.toString().trim();
        if (raw.startsWith("[") || tag instanceof MapTag) {
            map = tag.asType(MapTag.class, context);
        }
        if (map == null) {
            map = shorthand(raw);
        }
        return shape(map, context);
    }

    private static MapTag shorthand(String raw) {
        MapTag map = new MapTag();
        int colon = raw.indexOf(":");
        String type = colon < 0 ? raw : raw.substring(0, colon);
        String rest = colon < 0 ? "" : raw.substring(colon + 1);
        map.putObject("type", new ElementTag(type));
        String[] parts = rest.isEmpty() ? new String[0] : rest.split(",");
        String t = CoreUtilities.toLowerCase(type);
        if (t.equals("box") && parts.length == 3) {
            map.putObject("size", new ElementTag(rest));
        }
        else if (t.equals("box") && parts.length == 1) {
            map.putObject("size", new ElementTag(parts[0] + "," + parts[0] + "," + parts[0]));
        }
        else if (parts.length >= 1) {
            map.putObject("radius", new ElementTag(parts[0]));
            if (parts.length >= 2) {
                map.putObject("height", new ElementTag(parts[1]));
            }
        }
        return map;
    }

    public static ShapeSpec shape(MapTag map, TagContext context) {
        ObjectTag typeTag = map.getObject("type");
        if (typeTag == null) {
            throw new PhysException("Shape needs a type.");
        }
        String type = CoreUtilities.toLowerCase(typeTag.toString());
        ShapeSpec spec = new ShapeSpec();
        double scale = num(map.getObject("scale"), 1.0);
        switch (type) {
            case "box":
            case "cube": {
                spec.kind = ShapeSpec.Kind.BOX;
                ObjectTag size = map.getObject("size");
                double[] s = size == null ? new double[]{1, 1, 1} : vec(size, context);
                spec.sizeX = s[0] * scale;
                spec.sizeY = s[1] * scale;
                spec.sizeZ = s[2] * scale;
                break;
            }
            case "sphere":
                spec.kind = ShapeSpec.Kind.SPHERE;
                spec.radius = num(map.getObject("radius"), 0.5) * scale;
                break;
            case "capsule":
                spec.kind = ShapeSpec.Kind.CAPSULE;
                spec.radius = num(map.getObject("radius"), 0.25) * scale;
                spec.height = num(map.getObject("height"), 1.0) * scale;
                break;
            case "cylinder":
                spec.kind = ShapeSpec.Kind.CYLINDER;
                spec.radius = num(map.getObject("radius"), 0.5) * scale;
                spec.height = num(map.getObject("height"), 1.0) * scale;
                break;
            case "cone":
                spec.kind = ShapeSpec.Kind.CONE;
                spec.radius = num(map.getObject("radius"), 0.5) * scale;
                spec.height = num(map.getObject("height"), 1.0) * scale;
                spec.segments = (int) num(map.getObject("segments"), 16);
                break;
            case "hull":
            case "convex_hull":
                spec.kind = ShapeSpec.Kind.HULL;
                loadGeometry(spec, map, scale, context);
                break;
            case "mesh":
                spec.kind = ShapeSpec.Kind.MESH;
                loadGeometry(spec, map, scale, context);
                break;
            case "compound": {
                spec.kind = ShapeSpec.Kind.COMPOUND;
                ObjectTag children = map.getObject("children");
                if (children == null) {
                    throw new PhysException("Compound shape needs children.");
                }
                ListTag list = children.asType(ListTag.class, context);
                for (ObjectTag childTag : list.objectForms) {
                    MapTag childMap = childTag.asType(MapTag.class, context);
                    if (childMap == null) {
                        throw new PhysException("Invalid compound child '" + childTag + "'.");
                    }
                    ShapeSpec.Child child = new ShapeSpec.Child();
                    child.shape = shape(childMap, context);
                    ObjectTag offset = childMap.getObject("offset");
                    if (offset != null) {
                        double[] o = vec(offset, context);
                        child.offset = new double[]{o[0] * scale, o[1] * scale, o[2] * scale};
                    }
                    child.rotation = quat(childMap, context);
                    spec.children.add(child);
                }
                break;
            }
            default:
                throw new PhysException("Unknown shape type '" + type + "'.");
        }
        return spec;
    }

    private static void loadGeometry(ShapeSpec spec, MapTag map, double scale, TagContext context) {
        ObjectTag file = map.getObject("file");
        if (file != null) {
            String name = file.toString();
            if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                throw new PhysException("Model path must stay inside plugins/Physizen/models.");
            }
            File f = new File(modelFolder, name);
            if (!f.isFile()) {
                throw new PhysException("Model file not found: " + f.getPath());
            }
            try {
                ObjParser parsed = ObjParser.parse(f, scale);
                spec.vertices = parsed.vertices;
                spec.indices = spec.kind == ShapeSpec.Kind.MESH ? parsed.indices : null;
            }
            catch (Exception e) {
                throw new PhysException("Failed to read model: " + e.getMessage());
            }
            return;
        }
        ObjectTag points = map.getObject("points");
        if (points != null) {
            ListTag list = points.asType(ListTag.class, context);
            float[] v = new float[list.size() * 3];
            for (int i = 0; i < list.size(); i++) {
                double[] p = vec(list.objectForms.get(i), context);
                v[i * 3] = (float) (p[0] * scale);
                v[i * 3 + 1] = (float) (p[1] * scale);
                v[i * 3 + 2] = (float) (p[2] * scale);
            }
            spec.vertices = v;
            ObjectTag tris = map.getObject("triangles");
            if (tris != null && spec.kind == ShapeSpec.Kind.MESH) {
                ListTag tl = tris.asType(ListTag.class, context);
                int[] idx = new int[tl.size()];
                for (int i = 0; i < idx.length; i++) {
                    idx[i] = (int) num(tl.objectForms.get(i), 0);
                }
                spec.indices = idx;
            }
            return;
        }
        throw new PhysException("Shape needs 'file' or 'points'.");
    }

    public static List<EntityTag> entities(ObjectTag tag, TagContext context) {
        List<EntityTag> out = new ArrayList<>();
        if (tag == null) {
            return out;
        }
        ListTag list = tag.asType(ListTag.class, context);
        for (ObjectTag item : list.objectForms) {
            EntityTag e = item.asType(EntityTag.class, context);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }
}
