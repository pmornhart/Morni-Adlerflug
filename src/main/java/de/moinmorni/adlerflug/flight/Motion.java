package de.moinmorni.adlerflug.flight;

/**
 * Bewegung eines Rings in seiner eigenen Ebene: hin und her gleiten (eine Achse) oder kreisen (zwei Achsen). Die Zeit
 * ist die Flugzeit in Ticks seit dem Absprung – jeder fängt mit derselben Bewegung an, darum ist es für alle gleich.
 *
 * <p>Zum Zeitpunkt {@link #anchor} steht der Ring genau an seiner Mitte (dort, wo der Musterflug durchfliegt); davor und
 * danach ist er um {@link #offset} verschoben: {@code a·sin(ω(t−anchor)) + b·(cos(ω(t−anchor)) − 1)}. Gleiten: nur
 * {@code a}. Kreisen: {@code a} und {@code b} gleich lang und senkrecht zueinander – der Ring läuft auf einem Kreis
 * um {@code Mitte − b}.
 *
 * @param a      erste Achse (Länge = Ausschlag in Blöcken)
 * @param b      zweite Achse (Nullvektor beim Gleiten)
 * @param period Dauer einer ganzen Bewegung in Ticks
 * @param anchor Flugzeit (Ticks), zu der der Ring an seiner Mitte steht
 */
public record Motion(Vec a, Vec b, double period, double anchor) {

    public Motion {
        if (!(period > 0)) {
            throw new IllegalArgumentException("Bewegung braucht eine Dauer");
        }
    }

    /** Hin und her gleiten entlang {@code axis} (Länge egal) mit Ausschlag {@code amplitude}. */
    public static Motion glide(Vec axis, double amplitude, double period, double anchor) {
        return new Motion(axis.normalize().mul(amplitude), Vec.ZERO, period, anchor);
    }

    /** Kreisen in der Ebene aus {@code u} und {@code v} (senkrecht zueinander) mit Radius {@code amplitude}. */
    public static Motion circle(Vec u, Vec v, double amplitude, double period, double anchor) {
        return new Motion(u.normalize().mul(amplitude), v.normalize().mul(amplitude), period, anchor);
    }

    /** Verschiebung gegenüber der Mitte zur Flugzeit {@code t} (Ticks). */
    public Vec offset(double t) {
        double w = 2 * Math.PI * (t - anchor) / period;
        return a.mul(Math.sin(w)).add(b.mul(Math.cos(w) - 1));
    }

    /** Kreist der Ring (sonst gleitet er)? */
    public boolean circles() {
        return b.length() > 1e-9;
    }

    /** Größter Abstand von der Mitte, den der Ring erreicht. */
    public double reach() {
        return circles() ? 2 * b.length() : a.length();
    }

    /** Dieselbe Bewegung, auf die Ebene mit Normale {@code n} gelegt (Ringe bewegen sich nie aus ihrer Ebene). */
    Motion flat(Vec n) {
        return new Motion(a.sub(n.mul(a.dot(n))), b.sub(n.mul(b.dot(n))), period, anchor);
    }
}
