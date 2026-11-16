package de.moinmorni.adlerflug.course;

import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.RingCheck;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import java.util.ArrayList;
import java.util.List;

/**
 * Eine Flugstrecke: Startturm mit Absprung, Ringe der Reihe nach (der letzte ist das Ziel), Aufwind-Säulen, Landeplatz,
 * Medaillen-Zeiten und der Musterflug (Weg eines Piloten, der alle Ringe trifft – für Geist-Vorlage, Test-Spieler und
 * Medaillen). Entsteht im {@link CourseBuilder}, ohne Bukkit.
 */
public final class Course {

    /** Zeitgrenzen der Medaillen in ms. */
    public record Medals(long bronze, long silber, long gold, long adler) {
    }

    /**
     * Start: Stehplatz (Füße), Blickrichtung, Startbereich (wer ihn verlässt, fliegt los) und Schwung beim Absprung.
     */
    public record Start(Vec stand, float yaw, Box area, Vec launch, Vec line) {
    }

    /** Landeplatz nach dem Ziel (Füße, Blickrichtung). */
    public record Landing(Vec at, float yaw) {
    }

    public final String id;
    public final String nameDe;
    public final String nameEn;
    public final String textDe;
    public final String textEn;
    public final Difficulty difficulty;
    public final String icon;
    public final String biome;
    public final Start start;
    public final List<Ring> rings;
    public final List<Updraft> updrafts;
    public final Landing landing;
    public final Medals medals;
    /** Musterflug: Füße pro Tick ab Absprung (x, y, z hintereinander). */
    public final float[] path;
    /** Beste Zeit der Prüf-Piloten bis ins Ziel (Grundlage der Medaillen). */
    public final long designMs;
    /** Wie weit man vom Musterflug weg darf, bevor es zurück zum letzten Ring geht. */
    public final double maxOff;
    /** Wer tiefer fällt, ist abgestürzt (zurück zum letzten Ring). */
    public final double floorY;
    public final Box bounds;
    /** Zu jedem Punkt des Musterflugs: welcher Ring als nächstes kommt. */
    private final int[] pathRing;

    Course(String id, String nameDe, String nameEn, String textDe, String textEn, Difficulty difficulty, String icon,
           String biome, Start start, List<Ring> rings, List<Updraft> updrafts, Landing landing, Medals medals,
           float[] path, int[] pathRing, long designMs, double maxOff) {
        this.id = id;
        this.nameDe = nameDe;
        this.nameEn = nameEn;
        this.textDe = textDe;
        this.textEn = textEn;
        this.difficulty = difficulty;
        this.icon = icon;
        this.biome = biome;
        this.start = start;
        this.rings = List.copyOf(rings);
        this.updrafts = List.copyOf(updrafts);
        this.landing = landing;
        this.medals = medals;
        this.path = path;
        this.pathRing = pathRing;
        this.designMs = designMs;
        this.maxOff = maxOff;
        double minY = Double.MAX_VALUE;
        double x0 = Double.MAX_VALUE;
        double z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double y1 = -Double.MAX_VALUE;
        double z1 = -Double.MAX_VALUE;
        for (int i = 0; i + 2 < path.length; i += 3) {
            minY = Math.min(minY, path[i + 1]);
            x0 = Math.min(x0, path[i]);
            x1 = Math.max(x1, path[i]);
            y1 = Math.max(y1, path[i + 1]);
            z0 = Math.min(z0, path[i + 2]);
            z1 = Math.max(z1, path[i + 2]);
        }
        x0 = Math.min(x0, start.stand().x());
        x1 = Math.max(x1, start.stand().x());
        z0 = Math.min(z0, start.stand().z());
        z1 = Math.max(z1, start.stand().z());
        y1 = Math.max(y1, start.stand().y());
        this.floorY = minY - 30;
        this.bounds = new Box(x0, minY - 40, z0, x1, y1 + 30, z1);
    }

    public String name(String lang) {
        return "en".equals(lang) ? nameEn : nameDe;
    }

    public String text(String lang) {
        return "en".equals(lang) ? textEn : textDe;
    }

    public Medal medal(long ms) {
        return Medal.of(ms, medals);
    }

    public int ringCount() {
        return rings.size();
    }

    public Ring finish() {
        return rings.get(rings.size() - 1);
    }

    public int pathTicks() {
        return path.length / 3;
    }

    public Vec pathAt(int tick) {
        int i = Math.max(0, Math.min(pathTicks() - 1, tick)) * 3;
        return new Vec(path[i], path[i + 1], path[i + 2]);
    }

    /** Welcher Ring als nächstes kommt, wenn der Musterflug bei Tick {@code tick} ist. */
    public int pathRing(int tick) {
        return pathRing[Math.max(0, Math.min(pathTicks() - 1, tick))];
    }

    /** Musterflug als Punktliste (jeder {@code step}-te Tick). */
    public List<Vec> pathPoints(int step) {
        List<Vec> out = new ArrayList<>();
        for (int t = 0; t < pathTicks(); t += step) {
            out.add(pathAt(t));
        }
        out.add(pathAt(pathTicks() - 1));
        return out;
    }

    /**
     * Abstand eines Punkts zum Musterflug auf dem Stück vor dem nächsten Ring {@code next} (und etwas davor/danach).
     * So zählt nur das Stück der Strecke, auf dem man gerade ist – Schleifen der Strecke stören nicht.
     */
    public double offPath(Vec feet, int next) {
        double best = Double.POSITIVE_INFINITY;
        Vec prev = null;
        for (int t = 0; t < pathTicks(); t++) {
            int r = pathRing[t];
            if (r < next - 1 || r > next + 1) {
                prev = null;
                continue;
            }
            Vec p = pathAt(t);
            best = Math.min(best, prev == null ? feet.distance(p) : RingCheck.distanceToSegment(feet, prev, p));
            prev = p;
        }
        return best;
    }

    /** Erster Tick des Musterflugs kurz vor Ring {@code index} (für Übungs-Starts und zum Zurücksetzen). */
    public int tickBefore(int index, int ticksBefore) {
        for (int t = 0; t < pathTicks(); t++) {
            if (pathRing[t] >= index) {
                return Math.max(0, t - ticksBefore);
            }
        }
        return 0;
    }

    /** Weg vom Absprung bis ins Ziel für die Tests (Füße pro Tick). */
    public List<Vec> pathList() {
        List<Vec> out = new ArrayList<>(pathTicks());
        for (int t = 0; t < pathTicks(); t++) {
            out.add(pathAt(t));
        }
        return out;
    }

    /** Mitte des Körpers beim Gleiten. */
    public static Vec center(Vec feet) {
        return feet.add(0, FlightSim.CENTER, 0);
    }

    /** Liegt ein Punkt in einer Aufwind-Säule dieser Strecke? */
    public Updraft updraftAt(Vec center) {
        for (Updraft u : updrafts) {
            if (u.contains(center)) {
                return u;
            }
        }
        return null;
    }
}
