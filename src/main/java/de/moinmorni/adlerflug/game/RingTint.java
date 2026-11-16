package de.moinmorni.adlerflug.game;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.courses.Scenery;
import de.moinmorni.adlerflug.flight.Ring;
import io.papermc.paper.math.Position;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * Geschaffte Ringe leuchten grün – nur für den Spieler, der sie geschafft hat (andere sehen weiter die Farben der
 * Abschnitte). Neustart oder neue Strecke färbt sie zurück.
 */
public final class RingTint {

    private static final String DONE = "minecraft:lime_concrete";
    private static final String DONE_STUD = "minecraft:verdant_froglight[axis=y]";

    private final Map<String, Map<Integer, List<Scenery.RingBlock>>> blocks = new HashMap<>();
    private final Map<UUID, Set<String>> tinted = new HashMap<>();
    private final Map<String, BlockData> cache = new HashMap<>();

    public RingTint(List<Course> courses) {
        for (Course c : courses) {
            Map<Integer, List<Scenery.RingBlock>> m = new HashMap<>();
            for (Ring r : c.rings) {
                m.put(r.index(), Scenery.ringBlocks(r));
            }
            blocks.put(c.id, m);
        }
    }

    private BlockData data(String s) {
        return cache.computeIfAbsent(s, k -> Bukkit.createBlockData(k.contains(":") ? k : "minecraft:" + k));
    }

    /** Ring {@code index} für diesen Spieler grün färben. */
    public void passed(Player p, Course c, int index) {
        List<Scenery.RingBlock> list = blocks.getOrDefault(c.id, Map.of()).get(index);
        if (list == null) {
            return;
        }
        Map<Position, BlockData> change = new HashMap<>();
        for (Scenery.RingBlock b : list) {
            boolean stud = b.block().contains("glowstone") || b.block().contains("lantern") || b.block().contains("gold");
            change.put(Position.block(b.x(), b.y(), b.z()), data(stud ? DONE_STUD : DONE));
        }
        p.sendMultiBlockChange(change);
        tinted.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(c.id + ":" + index);
    }

    /** Alle eingefärbten Ringe dieser Strecke wieder in ihren echten Farben zeigen. */
    public void reset(Player p, Course c) {
        Set<String> mine = tinted.get(p.getUniqueId());
        if (mine == null || mine.isEmpty()) {
            return;
        }
        Map<Position, BlockData> change = new HashMap<>();
        for (String key : new HashSet<>(mine)) {
            String[] parts = key.split(":");
            List<Scenery.RingBlock> list = blocks.getOrDefault(parts[0], Map.of()).get(Integer.parseInt(parts[1]));
            if (list == null || p.getWorld() == null) {
                continue;
            }
            for (Scenery.RingBlock b : list) {
                change.put(Position.block(b.x(), b.y(), b.z()), p.getWorld().getBlockAt(b.x(), b.y(), b.z()).getBlockData());
            }
            mine.remove(key);
        }
        if (!change.isEmpty()) {
            p.sendMultiBlockChange(change);
        }
    }

    public void forget(UUID id) {
        tinted.remove(id);
    }
}
