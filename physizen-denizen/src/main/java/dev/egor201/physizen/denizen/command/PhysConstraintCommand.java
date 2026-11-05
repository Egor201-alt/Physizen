package dev.egor201.physizen.denizen.command;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultNull;
import com.denizenscript.denizencore.scripts.commands.generator.ArgLinear;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.world.PhysWorld;

// <--[command]
// @Name physconstraint
// @Syntax physconstraint [create/remove/motor/set] (world:<world>) name:<name> (type:<type>) (body_a:<a>) (body_b:<b>) (point_a:<loc>) (point_b:<loc>) (axis:<vec>) (swing:<normal,plane degrees>) (twist:<min,max degrees>) (break_distance:<blocks>) ...
// @Required 2
// @Maximum 24
// @Short Creates, removes, or controls physical constraints and joint motors.
// @Group Physizen
// -->
public class PhysConstraintCommand extends AbstractCommand {

    public PhysConstraintCommand() {
        setName("physconstraint");
        setSyntax("physconstraint [create/remove/motor/set] (world:<world>) name:<name> (type:<type>) (body_a:<a>) (body_b:<b>) (point_a:<loc>) (point_b:<loc>) (axis:<vec>) (swing:<normal,plane degrees>) (twist:<min,max degrees>) ...");
        setRequiredArguments(2, 24);
        isProcedural = false;
        autoCompile();
        addRemappedPrefixes("name", "id");
    }

    public enum Action {
        REMOVE, MOTOR, SET, CREATE
    }

