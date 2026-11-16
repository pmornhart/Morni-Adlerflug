package de.moinmorni.adlerflug.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.moinmorni.adlerflug.course.Box;
import de.moinmorni.adlerflug.flight.Vec;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stoppuhr, Zwischenzeiten und Anzeige. */
class TimeTest {

    @Test
    void clockShowsMinutesSecondsMillis() {
        assertEquals("0:00,000", TimeFormat.clock(0, "de"));
        assertEquals("0:05,250", TimeFormat.clock(5250, "de"));
        assertEquals("1:05,007", TimeFormat.clock(65007, "de"));
        assertEquals("1:05.007", TimeFormat.clock(65007, "en"));
        assertEquals("1:00:00,001", TimeFormat.clock(3_600_001, "de"));
    }

    @Test
    void differencesHaveSigns() {
        assertEquals("+0,452", TimeFormat.diff(452, "de"));
        assertEquals("−1,203", TimeFormat.diff(-1203, "de"));
        assertEquals("±0,000", TimeFormat.diff(0, "de"));
        assertEquals("+1:02,500", TimeFormat.diff(62500, "de"));
        assertEquals("−0.050", TimeFormat.diff(-50, "en"));
    }

    @Test
    void limitsAreShort() {
        assertEquals("0:45", TimeFormat.limit(45000, "de"));
        assertEquals("1:05,5", TimeFormat.limit(65500, "de"));
        assertEquals("0:23.25", TimeFormat.limit(23250, "en"));
    }

    @Test
    void runClockCountsTicksAndFractions() {
        RunClock c = new RunClock(100, 0.5);
        assertEquals(0, c.at(100, 0.5));
        assertEquals(50, c.at(101, 0.5));
        assertEquals(25, c.at(101, 0.0));
        assertEquals(1000, c.at(120, 0.5));
        assertEquals(1013, c.at(120, 0.76));
        // Anzeige während des Laufs in ganzen Ticks
        assertEquals(500, c.now(110));
        // Kein negativer Wert
        assertEquals(0, new RunClock(10, 0.9).at(10, 0.1));
    }

    @Test
    void exitFractionFindsTheMomentOfTakeOff() {
        Box start = new Box(10, 100, 0, 15, 103, 5);
        // Von x=14 nach x=16 in einem Tick: Kante x=15 auf halbem Weg
        assertEquals(0.5, start.exit(new Vec(14, 101, 2), new Vec(16, 101, 2)), 1e-9);
        // noch drin: −1
        assertEquals(-1, start.exit(new Vec(12, 101, 2), new Vec(13, 101, 2)), 1e-9);
        // von außen: −1
        assertEquals(-1, start.exit(new Vec(20, 101, 2), new Vec(21, 101, 2)), 1e-9);
        // nach unten von der Kante: Boden y=100 nach einem Viertel
        assertEquals(0.25, start.exit(new Vec(12, 100.5, 2), new Vec(12, 98.5, 2)), 1e-9);
    }

    @Test
    void splitsCompareWithBest() {
        List<Long> best = List.of(10_000L, 20_000L, 30_000L);
        assertEquals(-500L, Splits.delta(best, 1, 19_500));
        assertEquals(1200L, Splits.delta(best, 2, 31_200));
        assertNull(Splits.delta(best, 3, 40_000));
        assertNull(Splits.delta(List.of(), 0, 5_000));
        assertEquals("#5fd068", Splits.colour(-1));
        assertEquals("#ff6b5b", Splits.colour(1));
        assertEquals("#f5c542", Splits.colour(0));
    }
}
