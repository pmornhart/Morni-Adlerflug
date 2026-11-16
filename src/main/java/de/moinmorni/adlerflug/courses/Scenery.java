package de.moinmorni.adlerflug.courses;

import de.moinmorni.adlerflug.course.Blk;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Plan;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.land.Terrain;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Bausteine für die Welt: Ringe, Starttürme mit Schanze, Landeplätze, Aufwind-Sockel, Hütten, Kapelle und mehr. Alles
 * schreibt in den {@link Plan}; die Landschaft kommt von {@link Terrain}.
 */
public final class Scenery {

    /** Farben der Abschnitte: Gelb, Orange, Rot, Magenta. */
    public static final String[] SECTION_BLOCKS = {"yellow_concrete", "orange_concrete", "red_concrete",
        "magenta_concrete"};
    public static final String[] SECTION_GLASS = {"yellow_stained_glass", "orange_stained_glass",
        "red_stained_glass", "magenta_stained_glass"};
    public static final String[] SECTION_COLOURS = {"#ffd23f", "#ff8a3d", "#ff4d5e", "#d65fd8"};
    /** Band je Abschnitt: helle und satte Farbe (oben → unten). */
    static final String[][] SECTION_PAIRS = {{"yellow_concrete", "orange_concrete"}, {"orange_concrete",
        "red_concrete"}, {"red_concrete", "magenta_concrete"}, {"magenta_concrete", "purple_concrete"}};
    /** Leuchtender Saum je Abschnitt. */
    static final String[] SECTION_LIGHTS = {"ochre_froglight[axis=y]", "shroomlight", "redstone_lamp[lit=true]",
        "pearlescent_froglight[axis=y]"};
    public static final String BOOST_BLOCK = "light_blue_concrete";
    public static final String BOOST_COLOUR = "#5ad1ff";
    public static final String FINISH_COLOUR = "#ffffff";
    public static final String BONUS_COLOUR = "#7cff6b";
    public static final String GATE_COLOUR = "#ffcf33";

    private Scenery() {
    }

    // =========================================================================================
    // Ringe
    // =========================================================================================

    /**
     * Ein Teil eines Rings als Block-Anzeige: ein gestreckter Quader mit Mitte {@code at} (relativ zur Ring-Mitte),
     * Längsachse {@code along}, Querachse {@code out} (vom Ring nach außen) und {@code depth} (Flugrichtung), Maße
     * {@code length} × {@code thick} × {@code deep}.
     */
    public record RingPart(String block, Vec at, Vec along, Vec out, Vec depth, double length, double thick, double deep) {
    }

    /**
     * Flügel eines Takt-Tors: zwei Platten (Eisen mit rotem Rand innen), die von links und rechts in den Ring gleiten.
     * {@code gap} ist der Abstand der Innenkanten von der Ringmitte ({@link Ring#passes}: durch kommt, wer dazwischen
     * fliegt); bei {@code gap = 0} ist der Ring ganz zu. Lage wie {@link #ringParts} relativ zur Ringmitte.
     */
    public static List<RingPart> gateWings(Ring r, double gap) {
        List<RingPart> out = new ArrayList<>();
        Vec u = r.across();
        Vec v = r.up();
        Vec n = r.normal();
        double width = r.radius() + 1.6;
        double height = 2 * (r.radius() + 1.0);
        double edge = 0.35;
        for (int s : new int[] {-1, 1}) {
            Vec at = u.mul(s * (gap + edge + width / 2));
            out.add(new RingPart("iron_block", at, u, v, n, width, height, 0.22));
            out.add(new RingPart("redstone_block", u.mul(s * (gap + edge / 2)), u, v, n, edge, height, 0.3));
        }
        return out;
    }

    /** Wie viele Teile ein Band rund wirken lassen (Abweichung vom Kreis höchstens ein paar Hundertstel). */
    private static int segments(double radius, double minimum) {
        return (int) Math.max(minimum, Math.ceil(2 * Math.PI * radius / 0.95));
    }

