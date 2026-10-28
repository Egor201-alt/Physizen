package dev.egor201.physizen.core;

public final class PhysEvent {

    public enum Kind {
        COLLIDE,
        WAKE,
        SLEEP,
        FALL_OUT
    }

    public final Kind kind;
    public final PhysWorld world;
    public final PhysBody body;
    public final PhysBody other;
    public final double[] point;
    public final double[] normal;
    public final double speed;

    public PhysEvent(Kind kind, PhysWorld world, PhysBody body, PhysBody other, double[] point, double[] normal, double speed) {
        this.kind = kind;
        this.world = world;
        this.body = body;
        this.other = other;
        this.point = point;
        this.normal = normal;
        this.speed = speed;
    }
}
