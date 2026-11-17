package de.moinmorni.adlerflug.flight;

/**
 * Windzone: ein waagerechter Schlauch entlang der Strecke (x0, z0) → (x1, z1) mit Radius {@code radius}, von
 * {@code bottom} bis {@code top}; wer darin fliegt, wird zur Seite gedrückt – im Mittel um {@code push} Blöcke pro Tick,
 * in Böen alle {@link Air#GUST} Ticks ({@link Air#drift}). Der Server rechnet genau so wie der Musterflug (erst bewegen,
 * dann Wind), zu sehen ist der Wind an Schlieren, die in Windrichtung ziehen.
 */
public record Wind(double x0, double z0, double x1, double z1, double radius, double bottom, double top, Vec push) {

    public boolean contains(Vec p) {
        if (p.y() < bottom || p.y() > top) {
            return false;
        }
        return distance(p.x(), p.z()) <= radius;
    }

    /** Waagerechter Abstand zur Mittellinie der Zone. */
    public double distance(double x, double z) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double len2 = dx * dx + dz * dz;
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - x0) * dx + (z - z0) * dz) / len2));
        return Math.hypot(x - (x0 + dx * t), z - (z0 + dz * t));
    }

    /** Stärke in km/h, wie man sie auf Tafeln liest (Drift, die sich im Gleitflug einstellt: etwa das Zehnfache). */
    public static int kmh(Vec push) {
        return (int) Math.round(push.horizontal() * 10 * 20 * 3.6);
    }

    /** Punkt auf der Mittellinie (t = 0 … 1). */
    public double[] along(double t) {
        return new double[] {x0 + (x1 - x0) * t, z0 + (z1 - z0) * t};
    }

    public double length() {
        return Math.hypot(x1 - x0, z1 - z0);
    }
}
