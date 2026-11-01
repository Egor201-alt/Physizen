package dev.egor201.physizen.core.constraint;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.ConeConstraintSettings;
import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.HingeConstraintSettings;
import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.SliderConstraintSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import dev.egor201.physizen.core.model.ConstraintDef;

public final class ConstraintFactory {

    private ConstraintFactory() {
    }

    public static TwoBodyConstraint create(ConstraintDef def, Body body1, Body body2) {
        double[] p1 = def.pointA;
        double[] p2 = def.pointB == null ? def.pointA : def.pointB;
        RVec3 point1 = new RVec3(p1[0], p1[1], p1[2]);
        RVec3 point2 = new RVec3(p2[0], p2[1], p2[2]);
        Vec3 axis = new Vec3((float) def.axis[0], (float) def.axis[1], (float) def.axis[2]);
        Vec3 normal = new Vec3((float) def.normalAxis[0], (float) def.normalAxis[1], (float) def.normalAxis[2]);
        switch (def.kind) {
            case FIXED: {
                FixedConstraintSettings s = new FixedConstraintSettings();
                s.setSpace(EConstraintSpace.WorldSpace);
                s.setAutoDetectPoint(true);
                return s.create(body1, body2);
            }
            case HINGE: {
                HingeConstraintSettings s = new HingeConstraintSettings();
                s.setSpace(EConstraintSpace.WorldSpace);
                s.setPoint1(point1);
                s.setPoint2(point2);
                s.setHingeAxis1(axis);
                s.setHingeAxis2(axis);
                s.setNormalAxis1(normal);
                s.setNormalAxis2(normal);
                if (def.limited) {
                    s.setLimitsMin((float) def.limitMin);
                    s.setLimitsMax((float) def.limitMax);
                }
                s.setMaxFrictionTorque((float) def.maxFriction);
                TwoBodyConstraint made = s.create(body1, body2);
                if (made instanceof HingeConstraint) {
                    ConstraintMotors.configureHinge((HingeConstraint) made, def);
                }
                return made;
            }
            case SLIDER: {
                SliderConstraintSettings s = new SliderConstraintSettings();
                s.setSpace(EConstraintSpace.WorldSpace);
                s.setAutoDetectPoint(true);
                s.setSliderAxis(axis);
                if (def.limited) {
                    s.setLimitsMin((float) def.limitMin);
                    s.setLimitsMax((float) def.limitMax);
                }
                s.setMaxFrictionForce((float) def.maxFriction);
                TwoBodyConstraint made = s.create(body1, body2);
                if (made instanceof SliderConstraint) {
                    ConstraintMotors.configureSlider((SliderConstraint) made, def);
                }
                return made;
            }
            case DISTANCE: {
                DistanceConstraintSettings s = new DistanceConstraintSettings();
                s.setSpace(EConstraintSpace.WorldSpace);
                s.setPoint1(point1);
                s.setPoint2(point2);
                s.setMinDistance(def.limited ? (float) def.limitMin : -1f);
                s.setMaxDistance(def.limited ? (float) def.limitMax : -1f);
                if (def.springFrequency > 0) {
                    SpringSettings spring = s.getLimitsSpringSettings();
                    spring.setMode(ESpringMode.FrequencyAndDamping);
                    spring.setFrequency((float) def.springFrequency);
                    spring.setDamping((float) def.springDamping);
                }
                return s.create(body1, body2);
            }
            case CONE: {
                ConeConstraintSettings s = new ConeConstraintSettings();
                s.setSpace(EConstraintSpace.WorldSpace);
                s.setPoint1(point1);
                s.setPoint2(point2);
                s.setTwistAxis1(axis);
                s.setTwistAxis2(axis);
                s.setHalfConeAngle((float) def.halfConeAngle);
                return s.create(body1, body2);
            }
            default: {
                PointConstraintSettings s = new PointConstraintSettings();
                s.setSpace(EConstraintSpace.WorldSpace);
                s.setPoint1(point1);
                s.setPoint2(point2);
                return s.create(body1, body2);
            }
        }
    }
}
