package de.moinmorni.adlerflug.flight;

/**
 * Erkennt, ob ein Flug durch einen Ring ging – aus zwei Positionen nacheinander (vorher, jetzt). Auch bei hoher
 * Geschwindigkeit (mehrere Blöcke pro Tick) fällt nichts durch: gerechnet wird mit der Strecke dazwischen, nicht mit
 * dem Punkt. Bewegte Ringe und Takt-Tore werden zu dem Bruchteil des Ticks geprüft, in dem die Ebene gekreuzt wurde.
 * Ohne Bukkit, mit Unit-Tests.
 */
public final class RingCheck {

    /** Ergebnis: durch, knapp daneben (Ebene gekreuzt, außerhalb), gegen ein geschlossenes Tor, oder nichts. */
    public enum Hit {
        THROUGH, MISSED, CLOSED, NONE
    }

    /**
     * @param hit      was passiert ist
     * @param fraction wo zwischen {@code from} und {@code to} die Ebene gekreuzt wurde (0 … 1), sonst −1
     * @param distance Abstand des Kreuzungspunkts zur Ringmitte (sonst unendlich)
     */
    public record Result(Hit hit, double fraction, double distance) {
        static final Result NONE = new Result(Hit.NONE, -1, Double.POSITIVE_INFINITY);
    }

    /** Wie weit neben dem Ring ein Kreuzen der Ebene noch als „verpasst“ zählt (weiter weg = einfach woanders). */
    public static final double MISS_RANGE = 30;

    private RingCheck() {
    }

    /** Wie {@link #check(Ring, Vec, Vec, double)} für Ringe, die sich nicht bewegen. */
    public static Result check(Ring ring, Vec from, Vec to) {
        return check(ring, from, to, 0);
    }

    /**
     * Prüft die Strecke {@code from} → {@code to} gegen den Ring. Gezählt wird nur in Flugrichtung (von der
     * Vorderseite zur Rückseite). {@code t0} ist die Flugzeit (Ticks seit dem Absprung) bei {@code from}, {@code to}
     * liegt einen Tick später.
     */
    public static Result check(Ring ring, Vec from, Vec to, double t0) {
        double a = ring.side(from);
        double b = ring.side(to);
        if (!(a < 0 && b >= 0)) {
            return Result.NONE;
        }
        double t = a / (a - b);
        Vec at = from.lerp(to, t);
        double when = t0 + t;
        double dist = at.distance(ring.centerAt(when));
        if (dist <= ring.hitRadius()) {
            return new Result(ring.passes(at, when) ? Hit.THROUGH : Hit.CLOSED, t, dist);
        }
        if (dist <= MISS_RANGE) {
            return new Result(Hit.MISSED, t, dist);
        }
        return new Result(Hit.NONE, t, dist);
    }

    /** Kürzester Abstand eines Punkts zur Strecke a–b. */
    public static double distanceToSegment(Vec p, Vec a, Vec b) {
        Vec ab = b.sub(a);
        double len2 = ab.dot(ab);
        if (len2 < 1e-9) {
            return p.distance(a);
        }
        double t = Math.max(0, Math.min(1, p.sub(a).dot(ab) / len2));
        return p.distance(a.add(ab.mul(t)));
    }
}
