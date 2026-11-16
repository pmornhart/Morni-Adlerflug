package de.moinmorni.adlerflug.course;

import de.moinmorni.adlerflug.flight.Vec;

/** Quader in Blöcken (Startbereich, Grenzen einer Strecke). */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public Box {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Quader verkehrt herum");
        }
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Vec p) {
        return contains(p.x(), p.y(), p.z());
    }

    /** Waagerecht drin (Höhe egal). */
    public boolean containsXZ(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public Box grow(double d) {
        return new Box(minX - d, minY - d, minZ - d, maxX + d, maxY + d, maxZ + d);
    }

    public Vec center() {
        return new Vec((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    /**
     * Wo zwischen {@code from} (drin) und {@code to} (draußen) der Quader verlassen wurde (0 … 1); −1, wenn
     * {@code from} nicht drin oder {@code to} nicht draußen ist.
     */
    public double exit(Vec from, Vec to) {
        if (!contains(from) || contains(to)) {
            return -1;
        }
        double t = 1;
        t = Math.min(t, leave(from.x(), to.x(), minX, maxX));
        t = Math.min(t, leave(from.y(), to.y(), minY, maxY));
        t = Math.min(t, leave(from.z(), to.z(), minZ, maxZ));
        return Math.max(0, t);
    }

    private static double leave(double a, double b, double lo, double hi) {
        if (b > hi && b != a) {
            return (hi - a) / (b - a);
        }
        if (b < lo && b != a) {
            return (lo - a) / (b - a);
        }
        return 1;
    }

    public static Box around(Vec a, Vec b) {
        return new Box(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }
}
