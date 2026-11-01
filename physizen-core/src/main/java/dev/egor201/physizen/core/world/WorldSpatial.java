package dev.egor201.physizen.core.world;

import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.model.QueryFilter;
import dev.egor201.physizen.core.model.Snapshot;
import dev.egor201.physizen.core.validate.Guard;

import java.util.ArrayList;
import java.util.List;

final class WorldSpatial {

    private final PhysWorld world;

    WorldSpatial(PhysWorld world) {
        this.world = world;
    }

    void explosion(double[] center, double radius, double impulse, double upward) {
        Guard.position(center, "center");
        Guard.number(radius, "radius");
        Guard.number(impulse, "impulse");
        Guard.number(upward, "upward");
        for (PhysBody pb : world.allBodies()) {
            if (!pb.ready || pb.def.internal || (pb.def.type != MotionKind.DYNAMIC && pb.character == null)) {
                continue;
            }
            Snapshot s = pb.snapshot;
            double dx = s.px - center[0];
            double dy = s.py - center[1];
            double dz = s.pz - center[2];
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > radius) {
                continue;
            }
            double falloff = 1.0 - dist / radius;
            if (dist < 1.0e-6) {
                dx = 0;
                dy = 1;
                dz = 0;
                dist = 1;
            }
            double k = impulse * falloff / dist;
            pb.addImpulse(new double[]{dx * k, dy * k + upward * impulse * falloff, dz * k});
        }
    }

    List<PhysBody> inSphere(double[] c, double r) {
        Guard.position(c, "c");
        Guard.number(r, "r");
        List<PhysBody> out = new ArrayList<>();
        for (PhysBody pb : world.allBodies()) {
            if (pb.def.internal) {
                continue;
            }
            Snapshot s = pb.snapshot;
            double nx = Math.max(s.minX, Math.min(c[0], s.maxX));
            double ny = Math.max(s.minY, Math.min(c[1], s.maxY));
            double nz = Math.max(s.minZ, Math.min(c[2], s.maxZ));
            double dx = nx - c[0];
            double dy = ny - c[1];
            double dz = nz - c[2];
            if (dx * dx + dy * dy + dz * dz <= r * r) {
                out.add(pb);
            }
        }
        return out;
    }

    List<PhysBody> inBox(double[] min, double[] max) {
        Guard.position(min, "min");
        Guard.position(max, "max");
        List<PhysBody> out = new ArrayList<>();
        for (PhysBody pb : world.allBodies()) {
            if (pb.def.internal) {
                continue;
            }
            Snapshot s = pb.snapshot;
            if (s.maxX >= min[0] && s.minX <= max[0] && s.maxY >= min[1] && s.minY <= max[1] && s.maxZ >= min[2] && s.minZ <= max[2]) {
                out.add(pb);
            }
        }
        return out;
    }

    RayHit rayCast(double[] from, double[] dir, double maxDistance) {
        QueryFilter filter = new QueryFilter();
        filter.sensors = true;
        List<QueryHit> hits = world.queries().rayCast(from, dir, maxDistance, filter, false);
        if (hits.isEmpty()) {
            return null;
        }
        QueryHit h = hits.get(0);
        return new RayHit(h.body, h.point, h.normal, h.distance, h.fraction);
    }
}
