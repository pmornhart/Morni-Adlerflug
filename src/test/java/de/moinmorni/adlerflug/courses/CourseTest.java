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
 * Jede Strecke ist fliegbar: Der Musterflug geht der Reihe nach durch alle Ringe (auch bewegte Ringe und Tore im Takt)
 * und stößt nirgends an, ein einfacher Pilot (zielt auf die Ringe, nimmt den Aufwind mit) kommt ins Ziel, der Flug über
 * alle Bonus-Ringe klappt, Start und Landeplatz stehen richtig, die Strecken kommen sich nicht in die Quere.
 */
class CourseTest {

    private static World world;

    @BeforeAll
    static void build() {
        world = World.create();
    }

    @Test
    void eightCoursesFromEasyToExtreme() {
        assertEquals(8, world.courses().size());
        Set<Difficulty> used = EnumSet.noneOf(Difficulty.class);
        Set<String> ids = new HashSet<>();
        for (Course c : world.courses()) {
            used.add(c.difficulty);
            assertTrue(ids.add(c.id), "doppelt: " + c.id);
            assertTrue(c.id.matches("[a-z]+"), "Kennung nur Kleinbuchstaben (Statistik time_<id>_ms): " + c.id);
        }
        assertEquals(EnumSet.allOf(Difficulty.class), used);
        long hard = world.courses().stream().filter(c -> c.difficulty.ordinal() >= Difficulty.SCHWER.ordinal()).count();
        assertTrue(hard >= 5, "mindestens fünf schwere und extreme Strecken");
    }

