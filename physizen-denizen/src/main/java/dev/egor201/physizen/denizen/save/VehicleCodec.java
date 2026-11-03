package dev.egor201.physizen.denizen.save;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.VehicleDef;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.denizen.args.Args;

import java.util.ArrayList;
import java.util.List;

public final class VehicleCodec {

    private VehicleCodec() {
    }

    private static String join(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.toString();
    }

    private static double[] parseList(ObjectTag tag) {
        String text = tag.toString().trim();
        if (text.startsWith("l@")) {
            text = text.substring(2);
        }
        if (text.isEmpty()) {
            return new double[0];
        }
        String[] parts = text.split(",");
        double[] out = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Double.parseDouble(parts[i].trim());
            }
        }
        catch (NumberFormatException e) {
            throw new PhysException("Invalid number list '" + tag + "'.");
        }
        return out;
    }

    public static MapTag toMap(VehicleDef d) {
        MapTag map = new MapTag();
        map.putObject("name", new ElementTag(d.name));
        map.putObject("body", new ElementTag(d.body));
        map.putObject("up", new ElementTag(join(d.up)));
        map.putObject("forward", new ElementTag(join(d.forward)));
        map.putObject("max_pitch_roll", new ElementTag(d.maxPitchRoll));
        map.putObject("engine_torque", new ElementTag(d.engineTorque));
        map.putObject("engine_min_rpm", new ElementTag(d.engineMinRpm));
        map.putObject("engine_max_rpm", new ElementTag(d.engineMaxRpm));
        map.putObject("gears", new ElementTag(join(d.gearRatios)));
        map.putObject("reverse_gears", new ElementTag(join(d.reverseGearRatios)));
        map.putObject("differential", new ElementTag(d.differentialRatio));
        map.putObject("automatic", new ElementTag(d.automatic));
        map.putObject("limited_slip", new ElementTag(d.limitedSlip));
        ListTag wheels = new ListTag();
        for (VehicleDef.Wheel w : d.wheels) {
            MapTag wm = new MapTag();
            wm.putObject("position", new ElementTag(join(w.position)));
            wm.putObject("radius", new ElementTag(w.radius));
            wm.putObject("width", new ElementTag(w.width));
            wm.putObject("suspension_min", new ElementTag(w.suspensionMin));
            wm.putObject("suspension_max", new ElementTag(w.suspensionMax));
            wm.putObject("suspension_freq", new ElementTag(w.suspensionFrequency));
            wm.putObject("suspension_damp", new ElementTag(w.suspensionDamping));
            wm.putObject("max_steer", new ElementTag(w.maxSteer));
            wm.putObject("brake_torque", new ElementTag(w.maxBrakeTorque));
            wm.putObject("handbrake_torque", new ElementTag(w.maxHandBrakeTorque));
            wm.putObject("axle", new ElementTag(w.axle));
            wm.putObject("driven", new ElementTag(w.driven));
            wheels.addObject(wm);
        }
        map.putObject("wheels", wheels);
        return map;
    }

    public static VehicleDef fromMap(MapTag map, String prefix) {
        VehicleDef d = new VehicleDef();
        d.name = prefix + map.getElement("name").asString();
        d.body = prefix + map.getElement("body").asString();
        if (map.containsKey("up")) d.up = Args.vec(map.getObject("up"), CoreUtilities.noDebugContext);
        if (map.containsKey("forward")) d.forward = Args.vec(map.getObject("forward"), CoreUtilities.noDebugContext);
        d.maxPitchRoll = Args.num(map.getObject("max_pitch_roll"), d.maxPitchRoll);
        d.engineTorque = Args.num(map.getObject("engine_torque"), d.engineTorque);
        d.engineMinRpm = Args.num(map.getObject("engine_min_rpm"), d.engineMinRpm);
        d.engineMaxRpm = Args.num(map.getObject("engine_max_rpm"), d.engineMaxRpm);
        if (map.containsKey("gears")) d.gearRatios = parseList(map.getObject("gears"));
        if (map.containsKey("reverse_gears")) d.reverseGearRatios = parseList(map.getObject("reverse_gears"));
        d.differentialRatio = Args.num(map.getObject("differential"), d.differentialRatio);
        d.automatic = Args.bool(map.getObject("automatic"), true);
        d.limitedSlip = Args.num(map.getObject("limited_slip"), d.limitedSlip);
        if (map.containsKey("wheels")) {
            ListTag wheels = map.getObject("wheels").asType(ListTag.class, CoreUtilities.noDebugContext);
            for (ObjectTag obj : wheels.objectForms) {
                MapTag wm = obj.asType(MapTag.class, CoreUtilities.noDebugContext);
                if (wm == null) continue;
                VehicleDef.Wheel w = new VehicleDef.Wheel();
                w.position = Args.vec(wm.getObject("position"), CoreUtilities.noDebugContext);
                w.radius = Args.num(wm.getObject("radius"), w.radius);
                w.width = Args.num(wm.getObject("width"), w.width);
                w.suspensionMin = Args.num(wm.getObject("suspension_min"), w.suspensionMin);
                w.suspensionMax = Args.num(wm.getObject("suspension_max"), w.suspensionMax);
                w.suspensionFrequency = Args.num(wm.getObject("suspension_freq"), w.suspensionFrequency);
                w.suspensionDamping = Args.num(wm.getObject("suspension_damp"), w.suspensionDamping);
                w.maxSteer = Args.num(wm.getObject("max_steer"), 0);
                w.maxBrakeTorque = Args.num(wm.getObject("brake_torque"), w.maxBrakeTorque);
                w.maxHandBrakeTorque = Args.num(wm.getObject("handbrake_torque"), 0);
                w.axle = (int) Args.num(wm.getObject("axle"), 0);
                w.driven = Args.bool(wm.getObject("driven"), false);
                d.wheels.add(w);
            }
        }
        return d;
    }

    public static List<MapTag> listFor(PhysWorld pw, String bodyName) {
        List<MapTag> list = new ArrayList<>();
        for (String name : pw.vehicleNames()) {
            VehicleDef d = pw.vehicleDef(name);
            if (d != null && bodyName.equals(d.body)) {
                list.add(toMap(d));
            }
        }
        return list;
    }
}
