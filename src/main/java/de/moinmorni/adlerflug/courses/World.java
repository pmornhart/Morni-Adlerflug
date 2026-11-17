package de.moinmorni.adlerflug.courses;

import de.moinmorni.adlerflug.course.Blk;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.CourseBuilder;
import de.moinmorni.adlerflug.course.Pilots;
import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.course.Plan;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.land.Corridor;
import de.moinmorni.adlerflug.land.Terrain;
import de.moinmorni.adlerflug.land.Trees;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Die ganze Adlerflug-Welt als Modell: Landschaft, Bäume, Bauplan, Basislager und alle Strecken. Ohne Bukkit – der
 * Welt-Erzeuger fragt hier Block für Block, die Tests fliegen hier jede Strecke ab.
 */
public final class World {

    /** Startwert der Landschaft (gleiche Welt auf jedem Server). */
    public static final long SEED = 0x41D1E9F1L;

    private final Terrain terrain;
    private final Trees trees;
    private final Plan plan;
    private List<Design> designs;
    private List<Course> courses;
    /** Die vier Richtungen der Strecke des Tages (nur für Landschaft und Bauten). */
    private List<Design> dailies = List.of();
    private final Basecamp basecamp;
    /** Höhe der Landschaft je Säule, für die Prüf-Flüge (sie fragen dieselben Säulen sehr oft). */
    private final java.util.Map<Long, Integer> heights = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Critter> critters = new ArrayList<>();

    /** Deko-Tier ohne eigenen Willen (Kühe auf der Weide …): Art, Position, Blickrichtung. */
    public record Critter(String type, double x, double y, double z, float yaw) {
    }

    private World(Terrain terrain, Trees trees, Plan plan, List<Design> designs, Basecamp basecamp) {
        this.terrain = terrain;
        this.trees = trees;
        this.plan = plan;
        this.designs = designs;
        this.courses = designs.stream().map(Design::course).toList();
        this.basecamp = basecamp;
    }

    /** Baut das ganze Modell (dauert etwa eine Sekunde). */
    public static World create() {
        List<Design> designs = Designs.all();
        List<Design> dailies = Daily.designs();
        List<Design> every = new ArrayList<>(designs);
        every.addAll(dailies);
        Terrain terrain = new Terrain(SEED);
        Basecamp camp = new Basecamp();
        camp.shape(terrain);
        for (Design d : every) {
            d.shaper().shape(terrain);
        }
        for (Design d : every) {
            List<List<Vec>> pts = d.cutPoints();
            for (int i = 0; i < d.cuts().size(); i++) {
                Design.Cut cut = d.cuts().get(i);
                if (!pts.get(i).isEmpty()) {
                    terrain.corridor(new Corridor(pts.get(i), cut.clearance(), cut.floor(), cut.slope()));
                }
            }
            Vec l = d.course().landing.at();
            terrain.shapeLate(new Terrain.Plateau(l.x(), l.z(), 8, 12, l.y() - 1));
        }
        Trees trees = new Trees(terrain);
        Plan plan = new Plan();
        World w = new World(terrain, trees, plan, designs, camp);
        w.dailies = dailies;
        // je Strecke eine Station, dazu eine für die Strecke des Tages (Name und Farbe wie jeden Tag)
        List<Course> stations = new ArrayList<>(w.courses);
        stations.add(dailies.get(0).course());
        camp.build(w, stations);
        for (Design d : every) {
            Course c = d.course();
            Scenery.startTower(plan, terrain, c, d.accent());
            Vec s = c.start.stand();
            trees.block((int) s.x() - 12, (int) s.z() - 12, (int) s.x() + 12, (int) s.z() + 12);
            Vec a = c.landing.at();
            trees.block((int) a.x() - 9, (int) a.z() - 9, (int) a.x() + 9, (int) a.z() + 9);
            Scenery.landing(plan, terrain, c);
            for (Updraft u : c.updrafts) {
                Scenery.updraftBase(plan, terrain, u);
                trees.block((int) (u.x() - u.radius() - 2), (int) (u.z() - u.radius() - 2),
                        (int) (u.x() + u.radius() + 2), (int) (u.z() + u.radius() + 2));
            }
            // Windsäcke am Rand jeder Windzone zeigen, wohin es drückt
            Rocks.windsocks(plan, terrain, c, 15);
            d.builder().build(w, c);
        }
        plan.connect();
        // Medaillen mit Landschaft nachrechnen: nur Flüge der Prüf-Piloten, die nirgends anstoßen, zählen
        List<Design> done = new ArrayList<>();
        for (Design d : designs) {
            done.add(d.withCourse(CourseBuilder.medals(d.course(), w::solid)));
        }
        w.designs = List.copyOf(done);
        w.courses = w.designs.stream().map(Design::course).toList();
        w.heights.clear();
        return w;
    }

    public Terrain terrain() {
        return terrain;
    }

