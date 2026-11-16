package de.moinmorni.adlerflug.courses;

import de.moinmorni.adlerflug.course.Blk;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Plan;
import de.moinmorni.adlerflug.land.Terrain;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Das Basislager „Adlerhorst“ auf einem Berggipfel in der Mitte der Welt: runder Platz mit Adler-Mosaik, je Strecke
 * eine Station (Wegweiser mit Schild zum Anklicken, kleiner Ring in der Farbe der Schwierigkeit, Bestenliste darüber),
 * die Adlerhütte und im Süden der Holzturm mit Freiflug-Schanze über dem Tal.
 */
public final class Basecamp {

    /** Eine Station: Schild (anklickbar) und Platz für die Bestenliste darüber. */
    public record Station(String course, int signX, int signY, int signZ, double boardX, double boardY, double boardZ) {
    }

    /** Oberkante des Platzes (man steht auf Y + 1). */
    public static final int Y = 150;
    static final int PLAZA = 22;
    private static final int STATION_RADIUS = 17;
    /** Freiflug-Schanze: Plattform-Höhe über dem Platz und Lage. */
    private static final int TOWER = 12;
    private static final int TOWER_Z = 30;

    private final List<Station> stations = new ArrayList<>();

    public double spawnX() {
        return 0.5;
    }

    public double spawnY() {
        return Y + 1;
    }

    public double spawnZ() {
        return 8.5;
    }

    /** Blick nach Norden (zu den Stationen). */
    public float spawnYaw() {
        return 180f;
    }

    /** Tafel „Strecke der Woche“ über der Mitte. */
    public double[] weeklyBoard() {
        return new double[] {0.5, Y + 6.4, 0.5};
    }

    /** Begrüßung am Fuß der Treppe zur Schanze. */
    public double[] welcomeBoard() {
        return new double[] {0.5, Y + 4.2, 15.5};
    }

    /** Schild „Freiflug-Schanze“ über der Plattform. */
    public double[] jumpBoard() {
        return new double[] {0.5, Y + TOWER + 5.5, TOWER_Z + 0.5};
    }

    public List<Station> stations() {
        return stations;
    }

    /** Ist ein Punkt im Basislager (für Freiflug und Rückkehr)? */
    public boolean inside(double x, double y, double z) {
        return Math.hypot(x, z) < PLAZA + 8 && y > Y - 8;
    }

    void shape(Terrain t) {
        t.shape(new Terrain.Mound(0, 0, 170, Y + 4, 1.6, 10));
        t.shape(new Terrain.Plateau(0, 0, PLAZA + 7, 16, Y));
        t.region(new Terrain.Region(0, 0, 60, Terrain.Style.ALM));
    }

    void build(World w, List<Course> courses) {
        Plan p = w.plan();
        Terrain t = w.terrain();
        w.trees().block(-PLAZA - 8, -PLAZA - 8, PLAZA + 8, TOWER_Z + 22);
        plaza(p);
        for (int i = 0; i < courses.size(); i++) {
            double a = Math.toRadians(-75 + 150.0 * i / Math.max(1, courses.size() - 1));
            int sx = (int) Math.round(Math.sin(a) * STATION_RADIUS);
            int sz = (int) Math.round(-Math.cos(a) * STATION_RADIUS);
            int rotation = Math.floorMod((int) Math.round(Math.toDegrees(a) / 22.5), 16);
            Course c = courses.get(i);
            station(p, c, sx, sz, rotation, a);
            stations.add(new Station(c.id, sx, Y + 2, sz, sx + 0.5, Y + 8.6, sz + 0.5));
        }
        hut(p, t);
        jumpTower(p, t);
        decor(p, t);
    }

    // ---------- Platz ----------

