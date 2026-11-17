package de.moinmorni.adlerflug.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Was in der Luft wirkt: Takt-Tore, bewegte Ringe, Bonus-Ringe, Wind in Böen. */
class AirTest {

    private static final Ring RING = new Ring(0, new Vec(0, 100, 0), new Vec(1, 0, 0), 4, Ring.Kind.NORMAL, 0, 0);

    @Test
    void gateIsWideOpenAtItsAnchorAndShutsInRhythm() {
        Gate g = new Gate(80, 52, 100);
        assertTrue(g.open(100));
        assertEquals(0, g.shut(100), 1e-9);
        assertEquals(RING.hitRadius(), g.gap(100, RING.hitRadius()), 1e-9);
        // offen ±26 Ticks um den Anker (Grenze in der Mitte des Gleitens), dann 28 Ticks zu
        assertTrue(g.open(100 - 25.9) && g.open(100 + 25.9));
        assertFalse(g.open(100 + 26.1));
        assertEquals(1, g.shut(100 + 40), 1e-9);
        assertEquals(0, g.untilOpen(100), 1e-9);
        assertEquals(80 - 52 - 14, g.untilOpen(100 + 40), 1e-9);
        // wiederholt sich im Takt
        assertEquals(g.shut(37), g.shut(37 + 80), 1e-9);
        assertEquals(g.shut(37), g.shut(37 - 160), 1e-9);
        // Flügel gleiten: halb zu genau an der Grenze
        assertEquals(0.5, g.shut(100 + 26), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new Gate(20, 18, 0));
    }

    @Test
    void closedGateBlocksAndTheGapCounts() {
        Ring gated = RING.gated(new Gate(80, 52, 0.5));
        // genau beim Anker mitten durch
        assertEquals(RingCheck.Hit.THROUGH, RingCheck.check(gated, new Vec(-1, 100, 0), new Vec(1, 100, 0), 0).hit());
        // eine halbe Runde später ist es zu
        assertEquals(RingCheck.Hit.CLOSED, RingCheck.check(gated, new Vec(-1, 100, 0), new Vec(1, 100, 0), 40).hit());
        // beim Schließen: nur noch durch die Mitte, nicht am Rand
        double closing = 0.5 + 26 - 2; // Flügel ein Viertel zu
        Vec across = gated.across();
        Vec edge = new Vec(0, 100, 0).add(across.mul(3.8));
        assertTrue(gated.passes(new Vec(0, 100, 0), closing));
        assertFalse(gated.passes(edge, closing));
    }

    @Test
    void movingRingsAreAtTheirCentreAtTheAnchor() {
        Motion slide = Motion.glide(new Vec(0, 0, 1), 2.5, 120, 50);
        assertEquals(0, slide.offset(50).length(), 1e-9);
        assertEquals(2.5, slide.offset(50 + 30).length(), 1e-9);
        assertEquals(0, slide.offset(50 + 60).length(), 1e-9);
        assertFalse(slide.circles());
        Motion round = Motion.circle(new Vec(0, 0, 1), new Vec(0, 1, 0), 2, 90, 10);
        assertEquals(0, round.offset(10).length(), 1e-9);
        assertTrue(round.circles());
        assertEquals(4, round.reach(), 1e-9);
        for (int t = 0; t < 90; t += 7) {
            // läuft auf einem Kreis um Mitte − b
            assertEquals(2, round.offset(t).sub(new Vec(0, -2, 0)).length(), 1e-9);
        }
        // Ringe bleiben in ihrer Ebene
        Ring r = RING.moving(Motion.glide(new Vec(1, 0, 1), 2, 60, 0));
        assertEquals(0, r.centerAt(15).sub(r.center()).dot(r.normal()), 1e-9);
    }

    @Test
    void movingRingIsCheckedWhereItIsWhenYouCrossIt() {
        Ring r = RING.moving(Motion.glide(new Vec(0, 0, 1), 3, 40, 0));
        // zur Zeit 10 ist der Ring 3 Blöcke zur Seite gerutscht: dort zählt der Durchflug, in der Mitte nicht mehr
        assertEquals(RingCheck.Hit.THROUGH, RingCheck.check(r, new Vec(-1, 100, 3), new Vec(1, 100, 3), 9.5).hit());
        assertEquals(RingCheck.Hit.THROUGH, RingCheck.check(r, new Vec(-1, 100, 0), new Vec(1, 100, 0), -0.5).hit());
        assertEquals(RingCheck.Hit.MISSED, RingCheck.check(r, new Vec(-1, 100, -4), new Vec(1, 100, -4), 9.5).hit());
    }

    @Test
    void windComesInGustsAndAddsUp() {
        Wind zone = new Wind(0, 0, 100, 0, 10, 50, 150, new Vec(0, 0, 0.01));
        Air air = new Air(List.of(RING), List.of(), List.of(), List.of(zone), new Vec(0.004, 0, 0));
        Vec v = new Vec(1, 0, 0);
        Vec in = new Vec(50, 100, 3);
        Vec out = new Vec(50, 100, 30);
        // zwischen den Böen nichts
        assertEquals(v, air.drift(in, v, Air.GUST - 1));
        // Böe: Zone und Wind des Tages zusammen, so stark wie GUST Ticks gleichmäßiger Wind
        Vec gust = air.drift(in, v, Air.GUST);
        assertEquals(0.04 + 1, gust.x(), 1e-9);
        assertEquals(0.1, gust.z(), 1e-9);
        // außerhalb der Zone nur der Wind des Tages
        assertEquals(0, air.drift(out, v, 2L * Air.GUST).z(), 1e-9);
        assertEquals(new Vec(0.04, 0, 0.1), air.push(in));
        assertFalse(Air.gust(0));
        assertEquals(36, Wind.kmh(new Vec(0, 0, 0.05)));
    }

    @Test
    void updraftStillLiftsEveryTick() {
        Updraft u = new Updraft(0, 0, 5, 50, 150, 1.2);
        Air air = new Air(List.of(), List.of(u));
        Vec v = new Vec(1, -0.4, 0);
        Vec lifted = air.drift(new Vec(0, 100, 0), v, 3);
        assertTrue(lifted.y() > v.y());
    }
}
