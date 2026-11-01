package dev.egor201.physizen.denizen.args;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.ShapeSpec;
import dev.egor201.physizen.core.shape.ObjParser;
import dev.egor201.physizen.denizen.DenizenServices;

import java.io.File;

public final class ShapeParser {

    private ShapeParser() {
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
        double scale = Args.num(map.getObject("scale"), 1.0);
        switch (type) {
            case "box":
            case "cube": {
                spec.kind = ShapeSpec.Kind.BOX;
                ObjectTag size = map.getObject("size");
                double[] s = size == null ? new double[]{1, 1, 1} : Args.vec(size, context);
                spec.sizeX = s[0] * scale;
                spec.sizeY = s[1] * scale;
                spec.sizeZ = s[2] * scale;
                break;
            }
            case "sphere":
                spec.kind = ShapeSpec.Kind.SPHERE;
                spec.radius = Args.num(map.getObject("radius"), 0.5) * scale;
                break;
            case "capsule":
                spec.kind = ShapeSpec.Kind.CAPSULE;
                spec.radius = Args.num(map.getObject("radius"), 0.25) * scale;
                spec.height = Args.num(map.getObject("height"), 1.0) * scale;
                break;
            case "cylinder":
                spec.kind = ShapeSpec.Kind.CYLINDER;
                spec.radius = Args.num(map.getObject("radius"), 0.5) * scale;
                spec.height = Args.num(map.getObject("height"), 1.0) * scale;
                break;
            case "cone":
                spec.kind = ShapeSpec.Kind.CONE;
                spec.radius = Args.num(map.getObject("radius"), 0.5) * scale;
                spec.height = Args.num(map.getObject("height"), 1.0) * scale;
                spec.segments = (int) Args.num(map.getObject("segments"), 16);
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
                        double[] o = Args.vec(offset, context);
                        child.offset = new double[]{o[0] * scale, o[1] * scale, o[2] * scale};
                    }
                    child.rotation = Args.quat(childMap, context);
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
            File f = new File(DenizenServices.modelFolder(), name);
            if (!f.isFile()) {
                throw new PhysException("Model file not found: " + f.getPath());
            }
            try {
                ObjParser parsed = ObjParser.parse(f, scale);
                spec.vertices = parsed.vertices;
                spec.indices = spec.kind == ShapeSpec.Kind.MESH ? parsed.indices : null;
                spec.sourceFile = name;
                spec.sourceScale = scale;
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
                double[] p = Args.vec(list.objectForms.get(i), context);
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
                    idx[i] = (int) Args.num(tl.objectForms.get(i), 0);
                }
                spec.indices = idx;
            }
            return;
        }
        throw new PhysException("Shape needs 'file' or 'points'.");
    }
}
