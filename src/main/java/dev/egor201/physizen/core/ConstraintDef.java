package dev.egor201.physizen.core;

public final class ConstraintDef {

    public enum Kind {
        FIXED,
        POINT,
        HINGE,
        SLIDER,
        DISTANCE,
        CONE
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
}
