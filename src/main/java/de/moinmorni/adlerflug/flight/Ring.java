package de.moinmorni.adlerflug.flight;

/**
 * Ein Ring der Strecke: Mitte, Flugrichtung (Normale, Länge 1), Öffnung (Radius bis zum inneren Rand) und Art.
 * Pflicht-Ringe zählen nur der Reihe nach und nur in Flugrichtung; Bonus-Ringe liegen abseits der Linie, sind freiwillig
 * und schenken Zeit. Ein Ring kann gleiten oder kreisen ({@link Motion}) oder ein Takt-Tor sein ({@link Gate}) – beides
 * nach der Flugzeit seit dem Absprung, also für jeden gleich.
 *
 * @param index   Pflicht-Ringe: Nummer ab 0 (der letzte ist das Ziel); Bonus-Ringe: der Pflicht-Ring, vor dem sie liegen
 * @param center  Mitte (bei bewegten Ringen: dort, wo der Musterflug durchfliegt)
 * @param section Abschnitt der Strecke (bestimmt die Farbe)
 * @param power   Schub-Ringe: Geschwindigkeit danach (Blöcke pro Tick), sonst 0
 * @param motion  Bewegung oder null
 * @param gate    Takt-Tor oder null
 * @param credit  Bonus-Ringe: geschenkte Zeit in ms
 */
public record Ring(int index, Vec center, Vec normal, double radius, Kind kind, int section, double power, Motion motion,
                   Gate gate, long credit) {

    /** Art eines Rings. */
    public enum Kind {
        /** Normaler Pflicht-Ring mit Zwischenzeit. */
        NORMAL,
        /** Schub-Ring: gibt beim Durchfliegen Schwung nach vorn. */
        BOOST,
        /** Ziel. */
        FINISH,
        /** Freiwilliger Ring abseits der Linie: schenkt Zeit. */
        BONUS
    }

    /** Wie weit hinter dem Rand der Blöcke ein Flug noch zählt (Spieler sind 0,6 Blöcke breit). */
    public static final double TOLERANCE = 0.45;

    public Ring {
        normal = normal.normalize();
        if (motion != null) {
            motion = motion.flat(normal);
        }
    }

    public Ring(int index, Vec center, Vec normal, double radius, Kind kind, int section, double power) {
        this(index, center, normal, radius, kind, section, power, null, null, 0);
    }

    /** Radius, bis zu dem ein Durchflug zählt. */
    public double hitRadius() {
        return radius + TOLERANCE;
    }

    /** Abstand eines Punkts zur Ring-Ebene (positiv = schon durch). Bewegte Ringe bleiben in ihrer Ebene. */
    public double side(Vec p) {
        return p.sub(center).dot(normal);
    }

    /** Mitte zur Flugzeit {@code t} (Ticks seit dem Absprung). */
    public Vec centerAt(double t) {
        return motion == null ? center : center.add(motion.offset(t));
    }

    /** Waagerechte Richtung quer zum Ring (in seiner Ebene) – so gleiten die Flügel eines Takt-Tors. */
    public Vec across() {
        Vec u = Vec.UP.cross(normal);
        return u.length() < 1e-6 ? new Vec(1, 0, 0) : u.normalize();
    }

    /** Senkrecht zu {@link #across()} in der Ring-Ebene (meist „oben“). */
    public Vec up() {
        return normal.cross(across()).normalize();
    }

    /**
     * Kommt ein Flug, der die Ebene zur Zeit {@code t} am Punkt {@code at} kreuzt, durch? Am Rand gilt
     * {@link #TOLERANCE}; bei einem Takt-Tor muss man außerdem durch die Lücke zwischen den Flügeln.
     */
    public boolean passes(Vec at, double t) {
        Vec c = centerAt(t);
        if (at.distance(c) > hitRadius()) {
            return false;
        }
        if (gate == null) {
            return true;
        }
        double s = Math.abs(at.sub(c).dot(across()));
        return s < gate.gap(t, hitRadius());
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

    /** Freiwilliger Bonus-Ring? */
    public boolean bonus() {
        return kind == Kind.BONUS;
    }

    /** Bewegt sich der Ring oder hat er Flügel (braucht eine eigene Anzeige je Flieger)? */
    public boolean dynamic() {
        return motion != null || gate != null;
    }

    /** Derselbe Ring mit anderer Nummer und anderem Abschnitt. */
    public Ring renumber(int index, int section) {
        return new Ring(index, center, normal, radius, kind, section, power, motion, gate, credit);
    }

    /** Derselbe Ring mit Bewegung. */
    public Ring moving(Motion m) {
        return new Ring(index, center, normal, radius, kind, section, power, m, gate, credit);
    }

    /** Derselbe Ring als Takt-Tor. */
    public Ring gated(Gate g) {
        return new Ring(index, center, normal, radius, kind, section, power, motion, g, credit);
    }

    /** Derselbe Ring mit anderer Öffnung. */
    public Ring sized(double r) {
        return new Ring(index, center, normal, r, kind, section, power, motion, gate, credit);
    }
}
