package dev.egor201.physizen.core.world;

public final class CharacterState {

    public enum Ground {
        ON_GROUND,
        ON_STEEP_GROUND,
        NOT_SUPPORTED,
        IN_AIR
    }

    public final double px;
    public final double py;
    public final double pz;
    public final double vx;
    public final double vy;
    public final double vz;
    public final Ground ground;
    public final double[] groundNormal;
    public final double[] groundVelocity;
    public final PhysBody groundBody;
    public final boolean supported;
    public final long step;

    CharacterState(double[] pos, double[] vel, Ground ground, double[] groundNormal, double[] groundVelocity, PhysBody groundBody, boolean supported, long step) {
        this.px = pos[0];
        this.py = pos[1];
        this.pz = pos[2];
        this.vx = vel[0];
        this.vy = vel[1];
        this.vz = vel[2];
        this.ground = ground;
        this.groundNormal = groundNormal;
        this.groundVelocity = groundVelocity;
        this.groundBody = groundBody;
        this.supported = supported;
        this.step = step;
    }

    public boolean onGround() {
        return ground == Ground.ON_GROUND;
    }

    public double speed() {
        return Math.sqrt(vx * vx + vy * vy + vz * vz);
    }
}
