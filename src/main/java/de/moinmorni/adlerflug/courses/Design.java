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
 * steht). Im Stollen ({@code tunnel}) gibt es keine Schneise – dort bohrt der Bauplan den Weg frei.
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

    /** Punkte des Musterflugs je Abschnitt (ohne den Stollen). */
    public List<List<Vec>> cutPoints() {
        List<List<Vec>> out = new ArrayList<>();
        for (int i = 0; i < cuts.size(); i++) {
            int from = cuts.get(i).fromRing();
            int to = i + 1 < cuts.size() ? cuts.get(i + 1).fromRing() : Integer.MAX_VALUE;
            List<Vec> pts = new ArrayList<>();
            for (int t = 0; t < course.pathTicks(); t += 2) {
                int r = course.pathRing(t);
                Vec p = course.pathAt(t);
                if (r >= from && r < to && (tunnel == null || !tunnel.containsXZ(p.x(), p.z()))) {
                    pts.add(p);
                }
            }
            out.add(pts);
        }
        return out;
    }
}
