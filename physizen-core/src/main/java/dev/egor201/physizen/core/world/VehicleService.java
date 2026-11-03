package dev.egor201.physizen.core.world;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.VehicleCollisionTesterRay;
import com.github.stephengold.joltjni.VehicleConstraint;
import com.github.stephengold.joltjni.VehicleConstraintSettings;
import com.github.stephengold.joltjni.VehicleDifferentialSettings;
import com.github.stephengold.joltjni.WheelSettingsWv;
import com.github.stephengold.joltjni.WheeledVehicleController;
import com.github.stephengold.joltjni.WheeledVehicleControllerSettings;
import com.github.stephengold.joltjni.Wheel;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.MotionKind;
import dev.egor201.physizen.core.model.VehicleDef;
import dev.egor201.physizen.core.model.VehicleTelemetry;
import dev.egor201.physizen.core.validate.Guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

final class VehicleService {

    private static final class Entry {

        final VehicleDef def;
        volatile double forward;
        volatile double steer;
        volatile double brake;
        volatile double handbrake;
        volatile VehicleTelemetry telemetry;
        VehicleConstraint constraint;
        WheeledVehicleController controller;
        VehicleCollisionTesterRay tester;
        VehicleConstraintSettings settings;
        PhysBody chassis;
        volatile boolean removed;

        Entry(VehicleDef def) {
            this.def = def;
        }
    }

    private final PhysWorld world;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    VehicleService(PhysWorld world) {
        this.world = world;
    }

    List<String> names() {
        List<String> n = new ArrayList<>(entries.keySet());
        Collections.sort(n);
        return n;
    }

    VehicleDef def(String name) {
        Entry e = entries.get(name);
        return e == null ? null : e.def;
    }

    VehicleTelemetry telemetry(String name) {
        Entry e = entries.get(name);
        return e == null ? null : e.telemetry;
    }

    static void validate(VehicleDef d) {
        if (d.name == null || d.name.isEmpty()) {
            throw new PhysException("Vehicle needs a name.");
        }
        if (d.body == null || d.body.isEmpty()) {
            throw new PhysException("Vehicle needs a chassis body.");
        }
        if (d.wheels.isEmpty() || d.wheels.size() > VehicleDef.MAX_WHEELS) {
            throw new PhysException("Vehicle needs between 1 and " + VehicleDef.MAX_WHEELS + " wheels.");
        }
        Guard.vec(d.up, "up");
        Guard.vec(d.forward, "forward");
        Guard.normalizeAxis(d.up, "up");
        Guard.normalizeAxis(d.forward, "forward");
        double dot = d.up[0] * d.forward[0] + d.up[1] * d.forward[1] + d.up[2] * d.forward[2];
        if (Math.abs(dot) > 0.99) {
            throw new PhysException("Vehicle up and forward must not be parallel.");
        }
        Guard.positive(d.engineTorque, "engine torque");
        Guard.positive(d.engineMaxRpm, "engine max rpm");
        Guard.number(d.engineMinRpm, "engine min rpm");
        if (d.engineMinRpm < 0 || d.engineMinRpm >= d.engineMaxRpm) {
            throw new PhysException("Engine min rpm must be below max rpm.");
        }
        if (d.gearRatios == null || d.gearRatios.length == 0 || d.gearRatios.length > 12) {
            throw new PhysException("Vehicle needs between 1 and 12 gears.");
        }
        for (double g : d.gearRatios) {
            Guard.positive(g, "gear ratio");
        }
        Guard.positive(d.differentialRatio, "differential ratio");
        Guard.number(d.maxPitchRoll, "max pitch/roll");
        for (VehicleDef.Wheel w : d.wheels) {
            Guard.vec(w.position, "wheel position");
            Guard.positive(w.radius, "wheel radius");
            Guard.positive(w.width, "wheel width");
            Guard.positive(w.suspensionMax, "suspension max length");
            if (w.suspensionMin < 0 || w.suspensionMin > w.suspensionMax) {
                throw new PhysException("Suspension min length must be between 0 and max length.");
            }
            if (w.suspensionFrequency <= 0 || w.suspensionDamping < 0 || w.maxBrakeTorque < 0 || w.maxHandBrakeTorque < 0
                    || Math.abs(w.maxSteer) > 89) {
                throw new PhysException("Invalid wheel settings.");
            }
        }
    }

    void add(VehicleDef def) {
        validate(def);
        if (entries.containsKey(def.name)) {
            throw new PhysException("Vehicle " + def.name + " already exists.");
        }
        PhysBody chassis = world.body(def.body);
        if (chassis == null) {
            throw new PhysException("Unknown body " + def.body + ".");
        }
        if (chassis.character != null || chassis.def.type != MotionKind.DYNAMIC) {
            throw new PhysException("A vehicle chassis must be a dynamic body.");
        }
        for (Entry e : entries.values()) {
            if (e.def.body.equals(def.body)) {
                throw new PhysException("Body " + def.body + " is already a vehicle chassis.");
            }
        }
        Entry entry = new Entry(def.copy());
        entries.put(def.name, entry);
        world.post(() -> instantiate(entry));
    }

