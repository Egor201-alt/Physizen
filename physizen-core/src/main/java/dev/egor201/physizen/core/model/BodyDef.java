package dev.egor201.physizen.core.model;

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
    public boolean detectStatic = false;
    public boolean continuous = false;
    public boolean allowSleeping = true;
    public boolean notify = false;
    public boolean notifyPersist = false;
    public boolean removeOnFallOut = true;
    public boolean internal = false;
    public boolean managed = false;
    public String tag = "";

    public BodyDef copy() {
        BodyDef c = new BodyDef();
        c.name = name;
        c.shape = shape;
        c.position = position.clone();
        c.rotation = rotation.clone();
        c.type = type;
        c.mass = mass;
        c.friction = friction;
        c.restitution = restitution;
        c.linearDamping = linearDamping;
        c.angularDamping = angularDamping;
        c.gravityFactor = gravityFactor;
        c.maxLinearVelocity = maxLinearVelocity;
        c.maxAngularVelocity = maxAngularVelocity;
        c.linearVelocity = linearVelocity.clone();
        c.angularVelocity = angularVelocity.clone();
        c.group = group;
        c.sensor = sensor;
        c.detectStatic = detectStatic;
        c.continuous = continuous;
        c.allowSleeping = allowSleeping;
        c.notify = notify;
        c.notifyPersist = notifyPersist;
        c.removeOnFallOut = removeOnFallOut;
        c.internal = internal;
        c.managed = managed;
        c.tag = tag;
        return c;
    }
}
