package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.CollisionGroup;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.BodyDef;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.shape.ShapeBuilder;

final class BodyFactory {

    private BodyFactory() {
    }

    static EMotionType motion(MotionKind kind) {
        return kind == MotionKind.STATIC ? EMotionType.Static : kind == MotionKind.KINEMATIC ? EMotionType.Kinematic : EMotionType.Dynamic;
    }

    static void instantiate(BodyInterface bodyInterface, PhysBody pb, CollisionGroup group) {
        BodyDef def = pb.def;
        ShapeRefC shape = ShapeBuilder.build(def.shape);
        try {
            BodyCreationSettings bcs = new BodyCreationSettings();
            bcs.setShape(shape);
            bcs.setPosition(new RVec3(def.position[0], def.position[1], def.position[2]));
            bcs.setRotation(new Quat((float) def.rotation[0], (float) def.rotation[1], (float) def.rotation[2], (float) def.rotation[3]));
            if (group != null) {
                bcs.setCollisionGroup(group);
            }
            bcs.setMotionType(motion(def.type));
            bcs.setAllowDynamicOrKinematic(!def.internal);
            bcs.setObjectLayer(WorldLayers.layerFor(def));
            bcs.setFriction((float) def.friction);
            bcs.setRestitution((float) def.restitution);
            bcs.setLinearDamping((float) def.linearDamping);
            bcs.setAngularDamping((float) def.angularDamping);
            bcs.setGravityFactor((float) def.gravityFactor);
            bcs.setMaxLinearVelocity((float) def.maxLinearVelocity);
            bcs.setMaxAngularVelocity((float) def.maxAngularVelocity);
            bcs.setIsSensor(def.sensor);
            bcs.setCollideKinematicVsNonDynamic(def.detectStatic);
            bcs.setAllowSleeping(def.allowSleeping);
            bcs.setLinearVelocity(new Vec3((float) def.linearVelocity[0], (float) def.linearVelocity[1], (float) def.linearVelocity[2]));
            bcs.setAngularVelocity(new Vec3((float) def.angularVelocity[0], (float) def.angularVelocity[1], (float) def.angularVelocity[2]));
            if (def.continuous) {
                bcs.setMotionQuality(EMotionQuality.LinearCast);
            }
            if (def.mass > 0 && def.type == MotionKind.DYNAMIC) {
                bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
                bcs.getMassPropertiesOverride().setMass((float) def.mass);
            }
            Body body = bodyInterface.createBody(bcs);
            if (body == null) {
                throw new PhysException("Jolt could not create the body (body limit reached?).");
            }
            pb.body = body;
            pb.joltId = body.getId();
        }
        finally {
            shape.close();
        }
    }

    static void activate(BodyInterface bodyInterface, PhysBody pb) {
        BodyDef def = pb.def;
        bodyInterface.addBody(pb.body, def.type == MotionKind.STATIC ? EActivation.DontActivate : EActivation.Activate);
        if (def.type == MotionKind.DYNAMIC && pb.body.getMotionProperties() != null) {
            float inverse = pb.body.getMotionProperties().getInverseMass();
            pb.cachedMass = inverse > 0 ? 1f / inverse : 0f;
        }
    }
}