    /**
     * Teile eines Rings: ein rundes Band in der Farbe des Abschnitts (oben heller, unten satter), innen ein feiner
     * Goldrand, außen ein leuchtender Saum und kleine Leuchtsteine rundherum. Schub-Ringe himmelblau, das Ziel
     * schwarz-weiß kariert mit Gold. Ohne Bukkit (Anzeigen erzeugt {@code world.RingDisplays}).
     */
    public static List<RingPart> ringParts(Ring r) {
        List<RingPart> out = new ArrayList<>();
        Vec n = r.normal();
        Vec u = Math.abs(n.y()) > 0.95 ? new Vec(1, 0, 0) : Vec.UP.cross(n).normalize();
        Vec v = n.cross(u).normalize();
        boolean finish = r.kind() == Ring.Kind.FINISH;
        double rad = r.radius();
        // Band
        double bandR = rad + 0.95;
        double bandThick = finish ? 0.75 : 0.6;
        double bandDeep = finish ? 0.9 : 0.7;
        int bandN = segments(bandR, 40);
        band(out, u, v, n, bandR, bandThick, bandDeep, bandN, k -> bandBlock(r, k, bandN));
        // innen Gold, außen ein heller Saum
        int trimN = segments(rad + 0.5, 32);
        band(out, u, v, n, rad + 0.55, 0.16, bandDeep + 0.14, trimN, k -> finish ? "gold_block" : innerTrim(r));
        band(out, u, v, n, bandR + bandThick / 2 + 0.08, 0.14, bandDeep * 0.6, trimN, k -> outerTrim(r));
        // Leuchtsteine
        int studs = finish ? 16 : (rad >= 5 ? 12 : 8);
        double size = finish ? 0.5 : 0.42;
        for (int k = 0; k < studs; k++) {
            double a = 2 * Math.PI * k / studs + Math.PI / studs;
            Vec radial = u.mul(Math.cos(a)).add(v.mul(Math.sin(a)));
            Vec tangent = u.mul(-Math.sin(a)).add(v.mul(Math.cos(a)));
            out.add(new RingPart(stud(r), radial.mul(bandR), tangent, radial, n, size, size, bandDeep + 0.24));
        }
        return out;
    }

    private static void band(List<RingPart> out, Vec u, Vec v, Vec n, double radius, double thick, double deep, int count,
                             java.util.function.IntFunction<String> block) {
        // Länge an der Außenkante gemessen, damit zwischen den Teilen keine Spalten bleiben
        double length = 2 * (radius + thick / 2) * Math.tan(Math.PI / count) * 1.04;
        for (int k = 0; k < count; k++) {
            double a = 2 * Math.PI * k / count;
            Vec radial = u.mul(Math.cos(a)).add(v.mul(Math.sin(a)));
            Vec tangent = u.mul(-Math.sin(a)).add(v.mul(Math.cos(a)));
            out.add(new RingPart(block.apply(k), radial.mul(radius), tangent, radial, n, length, thick, deep));
        }
    }

    /** Band: oben die helle Farbe des Abschnitts, unten die satte – mit weichem Übergang (gestreut). */
    private static String bandBlock(Ring r, int k, int count) {
        if (r.kind() == Ring.Kind.FINISH) {
            return (k / Math.max(1, count / 16)) % 2 == 0 ? "white_concrete" : "black_concrete";
        }
        if (r.gate() != null) {
            // Takt-Tor: schwarz-gelb gestreift wie eine Schranke
            return (k / Math.max(1, count / 20)) % 2 == 0 ? "yellow_concrete" : "black_concrete";
        }
        String[] pair = switch (r.kind()) {
            case BOOST -> new String[] {"light_blue_concrete", "cyan_concrete"};
            case BONUS -> new String[] {"lime_concrete", "green_concrete"};
            default -> SECTION_PAIRS[Math.min(SECTION_PAIRS.length - 1, r.section())];
        };
        double up = Math.sin(2 * Math.PI * k / count); // +1 oben, −1 unten
        double mix = (up + 1) / 2;
        // Ordnung statt Zufall, damit sich das Muster nicht bei jedem Start ändert
        double dither = ((k * 7) % 5) / 5.0 * 0.5 + 0.25;
        return mix > dither ? pair[0] : pair[1];
    }

