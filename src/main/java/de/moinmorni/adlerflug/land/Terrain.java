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

    /** Gegend mit eigener Oberfläche. */
    public record Region(double cx, double cz, double radius, Style style) {
    }

    private final long seed;
    private final Noise mask;
    private final Noise hills;
    final Noise ridges;
    final Noise detail;
    private final List<Shape> shapes = new ArrayList<>();
    private final List<Shape> late = new ArrayList<>();
    private final List<Corridor> corridors = new ArrayList<>();
    private final List<Lake> lakes = new ArrayList<>();
    private final List<River> rivers = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();

    public Terrain(long seed) {
        this.seed = seed;
        this.mask = new Noise(seed);
        this.hills = new Noise(seed + 1);
        this.ridges = new Noise(seed + 2);
        this.detail = new Noise(seed + 3);
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

    /** Grundgebirge ohne Formen (Hügel, Grate, Gipfel). */
    public double base(double x, double z) {
        double m = Noise.smooth(-0.35, 0.55, mask.fbm(x / 720.0, z / 720.0, 3));
        double hill = hills.fbm(x / 170.0, z / 170.0, 4) * 16;
        double ridge = ridges.ridged(x / 280.0, z / 280.0, 5);
        double rough = detail.fbm(x / 38.0, z / 38.0, 2) * 2.5;
        return 74 + hill + rough + m * (Math.pow(ridge, 1.35) * 165 + 10);
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
        for (Corridor c : corridors) {
            double top = c.ceiling(x, z);
            if (h > top) {
                h = top;
            }
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

    /** Gegend an (x, z) (für Oberfläche und Bäume). */
    public Style style(double x, double z) {
        for (Region r : regions) {
            if (Math.hypot(x - r.cx(), z - r.cz()) <= r.radius()) {
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
        if (slope >= 2.2) {
            return n < 0.35 ? "coarse_dirt" : (n < 0.6 ? "stone" : "grass_block");
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
            case "grass_block", "moss_block", "coarse_dirt", "podzol" -> "dirt";
            case "snow_block" -> "stone";
            case "gravel" -> "stone";
            default -> surface.equals("packed_ice") ? "packed_ice" : "stone";
        };
    }

    /** Pflanze auf einer Wiese (oder null), fest je Stelle. */
    public String plant(int x, int z, int h, String surface) {
        if (!surface.equals("grass_block") && !surface.equals("moss_block")) {
            return null;
        }
        double n = Noise.hash(seed + 7, x, z);
        Style st = style(x, z);
        double flowers = 0.5 + 0.5 * detail.at(x / 30.0, z / 30.0);
        if (st == Style.GORGE) {
            return n < 0.12 ? "fern" : (n < 0.16 ? "large_fern" : null);
        }
        if (h > TREE_LINE + 10) {
            return n < 0.05 ? "short_grass" : null;
        }
        if (n < 0.16) {
            return "short_grass";
        }
        if (n < 0.16 + 0.07 * flowers) {
            String[] f = {"cornflower", "oxeye_daisy", "dandelion", "azure_bluet", "allium", "poppy", "blue_orchid"};
            return f[(int) (Noise.hash(seed + 8, x, z) * f.length)];
        }
        return n > 0.985 ? "fern" : null;
    }
}
