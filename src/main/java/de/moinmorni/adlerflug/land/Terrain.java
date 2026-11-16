package de.moinmorni.adlerflug.land;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Berglandschaft der Adlerflug-Welt, ganz aus Rechnung: Grundgebirge aus Rauschen (Hügel, Bergrücken, Gipfel), dazu
 * Formen der Strecken (Hochebenen, Kuppen, Seen, Bäche) und die Flugschneisen ({@link Corridor}), die jede Strecke
 * freihalten. Ohne Bukkit: Der Welt-Erzeuger fragt hier Höhe, Wasser und Oberfläche ab, die Tests prüfen damit, dass
 * nichts im Weg ist.
 */
public final class Terrain {

    /** Unterste Höhe der Landschaft (darunter nur Fels). */
    public static final int FLOOR = 20;
    /** Ab hier liegt Schnee, darunter Fels oder Wiese. */
    public static final int SNOW = 182;
    /** Bis hier wachsen Bäume. */
    public static final int TREE_LINE = 150;

    /** Art der Oberfläche einer Gegend. */
    public enum Style {
        /** Wiesen, Blumen, Nadelwald (Standard). */
        ALM,
        /** Gletscher: Schnee, Packeis, blanker Fels. */
        GLACIER,
        /** Felsiges Hochgebirge: Fels, Geröll, wenig Gras. */
        ROCK,
        /** Schlucht: Moos, Fels, Farn. */
        GORGE
    }

    /** Eine Form, die die Grundhöhe ändert. */
    public interface Shape {
        double apply(Terrain t, double x, double z, double h);
    }

    /**
     * Hochebene: in der Mitte {@code y} (mit {@code rough} Blöcken Unebenheit), nach außen sanft in die Landschaft.
     */
    public record Plateau(double cx, double cz, double radius, double blend, double y, double rough) implements Shape {
        public Plateau(double cx, double cz, double radius, double blend, double y) {
            this(cx, cz, radius, blend, y, 0);
        }

        @Override
        public double apply(Terrain t, double x, double z, double h) {
            double d = Math.hypot(x - cx, z - cz);
            double w = 1 - Noise.smooth(radius, radius + blend, d);
            if (w <= 0) {
                return h;
            }
            double target = y + (rough == 0 ? 0 : (t.relief(x, z) - 0.5) * 2 * rough);
            return h + (target - h) * w;
        }
    }

    /**
     * Berg: hebt die Landschaft bis etwa {@code peak}, nach außen abfallend (nie senkend), mit Graten und Felsen
     * ({@code rough} Blöcke).
     */
    public record Mound(double cx, double cz, double radius, double peak, double power, double rough) implements Shape {
        public Mound(double cx, double cz, double radius, double peak, double power) {
            this(cx, cz, radius, peak, power, 0);
        }

        @Override
        public double apply(Terrain t, double x, double z, double h) {
            double dx = x - cx;
            double dz = z - cz;
            double dist = Math.hypot(dx, dz);
            if (dist >= radius * 1.25) {
                return h;
            }
            // Grate: der Umriss ist nicht rund, sondern läuft in Rippen aus (je Richtung anders weit)
            double ang = Math.atan2(dz, dx);
            double spread = rough == 0 ? 1 : 0.85 + 0.4 * t.detail.fbm(Math.cos(ang) * 1.7 + cx / 97.0,
                    Math.sin(ang) * 1.7 + cz / 89.0, 3);
            double d = dist / (radius * spread);
            if (d >= 1) {
                return h;
            }
            double k = Math.pow(d, power);
            double top = peak - (peak - h) * k;
            if (rough != 0) {
                double ridge = t.ridges.ridged(x / 55.0 + cx / 31.0, z / 55.0 - cz / 29.0, 4);
                top += ((ridge - 0.45) * 2.2 * rough + (t.relief(x, z) - 0.55) * rough * 0.6) * Math.sqrt(1 - k);
            }
            return Math.max(h, top);
        }
    }

    /** Senke: drückt die Landschaft auf höchstens {@code y} (Talboden, Platz für einen Bau). */
    public record Basin(double cx, double cz, double radius, double blend, double y) implements Shape {
        @Override
        public double apply(Terrain t, double x, double z, double h) {
            double d = Math.hypot(x - cx, z - cz);
            double w = 1 - Noise.smooth(radius, radius + blend, d);
            return h > y ? h + (y - h) * w : h;
        }
    }

