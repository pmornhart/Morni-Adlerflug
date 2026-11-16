package de.moinmorni.adlerflug.world;

import de.moinmorni.adlerflug.course.Blk;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Plan;
import de.moinmorni.adlerflug.courses.World;
import de.moinmorni.adlerflug.flight.Vec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Die Adlerflug-Welt: eine eigene Welt, deren Chunks der {@link FlightGenerator} aus dem Modell erzeugt. Ändert sich
 * die Bau-Version ({@link #VERSION}) oder der Bauplan, wird die Welt gelöscht und entsteht neu – Spieler bauen hier
 * nichts, es geht nichts verloren. Schilder bekommen ihren Text, sobald ihr Chunk zum ersten Mal geladen wird.
 */
public final class Construction implements Listener {

    /** Bau-Version: erhöhen, wenn sich Landschaft, Basislager oder Strecken ändern – dann entsteht die Welt neu. */
    public static final int VERSION = 2;
    /** Bis hierhin überträgt der Server die Ring-Anzeigen (Blöcke). */
    private static final int DISPLAY_RANGE = 320;
    private static final String STAMP = "welt.yml";

    private final JavaPlugin plugin;
    private final World model;
    private final String name;
    private final Logger log;
    private final Map<Long, List<Plan.Sign>> signs = new HashMap<>();
    private org.bukkit.World world;
    private boolean ready;
    private FlightGenerator generator;

    public Construction(JavaPlugin plugin, World model, String name) {
        this.plugin = plugin;
        this.model = model;
        this.name = name;
        this.log = plugin.getLogger();
    }

    public org.bukkit.World world() {
        return world;
    }

    public boolean ready() {
        return ready;
    }

    /** Welt laden oder (bei neuer Bau-Version) frisch anlegen. */
    public void setup() throws IOException {
        Plan plan = model.plan();
        long t = System.currentTimeMillis();
        generator = prepare(plan);
        String fingerprint = VERSION + ":" + plan.fingerprint();
        Path stampFile = plugin.getDataFolder().toPath().resolve(STAMP);
        YamlConfiguration stamp = YamlConfiguration.loadConfiguration(stampFile.toFile());
        String built = stamp.getString("bau", "");
        world = create();
        if (!fingerprint.equals(built)) {
            if (!built.isEmpty()) {
                log.info("Neue Bau-Version (" + built + " → " + fingerprint + ") – die Adlerflug-Welt entsteht neu.");
                File folder = world.getWorldFolder();
                if (!Bukkit.unloadWorld(world, false)) {
                    throw new IOException("Welt " + name + " ließ sich nicht entladen");
                }
                deleteFolder(folder.toPath());
                world = create();
            }
            stamp.set("bau", fingerprint);
            stamp.set("bloecke", plan.size());
            stamp.set("angelegt", java.time.Instant.now().toString());
            Files.createDirectories(stampFile.getParent());
            stamp.save(stampFile.toFile());
        }
        rules();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ready = true;
        log.info("Adlerflug-Welt bereit (" + plan.size() + " Blöcke im Bauplan, " + (System.currentTimeMillis() - t)
                + " ms).");
    }

    /** Bauplan nach Chunks sortieren und Block-Zustände einmal anlegen (Hauptthread). */
    private FlightGenerator prepare(Plan plan) {
        Map<String, Short> ids = new HashMap<>();
        List<BlockData> palette = new ArrayList<>();
        Map<Long, List<long[]>> byChunk = new HashMap<>();
        for (Map.Entry<Long, Blk> e : plan.blocks().entrySet()) {
            long k = e.getKey();
            String data = e.getValue().data();
            Short id = ids.get(data);
            if (id == null) {
                BlockData bd;
                try {
                    bd = Bukkit.createBlockData(data);
                } catch (IllegalArgumentException ex) {
                    log.warning("Unbekannter Block im Bauplan: " + data);
                    bd = null;
                }
                id = (short) palette.size();
                palette.add(bd);
                ids.put(data, id);
            }
            int x = Plan.x(k);
            int y = Plan.y(k);
            int z = Plan.z(k);
            byChunk.computeIfAbsent(FlightGenerator.chunkKey(x >> 4, z >> 4), c -> new ArrayList<>())
                    .add(new long[] {(y << 8) | ((z & 15) << 4) | (x & 15), id});
        }
        Map<Long, FlightGenerator.ChunkBlocks> chunks = new HashMap<>();
        byChunk.forEach((key, list) -> {
            int[] pos = new int[list.size()];
            short[] pal = new short[list.size()];
            for (int i = 0; i < list.size(); i++) {
                pos[i] = (int) list.get(i)[0];
                pal[i] = (short) list.get(i)[1];
            }
            chunks.put(key, new FlightGenerator.ChunkBlocks(pos, pal));
        });
        for (Plan.Sign s : plan.signs()) {
            signs.computeIfAbsent(FlightGenerator.chunkKey(s.x() >> 4, s.z() >> 4), c -> new ArrayList<>()).add(s);
        }
        return new FlightGenerator(model, Map.copyOf(chunks), palette.toArray(new BlockData[0]));
    }

    private org.bukkit.World create() {
        // Die Ringe sind Block-Anzeigen: sie sollen weit zu sehen sein (Standard: 128 Blöcke). Nur für diese Welt und
        // nur im Speicher – Spigot liest den Wert beim Anlegen der Welt.
        try {
            var cfg = Bukkit.spigot().getSpigotConfig();
            String key = "world-settings." + name + ".entity-tracking-range.display";
            if (!cfg.contains(key)) {
                cfg.set(key, DISPLAY_RANGE);
            }
        } catch (RuntimeException | LinkageError e) {
            // ohne Spigot-Einstellungen eben mit dem Standard
        }
        WorldCreator wc = new WorldCreator(name).generator(generator)
                .environment(org.bukkit.World.Environment.NORMAL).generateStructures(false);
        org.bukkit.World w = Bukkit.createWorld(wc);
        if (w == null) {
            throw new IllegalStateException("Welt " + name + " ließ sich nicht anlegen");
        }
        return w;
    }

    /** Schilder beschriften, wenn ihr Chunk geladen wird (beim ersten Mal entstehen sie leer). */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.getWorld().equals(world)) {
            return;
        }
        List<Plan.Sign> list = signs.get(FlightGenerator.chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
        if (list == null) {
            return;
        }
        for (Plan.Sign s : list) {
            Block b = world.getBlockAt(s.x(), s.y(), s.z());
            if (b.getState() instanceof Sign sign) {
                SignSide side = sign.getSide(Side.FRONT);
                boolean same = true;
                for (int i = 0; i < Math.min(4, s.lines().size()); i++) {
                    Component want = Component.text(s.lines().get(i));
                    if (!want.equals(side.line(i))) {
                        side.line(i, want);
                        same = false;
                    }
                }
                if (!same || !sign.isWaxed()) {
                    sign.setWaxed(true);
                    sign.update(true, false);
                }
            }
        }
    }

    /**
     * Chunks entlang aller Strecken im Hintergrund erzeugen, damit beim ersten Flug nichts nachlädt (die Welt entsteht
     * sonst erst, wenn jemand hinkommt).
     */
    public void warmUp(int perSecond) {
        List<long[]> todo = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Course c : model.courses()) {
            for (Vec p : c.pathPoints(8)) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int cx = ((int) Math.floor(p.x()) >> 4) + dx;
                        int cz = ((int) Math.floor(p.z()) >> 4) + dz;
                        if (seen.add(FlightGenerator.chunkKey(cx, cz))) {
                            todo.add(new long[] {cx, cz});
                        }
                    }
                }
            }
        }
        int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            for (int i = 0; i < perSecond / 4 && index[0] < todo.size(); i++, index[0]++) {
                long[] c = todo.get(index[0]);
                world.getChunkAtAsync((int) c[0], (int) c[1], true);
            }
            if (index[0] >= todo.size()) {
                log.info("Strecken vorbereitet (" + todo.size() + " Chunks).");
                task.cancel();
            }
        }, 40L, 5L);
    }

    /** Ewiger Spätvormittag ohne Wetter, keine Monster, nichts geht kaputt. */
    private void rules() {
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(5000);
        world.setStorm(false);
        world.setThundering(false);
        var b = model.basecamp();
        world.setSpawnLocation(new Location(world, b.spawnX(), b.spawnY(), b.spawnZ(), b.spawnYaw(), 0));
        rule(GameRules.ADVANCE_TIME, false);
        rule(GameRules.ADVANCE_WEATHER, false);
        rule(GameRules.SPAWN_MOBS, false);
        rule(GameRules.SPAWN_MONSTERS, false);
        rule(GameRules.SPAWN_PHANTOMS, false);
        rule(GameRules.SPAWN_PATROLS, false);
        rule(GameRules.SPAWN_WANDERING_TRADERS, false);
        rule(GameRules.FALL_DAMAGE, false);
        rule(GameRules.FIRE_DAMAGE, false);
        rule(GameRules.DROWNING_DAMAGE, false);
        rule(GameRules.FREEZE_DAMAGE, false);
        rule(GameRules.PVP, false);
        rule(GameRules.RANDOM_TICK_SPEED, 0);
        rule(GameRules.KEEP_INVENTORY, true);
        rule(GameRules.IMMEDIATE_RESPAWN, true);
        rule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        rule(GameRules.SHOW_DEATH_MESSAGES, false);
        rule(GameRules.MOB_GRIEFING, false);
        rule(GameRules.BLOCK_DROPS, false);
        rule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        rule(GameRules.RESPAWN_RADIUS, 0);
        rule(GameRules.LOCATOR_BAR, false);
        rule(GameRules.WATER_SOURCE_CONVERSION, false);
    }

    private <T> void rule(GameRule<T> r, T value) {
        try {
            world.setGameRule(r, value);
        } catch (RuntimeException e) {
            log.fine("Spielregel " + r + " nicht gesetzt: " + e.getMessage());
        }
    }

    /** Ordner der alten Welt löschen – nur wenn es wirklich der eigene Welt-Ordner ist. */
    private void deleteFolder(Path folder) throws IOException {
        Path server = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path target = folder.toAbsolutePath().normalize();
        Path main = Bukkit.getWorlds().get(0).getWorldFolder().toPath().toAbsolutePath().normalize();
        if (target.equals(server) || target.equals(main) || !target.startsWith(server)
                || !target.getFileName().toString().equals(name)) {
            throw new IOException("Welt-Ordner sieht nicht nach Adlerflug aus, lösche ihn nicht: " + target);
        }
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    /** Nach einem Befehl: beim nächsten Start neu entstehen lassen. */
    public void scheduleRebuild() throws IOException {
        Path stampFile = plugin.getDataFolder().toPath().resolve(STAMP);
        YamlConfiguration stamp = YamlConfiguration.loadConfiguration(stampFile.toFile());
        stamp.set("bau", "neu");
        stamp.save(stampFile.toFile());
    }
}
