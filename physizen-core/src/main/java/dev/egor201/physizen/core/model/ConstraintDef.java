package dev.egor201.physizen.core.model;

public final class ConstraintDef {

    public enum Kind {
        FIXED,
        POINT,
        HINGE,
        SLIDER,
        DISTANCE,
        CONE
    }

    public enum MotorState {
        OFF,
        VELOCITY,
        POSITION
    }

    public String name;
    public Kind kind = Kind.POINT;
    public String bodyA;
    public String bodyB;
    public double[] pointA = new double[3];
    public double[] pointB;
    public double[] axis = {0, 1, 0};
    public double[] normalAxis = {1, 0, 0};
    public double limitMin = 0;
    public double limitMax = 0;
    public boolean limited = false;
    public double springFrequency = 0;
    public double springDamping = 0.5;
    public double maxFriction = 0;
    public double halfConeAngle = 0.7853981634;

    public MotorState motorState = MotorState.OFF;
    public double motorTarget = 0;
    public double motorMaxTorque = 0;
    public double motorMaxForce = 0;
    public double motorSpringFrequency = 2.0;
    public double motorSpringDamping = 1.0;

    public double[] localA;
    public double[] localB;
    public double[] localAxis;
    public double[] localNormal;

    public void captureLocal(Snapshot a, Snapshot b) {
        localA = toLocal(a, pointA);
        localB = toLocal(b, pointB == null ? pointA : pointB);
        localAxis = rotate(a, axis, true);
        localNormal = rotate(a, normalAxis, true);
    }

    public static double[] toLocal(Snapshot s, double[] p) {
        if (s == null) {
            return p.clone();
        }
        return rotate(s, new double[]{p[0] - s.px, p[1] - s.py, p[2] - s.pz}, true);
    }

    public static double[] toWorld(Snapshot s, double[] p) {
        if (s == null) {
            return p.clone();
        }
        double[] r = rotate(s, p, false);
        return new double[]{r[0] + s.px, r[1] + s.py, r[2] + s.pz};
    }

    public static double[] rotate(Snapshot s, double[] v, boolean inverse) {
        if (s == null) {
            return v.clone();
        }
        double qx = inverse ? -s.qx : s.qx;
        double qy = inverse ? -s.qy : s.qy;
        double qz = inverse ? -s.qz : s.qz;
        double qw = s.qw;
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
