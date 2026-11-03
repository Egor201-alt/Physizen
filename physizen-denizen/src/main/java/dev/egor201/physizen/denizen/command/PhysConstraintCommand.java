package dev.egor201.physizen.denizen.command;

import dev.egor201.physizen.denizen.args.Args;
import dev.egor201.physizen.denizen.args.ShapeParser;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
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
// @Syntax physconstraint [create/remove/motor/set] (world:<world>) name:<name> (type:<type>) (body_a:<a>) (body_b:<b>) (point_a:<loc>) (point_b:<loc>) (axis:<vec>) (swing:<normal,plane degrees>) (twist:<min,max degrees>) ...
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
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action")) {
                scriptEntry.addObject("action", arg.asElement());
            }
            else if (!scriptEntry.hasObject("name") && arg.matchesPrefix("name", "id")) {
                scriptEntry.addObject("name", arg.asElement());
            }
            else if (!scriptEntry.hasObject("world") && arg.matchesPrefix("world")) {
                scriptEntry.addObject("world", arg.asElement());
            }
            else {
                scriptEntry.addObject(arg.prefix != null ? arg.prefix : arg.getValue(), arg.object);
            }
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        ElementTag action = scriptEntry.getElement("action");
        ElementTag nameTag = scriptEntry.getElement("name");
        if (action == null || nameTag == null) {
            Debug.echoError(scriptEntry, "physconstraint needs action and name:<name>.");
            return;
        }

        String act = action.asString().toLowerCase();
        String worldId = scriptEntry.hasObject("world") ? scriptEntry.getElement("world").asString() : "default";

        try {
            PhysWorld w = Args.world(worldId);

            if (act.equals("remove")) {
                w.removeConstraint(nameTag.asString());
                return;
            }

            if (act.equals("motor") || act.equals("set")) {
                String cName = nameTag.asString();
                ConstraintDef existing = w.constraintDef(cName);
                if (existing == null) {
                    throw new PhysException("Unknown constraint '" + cName + "'.");
                }
                if (existing.kind != ConstraintDef.Kind.HINGE && existing.kind != ConstraintDef.Kind.SLIDER) {
                    throw new PhysException("Constraint '" + cName + "' (" + existing.kind + ") does not support motors. Only hinge and slider constraints support motors.");
                }

                ConstraintDef.MotorState newState = null;
                if (scriptEntry.hasObject("state") || scriptEntry.hasObject("motor_state") || scriptEntry.hasObject("motor")) {
                    String stateStr = CoreUtilities.toLowerCase(scriptEntry.getElement(scriptEntry.hasObject("state") ? "state" : scriptEntry.hasObject("motor_state") ? "motor_state" : "motor").asString());
                    if (stateStr.equals("velocity")) newState = ConstraintDef.MotorState.VELOCITY;
                    else if (stateStr.equals("position")) newState = ConstraintDef.MotorState.POSITION;
                    else if (stateStr.equals("off")) newState = ConstraintDef.MotorState.OFF;
                    else throw new PhysException("Unknown motor state '" + stateStr + "', expected off, velocity, or position.");
                }

                boolean degrees = Args.bool(scriptEntry.getObjectTag("degrees"), false);
                Double target = null;
                if (scriptEntry.hasObject("target") || scriptEntry.hasObject("motor_target")) {
                    target = Args.num(scriptEntry.getObjectTag(scriptEntry.hasObject("target") ? "target" : "motor_target"), 0);
                    if (existing.kind == ConstraintDef.Kind.HINGE && degrees) {
                        target = Math.toRadians(target);
                    }
                }
                else if (scriptEntry.hasObject("velocity") || scriptEntry.hasObject("speed") || scriptEntry.hasObject("motor_velocity")) {
                    target = Args.num(scriptEntry.getObjectTag(scriptEntry.hasObject("velocity") ? "velocity" : scriptEntry.hasObject("speed") ? "speed" : "motor_velocity"), 0);
                    if (existing.kind == ConstraintDef.Kind.HINGE && degrees) {
                        target = Math.toRadians(target);
                    }
                    if (newState == null) newState = ConstraintDef.MotorState.VELOCITY;
                }
                else if (scriptEntry.hasObject("angle") || scriptEntry.hasObject("position") || scriptEntry.hasObject("motor_angle") || scriptEntry.hasObject("motor_position")) {
                    target = Args.num(scriptEntry.getObjectTag(scriptEntry.hasObject("angle") ? "angle" : scriptEntry.hasObject("position") ? "position" : scriptEntry.hasObject("motor_angle") ? "motor_angle" : "motor_position"), 0);
                    if (existing.kind == ConstraintDef.Kind.HINGE && degrees) {
                        target = Math.toRadians(target);
                    }
                    if (newState == null) newState = ConstraintDef.MotorState.POSITION;
                }

                Double limit = null;
                if (scriptEntry.hasObject("torque") || scriptEntry.hasObject("motor_torque") || scriptEntry.hasObject("max_torque")) {
                    limit = Args.num(scriptEntry.getObjectTag(scriptEntry.hasObject("torque") ? "torque" : scriptEntry.hasObject("motor_torque") ? "motor_torque" : "max_torque"), 0);
                }
                else if (scriptEntry.hasObject("force") || scriptEntry.hasObject("motor_force") || scriptEntry.hasObject("max_force")) {
                    limit = Args.num(scriptEntry.getObjectTag(scriptEntry.hasObject("force") ? "force" : scriptEntry.hasObject("motor_force") ? "motor_force" : "max_force"), 0);
                }

                Double springFreq = (scriptEntry.hasObject("spring_freq") || scriptEntry.hasObject("motor_spring_freq"))
                        ? Args.num(scriptEntry.getObjectTag(scriptEntry.hasObject("spring_freq") ? "spring_freq" : "motor_spring_freq"), 2.0)
                        : null;
                Double springDamp = (scriptEntry.hasObject("spring_damp") || scriptEntry.hasObject("motor_spring_damp"))
                        ? Args.num(scriptEntry.getObjectTag(scriptEntry.hasObject("spring_damp") ? "spring_damp" : "motor_spring_damp"), 1.0)
                        : null;

                w.setConstraintMotor(cName, newState, target, limit, springFreq, springDamp);
                return;
            }

            if (act.equals("create")) {
                ConstraintDef def = new ConstraintDef();
                def.name = nameTag.asString();
                if (scriptEntry.hasObject("type")) {
                    def.kind = ConstraintDef.Kind.valueOf(scriptEntry.getElement("type").asString().toUpperCase());
                }
                if (scriptEntry.hasObject("body_a")) def.bodyA = scriptEntry.getElement("body_a").asString();
                if (scriptEntry.hasObject("body_b")) def.bodyB = scriptEntry.getElement("body_b").asString();

                if (scriptEntry.hasObject("point_a")) def.pointA = Args.vec(scriptEntry.getObjectTag("point_a"), scriptEntry.context);
                if (scriptEntry.hasObject("point_b")) def.pointB = Args.vec(scriptEntry.getObjectTag("point_b"), scriptEntry.context);
                if (scriptEntry.hasObject("axis")) def.axis = Args.vec(scriptEntry.getObjectTag("axis"), scriptEntry.context);
                if (scriptEntry.hasObject("normal")) def.normalAxis = Args.vec(scriptEntry.getObjectTag("normal"), scriptEntry.context);

                boolean degrees = Args.bool(scriptEntry.getObjectTag("degrees"), false);
                if (scriptEntry.hasObject("limits")) {
                    double[] lim = Args.pair(scriptEntry.getObjectTag("limits"));
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
                if (scriptEntry.hasObject("cone_angle")) {
                    double angle = Math.toRadians(Args.num(scriptEntry.getObjectTag("cone_angle"), 45.0));
                    def.halfConeAngle = Math.max(0.01, Math.min(Math.PI, angle));
                }
                if (scriptEntry.hasObject("swing")) {
                    double[] sw = Args.pair(scriptEntry.getObjectTag("swing"));
                    def.swingNormalHalfCone = Math.max(0.0, Math.min(Math.PI, Math.toRadians(sw[0])));
                    def.swingPlaneHalfCone = Math.max(0.0, Math.min(Math.PI, Math.toRadians(sw[1])));
                }
                if (scriptEntry.hasObject("twist")) {
                    double[] tw = Args.pair(scriptEntry.getObjectTag("twist"));
                    def.twistMin = Math.max(-Math.PI, Math.min(Math.PI, Math.toRadians(Math.min(tw[0], tw[1]))));
                    def.twistMax = Math.max(-Math.PI, Math.min(Math.PI, Math.toRadians(Math.max(tw[0], tw[1]))));
                }
                def.maxFriction = Args.num(scriptEntry.getObjectTag("friction"), 0);
                def.springFrequency = Args.num(scriptEntry.getObjectTag("spring_freq"), 0);
                def.springDamping = Args.num(scriptEntry.getObjectTag("spring_damp"), 0.5);

                if (scriptEntry.hasObject("motor_state") || scriptEntry.hasObject("motor")) {
                    String stateStr = CoreUtilities.toLowerCase(scriptEntry.getElement(scriptEntry.hasObject("motor_state") ? "motor_state" : "motor").asString());
                    if (stateStr.equals("velocity")) def.motorState = ConstraintDef.MotorState.VELOCITY;
                    else if (stateStr.equals("position")) def.motorState = ConstraintDef.MotorState.POSITION;
                    else if (stateStr.equals("off")) def.motorState = ConstraintDef.MotorState.OFF;
                    else throw new PhysException("Unknown motor state '" + stateStr + "'.");
                }

                if (scriptEntry.hasObject("motor_target")) {
                    def.motorTarget = Args.num(scriptEntry.getObjectTag("motor_target"), 0);
                    if (def.kind == ConstraintDef.Kind.HINGE && degrees) {
                        def.motorTarget = Math.toRadians(def.motorTarget);
                    }
                }
                else if (scriptEntry.hasObject("motor_velocity")) {
                    def.motorTarget = Args.num(scriptEntry.getObjectTag("motor_velocity"), 0);
                    if (def.kind == ConstraintDef.Kind.HINGE && degrees) {
                        def.motorTarget = Math.toRadians(def.motorTarget);
                    }
                    if (def.motorState == ConstraintDef.MotorState.OFF) def.motorState = ConstraintDef.MotorState.VELOCITY;
                }
                else if (scriptEntry.hasObject("motor_angle") || scriptEntry.hasObject("motor_position")) {
                    def.motorTarget = Args.num(scriptEntry.getObjectTag(scriptEntry.hasObject("motor_angle") ? "motor_angle" : "motor_position"), 0);
                    if (def.kind == ConstraintDef.Kind.HINGE && degrees) {
                        def.motorTarget = Math.toRadians(def.motorTarget);
                    }
                    if (def.motorState == ConstraintDef.MotorState.OFF) def.motorState = ConstraintDef.MotorState.POSITION;
                }

                if (scriptEntry.hasObject("motor_torque")) {
                    def.motorMaxTorque = Args.num(scriptEntry.getObjectTag("motor_torque"), 0);
                }
                if (scriptEntry.hasObject("motor_force")) {
                    def.motorMaxForce = Args.num(scriptEntry.getObjectTag("motor_force"), 0);
                }
                if (scriptEntry.hasObject("motor_spring_freq")) {
                    def.motorSpringFrequency = Args.num(scriptEntry.getObjectTag("motor_spring_freq"), 2.0);
                }
                if (scriptEntry.hasObject("motor_spring_damp")) {
                    def.motorSpringDamping = Args.num(scriptEntry.getObjectTag("motor_spring_damp"), 1.0);
                }

                w.addConstraint(def);
            }
        }
        catch (PhysException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }
}