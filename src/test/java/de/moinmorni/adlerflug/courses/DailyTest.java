package de.moinmorni.adlerflug.courses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Pilots;
import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.RingCheck;
import de.moinmorni.adlerflug.flight.Vec;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Strecke des Tages: folgt nur aus dem Datum, ist abwechslungsreich und jeden Tag fliegbar. */
class DailyTest {

    private static World world;
    private static final LocalDate START = LocalDate.of(2026, 11, 16);

    @BeforeAll
    static void build() {
        world = World.create();
    }

    @Test
    void sameDateSameConditionsForEveryone() {
        assertEquals(Daily.conditions(START), Daily.conditions(START));
        assertEquals(Daily.conditions(START), Daily.conditions(LocalDate.parse("2026-11-16")));
        assertNotEquals(Daily.conditions(START), Daily.conditions(START.plusDays(1)));
        Course a = world.daily(Daily.conditions(START)).course();
        Course b = world.daily(Daily.conditions(START)).course();
        assertEquals(a.signature(), b.signature(), "überall dieselbe Strecke");
        assertEquals(a.medals, b.medals);
        assertEquals(Daily.ID, a.id);
        assertEquals("2026-11-16", a.day);
    }

    @Test
    void somethingDifferentEveryDay() {
        Set<Daily.Layout> layouts = EnumSet.noneOf(Daily.Layout.class);
        Set<Daily.Time> times = EnumSet.noneOf(Daily.Time.class);
        Set<Daily.Weather> weather = EnumSet.noneOf(Daily.Weather.class);
        Set<Integer> winds = new HashSet<>();
        Set<Integer> dirs = new HashSet<>();
        int gates = 0;
        int small = 0;
        for (int d = 0; d < 120; d++) {
            Daily.Conditions c = Daily.conditions(START.plusDays(d));
            layouts.add(c.layout());
            times.add(c.time());
            weather.add(c.weather());
            winds.add(c.windLevel());
            dirs.add(c.windDir());
            gates += c.gate() >= 0 ? 1 : 0;
            small += c.radius() < 4 ? 1 : 0;
            assertFalse(c.moving().isEmpty(), "immer etwas Bewegung");
            assertFalse(c.bonus().isEmpty(), "immer ein Bonus-Ring");
        }
        assertEquals(EnumSet.allOf(Daily.Layout.class), layouts);
        assertEquals(EnumSet.allOf(Daily.Time.class), times);
        assertEquals(EnumSet.allOf(Daily.Weather.class), weather);
        assertEquals(4, winds.size());
        assertEquals(8, dirs.size());
        assertTrue(gates > 40 && gates < 110, "Takt-Tor an manchen Tagen: " + gates);
        assertTrue(small > 20, "kleine Ringe an manchen Tagen: " + small);
    }

    @Test
    void windBlowsWhereItSays() {
        for (int d = 0; d < 40; d++) {
            Daily.Conditions c = Daily.conditions(START.plusDays(d));
            Vec w = c.wind();
            if (c.windLevel() == 0) {
                assertEquals(0, w.length(), 1e-12);
                continue;
            }
            assertEquals(Daily.WIND[c.windLevel()], w.length(), 1e-9);
            // aus Norden (0) weht nach Süden (+z), aus Osten (2) nach Westen (−x)
            if (c.windDir() == 0) {
                assertTrue(w.z() > 0);
            } else if (c.windDir() == 2) {
                assertTrue(w.x() < 0);
            }
            assertFalse(c.windText("de").isBlank() || c.windText("en").isBlank());
        }
    }

    /** Zwei Wochen am Stück: jeder Tag lässt sich bauen und fliegen, mit und ohne Bonus-Ringe, ohne Anstoßen. */
    @Test
    void everyDayCanBeFlown() {
        for (int d = 0; d < 14; d++) {
            Daily.Today t = world.daily(Daily.conditions(START.plusDays(d)));
            Course c = t.course();
            FlightSim.Result r = FlightSim.fly(c.start.line(), c.start.launch(), c.air(), Pilots.aim(c), world::solid,
                    8000);
            assertTrue(r.finished(), c.day + ": " + r.problem());
            assertTrue(c.medals.steinadler() < c.medals.adler(), c.day);
            assertEquals(t.conditions().wind(), c.wind, "der Wind, der gilt, steckt in der Strecke");
            // Musterflug durch alle Ringe, Tore offen, nichts im Weg
            List<Vec> path = c.pathList();
            int next = 0;
            for (int k = 1; k < path.size() && next < c.ringCount(); k++) {
                RingCheck.Result h = RingCheck.check(c.rings.get(next), Course.center(path.get(k - 1)),
                        Course.center(path.get(k)), k - 1);
                assertTrue(h.hit() == RingCheck.Hit.NONE || h.hit() == RingCheck.Hit.THROUGH, c.day + " Ring " + next);
                if (h.hit() == RingCheck.Hit.THROUGH) {
                    next++;
                }
                Vec p = path.get(k);
                if (next < c.ringCount()) {
                    assertFalse(world.solid((int) Math.floor(p.x()), (int) Math.floor(p.y() + 0.3),
                            (int) Math.floor(p.z())), c.day + ": Musterflug stößt an bei " + p);
                }
            }
            assertEquals(c.ringCount(), next, c.day);
            for (Ring b : c.bonus) {
                assertTrue(b.bonus());
            }
        }
    }

    @Test
    void mirroredAndReversedLayoutsGoTheOtherWay() {
        List<Course> l = world.dailyLayouts();
        assertEquals(4, l.size());
        Course normal = l.get(Daily.Layout.NORMAL.ordinal());
        Course rev = l.get(Daily.Layout.UMGEDREHT.ordinal());
        Course mir = l.get(Daily.Layout.GESPIEGELT.ordinal());
        assertTrue(normal.start.line().x() < rev.start.line().x(), "andersherum startet am anderen Ende");
        assertEquals(normal.start.line().x(), mir.start.line().x(), 1e-9);
        double nz = normal.rings.get(0).center().z() - Daily.AXIS;
        double mz = mir.rings.get(0).center().z() - Daily.AXIS;
        assertTrue(nz * mz < 0, "gespiegelt liegt der erste Ring auf der anderen Seite des Tals");
    }
}