    private static String innerTrim(Ring r) {
        if (r.gate() != null) {
            return "iron_block";
        }
        return switch (r.kind()) {
            case BOOST -> "diamond_block";
            case BONUS -> "emerald_block";
            default -> "gold_block";
        };
    }

    private static String outerTrim(Ring r) {
        if (r.gate() != null) {
            return "redstone_lamp[lit=true]";
        }
        return switch (r.kind()) {
            case FINISH -> "gold_block";
            case BOOST -> "sea_lantern";
            case BONUS -> "verdant_froglight[axis=y]";
            case NORMAL -> SECTION_LIGHTS[Math.min(SECTION_LIGHTS.length - 1, r.section())];
        };
    }

    private static String stud(Ring r) {
        if (r.gate() != null) {
            return "redstone_lamp[lit=true]";
        }
        return switch (r.kind()) {
            case FINISH, BOOST -> "sea_lantern";
            case BONUS -> "glowstone";
            case NORMAL -> r.motion() != null ? "amethyst_block" : "pearlescent_froglight[axis=y]";
        };
    }

    /** Farbe eines Rings für Texte (#rrggbb). */
    public static String ringColour(Ring r) {
        if (r.gate() != null) {
            return GATE_COLOUR;
        }
        return switch (r.kind()) {
            case FINISH -> FINISH_COLOUR;
            case BOOST -> BOOST_COLOUR;
            case BONUS -> BONUS_COLOUR;
            case NORMAL -> SECTION_COLOURS[Math.min(SECTION_COLOURS.length - 1, r.section())];
        };
    }

    // =========================================================================================
    // Startturm mit Schanze
    // =========================================================================================

