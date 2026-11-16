package de.moinmorni.adlerflug;

import de.moinmorni.adlerflug.cmd.AdminCommand;
import de.moinmorni.adlerflug.cmd.FlightCommand;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Medal;
import de.moinmorni.adlerflug.course.Pilots;
import de.moinmorni.adlerflug.courses.Basecamp;
import de.moinmorni.adlerflug.courses.Daily;
import de.moinmorni.adlerflug.courses.World;
import de.moinmorni.adlerflug.data.Store;
import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.flight.Wind;
import de.moinmorni.adlerflug.game.Boards;
import de.moinmorni.adlerflug.game.Controls;
import de.moinmorni.adlerflug.game.Flights;
import de.moinmorni.adlerflug.game.Guard;
import de.moinmorni.adlerflug.game.Hotbar;
import de.moinmorni.adlerflug.game.Menus;
import de.moinmorni.adlerflug.game.RingTint;
import de.moinmorni.adlerflug.net.Network;
import de.moinmorni.adlerflug.stats.PlayerRecord;
import de.moinmorni.adlerflug.stats.WeeklyRotation;
import de.moinmorni.adlerflug.ui.Items;
import de.moinmorni.adlerflug.ui.Menu;
import de.moinmorni.adlerflug.ui.Texts;
import de.moinmorni.adlerflug.world.Construction;
import de.moinmorni.adlerflug.world.Critters;
import de.moinmorni.adlerflug.world.Holograms;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.WeatherType;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

/**
 * Adlerflug – Flug-Modus des MoinMorni-Netzwerks. Baut beim Start seine eigene Welt (Basislager, acht Strecken in den
 * Bergen und das Tal der Strecke des Tages), misst Flugzeiten, führt Bestenlisten und meldet Statistik und Zustand ans
 * Netzwerk. Die Strecke des Tages entsteht jeden Tag neu aus dem Datum (für alle gleich). Läuft auch allein.
 */
public final class AdlerflugPlugin extends JavaPlugin implements Listener {

    /** Einstellungen aus der config.yml. */
    public static final class Settings {
        public String world = "adlerflug";
        public double launch = 1.3;
        public boolean autoGlide = true;
        public boolean plausible = true;
        public int raceWait = 15;
        public boolean ghostDefault = true;
        public boolean freeFlight = true;
    }

