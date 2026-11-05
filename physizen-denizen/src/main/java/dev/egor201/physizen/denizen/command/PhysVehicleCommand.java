package dev.egor201.physizen.denizen.command;

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
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.VehicleDef;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.denizen.args.Args;
import com.denizenscript.denizen.objects.EntityTag;
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.bukkit.display.DisplayBinder;
import java.util.ArrayList;
import java.util.List;

// <--[command]
// @Name physvehicle
// @Syntax physvehicle [create/remove/input/bind/unbind] (world:<world>) name:<name> (body:<chassis body>) (drive:<front/rear/all>) (forward:<vec>) (up:<vec>) (wheel_radius:<number>) (max_steer:<degrees>) (suspension_freq:<number>) (brake_torque:<number>) (handbrake_torque:<number>) (torque:<number>) (min_rpm:<number>) (max_rpm:<number>) (gears:<list>) (differential:<number>) (automatic:<boolean>) (max_pitch_roll:<degrees>) (steer:<-1 to 1>) (throttle:<-1 to 1>) (brake:<0 to 1>) (handbrake:<0 to 1>) (wheels:<list of entities>) (chassis:<entity>) (offset:<vec>) (scale:<vec>) (rotation_offset:<rotation>) (interpolation:<ticks>) (teleport_duration:<ticks>) (remove_with_body:<boolean>)
// @Required 2
// @Maximum 32
// @Short Turns a dynamic body into a wheeled vehicle, removes it, sets driver input, or binds wheel and chassis display entities to it.
// @Group Physizen
// -->
public class PhysVehicleCommand extends AbstractCommand {

