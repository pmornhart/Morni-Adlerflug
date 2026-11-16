package de.moinmorni.adlerflug.world;

import de.moinmorni.adlerflug.courses.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Deko-Tiere (Kühe auf der Weide, Schafe am Hang): ohne eigenen Willen, nicht verwundbar, nicht gespeichert – sie
 * entstehen neu, wenn ihr Chunk geladen wird, und es gibt nie doppelte.
 */
public final class Critters implements Listener {

    private static final String TAG = "adlerflug-tier";

    private final JavaPlugin plugin;
    private final org.bukkit.World world;
    private final Map<Long, List<World.Critter>> byChunk = new HashMap<>();
    private final Map<World.Critter, Entity> spawned = new HashMap<>();

    public Critters(JavaPlugin plugin, org.bukkit.World world, List<World.Critter> list) {
        this.plugin = plugin;
        this.world = world;
        for (World.Critter c : list) {
            byChunk.computeIfAbsent(FlightGenerator.chunkKey((int) Math.floor(c.x()) >> 4, (int) Math.floor(c.z()) >> 4),
                    k -> new ArrayList<>()).add(c);
        }
    }

    public void start() {
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains(TAG)) {
                e.remove();
            }
        }
        byChunk.forEach((key, list) -> {
            if (world.isChunkLoaded((int) (key >> 32), (int) (long) key)) {
                list.forEach(this::spawn);
            }
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.getWorld().equals(world)) {
            return;
        }
        List<World.Critter> list = byChunk.get(FlightGenerator.chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
        if (list != null) {
            Bukkit.getScheduler().runTask(plugin, () -> list.forEach(this::spawn));
        }
    }

    private void spawn(World.Critter c) {
        Entity old = spawned.get(c);
        if (old != null && old.isValid()) {
            return;
        }
        NamespacedKey key = NamespacedKey.minecraft(c.type());
        EntityType type = Registry.ENTITY_TYPE.get(key);
        if (type == null || type.getEntityClass() == null) {
            return;
        }
        Entity e = world.spawn(new Location(world, c.x(), c.y(), c.z(), c.yaw(), 0), type.getEntityClass(), x -> {
            x.setPersistent(false);
            x.addScoreboardTag(TAG);
            x.setInvulnerable(true);
            x.setSilent(true);
            if (x instanceof LivingEntity l) {
                l.setAI(false);
                l.setCollidable(false);
                l.setRemoveWhenFarAway(false);
            }
        });
        spawned.put(c, e);
    }
}
