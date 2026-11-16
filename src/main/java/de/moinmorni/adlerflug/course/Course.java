package de.moinmorni.adlerflug.course;

import de.moinmorni.adlerflug.flight.Air;
import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.RingCheck;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.flight.Wind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eine Flugstrecke: Startturm mit Absprung, Pflicht-Ringe der Reihe nach (der letzte ist das Ziel), Bonus-Ringe abseits
 * der Linie, Aufwind-Säulen, Windzonen, Landeplatz, Medaillen-Zeiten und der Musterflug (Weg eines Piloten, der alle
 * Pflicht-Ringe trifft – für Geist-Vorlage, Test-Spieler und Medaillen). Entsteht im {@link CourseBuilder}, ohne Bukkit.
 */
public final class Course {

    /** Zeitgrenzen der Medaillen in ms. */
    public record Medals(long bronze, long silber, long gold, long adler, long steinadler) {
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
    /** Pflicht-Ringe der Reihe nach, der letzte ist das Ziel. */
    public final List<Ring> rings;
    /** Freiwillige Bonus-Ringe (schenken Zeit). */
    public final List<Ring> bonus;
    public final List<Updraft> updrafts;
    public final List<Wind> winds;
    /** Wind, der auf der ganzen Strecke drückt (Strecke des Tages), sonst null-lang. */
    public final Vec wind;
    public final Landing landing;
    public final Medals medals;
    /** Musterflug: Füße pro Tick ab Absprung (x, y, z hintereinander). */
    public final float[] path;
    /** Ein Flug, der alle Bonus-Ringe mitnimmt (für den Test-Autopiloten), oder null. */
    public final float[] bonusPath;
    /** Zeit des einfachen Prüf-Piloten bzw. des Musterflugs (die schnellere) bis ins Ziel. */
    public final long designMs;
    /** Beste Zeit der geübten Prüf-Piloten (Grundlage des Steinadlers). */
    public final long expertMs;
    /** Wie weit man vom Musterflug weg darf, bevor es zurück zum letzten Ring geht. */
    public final double maxOff;
    /** Wer tiefer fällt, ist abgestürzt (zurück zum letzten Ring). */
    public final double floorY;
    public final Box bounds;
    /** Strecke des Tages: Datum (z. B. „2026-11-16“), sonst null. */
    public final String day;
    /** Zu jedem Punkt des Musterflugs: welcher Ring als nächstes kommt. */
    private final int[] pathRing;
    private final Air air;

    @SuppressWarnings("checkstyle:ParameterNumber")
    Course(String id, String nameDe, String nameEn, String textDe, String textEn, Difficulty difficulty, String icon,
           String biome, Start start, List<Ring> rings, List<Ring> bonus, List<Updraft> updrafts, List<Wind> winds,
           Vec wind, Landing landing, Medals medals, float[] path, int[] pathRing, float[] bonusPath, long designMs,
           long expertMs, double maxOff, String day) {
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
        this.bonus = List.copyOf(bonus);
        this.updrafts = List.copyOf(updrafts);
        this.winds = List.copyOf(winds);
        this.wind = wind == null ? Vec.ZERO : wind;
        this.landing = landing;
        this.medals = medals;
        this.path = path;
        this.pathRing = pathRing;
        this.bonusPath = bonusPath;
        this.designMs = designMs;
        this.expertMs = expertMs;
        this.maxOff = maxOff;
        this.day = day;
        this.air = new Air(this.rings, this.bonus, this.updrafts, this.winds, this.wind);
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
        for (Ring b : this.bonus) {
            x0 = Math.min(x0, b.center().x());
            x1 = Math.max(x1, b.center().x());
            z0 = Math.min(z0, b.center().z());
            z1 = Math.max(z1, b.center().z());
            minY = Math.min(minY, b.center().y());
        }
        x0 = Math.min(x0, start.stand().x());
        x1 = Math.max(x1, start.stand().x());
        z0 = Math.min(z0, start.stand().z());
        z1 = Math.max(z1, start.stand().z());
        y1 = Math.max(y1, start.stand().y());
        this.floorY = minY - 30;
        this.bounds = new Box(x0, minY - 40, z0, x1, y1 + 30, z1);
    }

