package de.moinmorni.adlerflug.course;

import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Gate;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Piloten für die Prüfung einer Strecke. Der einfache Pilot fliegt wie ein Anfänger, der dem Leuchten folgt – direkt
 * auf den nächsten Ring zu (bei bewegten Ringen dorthin, wo der Ring sein wird, wenn er ankommt), durch die Mitte einer
 * Aufwind-Säule, wenn sie vor dem Ring liegt, und vor einem geschlossenen Takt-Tor nimmt er etwas Tempo heraus. Geübte
 * Piloten schneiden Kurven an, stechen für Tempo tiefer und holen Bonus-Ringe – aus ihnen kommt der Steinadler.
 */
public final class Pilots {

    /** So viele Ticks schaut der Pilot beim Steigen und Sinken voraus. */
    private static final double LEAD = 10;
    /** Langsamer als das (Blöcke pro Tick) – dann drückt jeder Pilot die Nase herunter. */
    private static final double STALL = 0.95;
    /** Unterhalb dieses Abstands zielt auch der geübte Pilot genau auf den Ring. */
    private static final double NEAR = 22;

    private Pilots() {
    }

    /** Pilot, der auf die Ringe zielt und die Aufwind-Säulen mitnimmt. */
    public static FlightSim.Pilot aim(Course c) {
        return new Aim(c, LEAD, 0, 0, false);
    }

    /** Wie {@link #aim}, holt aber alle Bonus-Ringe (in der Reihenfolge der Strecke). */
    public static FlightSim.Pilot hunter(Course c) {
        return new Aim(c, LEAD, 0, 0, true);
    }

    /**
     * Geübter Pilot.
     *
     * @param lead wie weit er beim Steigen und Sinken vorausschaut (Ticks)
     * @param cut  wie weit (Anteil der Öffnung) er den Ring zur nächsten Kurve hin anschneidet
     * @param low  wie viel tiefer (Blöcke) er von weitem anfliegt, um Tempo zu holen
     * @param hunt Bonus-Ringe holen
     */
    public static FlightSim.Pilot expert(Course c, double lead, double cut, double low, boolean hunt) {
        return new Aim(c, lead, cut, low, hunt);
    }

    /** Alle geübten Piloten, die für den Steinadler antreten. */
    public static List<FlightSim.Pilot> experts(Course c) {
        List<FlightSim.Pilot> out = new ArrayList<>();
        for (boolean hunt : c.bonus.isEmpty() ? new boolean[] {false} : new boolean[] {false, true}) {
            for (double lead : new double[] {7, 10, 13}) {
                for (double cut : new double[] {0, 0.35, 0.6}) {
                    for (double low : new double[] {0, 2.5}) {
                        out.add(new Aim(c, lead, cut, low, hunt));
                    }
                }
            }
        }
        return out;
    }

    /** Ob der Pilot alle Ringe in der richtigen Reihenfolge getroffen hat. */
    public static boolean allRings(FlightSim.Result r) {
        for (int t : r.ringTicks()) {
            if (t < 0) {
                return false;
            }
        }
        return r.finished();
    }

    /** Für Ringe einer Strecke den Index des Rings vor dem gegebenen (−1 = Start). */
    public static int before(Ring r) {
        return r.index() - 1;
    }

    /** Der Pilot: zielt, sagt voraus, wartet an Toren, holt auf Wunsch Bonus-Ringe. */
    private static final class Aim implements FlightSim.Pilot {
        private final Course c;
        private final double lead;
        private final double cut;
        private final double low;
        private final boolean hunt;
        private final List<double[]> vias = new ArrayList<>();
        private final Set<Ring> got = new HashSet<>();

