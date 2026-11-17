package de.moinmorni.adlerflug.flight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Fliegt eine Strecke Tick für Tick wie im Spiel nach ({@link Glide}), mit allem, was in der Luft wirkt ({@link Air}),
 * genau so, wie der Server es anwendet: erst bewegen, dann Ringe prüfen (Schub, Bonus, Reihenfolge), dann Aufwind und
 * Wind. Bewegte Ringe und Takt-Tore laufen nach der Flugzeit seit dem Absprung. Wer steuert, sagt ein {@link Pilot}.
 * Ergebnis: der Weg (Füße, ein Punkt pro Tick), wann welcher Ring kam, die geschenkte Zeit der Bonus-Ringe, ob das Ziel
 * erreicht wurde und was schiefging.
 */
public final class FlightSim {

    /** Höhe der Mitte über den Füßen beim Gleiten (Spieler ist dann 0,6 Blöcke hoch). */
    public static final double CENTER = 0.3;
    /** So viele Ringe nach dem nächsten prüft der Server auf „falsche Reihenfolge“. */
    public static final int ORDER_LOOKAHEAD = 3;

    /** Ist an dieser Stelle ein fester Block? */
    public interface Solid {
        boolean test(int x, int y, int z);
    }

    /** Wer lenkt: Gierwinkel und Neigung (Grad) für diesen Tick. */
    public interface Pilot {
        double[] steer(Vec feet, Vec velocity, Ring next, int tick);

        /** Ein Bonus-Ring wurde eingesammelt (für Piloten, die Bonus-Ringe anfliegen). */
        default void collected(Ring bonus) {
        }
    }

    /**
     * @param path      Füße, ein Punkt pro Tick (Index 0 = Absprung)
     * @param ringTicks Tick, in dem Pflicht-Ring i durchflogen wurde (−1 = nicht)
     * @param ringFrac  Anteil innerhalb dieses Ticks
     * @param collected welche Bonus-Ringe eingesammelt wurden
     * @param credit    geschenkte Zeit der Bonus-Ringe (ms)
     * @param finished  Ziel erreicht
     * @param problem   was schiefging (null = nichts)
     */
    public record Result(List<Vec> path, int[] ringTicks, double[] ringFrac, boolean[] collected, long credit,
                         boolean finished, String problem) {

        /** Zeit bis Ring i (ms), wie die Stoppuhr des Servers sie misst (ab Absprung, ohne Bonus). */
        public long ringMs(int i) {
            return ringTicks[i] < 0 ? -1 : Math.round((ringTicks[i] - 1 + ringFrac[i]) * 50);
        }

        /** Zeit bis ins Ziel ohne Bonus. */
        public long finishMs() {
            return ringMs(ringTicks.length - 1);
        }

        /** Gewertete Zeit: bis ins Ziel, abzüglich der Bonus-Ringe. */
        public long scoreMs() {
            long raw = finishMs();
            return raw < 0 ? raw : Math.max(1, raw - credit);
        }

        /** Wie viele Bonus-Ringe eingesammelt wurden. */
        public int bonusCount() {
            int n = 0;
            for (boolean b : collected) {
                n += b ? 1 : 0;
            }
            return n;
        }
    }

    private FlightSim() {
    }

    /**
     * @param feet   Füße beim Absprung
     * @param launch Geschwindigkeit beim Absprung (Blöcke pro Tick)
     */
    public static Result fly(Vec feet, Vec launch, List<Ring> rings, List<Updraft> updrafts, Pilot pilot, Solid solid,
                             int maxTicks) {
        return fly(feet, launch, new Air(rings, updrafts), pilot, solid, maxTicks);
    }

