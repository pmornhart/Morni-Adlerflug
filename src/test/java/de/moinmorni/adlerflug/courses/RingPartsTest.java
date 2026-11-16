package de.moinmorni.adlerflug.courses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Vec;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Die Ringe aus Anzeige-Teilen: rund, in der Ring-Ebene, nichts ragt in die Öffnung. */
class RingPartsTest {

    @Test
    void ringsAreRoundAndLeaveTheOpeningFree() {
        for (Design d : Designs.all()) {
            Course c = d.course();
            for (Ring r : c.rings) {
                List<Scenery.RingPart> parts = Scenery.ringParts(r);
                assertTrue(parts.size() >= 80, c.id + " Ring " + r.index() + ": zu grob");
                for (Scenery.RingPart p : parts) {
                    // Achsen stehen senkrecht aufeinander und haben Länge 1
                    assertEquals(1, p.along().length(), 1e-6);
                    assertEquals(1, p.out().length(), 1e-6);
                    assertEquals(0, p.along().dot(p.out()), 1e-6);
                    assertEquals(0, p.along().dot(p.depth()), 1e-6);
                    // in der Ebene des Rings
                    assertEquals(0, p.at().dot(r.normal()), 1e-6);
                    // innere Kante liegt außerhalb dessen, was als Treffer zählt (man fliegt nie „durch“ ein Teil)
                    double inner = p.at().length() - p.thick() / 2;
                    assertTrue(inner >= r.radius() + 0.4, c.id + " Ring " + r.index() + ": Teil ragt hinein");
                    assertTrue(p.at().length() <= r.radius() + 2.5);
                    Blocks.known(p.block());
                }
            }
        }
    }

    @Test
    void neighbouringPartsCloseTheBand() {
        Ring r = Designs.all().get(0).course().rings.get(0);
        List<Scenery.RingPart> band = Scenery.ringParts(r).stream()
                .filter(p -> Math.abs(p.at().length() - (r.radius() + 0.95)) < 1e-6 && p.length() > p.thick() + 0.01)
                .toList();
        assertTrue(band.size() >= 40);
        Vec a = band.get(0).at();
        Vec b = band.get(1).at();
        assertTrue(band.get(0).length() >= a.distance(b), "Lücke zwischen zwei Teilen");
    }

    /** Nur Blocknamen von 26.x. */
    static final class Blocks {
        static void known(String block) {
            String id = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
            assertTrue(!id.equals("chain") && !id.equals("grass") && id.matches("[a-z_]+"), id);
        }
    }
}
