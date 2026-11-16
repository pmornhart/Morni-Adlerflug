package de.moinmorni.adlerflug.time;

import java.util.List;

/** Zwischenzeiten an Ringen im Vergleich zur eigenen Bestzeit. */
public final class Splits {

    private Splits() {
    }

    /**
     * Abstand zur Zwischenzeit der Bestzeit an Ring {@code index} (0 = erster). null, wenn es keine Vergleichszeit
     * gibt. Negativ = schneller (grün), positiv = langsamer (rot).
     */
    public static Long delta(List<Long> best, int index, long now) {
        if (best == null || index < 0 || index >= best.size() || best.get(index) == null || best.get(index) <= 0) {
            return null;
        }
        return now - best.get(index);
    }

    /** Farbe für den Abstand: grün schneller, rot langsamer, gelb gleich. */
    public static String colour(long delta) {
        if (delta < 0) {
            return "#5fd068";
        }
        return delta > 0 ? "#ff6b5b" : "#f5c542";
    }
}