    /**
     * Startturm: Holzturm bis zur Absprungkante, oben eine Plattform mit Anlauf, Geländer, Torbogen mit Fahnen. Der
     * Boden der Plattform liegt einen Block unter den Füßen.
     */
    public static void startTower(Plan plan, Terrain terrain, Course c, String accent) {
        Course.Start s = c.start;
        Vec dir = Vec.look(s.yaw(), 0);
        Vec side = new Vec(dir.z(), 0, -dir.x());
        int floor = (int) Math.floor(s.line().y()) - 1;
        Vec back = s.line().sub(dir.mul(s.line().horizontalDistance(s.stand()) + 3));
        double length = s.line().horizontalDistance(back);
        // Plattform und Anlauf (5 breit), vorne leicht abfallend wie eine Schanze
        for (double t = 0; t <= length + 0.01; t += 0.5) {
            for (double w = -2; w <= 2.01; w += 0.5) {
                Vec p = back.add(dir.mul(t)).add(side.mul(w));
                int x = (int) Math.floor(p.x());
                int z = (int) Math.floor(p.z());
                String floorBlock = Math.abs(w) > 1.6 ? "spruce_planks" : (Math.abs(w) < 0.6 ? accent : "birch_planks");
                plan.set(x, floor, z, floorBlock);
                plan.set(x, floor - 1, z, "spruce_planks");
                plan.set(x, floor + 1, z, Blk.AIR);
                plan.set(x, floor + 2, z, Blk.AIR);
                plan.set(x, floor + 3, z, Blk.AIR);
            }
            for (double w : new double[] {-2.6, 2.6}) {
                Vec p = back.add(dir.mul(t)).add(side.mul(w));
                plan.set((int) Math.floor(p.x()), floor + 1, (int) Math.floor(p.z()), "spruce_fence");
            }
        }
        // Rückwand hinter dem Stehplatz
        for (double w = -2.6; w <= 2.61; w += 0.5) {
            Vec p = back.add(side.mul(w)).sub(dir.mul(0.5));
            plan.set((int) Math.floor(p.x()), floor + 1, (int) Math.floor(p.z()), "spruce_fence");
        }
        // Stützen bis zum Boden: vier Ecken + Mitte vorne, mit Querstreben
        double[][] legs = {{0, -2.5}, {0, 2.5}, {length, -2.5}, {length, 2.5}, {length / 2, -2.5}, {length / 2, 2.5}};
        for (double[] l : legs) {
            Vec p = back.add(dir.mul(l[0])).add(side.mul(l[1]));
            int x = (int) Math.floor(p.x());
            int z = (int) Math.floor(p.z());
            int ground = terrain.height(x, z);
            for (int y = ground + 1; y < floor; y++) {
                plan.set(x, y, z, "stripped_spruce_log[axis=y]");
            }
            plan.set(x, ground, z, "cobblestone");
        }
        for (int y = floor - 6; y > terrain.height((int) back.x(), (int) back.z()) + 2; y -= 6) {
            for (double t = 0; t <= length; t += 0.5) {
                for (double w : new double[] {-2.5, 2.5}) {
                    Vec p = back.add(dir.mul(t)).add(side.mul(w));
                    plan.setIfEmpty((int) Math.floor(p.x()), y, (int) Math.floor(p.z()), Blk.of("spruce_planks"));
                }
            }
        }
        // Torbogen über der Kante: zwei Pfosten, Balken, Fahnen
        Vec lineL = s.line().add(side.mul(-2.6)).sub(dir.mul(0.5));
        Vec lineR = s.line().add(side.mul(2.6)).sub(dir.mul(0.5));
        for (int y = floor + 1; y <= floor + 5; y++) {
            plan.set((int) Math.floor(lineL.x()), y, (int) Math.floor(lineL.z()), "stripped_spruce_log[axis=y]");
            plan.set((int) Math.floor(lineR.x()), y, (int) Math.floor(lineR.z()), "stripped_spruce_log[axis=y]");
        }
        for (double w = -2.6; w <= 2.61; w += 0.5) {
            Vec p = s.line().add(side.mul(w)).sub(dir.mul(0.5));
            plan.set((int) Math.floor(p.x()), floor + 6, (int) Math.floor(p.z()), accent);
        }
        String axis = Math.abs(dir.x()) > Math.abs(dir.z()) ? "z" : "x";
        plan.set((int) Math.floor(lineL.x()), floor + 6, (int) Math.floor(lineL.z()), "stripped_spruce_log[axis=" + axis + "]");
        plan.set((int) Math.floor(lineR.x()), floor + 6, (int) Math.floor(lineR.z()), "stripped_spruce_log[axis=" + axis + "]");
        plan.set((int) Math.floor(lineL.x()), floor + 7, (int) Math.floor(lineL.z()), "lantern");
        plan.set((int) Math.floor(lineR.x()), floor + 7, (int) Math.floor(lineR.z()), "lantern");
    }

    // =========================================================================================
    // Landeplatz, Aufwind, Wegweiser
    // =========================================================================================

