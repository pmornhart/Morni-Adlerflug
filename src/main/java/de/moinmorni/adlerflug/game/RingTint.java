package de.moinmorni.adlerflug.game;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.courses.Scenery;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.world.Holograms;
import io.papermc.paper.entity.TeleportFlag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
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
 *
 * <p>Bewegte Ringe und Takt-Tore laufen nach der Flugzeit – die ist für jeden Flieger anders. Darum gibt es sie zweimal:
 * eine gemeinsame Anzeige für alle Zuschauer (läuft nach der Uhr des Servers) und für jeden Flieger eine eigene, die nur
 * er sieht und die genau nach seiner Flugzeit läuft (die gemeinsame ist für ihn ausgeblendet). Ein bewegter Ring hängt
 * mit allen Teilen an einem unsichtbaren Träger, nur der wird bewegt; die Flügel eines Tors gleiten über die
 * Interpolation des Spiels.
 */
public final class RingTint implements Listener {

    private static final String TAG = Holograms.TAG + "_ring";
    /** So weit sieht man die Anzeigen (×64 Blöcke, soweit der Server sie überträgt). */
    private static final float VIEW_RANGE = 6f;
    private static final int HALO_POINTS = 40;
    /** Über so viele Ticks gleitet ein bewegter Ring bzw. ein Flügel zur nächsten Stellung (weich statt ruckig). */
    private static final int GLIDE_TICKS = 2;
    /** Eigene Anzeige eines Rings höchstens so oft neu erzeugen (Ticks), falls sie verschwunden ist. */
    private static final long REBUILD_TICKS = 40;

    /** Ein Ring einer Strecke: Teile und die gemeinsame Anzeige (für alle, die ihn nicht gerade selbst fliegen). */
    static final class Spec {
        final String key;
        final String course;
        final Ring ring;
        final List<Scenery.RingPart> parts;
        final Shown shared = new Shown();

        Spec(String key, String course, Ring ring) {
            this.key = key;
            this.course = course;
            this.ring = ring;
            this.parts = Scenery.ringParts(ring);
        }

        long chunk() {
            return chunkKey((int) Math.floor(ring.center().x()) >> 4, (int) Math.floor(ring.center().z()) >> 4);
        }

        boolean moving() {
            return ring.motion() != null;
        }
    }

    /** Erzeugte Anzeigen eines Rings: Teile (bei bewegten Ringen am Träger), Träger, Flügel. */
    private static final class Shown {
        final List<Entity> parts = new ArrayList<>();
        Entity carrier;
        final List<BlockDisplay> wings = new ArrayList<>();

        /**
         * Gibt es die Anzeigen noch? Nicht {@code isValid()}: in einem Chunk, der geladen ist, dessen Wesen der Server
         * aber (noch) nicht verfolgt, ist ein frisch erzeugtes Wesen „nicht gültig“ – würde man dann neu erzeugen, kämen
         * jeden Tick über hundert Anzeigen dazu. Weg sind sie erst, wenn der Chunk sie beim Entladen verwirft.
         */
        boolean alive() {
            return !parts.isEmpty() && !parts.get(0).isDead() && (carrier == null || !carrier.isDead());
        }

        List<Entity> all() {
            List<Entity> out = new ArrayList<>(parts);
            if (carrier != null) {
                out.add(carrier);
            }
            out.addAll(wings);
            return out;
        }

        void remove() {
            all().forEach(Entity::remove);
            parts.clear();
            wings.clear();
            carrier = null;
        }
    }

    /** Eigene Anzeigen eines Fliegers für die bewegten Ringe und Tore seiner Strecke. */
    private static final class Own {
        final String course;
        final Map<String, Shown> copies = new HashMap<>();
        /** Wann eine eigene Anzeige zuletzt erzeugt wurde (Server-Tick) – höchstens alle {@link #REBUILD_TICKS}. */
        final Map<String, Long> built = new HashMap<>();

        Own(String course) {
            this.course = course;
        }
    }

    private final JavaPlugin plugin;
    private final LongSupplier clock;
    private World world;
    private final Map<String, Spec> specs = new HashMap<>();
    private final Map<Long, List<Spec>> byChunk = new HashMap<>();
    private final Map<UUID, Set<String>> passed = new HashMap<>();
    private final Map<UUID, Own> own = new HashMap<>();
    private final Map<String, BlockData> cache = new HashMap<>();

