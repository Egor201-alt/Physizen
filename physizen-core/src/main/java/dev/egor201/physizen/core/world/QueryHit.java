package dev.egor201.physizen.core.world;

public final class QueryHit {

    public final PhysBody body;
    public final double[] point;
    public final double[] normal;
    public final double distance;
    public final double fraction;
    public final double depth;
    public final boolean inside;

    public QueryHit(PhysBody body, double[] point, double[] normal, double distance, double fraction, double depth, boolean inside) {
        this.body = body;
        this.point = point;
        this.normal = normal;
        this.distance = distance;
        this.fraction = fraction;
        this.depth = depth;
        this.inside = inside;
    }

    public boolean terrain() {
        return body == null || body.def.internal;
    }
}
