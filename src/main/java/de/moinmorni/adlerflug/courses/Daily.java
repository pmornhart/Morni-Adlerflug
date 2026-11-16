package de.moinmorni.adlerflug.courses;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.CourseBuilder;
import de.moinmorni.adlerflug.course.Difficulty;
import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.land.Terrain;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Strecke des Tages („Tagesflug“): ein langes Tal im Süden mit einem Startturm an jedem Ende. Was heute gilt, folgt
 * allein aus dem Datum (deutsche Zeit) – auf jedem Server und für jeden Spieler gleich: in welche Richtung geflogen
 * wird (normal, gespiegelt, umgedreht oder beides), Wind aus einer von acht Richtungen in vier Stärken, Tageszeit und
 * Wetter, wie groß die Ringe sind, welche Ringe gleiten oder kreisen, wo ein Takt-Tor steht und wo Bonus-Ringe liegen.
 *
 * <p>Die Landschaft ist für alle vier Richtungen samt allen Bonus-Ringen geformt; jede Tagesstrecke entsteht mit dem
 * nachgebauten Gleitflug neu ({@link CourseBuilder}), Medaillen inklusive.
 */
public final class Daily {

    public static final String ID = "tagesflug";
    /** Mitte des Tals (Achse) und Enden mit den Starttürmen. */
    static final double AXIS = 800;
    static final double WEST = 232.5;
    static final double EAST = 856.5;
    /** Höhe der Füße auf den Starttürmen. */
    static final double TOWER = 206;

    /** Richtung des Flugs. */
    public enum Layout {
        NORMAL("normal", "normal", false, false),
        GESPIEGELT("gespiegelt", "mirrored", true, false),
        UMGEDREHT("umgedreht", "reversed", false, true),
        BEIDES("umgedreht und gespiegelt", "reversed and mirrored", true, true);

        public final String de;
        public final String en;
        public final boolean mirrored;
        public final boolean reversed;

        Layout(String de, String en, boolean mirrored, boolean reversed) {
            this.de = de;
            this.en = en;
            this.mirrored = mirrored;
            this.reversed = reversed;
        }

        public String name(String lang) {
            return "en".equals(lang) ? en : de;
        }
    }

    /** Tageszeit (Minecraft-Zeit, die der Flieger sieht). */
    public enum Time {
        MORGEN("Morgenrot", "Dawn", 23250), MITTAG("Mittag", "Midday", 6000), ABEND("Abendrot", "Sunset", 12350),
        NACHT("Nacht", "Night", 18000);

        public final String de;
        public final String en;
        public final long ticks;

        Time(String de, String en, long ticks) {
            this.de = de;
            this.en = en;
            this.ticks = ticks;
        }

        public String name(String lang) {
            return "en".equals(lang) ? en : de;
        }
    }

    /** Wetter. */
    public enum Weather {
        KLAR("klar", "clear"), REGEN("Regen", "rain");

        public final String de;
        public final String en;

        Weather(String de, String en) {
            this.de = de;
            this.en = en;
        }

        public String name(String lang) {
            return "en".equals(lang) ? en : de;
        }
    }

    /** Windstärken: Druck pro Tick (Blöcke). */
    static final double[] WIND = {0, 0.004, 0.007, 0.010};
    private static final String[] WIND_DE = {"windstill", "leichter Wind", "frischer Wind", "stürmisch"};
    private static final String[] WIND_EN = {"calm", "light wind", "fresh breeze", "stormy"};
    private static final String[] FROM_DE = {"Norden", "Nordosten", "Osten", "Südosten", "Süden", "Südwesten", "Westen",
        "Nordwesten"};
    private static final String[] FROM_EN = {"the north", "the north-east", "the east", "the south-east", "the south",
        "the south-west", "the west", "the north-west"};
    /** Pfeil in die Richtung, in die der Wind weht (aus Sicht der Karte, Norden oben). */
    private static final String[] TO_ARROW = {"↓", "↙", "←", "↖", "↑", "↗", "→", "↘"};