    private static final String NO_COLLISION = "adler_nocoll";
    private static final Title.Times WELCOME = Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3500),
            Duration.ofMillis(900));
    private static final Particle.DustOptions LIFT = new Particle.DustOptions(Color.fromRGB(255, 250, 235), 1.4f);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final Settings settings = new Settings();
    private World model;
    private Construction worlds;
    private Network net;
    private Store store;
    private Holograms holos;
    private Flights flights;
    private Hotbar hotbar;
    private Menus menus;
    private Controls controls;
    private RingTint tint;
    private final Map<UUID, String> langSeen = new HashMap<>();
    private final Map<UUID, Component> tabBase = new HashMap<>();
    private final Map<UUID, Integer> freeLanded = new HashMap<>();
    private Course weekly;
    private String lastState = "";
    private long lastStateAt;
    private Boards boards;
    /** Strecke des Tages (null, solange sie noch gerechnet wird). */
    private volatile Daily.Today daily;
    private String dailyBuilding = "";
    /** Nur für Tests ({@code /adleradmin tag}): anderer Tag für die Strecke des Tages. */
    private LocalDate forcedDay;
    /** Stellen in den Windzonen (auf dem Musterflug), an denen Schlieren ziehen: x, y, z, Wind x, Wind z. */
    private final List<double[]> windSpots = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings.world = getConfig().getString("welt", "adlerflug");
        settings.launch = Math.max(0, Math.min(3, getConfig().getDouble("start-schwung", 1.3)));
        settings.autoGlide = getConfig().getBoolean("fluegel-automatisch", true);
        settings.plausible = getConfig().getBoolean("plausibel-pruefen", true);
        settings.raceWait = Math.max(5, getConfig().getInt("rennen-wartezeit", 15));
        settings.ghostDefault = getConfig().getBoolean("geist-standard", true);
        settings.freeFlight = getConfig().getBoolean("freiflug", true);
        Items.init(this);

        long t = System.currentTimeMillis();
        model = World.create();
        getLogger().info("Modell gebaut: " + model.courses().size() + " Strecken in " + (System.currentTimeMillis() - t)
                + " ms.");
        for (Course c : model.courses()) {
            FlightSim.Result r = FlightSim.fly(c.start.line(), c.start.launch(), c.air(), Pilots.aim(c), model::solid,
                    8000);
            if (!r.finished()) {
                getLogger().warning("Strecke " + c.id + " ist für den Prüf-Piloten nicht fliegbar: " + r.problem());
            }
            windSpots(c);
        }
        worlds = new Construction(this, model, settings.world);
        try {
            worlds.setup();
        } catch (IOException | RuntimeException e) {
            getLogger().severe("Die Adlerflug-Welt ließ sich nicht bauen: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        net = Network.connect(this);
        store = new Store(getDataFolder().toPath(), getLogger(), model.courses(), Daily.ID);
        store.ghostDefault(settings.ghostDefault);
        try {
            store.load();
        } catch (IOException e) {
            getLogger().severe("Spielerdaten ließen sich nicht laden: " + e);
        }
        rollWeek();

        tint = new RingTint(this, model.courses(), () -> flights == null ? 0 : flights.tick());
        hotbar = new Hotbar(this);
        menus = new Menus(this);
        flights = new Flights(this);
        controls = new Controls(this);
        holos = new Holograms(this, worlds.world(), this::langOf);
        boards = new Boards(this);
        boards.register(holos);
        keepBasecampLoaded();
        holos.start();
        tint.start(worlds.world());
        new Critters(this, worlds.world(), model.critters()).start();
        flights.start();
        worlds.warmUp(60);
        rollDay();

        var pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(controls, this);
        pm.registerEvents(new Guard(this), this);
        pm.registerEvents(new Menu.Clicks(), this);
        PluginCommand cmd = getCommand("adlerflug");
        if (cmd != null) {
            FlightCommand fc = new FlightCommand(this);
            cmd.setExecutor(fc);
            cmd.setTabCompleter(fc);
        }
        PluginCommand admin = getCommand("adleradmin");
        if (admin != null) {
            AdminCommand ac = new AdminCommand(this);
            admin.setExecutor(ac);
            admin.setTabCompleter(ac);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::everySecond, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::everyFiveSeconds, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, this::ambient, 7L, 4L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            welcome(p, false);
        }
        state();
        getLogger().info("Adlerflug ist bereit: " + model.courses().size() + " Strecken, Strecke der Woche: "
                + (weekly == null ? "-" : weekly.nameDe) + ".");
    }

    @Override
    public void onDisable() {
        if (store != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (flights != null) {
                    flights.abandon(p);
                }
            }
            store.close();
        }
    }

    // =========================================================================================
    // Für die anderen Teile
    // =========================================================================================

    public Settings settings() {
        return settings;
    }

    public World model() {
        return model;
    }

    public Construction worlds() {
        return worlds;
    }

    public org.bukkit.World world() {
        return worlds.world();
    }

    public Network net() {
        return net;
    }

    public Store store() {
        return store;
    }

    public Flights flights() {
        return flights;
    }

    public Hotbar hotbar() {
        return hotbar;
    }

    public Menus menus() {
        return menus;
    }

    public RingTint tint() {
        return tint;
    }

    /** Strecke des Tages (null, solange sie noch gerechnet wird). */
    public Daily.Today daily() {
        return daily;
    }

    /** Alle Strecken, die man gerade fliegen kann: die acht festen und (wenn fertig) die Strecke des Tages. */
    public List<Course> courses() {
        Daily.Today d = daily;
        if (d == null) {
            return model.courses();
        }
        List<Course> out = new ArrayList<>(model.courses());
        out.add(d.course());
        return out;
    }

    /** Strecke nach Kennung (auch „tagesflug“ für die Strecke des Tages). */
    public Optional<Course> course(String id) {
        Daily.Today d = daily;
        if (d != null && Daily.ID.equals(id)) {
            return Optional.of(d.course());
        }
        return model.course(id);
    }

    public String lang(Player p) {
        return net == null ? "de" : net.language(p);
    }

    private String langOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p == null ? "de" : lang(p);
    }

    /** Stehplatz auf der Schanze (im Rennen nebeneinander). */
    public Location startLocation(Course c, int lane) {
        Vec s = c.start.stand();
        Vec dir = Vec.look(c.start.yaw(), 0);
        Vec side = new Vec(dir.z(), 0, -dir.x());
        double off = (lane % 2 == 0 ? 1 : -1) * ((lane + 1) / 2) * 1.5;
        return new Location(world(), s.x() + side.x() * off, s.y(), s.z() + side.z() * off, c.start.yaw(), 0);
    }

    public Location spawn() {
        Basecamp b = model.basecamp();
        return new Location(world(), b.spawnX(), b.spawnY(), b.spawnZ(), b.spawnYaw(), 0);
    }

    public void toBasecamp(Player p) {
        p.setGliding(false);
        p.setVelocity(new Vector());
        p.setFallDistance(0);
        p.teleport(spawn());
        dayLook(p, null);
        hotbar.base(p);
    }

    // =========================================================================================
    // Strecke des Tages
    // =========================================================================================

    /** Heutiger Tag (deutsche Zeit) – oder der für Tests gesetzte. */
    public LocalDate today() {
        return forcedDay != null ? forcedDay : LocalDate.ofInstant(now(), BERLIN);
    }

    /** Nur für Tests: Strecke des Tages für einen anderen Tag (null = wieder heute). */
    public void forceDay(LocalDate day) {
        forcedDay = day;
        rollDay();
    }

    /** Neuer Tag? Dann die Strecke des Tages im Hintergrund neu rechnen (dauert etwa eine Sekunde). */
    private void rollDay() {
        LocalDate day = today();
        String id = day.toString();
        Daily.Today d = daily;
        if ((d != null && id.equals(d.course().day)) || id.equals(dailyBuilding)) {
            return;
        }
        dailyBuilding = id;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            long t = System.currentTimeMillis();
            try {
                Daily.Today next = model.daily(Daily.conditions(day));
                long took = System.currentTimeMillis() - t;
                Bukkit.getScheduler().runTask(this, () -> installDaily(next, took));
            } catch (RuntimeException e) {
                getLogger().severe("Strecke des Tages " + id + " ließ sich nicht bauen: " + e);
            }
        });
    }

    private void installDaily(Daily.Today next, long took) {
        if (!next.course().day.equals(dailyBuilding)) {
            return; // inzwischen ein anderer Tag bestellt
        }
        Daily.Today old = daily;
        if (old != null) {
            // Wer noch die Strecke von gestern fliegt, landet im Adlerhorst (ihre Ringe gibt es nicht mehr)
            for (Player p : Bukkit.getOnlinePlayers()) {
                Course c = flights.courseOf(p.getUniqueId());
                if (c != null && c.daily()) {
                    flights.abandon(p);
                    toBasecamp(p);
                    p.sendMessage(Texts.chat(lang(p), "daily.new"));
                }
            }
            tint.remove(Daily.ID);
            boards.unregisterCourse(holos, Daily.ID);
        }
        daily = next;
        store.setDay(next.conditions().id());
        tint.add(next.course());
        boards.registerCourse(holos, next.course());
        holos.spawnLoaded();
        holos.refresh("board:" + Daily.ID);
        holos.refresh("weekly");
        Daily.Conditions k = next.conditions();
        getLogger().info("Strecke des Tages bereit (" + k.id() + ", " + k.layout().de + ", " + k.windText("de") + ", "
                + k.time().de + ", " + k.weather().de + ", " + next.course().ringCount() + " Ringe, Bonus "
                + next.course().bonus.size() + ") in " + took + " ms.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!flights.flying(p.getUniqueId())) {
                hotbar.base(p);
            }
        }
    }

    /** Wer die Strecke des Tages fliegt, sieht ihre Tageszeit und ihr Wetter; sonst gilt wieder das der Welt. */
    public void dayLook(Player p, Course c) {
        Daily.Today d = daily;
        if (c != null && c.daily() && d != null) {
            p.setPlayerTime(d.conditions().time().ticks, false);
            p.setPlayerWeather(d.conditions().weather() == Daily.Weather.REGEN ? WeatherType.DOWNFALL : WeatherType.CLEAR);
        } else {
            p.resetPlayerTime();
            p.resetPlayerWeather();
        }
    }

    /** Die Bedingungen des Tages als Text („16.11.2026 · gespiegelt · frischer Wind aus Westen ← · Abendrot …“). */
    public Component dailyText(String l) {
        Daily.Today d = daily;
        if (d == null) {
            return Texts.get(l, "daily.building");
        }
        Daily.Conditions k = d.conditions();
        Course c = d.course();
        List<String> parts = new ArrayList<>();
        parts.add("<white>" + k.date(l) + "</white>");
        parts.add(Texts.raw(l, "daily.layout." + k.layout().name().toLowerCase(java.util.Locale.ROOT)));
        parts.add("<aqua>" + k.windText(l) + (k.windLevel() > 0 ? " " + k.windArrow() : "") + "</aqua>");
        parts.add("<gold>" + k.time().name(l) + "</gold>");
        parts.add(k.weather() == Daily.Weather.REGEN ? "<blue>" + k.weather().name(l) + "</blue>"
                : k.weather().name(l));
        if (k.radius() < 4.0) {
            parts.add(Texts.raw(l, "daily.small"));
        }
        parts.add(Flights.extrasText(c, l));
        return Texts.MM.deserialize("<gray>" + String.join("<dark_gray> · </dark_gray>", parts) + "</gray>");
    }

    /** Stellen für Schlieren: Punkte des Musterflugs in Windzonen. */
    private void windSpots(Course c) {
        for (Wind w : c.winds) {
            for (int t = 0; t < c.pathTicks(); t += 6) {
                Vec p = c.pathAt(t);
                if (w.contains(Course.center(p))) {
                    windSpots.add(new double[] {p.x(), p.y(), p.z(), w.push().x(), w.push().z(), w.radius()});
                }
            }
        }
    }

    /** Ins Nichts gefallen (ohne laufenden Flug): zurück ins Basislager. */
    public void rescue(Player p) {
        if (!flights.flying(p.getUniqueId())) {
            toBasecamp(p);
        }
    }

    public Course weeklyCourse() {
        return weekly;
    }

    /** Restzeit der Woche als Text („3 Tagen 4 Std.“). */
    public String weekLeft(String l) {
        Instant now = now();
        Duration left = Duration.between(now, WeeklyRotation.nextChange(now));
        long days = left.toDays();
        long hours = left.toHoursPart();
        if (days > 0) {
            return Texts.raw(l, "time.days").replace("<d>", String.valueOf(days)).replace("<h>", String.valueOf(hours));
        }
        return Texts.raw(l, "time.hours").replace("<h>", String.valueOf(hours))
                .replace("<m>", String.valueOf(left.toMinutesPart()));
    }

    public void toggleHide(Player p) {
        PlayerRecord r = store.record(p.getUniqueId(), p.getName());
        r.hideOthers = !r.hideOthers;
        store.save(r);
        visibility(p);
        hotbar.refreshHide(p);
        p.sendMessage(Texts.chat(lang(p), r.hideOthers ? "hide.hidden" : "hide.shown"));
    }

    public void toggleGhost(Player p) {
        PlayerRecord r = store.record(p.getUniqueId(), p.getName());
        r.ghost = !r.ghost;
        store.save(r);
        p.sendMessage(Texts.chat(lang(p), r.ghost ? "ghost.on" : "ghost.off"));
    }

    /** Medaillen als „▲ 1 · ● 2 · ● 0 · ● 3“. */
    public Component medalCount(PlayerRecord r, String l) {
        int[] n = new int[Medal.values().length];
        for (Course c : model.courses()) {
            Medal m = c.medal(r.best(c.id));
            if (m != null) {
                n[m.ordinal()]++;
            }
        }
        Component out = Component.empty();
        for (int i = n.length - 1; i >= 0; i--) {
            Medal m = Medal.values()[i];
            out = out.append(Component.text(m.symbol + " " + m.name(l) + " " + n[i], TextColor.fromHexString(m.colour)));
            if (i > 0) {
                out = out.append(Component.text(" · ", NamedTextColor.DARK_GRAY));
            }
        }
        return out;
    }

    public void boards(String courseId) {
        holos.refresh("board:" + courseId);
        holos.refresh("landing:" + courseId);
        holos.refresh("weekly");
    }

    public void boardsAll() {
        holos.refreshAll();
    }

    /** Zustand ans Netzwerk (Lobby-Figur, Webseite): „open“ und Spielerzahl – nur bei Änderung, sonst jede Minute. */
    public void state() {
        Bukkit.getScheduler().runTask(this, () -> {
            int players = Bukkit.getOnlinePlayers().size();
            String now = "open:" + players;
            long t = System.currentTimeMillis();
            if (!now.equals(lastState) || t - lastStateAt > 60_000) {
                lastState = now;
                lastStateAt = t;
                net.state("open", players);
            }
        });
    }

    // =========================================================================================
    // Woche
    // =========================================================================================

    /** Uhr für die Strecke der Woche; nur für Tests mit {@code -Dadlerflug.uhr-versatz=<ms>} verstellbar. */
    public static Instant now() {
        return Instant.now().plusMillis(CLOCK_OFFSET);
    }

    private static final long CLOCK_OFFSET = Long.getLong("adlerflug.uhr-versatz", 0L);

    private void rollWeek() {
        Instant now = now();
        List<Course> cs = model.courses();
        Course w = cs.get(WeeklyRotation.index(now, cs.size()));
        String id = WeeklyRotation.weekId(now);
        boolean changed = weekly != null && (!id.equals(store.weekId()) || w != weekly);
        weekly = w;
        store.setWeek(id, w.id);
        if (changed) {
            getLogger().info("Neue Woche " + id + ": Strecke der Woche ist " + w.nameDe + ".");
            holos.refreshAll();
            for (Player p : Bukkit.getOnlinePlayers()) {
                store.record(p.getUniqueId(), p.getName());
                if (!flights.flying(p.getUniqueId())) {
                    hotbar.base(p);
                }
            }
        }
    }

    // =========================================================================================
    // Beitreten und Verlassen
    // =========================================================================================

    @EventHandler
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        if (worlds != null && worlds.ready()) {
            event.setSpawnLocation(spawn());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        welcome(event.getPlayer(), true);
    }

    private void welcome(Player p, boolean greet) {
        store.record(p.getUniqueId(), p.getName());
        if (!p.getWorld().equals(world())) {
            p.teleport(spawn());
        }
        hotbar.base(p);
        p.setCollidable(false);
        noCollision(p);
        visibility(p);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p)) {
                visibility(o);
            }
        }
        holos.language(p);
        langSeen.put(p.getUniqueId(), lang(p));
        state();
        if (!greet) {
            return;
        }
        // Kurz warten: Die Sprache kommt vom Netzwerk (bzw. aus dem Spiel) erst kurz nach dem Betreten.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) {
                return;
            }
            String l = lang(p);
            p.showTitle(Title.title(Texts.get(l, "welcome.title"), Texts.get(l, "welcome.subtitle"), WELCOME));
            p.sendMessage(Texts.chat(l, "welcome.chat"));
            p.sendMessage(Texts.chat(l, "welcome.how"));
            if (weekly != null) {
                p.sendMessage(Texts.chat(l, "welcome.weekly", Texts.comp("course", Flights.courseName(weekly, l)),
                        Texts.comp("badge", Texts.get(l, "badge"))));
            }
            if (daily != null) {
                p.sendMessage(Texts.chat(l, "welcome.daily", Texts.comp("conditions", dailyText(l))));
            }
        }, 30L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        flights.quit(p);
        controls.forget(p.getUniqueId());
        holos.forget(p.getUniqueId());
        tint.forget(p.getUniqueId());
        langSeen.remove(p.getUniqueId());
        tabBase.remove(p.getUniqueId());
        freeLanded.remove(p.getUniqueId());
        flights.forgetGhosts(p.getUniqueId());
        store.save(store.record(p.getUniqueId(), p.getName()));
        state();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(spawn());
        Bukkit.getScheduler().runTask(this, () -> hotbar.base(event.getPlayer()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (event.getFrom().equals(world())) {
            flights.abandon(event.getPlayer());
            event.getPlayer().getInventory().clear();
        }
    }

    /** Ohne Netzwerk kommt die Sprache aus dem Spiel: Wechsel dort gilt sofort. */
    @EventHandler
    public void onLocale(PlayerLocaleChangeEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> langSeen.remove(event.getPlayer().getUniqueId()), 2L);
    }

    /** Abzeichen „Wochen-Adler“ im Chat – gilt, solange der Chat auf diesem Server bleibt (ohne Netzwerk). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (net.connected()) {
            return;
        }
        Player p = event.getPlayer();
        PlayerRecord r = store.find(p.getUniqueId());
        if (r == null || !r.weeklyDone(store.weekId())) {
            return;
        }
        event.renderer((source, name, message, viewer) -> {
            String l = viewer instanceof Player v ? lang(v) : "de";
            return Texts.get(l, "badge").append(Component.text(" ")).append(name)
                    .append(Component.text(" » ", NamedTextColor.DARK_GRAY)).append(message);
        });
    }

    // =========================================================================================
    // Wiederkehrend
    // =========================================================================================

    private void everySecond() {
        if (!WeeklyRotation.weekId(now()).equals(store.weekId())) {
            rollWeek();
        }
        rollDay();
        Basecamp camp = model.basecamp();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String l = lang(p);
            if (!l.equals(langSeen.get(p.getUniqueId()))) {
                // Sprache kam vom Netzwerk oder wurde geändert: Tafeln und Leiste neu.
                langSeen.put(p.getUniqueId(), l);
                holos.language(p);
                if (flights.flying(p.getUniqueId())) {
                    hotbar.flight(p, flights.practice(p.getUniqueId()));
                } else {
                    hotbar.base(p);
                }
            }
            if (flights.flying(p.getUniqueId()) || !p.getWorld().equals(world())) {
                freeLanded.remove(p.getUniqueId());
                continue;
            }
            Location at = p.getLocation();
            // Freiflug: Wer außerhalb des Basislagers landet oder ins Tal stürzt, kommt zurück.
            boolean outside = !camp.inside(at.getX(), at.getY(), at.getZ());
            boolean down = (p.isOnGround() || p.isInWater()) && !p.isGliding();
            if (at.getY() < -40 || (outside && down && !nearLanding(at))) {
                int n = freeLanded.merge(p.getUniqueId(), 1, Integer::sum);
                if (n >= 2 || at.getY() < -40) {
                    freeLanded.remove(p.getUniqueId());
                    toBasecamp(p);
                    p.sendMessage(Texts.chat(l, "free.back"));
                }
            } else {
                freeLanded.remove(p.getUniqueId());
            }
        }
    }

    /** Auf einem Landeplatz einer Strecke (nach dem Ziel) darf man stehen bleiben. */
    private boolean nearLanding(Location at) {
        for (Course c : courses()) {
            Vec l = c.landing.at();
            if (Math.abs(at.getX() - l.x()) < 12 && Math.abs(at.getZ() - l.z()) < 12 && Math.abs(at.getY() - l.y()) < 8) {
                return true;
            }
        }
        return false;
    }

    private void everyFiveSeconds() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            noCollision(p);
            badge(p);
        }
        state();
    }

    /**
     * Aufwind sichtbar machen: helle Wirbel steigen in jeder Säule auf (für alle in der Nähe). Beim Freiflug gehen die
     * Flügel von selbst auf, sobald man fällt.
     */
    private void ambient() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(world())) {
                continue;
            }
            if (settings.freeFlight && settings.autoGlide && !flights.flying(p.getUniqueId()) && !p.isGliding()
                    && !p.isOnGround() && !p.isInWater() && p.getFallDistance() > 2.5f) {
                p.setGliding(true);
            }
            Location at = p.getLocation();
            windStreaks(p, at, r);
            for (Updraft u : model.updrafts()) {
                double d = Math.hypot(at.getX() - u.x(), at.getZ() - u.z());
                if (d > 140) {
                    continue;
                }
                int ground = model.terrain().height(u.x(), u.z());
                for (int i = 0; i < 10; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double rad = Math.sqrt(r.nextDouble()) * u.radius();
                    double y = ground + 1 + r.nextDouble() * Math.max(4, u.top() - ground - 1);
                    p.spawnParticle(Particle.DUST, u.x() + Math.cos(a) * rad, y, u.z() + Math.sin(a) * rad, 1, 0, 0.6,
                            0, 0, LIFT);
                }
                for (int i = 0; i < 3; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double rad = r.nextDouble() * u.radius();
                    p.spawnParticle(Particle.CLOUD, u.x() + Math.cos(a) * rad, ground + 2 + r.nextDouble() * 6,
                            u.z() + Math.sin(a) * rad, 0, 0, 0.6, 0, 0.5);
                }
            }
        }
    }

    /**
     * Wind sichtbar machen: Schlieren ziehen in den Windzonen in Windrichtung (für alle in der Nähe); wer die Strecke
     * des Tages bei Wind fliegt, sieht sie rund um sich.
     */
    private void windStreaks(Player p, Location at, ThreadLocalRandom r) {
        for (double[] w : windSpots) {
            if (Math.abs(at.getX() - w[0]) > 120 || Math.abs(at.getZ() - w[2]) > 120 || r.nextInt(3) != 0) {
                continue;
            }
            double len = Math.max(1e-6, Math.hypot(w[3], w[4]));
            double dx = w[3] / len;
            double dz = w[4] / len;
            double ox = (r.nextDouble() * 2 - 1) * w[5] * 0.8;
            double oz = (r.nextDouble() * 2 - 1) * w[5] * 0.8;
            double oy = (r.nextDouble() * 2 - 1) * 5;
            p.spawnParticle(Particle.CLOUD, w[0] + ox, w[1] + oy, w[2] + oz, 0, dx, 0, dz, 0.45);
            if (r.nextInt(6) == 0) {
                p.spawnParticle(Particle.SMALL_GUST, w[0] + ox, w[1] + oy, w[2] + oz, 1, 0, 0, 0, 0);
            }
        }
        Course c = flights.courseOf(p.getUniqueId());
        Daily.Today d = daily;
        if (c != null && c.daily() && d != null && d.conditions().windLevel() > 0) {
            Vec wind = d.conditions().wind().normalize();
            for (int i = 0; i < d.conditions().windLevel() * 2; i++) {
                double ox = (r.nextDouble() * 2 - 1) * 10;
                double oz = (r.nextDouble() * 2 - 1) * 10;
                p.spawnParticle(Particle.CLOUD, at.getX() + ox - wind.x() * 6, at.getY() + (r.nextDouble() * 2 - 1) * 4,
                        at.getZ() + oz - wind.z() * 6, 0, wind.x(), 0, wind.z(), 0.5);
            }
        }
    }

    /** Spieler schieben sich nicht gegenseitig (auch nicht in Teams des Netzwerks). */
    private void noCollision(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getEntryTeam(p.getName());
        if (team == null) {
            team = board.getTeam(NO_COLLISION);
            if (team == null) {
                team = board.registerNewTeam(NO_COLLISION);
            }
            team.addEntry(p.getName());
        }
        if (team.getOption(Team.Option.COLLISION_RULE) != Team.OptionStatus.NEVER) {
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
    }

    /** ▲ hinter dem Namen in der Tab-Liste für alle mit Wochen-Adler. */
    private void badge(Player p) {
        PlayerRecord r = store.find(p.getUniqueId());
        boolean has = r != null && r.weeklyDone(store.weekId());
        Component current = p.playerListName();
        String plain = PlainTextComponentSerializer.plainText().serialize(current);
        if (has && !plain.endsWith(" ▲")) {
            tabBase.put(p.getUniqueId(), current);
            p.playerListName(current.append(Component.text(" ▲", TextColor.fromHexString("#f4a259"))));
        } else if (!has && plain.endsWith(" ▲")) {
            Component base = tabBase.remove(p.getUniqueId());
            p.playerListName(base);
        }
    }

    /** Sichtbarkeit: Wer „Mitspieler ausblenden“ an hat, sieht niemanden. */
    private void visibility(Player viewer) {
        PlayerRecord r = store.record(viewer.getUniqueId(), viewer.getName());
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(viewer)) {
                continue;
            }
            if (r.hideOthers) {
                viewer.hidePlayer(this, o);
            } else {
                viewer.showPlayer(this, o);
            }
        }
    }

    /** Chunks des Basislagers bleiben geladen (Tafeln, schneller Einstieg). */
    private void keepBasecampLoaded() {
        org.bukkit.World w = world();
        for (int cx = -3; cx <= 2; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                w.setChunkForceLoaded(cx, cz, true);
            }
        }
    }
}
