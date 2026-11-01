package dev.egor201.physizen.denizen.save;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import dev.egor201.physizen.core.model.ConstraintDef;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.denizen.args.Args;

import java.util.ArrayList;
import java.util.List;

public final class ConstraintCodec {

    private ConstraintCodec() {
    }

    private static String join3(double[] v) {
        return v[0] + "," + v[1] + "," + v[2];
    }

    public static MapTag toMap(ConstraintDef def) {
        MapTag cMap = new MapTag();
        cMap.putObject("name", new ElementTag(def.name));
        cMap.putObject("type", new ElementTag(def.kind.name()));
        if (def.bodyA != null) cMap.putObject("body_a", new ElementTag(def.bodyA));
        if (def.bodyB != null) cMap.putObject("body_b", new ElementTag(def.bodyB));
        cMap.putObject("point_a", new ElementTag(join3(def.pointA)));
        if (def.pointB != null) cMap.putObject("point_b", new ElementTag(join3(def.pointB)));
        cMap.putObject("axis", new ElementTag(join3(def.axis)));
        cMap.putObject("normal", new ElementTag(join3(def.normalAxis)));
        cMap.putObject("limited", new ElementTag(def.limited));
        cMap.putObject("limits", new ElementTag(def.limitMin + "," + def.limitMax));
        cMap.putObject("friction", new ElementTag(def.maxFriction));
        cMap.putObject("spring_freq", new ElementTag(def.springFrequency));
        cMap.putObject("spring_damp", new ElementTag(def.springDamping));
        cMap.putObject("cone_angle", new ElementTag(Math.toDegrees(def.halfConeAngle)));
        cMap.putObject("motor_state", new ElementTag(def.motorState.name().toLowerCase()));
        cMap.putObject("motor_target", new ElementTag(def.motorTarget));
        cMap.putObject("motor_torque", new ElementTag(def.motorMaxTorque));
        cMap.putObject("motor_force", new ElementTag(def.motorMaxForce));
        cMap.putObject("motor_spring_freq", new ElementTag(def.motorSpringFrequency));
        cMap.putObject("motor_spring_damp", new ElementTag(def.motorSpringDamping));
        if (def.localA != null) {
            cMap.putObject("local_a", new ElementTag(join3(def.localA)));
            cMap.putObject("local_b", new ElementTag(join3(def.localB)));
            cMap.putObject("local_axis", new ElementTag(join3(def.localAxis)));
            cMap.putObject("local_normal", new ElementTag(join3(def.localNormal)));
        }
        return cMap;
    }

    public static List<MapTag> listFor(PhysWorld pw, String bodyName) {
        List<MapTag> list = new ArrayList<>();
        for (String cName : pw.constraintNames()) {
            ConstraintDef def = pw.constraintDef(cName);
            if (def != null && (bodyName.equals(def.bodyA) || bodyName.equals(def.bodyB))) {
                list.add(toMap(def));
            }
        }
        return list;
    }

    static void applyCommon(ConstraintDef def, MapTag cMap) {
        if (cMap.containsKey("normal")) def.normalAxis = Args.vec(cMap.getObject("normal"), CoreUtilities.noDebugContext);
        if (cMap.containsKey("limits")) {
            double[] lim = Args.pair(cMap.getObject("limits"));
            def.limitMin = lim[0];
            def.limitMax = lim[1];
            def.limited = Args.bool(cMap.getObject("limited"), true);
        }
        def.maxFriction = Args.num(cMap.getObject("friction"), 0);
        def.springFrequency = Args.num(cMap.getObject("spring_freq"), 0);
        def.springDamping = Args.num(cMap.getObject("spring_damp"), 0.5);
        if (cMap.containsKey("cone_angle")) {
            def.halfConeAngle = Math.toRadians(Args.num(cMap.getObject("cone_angle"), 45.0));
        }

        if (cMap.containsKey("motor_state")) {
            String stateStr = cMap.getElement("motor_state").asString().toLowerCase();
            if (stateStr.equals("velocity")) def.motorState = ConstraintDef.MotorState.VELOCITY;
            else if (stateStr.equals("position")) def.motorState = ConstraintDef.MotorState.POSITION;
            else def.motorState = ConstraintDef.MotorState.OFF;
        }
        def.motorTarget = Args.num(cMap.getObject("motor_target"), 0);
        def.motorMaxTorque = Args.num(cMap.getObject("motor_torque"), 0);
        def.motorMaxForce = Args.num(cMap.getObject("motor_force"), 0);
        def.motorSpringFrequency = Args.num(cMap.getObject("motor_spring_freq"), 2.0);
        def.motorSpringDamping = Args.num(cMap.getObject("motor_spring_damp"), 1.0);
    }

    public static ConstraintDef fromMap(PhysWorld pw, MapTag cMap) {
        ConstraintDef def = new ConstraintDef();
        def.name = cMap.getElement("name").asString();
        def.kind = ConstraintDef.Kind.valueOf(cMap.getElement("type").asString().toUpperCase());
        if (cMap.containsKey("body_a")) def.bodyA = cMap.getElement("body_a").asString();
        if (cMap.containsKey("body_b")) def.bodyB = cMap.getElement("body_b").asString();
        applyCommon(def, cMap);
        PhysBody a = def.bodyA == null ? null : pw.body(def.bodyA);
        PhysBody b = def.bodyB == null ? null : pw.body(def.bodyB);
        if (cMap.containsKey("local_a")) {
            def.pointA = ConstraintDef.toWorld(a == null ? null : a.snapshot, Args.vec(cMap.getObject("local_a"), CoreUtilities.noDebugContext));
            def.pointB = ConstraintDef.toWorld(b == null ? null : b.snapshot, Args.vec(cMap.getObject("local_b"), CoreUtilities.noDebugContext));
            def.axis = ConstraintDef.rotate(a == null ? null : a.snapshot, Args.vec(cMap.getObject("local_axis"), CoreUtilities.noDebugContext), false);
            def.normalAxis = ConstraintDef.rotate(a == null ? null : a.snapshot, Args.vec(cMap.getObject("local_normal"), CoreUtilities.noDebugContext), false);
        }
        else {
            def.pointA = Args.vec(cMap.getObject("point_a"), CoreUtilities.noDebugContext);
            def.pointB = cMap.containsKey("point_b") ? Args.vec(cMap.getObject("point_b"), CoreUtilities.noDebugContext) : def.pointA;
            if (cMap.containsKey("axis")) def.axis = Args.vec(cMap.getObject("axis"), CoreUtilities.noDebugContext);
        }
        return def;
    }
}
