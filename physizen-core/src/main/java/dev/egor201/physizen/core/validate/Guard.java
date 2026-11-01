package dev.egor201.physizen.core.validate;

import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.CharacterDef;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.model.ShapeSpec;

public final class Guard {

    public static final double MAX_COORDINATE = 3.0e7;
    public static final double MAX_VALUE = 1.0e9;
    public static final double MAX_SIZE = 1.0e5;
    public static final int MAX_COMPOUND_DEPTH = 4;

    private Guard() {
    }

    public static boolean ok(double... values) {
        for (double v : values) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return false;
            }
        }
        return true;
    }

    public static boolean inWorld(double x, double y, double z) {
        return ok(x, y, z) && Math.abs(x) <= MAX_COORDINATE * 4 && Math.abs(y) <= MAX_COORDINATE * 4 && Math.abs(z) <= MAX_COORDINATE * 4;
    }

    public static void number(double v, String what) {
        if (!ok(v) || Math.abs(v) > MAX_VALUE) {
            throw new PhysException("Invalid " + what + ": " + v + ".");
        }
    }

    public static void positive(double v, String what) {
        number(v, what);
        if (v <= 0) {
            throw new PhysException(what + " must be greater than zero.");
        }
    }

    public static void vec(double[] v, String what) {
        if (v == null || v.length < 3) {
            throw new PhysException("Missing " + what + ".");
        }
        for (int i = 0; i < 3; i++) {
            number(v[i], what);
        }
    }

    public static void position(double[] v, String what) {
        vec(v, what);
        for (int i = 0; i < 3; i++) {
            if (Math.abs(v[i]) > MAX_COORDINATE) {
                throw new PhysException(what + " is outside the supported world size.");
            }
        }
    }

    public static void quat(double[] q, String what) {
        if (q == null || q.length < 4) {
            throw new PhysException("Missing " + what + ".");
        }
        double sum = 0;
        for (int i = 0; i < 4; i++) {
            number(q[i], what);
            sum += q[i] * q[i];
        }
        if (sum < 1.0e-12) {
            throw new PhysException("Invalid " + what + " (zero rotation).");
        }
    }

    public static void normalizeQuat(double[] q) {
        double len = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        for (int i = 0; i < 4; i++) {
            q[i] /= len;
        }
    }

    public static void normalizeAxis(double[] a, String what) {
        double len = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (len < 1.0e-6) {
            throw new PhysException(what + " must not be a zero vector.");
        }
        for (int i = 0; i < 3; i++) {
            a[i] /= len;
        }
    }

    public static void validateDef(BodyDef def, int maxChildren) {
        if (def.name == null || def.name.isEmpty()) {
            throw new PhysException("Body needs a name.");
        }
        position(def.position, "position");
        quat(def.rotation, "rotation");
        normalizeQuat(def.rotation);
        vec(def.linearVelocity, "velocity");
        vec(def.angularVelocity, "angular velocity");
        number(def.mass, "mass");
        if (def.mass < 0) {
            throw new PhysException("Mass cannot be negative.");
        }
        number(def.friction, "friction");
        number(def.restitution, "restitution");
        number(def.linearDamping, "linear damping");
        number(def.angularDamping, "angular damping");
        number(def.gravityFactor, "gravity factor");
        number(def.maxLinearVelocity, "max velocity");
        number(def.maxAngularVelocity, "max angular velocity");
        if (def.friction < 0 || def.restitution < 0 || def.linearDamping < 0 || def.angularDamping < 0) {
            throw new PhysException("Friction, restitution and damping cannot be negative.");
        }
        validateShape(def.shape, 0, maxChildren);
    }

    public static void validateShape(ShapeSpec s, int depth, int maxChildren) {
        if (s == null) {
            throw new PhysException("Body needs a shape.");
        }
        switch (s.kind) {
            case BOX:
                positive(s.sizeX, "box size");
                positive(s.sizeY, "box size");
                positive(s.sizeZ, "box size");
                if (s.sizeX > MAX_SIZE || s.sizeY > MAX_SIZE || s.sizeZ > MAX_SIZE) {
                    throw new PhysException("Box is too large.");
                }
                break;
            case SPHERE:
                positive(s.radius, "radius");
                if (s.radius > MAX_SIZE) {
                    throw new PhysException("Sphere is too large.");
                }
                break;
            case CAPSULE:
            case CYLINDER:
            case CONE:
                positive(s.radius, "radius");
                positive(s.height, "height");
                if (s.radius > MAX_SIZE || s.height > MAX_SIZE) {
                    throw new PhysException("Shape is too large.");
                }
                if (s.kind == ShapeSpec.Kind.CONE && (s.segments < 3 || s.segments > 256)) {
                    throw new PhysException("Cone segments must be between 3 and 256.");
                }
                break;
            case HULL:
                checkVertices(s, 12);
                break;
            case MESH:
                checkVertices(s, 9);
                if (s.indices == null || s.indices.length < 3 || s.indices.length % 3 != 0) {
                    throw new PhysException("Mesh needs triangle indices.");
                }
                int count = s.vertices.length / 3;
                for (int idx : s.indices) {
                    if (idx < 0 || idx >= count) {
                        throw new PhysException("Mesh index is out of range.");
                    }
                }
                break;
            default:
                if (depth >= MAX_COMPOUND_DEPTH) {
                    throw new PhysException("Compound shapes are nested too deeply.");
                }
                if (s.children.isEmpty() || s.children.size() > maxChildren) {
                    throw new PhysException("Compound shape needs between 1 and " + maxChildren + " parts.");
                }
                for (ShapeSpec.Child c : s.children) {
                    position(c.offset, "part offset");
                    quat(c.rotation, "part rotation");
                    validateShape(c.shape, depth + 1, maxChildren);
                }
                break;
        }
    }

    private static void checkVertices(ShapeSpec s, int minFloats) {
        if (s.vertices == null || s.vertices.length < minFloats || s.vertices.length % 3 != 0) {
            throw new PhysException("Shape has too few vertices.");
        }
        for (float f : s.vertices) {
            if (Float.isNaN(f) || Float.isInfinite(f) || Math.abs(f) > MAX_SIZE) {
                throw new PhysException("Shape has an invalid vertex.");
            }
        }
    }

    public static void validateConstraint(ConstraintDef d) {
        position(d.pointA, "constraint point");
        if (d.pointB != null) {
            position(d.pointB, "constraint point");
        }
        vec(d.axis, "constraint axis");
        vec(d.normalAxis, "constraint normal");
        normalizeAxis(d.axis, "Constraint axis");
        normalizeAxis(d.normalAxis, "Constraint normal");
        number(d.limitMin, "limit");
        number(d.limitMax, "limit");
        number(d.maxFriction, "friction");
        number(d.springFrequency, "spring frequency");
        number(d.springDamping, "spring damping");
        number(d.halfConeAngle, "cone angle");
        number(d.motorTarget, "motor target");
        if (d.limited && d.limitMin > d.limitMax) {
            throw new PhysException("Lower limit must not be greater than the upper limit.");
        }
        if (d.kind == ConstraintDef.Kind.HINGE && d.limited && (d.limitMin < -Math.PI || d.limitMax > Math.PI)) {
            throw new PhysException("Hinge limits must stay between -180 and 180 degrees.");
        }
        if (d.maxFriction < 0 || d.springFrequency < 0 || d.springDamping < 0) {
            throw new PhysException("Friction and spring values cannot be negative.");
        }
        if (d.halfConeAngle < 0 || d.halfConeAngle > Math.PI) {
            throw new PhysException("Cone angle must stay between 0 and 180 degrees.");
        }
    }

    public static void validateCharacter(CharacterDef d, int maxChildren) {
        if (d.name == null || d.name.isEmpty()) {
            throw new PhysException("Character needs a name.");
        }
        position(d.position, "position");
        quat(d.rotation, "rotation");
        normalizeQuat(d.rotation);
        vec(d.up, "up");
        normalizeAxis(d.up, "Up vector");
        validateShape(d.shape, 0, maxChildren);
        if (d.shape.requiresNonDynamic()) {
            throw new PhysException("Mesh shapes cannot be used by characters.");
        }
        positive(d.mass, "mass");
        positive(d.maxStrength, "max strength");
        number(d.maxSlopeDegrees, "max slope");
        if (d.maxSlopeDegrees < 0 || d.maxSlopeDegrees > 89) {
            throw new PhysException("Max slope must be between 0 and 89 degrees.");
        }
        number(d.padding, "padding");
        number(d.penetrationRecoverySpeed, "recovery speed");
        number(d.predictiveContactDistance, "predictive contact distance");
        number(d.gravityFactor, "gravity factor");
        number(d.stepHeight, "step height");
        number(d.stickDistance, "stick distance");
        if (d.padding < 0 || d.penetrationRecoverySpeed < 0 || d.predictiveContactDistance < 0 || d.stepHeight < 0 || d.stickDistance < 0) {
            throw new PhysException("Character distances and speeds cannot be negative.");
        }
    }
}