    public PhysVehicleCommand() {
        setName("physvehicle");
        setSyntax("physvehicle [create/remove/input/bind/unbind] (world:<world>) name:<name> (body:<chassis body>) (drive:<front/rear/all>) (forward:<vec>) (up:<vec>) (wheel_radius:<number>) (max_steer:<degrees>) (suspension_freq:<number>) (brake_torque:<number>) (handbrake_torque:<number>) (torque:<number>) (min_rpm:<number>) (max_rpm:<number>) (gears:<list>) (differential:<number>) (automatic:<boolean>) (max_pitch_roll:<degrees>) (steer:<-1 to 1>) (throttle:<-1 to 1>) (brake:<0 to 1>) (handbrake:<0 to 1>) (wheels:<list of entities>) (chassis:<entity>) (offset:<vec>) (scale:<vec>) (rotation_offset:<rotation>) (interpolation:<ticks>) (teleport_duration:<ticks>) (remove_with_body:<boolean>)");
        setRequiredArguments(2, 32);
        isProcedural = false;
        autoCompile();
        addRemappedPrefixes("name", "id");
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

    public enum Action {
        REMOVE, UNBIND, BIND, INPUT, CREATE
    }

    public static void autoExecute(ScriptEntry scriptEntry,
            @ArgName("action") @ArgLinear Action action,
            @ArgName("name") @ArgPrefixed @ArgDefaultNull ElementTag nameArg,
            @ArgName("world") @ArgPrefixed @ArgDefaultNull ElementTag worldArg,
            @ArgName("wheels") @ArgPrefixed @ArgDefaultNull ObjectTag wheelsArg,
            @ArgName("chassis") @ArgPrefixed @ArgDefaultNull ObjectTag chassisArg,
            @ArgName("throttle") @ArgPrefixed @ArgDefaultNull ObjectTag throttleArg,
            @ArgName("forward") @ArgPrefixed @ArgDefaultNull ObjectTag forwardArg,
            @ArgName("steer") @ArgPrefixed @ArgDefaultNull ObjectTag steerArg,
            @ArgName("brake") @ArgPrefixed @ArgDefaultNull ObjectTag brakeArg,
            @ArgName("handbrake") @ArgPrefixed @ArgDefaultNull ObjectTag handbrakeArg,
            @ArgName("body") @ArgPrefixed @ArgDefaultNull ElementTag bodyArg,
            @ArgName("drive") @ArgPrefixed @ArgDefaultNull ElementTag driveArg,
            @ArgName("rear") @ArgPrefixed @ArgDefaultNull ObjectTag rearArg,
            @ArgName("up") @ArgPrefixed @ArgDefaultNull ObjectTag upArg,
            @ArgName("wheel_radius") @ArgPrefixed @ArgDefaultNull ObjectTag wheelRadiusArg,
            @ArgName("suspension_freq") @ArgPrefixed @ArgDefaultNull ObjectTag suspensionFreqArg,
            @ArgName("brake_torque") @ArgPrefixed @ArgDefaultNull ObjectTag brakeTorqueArg,
            @ArgName("handbrake_torque") @ArgPrefixed @ArgDefaultNull ObjectTag handbrakeTorqueArg,
            @ArgName("max_steer") @ArgPrefixed @ArgDefaultNull ObjectTag maxSteerArg,
            @ArgName("torque") @ArgPrefixed @ArgDefaultNull ObjectTag torqueArg,
            @ArgName("min_rpm") @ArgPrefixed @ArgDefaultNull ObjectTag minRpmArg,
            @ArgName("max_rpm") @ArgPrefixed @ArgDefaultNull ObjectTag maxRpmArg,
            @ArgName("differential") @ArgPrefixed @ArgDefaultNull ObjectTag differentialArg,
            @ArgName("max_pitch_roll") @ArgPrefixed @ArgDefaultNull ObjectTag maxPitchRollArg,
            @ArgName("automatic") @ArgPrefixed @ArgDefaultNull ObjectTag automaticArg,
            @ArgName("gears") @ArgPrefixed @ArgDefaultNull ElementTag gearsArg,
            @ArgName("offset") @ArgPrefixed @ArgDefaultNull ObjectTag offsetArg,
            @ArgName("scale") @ArgPrefixed @ArgDefaultNull ObjectTag scaleArg,
            @ArgName("rotation_offset") @ArgPrefixed @ArgDefaultNull ObjectTag rotationOffsetArg,
            @ArgName("interpolation_duration") @ArgPrefixed @ArgDefaultNull ObjectTag interpolationDurationArg,
            @ArgName("interpolation") @ArgPrefixed @ArgDefaultNull ObjectTag interpolationArg,
            @ArgName("teleport_duration") @ArgPrefixed @ArgDefaultNull ObjectTag teleportDurationArg,
            @ArgName("remove_with_body") @ArgPrefixed @ArgDefaultNull ObjectTag removeWithBodyArg) {
        ElementTag action = actionArg;
        ElementTag nameTag = nameArg;
        if (action == null || nameTag == null) {
            Debug.echoError(scriptEntry, "physvehicle needs an action and name:<name>.");
            return;
        }
        String act = action.name().toLowerCase();
        String worldId = (worldArg != null) ? worldArg.asString() : "default";
        String name = nameTag.asString();
        try {
            PhysWorld w = Args.world(worldId);
            if (action == Action.REMOVE) {
                w.removeVehicle(name);
                return;
            }
            if (action == Action.UNBIND) {
                VehicleDef bound = w.vehicleDef(name);
                PhysizenServices.get().wheelDisplays.unbind(worldId, name);
                if (bound != null) {
                    PhysizenServices.get().displays.unbindAll(worldId, bound.body);
                }
                return;
            }
            if (action == Action.BIND) {
                VehicleDef bound = w.vehicleDef(name);
                if (bound == null) {
                    throw new PhysException("Unknown vehicle '" + name + "'.");
                }
                if (wheelsArg != null) {
                    List<DisplayBinder.Binding> perWheel = new ArrayList<>();
                    for (EntityTag et : Args.entities(wheelsArg, scriptEntry.context)) {
                        perWheel.add(BindingArgs.create(et, scriptEntry, offsetArg, scaleArg, rotationOffsetArg, interpolationDurationArg, interpolationArg, teleportDurationArg, removeWithBodyArg));
                    }
                    if (perWheel.size() > bound.wheels.size()) {
                        throw new PhysException("Vehicle '" + name + "' has " + bound.wheels.size() + " wheels but " + perWheel.size() + " entities were given.");
                    }
                    PhysizenServices.get().wheelDisplays.bind(worldId, name, perWheel);
                }
                if (chassisArg != null) {
                    for (EntityTag et : Args.entities(chassisArg, scriptEntry.context)) {
                        PhysizenServices.get().displays.bind(worldId, bound.body, BindingArgs.create(et, scriptEntry, offsetArg, scaleArg, rotationOffsetArg, interpolationDurationArg, interpolationArg, teleportDurationArg, removeWithBodyArg));
                    }
                }
                return;
            }
            if (action == Action.INPUT) {
                VehicleDef current = w.vehicleDef(name);
                if (current == null) {
                    throw new PhysException("Unknown vehicle '" + name + "'.");
                }
                double throttle = Args.num(throttleArg, Args.num(forwardArg, 0));
                double steer = Args.num(steerArg, 0);
                double brake = Args.num(brakeArg, 0);
                double handbrake = Args.num(handbrakeArg, 0);
                w.setVehicleInput(name, throttle, steer, brake, handbrake);
                return;
            }
            if (!action == Action.CREATE) {
                throw new PhysException("Unknown physvehicle action '" + act + "', expected create, remove, input, bind or unbind.");
            }
            if (!(bodyArg != null)) {
                throw new PhysException("physvehicle create needs body:<chassis body>.");
            }
            String bodyName = bodyArg.asString();
            PhysBody chassis = w.body(bodyName);
            if (chassis == null) {
                throw new PhysException("Unknown body '" + bodyName + "'.");
            }
            String drive = (driveArg != null) ? CoreUtilities.toLowerCase(driveArg.asString()) : "rear";
            if (!drive.equals("front") && !drive.equals("rear") && !drive.equals("all")) {
                throw new PhysException("drive must be front, rear or all.");
            }
            double[] half = chassis.def.shape.halfExtentsEstimate();
            VehicleDef d = VehicleDef.car(name, bodyName, half[0], half[1], half[2], !drive.equals("rear"), !drive.equals("front"));
            if (forwardArg != null) d.forward = Args.vec(forwardArg, scriptEntry.context);
            if (upArg != null) d.up = Args.vec(upArg, scriptEntry.context);
            for (VehicleDef.Wheel wh : d.wheels) {
                if (wheelRadiusArg != null) wh.radius = Args.num(wheelRadiusArg, wh.radius);
                if (suspensionFreqArg != null) wh.suspensionFrequency = Args.num(suspensionFreqArg, wh.suspensionFrequency);
                if (brakeTorqueArg != null) wh.maxBrakeTorque = Args.num(brakeTorqueArg, wh.maxBrakeTorque);
                if ((handbrakeTorqueArg != null) && wh.maxHandBrakeTorque > 0) wh.maxHandBrakeTorque = Args.num(handbrakeTorqueArg, wh.maxHandBrakeTorque);
                if ((maxSteerArg != null) && wh.maxSteer > 0) wh.maxSteer = Args.num(maxSteerArg, wh.maxSteer);
                if (wheelRadiusArg != null) {
                    wh.suspensionMin = wh.radius * 0.5;
                    wh.suspensionMax = wh.radius * 1.25;
                }
            }
            d.engineTorque = Args.num(torqueArg, d.engineTorque);
            d.engineMinRpm = Args.num(minRpmArg, d.engineMinRpm);
            d.engineMaxRpm = Args.num(maxRpmArg, d.engineMaxRpm);
            d.differentialRatio = Args.num(differentialArg, d.differentialRatio);
            d.maxPitchRoll = Args.num(maxPitchRollArg, d.maxPitchRoll);
            d.automatic = Args.bool(automaticArg, true);
            if (gearsArg != null) d.gearRatios = numbers(gearsArg.asString());
            w.addVehicle(d);
        }
        catch (PhysException | IllegalArgumentException e) {
            Debug.echoError(scriptEntry, e.getMessage());
        }
    }

}