    /** Landeplatz: runde Wiese mit Steinkreis, weiß-blauen Fahnen und Bänken. */
    public static void landing(Plan plan, Terrain terrain, Course c) {
        Vec a = c.landing.at();
        int cx = (int) Math.floor(a.x());
        int cz = (int) Math.floor(a.z());
        int y = (int) Math.floor(a.y()) - 1;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 7.4) {
                    continue;
                }
                plan.set(cx + dx, y, cz + dz, d > 6.2 ? "polished_andesite" : (d < 1.5 ? "gold_block"
                        : (Math.floorMod(dx + dz, 2) == 0 ? "white_concrete" : "light_blue_concrete")));
                for (int k = 1; k <= 6; k++) {
                    plan.set(cx + dx, y + k, cz + dz, Blk.AIR);
                }
                for (int k = 1; k <= 3; k++) {
                    plan.setIfEmpty(cx + dx, y - k, cz + dz, Blk.of("dirt"));
                }
            }
        }
        for (int[] f : new int[][] {{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
            flagPole(plan, cx + f[0], y + 1, cz + f[1], 6, Math.abs(f[0] + f[1]) == 12 ? "white_wool" : "light_blue_wool");
        }
    }

    /** Fahnenmast mit kleiner Fahne. */
    public static void flagPole(Plan plan, int x, int y, int z, int height, String cloth) {
        for (int k = 0; k < height; k++) {
            plan.set(x, y + k, z, "spruce_fence");
        }
        plan.set(x, y + height, z, "lightning_rod[facing=up]");
        plan.set(x + 1, y + height - 1, z, cloth);
        plan.set(x + 1, y + height - 2, z, cloth.startsWith("white") ? "light_blue_wool" : "white_wool");
        plan.set(x + 2, y + height - 1, z, cloth.startsWith("white") ? "light_blue_wool" : "white_wool");
        plan.set(x + 2, y + height - 2, z, cloth);
    }

    /** Sockel einer Aufwind-Säule: heller Steinkreis mit Lagerfeuern (Rauch steigt auf). */
    public static void updraftBase(Plan plan, Terrain terrain, Updraft u) {
        int cx = (int) Math.floor(u.x());
        int cz = (int) Math.floor(u.z());
        int r = (int) Math.round(u.radius());
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.4) {
                    continue;
                }
                int g = terrain.height(cx + dx, cz + dz);
                if (g > u.top() - 4) {
                    continue;
                }
                if (d > r - 0.8) {
                    plan.set(cx + dx, g, cz + dz, "calcite");
                } else if (d < 1.5) {
                    plan.set(cx + dx, g, cz + dz, "magma_block");
                    plan.set(cx + dx, g + 1, cz + dz, Blk.AIR);
                } else if (Math.floorMod(dx * 3 + dz * 5, 7) == 0) {
                    plan.set(cx + dx, g, cz + dz, "smooth_basalt");
                    plan.set(cx + dx, g + 1, cz + dz, "campfire[lit=true,signal_fire=true]");
                } else {
                    plan.set(cx + dx, g, cz + dz, d < r - 2 ? "smooth_stone" : "diorite");
                    plan.set(cx + dx, g + 1, cz + dz, Blk.AIR);
                }
            }
        }
    }

    // =========================================================================================
    // Häuser
    // =========================================================================================

    /**
     * Almhütte: Steinsockel, Holzwände, Satteldach mit Überstand, Fenster mit Blumenkästen, Bank vor der Tür. Steht auf
     * dem Boden bei (x, z), Giebel zeigt nach {@code alongX ? x : z}.
     */
    public static void hut(Plan plan, Terrain terrain, int x, int z, int w, int d, boolean alongX, long seed) {
        int ground = Integer.MAX_VALUE;
        int top = Integer.MIN_VALUE;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                int g = terrain.height(x + dx, z + dz);
                ground = Math.min(ground, g);
                top = Math.max(top, g);
            }
        }
        int base = top + 1;
        SplittableRandom r = new SplittableRandom(seed);
        // Sockel bis in den Hang
        for (int dx = -1; dx <= w; dx++) {
            for (int dz = -1; dz <= d; dz++) {
                for (int y = ground - 1; y < base; y++) {
                    plan.set(x + dx, y, z + dz, r.nextInt(5) == 0 ? "mossy_cobblestone" : "cobblestone");
                }
            }
        }
        int wallTop = base + 3;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                boolean edge = dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1;
                boolean corner = (dx == 0 || dx == w - 1) && (dz == 0 || dz == d - 1);
                plan.set(x + dx, base - 1, z + dz, "spruce_planks");
                for (int y = base; y <= wallTop; y++) {
                    if (!edge) {
                        plan.set(x + dx, y, z + dz, Blk.AIR);
                        continue;
                    }
                    String b = corner ? "stripped_spruce_log[axis=y]" : (y == base ? "stone_bricks" : "spruce_planks");
                    boolean window = !corner && y == base + 1 && ((alongX ? dx : dz) % 3 == 1);
                    plan.set(x + dx, y, z + dz, window ? "glass_pane" : b);
                }
            }
        }
        // Tür in der Mitte einer langen Seite, Bank daneben
        int doorX = alongX ? x + w / 2 : x;
        int doorZ = alongX ? z : z + d / 2;
        plan.set(doorX, base, doorZ, "spruce_door[half=lower,facing=" + (alongX ? "north" : "west") + "]");
        plan.set(doorX, base + 1, doorZ, "spruce_door[half=upper,facing=" + (alongX ? "north" : "west") + "]");
        // Satteldach (dunkle Eiche) mit Überstand
        int span = alongX ? d : w;
        int len = alongX ? w : d;
        for (int k = 0; k <= span / 2 + 1; k++) {
            int y = wallTop + 1 + k;
            for (int a = -1; a <= len; a++) {
                for (int side : new int[] {-1 + k, span - k}) {
                    int bx = alongX ? x + a : x + side;
                    int bz = alongX ? z + side : z + a;
                    String facing = alongX ? (side < span / 2.0 ? "south" : "north") : (side < span / 2.0 ? "east" : "west");
                    if (side > span - 1 - k + 1 && side < -1 + k) {
                        continue;
                    }
                    plan.set(bx, y, bz, "dark_oak_stairs[facing=" + facing + "]");
                }
                // Giebelwand
                if (a == 0 || a == len - 1) {
                    for (int s = k; s < span - k; s++) {
                        int bx = alongX ? x + a : x + s;
                        int bz = alongX ? z + s : z + a;
                        plan.setIfEmpty(bx, y, bz, Blk.of("spruce_planks"));
                    }
                }
            }
            if (k * 2 + 2 >= span) {
                for (int a = -1; a <= len; a++) {
                    int bx = alongX ? x + a : x + span / 2;
                    int bz = alongX ? z + span / 2 : z + a;
                    plan.set(bx, y + 1, bz, "dark_oak_slab[type=bottom]");
                }
                break;
            }
        }
        // Blumenkästen unter den Fenstern
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                Blk b = plan.get(x + dx, base + 1, z + dz);
                if (b != null && b.data().contains("glass_pane")) {
                    int ox = dz == 0 ? 0 : (dz == d - 1 ? 0 : (dx == 0 ? -1 : 1));
                    int oz = dz == 0 ? -1 : (dz == d - 1 ? 1 : 0);
                    plan.setIfEmpty(x + dx + ox, base, z + dz + oz, Blk.of("spruce_trapdoor[half=top,open=false]"));
                    plan.setIfEmpty(x + dx + ox, base + 1, z + dz + oz,
                            Blk.deco(r.nextBoolean() ? "potted_red_tulip" : "potted_blue_orchid"));
                }
            }
        }
        // Kamin
        plan.set(x + w - 2, wallTop + span / 2 + 1, z + d - 2, "bricks");
        plan.set(x + w - 2, wallTop + span / 2 + 2, z + d - 2, "bricks");
        plan.set(x + w - 2, wallTop + span / 2 + 3, z + d - 2, "campfire[lit=true]");
    }

    /** Heustadel: offenes Holzgerüst mit Heuballen. */
    public static void barn(Plan plan, Terrain terrain, int x, int z, int w, int d) {
        int base = terrain.height(x + w / 2, z + d / 2) + 1;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                int g = terrain.height(x + dx, z + dz);
                for (int y = g; y < base; y++) {
                    plan.set(x + dx, y, z + dz, "dirt");
                }
                plan.set(x + dx, base - 1, z + dz, "spruce_planks");
                boolean edge = dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1;
                for (int y = base; y < base + 4; y++) {
                    if (edge && (dx % 2 == 0 || dz % 2 == 0)) {
                        plan.set(x + dx, y, z + dz, (dx == 0 || dx == w - 1) && (dz == 0 || dz == d - 1)
                                ? "spruce_log[axis=y]" : "spruce_fence");
                    }
                }
                if (!edge && dx % 2 == 1) {
                    plan.set(x + dx, base, z + dz, "hay_block[axis=x]");
                    if (dz % 3 != 0) {
                        plan.set(x + dx, base + 1, z + dz, "hay_block[axis=z]");
                    }
                }
                plan.set(x + dx, base + 4, z + dz, "spruce_slab[type=bottom]");
            }
        }
    }

    /** Kapelle mit Zwiebelturm (klein, weiß, rotes Dach). */
    public static void chapel(Plan plan, Terrain terrain, int x, int z) {
        int base = terrain.height(x + 3, z + 4) + 1;
        for (int dx = -1; dx <= 7; dx++) {
            for (int dz = -1; dz <= 9; dz++) {
                for (int y = terrain.height(x + dx, z + dz); y < base; y++) {
                    plan.set(x + dx, y, z + dz, "stone_bricks");
                }
            }
        }
        for (int dx = 0; dx <= 6; dx++) {
            for (int dz = 0; dz <= 8; dz++) {
                boolean edge = dx == 0 || dx == 6 || dz == 0 || dz == 8;
                plan.set(x + dx, base - 1, z + dz, "polished_andesite");
                for (int y = base; y < base + 5; y++) {
                    if (edge) {
                        boolean window = (dz == 3 || dz == 5) && y >= base + 1 && y <= base + 3 && (dx == 0 || dx == 6);
                        plan.set(x + dx, y, z + dz, window ? "light_blue_stained_glass_pane" : "white_concrete");
                    } else {
                        plan.set(x + dx, y, z + dz, Blk.AIR);
                    }
                }
            }
        }
        plan.set(x + 3, base, z, Blk.AIR);
        plan.set(x + 3, base + 1, z, Blk.AIR);
        plan.set(x + 3, base, z, "oak_door[half=lower,facing=north]");
        plan.set(x + 3, base + 1, z, "oak_door[half=upper,facing=north]");
        // Dach
        for (int k = 0; k <= 3; k++) {
            for (int dz = -1; dz <= 9; dz++) {
                plan.set(x - 1 + k, base + 5 + k, z + dz, "red_nether_brick_stairs[facing=east]");
                plan.set(x + 7 - k, base + 5 + k, z + dz, "red_nether_brick_stairs[facing=west]");
                if (k == 3) {
                    plan.set(x + 3, base + 8, z + dz, "red_nether_brick_slab[type=bottom]");
                }
            }
            for (int dx = k; dx <= 6 - k; dx++) {
                plan.setIfEmpty(x + dx, base + 5 + k, z, Blk.of("white_concrete"));
                plan.setIfEmpty(x + dx, base + 5 + k, z + 8, Blk.of("white_concrete"));
            }
        }
        // Turm über dem Eingang mit Zwiebel
        int tx = x + 2;
        int tz = z - 3;
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                for (int y = terrain.height(tx + dx, tz + dz); y < base + 11; y++) {
                    plan.set(tx + dx, y, tz + dz, dx == 1 && dz == 1 && y > base + 8 ? "bell[attachment=ceiling]"
                            : "white_concrete");
                }
            }
        }
        plan.set(tx + 1, base + 9, tz, Blk.AIR);
        plan.set(tx + 1, base + 9, tz + 2, Blk.AIR);
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                if (Math.abs(dx - 1) + Math.abs(dz - 1) <= 3) {
                    plan.set(tx + dx, base + 11, tz + dz, "dark_prismarine");
                }
            }
        }
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                plan.set(tx + dx, base + 12, tz + dz, "dark_prismarine");
            }
        }
        plan.set(tx + 1, base + 13, tz + 1, "dark_prismarine");
        plan.set(tx + 1, base + 14, tz + 1, "gold_block");
        plan.set(tx + 1, base + 15, tz + 1, "lightning_rod[facing=up]");
    }

    /** Zaun um eine Weide (einfacher Kreis). */
    public static void fenceRing(Plan plan, Terrain terrain, int cx, int cz, int r) {
        for (int a = 0; a < 360; a += 2) {
            int x = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * r);
            int z = cz + (int) Math.round(Math.sin(Math.toRadians(a)) * r);
            int g = terrain.height(x, z);
            plan.setIfEmpty(x, g + 1, z, Blk.of("spruce_fence"));
        }
    }
}