        Aim(Course c, double lead, double cut, double low, boolean hunt) {
            this.c = c;
            this.lead = lead;
            this.cut = cut;
            this.low = low;
            this.hunt = hunt;
            for (Updraft u : c.updrafts) {
                int best = 0;
                double d = Double.MAX_VALUE;
                for (int t = 0; t < c.pathTicks(); t++) {
                    Vec p = c.pathAt(t);
                    double h = Math.hypot(p.x() - u.x(), p.z() - u.z());
                    if (h < d) {
                        d = h;
                        best = t;
                    }
                }
                Vec p = c.pathAt(best);
                vias.add(new double[] {u.x(), p.y(), u.z(), c.pathRing(best), 0});
            }
        }

        @Override
        public void collected(Ring bonus) {
            got.add(bonus);
        }

        @Override
        public double[] steer(Vec feet, Vec vel, Ring next, int tick) {
            if (next == null) {
                return new double[] {vel.yaw(), 0};
            }
            Vec body = Course.center(feet);
            double now = tick - 1;
            Vec target = null;
            boolean exact = false;
            if (hunt) {
                for (Ring b : c.bonus) {
                    if (b.index() == next.index() && !got.contains(b) && b.side(body) < 0) {
                        target = predict(b, body, vel, now);
                        exact = true;
                        break;
                    }
                }
            }
            if (target == null) {
                for (double[] v : vias) {
                    if (v[4] == 0 && (int) v[3] == next.index()) {
                        if (Math.hypot(feet.x() - v[0], feet.z() - v[2]) < 3) {
                            v[4] = 1;
                        } else {
                            target = new Vec(v[0], v[1], v[2]);
                            exact = true;
                        }
                        break;
                    }
                }
            }
            double pitchBias = 0;
            if (target == null) {
                target = predict(next, body, vel, now);
                double dist = target.distance(body);
                if (cut > 0 && next.index() + 1 < c.rings.size() && next.gate() == null) {
                    Vec after = c.rings.get(next.index() + 1).center().sub(next.center());
                    Vec inPlane = after.sub(next.normal().mul(after.dot(next.normal())));
                    double room = Math.max(0, next.radius() - 1.0) * cut * Math.min(1, NEAR / Math.max(1, dist));
                    target = target.add(inPlane.normalize().mul(room));
                }
                if (low > 0 && dist > NEAR) {
                    target = target.add(0, -low * Math.min(1, (dist - NEAR) / 40), 0);
                }
                pitchBias = gateBias(next, body, vel, now, dist);
            }
            // Schwung nach oben/unten mit einrechnen (wie ein Mensch, der früh gegensteuert)
            Vec d = target.sub(body).add(0, -vel.y() * (exact ? LEAD : lead), 0);
            double pitch = Math.max(-45, Math.min(60, d.pitch() + pitchBias));
            // zu langsam: Nase runter, sonst fällt man durch (wie jeder, der schon einmal gegleitet ist)
            double speed = vel.length();
            if (speed < STALL && pitchBias == 0) {
                pitch = Math.max(pitch, (STALL - speed) * 45);
            }
            return new double[] {d.yaw(), pitch};
        }

        /** Wo der Ring sein wird, wenn der Pilot ankommt (bei festen Ringen: seine Mitte). */
        private static Vec predict(Ring r, Vec body, Vec vel, double now) {
            if (r.motion() == null) {
                return r.center();
            }
            double speed = Math.max(0.6, vel.length());
            Vec at = r.centerAt(now);
            for (int i = 0; i < 3; i++) {
                double eta = at.distance(body) / speed;
                at = r.centerAt(now + eta);
            }
            return at;
        }

        /** Vor einem Takt-Tor, das bei der Ankunft zu wäre: etwas hochziehen (langsamer werden). */
        private static double gateBias(Ring r, Vec body, Vec vel, double now, double dist) {
            Gate g = r.gate();
            if (g == null || dist < 8) {
                return 0;
            }
            double eta = dist / Math.max(0.6, vel.length());
            if (g.shut(now + eta) < 0.2) {
                return 0;
            }
            return -Math.min(14, 4 + g.untilOpen(now + eta) * 0.4);
        }
    }
}
