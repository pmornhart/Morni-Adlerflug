package de.moinmorni.adlerflug.game;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.courses.Scenery;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.world.Holograms;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Die Ringe als feine, runde Block-Anzeigen ({@link Scenery#ringParts}): leuchtend, auch im Dunkeln gut zu sehen. Sie
 * werden nie gespeichert, sondern beim Laden des Chunks neu erzeugt. Pro Spieler: geschaffte Ringe verschwinden mit
 * einem Funkenregen (andere sehen sie weiter), der nächste Ring bekommt einen leuchtenden Kranz aus Funken, der auch
 * hinter Bergen und von weit weg zu sehen ist. Neustart oder neue Strecke zeigt alle wieder.
 */
public final class RingTint implements Listener {

    private static final String TAG = Holograms.TAG + "_ring";
    /** So weit sieht man die Anzeigen (×64 Blöcke, soweit der Server sie überträgt). */
    private static final float VIEW_RANGE = 6f;
    private static final int HALO_POINTS = 40;

    /** Ein Ring mit seinen Teilen und den erzeugten Anzeigen. */
    private static final class Spec {
        final String key;
        final Ring ring;
        final List<Scenery.RingPart> parts;
        final List<Entity> spawned = new ArrayList<>();

        Spec(String key, Ring ring) {
            this.key = key;
            this.ring = ring;
            this.parts = Scenery.ringParts(ring);
        }

        long chunk() {
            return chunkKey((int) Math.floor(ring.center().x()) >> 4, (int) Math.floor(ring.center().z()) >> 4);
        }
    }

    private final JavaPlugin plugin;
    private World world;
    private final Map<String, Spec> specs = new HashMap<>();
    private final Map<Long, List<Spec>> byChunk = new HashMap<>();
    private final Map<UUID, Set<String>> passed = new HashMap<>();
    private final Map<String, BlockData> cache = new HashMap<>();

    public RingTint(JavaPlugin plugin, List<Course> courses) {
        this.plugin = plugin;
        for (Course c : courses) {
            for (Ring r : c.rings) {
                Spec s = new Spec(c.id + ":" + r.index(), r);
                specs.put(s.key, s);
                byChunk.computeIfAbsent(s.chunk(), k -> new ArrayList<>()).add(s);
            }
        }
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Alte Reste entfernen und die Ringe in geladenen Chunks erzeugen. */
    public void start(World w) {
        this.world = w;
        for (Entity e : w.getEntities()) {
            if (e.getScoreboardTags().contains(TAG)) {
                e.remove();
            }
        }
        for (Spec s : specs.values()) {
            if (w.isChunkLoaded((int) Math.floor(s.ring.center().x()) >> 4, (int) Math.floor(s.ring.center().z()) >> 4)) {
                spawn(s);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (world == null || !event.getWorld().equals(world)) {
            return;
        }
        List<Spec> list = byChunk.get(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
        if (list != null) {
            Bukkit.getScheduler().runTask(plugin, () -> list.forEach(this::spawn));
        }
    }

    private BlockData data(String s) {
        return cache.computeIfAbsent(s, k -> Bukkit.createBlockData(k.contains(":") ? k : "minecraft:" + k));
    }

    private void spawn(Spec s) {
        if (!s.spawned.isEmpty() && s.spawned.get(0).isValid()) {
            return;
        }
        s.spawned.forEach(Entity::remove);
        s.spawned.clear();
        Vec c = s.ring.center();
        // Alle Teile hängen an einem Punkt unter der Ring-Mitte; die Größe der Anzeige-Box sorgt dafür, dass der Ring
        // nicht ausgeblendet wird, solange irgendein Teil zu sehen ist.
        float ext = (float) (s.ring.radius() + 2.5);
        Location at = new Location(world, c.x(), c.y() - ext, c.z());
        for (Scenery.RingPart part : s.parts) {
            BlockData bd;
            try {
                bd = data(part.block());
            } catch (IllegalArgumentException e) {
                continue;
            }
            Transformation tf = transform(part, ext);
            BlockDisplay d = world.spawn(at, BlockDisplay.class, b -> {
                b.setPersistent(false);
                b.addScoreboardTag(TAG);
                b.setBlock(bd);
                b.setTransformation(tf);
                b.setBrightness(new Display.Brightness(15, 15));
                b.setViewRange(VIEW_RANGE);
                b.setDisplayWidth(ext * 2);
                b.setDisplayHeight(ext * 2);
            });
            s.spawned.add(d);
        }
        // Wer den Ring gerade geschafft hat, sieht ihn auch nach dem Neu-Laden nicht
        for (Map.Entry<UUID, Set<String>> e : passed.entrySet()) {
            if (e.getValue().contains(s.key)) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    s.spawned.forEach(d -> p.hideEntity(plugin, d));
                }
            }
        }
    }

    /** Lage eines Teils: Quader 1×1×1 → gestreckt, gedreht und an seine Stelle geschoben. */
    static Transformation transform(Scenery.RingPart p, float ext) {
        Vec a = p.along();
        Vec o = p.out();
        Vec n = p.depth();
        // rechtshändig halten (sonst wäre es eine Spiegelung)
        if (a.cross(o).dot(n) < 0) {
            n = n.mul(-1);
        }
        Matrix3f m = new Matrix3f((float) a.x(), (float) a.y(), (float) a.z(), (float) o.x(), (float) o.y(),
                (float) o.z(), (float) n.x(), (float) n.y(), (float) n.z());
        Quaternionf q = new Quaternionf().setFromNormalized(m).normalize();
        Vector3f scale = new Vector3f((float) p.length(), (float) p.thick(), (float) p.deep());
        Vector3f half = new Vector3f(scale).mul(0.5f);
        q.transform(half);
        Vector3f t = new Vector3f((float) p.at().x(), (float) p.at().y() + ext, (float) p.at().z()).sub(half);
        return new Transformation(t, q, scale, new Quaternionf());
    }

    /** Ring {@code index} für diesen Spieler geschafft: verschwindet mit einem Funkenregen. */
    public void passed(Player p, Course c, int index) {
        Spec s = specs.get(c.id + ":" + index);
        if (s == null) {
            return;
        }
        passed.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(s.key);
        s.spawned.forEach(d -> {
            if (d.isValid()) {
                p.hideEntity(plugin, d);
            }
        });
        Color colour = Color.fromRGB(Integer.parseInt(Scenery.ringColour(s.ring).substring(1), 16));
        Vec ctr = s.ring.center();
        Vec n = s.ring.normal();
        Vec u = Math.abs(n.y()) > 0.95 ? new Vec(1, 0, 0) : Vec.UP.cross(n).normalize();
        Vec v = n.cross(u).normalize();
        double r = s.ring.radius() + 0.95;
        for (int k = 0; k < 28; k++) {
            double a = 2 * Math.PI * k / 28;
            Vec q = ctr.add(u.mul(Math.cos(a) * r)).add(v.mul(Math.sin(a) * r));
            p.spawnParticle(Particle.DUST, q.x(), q.y(), q.z(), 2, 0.15, 0.15, 0.15, 0,
                    new Particle.DustOptions(Color.fromRGB(120, 255, 140), 1.6f), true);
            if (k % 2 == 0) {
                p.spawnParticle(Particle.DUST_COLOR_TRANSITION, q.x(), q.y(), q.z(), 1, 0.2, 0.2, 0.2, 0,
                        new Particle.DustTransition(colour, Color.WHITE, 1.4f), true);
            }
        }
        p.spawnParticle(Particle.FIREWORK, ctr.x(), ctr.y(), ctr.z(), 14, 0.6, 0.6, 0.6, 0.12, null, true);
    }

    /** Leuchtender Kranz um den nächsten Ring (nur für diesen Spieler, auch von weit weg zu sehen). */
    public void highlight(Player p, Ring ring, long tick) {
        Color colour = Color.fromRGB(Integer.parseInt(Scenery.ringColour(ring).substring(1), 16));
        Vec ctr = ring.center();
        Vec n = ring.normal();
        Vec u = Math.abs(n.y()) > 0.95 ? new Vec(1, 0, 0) : Vec.UP.cross(n).normalize();
        Vec v = n.cross(u).normalize();
        double r = ring.radius() + 2.0;
        double turn = (tick % 80) / 80.0 * 2 * Math.PI;
        Particle.DustOptions dust = new Particle.DustOptions(colour, 2.2f);
        for (int k = 0; k < HALO_POINTS; k++) {
            double a = 2 * Math.PI * k / HALO_POINTS + turn / 4;
            Vec q = ctr.add(u.mul(Math.cos(a) * r)).add(v.mul(Math.sin(a) * r));
            p.spawnParticle(Particle.DUST, q.x(), q.y(), q.z(), 1, 0, 0, 0, 0, dust, true);
        }
        // zwei kreisende Lichter
        for (int k = 0; k < 2; k++) {
            double a = turn + k * Math.PI;
            Vec q = ctr.add(u.mul(Math.cos(a) * r)).add(v.mul(Math.sin(a) * r));
            p.spawnParticle(Particle.END_ROD, q.x(), q.y(), q.z(), 1, 0, 0, 0, 0, null, true);
        }
    }

    /** Alle geschafften Ringe dieser Strecke wieder zeigen. */
    public void reset(Player p, Course c) {
        Set<String> mine = passed.get(p.getUniqueId());
        if (mine == null || mine.isEmpty()) {
            return;
        }
        for (String key : new HashSet<>(mine)) {
            Spec s = specs.get(key);
            if (s != null) {
                s.spawned.forEach(d -> {
                    if (d.isValid()) {
                        p.showEntity(plugin, d);
                    }
                });
            }
            mine.remove(key);
        }
    }

    public void forget(UUID id) {
        passed.remove(id);
    }
}
