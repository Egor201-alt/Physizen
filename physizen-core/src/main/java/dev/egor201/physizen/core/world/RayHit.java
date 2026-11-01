package dev.egor201.physizen.core.world;

public final class RayHit {

    public final PhysBody body;
    public final double[] point;
    public final double[] normal;
    public final double distance;
    public final double fraction;

    public RayHit(PhysBody body, double[] point, double[] normal, double distance, double fraction) {
        this.body = body;
        this.point = point;
        this.normal = normal;
        this.distance = distance;
        this.fraction = fraction;
    }
}
