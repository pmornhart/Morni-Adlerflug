package de.moinmorni.adlerflug.land;

import java.util.ArrayList;
import java.util.List;

/**
 * Bäume der Landschaft: Fichten (spitz), Lärchen (schmal) und ein paar Birken an fest gewürfelten Stellen – eine pro
 * Zelle von {@link #CELL}×{@link #CELL} Blöcken, nie in einer Flugschneise oder über der Baumgrenze. Ohne Bukkit, damit
 * die Prüfung der Strecken auch Bäume sieht.
 */
public final class Trees {

    /** Rastergröße: in jeder Zelle höchstens ein Baum. */
    public static final int CELL = 6;
    /** So weit reicht ein Baum höchstens zur Seite. */
    public static final int REACH = 3;

    /** Ein Baum: Stamm-Position, Boden, Höhe, Art. */
    public record Tree(int x, int ground, int z, int height, Kind kind) {
        public int top() {
            return ground + height + 1;
        }
    }

    public enum Kind {
        SPRUCE, LARCH, BIRCH
    }

    /** Ein Block eines Baums. */
    public record Part(int x, int y, int z, String block) {
    }

    private final Terrain terrain;
    private final long seed;
    private final Noise forestNoise;
    /** Zusätzliche Sperrflächen (Bauten, Plätze): x0, z0, x1, z1. */
    private final List<int[]> blocked = new ArrayList<>();

    public Trees(Terrain terrain) {
        this.terrain = terrain;
        this.seed = terrain.seed() + 101;
        this.forestNoise = new Noise(seed);
    }

    /** Hier keine Bäume (Plätze, Bauten). */
    public void block(int x0, int z0, int x1, int z1) {
        blocked.add(new int[] {Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
    }

    /** Baum der Zelle (cx, cz) oder null. */
    public Tree at(int cx, int cz) {
        double chance = Noise.hash(seed, cx, cz);
        double forest = 0.5 + 0.5 * forestNoise.at(cx * CELL / 90.0, cz * CELL / 90.0);
        if (chance > 0.18 + 0.55 * forest) {
            return null;
        }
        int x = cx * CELL + 1 + (int) (Noise.hash(seed + 1, cx, cz) * (CELL - 2));
        int z = cz * CELL + 1 + (int) (Noise.hash(seed + 2, cx, cz) * (CELL - 2));
        for (int[] b : blocked) {
            if (x >= b[0] - REACH && x <= b[2] + REACH && z >= b[1] - REACH && z <= b[3] + REACH) {
                return null;
            }
        }
        Terrain.Style style = terrain.style(x, z);
        if (style == Terrain.Style.GLACIER || style == Terrain.Style.ROCK) {
            return null;
        }
        int h = terrain.height(x, z);
        if (h < 62 || h > Terrain.TREE_LINE) {
            return null;
        }
        // nicht an steilen Hängen
        double slope = 0;
        for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            slope = Math.max(slope, Math.abs(terrain.heightExact(x + d[0], z + d[1]) - h));
        }
        if (slope > 3.5 || terrain.water(x, z, h) != Integer.MIN_VALUE) {
            return null;
        }
        String surface = terrain.surface(x, z, h, slope / 2);
        if (!surface.equals("grass_block") && !surface.equals("moss_block") && !surface.equals("coarse_dirt")) {
            return null;
        }
        double k = Noise.hash(seed + 3, cx, cz);
        Kind kind = h < 92 && k < 0.22 ? Kind.BIRCH : (k > 0.8 ? Kind.LARCH : Kind.SPRUCE);
        int height = switch (kind) {
            case BIRCH -> 5 + (int) (k * 10) % 3;
            case LARCH -> 9 + (int) (k * 40) % 4;
            case SPRUCE -> 7 + (int) (k * 70) % 6;
        };
        if (h + height + 2 > terrain.skyLimit(x, z)) {
            return null;
        }
        return new Tree(x, h, z, height, kind);
    }

    /** Alle Bäume, die in den Bereich x0…x1 / z0…z1 hineinreichen. */
    public List<Tree> around(int x0, int z0, int x1, int z1) {
        List<Tree> out = new ArrayList<>();
        for (int cx = Math.floorDiv(x0 - REACH, CELL); cx <= Math.floorDiv(x1 + REACH, CELL); cx++) {
            for (int cz = Math.floorDiv(z0 - REACH, CELL); cz <= Math.floorDiv(z1 + REACH, CELL); cz++) {
                Tree t = at(cx, cz);
                if (t != null) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** Alle Blöcke eines Baums. */
    public static List<Part> parts(Tree t) {
        List<Part> out = new ArrayList<>();
        String log = switch (t.kind()) {
            case BIRCH -> "birch_log[axis=y]";
            case LARCH -> "dark_oak_log[axis=y]";
            default -> "spruce_log[axis=y]";
        };
        String leaves = switch (t.kind()) {
            case BIRCH -> "birch_leaves[persistent=true]";
            case LARCH -> "spruce_leaves[persistent=true]";
            case SPRUCE -> "spruce_leaves[persistent=true]";
        };
        int base = t.ground() + 1;
        for (int y = 0; y < t.height(); y++) {
            out.add(new Part(t.x(), base + y, t.z(), log));
        }
        int top = base + t.height();
        switch (t.kind()) {
            case SPRUCE -> {
                // Kegel: unten breit, oben spitz, Kränze mit Lücken
                for (int y = 2; y <= t.height(); y++) {
                    int fromTop = t.height() - y;
                    int r = fromTop <= 1 ? 1 : (fromTop % 2 == 0 ? Math.min(3, 1 + fromTop / 3) : Math.min(2, fromTop / 3 + 1));
                    disc(out, t.x(), base + y, t.z(), r, leaves, true);
                }
                out.add(new Part(t.x(), top, t.z(), leaves));
                out.add(new Part(t.x(), top + 1, t.z(), leaves));
            }
            case LARCH -> {
                for (int y = 3; y <= t.height(); y++) {
                    int fromTop = t.height() - y;
                    int r = fromTop < 2 ? 1 : 2;
                    disc(out, t.x(), base + y, t.z(), r, leaves, true);
                }
                out.add(new Part(t.x(), top, t.z(), leaves));
            }
            case BIRCH -> {
                for (int y = t.height() - 3; y <= t.height(); y++) {
                    int r = y >= t.height() - 1 ? 1 : 2;
                    disc(out, t.x(), base + y, t.z(), r, leaves, true);
                }
                out.add(new Part(t.x(), top, t.z(), leaves));
            }
        }
        return out;
    }

    private static void disc(List<Part> out, int x, int y, int z, int r, String leaves, boolean round) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if ((dx == 0 && dz == 0) || (round && r > 1 && Math.abs(dx) == r && Math.abs(dz) == r)) {
                    continue;
                }
                out.add(new Part(x + dx, y, z + dz, leaves));
            }
        }
    }
}