    /**
     * @param clock Uhr der gemeinsamen Anzeigen (Ticks des Servers)
     */
    public RingTint(JavaPlugin plugin, List<Course> courses, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
        courses.forEach(this::register);
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Schlüssel eines Rings (Pflicht-Ringe nach Nummer, Bonus-Ringe nach ihrer Stelle in der Liste). */
    static String key(Course c, Ring r) {
        return r.bonus() ? c.id + ":b" + c.bonus.indexOf(r) : c.id + ":" + r.index();
    }

    private void register(Course c) {
        List<Ring> all = new ArrayList<>(c.rings);
        all.addAll(c.bonus);
        for (Ring r : all) {
            Spec s = new Spec(key(c, r), c.id, r);
            specs.put(s.key, s);
            byChunk.computeIfAbsent(s.chunk(), k -> new ArrayList<>()).add(s);
        }
    }

    /** Strecke dazu (z. B. die neue Strecke des Tages); ihre Ringe erscheinen, sobald ihr Chunk geladen ist. */
    public void add(Course c) {
        register(c);
        if (world != null) {
            for (Spec s : specs.values()) {
                if (s.course.equals(c.id) && loaded(s.ring.center())) {
                    spawn(s);
                }
            }
        }
    }

    /** Alle Ringe einer Strecke weg (alte Strecke des Tages). */
    public void remove(String courseId) {
        for (Iterator<Spec> it = specs.values().iterator(); it.hasNext(); ) {
            Spec s = it.next();
            if (s.course.equals(courseId)) {
                s.shared.remove();
                List<Spec> list = byChunk.get(s.chunk());
                if (list != null) {
                    list.remove(s);
                }
                it.remove();
            }
        }
        for (Map.Entry<UUID, Own> e : own.entrySet()) {
            if (e.getValue().course.equals(courseId)) {
                e.getValue().copies.values().forEach(Shown::remove);
                e.getValue().copies.clear();
            }
        }
        passed.values().forEach(set -> set.removeIf(k -> k.startsWith(courseId + ":")));
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
            if (loaded(s.ring.center())) {
                spawn(s);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::animateShared, 1L, 1L);
    }

    private boolean loaded(Vec at) {
        return world.isChunkLoaded((int) Math.floor(at.x()) >> 4, (int) Math.floor(at.z()) >> 4);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (world == null || !event.getWorld().equals(world)) {
            return;
        }
        List<Spec> list = byChunk.get(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
        if (list != null) {
            List<Spec> copy = List.copyOf(list);
            Bukkit.getScheduler().runTask(plugin, () -> copy.forEach(s -> {
                if (specs.get(s.key) == s) {
                    spawn(s);
                }
            }));
        }
    }

    private BlockData data(String s) {
        return cache.computeIfAbsent(s, k -> Bukkit.createBlockData(k.contains(":") ? k : "minecraft:" + k));
    }

    /** Gemeinsame Anzeige erzeugen (falls nicht schon da). */
    private void spawn(Spec s) {
        if (s.shared.alive()) {
            return;
        }
        s.shared.remove();
        build(s, s.shared, null, clock.getAsLong());
        // Wer den Ring gerade geschafft hat oder ihn selbst als eigene Anzeige sieht, sieht die gemeinsame nicht
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hides(p.getUniqueId(), s)) {
                s.shared.all().forEach(d -> p.hideEntity(plugin, d));
            }
        }
    }

    private boolean hides(UUID id, Spec s) {
        Set<String> mine = passed.get(id);
        if (mine != null && mine.contains(s.key)) {
            return true;
        }
        Own o = own.get(id);
        return o != null && o.course.equals(s.course) && s.ring.dynamic();
    }