    /** Waagerechte Wegpunkte des Tals von West nach Ost (Normalrichtung), Bonus-Stellen, Aufwinde. */
    private static final double[][] WAY = {{298, 790}, {342, 812}, {386, 788}, {428, 808}, {502, 784}, {544, 810},
        {588, 792}, {630, 812}, {704, 786}, {746, 808}, {790, 792}};
    /** Hinter diesem Wegpunkt (Index in {@link #WAY}) liegt ein Aufwind (Normalrichtung): 3 → (464, 800), 7 → (666, 800). */
    private static final double[][] UPDRAFT = {{464, AXIS}, {666, AXIS}};
    /** Schub-Ring (Index in {@link #WAY}): je Richtung einer, damit danach keine Kurve zu eng wird. */
    private static final int BOOST = 6;
    private static final int BOOST_REVERSED = 4;
    /** Wegpunkte, die gleiten oder kreisen dürfen, und wo ein Takt-Tor stehen darf (Index in {@link #WAY}). */
    private static final int[] MOVING = {1, 3, 8, 9};
    private static final int[] GATES = {2, 7};
    /**
     * Bonus-Stellen: zwischen Wegpunkt i und i+1 (Normalrichtung) bei (x, z) – so gewählt, dass sie in allen vier
     * Richtungen erreichbar sind und sich für geübte Flieger lohnen (Suche mit den Prüf-Piloten).
     */
    private static final double[][] BONUS = {{2, 410, 791}, {4, 519, 804}, {8, 722, 802}, {9, 763, 808}};
    /** Ziel und Landeplatz liegen in Verlängerung des letzten Stücks – schräg an den Türmen vorbei. */
    private static final double FINISH = 46;
    private static final double LANDING = 80;

