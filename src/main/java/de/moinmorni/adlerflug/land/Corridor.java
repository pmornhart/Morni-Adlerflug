package de.moinmorni.adlerflug.land;

import de.moinmorni.adlerflug.flight.Vec;
import java.util.Arrays;
import java.util.List;

/**
 * Flugschneise einer Strecke: Unter dem Musterflug darf die Landschaft höchstens bis {@code y − clearance} reichen, zur
 * Seite steigt die erlaubte Höhe mit {@code slope} pro Block an (sanft = weites Tal, steil = Klamm). So ist nie ein Berg
 * im Weg, und die Täler folgen der Strecke. Als Raster mit Zwischenwerten (schnell genug für den Welt-Erzeuger).
 */
public final class Corridor {

    private static final int RES = 2;
    private static final double MAX_INFLUENCE = 260;
    /** Weiter weg werden die Hänge immer steiler (Trogtal): ab dieser Höhe wirkt die Schneise nicht mehr. */
    private static final double TOP = 330;
    /** Zusätzliche Krümmung der Hänge (Blöcke pro Block²) – so laufen die Talwände ohne Kante in die Berge über. */
    private static final double BEND = 0.009;
    private static final float NONE = Float.POSITIVE_INFINITY;

    private final int minX;
    private final int minZ;
    private final int w;
    private final int h;
    private final float[] ceil;
    private final float[] dist;
    private final float[] fly;
    private final double clearance;
    private final double lowestFlight;

    /**
     * @param path      Punkte des Musterflugs (alle paar Blöcke einer)
     * @param clearance so viel Luft bleibt unter dem Flug mindestens
     * @param floor     halbe Breite des Talbodens (dort liegt die Landschaft ganz unten)
     * @param slope     Anstieg der Hänge pro Block (0,5 = sanft, 4 = Felswand)
     */
    public Corridor(List<Vec> path, double clearance, double floor, double slope) {
        this.clearance = clearance;
        double low = Double.POSITIVE_INFINITY;
        for (Vec p : path) {
            low = Math.min(low, p.y());
        }
        this.lowestFlight = low;
        double lowest = Double.POSITIVE_INFINITY;
        for (Vec p : path) {
            lowest = Math.min(lowest, p.y() - clearance);
        }
        // so weit, bis die Talwand über die höchsten Berge reicht (sonst entstünde am Rand eine Stufe)
        double rise = Math.max(0, TOP - lowest);
        double reachOut = (-slope + Math.sqrt(slope * slope + 4 * BEND * rise)) / (2 * BEND);
        double influence = Math.min(MAX_INFLUENCE, floor + reachOut);
        double x0 = Double.MAX_VALUE;
        double z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double z1 = -Double.MAX_VALUE;
        for (Vec p : path) {
            x0 = Math.min(x0, p.x());
            z0 = Math.min(z0, p.z());
            x1 = Math.max(x1, p.x());
            z1 = Math.max(z1, p.z());
        }
        minX = (int) Math.floor(x0 - influence) - RES;
        minZ = (int) Math.floor(z0 - influence) - RES;
        w = (int) Math.ceil((x1 + influence - minX) / RES) + 2;
        h = (int) Math.ceil((z1 + influence - minZ) / RES) + 2;
        ceil = new float[w * h];
        dist = new float[w * h];
        fly = new float[w * h];
        Arrays.fill(ceil, NONE);
        Arrays.fill(dist, NONE);
        Arrays.fill(fly, NONE);
        int reach = (int) Math.ceil(influence / RES) + 1;
        for (Vec p : path) {
            int ci = (int) Math.round((p.x() - minX) / RES);
            int cj = (int) Math.round((p.z() - minZ) / RES);
            for (int i = Math.max(0, ci - reach); i <= Math.min(w - 1, ci + reach); i++) {
                double cx = minX + i * RES;
                double dx = cx - p.x();
                for (int j = Math.max(0, cj - reach); j <= Math.min(h - 1, cj + reach); j++) {
                    double cz = minZ + j * RES;
                    double d = Math.sqrt(dx * dx + (cz - p.z()) * (cz - p.z()));
                    if (d > influence) {
                        continue;
                    }
                    int k = i + j * w;
                    double e = Math.max(0, d - floor);
                    float c = (float) (p.y() - clearance + e * slope + e * e * BEND);
                    if (c < ceil[k]) {
                        ceil[k] = c;
                    }
                    if (d < dist[k]) {
                        dist[k] = (float) d;
                        fly[k] = (float) p.y();
                    }
                }
            }
        }
    }

    /** Höchste erlaubte Landschaft bei (x, z); unendlich, wenn die Schneise dort nicht wirkt. */
    public double ceiling(double x, double z) {
        return sample(ceil, x, z);
    }

    /** Waagerechter Abstand zum Musterflug (ungefähr); unendlich weit weg. */
    public double distance(double x, double z) {
        return sample(dist, x, z);
    }

    public double clearance() {
        return clearance;
    }

    /** Tiefster Punkt des Musterflugs in dieser Schneise. */
    public double lowestFlight() {
        return lowestFlight;
    }

    /** Flughöhe am nächsten Punkt des Musterflugs (unendlich, wenn die Schneise hier nicht wirkt). */
    public double flightNear(double x, double z) {
        return sample(fly, x, z);
    }

    private double sample(float[] grid, double x, double z) {
        double gx = (x - minX) / RES;
        double gz = (z - minZ) / RES;
        int i = (int) Math.floor(gx);
        int j = (int) Math.floor(gz);
        if (i < 0 || j < 0 || i + 1 >= w || j + 1 >= h) {
            return Double.POSITIVE_INFINITY;
        }
        double fx = gx - i;
        double fz = gz - j;
        float a = grid[i + j * w];
        float b = grid[i + 1 + j * w];
        float c = grid[i + (j + 1) * w];
        float d = grid[i + 1 + (j + 1) * w];
        if (a == NONE || b == NONE || c == NONE || d == NONE) {
            // Rand der Schneise: der kleinste bekannte Wert (sicher statt glatt)
            float m = Math.min(Math.min(a, b), Math.min(c, d));
            return m == NONE ? Double.POSITIVE_INFINITY : m;
        }
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }
}
