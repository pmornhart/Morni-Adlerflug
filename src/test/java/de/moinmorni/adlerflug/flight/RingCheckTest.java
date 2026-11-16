package de.moinmorni.adlerflug.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Ring-Erkennung aus zwei Positionen nacheinander. */
class RingCheckTest {

    private static final Ring RING = new Ring(0, new Vec(0, 100, 0), new Vec(1, 0, 0), 5, Ring.Kind.NORMAL, 0, 0);

    @Test
    void throughTheMiddleCountsWithFraction() {
        RingCheck.Result r = RingCheck.check(RING, new Vec(-1, 100, 0), new Vec(3, 100, 0));
        assertEquals(RingCheck.Hit.THROUGH, r.hit());
        assertEquals(0.25, r.fraction(), 1e-9);
        assertEquals(0, r.distance(), 1e-9);
    }

    @Test
    void fastFlightsDoNotSlipThrough() {
        // 12 Blöcke in einem Tick (viel schneller als jeder Sturzflug), schräg durch den Rand der Öffnung
        RingCheck.Result r = RingCheck.check(RING, new Vec(-6, 104, 2), new Vec(6, 103, 1));
        assertEquals(RingCheck.Hit.THROUGH, r.hit());
    }

    @Test
    void onlyInFlightDirection() {
        assertEquals(RingCheck.Hit.NONE, RingCheck.check(RING, new Vec(3, 100, 0), new Vec(-1, 100, 0)).hit());
        assertEquals(RingCheck.Hit.NONE, RingCheck.check(RING, new Vec(-3, 100, 0), new Vec(-1, 100, 0)).hit());
        assertEquals(RingCheck.Hit.NONE, RingCheck.check(RING, new Vec(1, 100, 0), new Vec(3, 100, 0)).hit());
    }

    @Test
    void besideTheRingIsMissedFarAwayIsNothing() {
        RingCheck.Result miss = RingCheck.check(RING, new Vec(-1, 100, 8), new Vec(1, 100, 8));
        assertEquals(RingCheck.Hit.MISSED, miss.hit());
        assertEquals(8, miss.distance(), 1e-9);
        // knapp innerhalb der Toleranz (Spieler ist 0,6 breit)
        assertEquals(RingCheck.Hit.THROUGH, RingCheck.check(RING, new Vec(-1, 105.3, 0), new Vec(1, 105.3, 0)).hit());
        assertEquals(RingCheck.Hit.MISSED, RingCheck.check(RING, new Vec(-1, 105.6, 0), new Vec(1, 105.6, 0)).hit());
        assertEquals(RingCheck.Hit.NONE, RingCheck.check(RING, new Vec(-1, 100, 60), new Vec(1, 100, 60)).hit());
    }

    @Test
    void tiltedRingsWork() {
        Ring up = new Ring(1, new Vec(0, 100, 0), new Vec(1, 1, 0), 4, Ring.Kind.BOOST, 0, 2.2);
        assertEquals(RingCheck.Hit.THROUGH, RingCheck.check(up, new Vec(-1, 99, 0), new Vec(1, 101, 0)).hit());
        Vec boosted = up.boost(new Vec(1.0, 0, 0));
        assertEquals(2.2, boosted.length(), 1e-9);
        assertTrue(boosted.y() > 1.5, "Schub nach oben");
        assertEquals(new Vec(1, 0, 0), RING.boost(new Vec(1, 0, 0)), "normaler Ring schiebt nicht");
    }

    @Test
    void distanceToSegment() {
        assertEquals(3, RingCheck.distanceToSegment(new Vec(5, 3, 0), new Vec(0, 0, 0), new Vec(10, 0, 0)), 1e-9);
        assertEquals(5, RingCheck.distanceToSegment(new Vec(-3, 4, 0), new Vec(0, 0, 0), new Vec(10, 0, 0)), 1e-9);
    }
}