    public static void autoExecute(ScriptEntry scriptEntry,
            @ArgName("action") @ArgLinear Action action,
            @ArgName("name") @ArgPrefixed @ArgDefaultNull ElementTag nameArg,
            @ArgName("world") @ArgPrefixed @ArgDefaultNull ElementTag worldArg,
            @ArgName("state") @ArgPrefixed @ArgDefaultNull ElementTag stateArg,
            @ArgName("motor_state") @ArgPrefixed @ArgDefaultNull ElementTag motorStateArg,
            @ArgName("motor") @ArgPrefixed @ArgDefaultNull ElementTag motorArg,
            @ArgName("degrees") @ArgPrefixed @ArgDefaultNull ObjectTag degreesArg,
            @ArgName("target") @ArgPrefixed @ArgDefaultNull ObjectTag targetArg,
            @ArgName("motor_target") @ArgPrefixed @ArgDefaultNull ObjectTag motorTargetArg,
            @ArgName("velocity") @ArgPrefixed @ArgDefaultNull ObjectTag velocityArg,
            @ArgName("speed") @ArgPrefixed @ArgDefaultNull ObjectTag speedArg,
            @ArgName("motor_velocity") @ArgPrefixed @ArgDefaultNull ObjectTag motorVelocityArg,
            @ArgName("angle") @ArgPrefixed @ArgDefaultNull ObjectTag angleArg,
            @ArgName("position") @ArgPrefixed @ArgDefaultNull ObjectTag positionArg,
            @ArgName("motor_angle") @ArgPrefixed @ArgDefaultNull ObjectTag motorAngleArg,
            @ArgName("motor_position") @ArgPrefixed @ArgDefaultNull ObjectTag motorPositionArg,
            @ArgName("torque") @ArgPrefixed @ArgDefaultNull ObjectTag torqueArg,
            @ArgName("motor_torque") @ArgPrefixed @ArgDefaultNull ObjectTag motorTorqueArg,
            @ArgName("max_torque") @ArgPrefixed @ArgDefaultNull ObjectTag maxTorqueArg,
            @ArgName("force") @ArgPrefixed @ArgDefaultNull ObjectTag forceArg,
            @ArgName("motor_force") @ArgPrefixed @ArgDefaultNull ObjectTag motorForceArg,
            @ArgName("max_force") @ArgPrefixed @ArgDefaultNull ObjectTag maxForceArg,
            @ArgName("spring_freq") @ArgPrefixed @ArgDefaultNull ObjectTag springFreqArg,
            @ArgName("motor_spring_freq") @ArgPrefixed @ArgDefaultNull ObjectTag motorSpringFreqArg,
            @ArgName("spring_damp") @ArgPrefixed @ArgDefaultNull ObjectTag springDampArg,
            @ArgName("motor_spring_damp") @ArgPrefixed @ArgDefaultNull ObjectTag motorSpringDampArg,
            @ArgName("type") @ArgPrefixed @ArgDefaultNull ConstraintDef.Kind typeArg,
            @ArgName("body_a") @ArgPrefixed @ArgDefaultNull ElementTag bodyAArg,
            @ArgName("body_b") @ArgPrefixed @ArgDefaultNull ElementTag bodyBArg,
            @ArgName("point_a") @ArgPrefixed @ArgDefaultNull ObjectTag pointAArg,
            @ArgName("point_b") @ArgPrefixed @ArgDefaultNull ObjectTag pointBArg,
            @ArgName("axis") @ArgPrefixed @ArgDefaultNull ObjectTag axisArg,
            @ArgName("break_distance") @ArgPrefixed @ArgDefaultNull ObjectTag breakDistanceArg,
            @ArgName("normal") @ArgPrefixed @ArgDefaultNull ObjectTag normalArg,
            @ArgName("limits") @ArgPrefixed @ArgDefaultNull ObjectTag limitsArg,
            @ArgName("cone_angle") @ArgPrefixed @ArgDefaultNull ObjectTag coneAngleArg,
            @ArgName("swing") @ArgPrefixed @ArgDefaultNull ObjectTag swingArg,
            @ArgName("twist") @ArgPrefixed @ArgDefaultNull ObjectTag twistArg,
            @ArgName("friction") @ArgPrefixed @ArgDefaultNull ObjectTag frictionArg) {
        ElementTag nameTag = nameArg;
        if (action == null || nameTag == null) {
            Debug.echoError(scriptEntry, "physconstraint needs action and name:<name>.");
            return;
        }

        String worldId = (worldArg != null) ? worldArg.asString() : "default";

        try {
            PhysWorld w = Args.world(worldId);

            if (action == Action.REMOVE) {
                w.removeConstraint(nameTag.asString());
                return;
            }

            if (action == Action.MOTOR || action == Action.SET) {
                String cName = nameTag.asString();
                ConstraintDef existing = w.constraintDef(cName);
                if (existing == null) {
                    throw new PhysException("Unknown constraint '" + cName + "'.");
                }
                if (existing.kind != ConstraintDef.Kind.HINGE && existing.kind != ConstraintDef.Kind.SLIDER) {
                    throw new PhysException("Constraint '" + cName + "' (" + existing.kind + ") does not support motors. Only hinge and slider constraints support motors.");
                }

                ConstraintDef.MotorState newState = null;
                if ((stateArg != null) || (motorStateArg != null) || (motorArg != null)) {
                    String stateStr = CoreUtilities.toLowerCase((stateArg != null ? stateArg : motorStateArg != null ? motorStateArg : motorArg).asString());
                    if (stateStr.equals("velocity")) newState = ConstraintDef.MotorState.VELOCITY;
                    else if (stateStr.equals("position")) newState = ConstraintDef.MotorState.POSITION;
                    else if (stateStr.equals("off")) newState = ConstraintDef.MotorState.OFF;
                    else throw new PhysException("Unknown motor state '" + stateStr + "', expected off, velocity, or position.");
                }

                boolean degrees = Args.bool(degreesArg, false);
                Double target = null;
                if ((targetArg != null) || (motorTargetArg != null)) {
                    target = Args.num((targetArg != null ? targetArg : motorTargetArg), 0);
                    if (existing.kind == ConstraintDef.Kind.HINGE && degrees) {
                        target = Math.toRadians(target);
                    }
                }
                else if ((velocityArg != null) || (speedArg != null) || (motorVelocityArg != null)) {
                    target = Args.num((velocityArg != null ? velocityArg : speedArg != null ? speedArg : motorVelocityArg), 0);
                    if (existing.kind == ConstraintDef.Kind.HINGE && degrees) {
                        target = Math.toRadians(target);
                    }
                    if (newState == null) newState = ConstraintDef.MotorState.VELOCITY;
                }
                else if ((angleArg != null) || (positionArg != null) || (motorAngleArg != null) || (motorPositionArg != null)) {
                    target = Args.num((angleArg != null ? angleArg : positionArg != null ? positionArg : motorAngleArg != null ? motorAngleArg : motorPositionArg), 0);
                    if (existing.kind == ConstraintDef.Kind.HINGE && degrees) {
                        target = Math.toRadians(target);
                    }
                    if (newState == null) newState = ConstraintDef.MotorState.POSITION;
                }

                Double limit = null;
                if ((torqueArg != null) || (motorTorqueArg != null) || (maxTorqueArg != null)) {
                    limit = Args.num((torqueArg != null ? torqueArg : motorTorqueArg != null ? motorTorqueArg : maxTorqueArg), 0);
                }
                else if ((forceArg != null) || (motorForceArg != null) || (maxForceArg != null)) {
                    limit = Args.num((forceArg != null ? forceArg : motorForceArg != null ? motorForceArg : maxForceArg), 0);
                }

                Double springFreq = ((springFreqArg != null) || (motorSpringFreqArg != null))
                        ? Args.num((springFreqArg != null ? springFreqArg : motorSpringFreqArg), 2.0)
                        : null;
                Double springDamp = ((springDampArg != null) || (motorSpringDampArg != null))
                        ? Args.num((springDampArg != null ? springDampArg : motorSpringDampArg), 1.0)
                        : null;

                w.setConstraintMotor(cName, newState, target, limit, springFreq, springDamp);
                return;
            }

            if (action == Action.CREATE) {
                ConstraintDef def = new ConstraintDef();
                def.name = nameTag.asString();
                if (typeArg != null) {
                    def.kind = typeArg;
                }
                if (bodyAArg != null) def.bodyA = bodyAArg.asString();
                if (bodyBArg != null) def.bodyB = bodyBArg.asString();

                if (pointAArg != null) def.pointA = Args.vec(pointAArg, scriptEntry.context);
                if (pointBArg != null) def.pointB = Args.vec(pointBArg, scriptEntry.context);
                if (axisArg != null) def.axis = Args.vec(axisArg, scriptEntry.context);
                if (breakDistanceArg != null) def.breakDistance = Math.max(0, Args.num(breakDistanceArg, 0));
                if (normalArg != null) def.normalAxis = Args.vec(normalArg, scriptEntry.context);

                boolean degrees = Args.bool(degreesArg, false);
                if (limitsArg != null) {
                    double[] lim = Args.pair(limitsArg);
                    double lo = Math.min(lim[0], lim[1]);
                    double hi = Math.max(lim[0], lim[1]);
                    if (def.kind == ConstraintDef.Kind.HINGE) {
                        if (degrees) {
                            lo = Math.toRadians(lo);
                            hi = Math.toRadians(hi);
                        }
                        lo = Math.max(-Math.PI, Math.min(0, lo));
                        hi = Math.min(Math.PI, Math.max(0, hi));
                    }
                    def.limitMin = lo;
                    def.limitMax = hi;
                    def.limited = true;
                }
                if (coneAngleArg != null) {
                    double angle = Math.toRadians(Args.num(coneAngleArg, 45.0));
                    def.halfConeAngle = Math.max(0.01, Math.min(Math.PI, angle));
                }
                if (swingArg != null) {
                    double[] sw = Args.pair(swingArg);
                    def.swingNormalHalfCone = Math.max(0.0, Math.min(Math.PI, Math.toRadians(sw[0])));
                    def.swingPlaneHalfCone = Math.max(0.0, Math.min(Math.PI, Math.toRadians(sw[1])));
                }
                if (twistArg != null) {
                    double[] tw = Args.pair(twistArg);
                    def.twistMin = Math.max(-Math.PI, Math.min(Math.PI, Math.toRadians(Math.min(tw[0], tw[1]))));
                    def.twistMax = Math.max(-Math.PI, Math.min(Math.PI, Math.toRadians(Math.max(tw[0], tw[1]))));
                }
                def.maxFriction = Args.num(frictionArg, 0);
                def.springFrequency = Args.num(springFreqArg, 0);
                def.springDamping = Args.num(springDampArg, 0.5);

                if ((motorStateArg != null) || (motorArg != null)) {
                    String stateStr = CoreUtilities.toLowerCase((motorStateArg != null ? motorStateArg : motorArg).asString());
                    if (stateStr.equals("velocity")) def.motorState = ConstraintDef.MotorState.VELOCITY;
                    else if (stateStr.equals("position")) def.motorState = ConstraintDef.MotorState.POSITION;
                    else if (stateStr.equals("off")) def.motorState = ConstraintDef.MotorState.OFF;
                    else throw new PhysException("Unknown motor state '" + stateStr + "'.");
                }

                if (motorTargetArg != null) {
                    def.motorTarget = Args.num(motorTargetArg, 0);
                    if (def.kind == ConstraintDef.Kind.HINGE && degrees) {
                        def.motorTarget = Math.toRadians(def.motorTarget);
                    }
                }
                else if (motorVelocityArg != null) {
                    def.motorTarget = Args.num(motorVelocityArg, 0);
                    if (def.kind == ConstraintDef.Kind.HINGE && degrees) {
                        def.motorTarget = Math.toRadians(def.motorTarget);
                    }
                    if (def.motorState == ConstraintDef.MotorState.OFF) def.motorState = ConstraintDef.MotorState.VELOCITY;
                }
                else if ((motorAngleArg != null) || (motorPositionArg != null)) {
                    def.motorTarget = Args.num((motorAngleArg != null ? motorAngleArg : motorPositionArg), 0);
                    if (def.kind == ConstraintDef.Kind.HINGE && degrees) {
                        def.motorTarget = Math.toRadians(def.motorTarget);
                    }
                    if (def.motorState == ConstraintDef.MotorState.OFF) def.motorState = ConstraintDef.MotorState.POSITION;
                }

                if (motorTorqueArg != null) {
                    def.motorMaxTorque = Args.num(motorTorqueArg, 0);
                }
                if (motorForceArg != null) {
                    def.motorMaxForce = Args.num(motorForceArg, 0);
                }
                if (motorSpringFreqArg != null) {
                    def.motorSpringFrequency = Args.num(motorSpringFreqArg, 2.0);
                }
                if (motorSpringDampArg != null) {
                    def.motorSpringDamping = Args.num(motorSpringDampArg, 1.0);
                }

                w.addConstraint(def);
            }
        }
        catch (PhysException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }

}