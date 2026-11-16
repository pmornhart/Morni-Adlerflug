package de.moinmorni.adlerflug.data;

import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.stats.Leaderboard;
import de.moinmorni.adlerflug.stats.PlayerRecord;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Lokale Daten: ein YAML pro Spieler ({@code spieler/<uuid>.yml}), daraus die Bestenlisten aller Strecken und der
 * Woche. Gespeichert wird im Hintergrund, atomar (erst Hilfsdatei, dann umbenennen) – ein Absturz zerstört nichts.
 * Dazu die Wege der Bestzeiten als „Geist“ ({@code geister/<uuid>/<strecke>.bin}).
 */
public final class Store {

    private final Path dir;
    private final Logger log;
    private final Map<UUID, PlayerRecord> records = new HashMap<>();
    private final Map<String, Leaderboard> boards = new LinkedHashMap<>();
    private final Leaderboard weekly = new Leaderboard();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Adlerflug-Speichern");
        t.setDaemon(true);
        return t;
    });
    private String weekId = "";
    private String weekCourse = "";
    private boolean ghostDefault = true;
    /** Strecke des Tages: Kennung und der Tag, für den ihre Bestenliste gilt. */
    private final String daily;
    private String dayId = "";
    /** Fingerabdruck jeder festen Strecke: ändert sich eine Strecke, zählen ihre Bestzeiten neu. */
    private final Map<String, String> signatures = new HashMap<>();

    public Store(Path dir, Logger log, Collection<Course> courses, String daily) {
        this.dir = dir;
        this.log = log;
        this.daily = daily;
        for (Course c : courses) {
            boards.put(c.id, new Leaderboard());
            signatures.put(c.id, c.signature());
        }
        boards.put(daily, new Leaderboard());
    }

    /** Alle Spieler laden und Bestenlisten aufbauen. */
    public void load() throws IOException {
        int changed = 0;
        Path players = dir.resolve("spieler");
        Files.createDirectories(players);
        try (var files = Files.list(players)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".yml")).toList()) {
                String name = f.getFileName().toString();
                try {
                    UUID uuid = UUID.fromString(name.substring(0, name.length() - 4));
                    YamlConfiguration y = new YamlConfiguration();
                    y.loadFromString(Files.readString(f, StandardCharsets.UTF_8));
                    PlayerRecord r = PlayerRecord.fromMap(uuid, toMap(y));
                    records.put(uuid, r);
                    if (renew(r)) {
                        changed++;
                        save(r);
                    }
                } catch (IllegalArgumentException | InvalidConfigurationException e) {
                    log.warning("Spielerdatei " + name + " ist kaputt und wird übersprungen: " + e.getMessage());
                }
            }
        }
        if (changed > 0) {
            log.info("Strecken haben sich geändert – alte Bestzeiten von " + changed + " Spielern zählen dort nicht mehr.");
        }
        rebuildBoards();
    }

    /**
     * Bestzeiten auf Strecken, die sich seitdem geändert haben (anderer Fingerabdruck), zählen nicht mehr: Bestzeit,
     * Zwischenzeiten und Geist fallen weg, Zähler (Flüge, Ziele) bleiben. true = etwas geändert.
     */
    private boolean renew(PlayerRecord r) {
        boolean any = false;
        for (Map.Entry<String, PlayerRecord.CourseStats> e : r.courses.entrySet()) {
            String sig = signatures.get(e.getKey());
            PlayerRecord.CourseStats st = e.getValue();
            if (sig == null || sig.equals(st.sig)) {
                continue;
            }
            if (st.best > 0) {
                st.best = 0;
                st.bestWhen = 0;
                st.splits = new java.util.ArrayList<>();
                deleteGhosts(r.uuid, e.getKey());
                any = true;
            }
            st.sig = sig;
        }
        return any;
    }

    /** Fingerabdruck der Strecke (für neue Bestzeiten), leer für die Strecke des Tages. */
    public String signature(String course) {
        return signatures.getOrDefault(course, "");
    }

    private void rebuildBoards() {
        boards.values().forEach(Leaderboard::clear);
        for (PlayerRecord r : records.values()) {
            r.courses.forEach((id, s) -> {
                Leaderboard b = boards.get(id);
                boolean today = !id.equals(daily) || r.dayId.equals(dayId);
                if (b != null && s.best > 0 && today) {
                    b.submit(r.uuid, r.name, s.best, s.bestWhen);
                }
            });
        }
        rebuildWeekly();
    }

    /** Tag der Strecke des Tages (neu) setzen: ihre Bestenliste gilt nur für diesen Tag. */
    public void setDay(String id) {
        if (id.equals(dayId)) {
            return;
        }
        dayId = id;
        Leaderboard b = boards.get(daily);
        b.clear();
        for (PlayerRecord r : records.values()) {
            PlayerRecord.CourseStats st = r.courses.get(daily);
            if (r.dayId.equals(id) && st != null && st.best > 0) {
                b.submit(r.uuid, r.name, st.best, st.bestWhen);
            }
        }
    }

    public String dayId() {
        return dayId;
    }

    /** Woche (neu) setzen: Wochenbestenliste nur aus Zeiten dieser Woche auf der Strecke der Woche. */
    public void setWeek(String id, String course) {
        if (id.equals(weekId) && course.equals(weekCourse)) {
            return;
        }
        weekId = id;
        weekCourse = course;
        rebuildWeekly();
    }

    private void rebuildWeekly() {
        weekly.clear();
        for (PlayerRecord r : records.values()) {
            if (r.weekId.equals(weekId) && r.weekCourse.equals(weekCourse) && r.weekBest > 0) {
                weekly.submit(r.uuid, r.name, r.weekBest, r.weekBestWhen);
            }
        }
    }

    public String weekId() {
        return weekId;
    }

    public String weekCourse() {
        return weekCourse;
    }

    /** Geist für neue Spieler an oder aus (config.yml). */
    public void ghostDefault(boolean on) {
        ghostDefault = on;
    }

    public PlayerRecord record(UUID uuid, String name) {
        PlayerRecord r = records.computeIfAbsent(uuid, u -> {
            PlayerRecord n = new PlayerRecord(u, name);
            n.ghost = ghostDefault;
            return n;
        });
        if (name != null && !name.equals(r.name)) {
            r.name = name;
        }
        r.enterWeek(weekId, weekCourse);
        if (!dayId.isEmpty()) {
            r.enterDay(dayId, daily);
        }
        return r;
    }

    /** Ohne Anlegen (für /adlerflug stats anderer Spieler). */
    public PlayerRecord find(UUID uuid) {
        return records.get(uuid);
    }

    public PlayerRecord findByName(String name) {
        for (PlayerRecord r : records.values()) {
            if (r.name.equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }

    public Leaderboard board(String course) {
        return boards.get(course);
    }

    public Leaderboard weekly() {
        return weekly;
    }

    /** Anzahl Wochen-Adler dieser Woche. */
    public int weeklyDone() {
        int n = 0;
        for (PlayerRecord r : records.values()) {
            if (r.weeklyDone(weekId)) {
                n++;
            }
        }
        return n;
    }

    /** Neue Zeit in Liste übernehmen (nach einer neuen Bestzeit). */
    public void submit(PlayerRecord r, String course, long ms, long when) {
        r.course(course).sig = signature(course);
        Leaderboard b = boards.get(course);
        if (b != null) {
            b.submit(r.uuid, r.name, ms, when);
        }
        if (course.equals(weekCourse) && r.weekBest > 0) {
            weekly.submit(r.uuid, r.name, r.weekBest, r.weekBestWhen);
        }
    }

    /** Zeiten eines Spielers löschen (alle oder eine Strecke). */
    public void reset(PlayerRecord r, String course) {
        if (course == null) {
            r.courses.clear();
            r.days = 0;
            r.weekBest = 0;
            r.weekDone = false;
            r.flights = 0;
            r.resets = 0;
            r.finishes = 0;
            r.rings = 0;
        } else {
            r.courses.remove(course);
            if (course.equals(r.weekCourse)) {
                r.weekBest = 0;
                r.weekDone = false;
            }
        }
        rebuildBoards();
        save(r);
    }

    /** Im Hintergrund speichern (Inhalt wird jetzt erstellt, geschrieben wird danach). */
    public void save(PlayerRecord r) {
        YamlConfiguration y = new YamlConfiguration();
        r.toMap().forEach(y::set);
        String text = y.saveToString();
        Path file = dir.resolve("spieler").resolve(r.uuid + ".yml");
        io.execute(() -> writeAtomic(file, text.getBytes(StandardCharsets.UTF_8)));
    }

    // ---------- Geister ----------

    /** Weg der Bestzeit speichern (x/y/z pro Tick). */
    public void saveGhost(UUID uuid, String course, float[] path) {
        Path file = dir.resolve("geister").resolve(uuid.toString()).resolve(course + ".bin");
        float[] copy = path.clone();
        io.execute(() -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
                    out.writeInt(0x41444731); // „ADG1“
                    out.writeInt(copy.length);
                    for (float f : copy) {
                        out.writeFloat(f);
                    }
                }
                writeAtomic(file, bytes.toByteArray());
            } catch (IOException e) {
                log.log(Level.WARNING, "Geist ließ sich nicht speichern", e);
            }
        });
    }

    /** Weg der Bestzeit laden (null, wenn es keinen gibt). Klein genug für den Hauptthread (wenige KB). */
    public float[] loadGhost(UUID uuid, String course) {
        Path file = dir.resolve("geister").resolve(uuid.toString()).resolve(course + ".bin");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            if (in.readInt() != 0x41444731) {
                return null;
            }
            int n = in.readInt();
            if (n < 0 || n > 3 * 20 * 60 * 30) {
                return null;
            }
            float[] path = new float[n];
            for (int i = 0; i < n; i++) {
                path[i] = in.readFloat();
            }
            return path;
        } catch (IOException e) {
            return null;
        }
    }

    public void deleteGhosts(UUID uuid, String course) {
        Path folder = dir.resolve("geister").resolve(uuid.toString());
        io.execute(() -> {
            try {
                if (course != null) {
                    Files.deleteIfExists(folder.resolve(course + ".bin"));
                } else if (Files.isDirectory(folder)) {
                    try (var files = Files.list(folder)) {
                        for (Path f : files.toList()) {
                            Files.deleteIfExists(f);
                        }
                    }
                }
            } catch (IOException e) {
                log.log(Level.WARNING, "Geister ließen sich nicht löschen", e);
            }
        });
    }

    /** Ausstehendes speichern (beim Abschalten). */
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warning("Speichern dauert zu lange – breche ab.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeAtomic(Path file, byte[] data) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Konnte " + file.getFileName() + " nicht speichern", e);
        }
    }

    /** YAML-Abschnitte rekursiv in Maps (für {@link PlayerRecord#fromMap}). */
    private static Map<String, Object> toMap(ConfigurationSection s) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : s.getKeys(false)) {
            Object v = s.get(k);
            m.put(k, v instanceof ConfigurationSection cs ? toMap(cs) : v);
        }
        return m;
    }

    public static long now() {
        return Instant.now().toEpochMilli();
    }

    public Collection<PlayerRecord> all() {
        return records.values();
    }

    public List<String> names() {
        return records.values().stream().map(r -> r.name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
