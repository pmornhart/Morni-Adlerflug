package de.moinmorni.adlerflug.flight;

/** Punkt oder Richtung im Raum (Blöcke). Unveränderlich, ohne Bukkit. */
public record Vec(double x, double y, double z) {

    public static final Vec ZERO = new Vec(0, 0, 0);
    public static final Vec UP = new Vec(0, 1, 0);

    public Vec add(Vec o) {
        return new Vec(x + o.x, y + o.y, z + o.z);
    }

    public Vec add(double dx, double dy, double dz) {
        return new Vec(x + dx, y + dy, z + dz);
    }

    public Vec sub(Vec o) {
        return new Vec(x - o.x, y - o.y, z - o.z);
    }

    public Vec mul(double f) {
        return new Vec(x * f, y * f, z * f);
    }

    public double dot(Vec o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec cross(Vec o) {
        return new Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** Abstand in der Waagerechten. */
    public double horizontal() {
        return Math.sqrt(x * x + z * z);
    }

    public double distance(Vec o) {
        return sub(o).length();
    }

    public double horizontalDistance(Vec o) {
        return Math.hypot(x - o.x, z - o.z);
    }

    /** Auf Länge 1 (Nullvektor bleibt null). */
    public Vec normalize() {
        double l = length();
        return l < 1e-9 ? ZERO : mul(1 / l);
    }

    /** Punkt zwischen diesem und {@code o} (t = 0 … 1). */
    public Vec lerp(Vec o, double t) {
        return new Vec(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    /** Minecraft-Blickwinkel (Gierwinkel): 0 = Süden (+z), 90 = Westen (−x). */
    public float yaw() {
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    /** Minecraft-Neigung: positiv = nach unten. */
    public float pitch() {
        return (float) -Math.toDegrees(Math.atan2(y, horizontal()));
    }

    /** Blickrichtung wie im Spiel aus Gierwinkel und Neigung (Grad). */
    public static Vec look(double yawDeg, double pitchDeg) {
        double p = Math.toRadians(pitchDeg);
        double g = Math.toRadians(-yawDeg);
        double cosP = Math.cos(p);
        return new Vec(Math.sin(g) * cosP, -Math.sin(p), Math.cos(g) * cosP);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", x, y, z);
    }
}
