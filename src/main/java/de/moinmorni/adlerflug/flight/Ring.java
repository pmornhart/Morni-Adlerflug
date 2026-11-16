package de.moinmorni.adlerflug.flight;

/**
 * Ein Ring der Strecke: Mitte, Flugrichtung (Normale, Länge 1), Öffnung (Radius bis zum inneren Rand) und Art.
 * Ringe zählen nur der Reihe nach und nur in Flugrichtung.
 *
 * @param index   Nummer ab 0 (der letzte Ring ist das Ziel)
 * @param section Abschnitt der Strecke (bestimmt die Farbe)
 * @param power   Schub-Ringe: Geschwindigkeit danach (Blöcke pro Tick), sonst 0
 */
public record Ring(int index, Vec center, Vec normal, double radius, Kind kind, int section, double power) {

    /** Art eines Rings. */
    public enum Kind {
        /** Normaler Ring mit Zwischenzeit. */
        NORMAL,
        /** Schub-Ring: gibt beim Durchfliegen Schwung nach vorn. */
        BOOST,
        /** Ziel. */
        FINISH
    }

    /** Wie weit hinter dem Rand der Blöcke ein Flug noch zählt (Spieler sind 0,6 Blöcke breit). */
    public static final double TOLERANCE = 0.45;

    public Ring {
        normal = normal.normalize();
    }

    /** Radius, bis zu dem ein Durchflug zählt. */
    public double hitRadius() {
        return radius + TOLERANCE;
    }

    /** Abstand eines Punkts zur Ring-Ebene (positiv = schon durch). */
    public double side(Vec p) {
        return p.sub(center).dot(normal);
    }

    /**
     * Geschwindigkeit nach einem Schub-Ring: in Flugrichtung des Rings, mindestens {@link #power} schnell (wer schon
     * schneller ist, behält sein Tempo). Andere Ringe ändern nichts.
     */
    public Vec boost(Vec v) {
        if (kind != Kind.BOOST || power <= 0) {
            return v;
        }
        return normal.mul(Math.max(power, v.length()));
    }

    /** Ist das Ziel? */
    public boolean finish() {
        return kind == Kind.FINISH;
    }
}