    /**
     * Anzeigen eines Rings erzeugen: Teile (bewegte Ringe am Träger), Flügel eines Tors. {@code viewer} = nur dieser
     * Spieler sieht sie (eigene Anzeige), null = alle.
     */
    private void build(Spec s, Shown out, Player viewer, double time) {
        Ring r = s.ring;
        Vec c = r.centerAt(time);
        // Alle Teile hängen an einem Punkt unter der Ring-Mitte; die Größe der Anzeige-Box sorgt dafür, dass der Ring
        // nicht ausgeblendet wird, solange irgendein Teil zu sehen ist.
        float ext = ext(r);
        Location at = new Location(world, c.x(), c.y() - ext, c.z());
        if (s.moving()) {
            out.carrier = world.spawn(at, BlockDisplay.class, b -> {
                prepare(b, viewer);
                b.setTeleportDuration(GLIDE_TICKS);
            });
        }
        for (Scenery.RingPart part : s.parts) {
            BlockDisplay d = part(at, part, ext, viewer);
            if (d == null) {
                continue;
            }
            out.parts.add(d);
            if (out.carrier != null) {
                out.carrier.addPassenger(d);
            }
        }
        if (r.gate() != null) {
            for (Scenery.RingPart part : Scenery.gateWings(r, r.gate().gap(time, r.hitRadius()))) {
                BlockDisplay d = part(at, part, ext, viewer);
                if (d != null) {
                    d.setInterpolationDuration(GLIDE_TICKS);
                    out.wings.add(d);
                }
            }
        }
        if (viewer != null) {
            out.all().forEach(e -> viewer.showEntity(plugin, e));
        }
    }

    private BlockDisplay part(Location at, Scenery.RingPart part, float ext, Player viewer) {
        BlockData bd;
        try {
            bd = data(part.block());
        } catch (IllegalArgumentException e) {
            return null;
        }
        Transformation tf = transform(part, ext);
        return world.spawn(at, BlockDisplay.class, b -> {
            prepare(b, viewer);
            b.setBlock(bd);
            b.setTransformation(tf);
            b.setBrightness(new Display.Brightness(15, 15));
            b.setDisplayWidth(ext * 2);
            b.setDisplayHeight(ext * 2);
        });
    }

    private static void prepare(BlockDisplay b, Player viewer) {
        b.setPersistent(false);
        b.addScoreboardTag(TAG);
        b.setViewRange(VIEW_RANGE);
        if (viewer != null) {
            b.setVisibleByDefault(false);
        }
    }

