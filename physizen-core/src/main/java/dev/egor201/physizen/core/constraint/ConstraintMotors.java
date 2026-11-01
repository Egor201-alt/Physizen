package dev.egor201.physizen.core.constraint;

import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.SliderConstraint;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import dev.egor201.physizen.core.model.ConstraintDef;

public final class ConstraintMotors {

    private ConstraintMotors() {
    }

    public static void configureHinge(HingeConstraint hinge, ConstraintDef def) {
        if (def.motorMaxTorque > 0) {
            hinge.getMotorSettings().setTorqueLimit((float) def.motorMaxTorque);
        }
        applySpring(hinge.getMotorSettings(), def.motorSpringFrequency, def.motorSpringDamping);
        driveHinge(hinge, def);
    }

    public static void configureSlider(SliderConstraint slider, ConstraintDef def) {
        if (def.motorMaxForce > 0) {
            slider.getMotorSettings().setForceLimit((float) def.motorMaxForce);
        }
        applySpring(slider.getMotorSettings(), def.motorSpringFrequency, def.motorSpringDamping);
        driveSlider(slider, def);
    }

    public static void update(Object constraint, ConstraintDef def, ConstraintDef.MotorState state, Double target, Double maxForceOrTorque, Double springFreq, Double springDamp) {
        if (def.kind == ConstraintDef.Kind.HINGE && constraint instanceof HingeConstraint) {
            HingeConstraint hinge = (HingeConstraint) constraint;
            MotorSettings ms = hinge.getMotorSettings();
            if (maxForceOrTorque != null && maxForceOrTorque > 0) {
                ms.setTorqueLimit(maxForceOrTorque.floatValue());
                def.motorMaxTorque = maxForceOrTorque;
            }
            merge(ms, def, state, target, springFreq, springDamp);
            driveHinge(hinge, def);
        }
        else if (def.kind == ConstraintDef.Kind.SLIDER && constraint instanceof SliderConstraint) {
            SliderConstraint slider = (SliderConstraint) constraint;
            MotorSettings ms = slider.getMotorSettings();
            if (maxForceOrTorque != null && maxForceOrTorque > 0) {
                ms.setForceLimit(maxForceOrTorque.floatValue());
                def.motorMaxForce = maxForceOrTorque;
            }
            merge(ms, def, state, target, springFreq, springDamp);
            driveSlider(slider, def);
        }
    }

    private static void merge(MotorSettings ms, ConstraintDef def, ConstraintDef.MotorState state, Double target, Double springFreq, Double springDamp) {
        if (springFreq != null && springFreq > 0) {
            def.motorSpringFrequency = springFreq;
            if (springDamp != null) {
                def.motorSpringDamping = springDamp;
            }
            applySpring(ms, def.motorSpringFrequency, def.motorSpringDamping);
        }
        if (state != null) {
            def.motorState = state;
        }
        if (target != null) {
            def.motorTarget = target;
        }
    }

    private static void applySpring(MotorSettings ms, double frequency, double damping) {
        if (frequency > 0) {
            SpringSettings ss = ms.getSpringSettings();
            ss.setMode(ESpringMode.FrequencyAndDamping);
            ss.setFrequency((float) frequency);
            ss.setDamping((float) damping);
        }
    }

    private static void driveHinge(HingeConstraint hinge, ConstraintDef def) {
        if (def.motorState == ConstraintDef.MotorState.VELOCITY) {
            hinge.setMotorState(EMotorState.Velocity);
            hinge.setTargetAngularVelocity((float) def.motorTarget);
        }
        else if (def.motorState == ConstraintDef.MotorState.POSITION) {
            hinge.setMotorState(EMotorState.Position);
            hinge.setTargetAngle((float) def.motorTarget);
        }
        else {
            hinge.setMotorState(EMotorState.Off);
        }
    }

    private static void driveSlider(SliderConstraint slider, ConstraintDef def) {
        if (def.motorState == ConstraintDef.MotorState.VELOCITY) {
            slider.setMotorState(EMotorState.Velocity);
            slider.setTargetVelocity((float) def.motorTarget);
        }
        else if (def.motorState == ConstraintDef.MotorState.POSITION) {
            slider.setMotorState(EMotorState.Position);
            slider.setTargetPosition((float) def.motorTarget);
        }
        else {
            slider.setMotorState(EMotorState.Off);
        }
    }
}
