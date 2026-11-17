package de.moinmorni.adlerflug.flight;

/**
 * Takt-Tor: Zwei Flügel gleiten von links und rechts in den Ring und schließen ihn in einem festen Takt. Die Zeit ist
 * die Flugzeit seit dem Absprung – für jeden gleich. Zum Zeitpunkt {@code anchor} (wenn der Musterflug durchfliegt)
 * ist das Tor genau in der Mitte seiner offenen Zeit.
 *
 * <p>Was zählt, ist genau das, was man sieht: Die Flügel bewegen sich in {@link #SWING} Ticks auf bzw. zu (die Grenze
 * zwischen offen und zu liegt in der Mitte der Bewegung), und durch kommt nur, wer durch die Lücke zwischen den Flügeln
 * fliegt ({@link #gap}).
 *
 * @param period Dauer eines Takts in Ticks
 * @param open   davon offen (Ticks, von halb offen bis halb zu)
 * @param anchor Flugzeit, zu der das Tor am weitesten offen ist
 */
public record Gate(double period, double open, double anchor) {

    /** So lange (Ticks) gleiten die Flügel zu bzw. auf. */
    public static final double SWING = 8;

    public Gate {
        if (!(period > 0) || !(open > SWING) || period - open <= SWING) {
            throw new IllegalArgumentException("Takt-Tor: offen " + open + " von " + period);
        }
    }

    /** Wie weit im Takt (0 … period): 0 = Anfang der offenen Zeit (Flügel halb offen und gehen auf). */
    public double phase(double t) {
        double p = (t - anchor + open / 2) % period;
        return p < 0 ? p + period : p;
    }

    /** Wie weit die Flügel zu sind: 0 = ganz offen, 1 = ganz zu (dazwischen gleiten sie). */
    public double shut(double t) {
        double p = phase(t);
        double h = SWING / 2;
        if (p < h) {
            return 0.5 - p / SWING;
        }
        if (p < open - h) {
            return 0;
        }
        if (p < open + h) {
            return (p - (open - h)) / SWING;
        }
        if (p < period - h) {
            return 1;
        }
        return 1 - (p - (period - h)) / SWING;
    }

    /** Ist das Tor zur Flugzeit {@code t} offen (Flügel weniger als halb zu)? */
    public boolean open(double t) {
        return shut(t) < 0.5;
    }

    /**
     * Halbe Breite der Lücke zwischen den Flügeln zur Zeit {@code t}, gemessen von der Ringmitte: {@code reach} bei ganz
     * offenem Tor, 0 bei ganz geschlossenem.
     */
    public double gap(double t, double reach) {
        return (1 - shut(t)) * reach;
    }

    /** Wie viele Ticks, bis das Tor wieder offen ist (0, wenn es gerade offen ist). */
    public double untilOpen(double t) {
        double p = phase(t);
        return p < open ? 0 : period - p;
    }
}