    @Test
    void designFlightPassesEveryRingInOrder() {
        for (Course c : world.courses()) {
            List<Vec> path = c.pathList();
            int next = 0;
            for (int t = 1; t < path.size() && next < c.ringCount(); t++) {
                RingCheck.Result r = RingCheck.check(c.rings.get(next), Course.center(path.get(t - 1)),
                        Course.center(path.get(t)), t - 1);
                assertFalse(r.hit() == RingCheck.Hit.MISSED, c.id + ": Musterflug verfehlt Ring " + (next + 1));
                assertFalse(r.hit() == RingCheck.Hit.CLOSED, c.id + ": Musterflug vor geschlossenem Tor " + (next + 1));
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
            FlightSim.Result r = FlightSim.fly(c.start.line(), c.start.launch(), c.air(), Pilots.aim(c), world::solid,
                    8000);
            assertTrue(r.finished() && Pilots.allRings(r), c.id + ": " + r.problem());
            assertTrue(r.scoreMs() >= c.designMs, c.id + ": Medaillen richten sich nach dem schnellsten Piloten");
        }
    }

    @Test
    void everyBonusRingCanBeCollectedAndStillFinish() {
        int total = 0;
        for (Course c : world.courses()) {
            total += c.bonus.size();
            if (c.bonus.isEmpty()) {
                continue;
            }
            FlightSim.Result r = FlightSim.fly(c.start.line(), c.start.launch(), c.air(), Pilots.hunter(c), world::solid,
                    8000);
            assertTrue(r.finished(), c.id + ": Flug über die Bonus-Ringe: " + r.problem());
            assertEquals(c.bonus.size(), r.bonusCount(), c.id + ": nicht alle Bonus-Ringe");
            assertTrue(r.credit() > 0 && r.scoreMs() < r.finishMs(), c.id + ": Gutschrift");
            for (Ring b : c.bonus) {
                assertTrue(b.bonus() && b.credit() >= 1000, c.id + ": Bonus-Ring schenkt Zeit");
                double off = c.pathList().stream().mapToDouble(p -> p.distance(b.center())).min().orElse(0);
                assertTrue(off > b.radius() + 2, c.id + ": Bonus-Ring liegt abseits der Linie (" + off + ")");
            }
            // auch der Autopilot im echten Test fliegt diesen Weg: Bonus-Flug ohne Anstoßen
            assertNotNull(c.bonusPath, c.id);
            for (int t = 1; t < c.bonusPathTicks(); t++) {
                Vec p = c.bonusPathAt(t);
                assertFalse(world.solid((int) Math.floor(p.x()), (int) Math.floor(p.y() + 0.3), (int) Math.floor(p.z())),
                        c.id + ": Bonus-Flug stößt bei Tick " + t + " an");
            }
        }
        assertTrue(total >= 7, "Bonus-Ringe auf fast allen Strecken");
    }

    @Test
    void moreHappensInTheAir() {
        int moving = 0;
        int circling = 0;
        int gates = 0;
        int winds = 0;
        for (Course c : world.courses()) {
            for (Ring r : c.rings) {
                moving += r.motion() != null ? 1 : 0;
                circling += r.motion() != null && r.motion().circles() ? 1 : 0;
                gates += r.gate() != null ? 1 : 0;
                assertFalse(r.motion() != null && r.gate() != null, c.id + ": bewegtes Tor wäre unfair");
            }
            winds += c.winds.size();
            if (c.difficulty.ordinal() >= Difficulty.SCHWER.ordinal()) {
                boolean extra = !c.winds.isEmpty() || c.rings.stream().anyMatch(Ring::dynamic);
                assertTrue(extra || c.id.equals("stollenflug") || !c.bonus.isEmpty(), c.id + ": etwas Besonderes");
            }
        }
        assertTrue(moving >= 6, "bewegte Ringe: " + moving);
        assertTrue(circling >= 2, "kreisende Ringe: " + circling);
        assertTrue(gates >= 4, "Tore im Takt: " + gates);
        assertTrue(winds >= 4, "Windzonen: " + winds);
    }

    @Test
    void medalsAreStrictAndTheGoldenEagleIsReachable() {
        for (Course c : world.courses()) {
            Course.Medals m = c.medals;
            assertTrue(m.bronze() > m.silber() && m.silber() > m.gold() && m.gold() > m.adler()
                    && m.adler() > m.steinadler() && m.steinadler() > 10_000, c.id + ": Reihenfolge " + m);
            // Adler etwas schneller als der einfache Pilot bzw. der Musterflug, Gold höchstens 7 % langsamer
            assertTrue(m.adler() <= Math.ceil(c.designMs * 0.98 / 250.0) * 250, c.id + ": Adler zu leicht");
            assertTrue(m.gold() <= Math.ceil(c.designMs * 1.07 / 250.0) * 250, c.id + ": Gold zu leicht");
            // Steinadler: ein geübter Prüf-Pilot schafft ihn ohne Anstoßen
            boolean reached = false;
            for (FlightSim.Pilot p : Pilots.experts(c)) {
                FlightSim.Result r = FlightSim.fly(c.start.line(), c.start.launch(), c.air(), p, world::solid, 8000);
                if (r.finished() && r.scoreMs() <= m.steinadler()) {
                    reached = true;
                    break;
                }
            }
            assertTrue(reached, c.id + ": Steinadler (" + m.steinadler() + ") schafft kein Prüf-Pilot");
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
                assertTrue(r.radius() >= 3.0, c.id + ": Ring " + (i + 1) + " zu klein");
                if (c.difficulty.ordinal() >= Difficulty.SCHWER.ordinal() && !r.finish()) {
                    assertTrue(r.radius() <= 4.0, c.id + ": Ring " + (i + 1) + " zu groß für eine schwere Strecke");
                }
                assertTrue(r.section() >= 0 && r.section() < 4);
                if (i > 0) {
                    double d = r.center().distance(c.rings.get(i - 1).center());
                    assertTrue(d >= 25, c.id + ": Ringe " + i + "/" + (i + 1) + " zu nah (" + d + ")");
                }
            }
            assertTrue(c.medals.bronze() > c.medals.silber() && c.medals.silber() > c.medals.gold()
                    && c.medals.gold() > c.medals.adler() && c.medals.adler() > c.medals.steinadler(), c.id + ": Medaillen");
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
        assertEquals(world.courses().size() + 1, world.basecamp().stations().size(), "acht Strecken + Strecke des Tages");
        assertTrue(world.basecamp().stations().stream().anyMatch(s -> s.course().equals(Daily.ID)));
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

    /**
     * Treppe zur Freiflug-Schanze: Man läuft sie hinauf, ohne zu springen (jede Stufe ist eine Treppe, die nach Süden
     * ansteigt, oder ein Absatz), darüber ist Platz für den Kopf, links und rechts ist ein Geländer.
     */
    @Test
    void stairsToTheJumpTowerCanBeWalkedUp() {
        int feet = Basecamp.Y + 1;
        int landings = 0;
        for (int z = 14; z <= 27; z++) {
            for (int x = -2; x <= 2; x++) {
                String b = world.blockAt(x, feet, z);
                if (b.contains("stairs")) {
                    assertTrue(b.contains("facing=south") && !b.contains("half=top"),
                            "Stufe bei " + x + "/" + z + " steigt nach Süden an: " + b);
                } else {
                    assertTrue(world.solid(x, feet - 1, z) && !world.solid(x, feet, z), "Absatz bei " + x + "/" + z);
                }
            }
            if (world.blockAt(0, feet, z).contains("stairs")) {
                feet++;
            } else {
                landings++;
            }
            for (int a = 0; a <= 1; a++) {
                assertFalse(world.solid(0, feet + a, z), "Kopf frei bei z " + z);
            }
            assertTrue(world.blockAt(-3, feet, z).contains("fence"), "Geländer links bei z " + z);
            assertTrue(world.blockAt(3, feet, z).contains("fence"), "Geländer rechts bei z " + z);
        }
        assertTrue(landings >= 2, "Absatz in halber Höhe");
        assertEquals(Basecamp.Y + 13, feet, "oben auf der Höhe der Plattform der Schanze");
        assertTrue(world.solid(0, Basecamp.Y + 12, 28), "Plattform schließt an");
        assertTrue(world.blockAt(3, Basecamp.Y + 3, 13).contains("lantern"), "Laterne am Antritt");
        assertTrue(world.blockAt(-3, Basecamp.Y + 8, 21).contains("lantern"), "Laterne am Absatz");
    }
}
