package de.moinmorni.adlerflug.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Medaillen: Grenzen, nächste Stufe, Zählen der Stufen. */
class MedalTest {

    private static final Course.Medals M = new Course.Medals(40_000, 33_000, 29_000, 26_000, 24_000);

    @Test
    void bestMedalForTime() {
        assertNull(Medal.of(41_000, M));
        assertNull(Medal.of(0, M));
        assertEquals(Medal.BRONZE, Medal.of(40_000, M));
        assertEquals(Medal.SILBER, Medal.of(30_000, M));
        assertEquals(Medal.GOLD, Medal.of(29_000, M));
        assertEquals(Medal.ADLER, Medal.of(25_999, M));
        assertEquals(Medal.ADLER, Medal.of(24_001, M));
        assertEquals(Medal.STEINADLER, Medal.of(24_000, M));
    }

    @Test
    void nextAndTiers() {
        assertEquals(Medal.BRONZE, Medal.next(null));
        assertEquals(Medal.ADLER, Medal.next(Medal.GOLD));
        assertEquals(Medal.STEINADLER, Medal.next(Medal.ADLER));
        assertNull(Medal.next(Medal.STEINADLER));
        assertEquals(0, Medal.tiers(null));
        assertEquals(4, Medal.tiers(Medal.ADLER));
        assertEquals(5, Medal.tiers(Medal.STEINADLER));
        assertEquals(2, Medal.tiers(Medal.GOLD) - Medal.tiers(Medal.BRONZE));
        assertTrue(Medal.ADLER.name("en").equals("Eagle") && Medal.ADLER.name("de").equals("Adler"));
    }

    @Test
    void medalsFromDesignTimeAreOrdered() {
        Course.Medals m = CourseBuilder.medals(24_400, 22_100);
        assertEquals(22_250, m.steinadler(), "auf Viertelsekunden aufgerundet");
        assertEquals(24_000, m.adler(), "Adler etwas schneller als der einfache Pilot");
        assertTrue(m.bronze() > m.silber() && m.silber() > m.gold() && m.gold() > m.adler()
                && m.adler() > m.steinadler());
        Course.Medals close = CourseBuilder.medals(20_000, 19_900);
        assertTrue(close.adler() - close.steinadler() >= 250, "jede Stufe mindestens eine Viertelsekunde");
    }
}
