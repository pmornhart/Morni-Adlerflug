package de.moinmorni.adlerflug.course;

/**
 * Ein Block für den Bauplan: Block-Zustand als Text wie im Spiel (z. B. {@code minecraft:spruce_stairs[facing=east]})
 * und ob man dagegen fliegt. Ohne Bukkit, damit die Prüfung der Strecken ohne Server läuft.
 */
public record Blk(String data, boolean solid) {

    /** Luft: schneidet Landschaft weg (Stollen, Tore). */
    public static final Blk AIR = new Blk("minecraft:air", false);
    public static final Blk WATER = new Blk("minecraft:water", false);

    /** Fester Block (Name ohne „minecraft:“, auch mit Zustand). */
    public static Blk of(String id) {
        return new Blk(ns(id), true);
    }

    /** Ohne Kollision: Blumen, Fackeln, Teppich, Schilder … */
    public static Blk deco(String id) {
        return new Blk(ns(id), false);
    }

    public boolean air() {
        return data.equals("minecraft:air") || data.equals("minecraft:cave_air");
    }

    /** Name ohne Zustand, z. B. {@code minecraft:ladder}. */
    public String id() {
        int i = data.indexOf('[');
        return i < 0 ? data : data.substring(0, i);
    }

    private static String ns(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }
}
