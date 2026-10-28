package dev.egor201.physizen.core;

public final class Snapshot {

    public final double px;
    public final double py;
    public final double pz;
    public final float qx;
    public final float qy;
    public final float qz;
    public final float qw;
    public final float vx;
    public final float vy;
    public final float vz;
    public final float wx;
    public final float wy;
    public final float wz;
    public final float minX;
    public final float minY;
    public final float minZ;
    public final float maxX;
    public final float maxY;
    public final float maxZ;
    public final boolean active;
    public final long step;

    public Snapshot(double px, double py, double pz, float qx, float qy, float qz, float qw,
                    float vx, float vy, float vz, float wx, float wy, float wz,
                    float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                    boolean active, long step) {
        this.px = px;
        this.py = py;
        this.pz = pz;
        this.qx = qx;
        this.qy = qy;
        this.qz = qz;
        this.qw = qw;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.wx = wx;
        this.wy = wy;
        this.wz = wz;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.active = active;
        this.step = step;
    }

    public static Snapshot initial(double[] pos, double[] rot) {
        return new Snapshot(pos[0], pos[1], pos[2], (float) rot[0], (float) rot[1], (float) rot[2], (float) rot[3],
                0, 0, 0, 0, 0, 0,
                (float) pos[0], (float) pos[1], (float) pos[2], (float) pos[0], (float) pos[1], (float) pos[2],
                true, 0);
    }

    public double speed() {
        return Math.sqrt((double) vx * vx + (double) vy * vy + (double) vz * vz);
    }

    public double angularSpeed() {
        return Math.sqrt((double) wx * wx + (double) wy * wy + (double) wz * wz);
    }
}
