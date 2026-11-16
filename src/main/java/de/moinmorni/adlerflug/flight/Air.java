package de.moinmorni.adlerflug.flight;

import java.util.List;

/**
 * Alles, was auf einer Strecke in der Luft wirkt: Pflicht-Ringe (der Reihe nach, der letzte ist das Ziel),
 * freiwillige Bonus-Ringe, Aufwind-Säulen, Windzonen und der Wind des Tages (überall gleich). Der Musterflug, die
 * Prüf-Piloten und der Server rechnen damit genau gleich ({@link #drift}).
 *
 * @param wind Wind, der überall drückt (Blöcke pro Tick, meist null-lang)
 */
public record Air(List<Ring> rings, List<Ring> bonus, List<Updraft> updrafts, List<Wind> winds, Vec wind) {

    public Air {
        rings = List.copyOf(rings);
        bonus = List.copyOf(bonus);
        updrafts = List.copyOf(updrafts);
        winds = List.copyOf(winds);
        wind = wind == null ? Vec.ZERO : wind;
    }

    /** Nur Ringe und Aufwind (wie früher). */
    public Air(List<Ring> rings, List<Updraft> updrafts) {
        this(rings, List.of(), updrafts, List.of(), Vec.ZERO);
    }

    /** Alle so viele Ticks kommt eine Böe (Wind und Wind des Tages). */
    public static final int GUST = 10;

    /** Kommt im Flug-Tick {@code tick} (seit dem Absprung) eine Böe? */
    public static boolean gust(long tick) {
        return tick > 0 && tick % GUST == 0;
    }

    /**
     * Geschwindigkeit nach dem Flug-Tick {@code tick} (seit dem Absprung) an dieser Stelle (Körpermitte): Aufwind hebt
     * jeden Tick an, Windzonen und der Wind des Tages drücken bei jeder Böe ({@link #gust}). Wird nach der Bewegung
     * angewandt und wirkt ab dem nächsten Tick.
     */
    public Vec drift(Vec center, Vec v, long tick) {
        for (Updraft u : updrafts) {
            if (u.contains(center)) {
                v = u.lift(v);
            }
        }
        if (!gust(tick)) {
            return v;
        }
        return v.add(push(center));
    }

    /** Stoß einer Böe an dieser Stelle (Windzonen und Wind des Tages zusammen), Nullvektor bei Windstille. */
    public Vec push(Vec center) {
        Vec p = wind.mul(GUST);
        for (Wind w : winds) {
            if (w.contains(center)) {
                p = p.add(w.push().mul(GUST));
            }
        }
        return p;
    }

    /** Aufwind-Säule an dieser Stelle (oder null). */
    public Updraft updraftAt(Vec center) {
        for (Updraft u : updrafts) {
            if (u.contains(center)) {
                return u;
            }
        }
        return null;
    }

    /** Windzone an dieser Stelle (oder null). */
    public Wind windAt(Vec center) {
        for (Wind w : winds) {
            if (w.contains(center)) {
                return w;
            }
        }
        return null;
    }

    /** Dieselbe Luft mit anderem Wind des Tages. */
    public Air withWind(Vec w) {
        return new Air(rings, bonus, updrafts, winds, w);
    }
}
