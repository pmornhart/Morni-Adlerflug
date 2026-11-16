package de.moinmorni.adlerflug.courses;

import de.moinmorni.adlerflug.course.Blk;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Plan;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.land.Noise;
import de.moinmorni.adlerflug.land.Terrain;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Bauten der einzelnen Strecken: Hütten, Heustadel und Kapelle auf der Alm, Wasserfall und Hängebrücke in der Klamm,
 * Burg und Steinbrücke, Eisbögen über der Gletscherspalte, der Bergwerksstollen und das Gipfelkreuz mit Felsscharte.
 * Alles bleibt unter der Flugschneise ({@link Terrain#skyLimit}) – außer Rahmen, die genau um einen Ring herum stehen.
 */
final class Builders {

    private Builders() {
    }

    // =========================================================================================
    // Almrundflug
    // =========================================================================================

    static void alm(World w, Course c) {
        Plan p = w.plan();
        Terrain t = w.terrain();
        Scenery.chapel(p, t, 522, -36);
        w.trees().block(518, -42, 532, -24);
        int[][] huts = {{470, -40, 9, 7}, {612, 82, 8, 7}, {700, -60, 9, 7}, {800, 20, 8, 7}, {640, -210, 9, 7}};
        long seed = 11;
        for (int[] h : huts) {
            Scenery.hut(p, t, h[0], h[1], h[2], h[3], seed % 2 == 0, seed++);
            w.trees().block(h[0] - 3, h[1] - 3, h[0] + h[2] + 3, h[1] + h[3] + 3);
        }
        int[][] barns = {{560, 90, 6, 5}, {676, 90, 5, 6}, {780, -110, 6, 5}, {480, 60, 5, 5}};
        for (int[] b : barns) {
            Scenery.barn(p, t, b[0], b[1], b[2], b[3]);
            w.trees().block(b[0] - 2, b[1] - 2, b[0] + b[2] + 2, b[1] + b[3] + 2);
        }
        Scenery.fenceRing(p, t, 650, 120, 22);
        w.trees().block(626, 96, 674, 144);
        for (int i = 0; i < 9; i++) {
            double a = i * 0.7;
            w.critter("cow", 650 + Math.cos(a) * (6 + i), t.height(650 + Math.cos(a) * (6 + i), 120 + Math.sin(a) * (6 + i)) + 1,
                    120 + Math.sin(a) * (6 + i), i * 40);
        }
        for (int i = 0; i < 6; i++) {
            double x = 505 + i * 9;
            double z = 95 + (i % 2) * 7;
            w.critter("sheep", x, t.height(x, z) + 1, z, i * 70);
        }
        jetty(p, t, 596, -60, 85);
    }

    /** Holzsteg in den See. */
    private static void jetty(Plan p, Terrain t, int x, int z, int level) {
        for (int k = 0; k < 14; k++) {
            for (int d = 0; d <= 1; d++) {
                p.set(x + k, level, z + d, "spruce_planks");
            }
            if (k % 4 == 0) {
                p.set(x + k, level - 1, z - 1, "spruce_log[axis=y]");
                p.set(x + k, level - 1, z + 2, "spruce_log[axis=y]");
                p.set(x + k, level + 1, z - 1, "spruce_fence");
                p.set(x + k, level + 1, z + 2, "spruce_fence");
            }
        }
        p.set(x + 13, level + 2, z - 1, "lantern");
    }

    // =========================================================================================
    // Klammflug
    // =========================================================================================

    static void klamm(World w, Course c) {
        Plan p = w.plan();
        Terrain t = w.terrain();
        // Wasserfall an der Felswand neben Ring 6, Hängebrücken hoch über der Klamm
        waterfall(p, t, c, 5, -1);
        waterfall(p, t, c, 9, 1);
        bridge(p, t, c, 2, 16);
        bridge(p, t, c, 7, 18);
        // Holzsteg über dem Bach am Ende
        Ring last = c.finish();
        int y = t.water((int) last.center().x(), (int) last.center().z() + 12,
                t.height(last.center().x(), last.center().z() + 12));
        if (y != Integer.MIN_VALUE) {
            for (int k = -6; k <= 6; k++) {
                p.set((int) last.center().x() + k, y + 1, (int) last.center().z() + 12, "spruce_slab[type=bottom]");
            }
        }
    }

    /** Wasserfall in einer Kerbe der Felswand seitlich von Ring {@code ring} ({@code side} = links/rechts). */
    private static void waterfall(Plan p, Terrain t, Course c, int ring, int side) {
        Ring r = c.rings.get(ring);
        Vec u = across(r).mul(side);
        Vec base = r.center();
        // zur Wand gehen, bis die Landschaft deutlich über dem Flug liegt
        Vec wall = null;
        for (double k = 4; k < 30; k += 0.5) {
            Vec q = base.add(u.mul(k));
            if (t.height(q.x(), q.z()) > base.y() + 10) {
                wall = q;
                break;
            }
        }
        if (wall == null) {
            return;
        }
        int top = t.height(wall.x() + u.x() * 3, wall.z() + u.z() * 3);
        int bottom = t.height(base.x(), base.z());
        for (int k = -1; k <= 1; k++) {
            Vec q = wall.add(new Vec(-u.z(), 0, u.x()).mul(k));
            int x = (int) Math.floor(q.x());
            int z = (int) Math.floor(q.z());
            int ground = t.height(x, z);
            for (int y = Math.max(bottom + 1, Math.min(ground, top) - 60); y <= Math.min(ground, top); y++) {
                p.set(x, y, z, Blk.WATER);
            }
            // Becken oben, damit das Wasser eine Quelle hat
            int bx = (int) Math.floor(q.x() + u.x());
            int bz = (int) Math.floor(q.z() + u.z());
            p.set(bx, Math.min(ground, top), bz, Blk.WATER);
        }
    }

    /** Hängebrücke quer über die Klamm, {@code above} Blöcke über Ring {@code ring}. */
    private static void bridge(Plan p, Terrain t, Course c, int ring, int above) {
        Ring r = c.rings.get(ring);
        Vec u = across(r);
        int y = (int) Math.floor(r.center().y()) + above;
        Vec mid = r.center();
        for (double k = -40; k <= 40; k += 0.5) {
            Vec q = mid.add(u.mul(k));
            int x = (int) Math.floor(q.x());
            int z = (int) Math.floor(q.z());
            if (t.height(x, z) >= y - 1) {
                continue;
            }
            int sag = (int) Math.round((1 - (k / 40) * (k / 40)) * 2);
            p.set(x, y - sag, z, "spruce_slab[type=top]");
            Vec s1 = q.add(new Vec(-u.z(), 0, u.x()));
            Vec s2 = q.add(new Vec(u.z(), 0, -u.x()));
            p.setIfEmpty((int) Math.floor(s1.x()), y - sag + 1, (int) Math.floor(s1.z()), Blk.of("spruce_fence"));
            p.setIfEmpty((int) Math.floor(s2.x()), y - sag + 1, (int) Math.floor(s2.z()), Blk.of("spruce_fence"));
        }
    }

    // =========================================================================================
    // Burgruine
    // =========================================================================================

    static void burg(World w, Course c) {
        Plan p = w.plan();
        Terrain t = w.terrain();
        SplittableRandom rnd = new SplittableRandom(77);
        int cx = -600;
        int cz = 0;
        int floor = 118;
        w.trees().block(cx - 44, cz - 44, cx + 44, cz + 44);
        Ring gate = c.rings.get(2);
        // Ringmauer mit Zinnen: zwei Blöcke dick, 9 hoch; ein paar Stücke sind eingestürzt (niedrig, mit Schutt)
        for (int a = 0; a < 720; a++) {
            double ang = Math.toRadians(a / 2.0);
            int x = cx + (int) Math.round(Math.cos(ang) * 34);
            int z = cz + (int) Math.round(Math.sin(ang) * 34);
            if (Math.hypot(x - gate.center().x(), z - gate.center().z()) < gate.radius() + 4) {
                continue;
            }
            int section = a / 50;
            boolean ruined = Noise.hash(5, section, 0) < 0.3;
            double edge = Math.abs((a % 50) - 25) / 25.0;
            int height = ruined ? 2 + (int) Math.round(edge * 6) : 9;
            int limit = (int) Math.floor(t.skyLimit(x, z)) - 1;
            int ground = t.height(x, z);
            for (int y = ground; y <= Math.min(floor + height, limit); y++) {
                for (int d = 0; d <= 1; d++) {
                    int xx = x + (int) Math.round(Math.cos(ang) * d);
                    int zz = z + (int) Math.round(Math.sin(ang) * d);
                    p.set(xx, y, zz, y == floor + height && !ruined ? "stone_bricks" : stone(rnd));
                }
            }
            if (!ruined && (a / 3) % 2 == 0 && floor + 10 <= limit) {
                p.set(x, floor + 10, z, "stone_bricks");
            }
            if (ruined && rnd.nextInt(4) == 0) {
                int rx = cx + (int) Math.round(Math.cos(ang) * (31 + rnd.nextInt(2)));
                int rz = cz + (int) Math.round(Math.sin(ang) * (31 + rnd.nextInt(2)));
                p.setIfEmpty(rx, t.height(rx, rz) + 1, rz, Blk.of(rnd.nextBoolean() ? "mossy_cobblestone"
                        : "cobblestone_slab[type=bottom]"));
            }
        }
        // Torhaus: zwei Türme um den Torring, Bogen darüber
        gateFrame(p, t, gate, rnd, 7, floor);
        // zwei hohe Türme an der Westmauer, der Flug geht zwischen ihnen hindurch
        Ring west = c.rings.get(3);
        Vec u = across(west);
        for (int s : new int[] {-1, 1}) {
            Vec q = west.center().add(u.mul(s * (west.radius() + 6.5)));
            roundTower(p, t, (int) Math.floor(q.x()), (int) Math.floor(q.z()), 4, (int) west.center().y() + 2, rnd);
        }
        // Bergfried (verfallen) im Norden des Hofs
        keep(p, t, cx - 8, cz - 32, 12, floor, 150, rnd);
        // Steinbrücke (Aquädukt) mit hohen Bögen quer zur Strecke bei Ring 10
        aqueduct(p, t, c.rings.get(9), rnd);
        // Trümmer im Hof
        for (int i = 0; i < 40; i++) {
            int x = cx - 26 + rnd.nextInt(52);
            int z = cz - 26 + rnd.nextInt(52);
            if (Math.hypot(x - cx, z - cz) < 30 && rnd.nextInt(3) == 0) {
                p.setIfEmpty(x, t.height(x, z) + 1, z, Blk.of(rnd.nextBoolean() ? "mossy_cobblestone" : "cobblestone_slab[type=bottom]"));
            }
        }
    }

    private static String stone(SplittableRandom r) {
        int k = r.nextInt(10);
        return k < 4 ? "stone_bricks" : (k < 7 ? "mossy_stone_bricks" : (k < 9 ? "cracked_stone_bricks" : "andesite"));
    }

    /** Rahmen aus Mauerwerk eng um einen Ring (Tor): Pfeiler links und rechts, Bogen oben. */
    private static void gateFrame(Plan p, Terrain t, Ring r, SplittableRandom rnd, int towerRadius, int floor) {
        Vec u = across(r);
        Vec n = new Vec(r.normal().x(), 0, r.normal().z()).normalize();
        double reach = r.radius() + 2.2;
        int topY = (int) Math.floor(r.center().y() + reach + 1);
        for (double along = -3; along <= 3; along += 0.5) {
            for (double k = -reach - 4; k <= reach + 4; k += 0.5) {
                Vec q = r.center().add(u.mul(k)).add(n.mul(along));
                int x = (int) Math.floor(q.x());
                int z = (int) Math.floor(q.z());
                int ground = t.height(x, z);
                for (int y = ground; y <= topY + 3; y++) {
                    double dy = y + 0.5 - r.center().y();
                    double dist = Math.hypot(k, dy);
                    boolean opening = dist < reach || (dy < 0 && Math.abs(k) < reach);
                    if (opening) {
                        continue;
                    }
                    if (y == topY + 3 && (int) Math.floor(k) % 2 != 0) {
                        continue;
                    }
                    p.setIfEmpty(x, y, z, Blk.of(stone(rnd)));
                }
            }
        }
        // Fallgitter (hochgezogen) und Fahnen
        Vec top = r.center().add(0, reach + 4.5, 0);
        Scenery.flagPole(p, (int) Math.floor(top.x()), (int) Math.floor(top.y()), (int) Math.floor(top.z()), 4, "red_wool");
    }

    /** Runder Turm mit Fenstern und spitzem Schieferdach (dunkle Steine), oben eine Fahne. */
    private static void roundTower(Plan p, Terrain t, int x, int z, int r, int top, SplittableRandom rnd) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.3) {
                    continue;
                }
                int ground = t.height(x + dx, z + dz);
                boolean shell = d > r - 1.2;
                for (int y = ground; y <= top; y++) {
                    boolean window = shell && (y - ground) % 8 == 5 && (dx == 0 || dz == 0);
                    p.set(x + dx, y, z + dz, window ? "air" : (shell ? stone(rnd) : "air"));
                }
                if (!shell) {
                    p.set(x + dx, top - 7, z + dz, "spruce_planks");
                }
                if (shell && Math.floorMod(dx + dz, 2) == 0) {
                    p.set(x + dx, top + 1, z + dz, "stone_bricks");
                }
            }
        }
        // Kegeldach aus dunklen Ziegeln, etwas über den Rand
        int roofBase = top + 2;
        for (int k = 0; k <= r + 2; k++) {
            double rr = r + 1 - k * (r + 1.0) / (r + 2);
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d <= rr + 0.3 && d > rr - 1.1) {
                        p.set(x + dx, roofBase + k, z + dz, k % 2 == 0 ? "deepslate_tiles" : "polished_deepslate");
                    }
                }
            }
        }
        Scenery.flagPole(p, x, roofBase + r + 3, z, 4, "red_wool");
    }

    /** Bergfried: viereckig, oben abgebrochen, Fenster. */
    private static void keep(Plan p, Terrain t, int x, int z, int size, int floor, int top, SplittableRandom rnd) {
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                boolean shell = dx == 0 || dz == 0 || dx == size - 1 || dz == size - 1;
                int ground = t.height(x + dx, z + dz);
                int broken = top - (int) (Noise.hash(13, dx, dz) * 9) - (dx + dz < size ? 0 : 6);
                for (int y = ground; y <= broken; y++) {
                    boolean window = shell && (y - floor) % 8 == 4 && (dx == size / 2 || dz == size / 2);
                    p.set(x + dx, y, z + dz, shell ? (window ? "air" : stone(rnd)) : (y == floor + 8 || y == floor + 16
                            ? "spruce_planks" : "air"));
                }
            }
        }
    }

    /** Steinbrücke mit hohen Bögen quer zur Flugrichtung, ein Bogen genau um den Ring. */
    private static void aqueduct(Plan p, Terrain t, Ring r, SplittableRandom rnd) {
        Vec u = across(r);
        double reach = r.radius() + 2.2;
        int deck = (int) Math.floor(r.center().y() + reach + 2);
        double span = 2 * reach + 3;
        for (double k = -70; k <= 70; k += 0.5) {
            Vec q = r.center().add(u.mul(k));
            int x = (int) Math.floor(q.x());
            int z = (int) Math.floor(q.z());
            int ground = t.height(x, z);
            if (ground >= deck) {
                continue;
            }
            double local = Math.IEEEremainder(k, span);
            double archTop = r.center().y() + Math.sqrt(Math.max(0, reach * reach - local * local));
            for (double along = -1.5; along <= 1.5; along += 0.5) {
                Vec qq = q.add(new Vec(r.normal().x(), 0, r.normal().z()).normalize().mul(along));
                int xx = (int) Math.floor(qq.x());
                int zz = (int) Math.floor(qq.z());
                int g = t.height(xx, zz);
                for (int y = g; y <= deck; y++) {
                    boolean inArch = Math.abs(local) < reach && y < archTop && y > g;
                    boolean ruined = Noise.hash(17, (int) Math.floor(k / span), 0) < 0.25 && y > deck - 3;
                    if (!inArch && !ruined) {
                        p.setIfEmpty(xx, y, zz, Blk.of(stone(rnd)));
                    }
                }
            }
        }
    }

    // =========================================================================================
    // Gletscherspalte
    // =========================================================================================

    static void gletscher(World w, Course c) {
        Plan p = w.plan();
        Terrain t = w.terrain();
        for (int i : new int[] {1, 3, 5, 7}) {
            iceArch(p, t, c.rings.get(i), 7 + (i % 3));
        }
        SplittableRandom rnd = new SplittableRandom(31);
        // Eistürme (Séracs) auf dem Gletscher
        for (int i = 0; i < 26; i++) {
            int x = -120 + rnd.nextInt(240);
            int z = -880 + rnd.nextInt(400);
            if (t.corridorDistance(x, z) < 26) {
                continue;
            }
            int g = t.height(x, z);
            int h = 3 + rnd.nextInt(7);
            int r = 1 + rnd.nextInt(2);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int y = 0; y < h - Math.abs(dx) - Math.abs(dz); y++) {
                        p.set(x + dx, g + 1 + y, z + dz, rnd.nextInt(4) == 0 ? "blue_ice" : "packed_ice");
                    }
                }
            }
        }
        Vec l = c.landing.at();
        Scenery.hut(p, t, (int) l.x() + 10, (int) l.z() - 4, 9, 8, false, 41);
        w.trees().block((int) l.x() + 6, (int) l.z() - 8, (int) l.x() + 24, (int) l.z() + 8);
    }

    /** Eisbogen quer über die Spalte, {@code above} Blöcke über dem Ring. */
    private static void iceArch(Plan p, Terrain t, Ring r, int above) {
        Vec u = across(r);
        Vec n = new Vec(r.normal().x(), 0, r.normal().z()).normalize();
        double y0 = r.center().y() + above;
        for (double k = -26; k <= 26; k += 0.5) {
            Vec q = r.center().add(u.mul(k));
            double sag = 3 - 3 * (k / 26) * (k / 26);
            for (double along = -1.5; along <= 1.5; along += 0.5) {
                Vec qq = q.add(n.mul(along));
                int x = (int) Math.floor(qq.x());
                int z = (int) Math.floor(qq.z());
                int top = (int) Math.floor(y0 + sag);
                if (t.height(x, z) > top + 4) {
                    continue;
                }
                p.set(x, top, z, Math.abs(along) > 1 ? "packed_ice" : "blue_ice");
                p.set(x, top + 1, z, "snow_block");
            }
        }
    }

    // =========================================================================================
    // Stollenflug
    // =========================================================================================

    static void stollen(World w, Course c) {
        Plan p = w.plan();
        Terrain t = w.terrain();
        SplittableRandom rnd = new SplittableRandom(53);
        double tube = 6.0;
        boolean inside = false;
        double walked = 0;
        double lastFrameAt = -100;
        for (int tick = 1; tick < c.pathTicks(); tick++) {
            Vec a = Course.center(c.pathAt(tick - 1));
            Vec b = Course.center(c.pathAt(tick));
            if (b.x() < 520 || a.x() > 745) {
                continue;
            }
            Vec dir = b.sub(a).normalize();
            Vec side = new Vec(-dir.z(), 0, dir.x()).normalize();
            double len = a.distance(b);
            for (double f = 0; f < len; f += 0.4) {
                Vec center = a.add(dir.mul(f));
                walked += 0.4;
                slice(p, t, center, side, tube);
                if (walked - lastFrameAt >= 7 && t.height(center.x(), center.z()) > center.y() + tube + 1) {
                    lastFrameAt = walked;
                    inside = true;
                    frame(p, center, side, tube, (int) Math.floor(center.y() - tube));
                }
            }
        }
        // Mundloch-Schilder an beiden Enden des Bergs
        if (inside) {
            Ring first = c.rings.get(2);
            Vec a = first.center().add(across(first).mul(first.radius() + 2.5));
            p.sign((int) Math.floor(a.x()), (int) Math.floor(a.y()) - 2, (int) Math.floor(a.z()),
                    List.of("", "Adlerstollen", "Glück auf!", ""));
        }
    }

    /** Querschnitt der Röhre an einer Stelle: Boden flach, Decke gewölbt, Wände mit Erzen. */
    private static void slice(Plan p, Terrain t, Vec center, Vec side, double tube) {
        int floor = (int) Math.floor(center.y() - tube);
        for (double s = -tube - 1.5; s <= tube + 1.5; s += 0.5) {
            Vec q = center.add(side.mul(s));
            int x = (int) Math.floor(q.x());
            int z = (int) Math.floor(q.z());
            int ground = t.height(x, z);
            double roof = center.y() + Math.sqrt(Math.max(0, (tube + 1) * (tube + 1) - s * s)) - 0.5;
            for (int y = floor - 1; y <= (int) Math.ceil(roof) + 1; y++) {
                boolean carve = Math.abs(s) <= tube && y >= floor + 1 && y <= roof;
                if (carve) {
                    Blk old = p.get(x, y, z);
                    if (old == null || old.solid() && !old.id().contains("spruce") && !old.id().contains("lantern")) {
                        p.set(x, y, z, Blk.AIR);
                    }
                } else if (y <= ground && !p.has(x, y, z)) {
                    double n = Noise.hash(91, x * 31 + y, z);
                    String b = y <= floor ? (Math.abs(s) < 1.2 ? "gravel" : "cobblestone")
                            : (n < 0.04 ? "coal_ore" : n < 0.06 ? "iron_ore" : n < 0.075 ? "copper_ore"
                            : n < 0.08 ? "gold_ore" : n < 0.3 ? "andesite" : n < 0.4 ? "tuff" : "stone");
                    p.set(x, y, z, b);
                }
            }
            if (Math.abs(s) < 0.6) {
                p.set(x, floor, z, "gravel");
                p.set(x, floor + 1, z, Blk.deco("rail"));
            }
        }
    }

    private static void frame(Plan p, Vec center, Vec side, double tube, int floor) {
        int roof = (int) Math.floor(center.y() + tube - 0.5);
        for (int s : new int[] {-1, 1}) {
            Vec q = center.add(side.mul(s * (tube - 0.4)));
            int x = (int) Math.floor(q.x());
            int z = (int) Math.floor(q.z());
            for (int y = floor + 1; y <= roof; y++) {
                p.set(x, y, z, "stripped_spruce_log[axis=y]");
            }
        }
        for (double s = -tube + 0.5; s <= tube - 0.5; s += 0.5) {
            Vec q = center.add(side.mul(s));
            p.set((int) Math.floor(q.x()), roof, (int) Math.floor(q.z()), "spruce_planks");
        }
        p.set((int) Math.floor(center.x()), roof - 1, (int) Math.floor(center.z()), "lantern[hanging=true]");
    }

    // =========================================================================================
    // Gipfelkreuz
    // =========================================================================================

    static void gipfel(World w, Course c) {
        Plan p = w.plan();
        Terrain t = w.terrain();
        // Gipfel suchen: höchster Punkt rund um die Mitte des Bergs
        int bx = 640;
        int bz = -600;
        int best = t.height(bx, bz);
        for (int dx = -30; dx <= 30; dx++) {
            for (int dz = -30; dz <= 30; dz++) {
                int h = t.height(640 + dx, -600 + dz);
                if (h > best) {
                    best = h;
                    bx = 640 + dx;
                    bz = -600 + dz;
                }
            }
        }
        summitCross(p, bx, best + 1, bz);
        // Felsscharte: zwei Felstürme links und rechts von Ring 10
        Ring notch = c.rings.get(9);
        Vec u = across(notch);
        SplittableRandom rnd = new SplittableRandom(61);
        for (int s : new int[] {-1, 1}) {
            Vec q = notch.center().add(u.mul(s * (notch.radius() + 6)));
            spire(p, t, (int) Math.floor(q.x()), (int) Math.floor(q.z()), 5, (int) notch.center().y() + 14, rnd);
        }
        Vec l = c.landing.at();
        Scenery.hut(p, t, (int) l.x() - 20, (int) l.z() - 4, 10, 8, true, 77);
        w.trees().block((int) l.x() - 24, (int) l.z() - 8, (int) l.x() - 6, (int) l.z() + 8);
    }

    private static void summitCross(Plan p, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.set(x + dx, y - 1, z + dz, "cobblestone");
                p.set(x + dx, y, z + dz, dx == 0 && dz == 0 ? "cobblestone" : "cobblestone_slab[type=bottom]");
            }
        }
        for (int k = 1; k <= 14; k++) {
            p.set(x, y + k, z, "dark_oak_log[axis=y]");
        }
        for (int k = -4; k <= 4; k++) {
            if (k != 0) {
                p.set(x + k, y + 10, z, "dark_oak_log[axis=x]");
            }
        }
        p.set(x, y + 15, z, "lightning_rod[facing=up]");
        p.set(x - 5, y + 10, z, "gold_block");
        p.set(x + 5, y + 10, z, "gold_block");
        p.set(x, y + 10, z + 1, "gold_block");
        p.set(x, y + 10, z - 1, "gold_block");
        p.set(x + 1, y + 1, z + 1, "lantern");
    }

    private static void spire(Plan p, Terrain t, int x, int z, int r, int top, SplittableRandom rnd) {
        int ground = t.height(x, z);
        for (int y = ground - 3; y <= top; y++) {
            double f = (double) (y - ground) / Math.max(1, top - ground);
            double rr = r * (1 - 0.65 * f);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.hypot(dx, dz) <= rr + Noise.hash(3, x + dx + y, z + dz) * 0.8) {
                        int k = rnd.nextInt(10);
                        p.set(x + dx, y, z + dz, k < 5 ? "stone" : (k < 8 ? "andesite" : "tuff"));
                    }
                }
            }
        }
    }

    // =========================================================================================
    // Hilfen
    // =========================================================================================

    /** Waagerechte Richtung quer zum Ring (in der Ring-Ebene). */
    static Vec across(Ring r) {
        Vec n = r.normal();
        Vec u = Vec.UP.cross(n);
        return u.length() < 1e-6 ? new Vec(1, 0, 0) : u.normalize();
    }
}