    /**
     * Was heute gilt.
     *
     * @param windDir  aus welcher Richtung der Wind kommt (0 = Norden, 2 = Osten …)
     * @param windLevel 0 = windstill … 3 = stürmisch
     * @param moving   Wegpunkte (Index), die sich bewegen; {@code circles} sagt, ob sie kreisen
     * @param gate     Wegpunkt mit Takt-Tor oder −1
     * @param bonus    Bonus-Stellen (Index in {@link #BONUS})
     */
    public record Conditions(LocalDate date, Layout layout, int windDir, int windLevel, Time time, Weather weather,
                             double radius, List<Integer> moving, boolean circles, int gate, List<Integer> bonus) {

        /** Wind als Druck pro Tick (Richtung: wohin er weht). */
        public Vec wind() {
            double a = Math.toRadians(windDir * 45);
            // aus Norden (−z) weht nach Süden (+z)
            return new Vec(-Math.sin(a), 0, Math.cos(a)).mul(WIND[windLevel]);
        }

        /** „frischer Wind aus Westen“ bzw. „windstill“. */
        public String windText(String lang) {
            boolean en = "en".equals(lang);
            if (windLevel == 0) {
                return en ? WIND_EN[0] : WIND_DE[0];
            }
            return en ? WIND_EN[windLevel] + " from " + FROM_EN[windDir]
                    : WIND_DE[windLevel] + " aus " + FROM_DE[windDir];
        }

        /** Pfeil, wohin der Wind weht (leer bei Windstille). */
        public String windArrow() {
            return windLevel == 0 ? "" : TO_ARROW[windDir];
        }

        public String date(String lang) {
            return "en".equals(lang) ? date.toString()
                    : String.format("%02d.%02d.%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
        }

        /** Kennung für Tagesbestenliste und Speicher, z. B. „2026-11-16“. */
        public String id() {
            return date.toString();
        }
    }

    private Daily() {
    }

    /** Die Bedingungen eines Tages: nur aus dem Datum, also überall gleich. */
    public static Conditions conditions(LocalDate date) {
        SplittableRandom r = new SplittableRandom(date.toEpochDay() * 0x9E3779B97F4A7C15L ^ 0x7A6E5F1DL);
        Layout layout = Layout.values()[r.nextInt(Layout.values().length)];
        int dir = r.nextInt(8);
        int roll = r.nextInt(100);
        int level = roll < 15 ? 0 : (roll < 50 ? 1 : (roll < 85 ? 2 : 3));
        int t = r.nextInt(100);
        Time time = t < 27 ? Time.MORGEN : (t < 54 ? Time.MITTAG : (t < 80 ? Time.ABEND : Time.NACHT));
        Weather weather = r.nextInt(100) < 30 ? Weather.REGEN : Weather.KLAR;
        double radius = r.nextInt(3) == 0 ? 3.4 : 4.0;
        List<Integer> moving = new ArrayList<>();
        int count = 1 + r.nextInt(2);
        while (moving.size() < count) {
            int k = MOVING[r.nextInt(MOVING.length)];
            if (!moving.contains(k)) {
                moving.add(k);
            }
        }
        moving.sort(null);
        boolean circles = r.nextBoolean();
        int gate = r.nextInt(3) == 0 ? -1 : GATES[r.nextInt(GATES.length)];
        Set<Integer> bonus = new LinkedHashSet<>();
        int nb = 1 + r.nextInt(2);
        while (bonus.size() < nb) {
            bonus.add(r.nextInt(BONUS.length));
        }
        List<Integer> b = new ArrayList<>(bonus);
        b.sort(null);
        return new Conditions(date, layout, dir, level, time, weather, radius, List.copyOf(moving), circles, gate,
                List.copyOf(b));
    }

    /** Die Strecke eines Tages (ohne Landschaft gerechnet; Medaillen mit Landschaft rechnet {@link World#daily}). */
    public static Course build(Conditions c) {
        return course(c.layout(), c, false);
    }

    /** Strecke des Tages mit den Bedingungen, die wirklich gelten. */
    public record Today(Conditions conditions, Course course) {
    }

    /** Reihenfolge, in der erleichtert wird: meist liegt es am Wind, dann an Tor, Bonus-Ring oder Bewegung. */
    private static final String STEPS = "wtbwmbwr";

    /**
     * Etwas leichtere Bedingungen, falls die Prüf-Piloten mit den gewürfelten nicht durchkommen (z. B. Sturm gegen
     * einen Bonus-Ring): Schritt {@code step} nimmt der Reihe nach etwas weg – Wind eine Stufe schwächer, das Tor, einen
     * Bonus-Ring, die Bewegung, größere Ringe. Auch das folgt nur aus dem Datum – für alle gleich. null, wenn es nichts
     * mehr zu erleichtern gibt.
     */
    public static Conditions easier(Conditions c, int step) {
        Conditions out = c;
        for (int i = 0; i < Math.min(step, STEPS.length()); i++) {
            out = easier(out, STEPS.charAt(i));
        }
        return step > STEPS.length() ? null : out;
    }

    private static Conditions easier(Conditions c, char what) {
        return switch (what) {
            case 'w' -> c.windLevel() == 0 ? c : new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel() - 1,
                    c.time(), c.weather(), c.radius(), c.moving(), c.circles(), c.gate(), c.bonus());
            case 't' -> new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel(), c.time(), c.weather(),
                    c.radius(), c.moving(), c.circles(), -1, c.bonus());
            case 'b' -> c.bonus().isEmpty() ? c : new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel(),
                    c.time(), c.weather(), c.radius(), c.moving(), c.circles(), c.gate(),
                    c.bonus().subList(0, c.bonus().size() - 1));
            case 'm' -> new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel(), c.time(), c.weather(),
                    c.radius(), List.of(), c.circles(), c.gate(), c.bonus());
            default -> new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel(), c.time(), c.weather(), 4.0,
                    c.moving(), c.circles(), c.gate(), c.bonus());
        };
    }

    /** Ganz ohne Zusätze (falls nichts anderes geht). */
    public static Conditions plain(Conditions c) {
        return new Conditions(c.date(), c.layout(), c.windDir(), 0, c.time(), c.weather(), 4.0, List.of(), false, -1,
                List.of());
    }

    /** Alt: ein Schritt leichter (für Tests). */
    static Conditions easier(Conditions c) {
        if (!c.bonus().isEmpty()) {
            return new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel(), c.time(), c.weather(), c.radius(),
                    c.moving(), c.circles(), c.gate(), c.bonus().subList(0, c.bonus().size() - 1));
        }
        if (c.gate() >= 0) {
            return new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel(), c.time(), c.weather(), c.radius(),
                    c.moving(), c.circles(), -1, c.bonus());
        }
        if (!c.moving().isEmpty()) {
            return new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel(), c.time(), c.weather(), c.radius(),
                    List.of(), c.circles(), c.gate(), c.bonus());
        }
        if (c.windLevel() > 0) {
            return new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel() - 1, c.time(), c.weather(),
                    c.radius(), c.moving(), c.circles(), c.gate(), c.bonus());
        }
        if (c.radius() < 4.0) {
            return new Conditions(c.date(), c.layout(), c.windDir(), c.windLevel(), c.time(), c.weather(), 4.0,
                    c.moving(), c.circles(), c.gate(), c.bonus());
        }
        return null;
    }

    /**
     * Eine Richtung des Tals mit allen Bonus-Stellen und ohne Wind, Bewegung und Tor – so wird die Landschaft geformt,
     * damit jede Tagesstrecke frei ist.
     */
    static Course canonical(Layout layout) {
        return course(layout, null, true);
    }

    private static Course course(Layout layout, Conditions c, boolean allBonus) {
        boolean rev = layout.reversed;
        boolean mir = layout.mirrored;
        CourseBuilder b = new CourseBuilder(ID, "Tagesflug", "Daily Flight", Difficulty.SCHWER)
                .text("Jeden Tag anders: Richtung, Wind, Tageszeit, Wetter und Ringe ergeben sich aus dem Datum – "
                        + "für alle gleich. Eigene Tages-Bestenliste!",
                        "Different every day: direction, wind, time of day, weather and rings follow from the date – the "
                        + "same for everyone. With its own daily leaderboard!")
                .icon("clock").biome("minecraft:meadow").radius(c == null ? 4.0 : c.radius()).maxOff(30)
                .day(c == null ? "muster" : c.id());
        if (c != null) {
            b.wind(c.wind());
        }
        double sx = rev ? EAST : WEST;
        b.start(sx, TOWER, z(AXIS, mir) + 0.5, rev ? 90 : -90, 5, 1.3, 2).pitch(4);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < WAY.length; i++) {
            order.add(rev ? WAY.length - 1 - i : i);
        }
        for (int step = 0; step < order.size(); step++) {
            int k = order.get(step);
            // Aufwind vor diesem Wegpunkt?
            for (double[] u : UPDRAFT) {
                int before = rev ? k + 1 : k - 1;
                boolean between = before >= 0 && before < WAY.length
                        && Math.min(WAY[before][0], WAY[k][0]) < u[0] && u[0] < Math.max(WAY[before][0], WAY[k][0]);
                if (between) {
                    b.updraft(u[0], z(u[1], mir), 8, 1.35, 60, 225);
                }
            }
            // Bonus-Stellen vor diesem Wegpunkt (Normalrichtung: zwischen i und i+1)
            for (int bi = 0; bi < BONUS.length; bi++) {
                int after = (int) BONUS[bi][0];
                boolean here = rev ? k == after : k == after + 1;
                if (here && (allBonus || c.bonus().contains(bi))) {
                    b.bonus(BONUS[bi][1], z(BONUS[bi][2], mir), 1500);
                }
            }
            double x = WAY[k][0];
            double zz = z(WAY[k][1], mir);
            if (k == (rev ? BOOST_REVERSED : BOOST)) {
                b.boost(x, zz, 2.1, 6);
            } else if (c != null && c.gate() == k) {
                b.gate(x, zz, 84, 56);
            } else if (c != null && c.moving().contains(k)) {
                if (c.circles()) {
                    b.circle(x, zz, 2.0, 90);
                } else {
                    b.glide(x, zz, 2.4, 80, k % 2 == 0);
                }
            } else {
                b.ring(x, zz);
            }
        }
        double[] last = WAY[rev ? 0 : WAY.length - 1];
        double[] prev = WAY[rev ? 1 : WAY.length - 2];
        double dx = last[0] - prev[0];
        double dz = last[1] - prev[1];
        double len = Math.hypot(dx, dz);
        double fx = last[0] + dx / len * FINISH;
        double fz = last[1] + dz / len * FINISH;
        double lx = last[0] + dx / len * LANDING;
        double lz = last[1] + dz / len * LANDING;
        float yaw = new Vec(dx, 0, mir ? -dz : dz).yaw();
        return b.finish(fx, z(fz, mir), 5).landingBelow(lx, z(lz, mir), yaw, 3).build(4);
    }

    /** Spiegeln an der Achse des Tals. */
    private static double z(double z, boolean mirror) {
        return mirror ? 2 * AXIS - z : z;
    }

    /** Formt das Tal: Grate links und rechts, Hochfläche, Wiesen. */
    static void shape(Terrain t) {
        double mid = (WEST + EAST) / 2;
        t.shape(new Terrain.Plateau(mid, AXIS, 320, 80, 176, 6));
        t.shape(new Terrain.Mound(mid, AXIS - 150, 260, 262, 1.6, 18));
        t.shape(new Terrain.Mound(mid, AXIS + 150, 260, 258, 1.6, 18));
        t.shape(new Terrain.Mound(WEST, AXIS, 34, TOWER - 22, 1.4, 4));
        t.shape(new Terrain.Mound(EAST, AXIS, 34, TOWER - 22, 1.4, 4));
        t.region(new Terrain.Region(mid, AXIS, 340, Terrain.Style.ALM));
    }

    /**
     * Die vier Richtungen als Entwürfe für die Landschaft: Schneisen für alle Flüge samt Bonus-Flügen, Starttürme an
     * beiden Enden, vier Landeplätze, Aufwind-Sockel und ein paar Hütten.
     */
    static List<Design> designs() {
        List<Design> out = new ArrayList<>();
        for (Layout l : Layout.values()) {
            Design.Shaper shaper = l == Layout.NORMAL ? Daily::shape : t -> { };
            out.add(new Design(canonical(l), shaper, Design.cut(14, 14, 0.75), Daily::scenery, "purple_concrete", null));
        }
        return out;
    }

    /** Ein paar Hütten am Talrand und Heustadel (abseits jeder Schneise). */
    private static void scenery(World w, Course c) {
        if (c.start.line().x() > (WEST + EAST) / 2 || c.start.line().z() != AXIS + 0.5) {
            return;
        }
        SplittableRandom r = new SplittableRandom(29);
        int placed = 0;
        for (int k = 0; k < 60 && placed < 7; k++) {
            int x = 300 + r.nextInt(500);
            int z = (int) AXIS - 90 + r.nextInt(180);
            if (w.terrain().corridorDistance(x, z) < 34) {
                continue;
            }
            int g = w.terrain().height(x, z);
            if (g > w.terrain().skyLimit(x, z) - 14 || g > 200) {
                continue;
            }
            if (placed % 3 == 2) {
                Scenery.barn(w.plan(), w.terrain(), x, z, 6, 5);
            } else {
                Scenery.hut(w.plan(), w.terrain(), x, z, 8, 7, placed % 2 == 0, 300 + k);
            }
            w.trees().block(x - 4, z - 4, x + 12, z + 12);
            placed++;
        }
    }

    /** Fliegt der einfache Pilot die Tagesstrecke bis ins Ziel (für die Prüfung)? */
    static FlightSim.Result check(Course c, FlightSim.Solid solid) {
        return FlightSim.fly(c.start.line(), c.start.launch(), c.air(), de.moinmorni.adlerflug.course.Pilots.aim(c),
                solid, 8000);
    }
}
