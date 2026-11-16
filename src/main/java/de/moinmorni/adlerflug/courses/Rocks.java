package de.moinmorni.adlerflug.courses;

import de.moinmorni.adlerflug.course.Blk;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Plan;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.flight.Wind;
import de.moinmorni.adlerflug.land.Noise;
import de.moinmorni.adlerflug.land.Terrain;
import java.util.SplittableRandom;

/**
 * Felsbauten für die schweren Strecken: Felsnadeln zum Umfliegen, natürliche Felsbögen um einen Ring, dunkle Höhlen
 * (nur die Ringe leuchten) und Windsäcke, die zeigen, woher der Wind kommt. Alles eigene Entwürfe; schreibt in den
 * {@link Plan}, die Landschaft kommt von {@link Terrain}.
 */
final class Rocks {

    private Rocks() {
    }

    /** Fels mit etwas Abwechslung (Stein, Andesit, Tuff, ein paar bemooste Stellen). */
    static String stone(SplittableRandom r) {
        int k = r.nextInt(20);
        return k < 9 ? "stone" : (k < 14 ? "andesite" : (k < 17 ? "tuff" : (k < 19 ? "cobblestone" : "mossy_cobblestone")));
    }

    /**
     * Felsnadel vom Boden bis {@code top}: unten breit (Radius {@code r}), nach oben schmaler, mit Rillen; oben ein
     * Moospolster und ein paar Latschen.
     */
    static void pillar(Plan p, Terrain t, int x, int z, double r, int top, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        int ground = t.height(x, z);
        for (int y = ground - 4; y <= top; y++) {
            double f = Math.max(0, (double) (y - ground) / Math.max(1, top - ground));
            double rr = r * (1 - 0.55 * f) + 0.4 * Math.sin(y * 0.37 + seed);
            int reach = (int) Math.ceil(r + 1);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double ang = Math.atan2(dz, dx);
                    double groove = 0.35 * Math.sin(ang * 5 + seed);
                    if (Math.hypot(dx, dz) <= rr + groove + Noise.hash(seed, x + dx + y * 7, z + dz) * 0.6) {
                        p.set(x + dx, y, z + dz, y == top ? "moss_block" : stone(rnd));
                    }
                }
            }
        }
        p.set(x, top + 1, z, Blk.deco("moss_carpet"));
        p.setIfEmpty(x + 1, top + 1, z, Blk.deco("fern"));
        p.setIfEmpty(x, top + 1, z - 1, Blk.deco("short_grass"));
    }

    /**
     * Natürlicher Felsbogen um einen Ring: oben ein dicker Bogen aus Fels, links und rechts Pfeiler bis in den Boden.
     * Die Öffnung lässt um den Ring ({@code clear} Blöcke mehr als sein Rand) frei, so ist nichts im Weg.
     */
    static void arch(Plan p, Terrain t, Ring r, double clear, double thick, double deep, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        Vec u = r.across();
        Vec n = new Vec(r.normal().x(), 0, r.normal().z()).normalize();
        double inner = r.radius() + 1.3 + clear;
        double outer = inner + thick;
        Vec c = r.center();
        for (double along = -deep / 2; along <= deep / 2; along += 0.5) {
            for (double k = -outer - 1; k <= outer + 1; k += 0.5) {
                Vec q = c.add(u.mul(k)).add(n.mul(along));
                int x = (int) Math.floor(q.x());
                int z = (int) Math.floor(q.z());
                int ground = t.height(x, z);
                double rough = Noise.hash(seed, x * 3 + (int) (along * 2), z) * 1.2;
                for (int y = ground - 2; y <= (int) Math.ceil(c.y() + outer + 1); y++) {
                    double dy = y + 0.5 - c.y();
                    double dist = Math.hypot(k, dy);
                    boolean ring = dist >= inner && dist <= outer + rough;
                    boolean leg = Math.abs(k) >= inner && Math.abs(k) <= outer + rough && dy < 0;
                    if ((ring && dy >= -1) || leg) {
                        p.set(x, y, z, dy > outer - 1.5 && rnd.nextInt(3) == 0 ? "moss_block" : stone(rnd));
                    }
                }
            }
        }
    }

    /**
     * Dunkle Höhle entlang des Musterflugs zwischen Tick {@code from} und {@code to}: unregelmäßige Röhre (Radius
     * {@code tube} ± Rauschen) durch den Berg, Boden mit Geröll, nur ein paar leuchtende Flechten und Amethyste – sonst
     * sieht man nur die Ringe. Wo kein Berg darüber ist, bleibt alles, wie es ist.
     */
    static void cave(Plan p, Terrain t, Course c, int from, int to, double tube, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        for (int tick = Math.max(1, from); tick < Math.min(c.pathTicks(), to); tick++) {
            Vec a = Course.center(c.pathAt(tick - 1));
            Vec b = Course.center(c.pathAt(tick));
            Vec dir = b.sub(a).normalize();
            Vec side = new Vec(-dir.z(), 0, dir.x()).normalize();
            double len = a.distance(b);
            for (double f = 0; f < len; f += 0.5) {
                Vec ctr = a.add(dir.mul(f));
                double wobble = 0.8 * Noise.hash(seed, (int) Math.floor(ctr.x()), (int) Math.floor(ctr.z()));
                double rad = tube + wobble;
                for (double s = -rad - 2; s <= rad + 2; s += 0.5) {
                    Vec q = ctr.add(side.mul(s));
                    int x = (int) Math.floor(q.x());
                    int z = (int) Math.floor(q.z());
                    int ground = t.height(x, z);
                    for (int y = (int) Math.floor(ctr.y() - rad - 2); y <= (int) Math.ceil(ctr.y() + rad + 2); y++) {
                        double d = Math.hypot(s, (y + 0.5 - ctr.y()) * 1.15);
                        if (d <= rad) {
                            Blk old = p.get(x, y, z);
                            if (old == null || old.solid()) {
                                p.set(x, y, z, Blk.AIR);
                            }
                        } else if (d <= rad + 1.6 && y <= ground && !p.has(x, y, z)) {
                            double h = Noise.hash(seed + 3, x * 13 + y, z);
                            String wall = y < ctr.y() - rad + 1 ? (h < 0.5 ? "gravel" : "cobbled_deepslate")
                                    : (h < 0.03 ? "amethyst_block" : (h < 0.35 ? "deepslate" : (h < 0.55 ? "tuff" : "stone")));
                            p.set(x, y, z, wall);
                        }
                    }
                }
            }
            if (tick % 9 == 0) {
                // ein paar leuchtende Flechten an der Wand (schwaches Licht, Höhle bleibt dunkel)
                Vec ctr = Course.center(c.pathAt(tick));
                double s = (rnd.nextBoolean() ? 1 : -1) * (tube + 0.4);
                Vec q = ctr.add(side.mul(s)).add(0, -tube * 0.4, 0);
                p.setIfEmpty((int) Math.floor(q.x()), (int) Math.floor(q.y()), (int) Math.floor(q.z()),
                        Blk.deco("glow_lichen[down=true]"));
                if (tick % 27 == 0) {
                    Vec g = ctr.add(0, -tube - 0.2, 0).add(side.mul(-s * 0.5));
                    p.set((int) Math.floor(g.x()), (int) Math.floor(g.y()), (int) Math.floor(g.z()), "amethyst_block");
                    p.set((int) Math.floor(g.x()), (int) Math.floor(g.y()) + 1, (int) Math.floor(g.z()),
                            Blk.deco("amethyst_cluster[facing=up]"));
                }
            }
        }
    }

    /**
     * Windsack am Rand einer Windzone: Mast mit rot-weißem Sack, der in Windrichtung zeigt (so sieht man schon von
     * Weitem, wohin es drückt).
     */
    static void windsock(Plan p, Terrain t, double x, double z, Vec push, int height) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int g = t.height(bx, bz);
        p.set(bx, g, bz, "cobblestone");
        for (int k = 1; k <= height; k++) {
            p.set(bx, g + k, bz, k == height ? "stripped_spruce_log[axis=y]" : "spruce_fence");
        }
        Vec d = new Vec(push.x(), 0, push.z()).normalize();
        int sx = (int) Math.signum(Math.round(d.x() * 2));
        int sz = (int) Math.signum(Math.round(d.z() * 2));
        if (sx == 0 && sz == 0) {
            sz = 1;
        }
        String[] cloth = {"red_wool", "white_wool", "red_wool", "white_wool"};
        for (int k = 0; k < cloth.length; k++) {
            p.set(bx + sx * (k + 1), g + height - (k / 2), bz + sz * (k + 1), cloth[k]);
        }
    }

    /** Windsäcke an Anfang und Ende jeder Windzone, seitlich der Flugbahn. */
    static void windsocks(Plan p, Terrain t, Course c, double off) {
        for (Wind w : c.winds) {
            for (double f : new double[] {0.15, 0.85}) {
                double[] at = w.along(f);
                double dx = w.x1() - w.x0();
                double dz = w.z1() - w.z0();
                double len = Math.max(1e-6, Math.hypot(dx, dz));
                double x = at[0] - dz / len * off;
                double z = at[1] + dx / len * off;
                windsock(p, t, x, z, w.push(), 7);
            }
        }
    }
}
