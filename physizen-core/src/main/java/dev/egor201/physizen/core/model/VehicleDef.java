package dev.egor201.physizen.core.model;

import java.util.ArrayList;
import java.util.List;

public final class VehicleDef {

    public static final int MAX_WHEELS = 16;

    public static final class Wheel {

        public double[] position = new double[3];
        public double radius = 0.4;
        public double width = 0.25;
        public double suspensionMin = 0.2;
        public double suspensionMax = 0.5;
        public double suspensionFrequency = 1.5;
        public double suspensionDamping = 0.5;
        public double maxSteer = 0;
        public double maxBrakeTorque = 1500;
        public double maxHandBrakeTorque = 0;
        public int axle = 0;
        public boolean driven = false;
    }

    public String name;
    public String body;
    public double[] up = {0, 1, 0};
    public double[] forward = {0, 0, 1};
    public double maxPitchRoll = 70;
    public double engineTorque = 500;
    public double engineMinRpm = 1000;
    public double engineMaxRpm = 6000;
    public double[] gearRatios = {2.66, 1.78, 1.3, 1.0, 0.74};
    public double[] reverseGearRatios = {-2.9};
    public double differentialRatio = 3.42;
    public boolean automatic = true;
    public double limitedSlip = 1.4;
    public final List<Wheel> wheels = new ArrayList<>();

    public Wheel wheel(int index) {
        return wheels.get(index);
    }

    public static VehicleDef car(String name, String body, double hx, double hy, double hz, boolean frontDrive, boolean rearDrive) {
        VehicleDef d = new VehicleDef();
        d.name = name;
        d.body = body;
        double radius = Math.max(0.15, Math.min(hy * 1.2, 0.5));
        double y = -hy + radius * 0.4;
        double x = hx + 0.05;
        double z = Math.max(0.2, hz * 0.7);
        for (int i = 0; i < 4; i++) {
            Wheel w = new Wheel();
            boolean front = i < 2;
            boolean left = (i % 2) == 0;
            w.position = new double[]{left ? x : -x, y, front ? z : -z};
            w.radius = radius;
            w.width = Math.max(0.1, radius * 0.6);
            w.suspensionMin = radius * 0.5;
            w.suspensionMax = radius * 1.25;
            w.axle = front ? 0 : 1;
            w.maxSteer = front ? 30 : 0;
            w.driven = front ? frontDrive : rearDrive;
            w.maxHandBrakeTorque = front ? 0 : 4000;
            d.wheels.add(w);
        }
        return d;
    }

    public VehicleDef copy() {
        VehicleDef c = new VehicleDef();
        c.name = name;
        c.body = body;
        c.up = up.clone();
        c.forward = forward.clone();
        c.maxPitchRoll = maxPitchRoll;
        c.engineTorque = engineTorque;
        c.engineMinRpm = engineMinRpm;
        c.engineMaxRpm = engineMaxRpm;
        c.gearRatios = gearRatios.clone();
        c.reverseGearRatios = reverseGearRatios.clone();
        c.differentialRatio = differentialRatio;
        c.automatic = automatic;
        c.limitedSlip = limitedSlip;
        for (Wheel w : wheels) {
            Wheel n = new Wheel();
            n.position = w.position.clone();
            n.radius = w.radius;
            n.width = w.width;
            n.suspensionMin = w.suspensionMin;
            n.suspensionMax = w.suspensionMax;
            n.suspensionFrequency = w.suspensionFrequency;
            n.suspensionDamping = w.suspensionDamping;
            n.maxSteer = w.maxSteer;
            n.maxBrakeTorque = w.maxBrakeTorque;
            n.maxHandBrakeTorque = w.maxHandBrakeTorque;
            n.axle = w.axle;
            n.driven = w.driven;
            c.wheels.add(n);
        }
        return c;
    }
}
