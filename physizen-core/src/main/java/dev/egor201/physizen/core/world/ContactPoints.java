package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.ClosestHitCollideShapeCollector;
import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.CollideShapeResult;
import com.github.stephengold.joltjni.CollideShapeSettings;
import com.github.stephengold.joltjni.CollisionDispatch;
import com.github.stephengold.joltjni.Mat44;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SubShapeIdCreator;
import com.github.stephengold.joltjni.Vec3;

final class ContactPoints {

    private static final float SEPARATION = 0.02f;

    private ContactPoints() {
    }

    static double[] between(Body b1, Body b2) {
        try {
            RVec3 c1 = b1.getCenterOfMassPosition();
            RVec3 c2 = b2.getCenterOfMassPosition();
            double bx = c1.xx();
            double by = c1.yy();
            double bz = c1.zz();
            Quat q1 = b1.getRotation();
            Quat q2 = b2.getRotation();
            Mat44 t1 = Mat44.sRotationTranslation(q1, new Vec3(0f, 0f, 0f));
            Mat44 t2 = Mat44.sRotationTranslation(q2, new Vec3((float) (c2.xx() - bx), (float) (c2.yy() - by), (float) (c2.zz() - bz)));
            CollideShapeSettings settings = new CollideShapeSettings();
            settings.setMaxSeparationDistance(SEPARATION);
            ClosestHitCollideShapeCollector collector = new ClosestHitCollideShapeCollector();
            CollisionDispatch.sCollideShapeVsShape(b1.getShape(), b2.getShape(), new Vec3(1f, 1f, 1f), new Vec3(1f, 1f, 1f), t1, t2,
                    new SubShapeIdCreator(), new SubShapeIdCreator(), settings, collector);
            CollideShapeResult best = collector.hadHit() ? collector.getHit() : null;
            if (best == null) {
                return null;
            }
            Vec3 p1 = best.getContactPointOn1();
            Vec3 p2 = best.getContactPointOn2();
            return new double[]{bx + (p1.getX() + p2.getX()) * 0.5, by + (p1.getY() + p2.getY()) * 0.5, bz + (p1.getZ() + p2.getZ()) * 0.5};
        }
        catch (Throwable t) {
            return null;
        }
    }
}
