package dev.egor201.physizen.core;

public final class FieldDef {

    public enum Kind {
        RADIAL,
        WIND,
        VORTEX,
        DRAG,
        BUOYANCY,
        GRAVITY
    }

    public String name;
    public Kind kind = Kind.RADIAL;
    public double[] center = new double[3];
    public double[] direction = {0, 1, 0};
    public double radius = 0;
    public double strength = 1;
    public double surfaceY = 0;
    public double density = 1000;
    public double drag = 0.5;
    public boolean massIndependent = true;
    public int group = -1;
}
