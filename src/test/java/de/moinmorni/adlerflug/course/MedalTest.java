package de.moinmorni.adlerflug.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Medaillen: Grenzen, nächste Stufe, Zählen der Stufen. */
class MedalTest {

    private static final Course.Medals M = new Course.Medals(40_000, 33_000, 29_000, 26_000);

    @Test
    void bestMedalForTime() {
        assertNull(Medal.of(41_000, M));
        assertNull(Medal.of(0, M));
        assertEquals(Medal.BRONZE, Medal.of(40_000, M));
        assertEquals(Medal.SILBER, Medal.of(30_000, M));
        assertEquals(Medal.GOLD, Medal.of(29_000, M));
        assertEquals(Medal.ADLER, Medal.of(25_999, M));
    }

    @Test
    void nextAndTiers() {
        assertEquals(Medal.BRONZE, Medal.next(null));
        assertEquals(Medal.ADLER, Medal.next(Medal.GOLD));
        assertNull(Medal.next(Medal.ADLER));
        assertEquals(0, Medal.tiers(null));
        assertEquals(4, Medal.tiers(Medal.ADLER));
        assertEquals(2, Medal.tiers(Medal.GOLD) - Medal.tiers(Medal.BRONZE));
        assertTrue(Medal.ADLER.name("en").equals("Eagle") && Medal.ADLER.name("de").equals("Adler"));
    }

    @Test
    void medalsFromDesignTimeAreOrdered() {
        Course.Medals m = CourseBuilder.medals(24_400);
        assertEquals(24_500, m.adler(), "auf Viertelsekunden aufgerundet");
        assertTrue(m.bronze() > m.silber() && m.silber() > m.gold() && m.gold() > m.adler());
    }
}
