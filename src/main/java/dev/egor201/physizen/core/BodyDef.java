package dev.egor201.physizen.core;

public final class BodyDef {

    public String name;
    public ShapeSpec shape;
    public double[] position = new double[3];
    public double[] rotation = {0, 0, 0, 1};
    public MotionKind type = MotionKind.DYNAMIC;
    public double mass = 0;
    public double friction = 0.5;
    public double restitution = 0;
    public double linearDamping = 0.05;
    public double angularDamping = 0.05;
    public double gravityFactor = 1;
    public double maxLinearVelocity = 500;
    public double maxAngularVelocity = 47.1238898;
    public double[] linearVelocity = new double[3];
    public double[] angularVelocity = new double[3];
    public int group = 0;
    public boolean sensor = false;
    public boolean continuous = false;
    public boolean allowSleeping = true;
    public boolean notify = false;
    public boolean removeOnFallOut = true;
    public boolean internal = false;
    public String tag = "";
}
