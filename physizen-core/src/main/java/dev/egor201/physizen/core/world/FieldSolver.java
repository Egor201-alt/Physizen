package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Vec3;
import dev.egor201.physizen.core.model.FieldDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.model.Snapshot;

import java.util.Collection;

final class FieldSolver {

    private FieldSolver() {
    }

    static void apply(Collection<FieldDef> fields, Collection<PhysBody> bodies, BodyInterface bodyInterface, double gravityY) {
        if (fields.isEmpty()) {
            return;
        }
        for (FieldDef f : fields) {
            for (PhysBody pb : bodies) {
                if (!pb.ready || pb.def.type != MotionKind.DYNAMIC || pb.def.internal || pb.character != null) {
                    continue;
                }
                if (f.group >= 0 && pb.def.group != f.group) {
                    continue;
                }
                Snapshot s = pb.snapshot;
                if (!s.active) {
                    continue;
                }
                double[] force = force(f, pb, s, gravityY);
                if (force != null) {
                    bodyInterface.addForce(pb.joltId, new Vec3((float) force[0], (float) force[1], (float) force[2]));
                }
            }
        }
    }

    private static double[] force(FieldDef f, PhysBody pb, Snapshot s, double gravityY) {
        double mass = f.massIndependent ? Math.max(pb.cachedMass, 0.0001) : 1.0;
        double dx = f.center[0] - s.px;
        double dy = f.center[1] - s.py;
        double dz = f.center[2] - s.pz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (f.kind != FieldDef.Kind.GRAVITY && f.kind != FieldDef.Kind.BUOYANCY && f.radius > 0 && dist > f.radius) {
            return null;
        }
        double falloff = f.radius > 0 ? Math.max(0, 1.0 - dist / f.radius) : 1.0;
        switch (f.kind) {
            case RADIAL: {
                if (dist < 1.0e-6) {
                    return null;
                }
                double k = f.strength * falloff * mass / dist;
                return new double[]{dx * k, dy * k, dz * k};
            }
            case GRAVITY:
                return new double[]{f.direction[0] * f.strength * mass, f.direction[1] * f.strength * mass, f.direction[2] * f.strength * mass};
            case WIND: {
                double wx = f.direction[0] * f.strength;
                double wy = f.direction[1] * f.strength;
                double wz = f.direction[2] * f.strength;
                return new double[]{
                        (wx - s.vx) * f.drag * mass * falloff,
                        (wy - s.vy) * f.drag * mass * falloff,
                        (wz - s.vz) * f.drag * mass * falloff};
            }
            case VORTEX: {
                double rx = s.px - f.center[0];
                double rz = s.pz - f.center[2];
                double rl = Math.sqrt(rx * rx + rz * rz);
                if (rl < 1.0e-6) {
                    return null;
                }
                double k = f.strength * falloff * mass / rl;
                double fx = -rz * k + -rx / rl * f.drag * falloff * mass;
                double fz = rx * k + -rz / rl * f.drag * falloff * mass;
                double fy = f.direction[1] * f.strength * falloff * mass * 0.25;
                return new double[]{fx, fy, fz};
            }
            case DRAG: {
                double speed = s.speed();
                return new double[]{
                        -s.vx * speed * f.strength * falloff * mass,
                        -s.vy * speed * f.strength * falloff * mass,
                        -s.vz * speed * f.strength * falloff * mass};
            }
            case BUOYANCY: {
                double height = Math.max(s.maxY - s.minY, 0.0001);
                double submerged = Math.max(0, Math.min(1, (f.surfaceY - s.minY) / height));
                if (submerged <= 0) {
                    return null;
                }
                double volume = Math.max((s.maxX - s.minX) * height * (s.maxZ - s.minZ) * 0.6, 0.0001);
                double g = Math.abs(gravityY);
                return new double[]{
                        -s.vx * f.drag * submerged * mass,
                        f.density * g * volume * submerged * f.strength - s.vy * f.drag * submerged * mass,
                        -s.vz * f.drag * submerged * mass};
            }
            default:
                return null;
        }
    }
}
