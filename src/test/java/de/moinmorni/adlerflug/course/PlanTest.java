package de.moinmorni.adlerflug.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Bauplan: Schlüssel, Luft, Zäune verbinden, Fingerabdruck. */
class PlanTest {

    @Test
    void keysRoundTripIncludingNegatives() {
        for (int[] p : new int[][] {{0, 0, 0}, {-1200, -64, 900}, {1100, 319, -1100}, {-5, 150, -7}}) {
            long k = Plan.key(p[0], p[1], p[2]);
            assertEquals(p[0], Plan.x(k));
            assertEquals(p[1], Plan.y(k));
            assertEquals(p[2], Plan.z(k));
        }
    }

    @Test
    void airIsNotSolidAndCutsTheLandscape() {
        Plan plan = new Plan();
        plan.set(1, 2, 3, "air");
        assertTrue(plan.get(1, 2, 3).air());
        assertFalse(plan.get(1, 2, 3).solid());
        plan.set(1, 2, 3, "stone");
        assertTrue(plan.get(1, 2, 3).solid());
    }

    @Test
    void fencesConnectToNeighbours() {
        Plan plan = new Plan();
        plan.set(0, 0, 0, "spruce_fence");
        plan.set(1, 0, 0, "spruce_fence");
        plan.set(0, 0, -1, "stone");
        plan.set(-1, 0, 0, "oak_stairs[facing=east]");
        plan.connect();
        assertEquals("minecraft:spruce_fence[north=true,east=true,south=false,west=false]", plan.get(0, 0, 0).data());
    }

    @Test
    void fingerprintChangesWithABlock() {
        Plan a = new Plan();
        a.set(0, 0, 0, "stone");
        Plan b = new Plan();
        b.set(0, 0, 0, "stone");
        assertEquals(a.fingerprint(), b.fingerprint());
        b.set(0, 1, 0, "stone");
        assertNotEquals(a.fingerprint(), b.fingerprint());
    }
}