    public Trees trees() {
        return trees;
    }

    public Plan plan() {
        return plan;
    }

    public List<Course> courses() {
        return courses;
    }

    public List<Design> designs() {
        return designs;
    }

    public Basecamp basecamp() {
        return basecamp;
    }

    public Optional<Course> course(String id) {
        return courses.stream().filter(c -> c.id.equals(id)).findFirst();
    }

    /**
     * Strecke des Tages für diese Bedingungen – mit Medaillen, die nur Flüge ohne Anstoßen zählen (dauert etwas,
     * geht auch außerhalb des Hauptthreads).
     */
    public Daily.Today daily(Daily.Conditions c) {
        List<Daily.Conditions> tries = new ArrayList<>();
        for (int step = 0; Daily.easier(c, step) != null; step++) {
            if (!tries.contains(Daily.easier(c, step))) {
                tries.add(Daily.easier(c, step));
            }
        }
        tries.add(Daily.plain(c));
        for (Daily.Conditions cur : tries) {
            Course course;
            try {
                course = Daily.build(cur);
            } catch (IllegalStateException e) {
                continue;
            }
            if (!Daily.check(course, this::solid).finished()) {
                continue;
            }
            if (!course.bonus.isEmpty()) {
                FlightSim.Result hunt = FlightSim.fly(course.start.line(), course.start.launch(), course.air(),
                        Pilots.hunter(course), this::solid, 8000);
                if (!hunt.finished() || hunt.bonusCount() < course.bonus.size()) {
                    continue;
                }
            }
            Daily.Today today = new Daily.Today(cur, CourseBuilder.medals(course, this::solid));
            heights.clear();
            return today;
        }
        throw new IllegalStateException("Strecke des Tages " + c.id() + " lässt sich nicht bauen");
    }

    /** Die vier Richtungen der Strecke des Tages, wie die Landschaft für sie geformt ist. */
    public List<Course> dailyLayouts() {
        return dailies.stream().map(Design::course).toList();
    }

    /** Alle Strecken samt den vier Richtungen der Strecke des Tages (für Biome und Vorbereiten der Chunks). */
    public List<Course> everyLayout() {
        List<Course> out = new ArrayList<>(courses);
        out.addAll(dailyLayouts());
        return out;
    }

    /** Strecke, in deren Nähe der Punkt liegt (oder null). */
    public Course near(Vec p) {
        for (Course c : courses) {
            if (c.bounds.grow(60).contains(p)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Block an einer Stelle, wie der Welt-Erzeuger ihn setzt: erst der Plan, dann Bäume, dann Wasser und Landschaft.
     * Gibt den Blockzustand zurück („minecraft:air“ für Luft).
     */
    public String blockAt(int x, int y, int z) {
        Blk b = plan.get(x, y, z);
        if (b != null) {
            return b.data();
        }
        int h = terrain.height(x, z);
        if (y <= h) {
            return "minecraft:" + (y == h ? terrain.surface(x, z, h, 0) : "stone");
        }
        int water = terrain.water(x, z, h);
        if (water != Integer.MIN_VALUE && y <= water) {
            return "minecraft:water";
        }
        if (y > h + 5 && terrain.cloudAt(x, y, z, h)) {
            return "minecraft:" + Terrain.CLOUD;
        }
        if (y > h && y < h + 20) {
            for (Trees.Tree t : trees.around(x, z, x, z)) {
                for (Trees.Part p : Trees.parts(t)) {
                    if (p.x() == x && p.y() == y && p.z() == z) {
                        return "minecraft:" + p.block();
                    }
                }
            }
        }
        return "minecraft:air";
    }

    /** Fliegt man hier gegen etwas Festes? (Für die Prüfung der Strecken.) */
    public boolean solid(int x, int y, int z) {
        Blk b = plan.get(x, y, z);
        if (b != null) {
            return b.solid();
        }
        int h = heights.computeIfAbsent(Plan.key(x, 0, z), k -> terrain.height(x, z));
        if (y <= h) {
            return true;
        }
        if (y > h + 5 && terrain.cloudAt(x, y, z, h)) {
            return true;
        }
        if (y < h + 20) {
            for (Trees.Tree t : trees.around(x, z, x, z)) {
                if (y <= t.top() + 1) {
                    for (Trees.Part p : Trees.parts(t)) {
                        if (p.x() == x && p.y() == y && p.z() == z) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Deko-Tier dazu (vom Plugin erzeugt, ohne KI). */
    public void critter(String type, double x, double y, double z, float yaw) {
        critters.add(new Critter(type, x, y, z, yaw));
    }

    public List<Critter> critters() {
        return critters;
    }

    /** Alle Updrafts aller Strecken. */
    public List<Updraft> updrafts() {
        List<Updraft> out = new ArrayList<>();
        courses.forEach(c -> out.addAll(c.updrafts));
        return out;
    }
}