    /** Dieselbe Strecke mit anderen Medaillen-Zeiten. */
    Course withMedals(Medals m, long design, long expert) {
        return new Course(id, nameDe, nameEn, textDe, textEn, difficulty, icon, biome, start, rings, bonus, updrafts,
                winds, wind, landing, m, path, pathRing, bonusPath, design, expert, maxOff, day);
    }

    /** Dieselbe Strecke mit einem Bonus-Flug für den Test-Autopiloten. */
    Course withBonusPath(float[] p) {
        return new Course(id, nameDe, nameEn, textDe, textEn, difficulty, icon, biome, start, rings, bonus, updrafts,
                winds, wind, landing, medals, path, pathRing, p, designMs, expertMs, maxOff, day);
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

    /** Alles, was in der Luft wirkt (für Musterflug, Prüf-Piloten und Server gleich). */
    public Air air() {
        return air;
    }

    public int ringCount() {
        return rings.size();
    }

    public Ring finish() {
        return rings.get(rings.size() - 1);
    }

    /** Strecke des Tages? */
    public boolean daily() {
        return day != null;
    }

    public int pathTicks() {
        return path.length / 3;
    }

    public Vec pathAt(int tick) {
        return at(path, tick);
    }

    /** Punkt des Bonus-Flugs (oder des Musterflugs, wenn es keinen gibt). */
    public Vec bonusPathAt(int tick) {
        return at(bonusPath == null ? path : bonusPath, tick);
    }

    public int bonusPathTicks() {
        return (bonusPath == null ? path : bonusPath).length / 3;
    }

    private static Vec at(float[] p, int tick) {
        int n = p.length / 3;
        int i = Math.max(0, Math.min(n - 1, tick)) * 3;
        return new Vec(p[i], p[i + 1], p[i + 2]);
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
        if (bonusPath != null) {
            for (int t = 0; t < bonusPathTicks(); t += step) {
                out.add(bonusPathAt(t));
            }
        }
        return out;
    }

    /**
     * Abstand eines Punkts zum Musterflug auf dem Stück vor dem nächsten Ring {@code next} (und etwas davor/danach).
     * So zählt nur das Stück der Strecke, auf dem man gerade ist – Schleifen der Strecke stören nicht. Bonus-Ringe
     * (abseits der Linie) zählen mit: wer zu einem fliegt, ist nicht „zu weit weg“.
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
        for (Ring b : bonus) {
            if (b.index() >= next - 1 && b.index() <= next + 1) {
                best = Math.min(best, feet.distance(b.center()) - b.radius() - 6);
            }
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
        return air.updraftAt(center);
    }

    /** Pflicht-Ringe, die sich bewegen oder Flügel haben. */
    public List<Ring> dynamicRings() {
        List<Ring> out = new ArrayList<>();
        for (Ring r : rings) {
            if (r.dynamic()) {
                out.add(r);
            }
        }
        for (Ring r : bonus) {
            if (r.dynamic()) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * Fingerabdruck der Ringe (Lage, Größe, Art, Bewegung, Tor, Bonus, Wind): ändert sich eine Strecke, sind alte
     * Bestzeiten nicht mehr vergleichbar.
     */
    public String signature() {
        StringBuilder b = new StringBuilder(id);
        for (List<Ring> list : List.of(rings, bonus)) {
            for (Ring r : list) {
                b.append(String.format(Locale.ROOT, "|%s %.1f %.1f %.1f %.2f %s %.2f", r.kind(), r.center().x(),
                        r.center().y(), r.center().z(), r.radius(), r.motion() == null ? "-" : r.motion().reach() + "/"
                        + r.motion().period() + "/" + r.motion().circles(), r.power()));
                if (r.gate() != null) {
                    b.append(String.format(Locale.ROOT, " T%.0f/%.0f", r.gate().period(), r.gate().open()));
                }
                b.append(' ').append(r.credit());
            }
        }
        for (Updraft u : updrafts) {
            b.append(String.format(Locale.ROOT, "|U %.1f %.1f %.1f %.2f", u.x(), u.z(), u.radius(), u.rise()));
        }
        for (Wind w : winds) {
            b.append(String.format(Locale.ROOT, "|W %.1f %.1f %.1f %.1f %.1f %.0f %.0f %s", w.x0(), w.z0(), w.x1(), w.z1(),
                    w.radius(), w.bottom(), w.top(), w.push()));
        }
        b.append("|S ").append(start.line()).append(' ').append(start.launch());
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(b.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", h[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
