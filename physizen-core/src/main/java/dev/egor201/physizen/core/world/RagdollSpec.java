package dev.egor201.physizen.core.world;

import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.ShapeSpec;

import java.util.ArrayList;
import java.util.List;

public final class RagdollSpec {

    public static final int MAX_BONES = 64;

    public static final class Bone {

        public String name;
        public String parent;
        public ShapeSpec shape;
        public double[] center = new double[3];
        public double[] anchor = new double[3];
        public double[] twistAxis = {0, 1, 0};
        public double[] planeAxis = {1, 0, 0};
        public double swingNormal = 45;
        public double swingPlane = 45;
        public double twistMin = -45;
        public double twistMax = 45;
        public double massWeight = 1;
        public double jointFriction = 0.0;
    }

    public String preset = "custom";
    public final List<Bone> bones = new ArrayList<>();

    public Bone bone(String name) {
        for (Bone b : bones) {
            if (b.name.equals(name)) {
                return b;
            }
        }
        return null;
    }

    public void validate() {
        if (bones.isEmpty()) {
            throw new PhysException("Ragdoll needs at least one bone.");
        }
        if (bones.size() > MAX_BONES) {
            throw new PhysException("Ragdoll can have at most " + MAX_BONES + " bones.");
        }
        int roots = 0;
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Bone b : bones) {
            if (b.name == null || b.name.isEmpty()) {
                throw new PhysException("Every ragdoll bone needs a name.");
            }
            if (!names.add(b.name)) {
                throw new PhysException("Duplicate ragdoll bone '" + b.name + "'.");
            }
            if (b.shape == null) {
                throw new PhysException("Bone '" + b.name + "' needs a shape.");
            }
            if (b.parent == null) {
                roots++;
            }
        }
        if (roots != 1) {
            throw new PhysException("Ragdoll needs exactly one root bone (without parent).");
        }
        for (Bone b : bones) {
            if (b.parent != null && !names.contains(b.parent)) {
                throw new PhysException("Bone '" + b.name + "' has unknown parent '" + b.parent + "'.");
            }
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Bone b : bones) {
            if (b.parent != null && !seen.contains(b.parent)) {
                throw new PhysException("Bone '" + b.name + "' must be listed after its parent '" + b.parent + "'.");
            }
            seen.add(b.name);
        }
    }

    private static Bone box(String name, String parent, double sx, double sy, double sz, double cx, double cy, double cz,
                            double ax, double ay, double az, double[] twist, double[] plane,
                            double swingNormal, double swingPlane, double twistMin, double twistMax, double mass) {
        Bone b = new Bone();
        b.name = name;
        b.parent = parent;
        b.shape = ShapeSpec.box(sx, sy, sz);
        b.center = new double[]{cx, cy, cz};
        b.anchor = new double[]{ax, ay, az};
        b.twistAxis = twist;
        b.planeAxis = plane;
        b.swingNormal = swingNormal;
        b.swingPlane = swingPlane;
        b.twistMin = twistMin;
        b.twistMax = twistMax;
        b.massWeight = mass;
        return b;
    }

    public static RagdollSpec humanoid() {
        RagdollSpec spec = new RagdollSpec();
        spec.preset = "humanoid";
        double[] up = {0, 1, 0};
        double[] down = {0, -1, 0};
        double[] x = {1, 0, 0};
        spec.bones.add(box("torso", null, 0.5, 0.75, 0.25, 0, 1.125, 0, 0, 1.125, 0, up, x, 0, 0, 0, 0, 40));
        spec.bones.add(box("head", "torso", 0.5, 0.5, 0.5, 0, 1.75, 0, 0, 1.5, 0, up, x, 50, 45, -70, 70, 8));
        spec.bones.add(box("arm_left", "torso", 0.25, 0.75, 0.25, 0.375, 1.125, 0, 0.375, 1.45, 0, down, x, 100, 75, -45, 45, 7));
        spec.bones.add(box("arm_right", "torso", 0.25, 0.75, 0.25, -0.375, 1.125, 0, -0.375, 1.45, 0, down, x, 100, 75, -45, 45, 7));
        spec.bones.add(box("leg_left", "torso", 0.25, 0.75, 0.25, 0.125, 0.375, 0, 0.125, 0.75, 0, down, x, 70, 30, -25, 25, 15));
        spec.bones.add(box("leg_right", "torso", 0.25, 0.75, 0.25, -0.125, 0.375, 0, -0.125, 0.75, 0, down, x, 70, 30, -25, 25, 15));
        return spec;
    }
}
