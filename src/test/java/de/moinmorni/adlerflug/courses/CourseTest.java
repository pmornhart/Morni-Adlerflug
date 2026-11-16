package de.moinmorni.adlerflug.courses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.adlerflug.course.Blk;
import de.moinmorni.adlerflug.course.Box;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Difficulty;
import de.moinmorni.adlerflug.course.Pilots;
import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.RingCheck;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Jede Strecke ist fliegbar: Der Musterflug geht der Reihe nach durch alle Ringe und stößt nirgends an, ein einfacher
 * Pilot (zielt auf die Ringe, nimmt den Aufwind mit) kommt ins Ziel, Start und Landeplatz stehen richtig, die Strecken
 * kommen sich nicht in die Quere.
 */
class CourseTest {

    private static World world;

    @BeforeAll
    static void build() {
        world = World.create();
    }

    @Test
    void sixCoursesFromEasyToExtreme() {
        assertEquals(6, world.courses().size());
        Set<Difficulty> used = EnumSet.noneOf(Difficulty.class);
        Set<String> ids = new HashSet<>();
        for (Course c : world.courses()) {
            used.add(c.difficulty);
            assertTrue(ids.add(c.id), "doppelt: " + c.id);
            assertTrue(c.id.matches("[a-z]+"), "Kennung nur Kleinbuchstaben (Statistik time_<id>_ms): " + c.id);
        }
        assertEquals(EnumSet.allOf(Difficulty.class), used);
    }

    @Test
    void designFlightPassesEveryRingInOrder() {
        for (Course c : world.courses()) {
            List<Vec> path = c.pathList();
            int next = 0;
            for (int t = 1; t < path.size() && next < c.ringCount(); t++) {
                RingCheck.Result r = RingCheck.check(c.rings.get(next), Course.center(path.get(t - 1)),
                        Course.center(path.get(t)));
                assertFalse(r.hit() == RingCheck.Hit.MISSED, c.id + ": Musterflug verfehlt Ring " + (next + 1));
                if (r.hit() == RingCheck.Hit.THROUGH) {
                    next++;
                }
            }
            assertEquals(c.ringCount(), next, c.id + ": Musterflug schafft nicht alle Ringe");
        }
    }

    @Test
    void designFlightHitsNothing() {
        for (Course c : world.courses()) {
            List<Vec> path = c.pathList();
            int finish = c.tickBefore(c.ringCount() - 1, 0) + 2;
            for (int t = 1; t < Math.min(path.size(), finish); t++) {
                Vec a = path.get(t - 1);
                Vec b = path.get(t);
                for (double f = 0.25; f <= 1.0; f += 0.25) {
                    Vec p = a.lerp(b, f);
                    for (double[] o : new double[][] {{-0.29, 0.01, -0.29}, {0.29, 0.59, 0.29}, {0.29, 0.01, -0.29},
                        {-0.29, 0.59, 0.29}}) {
                        int x = (int) Math.floor(p.x() + o[0]);
                        int y = (int) Math.floor(p.y() + o[1]);
                        int z = (int) Math.floor(p.z() + o[2]);
                        assertFalse(world.solid(x, y, z), c.id + ": Musterflug stößt bei Tick " + t + " an "
                                + world.blockAt(x, y, z) + " (" + x + " " + y + " " + z + ")");
                    }
                }
            }
        }
    }

    @Test
    void aSimplePilotReachesTheFinish() {
        for (Course c : world.courses()) {
            FlightSim.Result r = FlightSim.fly(c.start.line(), c.start.launch(), c.rings, c.updrafts, Pilots.aim(c),
                    world::solid, 8000);
            assertTrue(r.finished() && Pilots.allRings(r), c.id + ": " + r.problem());
            assertTrue(r.finishMs() >= c.designMs, c.id + ": Medaillen richten sich nach dem schnellsten Piloten");
        }
    }

    @Test
    void startRampAndLandingPadStand() {
        for (Course c : world.courses()) {
            Vec s = c.start.stand();
            int x = (int) Math.floor(s.x());
            int y = (int) Math.floor(s.y());
            int z = (int) Math.floor(s.z());
            assertTrue(world.solid(x, y - 1, z), c.id + ": Boden unter dem Stehplatz");
            assertFalse(world.solid(x, y, z) || world.solid(x, y + 1, z), c.id + ": Platz zum Stehen");
            assertTrue(c.start.area().contains(s), c.id + ": Stehplatz im Startbereich");
            assertFalse(c.start.area().contains(c.pathAt(2)), c.id + ": Musterflug verlässt den Startbereich sofort");
            Vec l = c.landing.at();
            assertTrue(world.solid((int) Math.floor(l.x()), (int) Math.floor(l.y()) - 1, (int) Math.floor(l.z())),
                    c.id + ": Landeplatz hat Boden");
            assertFalse(world.solid((int) Math.floor(l.x()), (int) Math.floor(l.y()), (int) Math.floor(l.z())),
                    c.id + ": Landeplatz frei");
        }
    }

