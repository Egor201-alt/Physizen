package dev.egor201.physizen.denizen.command;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.VehicleDef;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.denizen.args.Args;

// <--[command]
// @Name physvehicle
// @Syntax physvehicle [create/remove/input] (world:<world>) name:<name> (body:<chassis body>) (drive:<front/rear/all>) (forward:<vec>) (up:<vec>) (wheel_radius:<number>) (max_steer:<degrees>) (suspension_freq:<number>) (brake_torque:<number>) (handbrake_torque:<number>) (torque:<number>) (min_rpm:<number>) (max_rpm:<number>) (gears:<list>) (differential:<number>) (automatic:<boolean>) (max_pitch_roll:<degrees>) (steer:<-1 to 1>) (throttle:<-1 to 1>) (brake:<0 to 1>) (handbrake:<0 to 1>)
// @Required 2
// @Maximum 24
// @Short Turns a dynamic body into a wheeled vehicle, removes it, or sets driver input.
// @Group Physizen
// -->
public class PhysVehicleCommand extends AbstractCommand {

    public PhysVehicleCommand() {
        setName("physvehicle");
        setSyntax("physvehicle [create/remove/input] (world:<world>) name:<name> (body:<chassis body>) (drive:<front/rear/all>) (forward:<vec>) (up:<vec>) (wheel_radius:<number>) (max_steer:<degrees>) (suspension_freq:<number>) (brake_torque:<number>) (handbrake_torque:<number>) (torque:<number>) (min_rpm:<number>) (max_rpm:<number>) (gears:<list>) (differential:<number>) (automatic:<boolean>) (max_pitch_roll:<degrees>) (steer:<-1 to 1>) (throttle:<-1 to 1>) (brake:<0 to 1>) (handbrake:<0 to 1>)");
        setRequiredArguments(2, 24);
        isProcedural = false;
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsRuntimeException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("action") && arg.prefix == null) {
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

    private static double[] numbers(String text) {
        String plain = text.trim();
        if (plain.startsWith("l@")) {
            plain = plain.substring(2);
        }
        String[] parts = plain.split(",");
        double[] out = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Double.parseDouble(parts[i].trim());
            }
        }
        catch (NumberFormatException e) {
            throw new PhysException("Invalid number list '" + text + "'.");
        }
        return out;
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        ElementTag action = scriptEntry.getElement("action");
        ElementTag nameTag = scriptEntry.getElement("name");
        if (action == null || nameTag == null) {
            Debug.echoError(scriptEntry, "physvehicle needs an action and name:<name>.");
            return;
        }
        String act = CoreUtilities.toLowerCase(action.asString());
        String worldId = scriptEntry.hasObject("world") ? scriptEntry.getElement("world").asString() : "default";
        String name = nameTag.asString();
        try {
            PhysWorld w = Args.world(worldId);
            if (act.equals("remove")) {
                w.removeVehicle(name);
                return;
            }
            if (act.equals("input")) {
                VehicleDef current = w.vehicleDef(name);
                if (current == null) {
                    throw new PhysException("Unknown vehicle '" + name + "'.");
                }
                double throttle = Args.num(scriptEntry.getObjectTag("throttle"), Args.num(scriptEntry.getObjectTag("forward"), 0));
                double steer = Args.num(scriptEntry.getObjectTag("steer"), 0);
                double brake = Args.num(scriptEntry.getObjectTag("brake"), 0);
                double handbrake = Args.num(scriptEntry.getObjectTag("handbrake"), 0);
                w.setVehicleInput(name, throttle, steer, brake, handbrake);
                return;
            }
            if (!act.equals("create")) {
                throw new PhysException("Unknown physvehicle action '" + act + "', expected create, remove or input.");
            }
            if (!scriptEntry.hasObject("body")) {
                throw new PhysException("physvehicle create needs body:<chassis body>.");
            }
            String bodyName = scriptEntry.getElement("body").asString();
            PhysBody chassis = w.body(bodyName);
            if (chassis == null) {
                throw new PhysException("Unknown body '" + bodyName + "'.");
            }
            String drive = scriptEntry.hasObject("drive") ? CoreUtilities.toLowerCase(scriptEntry.getElement("drive").asString()) : "rear";
            if (!drive.equals("front") && !drive.equals("rear") && !drive.equals("all")) {
                throw new PhysException("drive must be front, rear or all.");
            }
            double[] half = chassis.def.shape.halfExtentsEstimate();
            VehicleDef d = VehicleDef.car(name, bodyName, half[0], half[1], half[2], !drive.equals("rear"), !drive.equals("front"));
            if (scriptEntry.hasObject("forward")) d.forward = Args.vec(scriptEntry.getObjectTag("forward"), scriptEntry.context);
            if (scriptEntry.hasObject("up")) d.up = Args.vec(scriptEntry.getObjectTag("up"), scriptEntry.context);
            for (VehicleDef.Wheel wh : d.wheels) {
                if (scriptEntry.hasObject("wheel_radius")) wh.radius = Args.num(scriptEntry.getObjectTag("wheel_radius"), wh.radius);
                if (scriptEntry.hasObject("suspension_freq")) wh.suspensionFrequency = Args.num(scriptEntry.getObjectTag("suspension_freq"), wh.suspensionFrequency);
                if (scriptEntry.hasObject("brake_torque")) wh.maxBrakeTorque = Args.num(scriptEntry.getObjectTag("brake_torque"), wh.maxBrakeTorque);
                if (scriptEntry.hasObject("handbrake_torque") && wh.maxHandBrakeTorque > 0) wh.maxHandBrakeTorque = Args.num(scriptEntry.getObjectTag("handbrake_torque"), wh.maxHandBrakeTorque);
                if (scriptEntry.hasObject("max_steer") && wh.maxSteer > 0) wh.maxSteer = Args.num(scriptEntry.getObjectTag("max_steer"), wh.maxSteer);
                if (scriptEntry.hasObject("wheel_radius")) {
                    wh.suspensionMin = wh.radius * 0.5;
                    wh.suspensionMax = wh.radius * 1.25;
                }
            }
            d.engineTorque = Args.num(scriptEntry.getObjectTag("torque"), d.engineTorque);
            d.engineMinRpm = Args.num(scriptEntry.getObjectTag("min_rpm"), d.engineMinRpm);
            d.engineMaxRpm = Args.num(scriptEntry.getObjectTag("max_rpm"), d.engineMaxRpm);
            d.differentialRatio = Args.num(scriptEntry.getObjectTag("differential"), d.differentialRatio);
            d.maxPitchRoll = Args.num(scriptEntry.getObjectTag("max_pitch_roll"), d.maxPitchRoll);
            d.automatic = Args.bool(scriptEntry.getObjectTag("automatic"), true);
            if (scriptEntry.hasObject("gears")) d.gearRatios = numbers(scriptEntry.getElement("gears").asString());
            w.addVehicle(d);
        }
        catch (PhysException | IllegalArgumentException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }
}
