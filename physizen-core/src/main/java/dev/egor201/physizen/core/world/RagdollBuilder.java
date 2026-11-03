package dev.egor201.physizen.core.world;

import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.model.MotionKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RagdollBuilder {

    public static final class Result {

        public final List<BodyDef> bodies = new ArrayList<>();
        public final List<ConstraintDef> constraints = new ArrayList<>();
    }

    public static final class Options {

        public String id;
        public double[] position = new double[3];
        public double[] rotation = {0, 0, 0, 1};
        public double scale = 1;
        public double totalMass = 70;
        public double[] velocity = new double[3];
        public double friction = 0.6;
        public double restitution = 0.0;
        public double linearDamping = 0.05;
        public double angularDamping = 0.15;
        public int group = 0;
        public double jointFriction = 0.0;
    }

    private RagdollBuilder() {
    }

    public static String partName(String id, String bone) {
        return id + "." + bone;
    }

    public static Result build(RagdollSpec spec, Options o) {
        spec.validate();
        if (o.id == null || o.id.isEmpty()) {
            throw new PhysException("Ragdoll needs a name.");
        }
        if (!(o.scale > 0) || o.scale > 100) {
            throw new PhysException("Ragdoll scale must be between 0 and 100.");
        }
        if (!(o.totalMass > 0)) {
            throw new PhysException("Ragdoll mass must be positive.");
        }
        double weightSum = 0;
        for (RagdollSpec.Bone b : spec.bones) {
            weightSum += Math.max(0.0001, b.massWeight);
        }
        Result result = new Result();
        Map<String, BodyDef> defs = new HashMap<>();
        int index = 0;
        for (RagdollSpec.Bone b : spec.bones) {
            BodyDef d = new BodyDef();
            d.name = partName(o.id, b.name);
            d.shape = b.shape.scaled(o.scale);
            double[] c = world(o, b.center);
            d.position = c;
            d.rotation = o.rotation.clone();
            d.type = MotionKind.DYNAMIC;
            d.mass = Math.max(0.01, o.totalMass * Math.max(0.0001, b.massWeight) / weightSum);
            d.friction = o.friction;
            d.restitution = o.restitution;
            d.linearDamping = o.linearDamping;
            d.angularDamping = o.angularDamping;
            d.group = o.group;
            d.linearVelocity = o.velocity.clone();
            d.allowSleeping = true;
            d.notify = false;
            d.ragdoll = o.id;
            d.ragdollIndex = index++;
            d.tag = "ragdoll:" + o.id;
            result.bodies.add(d);
            defs.put(b.name, d);
        }
        for (RagdollSpec.Bone b : spec.bones) {
            if (b.parent == null) {
                continue;
            }
            ConstraintDef cd = new ConstraintDef();
            cd.name = partName(o.id, b.name) + ".joint";
            cd.kind = ConstraintDef.Kind.SWING_TWIST;
            cd.bodyA = partName(o.id, b.parent);
            cd.bodyB = partName(o.id, b.name);
            double[] anchor = world(o, b.anchor);
            cd.pointA = anchor;
            cd.pointB = anchor.clone();
            double[] twist = norm(rotate(o.rotation, b.twistAxis));
            double[] plane = rotate(o.rotation, b.planeAxis);
            double dot = plane[0] * twist[0] + plane[1] * twist[1] + plane[2] * twist[2];
            plane = norm(new double[]{plane[0] - dot * twist[0], plane[1] - dot * twist[1], plane[2] - dot * twist[2]});
            cd.axis = twist;
            cd.normalAxis = plane;
            cd.swingNormalHalfCone = Math.toRadians(clamp(b.swingNormal, 0, 180));
            cd.swingPlaneHalfCone = Math.toRadians(clamp(b.swingPlane, 0, 180));
            cd.twistMin = Math.toRadians(clamp(Math.min(b.twistMin, b.twistMax), -180, 180));
            cd.twistMax = Math.toRadians(clamp(Math.max(b.twistMin, b.twistMax), -180, 180));
            cd.maxFriction = Math.max(0, Math.max(o.jointFriction, b.jointFriction));
            result.constraints.add(cd);
        }
        return result;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double[] norm(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-9) {
            throw new PhysException("Ragdoll bone axis cannot be zero.");
        }
        return new double[]{v[0] / len, v[1] / len, v[2] / len};
    }

    private static double[] world(Options o, double[] local) {
        double[] s = {local[0] * o.scale, local[1] * o.scale, local[2] * o.scale};
        double[] r = rotate(o.rotation, s);
        return new double[]{o.position[0] + r[0], o.position[1] + r[1], o.position[2] + r[2]};
    }

    static double[] rotate(double[] q, double[] v) {
        double qx = q[0], qy = q[1], qz = q[2], qw = q[3];
        double tx = 2 * (qy * v[2] - qz * v[1]);
        double ty = 2 * (qz * v[0] - qx * v[2]);
        double tz = 2 * (qx * v[1] - qy * v[0]);
        return new double[]{
                v[0] + qw * tx + (qy * tz - qz * ty),
                v[1] + qw * ty + (qz * tx - qx * tz),
                v[2] + qw * tz + (qx * ty - qy * tx)
        };
    }
}
