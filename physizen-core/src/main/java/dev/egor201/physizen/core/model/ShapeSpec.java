package dev.egor201.physizen.core.model;

import dev.egor201.physizen.core.PhysException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    public String sourceFile;
    public double sourceScale = 1;
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

    public static ShapeSpec capsule(double radius, double height) {
        ShapeSpec spec = new ShapeSpec();
        spec.kind = Kind.CAPSULE;
        spec.radius = radius;
        spec.height = height;
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
        copy.sourceFile = sourceFile;
        copy.sourceScale = sourceScale * factor;
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
        return String.format(Locale.ROOT, "%.3f", value);
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
}
