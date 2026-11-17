package de.moinmorni.adlerflug.courses;

import de.moinmorni.adlerflug.course.Box;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.land.Terrain;
import java.util.ArrayList;
import java.util.List;

/**
 * Eine Strecke samt Landschaft: der Flug ({@link Course}), wie die Gegend geformt wird (vor den Flugschneisen), wie
 * die Schneise unter dem Flug aussieht ({@link Cut}, je Abschnitt) und welche Bauten dazukommen (nachdem die Landschaft
 * steht). Im Stollen bzw. in der Höhle ({@code tunnel}) gibt es keine Schneise – dort bohrt der Bauplan den Weg frei.
 */
public record Design(Course course, Shaper shaper, List<Cut> cuts, Builder builder, String accent, Box tunnel) {

    /**
     * Schneise ab Ring {@code fromRing} (Index, 0 = von Anfang an) bis zum nächsten Abschnitt.
     *
     * @param clearance Luft unter dem Musterflug
     * @param floor     halbe Breite des Talbodens
     * @param slope     Anstieg der Hänge (Blöcke pro Block)
     */
    public record Cut(int fromRing, double clearance, double floor, double slope) {
    }

    /** Formt die Gegend (Hochebenen, Kuppen, Seen, Oberflächen). */
    public interface Shaper {
        void shape(Terrain t);
    }

    /** Setzt die Bauten der Strecke (Hütten, Burg, Stollen …) in den Plan. */
    public interface Builder {
        void build(World w, Course c);
    }

    /** Eine Schneise für die ganze Strecke. */
    public static List<Cut> cut(double clearance, double floor, double slope) {
        return List.of(new Cut(0, clearance, floor, slope));
    }

    /** Dieselbe Strecke mit neu gerechneten Medaillen. */
    public Design withCourse(Course c) {
        return new Design(c, shaper, cuts, builder, accent, tunnel);
    }

    /**
     * Punkte des Musterflugs je Abschnitt (ohne den Stollen), dazu der Flug über die Bonus-Ringe (zum Abschnitt des
     * nächsten Punkts im Musterflug).
     */
    public List<List<Vec>> cutPoints() {
        List<List<Vec>> out = new ArrayList<>();
        for (int i = 0; i < cuts.size(); i++) {
            out.add(new ArrayList<>());
        }
        for (int t = 0; t < course.pathTicks(); t += 2) {
            add(out, course.pathAt(t), course.pathRing(t));
        }
        if (course.bonusPath != null) {
            int near = 0;
            for (int t = 0; t < course.bonusPathTicks(); t += 2) {
                Vec p = course.bonusPathAt(t);
                double best = Double.MAX_VALUE;
                for (int k = Math.max(0, near - 200); k < Math.min(course.pathTicks(), near + 400); k++) {
                    double d = p.distance(course.pathAt(k));
                    if (d < best) {
                        best = d;
                        near = k;
                    }
                }
                add(out, p, course.pathRing(near));
            }
        }
        return out;
    }

    private void add(List<List<Vec>> out, Vec p, int ring) {
        if (tunnel != null && tunnel.containsXZ(p.x(), p.z())) {
            return;
        }
        for (int i = cuts.size() - 1; i >= 0; i--) {
            if (ring >= cuts.get(i).fromRing()) {
                out.get(i).add(p);
                return;
            }
        }
    }
}