    private void plaza(Plan p) {
        SplittableRandom r = new SplittableRandom(3);
        for (int x = -PLAZA; x <= PLAZA; x++) {
            for (int z = -PLAZA; z <= PLAZA; z++) {
                double d = Math.hypot(x + 0.5, z + 0.5);
                if (d > PLAZA) {
                    continue;
                }
                String b;
                if (d <= 9.5) {
                    b = eagle(x, z);
                } else if (d <= 11.5) {
                    // Rautenband in Weiß und Hellblau
                    int u = Math.floorDiv(x + z + 1, 3);
                    int v = Math.floorDiv(x - z + 1, 3);
                    b = Math.floorMod(u + v, 2) == 0 ? "white_concrete" : "light_blue_concrete";
                } else if (d <= 12.5) {
                    b = "polished_deepslate";
                } else {
                    b = r.nextInt(7) == 0 ? "andesite" : (r.nextInt(9) == 0 ? "stone_bricks" : "polished_andesite");
                }
                p.set(x, Y, z, b);
                for (int k = 1; k <= 8; k++) {
                    p.set(x, Y + k, z, Blk.AIR);
                }
                p.set(x, Y - 1, z, "stone");
            }
        }
        // Rand: niedrige Mauer mit Lücken (Ausblick), Laternen
        for (int a = 0; a < 360; a += 2) {
            int x = (int) Math.round(Math.cos(Math.toRadians(a)) * (PLAZA + 0.5));
            int z = (int) Math.round(Math.sin(Math.toRadians(a)) * (PLAZA + 0.5));
            boolean south = z > 12 && Math.abs(x) < 5;
            if (south) {
                continue;
            }
            p.set(x, Y, z, "stone_bricks");
            p.set(x, Y + 1, z, a % 30 == 0 ? "stone_brick_wall[up=true]" : "stone_brick_slab[type=bottom]");
            if (a % 30 == 0) {
                p.set(x, Y + 2, z, "lantern");
            }
        }
    }

    /**
     * Windrose (Mosaik in der Mitte): vier lange Spitzen in Gold, vier kurze in Weiß-Blau, jede Spitze zweifarbig wie
     * von der Sonne beschienen, außen ein dunkler Ring. Eigene Zeichnung.
     */
    static String eagle(int x, int z) {
        double px = x + 0.5;
        double pz = z + 0.5;
        double d = Math.hypot(px, pz);
        if (d <= 1.3) {
            return "gold_block";
        }
        String hit = point(px, pz, 8.6, 2.3, 0, "yellow_concrete", "orange_concrete");
        if (hit == null) {
            hit = point(px, pz, 6.0, 1.7, Math.PI / 4, "white_concrete", "light_blue_concrete");
        }
        if (hit != null) {
            return hit;
        }
        if (d > 8.7) {
            return "polished_deepslate";
        }
        return d > 7.6 && d < 8.2 ? "light_gray_concrete" : "calcite";
    }

    /** Vier Spitzen der Länge {@code len}, gedreht um {@code turn}; null = keine Spitze hier. */
    private static String point(double px, double pz, double len, double width, double turn, String light, String dark) {
        for (int k = 0; k < 4; k++) {
            double a = turn + k * Math.PI / 2;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            double t = px * dx + pz * dz;
            double side = -px * dz + pz * dx;
            if (t >= 0 && t <= len && Math.abs(side) <= width * (1 - t / len) + 0.15) {
                return side >= 0 ? light : dark;
            }
        }
        return null;
    }

    // ---------- Stationen ----------

    private void station(Plan p, Course c, int x, int z, int rotation, double a) {
        String color = c.difficulty.block;
        p.set(x, Y, z, "chiseled_stone_bricks");
        p.set(x, Y + 1, z, "spruce_fence");
        p.set(x, Y + 2, z, Blk.deco("spruce_sign[rotation=" + rotation + "]"));
        p.sign(x, Y + 2, z, List.of("", c.nameDe, c.difficulty.stars(), "» Klick «"));
        // kleiner Ring in der Farbe der Schwierigkeit hinter dem Schild, quer zur Blickrichtung
        double dx = Math.sin(a);
        double dz = -Math.cos(a);
        double cx = x + 0.5 + dx * 2.5;
        double cz = z + 0.5 + dz * 2.5;
        double ux = -dz;
        double uz = dx;
        for (int k = 0; k < 40; k++) {
            double ang = 2 * Math.PI * k / 40;
            double ox = Math.cos(ang) * 2.2;
            double oy = Math.sin(ang) * 2.2;
            int bx = (int) Math.floor(cx + ux * ox);
            int bz = (int) Math.floor(cz + uz * ox);
            int by = (int) Math.floor(Y + 4.0 + oy);
            p.set(bx, by, bz, k % 10 == 0 ? "glowstone" : color);
        }
        int bx = (int) Math.floor(cx);
        int bz = (int) Math.floor(cz);
        p.set(bx, Y, bz, "chiseled_stone_bricks");
        p.set(bx, Y + 1, bz, "stone_brick_wall[up=true]");
    }

    // ---------- Adlerhütte ----------

    private void hut(Plan p, Terrain t) {
        Scenery.hut(p, t, -34, 2, 10, 8, false, 5);
        // Bänke und Tisch davor
        for (int k = 0; k < 3; k++) {
            p.set(-22 + k, Y + 1, 4, "spruce_stairs[facing=south]");
            p.set(-22 + k, Y + 1, 8, "spruce_stairs[facing=north]");
            p.set(-22 + k, Y + 1, 6, "spruce_slab[type=top]");
        }
    }

