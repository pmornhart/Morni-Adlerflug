package de.moinmorni.adlerflug.course;

import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import java.util.ArrayList;
import java.util.List;

/**
 * Piloten für die Prüfung einer Strecke: Ein einfacher Pilot fliegt wie ein Anfänger, der dem Leuchten folgt – direkt
 * auf den nächsten Ring zu, und durch die Mitte einer Aufwind-Säule, wenn sie vor dem Ring liegt.
 */
public final class Pilots {

    /** So viele Ticks schaut der Pilot beim Steigen und Sinken voraus. */
    private static final double LEAD = 10;

    private Pilots() {
    }

    /** Pilot, der auf die Ringe zielt und die Aufwind-Säulen mitnimmt. */
    public static FlightSim.Pilot aim(Course c) {
        List<double[]> vias = new ArrayList<>();
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
        return (feet, vel, next, tick) -> {
            if (next == null) {
                return new double[] {vel.yaw(), 0};
            }
            Vec target = next.center();
            for (double[] v : vias) {
                if (v[4] == 0 && (int) v[3] == next.index()) {
                    if (Math.hypot(feet.x() - v[0], feet.z() - v[2]) < 3) {
                        v[4] = 1;
                    } else {
                        target = new Vec(v[0], v[1], v[2]);
                    }
                    break;
                }
            }
            // Schwung nach oben/unten mit einrechnen (wie ein Mensch, der früh gegensteuert)
            Vec d = target.sub(Course.center(feet)).add(0, -vel.y() * LEAD, 0);
            double pitch = Math.max(-45, Math.min(60, d.pitch()));
            return new double[] {d.yaw(), pitch};
        };
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
}