    public static Result fly(Vec feet, Vec launch, Air air, Pilot pilot, Solid solid, int maxTicks) {
        List<Ring> rings = air.rings();
        List<Vec> path = new ArrayList<>();
        int[] ticks = new int[rings.size()];
        double[] frac = new double[rings.size()];
        boolean[] got = new boolean[air.bonus().size()];
        long credit = 0;
        Arrays.fill(ticks, -1);
        Vec pos = feet;
        Vec vel = launch;
        path.add(pos);
        int next = 0;
        for (int t = 1; t <= maxTicks; t++) {
            Ring target = next < rings.size() ? rings.get(next) : null;
            double[] look = pilot.steer(pos, vel, target, t);
            vel = Glide.step(vel, look[0], look[1]);
            Vec to = pos.add(vel);
            String crash = solid == null ? null : collide(pos, to, solid);
            if (crash != null) {
                path.add(to);
                return new Result(path, ticks, frac, got, credit, false, "t" + t + ": " + crash + " vor Ring " + (next + 1));
            }
            Vec c0 = pos.add(0, CENTER, 0);
            Vec c1 = to.add(0, CENTER, 0);
            double t0 = t - 1;
            pos = to;
            path.add(pos);
            if (target != null) {
                String wrong = order(rings, next, c0, c1, t0);
                if (wrong != null) {
                    return new Result(path, ticks, frac, got, credit, false, "t" + t + ": " + wrong);
                }
                RingCheck.Result r = RingCheck.check(target, c0, c1, t0);
                switch (r.hit()) {
                    case THROUGH -> {
                        ticks[next] = t;
                        frac[next] = r.fraction();
                        vel = target.boost(vel);
                        next++;
                        if (target.finish()) {
                            return new Result(path, ticks, frac, got, credit, true, null);
                        }
                    }
                    case MISSED -> {
                        return new Result(path, ticks, frac, got, credit, false, String.format(Locale.ROOT,
                                "t%d: Ring %d um %.1f verfehlt (Öffnung %.1f)", t, next + 1, r.distance(), target.radius()));
                    }
                    case CLOSED -> {
                        return new Result(path, ticks, frac, got, credit, false, String.format(Locale.ROOT,
                                "t%d: Takt-Tor %d war zu", t, next + 1));
                    }
                    default -> {
                    }
                }
            }
            for (int i = 0; i < got.length; i++) {
                if (!got[i] && RingCheck.check(air.bonus().get(i), c0, c1, t0).hit() == RingCheck.Hit.THROUGH) {
                    got[i] = true;
                    credit += air.bonus().get(i).credit();
                    pilot.collected(air.bonus().get(i));
                }
            }
            vel = air.drift(c1, vel, t);
            if (pos.y() < -60) {
                return new Result(path, ticks, frac, got, credit, false, "t" + t + ": abgestürzt vor Ring " + (next + 1));
            }
        }
        return new Result(path, ticks, frac, got, credit, false, "zu lang unterwegs, Ring " + (next + 1) + " nicht erreicht");
    }

    /**
     * Ging der Flug durch einen späteren Pflicht-Ring, bevor der nächste dran war? Dann stimmt die Reihenfolge nicht
     * (Text), sonst null. Geprüft werden die nächsten {@link #ORDER_LOOKAHEAD} Ringe nach dem fälligen.
     */
    public static String order(List<Ring> rings, int next, Vec c0, Vec c1, double t0) {
        for (int k = next + 1; k <= Math.min(rings.size() - 1, next + ORDER_LOOKAHEAD); k++) {
            if (RingCheck.check(rings.get(k), c0, c1, t0).hit() == RingCheck.Hit.THROUGH) {
                return "falsche Reihenfolge: Ring " + (k + 1) + " vor Ring " + (next + 1);
            }
        }
        return null;
    }

    /** Stößt der Körper (0,6 × 0,6 × 0,6) auf dem Weg von a nach b gegen einen festen Block? */
    static String collide(Vec a, Vec b, Solid solid) {
        double len = a.distance(b);
        int steps = Math.max(1, (int) Math.ceil(len / 0.4));
        for (int i = 1; i <= steps; i++) {
            Vec p = a.lerp(b, (double) i / steps);
            for (double dx : new double[] {-0.29, 0.29}) {
                for (double dz : new double[] {-0.29, 0.29}) {
                    for (double dy : new double[] {0.01, 0.59}) {
                        int x = (int) Math.floor(p.x() + dx);
                        int y = (int) Math.floor(p.y() + dy);
                        int z = (int) Math.floor(p.z() + dz);
                        if (solid.test(x, y, z)) {
                            return "gegen Block " + x + " " + y + " " + z;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Pilot, der direkt auf die Mitte des nächsten Rings zielt (wie ein Anfänger, der dem Leuchten folgt). */
    public static Pilot aimAtRings() {
        return (feet, vel, next, tick) -> {
            if (next == null) {
                return new double[] {vel.yaw(), 0};
            }
            Vec d = next.centerAt(tick - 1).sub(feet.add(0, CENTER, 0));
            double yaw = d.yaw();
            double pitch = Math.max(-45, Math.min(60, d.pitch()));
            return new double[] {yaw, pitch};
        };
    }
}