    private void instantiate(Entry entry) {
        VehicleDef d = entry.def;
        PhysBody chassis = world.body(d.body);
        if (entry.removed || chassis == null || chassis.removed || !chassis.ready || chassis.body == null) {
            entries.remove(d.name, entry);
            world.log.warning("Vehicle " + d.name + " was dropped because its chassis is gone.");
            return;
        }
        try {
            VehicleConstraintSettings s = new VehicleConstraintSettings();
            s.setUp(new Vec3((float) d.up[0], (float) d.up[1], (float) d.up[2]));
            s.setForward(new Vec3((float) d.forward[0], (float) d.forward[1], (float) d.forward[2]));
            s.setMaxPitchRollAngle((float) Math.toRadians(d.maxPitchRoll));
            WheelSettingsWv[] ws = new WheelSettingsWv[d.wheels.size()];
            for (int i = 0; i < ws.length; i++) {
                VehicleDef.Wheel w = d.wheels.get(i);
                WheelSettingsWv x = new WheelSettingsWv();
                x.setPosition(new Vec3((float) w.position[0], (float) w.position[1], (float) w.position[2]));
                x.setSuspensionDirection(new Vec3((float) -d.up[0], (float) -d.up[1], (float) -d.up[2]));
                x.setSteeringAxis(new Vec3((float) d.up[0], (float) d.up[1], (float) d.up[2]));
                x.setWheelForward(new Vec3((float) d.forward[0], (float) d.forward[1], (float) d.forward[2]));
                x.setWheelUp(new Vec3((float) d.up[0], (float) d.up[1], (float) d.up[2]));
                x.setSuspensionMinLength((float) w.suspensionMin);
                x.setSuspensionMaxLength((float) w.suspensionMax);
                x.setRadius((float) w.radius);
                x.setWidth((float) w.width);
                x.getSuspensionSpring().setFrequency((float) w.suspensionFrequency);
                x.getSuspensionSpring().setDamping((float) w.suspensionDamping);
                x.setMaxSteerAngle((float) Math.toRadians(Math.abs(w.maxSteer)));
                x.setMaxBrakeTorque((float) w.maxBrakeTorque);
                x.setMaxHandBrakeTorque((float) w.maxHandBrakeTorque);
                ws[i] = x;
            }
            s.addWheels(ws);

            WheeledVehicleControllerSettings cs = new WheeledVehicleControllerSettings();
            cs.getEngine().setMaxTorque((float) d.engineTorque);
            cs.getEngine().setMinRpm((float) d.engineMinRpm);
            cs.getEngine().setMaxRpm((float) d.engineMaxRpm);
            float[] gears = new float[d.gearRatios.length];
            for (int i = 0; i < gears.length; i++) {
                gears[i] = (float) d.gearRatios[i];
            }
            cs.getTransmission().setGearRatios(gears);
            float[] rev = new float[d.reverseGearRatios == null ? 0 : d.reverseGearRatios.length];
            for (int i = 0; i < rev.length; i++) {
                rev[i] = (float) d.reverseGearRatios[i];
            }
            cs.getTransmission().setReverseGearRatios(rev);
            cs.getTransmission().setMode(d.automatic ? ETransmissionMode.Auto : ETransmissionMode.Manual);

            Map<Integer, List<Integer>> axles = new TreeMap<>();
            for (int i = 0; i < d.wheels.size(); i++) {
                VehicleDef.Wheel w = d.wheels.get(i);
                if (w.driven) {
                    axles.computeIfAbsent(w.axle, k -> new ArrayList<>()).add(i);
                }
            }
            if (axles.isEmpty()) {
                throw new PhysException("Vehicle needs at least one driven axle.");
            }
            cs.setNumDifferentials(axles.size());
            float share = 1f / axles.size();
            int di = 0;
            for (List<Integer> pair : axles.values()) {
                VehicleDifferentialSettings ds = cs.getDifferential(di++);
                ds.setLeftWheel(pair.get(0));
                ds.setRightWheel(pair.size() > 1 ? pair.get(1) : -1);
                ds.setDifferentialRatio((float) d.differentialRatio);
                ds.setEngineTorqueRatio(share);
                ds.setLeftRightSplit(pair.size() > 1 ? 0.5f : 0f);
                ds.setLimitedSlipRatio((float) d.limitedSlip);
            }
            s.setController(cs);

            VehicleConstraint vc = new VehicleConstraint(chassis.body, s);
            Vec3 up = new Vec3((float) d.up[0], (float) d.up[1], (float) d.up[2]);
            VehicleCollisionTesterRay tester = new VehicleCollisionTesterRay(WorldLayers.layerFor(chassis.def), up, 0.9f);
            vc.setVehicleCollisionTester(tester);
            world.system.addConstraint(vc);
            world.system.addStepListener(vc.getStepListener());
            entry.constraint = vc;
            entry.tester = tester;
            entry.settings = s;
            entry.controller = (WheeledVehicleController) vc.getController();
            entry.chassis = chassis;
            world.bodyInterface.activateBody(chassis.joltId);
        }
        catch (Throwable t) {
            entries.remove(d.name, entry);
            world.log.warning("Failed to create vehicle " + d.name + ": " + t.getMessage());
        }
    }

