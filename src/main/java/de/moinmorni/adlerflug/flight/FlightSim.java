package de.moinmorni.adlerflug.flight;

import java.util.ArrayList;
import java.util.List;

/**
 * Fliegt eine Strecke Tick für Tick wie im Spiel nach ({@link Glide}), mit Aufwind-Säulen und Schub-Ringen genau so,
 * wie der Server sie anwendet: erst bewegen, dann Ringe prüfen (Schub), dann Aufwind. Wer steuert, sagt ein
 * {@link Pilot}. Ergebnis: der Weg (Füße, ein Punkt pro Tick), wann welcher Ring kam, ob das Ziel erreicht wurde und was
 * schiefging.
 */
public final class FlightSim {

    /** Höhe der Mitte über den Füßen beim Gleiten (Spieler ist dann 0,6 Blöcke hoch). */
    public static final double CENTER = 0.3;

    /** Ist an dieser Stelle ein fester Block? */
    public interface Solid {
        boolean test(int x, int y, int z);
    }

    /** Wer lenkt: Gierwinkel und Neigung (Grad) für diesen Tick. */
    public interface Pilot {
        double[] steer(Vec feet, Vec velocity, Ring next, int tick);
    }

    /**
     * @param path      Füße, ein Punkt pro Tick (Index 0 = Absprung)
     * @param ringTicks Tick, in dem Ring i durchflogen wurde (−1 = nicht)
     * @param ringFrac  Anteil innerhalb dieses Ticks
     * @param finished  Ziel erreicht
     * @param problem   was schiefging (null = nichts)
     */
    public record Result(List<Vec> path, int[] ringTicks, double[] ringFrac, boolean finished, String problem) {

        /** Zeit bis Ring i (ms), wie die Stoppuhr des Servers sie misst (ab Absprung). */
        public long ringMs(int i) {
            return ringTicks[i] < 0 ? -1 : Math.round((ringTicks[i] - 1 + ringFrac[i]) * 50);
        }

        public long finishMs() {
            return ringMs(ringTicks.length - 1);
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
        List<Vec> path = new ArrayList<>();
        int[] ticks = new int[rings.size()];
        double[] frac = new double[rings.size()];
        java.util.Arrays.fill(ticks, -1);
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
                return new Result(path, ticks, frac, false, "t" + t + ": " + crash + " vor Ring " + (next + 1));
            }
            Vec c0 = pos.add(0, CENTER, 0);
            Vec c1 = to.add(0, CENTER, 0);
            pos = to;
            path.add(pos);
            if (target != null) {
                RingCheck.Result r = RingCheck.check(target, c0, c1);
                if (r.hit() == RingCheck.Hit.THROUGH) {
                    ticks[next] = t;
                    frac[next] = r.fraction();
                    vel = target.boost(vel);
                    next++;
                    if (target.finish()) {
                        return new Result(path, ticks, frac, true, null);
                    }
                } else if (r.hit() == RingCheck.Hit.MISSED) {
                    return new Result(path, ticks, frac, false, String.format(java.util.Locale.ROOT,
                            "t%d: Ring %d um %.1f verfehlt (Öffnung %.1f)", t, next + 1, r.distance(), target.radius()));
                }
            }
            for (Updraft u : updrafts) {
                if (u.contains(c1)) {
                    vel = u.lift(vel);
                }
            }
            if (pos.y() < -60) {
                return new Result(path, ticks, frac, false, "t" + t + ": abgestürzt vor Ring " + (next + 1));
            }
        }
        return new Result(path, ticks, frac, false, "zu lang unterwegs, Ring " + (next + 1) + " nicht erreicht");
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
            Vec d = next.center().sub(feet.add(0, CENTER, 0));
            // kurz vor dem Ring auf die Flugrichtung des Rings ausrichten, sonst direkt hin
            double yaw = d.yaw();
            double pitch = Math.max(-45, Math.min(60, d.pitch()));
            return new double[] {yaw, pitch};
        };
    }
}
