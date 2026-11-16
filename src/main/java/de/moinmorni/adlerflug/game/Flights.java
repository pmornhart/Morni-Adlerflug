package de.moinmorni.adlerflug.game;

import de.moinmorni.adlerflug.AdlerflugPlugin;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Medal;
import de.moinmorni.adlerflug.courses.Scenery;
import de.moinmorni.adlerflug.data.Store;
import de.moinmorni.adlerflug.flight.Glide;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.RingCheck;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.stats.Leaderboard;
import de.moinmorni.adlerflug.stats.PlayerRecord;
import de.moinmorni.adlerflug.time.RunClock;
import de.moinmorni.adlerflug.time.Splits;
import de.moinmorni.adlerflug.time.TimeFormat;
import de.moinmorni.adlerflug.ui.Texts;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Herz von Adlerflug: die Flüge. Jeden Tick wird für jeden Flieger geprüft, ob er abspringt, durch den nächsten Ring
 * fliegt (aus der Strecke zwischen zwei Positionen – auch bei hohem Tempo), einen Ring verpasst, zu weit weg ist oder
 * gelandet ist. Aufwind-Säulen heben an, Schub-Ringe schieben. Zeiten in Ticks mit Bruchteilen ({@link RunClock}).
 */
public final class Flights {

    private static final Title.Times SHORT = Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600),
            Duration.ofMillis(400));
    private static final Title.Times LONG = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000),
            Duration.ofMillis(800));
    private static final Particle.DustOptions GHOST = new Particle.DustOptions(Color.fromRGB(127, 220, 255), 1.2f);
    private static final Particle.DustOptions GHOST_WING = new Particle.DustOptions(Color.fromRGB(230, 245, 255), 0.9f);
    /** So viele Ticks am Boden zählen als gelandet. */
    private static final int LANDED_TICKS = 8;
    private static final double HOP = 0.45;
    /** Nach dem Ziel so lange weitergleiten, dann zum Landeplatz. */
    private static final int AFTER_FINISH = 40;

    private final AdlerflugPlugin pl;
    private final Map<UUID, Flight> flights = new LinkedHashMap<>();
    private final Map<UUID, Race> invites = new HashMap<>();
    private final Set<Race> races = new HashSet<>();
    private final Map<String, float[]> ghosts = new HashMap<>();
    private final Set<UUID> practice = new HashSet<>();
    private final Map<UUID, Long> landingAt = new HashMap<>();
    private final Map<UUID, Course> landingCourse = new HashMap<>();
    private long tick;

    public Flights(AdlerflugPlugin pl) {
        this.pl = pl;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(pl, this::onTick, 1L, 1L);
    }

    public long tick() {
        return tick;
    }

    public boolean flying(UUID id) {
        return flights.containsKey(id);
    }

    public Course courseOf(UUID id) {
        Flight f = flights.get(id);
        return f == null ? null : f.course;
    }

    public boolean practice(UUID id) {
        return practice.contains(id);
    }

    public int count() {
        return flights.size();
    }

    /** Für {@code /adleradmin status}: Zustand eines Flugs als Text. */
    public String debug(UUID id) {
        Flight f = flights.get(id);
        if (f == null) {
            return "none";
        }
        return f.course.id + " state=" + f.state + " next=" + f.next + " resets=" + f.resets + " practice=" + f.practice
                + " race=" + (f.race != null) + " guide=" + f.guide;
    }

    // =========================================================================================
    // Wählen und Starten
    // =========================================================================================

    /** Strecke wählen: Party-Anführer mit Mitgliedern hier → Rennen, sonst allein. */
    public void select(Player p, Course c) {
        UUID id = p.getUniqueId();
        abandon(p);
        landingAt.remove(id);
        pl.net().party(id).completeOnTimeout(Optional.empty(), 2, TimeUnit.SECONDS)
                .whenComplete((party, err) -> Bukkit.getScheduler().runTask(pl, () -> {
                    if (!p.isOnline()) {
                        return;
                    }
                    List<Player> mates = new ArrayList<>();
                    if (err == null && party != null && party.isPresent() && party.get().leader().equals(id)) {
                        for (UUID m : party.get().members()) {
                            Player o = Bukkit.getPlayer(m);
                            if (!m.equals(id) && o != null && o.getWorld().equals(pl.world()) && !flights.containsKey(m)
                                    && !invites.containsKey(m)) {
                                mates.add(o);
                            }
                        }
                    }
                    if (mates.isEmpty()) {
                        begin(p, c, null, 0);
                        return;
                    }
                    Race race = new Race(c, id, p.getName());
                    race.until = tick + pl.settings().raceWait * 20L;
                    races.add(race);
                    begin(p, c, race, 0);
                    for (Player m : mates) {
                        race.invited.add(m.getUniqueId());
                        invites.put(m.getUniqueId(), race);
                        String l = pl.lang(m);
                        m.sendMessage(Texts.chat(l, "race.invite", Texts.arg("leader", p.getName()),
                                Texts.comp("course", courseName(c, l)), Texts.arg("sec", pl.settings().raceWait)));
                        m.playSound(m.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                        pl.hotbar().raceInvite(m, race.leaderName, c);
                    }
                }));
    }

    /** Auf die Schanze stellen (oder im Übungsmodus ab Ring {@code fromRing} in der Luft starten). */
    private void begin(Player p, Course c, Race race, int fromRing) {
        UUID id = p.getUniqueId();
        Flight f = new Flight(c, practice.contains(id));
        f.race = race;
        f.state = race != null ? Flight.State.FROZEN : Flight.State.READY;
        f.fromRing = fromRing;
        PlayerRecord rec = pl.store().record(id, p.getName());
        if (rec.ghost && !f.practice) {
            f.ghost = ghost(id, c.id);
        }
        flights.put(id, f);
        invites.remove(id);
        landingAt.remove(id);
        pl.hotbar().flight(p, f.practice);
        pl.tint().reset(p, c);
        String l = pl.lang(p);
        f.bar = BossBar.bossBar(courseName(c, l), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        p.showBossBar(f.bar);
        if (fromRing > 0) {
            airStart(p, f, fromRing);
            return;
        }
        teleport(p, f, pl.startLocation(c, race == null ? 0 : race.joined.size() - 1));
        p.showTitle(Title.title(courseName(c, l), Texts.get(l, "flight.sub", Texts.comp("diff", difficulty(c, l)),
                Texts.arg("n", c.ringCount())), LONG));
        p.sendMessage(Texts.chat(l, "flight.welcome", Texts.comp("course", courseName(c, l)),
                Texts.comp("diff", difficulty(c, l)), Texts.arg("n", c.ringCount())));
        if (race != null) {
            p.sendMessage(Texts.chat(l, "race.wait", Texts.arg("sec", pl.settings().raceWait)));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        pl.state();
    }

    /** Übungsmodus: in der Luft kurz vor Ring {@code ring} starten, mit dem Schwung des Musterflugs. */
    private void airStart(Player p, Flight f, int ring) {
        Course c = f.course;
        int t = c.tickBefore(ring, 25);
        Vec at = c.pathAt(t);
        Vec v = c.pathAt(t + 1).sub(at);
        f.next = ring;
        for (int i = 0; i < ring; i++) {
            pl.tint().passed(p, c, i);
        }
        Location loc = new Location(pl.world(), at.x(), at.y(), at.z(), v.yaw(), v.pitch());
        teleport(p, f, loc);
        f.state = Flight.State.FLYING;
        f.clock = new RunClock(tick, 0);
        f.launch = v;
        f.glideUntil = tick + 10;
        String l = pl.lang(p);
        p.sendMessage(Texts.chat(l, "practice.from", Texts.arg("n", ring + 1), Texts.comp("course", courseName(c, l))));
    }

    /** Übung ab einem bestimmten Ring (Menü im Übungsmodus). */
    public void practiceFrom(Player p, Course c, int ring) {
        abandon(p);
        practice.add(p.getUniqueId());
        begin(p, c, null, Math.max(0, Math.min(c.ringCount() - 1, ring)));
    }

    /** Einladung zum Rennen annehmen ({@code /adlerflug mitmachen} oder Gegenstand). */
    public void joinRace(Player p) {
        Race race = invites.get(p.getUniqueId());
        String l = pl.lang(p);
        if (race == null || race.state != Race.State.GATHER || !races.contains(race)) {
            p.sendMessage(Texts.chat(l, "race.none"));
            return;
        }
        if (flights.containsKey(p.getUniqueId())) {
            abandon(p);
        }
        race.joined.add(p.getUniqueId());
        begin(p, race.course, race, 0);
        for (UUID u : race.joined) {
            Player o = Bukkit.getPlayer(u);
            if (o != null) {
                o.sendMessage(Texts.chat(pl.lang(o), "race.joined", Texts.arg("player", p.getName())));
            }
        }
    }

    // =========================================================================================
    // Unterwegs: Gegenstände und Befehle
    // =========================================================================================

    /** Zurück zum letzten Ring (Gegenstand, Taste F oder Befehl). */
    public void backToRing(Player p) {
        Flight f = flights.get(p.getUniqueId());
        if (f == null) {
            p.sendMessage(Texts.chat(pl.lang(p), "cmd.notflying"));
            return;
        }
        if (f.state != Flight.State.FLYING) {
            teleport(p, f, pl.startLocation(f.course, 0));
            return;
        }
        reset(p, f, "flight.back.self");
    }

    /** Ganz von vorn (zurück auf die Schanze). */
    public void restart(Player p) {
        Flight f = flights.get(p.getUniqueId());
        if (f == null) {
            p.sendMessage(Texts.chat(pl.lang(p), "cmd.notflying"));
            return;
        }
        if (f.race != null && f.state == Flight.State.FLYING) {
            // Im Rennen läuft die Uhr weiter – nur zurück zum letzten Ring.
            reset(p, f, "flight.back.self");
            return;
        }
        Course c = f.course;
        reportEnd(p, f);
        f.resetProgress();
        f.guide = -1;
        if (f.state != Flight.State.FROZEN) {
            f.state = Flight.State.READY;
        }
        pl.tint().reset(p, c);
        p.setGliding(false);
        teleport(p, f, pl.startLocation(c, 0));
        p.sendActionBar(Texts.get(pl.lang(p), "flight.restart"));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.4f);
    }

    public void giveUp(Player p) {
        if (!flights.containsKey(p.getUniqueId())) {
            pl.toBasecamp(p);
            return;
        }
        abandon(p);
        pl.toBasecamp(p);
        p.sendMessage(Texts.chat(pl.lang(p), "flight.giveup"));
    }

    /** Flug beenden (Aufgeben, Verlassen, neue Strecke) – Zähler melden, speichern. */
    public void abandon(Player p) {
        UUID id = p.getUniqueId();
        Flight f = flights.remove(id);
        if (f == null) {
            return;
        }
        reportEnd(p, f);
        if (f.bar != null) {
            p.hideBossBar(f.bar);
        }
        pl.tint().reset(p, f.course);
        if (f.race != null) {
            f.race.out.add(id);
            checkRaceOver(f.race);
        }
        pl.store().save(pl.store().record(id, p.getName()));
        pl.state();
    }

    public void quit(Player p) {
        abandon(p);
        landingAt.remove(p.getUniqueId());
        landingCourse.remove(p.getUniqueId());
        Race inv = invites.remove(p.getUniqueId());
        if (inv != null) {
            inv.invited.remove(p.getUniqueId());
        }
    }

    /** Übungsmodus an/aus (setzt einen laufenden Flug zurück). */
    public void togglePractice(Player p) {
        UUID id = p.getUniqueId();
        boolean on = practice.add(id);
        if (!on) {
            practice.remove(id);
        }
        String l = pl.lang(p);
        p.sendMessage(Texts.chat(l, on ? "practice.on" : "practice.off"));
        Flight f = flights.get(id);
        if (f != null && f.race == null) {
            Course c = f.course;
            abandon(p);
            begin(p, c, null, 0);
        }
    }

    /**
     * Test-Autopilot (nur für Verwaltung und Tests): Der Server führt den Spieler Tick für Tick den Musterflug entlang
     * – bis Ring {@code until} (Index, sonst bis ins Ziel), an Ring {@code miss} absichtlich vorbei.
     */
    public boolean guide(Player p, int until, int miss) {
        Flight f = flights.get(p.getUniqueId());
        if (f == null) {
            return false;
        }
        if (f.state != Flight.State.FLYING) {
            Location line = toLocation(f.course.start.line(), f.course.start.yaw());
            teleport(p, f, line);
        }
        f.guide = f.state == Flight.State.FLYING ? f.course.tickBefore(f.next, 1) : 0;
        f.guideUntil = until < 0 ? Integer.MAX_VALUE : until;
        f.guideMiss = miss;
        return true;
    }

    // =========================================================================================
    // Jeder Tick
    // =========================================================================================

    private void onTick() {
        tick++;
        for (Iterator<Map.Entry<UUID, Flight>> it = flights.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Flight> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (!p.getWorld().equals(pl.world())) {
                it.remove();
                reportEnd(p, e.getValue());
                continue;
            }
            update(p, e.getValue());
        }
        for (Race race : new ArrayList<>(races)) {
            race(race);
        }
        afterFinish();
    }

    private void update(Player p, Flight f) {
        if (f.guide >= 0) {
            guideStep(p, f);
        }
        Location l = p.getLocation();
        Vec feet = new Vec(l.getX(), l.getY(), l.getZ());
        Vec c1 = Course.center(feet);
        Vec c0 = f.hasPrev ? f.prev : c1;
        Course c = f.course;
        switch (f.state) {
            case FROZEN -> {
                if (!c.start.area().contains(feet)) {
                    Location back = pl.startLocation(c, 0);
                    back.setYaw(l.getYaw());
                    back.setPitch(l.getPitch());
                    teleport(p, f, back);
                    return;
                }
            }
            case READY -> {
                if (!c.start.area().contains(feet)) {
                    double frac = c.start.area().exit(f.hasPrev ? c0.add(0, -0.3, 0) : feet, feet);
                    takeOff(p, f, frac < 0 ? 1 : frac);
                }
            }
            case FLYING -> {
                if (fly(p, f, c0, c1, feet)) {
                    return;
                }
            }
        }
        Vec moved = f.hasPrev ? c1.sub(f.prev) : Vec.ZERO;
        f.prev = c1;
        f.hasPrev = true;
        hud(p, f, c1, moved);
    }

    /** Ein Tick in der Luft; true = der Flug ist für diesen Tick erledigt (zurückgesetzt oder im Ziel). */
    private boolean fly(Player p, Flight f, Vec c0, Vec c1, Vec feet) {
        Course c = f.course;
        if (!f.practice) {
            f.record(feet);
        }
        // Flügel nach Absprung/Zurücksetzen sicher aufklappen, dann den Schwung geben
        if (tick <= f.glideUntil && f.guide < 0) {
            if (!p.isGliding()) {
                p.setGliding(true);
            }
            if (f.launch != null && p.isGliding()) {
                p.setVelocity(new Vector(f.launch.x(), f.launch.y(), f.launch.z()));
                f.launch = null;
            } else if (f.launch != null && !f.hopped && f.next == 0 && p.isOnGround()) {
                f.hopped = true;
                // Noch auf dem Brett (langsam über die Kante getreten): kleiner Sprung nach vorn, damit die
                // Flügel in der Luft aufgehen – sonst gilt man gleich als gelandet
                p.setVelocity(new Vector(f.launch.x() * 0.6, Math.max(f.launch.y(), HOP), f.launch.z() * 0.6));
                f.glideUntil = Math.max(f.glideUntil, tick + 10);
            }
        }
        Vec v = c1.sub(c0);
        if (f.next < c.ringCount()) {
            Ring ring = c.rings.get(f.next);
            RingCheck.Result r = RingCheck.check(ring, c0, c1);
            if (r.hit() == RingCheck.Hit.THROUGH) {
                ring(p, f, ring, r.fraction(), v);
                if (!flights.containsKey(p.getUniqueId())) {
                    return true; // im Ziel
                }
            } else if (r.hit() == RingCheck.Hit.MISSED) {
                reset(p, f, "flight.back.missed");
                return true;
            }
        }
        if (f.guide < 0) {
            Updraft u = c.updraftAt(c1);
            if (u != null) {
                Vec lifted = u.lift(v);
                p.setVelocity(new Vector(lifted.x(), lifted.y(), lifted.z()));
                if (tick % 3 == 0) {
                    p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.25f, 1.6f);
                }
            }
        }
        if (feet.y() < c.floorY) {
            reset(p, f, "flight.back.fell");
            return true;
        }
        if (f.next < c.ringCount() && c.offPath(feet, f.next) > c.maxOff) {
            reset(p, f, "flight.back.off");
            return true;
        }
        if (f.guide < 0 && tick > f.glideUntil) {
            boolean down = p.isInWater() || (!p.isGliding() && p.isOnGround());
            f.landed = down ? f.landed + 1 : 0;
            if (f.landed >= LANDED_TICKS) {
                reset(p, f, p.isInWater() ? "flight.back.water" : "flight.back.landed");
                return true;
            }
            if (!p.isGliding() && !p.isOnGround() && v.y() < -0.15 && pl.settings().autoGlide) {
                p.setGliding(true);
            }
        }
        return false;
    }

    /** Absprung: Zeit läuft, Flügel auf, Schwung von der Schanze. */
    private void takeOff(Player p, Flight f, double fraction) {
        f.state = Flight.State.FLYING;
        f.clock = new RunClock(tick, fraction);
        double speed = pl.settings().launch;
        f.launch = speed <= 0 ? null : f.course.start.launch().normalize().mul(speed);
        f.glideUntil = tick + 6;
        f.hopped = false;
        countFlight(p, f);
        p.sendActionBar(Texts.get(pl.lang(p), "flight.go"));
        p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.8f, 1.2f);
        p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 0.8f);
    }

    private void countFlight(Player p, Flight f) {
        if (f.practice) {
            return;
        }
        PlayerRecord rec = pl.store().record(p.getUniqueId(), p.getName());
        rec.flights++;
        rec.course(f.course.id).flights++;
        pl.net().stats(p.getUniqueId(), Map.of("flights", 1L), Map.of());
    }

    /** Durch einen Ring: Zwischenzeit, Schub, Farbe, Ton. */
    private void ring(Player p, Flight f, Ring ring, double fraction, Vec v) {
        long ms = f.clock.at(tick, fraction);
        int index = ring.index();
        f.splits[index] = ms;
        f.next++;
        f.ringsThisFlight++;
        f.landed = 0;
        pl.tint().passed(p, f.course, index);
        if (ring.finish()) {
            finish(p, f, ms);
            return;
        }
        String l = pl.lang(p);
        PlayerRecord rec = pl.store().record(p.getUniqueId(), p.getName());
        Long delta = f.practice ? null : Splits.delta(rec.course(f.course.id).splits, index, ms);
        Component d = delta == null ? Component.empty()
                : Component.text(" (" + TimeFormat.diff(delta, l) + ")", TextColor.fromHexString(Splits.colour(delta)));
        Component msg = Texts.get(l, "flight.ring", Texts.arg("n", index + 1), Texts.arg("total", f.course.ringCount()),
                Texts.arg("time", TimeFormat.clock(ms, l)), Texts.comp("delta", d));
        f.splitText = msg;
        f.splitShownUntil = tick + 30;
        p.sendActionBar(msg);
        float pitch = 0.7f + 1.1f * index / Math.max(1, f.course.ringCount() - 1);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, pitch);
        Location at = p.getLocation();
        p.spawnParticle(Particle.HAPPY_VILLAGER, at.getX(), at.getY() + 0.5, at.getZ(), 12, 0.6, 0.6, 0.6, 0);
        if (ring.kind() == Ring.Kind.BOOST && f.guide < 0) {
            Vec b = ring.boost(v);
            p.setVelocity(new Vector(b.x(), b.y(), b.z()));
            p.setGliding(true);
            p.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 1.1f);
            p.spawnParticle(Particle.FIREWORK, at.getX(), at.getY() + 0.3, at.getZ(), 20, 0.3, 0.3, 0.3, 0.12);
            p.sendMessage(Texts.chat(l, "flight.boost"));
        }
        if (f.bar != null) {
            f.bar.progress(Math.min(1f, (float) f.next / f.course.ringCount()));
        }
    }

    /**
     * Zurück zum letzten Ring: in der Luft kurz hinter dem Ring, mit Flügeln und Schwung des Musterflugs. Vor dem
     * ersten Ring zurück auf die Schanze (die Zeit beginnt neu, im Rennen läuft sie weiter).
     */
    private void reset(Player p, Flight f, String why) {
        String l = pl.lang(p);
        Course c = f.course;
        f.guide = -1;
        f.landed = 0;
        if (f.state == Flight.State.FLYING) {
            f.resets++;
            if (!f.practice) {
                PlayerRecord rec = pl.store().record(p.getUniqueId(), p.getName());
                rec.resets++;
                rec.course(c.id).resets++;
            }
        }
        p.sendMessage(Texts.chat(l, why, Texts.arg("n", f.next + 1)));
        int back = f.next > f.fromRing ? f.next : f.fromRing;
        if (f.next == 0 && f.fromRing == 0) {
            if (f.race == null || f.state != Flight.State.FLYING) {
                f.resetProgress();
                if (f.state != Flight.State.FROZEN) {
                    f.state = Flight.State.READY;
                }
            }
            p.setGliding(false);
            teleport(p, f, pl.startLocation(c, 0));
            p.sendActionBar(Texts.get(l, "flight.back.start"));
        } else {
            int t = back == f.fromRing && f.next == f.fromRing ? c.tickBefore(back, 25) : c.tickBefore(back, 0) + 2;
            Vec at = c.pathAt(t);
            Vec v = c.pathAt(t + 1).sub(at);
            teleport(p, f, new Location(pl.world(), at.x(), at.y(), at.z(), v.yaw(), Math.max(-20, v.pitch())));
            f.launch = v;
            f.glideUntil = tick + 10;
            p.sendActionBar(Texts.get(l, "flight.back.ring", Texts.arg("n", Math.max(1, f.next))));
        }
        f.splitShownUntil = tick + 30;
        f.splitText = Texts.get(l, "flight.back.short");
        p.playSound(p.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, 0.6f, 1.2f);
    }

    private void finish(Player p, Flight f, long ms) {
        UUID id = p.getUniqueId();
        Course c = f.course;
        flights.remove(id);
        if (f.bar != null) {
            f.bar.progress(1f);
            Bukkit.getScheduler().runTaskLater(pl, () -> p.hideBossBar(f.bar), 60L);
        }
        String l = pl.lang(p);
        Store store = pl.store();
        PlayerRecord rec = store.record(id, p.getName());
        landingAt.put(id, tick + AFTER_FINISH);
        landingCourse.put(id, c);
        Location at = p.getLocation();
        p.spawnParticle(Particle.FIREWORK, at.getX(), at.getY() + 1, at.getZ(), 50, 0.6, 0.8, 0.6, 0.1);
        p.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        if (f.practice) {
            p.showTitle(Title.title(Texts.get(l, "finish.title"), Component.text(TimeFormat.clock(ms, l),
                    NamedTextColor.LIGHT_PURPLE), LONG));
            p.sendMessage(Texts.chat(l, "finish.practice", Texts.arg("time", TimeFormat.clock(ms, l))));
            raceResult(p, f, ms);
            pl.state();
            return;
        }
        if (pl.settings().plausible && ms < c.designMs * 55 / 100) {
            pl.getLogger().warning(p.getName() + " war auf " + c.id + " unmöglich schnell (" + ms + " ms) – nicht gewertet.");
            p.sendMessage(Texts.chat(l, "finish.toofast", Texts.arg("time", TimeFormat.clock(ms, l))));
            reportEnd(p, f);
            store.save(rec);
            pl.state();
            return;
        }
        PlayerRecord.CourseStats cs = rec.course(c.id);
        long before = cs.best;
        Leaderboard board = store.board(c.id);
        Leaderboard.Entry oldRecord = board.record();
        rec.finishes++;
        cs.finishes++;
        boolean pb = before == 0 || ms < before;
        Medal oldMedal = before > 0 ? c.medal(before) : null;
        Medal medal = c.medal(pb ? ms : before);
        long now = Store.now();
        if (pb) {
            cs.best = ms;
            cs.bestWhen = now;
            cs.splits = new ArrayList<>();
            for (long s : f.splits) {
                cs.splits.add(s);
            }
            float[] path = f.path();
            if (path.length > 0) {
                store.saveGhost(id, c.id, path);
                ghosts.put(id + ":" + c.id, path);
            }
        }
        boolean weekly = c.id.equals(store.weekCourse());
        boolean firstWeekly = false;
        if (weekly) {
            firstWeekly = !rec.weekDone;
            rec.weekDone = true;
            if (rec.weekBest == 0 || ms < rec.weekBest) {
                rec.weekBest = ms;
                rec.weekBestWhen = now;
            }
        }
        store.submit(rec, c.id, cs.best, cs.bestWhen);
        Medal shown = c.medal(ms);
        p.showTitle(Title.title(Texts.get(l, "finish.title"), Texts.get(l, "finish.sub", Texts.arg("time",
                TimeFormat.clock(ms, l)), Texts.comp("medal", medal(shown, l))), LONG));
        if (before == 0) {
            p.sendMessage(Texts.chat(l, "finish.first", Texts.comp("course", courseName(c, l)),
                    Texts.arg("time", TimeFormat.clock(ms, l))));
        } else if (pb) {
            p.sendMessage(Texts.chat(l, "finish.pb", Texts.arg("time", TimeFormat.clock(ms, l)),
                    Texts.comp("delta", Component.text("(" + TimeFormat.diff(ms - before, l) + ")", NamedTextColor.GREEN)),
                    Texts.comp("course", courseName(c, l))));
        } else {
            p.sendMessage(Texts.chat(l, "finish.nopb", Texts.arg("time", TimeFormat.clock(ms, l)),
                    Texts.comp("delta", Component.text("(" + TimeFormat.diff(ms - before, l) + ")", NamedTextColor.RED)),
                    Texts.arg("best", TimeFormat.clock(before, l))));
        }
        int newTiers = Medal.tiers(medal) - Medal.tiers(oldMedal);
        if (newTiers > 0) {
            p.sendMessage(Texts.chat(l, "finish.medal", Texts.comp("medal", medal(medal, l))));
            if (medal == Medal.ADLER) {
                p.playSound(at, Sound.ENTITY_PHANTOM_AMBIENT, 0.6f, 1.6f);
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (!o.equals(p)) {
                        String ol = pl.lang(o);
                        o.sendMessage(Texts.chat(ol, "finish.eagle.all", Texts.arg("player", p.getName()),
                                Texts.comp("course", courseName(c, ol))));
                    }
                }
            }
        }
        Medal next = Medal.next(medal);
        if (next != null) {
            p.sendMessage(Texts.chat(l, "finish.next", Texts.comp("medal", medal(next, l)),
                    Texts.arg("diff", TimeFormat.diff(cs.best - next.limit(c.medals), l).substring(1))));
        }
        p.sendMessage(Texts.chat(l, "finish.rank", Texts.arg("rank", board.rank(id)), Texts.arg("total", board.size()),
                Texts.comp("course", courseName(c, l))));
        if (pb) {
            p.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
        Leaderboard.Entry newRecord = board.record();
        if (pb && newRecord != null && newRecord.uuid().equals(id) && (oldRecord == null || oldRecord.ms() > ms)) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                String ol = pl.lang(o);
                o.sendMessage(Texts.chat(ol, "finish.record", Texts.arg("player", p.getName()),
                        Texts.comp("course", courseName(c, ol)), Texts.arg("time", TimeFormat.clock(ms, ol))));
                o.playSound(o.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.7f, 1f);
            }
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.getX(), at.getY() + 1, at.getZ(), 60, 0.6, 1, 0.6, 0.3);
        }
        if (weekly) {
            Leaderboard wk = store.weekly();
            p.sendMessage(Texts.chat(l, "finish.weekly", Texts.arg("rank", wk.rank(id))));
            if (firstWeekly) {
                p.sendMessage(Texts.chat(l, "weekly.badge"));
                p.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (!o.equals(p)) {
                        o.sendMessage(Texts.chat(pl.lang(o), "weekly.broadcast", Texts.arg("player", p.getName())));
                    }
                }
            }
        }
        // Netzwerk: Zähler (ohne Bestwerte) und Bestzeit getrennt.
        Map<String, Long> add = new LinkedHashMap<>();
        add.put("finishes", 1L);
        add.put("finishes_" + c.id, 1L);
        if (newTiers > 0) {
            add.put("medals", (long) newTiers);
            if (medal == Medal.ADLER) {
                add.put("eagles", 1L);
            }
        }
        pl.net().stats(id, add, Map.of());
        pl.net().stats(id, Map.of(), Map.of("time_" + c.id + "_ms", ms));
        reportEnd(p, f);
        store.save(rec);
        pl.boards(c.id);
        raceResult(p, f, ms);
        pl.state();
    }

    /** Ringe und Zurücksetzungen dieses Flugs ans Netzwerk und in die Spielerdatei (einmal pro Flug). */
    private void reportEnd(Player p, Flight f) {
        if (f.practice) {
            f.ringsThisFlight = 0;
            f.resets = 0;
            return;
        }
        Map<String, Long> add = new LinkedHashMap<>();
        if (f.ringsThisFlight > 0) {
            add.put("rings", (long) f.ringsThisFlight);
            pl.store().record(p.getUniqueId(), p.getName()).rings += f.ringsThisFlight;
        }
        if (f.resets > 0) {
            add.put("resets", (long) f.resets);
        }
        if (!add.isEmpty()) {
            pl.net().stats(p.getUniqueId(), add, Map.of());
        }
        f.ringsThisFlight = 0;
        f.resets = 0;
    }

    /** Nach dem Ziel: kurz ausgleiten lassen, dann auf den Landeplatz. */
    private void afterFinish() {
        for (Iterator<Map.Entry<UUID, Long>> it = landingAt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (tick < e.getValue()) {
                continue;
            }
            it.remove();
            Player p = Bukkit.getPlayer(e.getKey());
            Course c = landingCourse.remove(e.getKey());
            if (p == null || c == null || flights.containsKey(e.getKey())) {
                continue;
            }
            p.setGliding(false);
            p.setVelocity(new Vector());
            p.setFallDistance(0);
            Vec a = c.landing.at();
            p.teleport(new Location(pl.world(), a.x(), a.y(), a.z(), c.landing.yaw(), 0));
            pl.hotbar().after(p, c);
            p.playSound(p.getLocation(), Sound.BLOCK_GRASS_STEP, 1f, 1f);
        }
    }

    // =========================================================================================
    // Anzeige unterwegs
    // =========================================================================================

    private void hud(Player p, Flight f, Vec c1, Vec moved) {
        String l = pl.lang(p);
        Course c = f.course;
        Ring next = f.next < c.ringCount() ? c.rings.get(f.next) : null;
        if (tick % 2 == 0) {
            if (tick < f.splitShownUntil && f.splitText != null) {
                p.sendActionBar(f.splitText);
            } else if (f.state == Flight.State.FLYING) {
                long ms = f.clock.now(tick);
                Component bar = Component.text("⏱ ", NamedTextColor.AQUA)
                        .append(Component.text(TimeFormat.clock(ms, l), NamedTextColor.WHITE))
                        .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                        .append(Texts.get(l, "hud.ring", Texts.arg("n", Math.min(f.next + 1, c.ringCount())),
                                Texts.arg("total", c.ringCount())))
                        .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(Glide.kmh(moved) + " km/h", NamedTextColor.YELLOW));
                if (next != null) {
                    bar = bar.append(Component.text("  " + arrow(p, next.center().sub(c1)), NamedTextColor.GOLD));
                }
                if (f.practice) {
                    bar = bar.append(Component.text("  ·  ", NamedTextColor.DARK_GRAY)).append(Texts.get(l, "hud.practice"));
                }
                p.sendActionBar(bar);
            } else if (f.state == Flight.State.READY && tick % 20 == 0) {
                p.sendActionBar(Texts.get(l, "flight.ready"));
            }
        }
        // Wegweiser: Funken am Rand des nächsten Rings (nur für diesen Spieler)
        if (next != null && tick % 8 == 0 && f.state != Flight.State.FROZEN) {
            rim(p, next, next.finish() ? Particle.WAX_ON : Particle.END_ROD, 16);
        }
        // Geist der Bestzeit: ein kleiner Adler aus blauen Funken fliegt den besten Weg mit
        if (f.ghost != null && f.state == Flight.State.FLYING && f.clock != null) {
            long i = (tick - f.clock.startTick() - 1) * 3;
            if (i >= 0 && i + 5 < f.ghost.length) {
                double gx = f.ghost[(int) i];
                double gy = f.ghost[(int) i + 1] + 0.4;
                double gz = f.ghost[(int) i + 2];
                Vec dir = new Vec(f.ghost[(int) i + 3] - gx, 0, f.ghost[(int) i + 5] - gz).normalize();
                Vec side = new Vec(-dir.z(), 0, dir.x());
                p.spawnParticle(Particle.DUST, gx, gy, gz, 3, 0.08, 0.08, 0.08, 0, GHOST);
                for (double k : new double[] {0.5, 0.9, 1.3}) {
                    double lift = k * 0.25;
                    p.spawnParticle(Particle.DUST, gx + side.x() * k, gy + lift, gz + side.z() * k, 1, 0, 0, 0, 0, GHOST_WING);
                    p.spawnParticle(Particle.DUST, gx - side.x() * k, gy + lift, gz - side.z() * k, 1, 0, 0, 0, 0, GHOST_WING);
                }
            }
        }
    }

    /** Funken rundherum am Rand eines Rings. */
    static void rim(Player p, Ring r, Particle particle, int n) {
        Vec u = Vec.UP.cross(r.normal());
        u = u.length() < 1e-6 ? new Vec(1, 0, 0) : u.normalize();
        Vec w = r.normal().cross(u).normalize();
        double rad = r.radius() + 0.2;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            Vec q = r.center().add(u.mul(Math.cos(a) * rad)).add(w.mul(Math.sin(a) * rad));
            p.spawnParticle(particle, q.x(), q.y(), q.z(), 1, 0, 0, 0, 0);
        }
    }

    /** Pfeil zur Richtung des nächsten Rings, aus Sicht des Spielers (↑ geradeaus, ← links …). */
    private static String arrow(Player p, Vec to) {
        double want = to.yaw();
        double diff = ((want - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        String[] arrows = {"↓", "↙", "←", "↖", "↑", "↗", "→", "↘", "↓"};
        int i = (int) Math.round((diff + 180) / 45.0);
        return arrows[Math.max(0, Math.min(8, i))];
    }

    private float[] ghost(UUID id, String course) {
        String key = id + ":" + course;
        if (!ghosts.containsKey(key)) {
            ghosts.put(key, pl.store().loadGhost(id, course));
        }
        return ghosts.get(key);
    }

    public void forgetGhosts(UUID id) {
        ghosts.keySet().removeIf(k -> k.startsWith(id + ":"));
    }

    // =========================================================================================
    // Test-Autopilot
    // =========================================================================================

    private void guideStep(Player p, Flight f) {
        Course c = f.course;
        int t = f.guide;
        if (t >= c.pathTicks() - 1 || f.next > f.guideUntil) {
            f.guide = -1;
            Vec v = c.pathAt(c.pathTicks() - 1).sub(c.pathAt(c.pathTicks() - 2));
            p.setGliding(true);
            p.setVelocity(new Vector(v.x(), v.y(), v.z()));
            return;
        }
        Vec at = c.pathAt(t);
        if (f.guideMiss >= 0 && f.guideMiss < c.ringCount()) {
            Ring r = c.rings.get(f.guideMiss);
            double d = at.distance(r.center());
            if (d < r.radius() + 25) {
                Vec u = Vec.UP.cross(r.normal()).normalize();
                at = at.add(u.mul((r.radius() + 6) * Math.max(0, 1 - d / (r.radius() + 25)) * 1.6));
            }
        }
        Vec v = c.pathAt(t + 1).sub(c.pathAt(t));
        if (f.state != Flight.State.FLYING && t == 0) {
            p.teleport(new Location(pl.world(), at.x(), at.y(), at.z(), v.yaw(), v.pitch()));
        } else {
            p.teleport(new Location(pl.world(), at.x(), at.y(), at.z(), v.yaw(), v.pitch()));
        }
        p.setGliding(true);
        f.guide = t + 1;
    }

    // =========================================================================================
    // Party-Rennen
    // =========================================================================================

    private void race(Race race) {
        switch (race.state) {
            case GATHER -> {
                boolean all = race.joined.containsAll(race.invited);
                if (tick < race.until && !all) {
                    if ((race.until - tick) % 100 == 0) {
                        for (UUID u : race.joined) {
                            Player o = Bukkit.getPlayer(u);
                            if (o != null) {
                                o.sendActionBar(Texts.get(pl.lang(o), "race.wait", Texts.arg("sec", (race.until - tick) / 20)));
                            }
                        }
                    }
                    return;
                }
                for (UUID u : race.invited) {
                    if (invites.get(u) == race) {
                        invites.remove(u);
                        Player o = Bukkit.getPlayer(u);
                        if (o != null && !flights.containsKey(u)) {
                            pl.hotbar().base(o);
                        }
                    }
                }
                if (race.joined.size() <= 1) {
                    races.remove(race);
                    Flight f = flights.get(race.leader);
                    Player p = Bukkit.getPlayer(race.leader);
                    if (f != null && p != null) {
                        f.race = null;
                        f.state = Flight.State.READY;
                        p.sendMessage(Texts.chat(pl.lang(p), "race.solo"));
                    }
                    return;
                }
                race.state = Race.State.COUNTDOWN;
                race.count = 3;
                race.until = tick;
            }
            case COUNTDOWN -> {
                if (tick < race.until) {
                    return;
                }
                if (race.count > 0) {
                    for (Player o : members(race)) {
                        o.showTitle(Title.title(Component.text(race.count, NamedTextColor.YELLOW), Component.empty(), SHORT));
                        o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    }
                    race.count--;
                    race.until = tick + 20;
                    return;
                }
                race.state = Race.State.RUNNING;
                int lane = 0;
                for (Player o : members(race)) {
                    Flight f = flights.get(o.getUniqueId());
                    if (f == null) {
                        continue;
                    }
                    // Alle gleichzeitig von der Kante, nebeneinander
                    Location line = toLocation(race.course.start.line(), race.course.start.yaw());
                    Vec side = sideOf(race.course);
                    double off = (lane % 2 == 0 ? 1 : -1) * ((lane + 1) / 2) * 1.6;
                    line.add(side.x() * off, 0.2, side.z() * off);
                    teleport(o, f, line);
                    lane++;
                    takeOff(o, f, 0);
                    f.clock = new RunClock(tick, 0);
                    o.showTitle(Title.title(Texts.get(pl.lang(o), "race.go"), Component.empty(), SHORT));
                    o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                }
            }
            case RUNNING -> checkRaceOver(race);
        }
    }

    private List<Player> members(Race race) {
        List<Player> out = new ArrayList<>();
        for (UUID u : race.joined) {
            Player o = Bukkit.getPlayer(u);
            Flight f = flights.get(u);
            if (o != null && f != null && f.race == race) {
                out.add(o);
            }
        }
        return out;
    }

    private void raceResult(Player p, Flight f, long ms) {
        Race race = f.race;
        if (race == null || race.state != Race.State.RUNNING) {
            return;
        }
        race.results.add(new Race.Result(p.getUniqueId(), p.getName(), ms));
        int place = race.results.size();
        for (UUID u : race.joined) {
            Player o = Bukkit.getPlayer(u);
            if (o != null) {
                String ol = pl.lang(o);
                o.sendMessage(Texts.chat(ol, "race.finish", Texts.arg("player", p.getName()),
                        Texts.arg("time", TimeFormat.clock(ms, ol)), Texts.arg("place", place)));
            }
        }
        checkRaceOver(race);
    }

    private void checkRaceOver(Race race) {
        if (!races.contains(race) || race.state != Race.State.RUNNING || !race.done()) {
            return;
        }
        races.remove(race);
        if (race.results.isEmpty()) {
            return;
        }
        String winner = race.results.get(0).name();
        for (UUID u : race.joined) {
            Player o = Bukkit.getPlayer(u);
            if (o != null) {
                o.sendMessage(Texts.chat(pl.lang(o), "race.over", Texts.arg("player", winner)));
            }
        }
    }

    // =========================================================================================
    // Hilfen
    // =========================================================================================

    private void teleport(Player p, Flight f, Location to) {
        p.setVelocity(new Vector());
        p.setFallDistance(0);
        p.teleport(to);
        f.hasPrev = false;
    }

    private Location toLocation(Vec v, float yaw) {
        return new Location(pl.world(), v.x(), v.y(), v.z(), yaw, 0);
    }

    static Vec sideOf(Course c) {
        Vec dir = Vec.look(c.start.yaw(), 0);
        return new Vec(dir.z(), 0, -dir.x());
    }

    public static Component courseName(Course c, String lang) {
        return Component.text(c.name(lang), TextColor.fromHexString(c.difficulty.colour));
    }

    public static Component difficulty(Course c, String lang) {
        return Component.text(c.difficulty.stars() + " " + c.difficulty.name(lang), TextColor.fromHexString(c.difficulty.colour));
    }

    public static Component medal(Medal m, String lang) {
        if (m == null) {
            return Component.empty();
        }
        return Component.text(m.symbol + " " + m.name(lang), TextColor.fromHexString(m.colour));
    }

    /** Farbe des Rings für Texte. */
    public static TextColor ringColour(Ring r) {
        return TextColor.fromHexString(Scenery.ringColour(r));
    }

    /** Material für Symbole. */
    static Material icon(Course c) {
        Material m = Material.matchMaterial(c.icon);
        return m == null ? Material.FEATHER : m;
    }
}
