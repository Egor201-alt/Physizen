package dev.egor201.physizen.core.world;

import dev.egor201.physizen.core.model.Snapshot;

final class ContactGeometry {

    private static final double[] UP = {0, 1, 0};

    private ContactGeometry() {
    }

    static double[] unit(double x, double y, double z, double[] fallback) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-6) {
            return fallback;
        }
        return new double[]{x / len, y / len, z / len};
    }

    static double[] fallbackNormal(Snapshot self, Snapshot other, boolean otherIsTerrain) {
        if (otherIsTerrain) {
            return unit(-self.vx, -self.vy, -self.vz, UP);
        }
        return unit(self.px - other.px, self.py - other.py, self.pz - other.pz, UP);
    }

    static double[] boundaryPoint(Snapshot s, double[] dir) {
        double cx = (s.minX + s.maxX) * 0.5;
        double cy = (s.minY + s.maxY) * 0.5;
        double cz = (s.minZ + s.maxZ) * 0.5;
        double hx = (s.maxX - s.minX) * 0.5;
        double hy = (s.maxY - s.minY) * 0.5;
        double hz = (s.maxZ - s.minZ) * 0.5;
        double t = Double.MAX_VALUE;
        if (Math.abs(dir[0]) > 1.0e-6) {
            t = Math.min(t, hx / Math.abs(dir[0]));
        }
        if (Math.abs(dir[1]) > 1.0e-6) {
            t = Math.min(t, hy / Math.abs(dir[1]));
        }
        if (Math.abs(dir[2]) > 1.0e-6) {
            t = Math.min(t, hz / Math.abs(dir[2]));
        }
        if (t == Double.MAX_VALUE) {
            t = 0;
        }
        return new double[]{cx + dir[0] * t, cy + dir[1] * t, cz + dir[2] * t};
    }

    static double[] overlapPoint(Snapshot a, Snapshot b) {
        double x0 = Math.max(a.minX, b.minX);
        double x1 = Math.min(a.maxX, b.maxX);
        double y0 = Math.max(a.minY, b.minY);
        double y1 = Math.min(a.maxY, b.maxY);
        double z0 = Math.max(a.minZ, b.minZ);
        double z1 = Math.min(a.maxZ, b.maxZ);
        if (x0 <= x1 && y0 <= y1 && z0 <= z1) {
            return new double[]{(x0 + x1) * 0.5, (y0 + y1) * 0.5, (z0 + z1) * 0.5};
        }
        return new double[]{(a.px + b.px) * 0.5, (a.py + b.py) * 0.5, (a.pz + b.pz) * 0.5};
    }

    static double[] negate(double[] v) {
        return new double[]{-v[0], -v[1], -v[2]};
    }
}
