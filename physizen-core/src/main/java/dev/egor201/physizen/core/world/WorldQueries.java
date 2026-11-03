package dev.egor201.physizen.core.world;

import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.QueryFilter;
import dev.egor201.physizen.core.model.ShapeSpec;
import dev.egor201.physizen.core.shape.ShapeBuilder;

import com.github.stephengold.joltjni.AllHitCastRayCollector;
import com.github.stephengold.joltjni.AllHitCastShapeCollector;
import com.github.stephengold.joltjni.AllHitCollideShapeCollector;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.CollideShapeResult;
import com.github.stephengold.joltjni.CollideShapeSettings;
import com.github.stephengold.joltjni.IgnoreMultipleBodiesFilter;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RRayCast;
import com.github.stephengold.joltjni.RShapeCast;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.RayCastResult;
import com.github.stephengold.joltjni.RayCastSettings;
import com.github.stephengold.joltjni.ShapeCastResult;
import com.github.stephengold.joltjni.ShapeCastSettings;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorldQueries {

    private final PhysWorld world;

    WorldQueries(PhysWorld world) {
        this.world = world;
    }

    private boolean accept(PhysBody pb, QueryFilter f) {
        if (pb == null || pb.def.internal) {
            return f.terrain;
        }
        if (!f.sensors && pb.def.sensor) {
            return false;
        }
        if (!f.groups.isEmpty() && !f.groups.contains(pb.def.group)) {
            return false;
        }
        return !f.ignore.contains(pb.name);
    }

    private IgnoreMultipleBodiesFilter bodyFilter(QueryFilter f) {
        IgnoreMultipleBodiesFilter filter = new IgnoreMultipleBodiesFilter();
        for (String name : f.ignore) {
            PhysBody pb = world.body(name);
            if (pb != null && pb.ready && pb.joltId >= 0) {
                filter.ignoreBody(pb.joltId);
            }
        }
        return filter;
    }

    private static double[] unit(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1.0e-9) {
            return null;
        }
        return new double[]{v[0] / len, v[1] / len, v[2] / len};
    }

    private static double[] towardCaster(Vec3 axis, double[] fallback) {
        double[] n = unit(new double[]{axis.getX(), axis.getY(), axis.getZ()});
        if (n == null) {
            return fallback;
        }
        return new double[]{-n[0], -n[1], -n[2]};
    }

    private double[] surfaceNormal(PhysBody pb, int subShapeId, double[] p, double[] fallback) {
        if (pb != null && pb.body != null) {
            try {
                Vec3 n = pb.body.getWorldSpaceSurfaceNormal(subShapeId, p[0], p[1], p[2]);
                double[] u = unit(new double[]{n.getX(), n.getY(), n.getZ()});
                if (u != null) {
                    return u;
                }
            }
            catch (RuntimeException ignored) {
            }
        }
        return fallback;
    }

    private static void requireCastable(ShapeSpec spec) {
        if (spec.kind == ShapeSpec.Kind.MESH) {
            throw new PhysException("Mesh shapes cannot be used for queries. Use box, sphere, capsule, cylinder, cone, hull or compound.");
        }
        for (ShapeSpec.Child c : spec.children) {
            requireCastable(c.shape);
        }
    }

    private static RMat44 transform(double[] pos, double[] rot) {
        double[] q = rot == null ? new double[]{0, 0, 0, 1} : rot;
        return RMat44.sRotationTranslation(new Quat((float) q[0], (float) q[1], (float) q[2], (float) q[3]), new RVec3(pos[0], pos[1], pos[2]));
    }

    public List<QueryHit> rayCast(double[] from, double[] dir, double max, QueryFilter f, boolean all) {
        double[] u = unit(dir);
        if (u == null || world.destroyed() || max <= 0) {
            return Collections.emptyList();
        }
        double[] back = {-u[0], -u[1], -u[2]};
        List<QueryHit> out = new ArrayList<>();
        world.queryLock.readLock().lock();
        try {
            if (world.destroyed()) {
                return new ArrayList<>();
            }
            RRayCast ray = new RRayCast(new RVec3(from[0], from[1], from[2]), new Vec3((float) (u[0] * max), (float) (u[1] * max), (float) (u[2] * max)));
            AllHitCastRayCollector collector = new AllHitCastRayCollector();
            world.system.getNarrowPhaseQuery().castRay(ray, new RayCastSettings(), collector, new BroadPhaseLayerFilter(), new ObjectLayerFilter(), bodyFilter(f));
            collector.sort();
            for (RayCastResult r : collector.getHits()) {
                PhysBody pb = world.byJoltId.get(r.getBodyId());
                if (!accept(pb, f)) {
                    continue;
                }
                double fraction = r.getFraction();
                double[] point = {from[0] + u[0] * max * fraction, from[1] + u[1] * max * fraction, from[2] + u[2] * max * fraction};
                double[] normal = surfaceNormal(pb, r.getSubShapeId2(), point, back);
                out.add(new QueryHit(pb, point, normal, max * fraction, fraction, 0, fraction <= 1.0e-9));
                if (!all || out.size() >= f.limit) {
                    break;
                }
            }
        }
        finally {
            world.queryLock.readLock().unlock();
        }
        return out;
    }

    public List<QueryHit> shapeCast(ShapeSpec spec, double[] from, double[] rot, double[] dir, double max, QueryFilter f, boolean all) {
        requireCastable(spec);
        double[] u = unit(dir);
        if (u == null || world.destroyed() || max <= 0) {
            return Collections.emptyList();
        }
        double[] back = {-u[0], -u[1], -u[2]};
        List<QueryHit> out = new ArrayList<>();
        ShapeRefC shape = ShapeBuilder.build(spec);
        world.queryLock.readLock().lock();
        try {
            if (world.destroyed()) {
                return new ArrayList<>();
            }
            RShapeCast cast = new RShapeCast(shape, new Vec3(1f, 1f, 1f), transform(from, rot), new Vec3((float) (u[0] * max), (float) (u[1] * max), (float) (u[2] * max)));
            AllHitCastShapeCollector collector = new AllHitCastShapeCollector();
            world.system.getNarrowPhaseQuery().castShape(cast, new ShapeCastSettings(), new RVec3(from[0], from[1], from[2]), collector, new BroadPhaseLayerFilter(), new ObjectLayerFilter(), bodyFilter(f));
            collector.sort();
            for (ShapeCastResult r : collector.getHits()) {
                PhysBody pb = world.byJoltId.get(r.getBodyId2());
                if (!accept(pb, f)) {
                    continue;
                }
                double fraction = r.getFraction();
                Vec3 contact = r.getContactPointOn2();
                double[] point = {from[0] + contact.getX(), from[1] + contact.getY(), from[2] + contact.getZ()};
                double[] normal = towardCaster(r.getPenetrationAxis(), back);
                double depth = r.getPenetrationDepth();
                out.add(new QueryHit(pb, point, normal, max * fraction, fraction, depth, fraction <= 1.0e-9 && depth > 1.0e-6));
                if (!all || out.size() >= f.limit) {
                    break;
                }
            }
        }
        finally {
            world.queryLock.readLock().unlock();
            shape.close();
        }
        return out;
    }

    public List<QueryHit> overlap(ShapeSpec spec, double[] pos, double[] rot, QueryFilter f) {
        requireCastable(spec);
        if (world.destroyed()) {
            return Collections.emptyList();
        }
        ShapeRefC shape = ShapeBuilder.build(spec);
        Map<Integer, QueryHit> best = new LinkedHashMap<>();
        world.queryLock.readLock().lock();
        try {
            if (world.destroyed()) {
                return new ArrayList<>();
            }
            AllHitCollideShapeCollector collector = new AllHitCollideShapeCollector();
            world.system.getNarrowPhaseQuery().collideShape(shape, new Vec3(1f, 1f, 1f), transform(pos, rot), new CollideShapeSettings(), new RVec3(pos[0], pos[1], pos[2]), collector, new BroadPhaseLayerFilter(), new ObjectLayerFilter(), bodyFilter(f));
            for (CollideShapeResult r : collector.getHits()) {
                int id = r.getBodyId2();
                PhysBody pb = world.byJoltId.get(id);
                if (!accept(pb, f)) {
                    continue;
                }
                double depth = r.getPenetrationDepth();
                QueryHit previous = best.get(id);
                if (previous != null && previous.depth >= depth) {
                    continue;
                }
                Vec3 contact = r.getContactPointOn2();
                double[] point = {pos[0] + contact.getX(), pos[1] + contact.getY(), pos[2] + contact.getZ()};
                double[] normal = towardCaster(r.getPenetrationAxis(), new double[]{0, 1, 0});
                best.put(id, new QueryHit(pb, point, normal, 0, 0, depth, true));
            }
        }
        finally {
            world.queryLock.readLock().unlock();
            shape.close();
        }
        List<QueryHit> out = new ArrayList<>(best.values());
        out.sort((a, b) -> Double.compare(b.depth, a.depth));
        if (out.size() > f.limit) {
            return new ArrayList<>(out.subList(0, f.limit));
        }
        return out;
    }
}