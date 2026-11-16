package de.moinmorni.adlerflug.flight;

/**
 * Aufwind-Säule: Wer hindurchfliegt, wird angehoben – die Steiggeschwindigkeit geht Tick für Tick in Richtung
 * {@link #rise} (Blöcke pro Tick). Der Server rechnet genau so (wie der Musterflug), mit Partikeln zum Sehen.
 *
 * @param bottom unterster Punkt (meist knapp über dem Boden)
 * @param top    oberster Punkt; darüber hebt nichts mehr
 */
public record Updraft(double x, double z, double radius, double bottom, double top, double rise) {

    /** Wie schnell die Steiggeschwindigkeit sich anpasst (Anteil pro Tick). */
    public static final double GRIP = 0.3;

    public boolean contains(Vec p) {
        return p.y() >= bottom && p.y() <= top && Math.hypot(p.x() - x, p.z() - z) <= radius;
    }

    /** Geschwindigkeit nach einem Tick in der Säule (nur nach oben, nie bremsend). */
    public Vec lift(Vec v) {
        double vy = v.y() + (rise - v.y()) * GRIP;
        return new Vec(v.x(), Math.max(v.y(), vy), v.z());
    }

    public Vec center(double y) {
        return new Vec(x, y, z);
    }
}
