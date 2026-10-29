package dev.egor201.physizen.core;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.objects.core.QuaternionTag;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.CapsuleShape;
import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.CylinderShape;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.SphereShape;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.Vec3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public final class ShapeSpec {

    public enum Kind {
        BOX,
        SPHERE,
        CAPSULE,
        CYLINDER,
        CONE,
        HULL,
        MESH,
        COMPOUND
    }

    public static final class Child {

        public ShapeSpec shape;
        public double[] offset = new double[3];
        public double[] rotation = {0, 0, 0, 1};
    }

    public Kind kind = Kind.BOX;
    public double sizeX = 1;
    public double sizeY = 1;
    public double sizeZ = 1;
    public double radius = 0.5;
    public double height = 1;
    public int segments = 16;
    public float[] vertices;
    public int[] indices;
    public final List<Child> children = new ArrayList<>();

    public static ShapeSpec box(double x, double y, double z) {
        ShapeSpec spec = new ShapeSpec();
        spec.kind = Kind.BOX;
        spec.sizeX = x;
        spec.sizeY = y;
        spec.sizeZ = z;
        return spec;
    }

    public static ShapeSpec sphere(double radius) {
        ShapeSpec spec = new ShapeSpec();
        spec.kind = Kind.SPHERE;
        spec.radius = radius;
        return spec;
    }

    public boolean requiresNonDynamic() {
        if (kind == Kind.MESH) {
            return true;
        }
        if (kind == Kind.COMPOUND) {
            for (Child child : children) {
                if (child.shape.requiresNonDynamic()) {
                    return true;
                }
            }
        }
        return false;
    }

    public ShapeSpec scaled(double factor) {
        if (!(factor > 0)) {
            throw new PhysException("Scale must be positive.");
        }
        ShapeSpec copy = new ShapeSpec();
        copy.kind = kind;
        copy.sizeX = sizeX * factor;
        copy.sizeY = sizeY * factor;
        copy.sizeZ = sizeZ * factor;
        copy.radius = radius * factor;
        copy.height = height * factor;
        copy.segments = segments;
        if (vertices != null) {
            copy.vertices = new float[vertices.length];
            for (int i = 0; i < vertices.length; i++) {
                copy.vertices[i] = (float) (vertices[i] * factor);
            }
        }
        copy.indices = indices == null ? null : indices.clone();
        for (Child child : children) {
            Child scaledChild = new Child();
            scaledChild.shape = child.shape.scaled(factor);
            scaledChild.offset = new double[]{child.offset[0] * factor, child.offset[1] * factor, child.offset[2] * factor};
            scaledChild.rotation = child.rotation.clone();
            copy.children.add(scaledChild);
        }
        return copy;
    }

    public String describe() {
        switch (kind) {
            case BOX:
                return "box[" + fmt(sizeX) + "," + fmt(sizeY) + "," + fmt(sizeZ) + "]";
            case SPHERE:
                return "sphere[r=" + fmt(radius) + "]";
            case CAPSULE:
                return "capsule[h=" + fmt(height) + ",r=" + fmt(radius) + "]";
            case CYLINDER:
                return "cylinder[h=" + fmt(height) + ",r=" + fmt(radius) + "]";
            case CONE:
                return "cone[h=" + fmt(height) + ",r=" + fmt(radius) + "]";
            case HULL:
                return "hull[points=" + (vertices == null ? 0 : vertices.length / 3) + "]";
            case MESH:
                return "mesh[triangles=" + triangleCount() + "]";
            default:
                return "compound[children=" + children.size() + "]";
        }
    }

    public int triangleCount() {
        if (vertices == null) {
            return 0;
        }
        if (indices == null) {
            return vertices.length / 9;
        }
        return indices.length / 3;
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    public double[] halfExtentsEstimate() {
        switch (kind) {
            case BOX:
                return new double[]{sizeX / 2, sizeY / 2, sizeZ / 2};
            case SPHERE:
                return new double[]{radius, radius, radius};
            case CAPSULE:
                return new double[]{radius, Math.max(height / 2, radius), radius};
            case CYLINDER:
            case CONE:
                return new double[]{radius, height / 2, radius};
            case HULL:
            case MESH:
                return boundsOfVertices();
            default:
                double[] max = {0.05, 0.05, 0.05};
                for (Child child : children) {
                    double[] sub = child.shape.halfExtentsEstimate();
                    for (int i = 0; i < 3; i++) {
                        max[i] = Math.max(max[i], Math.abs(child.offset[i]) + sub[i]);
                    }
                }
                return max;
        }
    }

    private double[] boundsOfVertices() {
        double[] max = {0.05, 0.05, 0.05};
        if (vertices == null) {
            return max;
        }
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            max[0] = Math.max(max[0], Math.abs(vertices[i]));
            max[1] = Math.max(max[1], Math.abs(vertices[i + 1]));
            max[2] = Math.max(max[2], Math.abs(vertices[i + 2]));
        }
        return max;
    }

    public ShapeRefC build() {
        switch (kind) {
            case BOX: {
                float hx = (float) Math.max(sizeX / 2, 0.005);
                float hy = (float) Math.max(sizeY / 2, 0.005);
                float hz = (float) Math.max(sizeZ / 2, 0.005);
                float convex = Math.min(0.05f, Math.min(hx, Math.min(hy, hz)) * 0.9f);
                return new BoxShape(new Vec3(hx, hy, hz), convex).toRefC();
            }
            case SPHERE:
                return new SphereShape((float) Math.max(radius, 0.005)).toRefC();
            case CAPSULE: {
                float r = (float) Math.max(radius, 0.005);
                float half = (float) Math.max((height - 2 * radius) / 2, 0.005);
                return new CapsuleShape(half, r).toRefC();
            }
            case CYLINDER: {
                float r = (float) Math.max(radius, 0.01);
                float half = (float) Math.max(height / 2, 0.01);
                float convex = Math.min(0.05f, Math.min(r, half) * 0.9f);
                return new CylinderShape(half, r, convex).toRefC();
            }
            case CONE:
                return buildHull(coneVertices());
            case HULL:
                return buildHull(vertices);
            case MESH:
                return buildMesh();
            default:
                return buildCompound();
        }
    }

    private float[] coneVertices() {
        int n = Math.max(3, segments);
        float[] points = new float[(n + 1) * 3];
        float half = (float) (height / 2);
        points[0] = 0;
        points[1] = half;
        points[2] = 0;
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            points[3 + i * 3] = (float) (Math.cos(angle) * radius);
            points[4 + i * 3] = -half;
            points[5 + i * 3] = (float) (Math.sin(angle) * radius);
        }
        return points;
    }

    private static ShapeRefC buildHull(float[] points) {
        if (points == null || points.length < 12) {
            throw new PhysException("A hull shape needs at least 4 points.");
        }
        int count = points.length / 3;
        FloatBuffer buffer = direct(points, count * 3);
        ConvexHullShapeSettings settings = new ConvexHullShapeSettings(count, buffer);
        ShapeResult result = settings.create();
        if (result.hasError()) {
            throw new PhysException("Failed to build hull shape: " + result.getError());
        }
        return result.get();
    }

    private ShapeRefC buildMesh() {
        if (vertices == null || vertices.length < 9) {
            throw new PhysException("A mesh shape needs at least one triangle.");
        }
        float[] soup;
        if (indices == null) {
            soup = vertices;
        }
        else {
            soup = new float[indices.length * 3];
            for (int i = 0; i < indices.length; i++) {
                int base = indices[i] * 3;
                if (base < 0 || base + 2 >= vertices.length) {
                    throw new PhysException("Mesh index " + indices[i] + " is out of range.");
                }
                soup[i * 3] = vertices[base];
                soup[i * 3 + 1] = vertices[base + 1];
                soup[i * 3 + 2] = vertices[base + 2];
            }
        }
        int usable = (soup.length / 9) * 9;
        FloatBuffer buffer = direct(soup, usable);
        MeshShapeSettings settings = new MeshShapeSettings(buffer);
        ShapeResult result = settings.create();
        if (result.hasError()) {
            throw new PhysException("Failed to build mesh shape: " + result.getError());
        }
        return result.get();
    }

    private ShapeRefC buildCompound() {
        if (children.isEmpty()) {
            throw new PhysException("A compound shape needs at least one child.");
        }
        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
        for (Child child : children) {
            ShapeRefC sub = child.shape.build();
            Vec3 offset = new Vec3((float) child.offset[0], (float) child.offset[1], (float) child.offset[2]);
            Quat rotation = new Quat((float) child.rotation[0], (float) child.rotation[1], (float) child.rotation[2], (float) child.rotation[3]);
            settings.addShape(offset, rotation, sub);
        }
        ShapeResult result = settings.create();
        if (result.hasError()) {
            throw new PhysException("Failed to build compound shape: " + result.getError());
        }
        return result.get();
    }

    private static FloatBuffer direct(float[] data, int length) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(length * Float.BYTES).order(ByteOrder.nativeOrder());
        FloatBuffer buffer = bytes.asFloatBuffer();
        buffer.put(data, 0, length);
        buffer.flip();
        return buffer;
    }

    public MapTag toMap() {
        MapTag map = new MapTag();
        map.putObject("type", new ElementTag(kind.name().toLowerCase()));
        switch (kind) {
            case BOX:
                map.putObject("size", new ElementTag(sizeX + "," + sizeY + "," + sizeZ));
                break;
            case SPHERE:
                map.putObject("radius", new ElementTag(radius));
                break;
            case CAPSULE:
                map.putObject("radius", new ElementTag(radius));
                map.putObject("height", new ElementTag(height));
                break;
            case CYLINDER:
                map.putObject("radius", new ElementTag(radius));
                map.putObject("height", new ElementTag(height));
                break;
            case CONE:
                map.putObject("radius", new ElementTag(radius));
                map.putObject("height", new ElementTag(height));
                map.putObject("segments", new ElementTag(segments));
                break;
            case HULL:
            case MESH:
                if (vertices != null) {
                    ListTag points = new ListTag();
                    for (int i = 0; i + 2 < vertices.length; i += 3) {
                        points.add(vertices[i] + "," + vertices[i + 1] + "," + vertices[i + 2]);
                    }
                    map.putObject("points", points);
                }
                if (indices != null && kind == Kind.MESH) {
                    ListTag triangles = new ListTag();
                    for (int idx : indices) {
                        triangles.add(String.valueOf(idx));
                    }
                    map.putObject("triangles", triangles);
                }
                break;
            case COMPOUND:
                ListTag list = new ListTag();
                for (Child child : children) {
                    MapTag childMap = child.shape.toMap();
                    childMap.putObject("offset", new ElementTag(child.offset[0] + "," + child.offset[1] + "," + child.offset[2]));
                    childMap.putObject("rotation", new QuaternionTag(child.rotation[0], child.rotation[1], child.rotation[2], child.rotation[3]));
                    list.addObject(childMap);
                }
                map.putObject("children", list);
                break;
        }
        return map;
    }
}