    private static float ext(Ring r) {
        double reach = r.motion() == null ? 0 : r.motion().reach();
        return (float) (r.radius() + 2.5 + (r.gate() != null ? r.radius() + 3 : 0) + reach);
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

    // =========================================================================================
    // Bewegung
    // =========================================================================================

    /** Gemeinsame Anzeigen bewegter Ringe und Tore laufen nach der Uhr des Servers. */
    private void animateShared() {
        long t = clock.getAsLong();
        for (Spec s : specs.values()) {
            if (s.ring.dynamic() && s.shared.alive()) {
                pose(s, s.shared, t);
            }
        }
    }

    /** Ring bzw. Flügel in die Stellung zur Flugzeit {@code time} bringen. */
    private void pose(Spec s, Shown shown, double time) {
        Ring r = s.ring;
        if (shown.carrier != null && r.motion() != null) {
            Vec c = r.centerAt(time);
            Location to = new Location(world, c.x(), c.y() - ext(r), c.z());
            shown.carrier.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
        if (r.gate() != null && !shown.wings.isEmpty()) {
            List<Scenery.RingPart> wings = Scenery.gateWings(r, r.gate().gap(time, r.hitRadius()));
            float ext = ext(r);
            for (int i = 0; i < wings.size() && i < shown.wings.size(); i++) {
                BlockDisplay d = shown.wings.get(i);
                if (!d.isDead()) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(GLIDE_TICKS);
                    d.setTransformation(transform(wings.get(i), ext));
                }
            }
        }
    }

    /** Ein Flieger startet: Seine bewegten Ringe und Tore laufen ab jetzt nach seiner Flugzeit (eigene Anzeige). */
    public void begin(Player p, Course c) {
        end(p);
        if (c.dynamicRings().isEmpty()) {
            return;
        }
        own.put(p.getUniqueId(), new Own(c.id));
        for (Spec s : specs.values()) {
            if (s.course.equals(c.id) && s.ring.dynamic()) {
                s.shared.all().forEach(d -> p.hideEntity(plugin, d));
            }
        }
    }

    /**
     * Jeden Tick während des Flugs: eigene Anzeigen nach der Flugzeit {@code time} stellen (und erzeugen, sobald der
     * Chunk des Rings geladen ist).
     */
    public void follow(Player p, Course c, double time) {
        Own o = own.get(p.getUniqueId());
        if (o == null || !o.course.equals(c.id)) {
            return;
        }
        Set<String> done = passed.getOrDefault(p.getUniqueId(), Set.of());
        for (Ring r : c.dynamicRings()) {
            String k = key(c, r);
            Spec s = specs.get(k);
            if (s == null || done.contains(k)) {
                continue;
            }
            Shown mine = o.copies.get(k);
            if (mine == null || !mine.alive()) {
                if (mine != null) {
                    mine.remove();
                }
                Long last = o.built.get(k);
                long now = clock.getAsLong();
                if (!loaded(r.center()) || (last != null && now - last < REBUILD_TICKS)) {
                    continue;
                }
                o.built.put(k, now);
                mine = new Shown();
                build(s, mine, p, time);
                o.copies.put(k, mine);
            }
            pose(s, mine, time);
        }
    }

    /** Flug vorbei: eigene Anzeigen weg, die gemeinsamen wieder zeigen. */
    public void end(Player p) {
        Own o = own.remove(p.getUniqueId());
        if (o == null) {
            return;
        }
        o.copies.values().forEach(Shown::remove);
        Set<String> mine = passed.getOrDefault(p.getUniqueId(), Set.of());
        for (Spec s : specs.values()) {
            if (s.course.equals(o.course) && s.ring.dynamic() && !mine.contains(s.key)) {
                s.shared.all().forEach(d -> {
                    if (!d.isDead()) {
                        p.showEntity(plugin, d);
                    }
                });
            }
        }
    }

    // =========================================================================================
    // Geschafft, Kranz, Zurücksetzen
    // =========================================================================================

    /** Ring für diesen Spieler geschafft: verschwindet mit einem Funkenregen (an der Stelle zur Flugzeit {@code time}). */
    public void passed(Player p, Course c, Ring ring, double time) {
        Spec s = skip(p, c, ring);
        if (s == null) {
            return;
        }
        Color colour = Color.fromRGB(Integer.parseInt(Scenery.ringColour(s.ring).substring(1), 16));
        Vec ctr = s.ring.centerAt(time);
        Vec u = s.ring.across();
        Vec v = s.ring.up();
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

    /** Ring für diesen Spieler ausblenden, ohne Funken (Übung ab einem späteren Ring). */
    public Spec skip(Player p, Course c, Ring ring) {
        Spec s = specs.get(key(c, ring));
        if (s == null) {
            return null;
        }
        passed.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(s.key);
        s.shared.all().forEach(d -> {
            if (!d.isDead()) {
                p.hideEntity(plugin, d);
            }
        });
        Own o = own.get(p.getUniqueId());
        if (o != null) {
            Shown mine = o.copies.remove(s.key);
            if (mine != null) {
                mine.remove();
            }
        }
        return s;
    }

    /**
     * Leuchtender Kranz um den nächsten Ring (nur für diesen Spieler, auch von weit weg zu sehen) – an der Stelle, an
     * der der Ring zur Flugzeit {@code time} ist. Ein Takt-Tor leuchtet grün, solange es offen ist, sonst rot.
     */
    public void highlight(Player p, Ring ring, long tick, double time) {
        Color colour;
        if (ring.gate() != null) {
            colour = ring.gate().open(time) ? Color.fromRGB(80, 255, 110) : Color.fromRGB(255, 70, 60);
        } else {
            colour = Color.fromRGB(Integer.parseInt(Scenery.ringColour(ring).substring(1), 16));
        }
        Vec ctr = ring.centerAt(time);
        Vec u = ring.across();
        Vec v = ring.up();
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

    /** Alle geschafften Ringe dieser Strecke wieder zeigen (Neustart). */
    public void reset(Player p, Course c) {
        Set<String> mine = passed.get(p.getUniqueId());
        if (mine == null || mine.isEmpty()) {
            return;
        }
        Own o = own.get(p.getUniqueId());
        for (String key : new HashSet<>(mine)) {
            Spec s = specs.get(key);
            mine.remove(key);
            if (s == null) {
                continue;
            }
            // Bewegte Ringe und Tore der eigenen Strecke kommen als eigene Anzeige wieder ({@link #follow})
            boolean ownCopy = o != null && o.course.equals(s.course) && s.ring.dynamic();
            if (!ownCopy) {
                s.shared.all().forEach(d -> {
                    if (!d.isDead()) {
                        p.showEntity(plugin, d);
                    }
                });
            }
        }
    }

    public void forget(UUID id) {
        passed.remove(id);
        Own o = own.remove(id);
        if (o != null) {
            o.copies.values().forEach(Shown::remove);
        }
    }
}
