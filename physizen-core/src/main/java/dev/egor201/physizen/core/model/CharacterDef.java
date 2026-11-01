package dev.egor201.physizen.core.model;

public final class CharacterDef {

    public enum Mode {
        DRIVEN,
        TRACK
    }

    public String name;
    public ShapeSpec shape = ShapeSpec.capsule(0.3, 1.8);
    public double[] position = new double[3];
    public double[] rotation = {0, 0, 0, 1};
    public double[] up = {0, 1, 0};
    public Mode mode = Mode.DRIVEN;
    public double mass = 70;
    public double maxStrength = 100;
    public double maxSlopeDegrees = 50;
    public double padding = 0.02;
    public double penetrationRecoverySpeed = 1.0;
    public double predictiveContactDistance = 0.1;
    public double gravityFactor = 1;
    public double stepHeight = 0.6;
    public double stickDistance = 0.5;
    public boolean walkStairs = true;
    public boolean stickToFloor = true;
    public boolean pushBodies = true;
    public boolean innerBody = true;
    public int group = 0;
    public boolean notify = true;
    public boolean managed = false;
    public String tag = "";

    public double supportRadius() {
        double[] half = shape.halfExtentsEstimate();
        return Math.max(0.01, Math.min(half[0], half[2]));
    }

    public double footOffset() {
        return shape.halfExtentsEstimate()[1];
    }
}
