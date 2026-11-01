package dev.egor201.physizen.denizen.args;

import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.objects.core.QuaternionTag;
import com.denizenscript.denizencore.objects.core.VectorObject;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.validate.Guard;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.world.PhysWorld;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public final class Args {

    private Args() {
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
            double[] parsed = new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim())};
            Guard.vec(parsed, "vector");
            return parsed;
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
            double parsed = Double.parseDouble(tag.toString().trim());
            Guard.number(parsed, "number");
            return parsed;
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

    public static double[] pair(ObjectTag tag) {
        if (tag == null) {
            throw new PhysException("Missing value.");
        }
        String text = tag.toString().trim();
        if (text.startsWith("l@") || text.startsWith("v@")) {
            text = text.substring(2);
        }
        String[] parts = text.split(",");
        if (parts.length < 2) {
            throw new PhysException("Invalid pair '" + tag + "', expected a,b.");
        }
        try {
            return new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        }
        catch (NumberFormatException e) {
            throw new PhysException("Invalid pair '" + tag + "'.");
        }
    }

    public static double[] rotation(ObjectTag tag, TagContext context) {
        if (tag == null) {
            throw new PhysException("Missing rotation value.");
        }
        if (tag instanceof QuaternionTag) {
            QuaternionTag qt = (QuaternionTag) tag;
            return normalize(new double[]{qt.x, qt.y, qt.z, qt.w});
        }
        String text = tag.toString().trim();
        if (text.startsWith("quaternion@")) {
            QuaternionTag qt = tag.asType(QuaternionTag.class, context);
            if (qt != null) {
                return normalize(new double[]{qt.x, qt.y, qt.z, qt.w});
            }
        }
        String plain = text.startsWith("l@") || text.startsWith("v@") ? text.substring(2) : text;
        String[] parts = plain.split(",");
        if (parts.length == 4) {
            try {
                return normalize(new double[]{
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()),
                        Double.parseDouble(parts[3].trim())});
            }
            catch (NumberFormatException ignored) {
            }
        }
        return euler(vec(tag, context));
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
        if (id == null || id.isEmpty()) {
            throw new PhysException("Missing world:<id>.");
        }
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