    boolean remove(String name) {
        Entry entry = entries.remove(name);
        if (entry == null) {
            return false;
        }
        entry.removed = true;
        world.post(() -> detach(entry));
        return true;
    }

    private void detach(Entry entry) {
        if (entry.constraint == null) {
            return;
        }
        try {
            world.system.removeStepListener(entry.constraint.getStepListener());
            world.system.removeConstraint(entry.constraint);
        }
        catch (Throwable t) {
            world.log.warning("Failed to remove vehicle " + entry.def.name + ": " + t.getMessage());
        }
        entry.constraint = null;
        entry.controller = null;
    }

    String vehicleOfBody(String bodyName) {
        for (Entry e : entries.values()) {
            if (e.def.body.equals(bodyName) && !e.removed) {
                return e.def.name;
            }
        }
        return null;
    }

    void removeForBody(String bodyName) {
        for (Entry e : new ArrayList<>(entries.values())) {
            if (e.def.body.equals(bodyName)) {
                remove(e.def.name);
            }
        }
    }

    void dropAll() {
        for (Entry e : entries.values()) {
            e.removed = true;
            e.constraint = null;
            e.controller = null;
        }
        entries.clear();
    }

    void setInput(String name, double forward, double steer, double brake, double handbrake) {
        Entry e = entries.get(name);
        if (e == null) {
            throw new PhysException("Unknown vehicle " + name + ".");
        }
        Guard.number(forward, "forward");
        Guard.number(steer, "steer");
        Guard.number(brake, "brake");
        Guard.number(handbrake, "handbrake");
        e.forward = Math.max(-1, Math.min(1, forward));
        e.steer = Math.max(-1, Math.min(1, steer));
        e.brake = Math.max(0, Math.min(1, brake));
        e.handbrake = Math.max(0, Math.min(1, handbrake));
    }

    void applyInputs() {
        for (Entry e : entries.values()) {
            WheeledVehicleController c = e.controller;
            if (c == null || e.chassis == null || e.chassis.removed) {
                continue;
            }
            float f = (float) e.forward;
            float b = (float) e.brake;
            c.setDriverInput(f, (float) e.steer, b, (float) e.handbrake);
            if (f != 0 || e.steer != 0 || b != 0 || e.handbrake != 0) {
                world.bodyInterface.activateBody(e.chassis.joltId);
            }
        }
    }

    void publish() {
        for (Entry e : entries.values()) {
            if (e.constraint == null || e.chassis == null || e.chassis.removed || !e.chassis.ready) {
                continue;
            }
            try {
                int n = e.def.wheels.size();
                double[][] pos = new double[n][];
                double[][] rot = new double[n][];
                double[] steer = new double[n];
                double[] spin = new double[n];
                double[] susp = new double[n];
                boolean[] contact = new boolean[n];
                RVec3 p = new RVec3();
                Quat q = new Quat();
                Vec3 right = new Vec3(1f, 0f, 0f);
                Vec3 wup = new Vec3(0f, 1f, 0f);
                for (int i = 0; i < n; i++) {
                    e.constraint.getWheelPositionAndRotation(i, right, wup, p, q);
                    pos[i] = new double[]{p.xx(), p.yy(), p.zz()};
                    rot[i] = new double[]{q.getX(), q.getY(), q.getZ(), q.getW()};
                    Wheel w = e.constraint.getWheel(i);
                    steer[i] = w.getSteerAngle();
                    spin[i] = w.getRotationAngle();
                    susp[i] = w.getSuspensionLength();
                    contact[i] = w.hasContact();
                }
                Vec3 vel = world.bodyInterface.getLinearVelocity(e.chassis.joltId);
                Quat cq = world.bodyInterface.getRotation(e.chassis.joltId);
                double[] fwd = RagdollBuilder.rotate(new double[]{cq.getX(), cq.getY(), cq.getZ(), cq.getW()}, e.def.forward);
                double speed = vel.getX() * fwd[0] + vel.getY() * fwd[1] + vel.getZ() * fwd[2];
                double rpm = e.controller.getEngine().getCurrentRpm();
                int gear = e.controller.getTransmission().getCurrentGear();
                e.telemetry = new VehicleTelemetry(speed, rpm, gear, pos, rot, steer, spin, susp, contact);
            }
            catch (Throwable t) {
                world.log.warning("Vehicle " + e.def.name + " update failed: " + t);
            }
        }
    }
}