    /** Wasser: See (rund) mit Wasserspiegel {@code level}. */
    public record Lake(double cx, double cz, double radius, int level) {
    }

    /** Bach/Fluss: Punkte mit Wasserspiegel (x, z, Spiegel), Breite des Wassers. */
    public record River(List<double[]> points, double width) {
        /** Abstand zur Mittellinie und Wasserspiegel dort (null, wenn weit weg). */
        double[] near(double x, double z) {
            double best = Double.POSITIVE_INFINITY;
            double level = 0;
            for (int i = 0; i + 1 < points.size(); i++) {
                double[] a = points.get(i);
                double[] b = points.get(i + 1);
                double dx = b[0] - a[0];
                double dz = b[1] - a[1];
                double len2 = dx * dx + dz * dz;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / len2));
                double d = Math.hypot(x - (a[0] + dx * t), z - (a[1] + dz * t));
                if (d < best) {
                    best = d;
                    level = a[2] + (b[2] - a[2]) * t;
                }
            }
            return best == Double.POSITIVE_INFINITY ? null : new double[] {best, level};
        }
    }

    /** Wanderweg: Punkte (x, z), halbe Breite – dort liegt ein Trampelpfad statt Wiese. */
    public record Path(List<double[]> points, double width) {
        boolean on(double x, double z) {
            for (int i = 0; i + 1 < points.size(); i++) {
                double[] a = points.get(i);
                double[] b = points.get(i + 1);
                double dx = b[0] - a[0];
                double dz = b[1] - a[1];
                double len2 = dx * dx + dz * dz;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / len2));
                if (Math.hypot(x - (a[0] + dx * t), z - (a[1] + dz * t)) <= width) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Gegend mit eigener Oberfläche. */
    public record Region(double cx, double cz, double radius, Style style) {
    }

    private final long seed;
    private final Noise mask;
    private final Noise hills;
    final Noise ridges;
    final Noise detail;
    private final Noise clouds;
    private final List<Shape> shapes = new ArrayList<>();
    private final List<Shape> late = new ArrayList<>();
    private final List<Corridor> corridors = new ArrayList<>();
    private final List<Lake> lakes = new ArrayList<>();
    private final List<River> rivers = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();
    private final List<Path> paths = new ArrayList<>();

    public Terrain(long seed) {
        this.seed = seed;
        this.mask = new Noise(seed);
        this.hills = new Noise(seed + 1);
        this.ridges = new Noise(seed + 2);
        this.detail = new Noise(seed + 3);
        this.clouds = new Noise(seed + 4);
    }

    public long seed() {
        return seed;
    }

    public void shape(Shape s) {
        shapes.add(s);
    }

    /** Form, die nach den Flugschneisen wirkt (Landeplätze, Bauplätze neben der Strecke). */
    public void shapeLate(Shape s) {
        late.add(s);
    }

    public void corridor(Corridor c) {
        corridors.add(c);
    }

    public void lake(Lake l) {
        lakes.add(l);
    }

    public void river(River r) {
        rivers.add(r);
    }

    public void region(Region r) {
        regions.add(r);
    }

    /** Wanderweg (Punkte x, z) mit halber Breite. */
    public void path(double width, double... xz) {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i + 1 < xz.length; i += 2) {
            pts.add(new double[] {xz[i], xz[i + 1]});
        }
        paths.add(new Path(pts, width));
    }

    /** Liegt hier ein Wanderweg? (Mit kleinen Ausfransungen am Rand.) */
    public boolean onPath(int x, int z) {
        double wobble = (Noise.hash(seed + 21, x, z) - 0.5) * 0.9;
        for (Path p : paths) {
            if (p.on(x + 0.5 + wobble, z + 0.5 - wobble)) {
                return true;
            }
        }
        return false;
    }

    /** Höchster Gipfel der Landschaft (Platz bis zur Weltgrenze für Schnee und Bauten). */
    public static final int PEAK = 292;

    /**
     * Grundgebirge ohne Formen: Täler mit Hügeln, dazwischen Gebirgszüge mit Graten und Schneegipfeln. Die Täler der
     * Strecken schneiden die Flugschneisen hinein ({@link Corridor}).
     */
    public double base(double x, double z) {
        double m = Noise.smooth(-0.55, 0.3, mask.fbm(x / 640.0, z / 640.0, 3));
        double hill = hills.fbm(x / 170.0, z / 170.0, 4) * 18;
        double ridge = ridges.ridged(x / 250.0, z / 250.0, 5);
        double rough = detail.fbm(x / 38.0, z / 38.0, 2) * 2.5;
        double h = 76 + hill + rough + m * (Math.pow(ridge, 1.45) * 205 + 22);
        // ganz oben runder statt abgeschnitten
        return h > PEAK - 30 ? PEAK - 30 + 30 * Math.tanh((h - PEAK + 30) / 30) : h;
    }

    /** Grate und Felsen für Berge (0 … 1). */
    public double relief(double x, double z) {
        return ridges.ridged(x / 90.0 + 40, z / 90.0 - 17, 4) * 0.7 + 0.3 * (0.5 + 0.5 * detail.fbm(x / 25.0, z / 25.0, 2));
    }

    /** Höhe der Landschaft (Oberkante des obersten festen Blocks) bei (x, z). */
    public int height(double x, double z) {
        return (int) Math.floor(heightExact(x, z));
    }

    /** Höhe ohne Runden (für Neigung und Schnee). */
    public double heightExact(double x, double z) {
        double h = base(x, z);
        for (Shape s : shapes) {
            h = s.apply(this, x, z, h);
        }
        double top = Double.POSITIVE_INFINITY;
        for (Corridor c : corridors) {
            top = Math.min(top, c.ceiling(x, z));
        }
        if (h > top) {
            // Talwand nicht glatt abgeschnitten, sondern mit Rinnen und Rippen (nur tiefer, nie höher)
            double gully = 0.5 + 0.5 * ridges.ridged(x / 19.0 - 7, z / 19.0 + 3, 3);
            h = top - gully * 5 * Math.min(1, (h - top) / 14);
        }
        for (Shape s : late) {
            h = s.apply(this, x, z, h);
        }
        for (Lake l : lakes) {
            double d = Math.hypot(x - l.cx(), z - l.cz());
            double edge = l.radius() * (0.88 + 0.12 * detail.at(x / 11.0, z / 11.0));
            if (d < edge + 6) {
                double depth = d < edge ? 2 + 5 * (1 - (d / edge) * (d / edge)) : 0;
                double bank = l.level() - 1 - depth + Math.max(0, d - edge) * 0.9;
                h = Math.min(h, bank);
            }
        }
        for (River r : rivers) {
            double[] n = r.near(x, z);
            if (n != null && n[0] < r.width() + 8) {
                double depth = n[0] < r.width() ? 1.5 + 1.5 * (1 - n[0] / r.width()) : 0;
                h = Math.min(h, Math.floor(n[1]) - depth + Math.max(0, n[0] - r.width()) * 1.2);
            }
        }
        return Math.max(FLOOR, h);
    }

    /** Wasserspiegel bei (x, z) oder {@link Integer#MIN_VALUE}, wenn dort kein Wasser ist. */
    public int water(double x, double z, int ground) {
        for (River r : rivers) {
            double[] n = r.near(x, z);
            if (n != null && n[0] < r.width() + 1 && ground < Math.floor(n[1])) {
                return (int) Math.floor(n[1]);
            }
        }
        for (Lake l : lakes) {
            if (ground < l.level() && Math.hypot(x - l.cx(), z - l.cz()) < l.radius()) {
                return l.level();
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Höhen der Wolkendecken: man fliegt über sie hinweg (nur dort, wo sie unter jeder Flugschneise liegen). */
    public static final int[] CLOUD_LAYERS = {150, 196};
    /** Block der Wolken. */
    public static final String CLOUD = "white_wool";

    /**
     * Wolke bei (x, z): Untergrenze und Dicke je Wolkendecke ({@code out[2·i]}, {@code out[2·i+1]}, Dicke 0 = keine).
     * Wolken liegen nie in einer Schneise (mindestens 8 Blöcke unter jedem Flug, seitlich Abstand) und nie im Berg.
     */
    public int[] clouds(int x, int z, int ground) {
        int[] out = new int[CLOUD_LAYERS.length * 2];
        double sky = skyLimit(x, z);
        double away = corridorDistance(x, z);
        // Wolken nur, wo jede Strecke in der Nähe hoch darüber fliegt (von unten sähe man nur graue Bäuche)
        double lowest = Double.POSITIVE_INFINITY;
        for (Corridor c : corridors) {
            lowest = Math.min(lowest, c.flightNear(x, z));
        }
        for (int i = 0; i < CLOUD_LAYERS.length; i++) {
            int base = CLOUD_LAYERS[i];
            // nur über tiefen Tälern, weit unter jedem Flug: von oben sieht man auf ein Wolkenmeer hinab
            if (ground > base - 18 || away < 30) {
                continue;
            }
            double ox = i * 517.0;
            double c = clouds.fbm((x + ox) / 52.0, (z - ox) / 44.0, 3) + 0.35 * clouds.at((x - ox) / 12.0, (z + ox) / 12.0);
            double thr = 0.36;
            if (c <= thr) {
                continue;
            }
            int thick = 1 + (int) Math.min(3, (c - thr) * 9);
            if (base + thick > sky - 16 || base + thick > lowest - 20) {
                continue;
            }
            out[2 * i] = base;
            out[2 * i + 1] = thick;
        }
        return out;
    }

    /** Ist bei (x, y, z) Wolke? */
    public boolean cloudAt(int x, int y, int z, int ground) {
        int[] c = clouds(x, z, ground);
        for (int i = 0; i < c.length; i += 2) {
            if (c[i + 1] > 0 && y >= c[i] && y < c[i] + c[i + 1]) {
                return true;
            }
        }
        return false;
    }

    /** Gegend an (x, z) (für Oberfläche und Bäume). */
    public Style style(double x, double z) {
        for (Region r : regions) {
            double edge = r.radius() * (0.86 + 0.24 * (0.5 + 0.5 * detail.fbm(x / 70.0, z / 70.0, 2)));
            if (Math.hypot(x - r.cx(), z - r.cz()) <= edge) {
                return r.style();
            }
        }
        return Style.ALM;
    }

    /** Wie nah ist eine Flugschneise (waagerecht)? */
    public double corridorDistance(double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (Corridor c : corridors) {
            best = Math.min(best, c.distance(x, z));
        }
        return best;
    }

    /** Wie hoch darf hier etwas in den Himmel ragen, ohne einem Flug im Weg zu sein? */
    public double skyLimit(double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (Corridor c : corridors) {
            best = Math.min(best, c.ceiling(x, z) + c.clearance() - 3);
        }
        return best;
    }

    /**
     * Oberster Block einer Säule: abhängig von Gegend, Höhe und Neigung ({@code slope} = größter Höhenunterschied zu
     * den Nachbarn). Gibt den Blocknamen zurück (ohne „minecraft:“).
     */
    public String surface(int x, int z, int h, double slope) {
        Style st = style(x, z);
        double n = Noise.hash(seed, x, z);
        double snowLine = SNOW + detail.at(x / 23.0, z / 23.0) * 9;
        if (st == Style.GLACIER) {
            if (slope >= 2.5) {
                return n < 0.62 ? "snow_block" : (n < 0.9 ? "packed_ice" : (n < 0.95 ? "blue_ice" : "stone"));
            }
            return n < 0.06 ? "packed_ice" : "snow_block";
        }
        if (h >= snowLine) {
            if (slope < 1.6 || (slope < 2.6 && n < 0.55)) {
                return "snow_block";
            }
            return rock(n);
        }
        if (slope >= 3.2 || h >= snowLine - 12) {
            return slope < 1.2 && n < 0.35 && h < snowLine - 4 ? "grass_block" : rock(n);
        }
        if (st == Style.ROCK) {
            if (slope < 1.3 && n < 0.45) {
                return n < 0.08 ? "moss_block" : "grass_block";
            }
            return n < 0.5 ? "stone" : (n < 0.68 ? "andesite" : (n < 0.78 ? "gravel" : (n < 0.88 ? "tuff" : "grass_block")));
        }
        if (st == Style.GORGE) {
            if (slope >= 2) {
                return n < 0.4 ? "stone" : (n < 0.58 ? "andesite" : (n < 0.74 ? "mossy_cobblestone" : (n < 0.86
                        ? "moss_block" : "tuff")));
            }
            return n < 0.3 ? "moss_block" : "grass_block";
        }
        if (slope < 1.6 && !paths.isEmpty() && onPath(x, z)) {
            return n < 0.12 ? "coarse_dirt" : "dirt_path";
        }
        if (slope >= 2.2) {
            return n < 0.35 ? "coarse_dirt" : (n < 0.6 ? "stone" : "grass_block");
        }
        // Hänge: Felsbänder und Geröll in Flecken statt gleichmäßiger Erdstufen
        if (slope >= 0.9 && detail.fbm(x / 15.0 - 31, z / 15.0 + 9, 2) > 0.18 - 0.08 * slope) {
            return n < 0.45 ? "stone" : (n < 0.65 ? "andesite" : (n < 0.78 ? "mossy_cobblestone" : (n < 0.9
                    ? "moss_block" : "gravel")));
        }
        return h > TREE_LINE + 8 && n < 0.3 ? "coarse_dirt" : "grass_block";
    }

    /** Fels mit etwas Abwechslung. */
    private static String rock(double n) {
        return n < 0.5 ? "stone" : (n < 0.72 ? "andesite" : (n < 0.86 ? "tuff" : (n < 0.95 ? "cobblestone" : "gravel")));
    }

    /** Block unter der Oberfläche (die ersten Schichten). */
    public String under(String surface) {
        return switch (surface) {
            case "grass_block", "moss_block", "coarse_dirt", "podzol", "dirt_path" -> "dirt";
            case "snow_block" -> "stone";
            case "gravel" -> "stone";
            default -> surface.equals("packed_ice") ? "packed_ice" : "stone";
        };
    }

    /** Pflanze auf einer Wiese (oder null), fest je Stelle. */
    public String plant(int x, int z, int h, String surface) {
        return plant(x, z, h, surface, 0);
    }

    /** Pflanze, abhängig auch von der Neigung (an Hängen Büsche und Farn, die die Stufen weicher machen). */
    public String plant(int x, int z, int h, String surface, double slope) {
        if (!surface.equals("grass_block") && !surface.equals("moss_block")) {
            return null;
        }
        double n = Noise.hash(seed + 7, x, z);
        Style st = style(x, z);
        if (st == Style.GORGE) {
            return n < 0.16 ? "fern" : null;
        }
        if (h > TREE_LINE + 10) {
            return n < 0.05 ? "short_grass" : null;
        }
        if (slope >= 0.9 && h <= TREE_LINE + 10) {
            return n < 0.3 ? "bush" : (n < 0.55 ? "short_grass" : (n < 0.65 ? "fern" : null));
        }
        // Blumenwiesen in Flecken: je Fleck eine Hauptblume (Farbfelder wie auf einer echten Alm), dazu ein paar andere
        double patch = Noise.smooth(0.05, 0.55, detail.fbm(x / 41.0 + 13, z / 41.0 - 5, 3));
        if (n < 0.02 + 0.42 * patch) {
            String[] f = {"cornflower", "oxeye_daisy", "dandelion", "azure_bluet", "allium", "poppy", "blue_orchid",
                "pink_tulip", "white_tulip", "orange_tulip", "lily_of_the_valley"};
            int main = (int) ((0.5 + 0.5 * hills.at(x / 33.0 - 70, z / 33.0 + 40)) * f.length * 0.999);
            double pick = Noise.hash(seed + 8, x, z);
            if (pick > 0.9) {
                // Blütenteppiche
                String[] dirs = {"north", "east", "south", "west"};
                String carpet = main % 2 == 0 ? "wildflowers" : "pink_petals";
                return carpet + "[flower_amount=" + (2 + (int) (pick * 31) % 3) + ",facing=" + dirs[(int) (pick * 97) % 4] + "]";
            }
            return pick < 0.68 ? f[Math.min(f.length - 1, main)] : f[(int) (pick * 977) % f.length];
        }
        if (n < 0.62) {
            return n < 0.5 ? null : "short_grass";
        }
        return n > 0.985 ? "fern" : null;
    }
}