    @Test
    void ringsAreBigEnoughAndApart() {
        for (Course c : world.courses()) {
            assertTrue(c.ringCount() >= 10, c.id + ": mindestens 10 Ringe");
            assertTrue(c.rings.stream().anyMatch(r -> r.kind() == Ring.Kind.BOOST), c.id + ": ein Schub-Ring");
            assertTrue(c.finish().finish(), c.id + ": letzter Ring ist das Ziel");
            for (int i = 0; i < c.ringCount(); i++) {
                Ring r = c.rings.get(i);
                assertEquals(i, r.index());
                assertTrue(r.radius() >= 3.5, c.id + ": Ring " + (i + 1) + " zu klein");
                assertTrue(r.section() >= 0 && r.section() < 4);
                if (i > 0) {
                    double d = r.center().distance(c.rings.get(i - 1).center());
                    assertTrue(d >= 25, c.id + ": Ringe " + i + "/" + (i + 1) + " zu nah (" + d + ")");
                }
            }
            assertTrue(c.medals.bronze() > c.medals.silber() && c.medals.silber() > c.medals.gold()
                    && c.medals.gold() > c.medals.adler() && c.medals.adler() > 10_000, c.id + ": Medaillen");
        }
    }

    @Test
    void updraftsLieOnTheWay() {
        int with = 0;
        for (Course c : world.courses()) {
            with += c.updrafts.isEmpty() ? 0 : 1;
            for (Updraft u : c.updrafts) {
                boolean through = c.pathList().stream().anyMatch(p -> u.contains(Course.center(p)));
                assertTrue(through, c.id + ": Musterflug geht nicht durch den Aufwind bei " + u.x() + "/" + u.z());
                assertTrue(world.terrain().height(u.x(), u.z()) < u.top() - 10, c.id + ": Aufwind steckt im Berg");
            }
        }
        assertTrue(with >= 4, "Aufwind auf den meisten Strecken");
    }

    @Test
    void coursesStayApartFromEachOtherAndTheBasecamp() {
        List<Course> cs = world.courses();
        for (int i = 0; i < cs.size(); i++) {
            Box a = cs.get(i).bounds;
            assertTrue(Math.hypot(a.center().x(), a.center().z()) > 250, cs.get(i).id + ": zu nah am Basislager");
            for (int j = i + 1; j < cs.size(); j++) {
                Box b = cs.get(j).bounds;
                boolean overlap = a.minX() - 40 < b.maxX() && b.minX() - 40 < a.maxX() && a.minZ() - 40 < b.maxZ()
                        && b.minZ() - 40 < a.maxZ();
                assertFalse(overlap, cs.get(i).id + " und " + cs.get(j).id + " überschneiden sich");
            }
        }
    }

    @Test
    void basecampHasAStationPerCourse() {
        assertEquals(world.courses().size(), world.basecamp().stations().size());
        for (var s : world.basecamp().stations()) {
            assertNotNull(world.plan().get(s.signX(), s.signY(), s.signZ()), s.course());
            assertTrue(world.plan().get(s.signX(), s.signY(), s.signZ()).data().contains("sign"), s.course());
            assertTrue(world.plan().signs().stream().anyMatch(g -> g.x() == s.signX() && g.z() == s.signZ()), s.course());
        }
        Basecamp b = world.basecamp();
        int x = (int) Math.floor(b.spawnX());
        int z = (int) Math.floor(b.spawnZ());
        assertTrue(world.solid(x, (int) b.spawnY() - 1, z) && !world.solid(x, (int) b.spawnY(), z), "Spawn steht frei");
    }

    @Test
    void onlyBlockNamesOfMinecraft26() {
        Set<String> old = Set.of("minecraft:chain", "minecraft:grass", "minecraft:sign", "minecraft:wall_sign");
        Set<String> seen = new HashSet<>();
        for (Blk b : world.plan().blocks().values()) {
            seen.add(b.id());
        }
        for (String id : seen) {
            assertFalse(old.contains(id), "alter Blockname: " + id);
            assertEquals(id.toLowerCase(java.util.Locale.ROOT), id);
        }
        assertTrue(seen.contains("minecraft:glowstone") && seen.contains("minecraft:yellow_concrete"));
    }
}
