package de.moinmorni.adlerflug.course;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bauplan aller Bauten der Adlerflug-Welt (Basislager, Ringe, Starttürme, Hütten, Burg, Stollen …): jeder Block mit
 * Position. Luft-Blöcke schneiden in die Landschaft (Stollen, Torbögen). Die Landschaft selbst ist Rechnung
 * ({@code land.Terrain}), der Welt-Erzeuger legt den Plan darüber.
 */
public final class Plan {

    /** Schild mit vier Zeilen (Vorderseite). */
    public record Sign(int x, int y, int z, List<String> lines) {
    }

    private final Map<Long, Blk> blocks = new HashMap<>();
    private final List<Sign> signs = new ArrayList<>();

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int x(long key) {
        return (int) (key >> 38);
    }

    public static int y(long key) {
        int y = (int) (key & 0xFFF);
        return y >= 0x800 ? y - 0x1000 : y;
    }

    public static int z(long key) {
        return (int) (key << 26 >> 38);
    }

    public void set(int x, int y, int z, Blk blk) {
        blocks.put(key(x, y, z), blk);
    }

    public void set(int x, int y, int z, String id) {
        set(x, y, z, id.equals("air") ? Blk.AIR : Blk.of(id));
    }

    /** Nur setzen, wo im Plan noch nichts steht. */
    public void setIfEmpty(int x, int y, int z, Blk blk) {
        blocks.putIfAbsent(key(x, y, z), blk);
    }

    public Blk get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    public boolean has(int x, int y, int z) {
        return blocks.containsKey(key(x, y, z));
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, Blk blk) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, blk);
                }
            }
        }
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String id) {
        fill(x0, y0, z0, x1, y1, z1, Blk.of(id));
    }

    public void sign(int x, int y, int z, List<String> lines) {
        signs.add(new Sign(x, y, z, List.copyOf(lines)));
    }

    public Map<Long, Blk> blocks() {
        return Collections.unmodifiableMap(blocks);
    }

    public List<Sign> signs() {
        return Collections.unmodifiableList(signs);
    }

    public int size() {
        return blocks.size();
    }

    /**
     * Zäune, Scheiben und Gitter mit ihren Nachbarn im Plan verbinden – die Welt entsteht ohne Block-Updates, sonst
     * blieben sie einzelne Pfosten.
     */
    public void connect() {
        Map<Long, Blk> changed = new HashMap<>();
        String[] sides = {"north", "east", "south", "west"};
        int[][] off = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        blocks.forEach((k, b) -> {
            String id = b.id();
            if (!fenceLike(id) || b.data().contains("[")) {
                return;
            }
            int x = x(k);
            int y = y(k);
            int z = z(k);
            StringBuilder state = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                Blk n = blocks.get(key(x + off[i][0], y, z + off[i][1]));
                boolean join = n != null && joins(id, n);
                state.append(state.length() == 0 ? "" : ",").append(sides[i]).append('=').append(join);
            }
            changed.put(k, new Blk(id + "[" + state + "]", b.solid()));
        });
        blocks.putAll(changed);
    }

    private static boolean fenceLike(String id) {
        return id.endsWith("_fence") || id.endsWith("glass_pane") || id.equals("minecraft:iron_bars");
    }

    private static boolean joins(String self, Blk other) {
        String o = other.id();
        if (other.air() || !other.solid() && !fenceLike(o)) {
            return false;
        }
        if (self.endsWith("_fence")) {
            if (o.endsWith("_fence") || o.endsWith("_fence_gate")) {
                return true;
            }
        } else if (fenceLike(o) || o.endsWith("_wall")) {
            return true;
        }
        return fullCube(o);
    }

    /** Grobe Schätzung „voller Würfel“ (zum Anschließen von Zäunen und Scheiben). */
    private static boolean fullCube(String id) {
        for (String part : new String[] {"_stairs", "_slab", "_fence", "_wall", "_door", "_trapdoor", "lantern",
            "_pane", "_bars", "torch", "_sign", "campfire", "_rod", "flower", "potted", "_carpet", "chain", "_button",
            "leaves", "bell", "_bed", "ladder", "_head", "_skull", "_plate", "glass"}) {
            if (id.contains(part)) {
                return false;
            }
        }
        return true;
    }

    /** Fingerabdruck: ändert sich ein Block, entsteht die Welt neu. Reihenfolge-unabhängig. */
    public String fingerprint() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            List<Long> keys = new ArrayList<>(blocks.keySet());
            Collections.sort(keys);
            byte[] buf = new byte[8];
            for (long k : keys) {
                for (int i = 0; i < 8; i++) {
                    buf[i] = (byte) (k >>> (56 - 8 * i));
                }
                sha.update(buf);
                sha.update(blocks.get(k).data().getBytes(StandardCharsets.UTF_8));
            }
            for (Sign s : signs) {
                sha.update((s.x() + "," + s.y() + "," + s.z() + String.join("|", s.lines()))
                        .getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : sha.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