    // ---------- Freiflug-Schanze ----------

    private void jumpTower(Plan p, Terrain t) {
        int top = Y + TOWER;
        // Treppe vom Platz hinauf (z 15 … 27), 5 breit
        for (int s = 0; s < TOWER; s++) {
            int z = 16 + s;
            int y = Y + 1 + s;
            for (int x = -2; x <= 2; x++) {
                p.set(x, y, z, "spruce_stairs[facing=north]");
                for (int below = Y + 1; below < y; below++) {
                    p.setIfEmpty(x, below, z, Math.abs(x) == 2 ? Blk.of("stripped_spruce_log[axis=y]") : Blk.AIR);
                }
                p.set(x, y + 1, z, Blk.AIR);
                p.set(x, y + 2, z, Blk.AIR);
                p.set(x, y + 3, z, Blk.AIR);
            }
            p.set(-3, y + 1, z, "spruce_fence");
            p.set(3, y + 1, z, "spruce_fence");
            p.set(-3, y, z, "spruce_planks");
            p.set(3, y, z, "spruce_planks");
        }
        // Plattform (z 28 … 32)
        for (int z = 28; z <= TOWER_Z + 2; z++) {
            for (int x = -3; x <= 3; x++) {
                p.set(x, top, z, Math.abs(x) <= 1 ? "light_blue_concrete" : "spruce_planks");
                for (int k = 1; k <= 4; k++) {
                    p.set(x, top + k, z, Blk.AIR);
                }
            }
            p.set(-4, top + 1, z, "spruce_fence");
            p.set(4, top + 1, z, "spruce_fence");
        }
        // Schanze: abwärts nach Süden bis zur Kante, darunter Stützen bis zum Hang
        int ramp = 14;
        for (int k = 0; k < ramp; k++) {
            int z = TOWER_Z + 3 + k;
            int y = top - (k + 1) / 2;
            for (int x = -2; x <= 2; x++) {
                p.set(x, y, z, k % 2 == 1 ? "spruce_slab[type=top]" : "spruce_planks");
                if (Math.abs(x) <= 1 && k % 2 == 0) {
                    p.set(x, y, z, "white_concrete");
                }
                for (int a = 1; a <= 4; a++) {
                    p.set(x, y + a, z, Blk.AIR);
                }
            }
            p.set(-3, y + 1, z, "spruce_fence");
            p.set(3, y + 1, z, "spruce_fence");
            if (k % 4 == 0) {
                for (int x : new int[] {-3, 3}) {
                    int g = t.height(x, z);
                    for (int yy = g + 1; yy < y; yy++) {
                        p.set(x, yy, z, "stripped_spruce_log[axis=y]");
                    }
                }
            }
        }
        // Turmbeine unter der Plattform
        for (int x : new int[] {-3, 3}) {
            for (int z : new int[] {28, TOWER_Z + 2}) {
                for (int y = t.height(x, z) + 1; y < top; y++) {
                    p.set(x, y, z, "stripped_spruce_log[axis=y]");
                }
            }
        }
        // Fahnen am Kopf der Schanze
        Scenery.flagPole(p, -4, top + 1, 28, 6, "white_wool");
        Scenery.flagPole(p, 4, top + 1, 28, 6, "light_blue_wool");
    }

    // ---------- Schmuck ----------

    private void decor(Plan p, Terrain t) {
        SplittableRandom r = new SplittableRandom(17);
        String[] flowers = {"cornflower", "oxeye_daisy", "allium", "red_tulip", "white_tulip", "azure_bluet", "poppy"};
        for (int a = 0; a < 360; a += 3) {
            double rad = PLAZA + 2.5;
            int x = (int) Math.round(Math.cos(Math.toRadians(a)) * rad);
            int z = (int) Math.round(Math.sin(Math.toRadians(a)) * rad);
            if (z > 12 && Math.abs(x) < 6) {
                continue;
            }
            int g = t.height(x, z);
            if (r.nextInt(3) > 0) {
                p.setIfEmpty(x, g + 1, z, Blk.deco(flowers[r.nextInt(flowers.length)]));
            }
        }
        // Wegweiser-Mast in der Mitte des Adlers (unter der Wochen-Tafel)
        p.set(0, Y + 1, 0, "polished_andesite_slab[type=bottom]");
    }
}
