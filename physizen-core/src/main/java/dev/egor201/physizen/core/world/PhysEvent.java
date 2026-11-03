package dev.egor201.physizen.core.world;

public final class PhysEvent {

    public enum Kind {
        COLLIDE,
        CONTACT_PERSISTED,
        CONTACT_REMOVED,
        SENSOR_ENTER,
        SENSOR_EXIT,
        SENSOR_STAY,
        WAKE,
        SLEEP,
        FALL_OUT,
        RAGDOLL_HIT,
        VEHICLE_HIT,
        CONSTRAINT_BREAK
    }

    public final Kind kind;
    public final PhysWorld world;
    public final PhysBody body;
    public final PhysBody other;
    public final double[] point;
    public final double[] normal;
    public final double speed;
    public final double depth;
    public String owner;

    public PhysEvent(Kind kind, PhysWorld world, PhysBody body, PhysBody other, double[] point, double[] normal, double speed, double depth) {
        this.kind = kind;
        this.world = world;
        this.body = body;
        this.other = other;
        this.point = point;
        this.normal = normal;
        this.speed = speed;
        this.depth = depth;
    }

    public PhysEvent(Kind kind, PhysWorld world, PhysBody body, PhysBody other, double[] point, double[] normal, double speed) {
        this(kind, world, body, other, point, normal, speed, 0);
    }

    public PhysEvent withOwner(String owner) {
        this.owner = owner;
        return this;
    }
}
