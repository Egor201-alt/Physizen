package dev.egor201.physizen.core.shape;

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
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.ShapeSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public final class ShapeBuilder {

    private ShapeBuilder() {
    }

    public static ShapeRefC build(ShapeSpec spec) {
        switch (spec.kind) {
            case BOX: {
                float hx = (float) Math.max(spec.sizeX / 2, 0.005);
                float hy = (float) Math.max(spec.sizeY / 2, 0.005);
                float hz = (float) Math.max(spec.sizeZ / 2, 0.005);
                float convex = Math.min(0.05f, Math.min(hx, Math.min(hy, hz)) * 0.9f);
                return new BoxShape(new Vec3(hx, hy, hz), convex).toRefC();
            }
            case SPHERE:
                return new SphereShape((float) Math.max(spec.radius, 0.005)).toRefC();
            case CAPSULE: {
                float r = (float) Math.max(spec.radius, 0.005);
                float half = (float) Math.max((spec.height - 2 * spec.radius) / 2, 0.005);
                return new CapsuleShape(half, r).toRefC();
            }
            case CYLINDER: {
                float r = (float) Math.max(spec.radius, 0.01);
                float half = (float) Math.max(spec.height / 2, 0.01);
                float convex = Math.min(0.05f, Math.min(r, half) * 0.9f);
                return new CylinderShape(half, r, convex).toRefC();
            }
            case CONE:
                return buildHull(coneVertices(spec));
            case HULL:
                return buildHull(spec.vertices);
            case MESH:
                return buildMesh(spec);
            default:
                return buildCompound(spec);
        }
    }

    private static float[] coneVertices(ShapeSpec spec) {
        int n = Math.max(3, spec.segments);
        float[] points = new float[(n + 1) * 3];
        float half = (float) (spec.height / 2);
        points[0] = 0;
        points[1] = half;
        points[2] = 0;
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            points[3 + i * 3] = (float) (Math.cos(angle) * spec.radius);
            points[4 + i * 3] = -half;
            points[5 + i * 3] = (float) (Math.sin(angle) * spec.radius);
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

    private static ShapeRefC buildMesh(ShapeSpec spec) {
        if (spec.vertices == null || spec.vertices.length < 9) {
            throw new PhysException("A mesh shape needs at least one triangle.");
        }
        float[] soup;
        if (spec.indices == null) {
            soup = spec.vertices;
        }
        else {
            soup = new float[spec.indices.length * 3];
            for (int i = 0; i < spec.indices.length; i++) {
                int base = spec.indices[i] * 3;
                if (base < 0 || base + 2 >= spec.vertices.length) {
                    throw new PhysException("Mesh index " + spec.indices[i] + " is out of range.");
                }
                soup[i * 3] = spec.vertices[base];
                soup[i * 3 + 1] = spec.vertices[base + 1];
                soup[i * 3 + 2] = spec.vertices[base + 2];
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

    private static ShapeRefC buildCompound(ShapeSpec spec) {
        if (spec.children.isEmpty()) {
            throw new PhysException("A compound shape needs at least one child.");
        }
        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
        List<ShapeRefC> parts = new ArrayList<>(spec.children.size());
        try {
            for (ShapeSpec.Child child : spec.children) {
                ShapeRefC sub = build(child.shape);
                parts.add(sub);
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
        finally {
            for (ShapeRefC part : parts) {
                part.close();
            }
        }
    }

    private static FloatBuffer direct(float[] data, int length) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(length * Float.BYTES).order(ByteOrder.nativeOrder());
        FloatBuffer buffer = bytes.asFloatBuffer();
        buffer.put(data, 0, length);
        buffer.flip();
        return buffer;
    }
}
