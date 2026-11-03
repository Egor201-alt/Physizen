package dev.egor201.physizen.core.model;

public final class VehicleTelemetry {

    public final double speed;
    public final double rpm;
    public final int gear;
    public final int wheelCount;
    public final double[][] wheelPos;
    public final double[][] wheelRot;
    public final double[] steerAngle;
    public final double[] spinAngle;
    public final double[] suspensionLength;
    public final boolean[] contact;

    public VehicleTelemetry(double speed, double rpm, int gear, double[][] wheelPos, double[][] wheelRot, double[] steerAngle,
            double[] spinAngle, double[] suspensionLength, boolean[] contact) {
        this.speed = speed;
        this.rpm = rpm;
        this.gear = gear;
        this.wheelCount = wheelPos.length;
        this.wheelPos = wheelPos;
        this.wheelRot = wheelRot;
        this.steerAngle = steerAngle;
        this.spinAngle = spinAngle;
        this.suspensionLength = suspensionLength;
        this.contact = contact;
    }
}
