package de.moinmorni.adlerflug.game;

import de.moinmorni.adlerflug.AdlerflugPlugin;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Difficulty;
import de.moinmorni.adlerflug.course.Medal;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.stats.Leaderboard;
import de.moinmorni.adlerflug.stats.PlayerRecord;
import de.moinmorni.adlerflug.time.TimeFormat;
import de.moinmorni.adlerflug.ui.Items;
import de.moinmorni.adlerflug.ui.Menu;
import de.moinmorni.adlerflug.ui.Texts;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Die Truhen-Menüs (gehen auch auf Bedrock): Strecke wählen (nach Schwierigkeit), Flugbuch, Bestenliste je Strecke und
 * im Übungsmodus „ab Ring …“.
 */
public final class Menus {

    private static final int WRAP = 38;

    private final AdlerflugPlugin pl;

    public Menus(AdlerflugPlugin pl) {
        this.pl = pl;
    }

    // ---------- Strecke wählen ----------

    public void courses(Player p) {
        String l = pl.lang(p);
        UUID id = p.getUniqueId();
        PlayerRecord rec = pl.store().record(id, p.getName());
        Menu m = new Menu(6, Texts.get(l, "menu.courses"));
        Difficulty[] ds = Difficulty.values();
        for (int row = 0; row < ds.length; row++) {
            Difficulty d = ds[row];
            int base = (row + 1) * 9;
            m.set(base, Items.icon(Items.material(d.block), Component.text(d.stars() + " " + d.name(l),
                    TextColor.fromHexString(d.colour)).decorate(TextDecoration.BOLD), List.of(), false), null);
            int col = 2;
            for (Course c : pl.model().courses()) {
                if (c.difficulty != d) {
                    continue;
                }
                m.set(base + col, courseIcon(c, rec, l, true), (who, right) -> {
                    if (right) {
                        top(who, c, true);
                    } else {
                        who.closeInventory();
                        pl.flights().select(who, c);
                    }
                });
                col += 2;
            }
        }
        m.set(4, Items.icon(Material.ELYTRA, Texts.get(l, "welcome.title"), List.of(Texts.get(l, "welcome.subtitle")),
                false), null);
        boolean practice = pl.flights().practice(id);
        m.set(45, Items.icon(Material.TARGET, Texts.get(l, "menu.practice", Texts.arg("state",
                Texts.raw(l, practice ? "on" : "off"))), List.of(Texts.get(l, "menu.practice.lore")), practice),
                (who, right) -> {
                    pl.flights().togglePractice(who);
                    courses(who);
                });
        m.set(47, Items.icon(Material.PHANTOM_MEMBRANE, Texts.get(l, "menu.ghost", Texts.arg("state",
                Texts.raw(l, rec.ghost ? "on" : "off"))), List.of(Texts.get(l, "menu.ghost.lore")), rec.ghost),
                (who, right) -> {
                    pl.toggleGhost(who);
                    courses(who);
                });
        m.set(49, Items.icon(Material.BOOK, Texts.get(l, "item.top"), List.of(Texts.get(l, "item.top.lore")), false),
                (who, right) -> book(who));
        m.set(51, pl.hotbar().hide(p), (who, right) -> {
            pl.toggleHide(who);
            courses(who);
        });
        m.set(53, Items.icon(Material.BARRIER, Texts.get(l, "menu.close"), List.of(), false),
                (who, right) -> who.closeInventory());
        m.open(p);
        click(p);
    }

    /** Symbol einer Strecke mit Bestzeit, Medaillen, Rekord und Platz. */
    private ItemStack courseIcon(Course c, PlayerRecord rec, String l, boolean clickHints) {
        List<Component> lore = new ArrayList<>();
        lore.add(Flights.difficulty(c, l).append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Texts.get(l, "menu.rings", Texts.arg("n", c.ringCount()))));
        for (String line : wrap(c.text(l))) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        long best = rec.best(c.id);
        Leaderboard board = pl.store().board(c.id);
        if (best > 0) {
            lore.add(Texts.get(l, "menu.best", Texts.arg("time", TimeFormat.clock(best, l)),
                    Texts.comp("medal", Flights.medal(c.medal(best), l))));
            lore.add(Texts.get(l, "menu.rank", Texts.arg("rank", board.rank(rec.uuid)), Texts.arg("total", board.size())));
        } else {
            lore.add(Texts.get(l, "menu.best.none"));
        }
        lore.add(Texts.get(l, "menu.medals", Texts.comp("list", medalList(c, l))));
        Leaderboard.Entry wr = board.record();
        lore.add(wr == null ? Texts.get(l, "menu.record.none") : Texts.get(l, "menu.record",
                Texts.arg("time", TimeFormat.clock(wr.ms(), l)), Texts.arg("player", wr.name())));
        boolean weekly = c.id.equals(pl.store().weekCourse());
        if (weekly) {
            lore.add(Component.empty());
            lore.add(Texts.get(l, "menu.weeklytag"));
            lore.add(Texts.get(l, "menu.weekly.left", Texts.arg("left", pl.weekLeft(l))));
        }
        if (clickHints) {
            lore.add(Component.empty());
            lore.add(Texts.get(l, "menu.click.start"));
            lore.add(Texts.get(l, "menu.click.top"));
        }
        Component name = Flights.courseName(c, l).decorate(TextDecoration.BOLD);
        if (weekly) {
            name = Component.text("★ ", TextColor.fromHexString("#f4a259")).append(name);
        }
        return Items.icon(Flights.icon(c), name, lore, weekly);
    }

    static Component medalList(Course c, String l) {
        Component out = Component.empty();
        Medal[] ms = Medal.values();
        for (int i = ms.length - 1; i >= 0; i--) {
            Medal m = ms[i];
            out = out.append(Component.text(m.symbol + " " + TimeFormat.limit(m.limit(c.medals), l),
                    TextColor.fromHexString(m.colour)));
            if (i > 0) {
                out = out.append(Component.text(" · ", NamedTextColor.DARK_GRAY));
            }
        }
        return out;
    }

    // ---------- Übung: ab Ring … ----------

    public void rings(Player p, Course c) {
        String l = pl.lang(p);
        int rows = Math.min(6, (c.ringCount() + 8) / 9 + 1);
        Menu m = new Menu(rows, Texts.get(l, "menu.pick", Texts.comp("course", Flights.courseName(c, l))));
        m.set(0, Items.icon(Material.OAK_STAIRS, Texts.get(l, "menu.pick.start"), List.of(), false), (who, right) -> {
            who.closeInventory();
            pl.flights().practiceFrom(who, c, 0);
        });
        for (Ring r : c.rings) {
            int slot = r.index() + 1;
            if (slot >= rows * 9) {
                break;
            }
            Material icon = switch (r.kind()) {
                case FINISH -> Material.GOLD_BLOCK;
                case BOOST -> Material.LIGHT_BLUE_CONCRETE;
                case NORMAL -> Material.matchMaterial(de.moinmorni.adlerflug.courses.Scenery.SECTION_BLOCKS[
                        Math.min(3, r.section())]);
            };
            m.set(slot, Items.icon(icon, Texts.get(l, "menu.pick.ring", Texts.arg("n", r.index() + 1)),
                    List.of(Texts.get(l, r.kind() == Ring.Kind.BOOST ? "menu.pick.boost" : "menu.pick.normal")), false),
                    (who, right) -> {
                        who.closeInventory();
                        pl.flights().practiceFrom(who, c, r.index());
                    });
        }
        m.open(p);
        click(p);
    }

    // ---------- Flugbuch ----------

    public void book(Player p) {
        String l = pl.lang(p);
        PlayerRecord rec = pl.store().record(p.getUniqueId(), p.getName());
        Menu m = new Menu(4, Texts.get(l, "menu.book"));
        int[] slots = {10, 11, 12, 14, 15, 16};
        List<Course> cs = pl.model().courses();
        for (int i = 0; i < cs.size() && i < slots.length; i++) {
            Course c = cs.get(i);
            ItemStack icon = courseIcon(c, rec, l, false);
            var meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Items.plain(Texts.get(l, "menu.click.open")));
            meta.lore(lore);
            icon.setItemMeta(meta);
            m.set(slots[i], icon, (who, right) -> top(who, c, false));
        }
        Course w = pl.weeklyCourse();
        if (w != null) {
            m.set(31, Items.icon(Material.NETHER_STAR, Texts.get(l, "menu.weekly"),
                    List.of(Flights.courseName(w, l), Texts.get(l, "menu.weekly.left", Texts.arg("left", pl.weekLeft(l))),
                            Texts.get(l, "menu.click.open")), true), (who, right) -> weekly(who));
        }
        m.set(27, Items.icon(Material.PLAYER_HEAD, Texts.get(l, "menu.stats"), statsLore(rec, l), false), null);
        m.set(35, Items.icon(Material.BARRIER, Texts.get(l, "menu.close"), List.of(), false),
                (who, right) -> who.closeInventory());
        m.open(p);
        click(p);
    }

    List<Component> statsLore(PlayerRecord rec, String l) {
        List<Component> lore = new ArrayList<>();
        lore.add(Texts.get(l, "stats.total", Texts.arg("finishes", rec.finishes), Texts.arg("flights", rec.flights),
                Texts.arg("rings", rec.rings)));
        lore.add(Texts.get(l, "stats.medals", Texts.comp("medals", pl.medalCount(rec, l))));
        if (rec.weeklyDone(pl.store().weekId())) {
            lore.add(Texts.get(l, "stats.badge", Texts.comp("badge", Texts.get(l, "badge"))));
        }
        return lore;
    }

    // ---------- Bestenliste einer Strecke ----------

    public void top(Player p, Course c, boolean fromCourses) {
        String l = pl.lang(p);
        render(p, Texts.get(l, "menu.top", Texts.arg("course", c.name(l))), pl.store().board(c.id), c, l, fromCourses);
    }

    public void weekly(Player p) {
        String l = pl.lang(p);
        Course w = pl.weeklyCourse();
        if (w != null) {
            render(p, Texts.get(l, "menu.weekly"), pl.store().weekly(), w, l, false);
        }
    }

    private void render(Player p, Component title, Leaderboard board, Course c, String l, boolean fromCourses) {
        Menu m = new Menu(5, title);
        int[] slots = {4, 12, 13, 14, 20, 21, 22, 23, 24, 31};
        List<Leaderboard.Entry> top = board.top(10);
        DateTimeFormatter date = DateTimeFormatter.ofPattern("en".equals(l) ? "yyyy-MM-dd" : "dd.MM.yyyy")
                .withZone(ZoneId.of("Europe/Berlin"));
        for (int i = 0; i < top.size(); i++) {
            Leaderboard.Entry e = top.get(i);
            ItemStack head = Items.icon(i < 3 ? Material.PLAYER_HEAD : Material.PAPER,
                    Texts.get(l, "menu.entry", Texts.arg("rank", i + 1), Texts.arg("player", e.name())),
                    List.of(Texts.get(l, "menu.entry.time", Texts.arg("time", TimeFormat.clock(e.ms(), l)),
                                    Texts.comp("medal", Flights.medal(c.medal(e.ms()), l))),
                            Texts.get(l, "menu.entry.when", Texts.arg("date", date.format(Instant.ofEpochMilli(e.when()))))),
                    false);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                Player online = Bukkit.getPlayer(e.uuid());
                skull.setPlayerProfile(online != null ? online.getPlayerProfile() : Bukkit.createProfile(e.uuid(), e.name()));
                head.setItemMeta(skull);
            }
            m.set(slots[i], head, null);
        }
        if (top.isEmpty()) {
            m.set(22, Items.icon(Material.FEATHER, Texts.get(l, "top.empty"), List.of(), false), null);
        }
        Leaderboard.Entry mine = board.of(p.getUniqueId());
        if (mine != null) {
            m.set(40, Items.icon(Material.EMERALD, Texts.get(l, "menu.you"), List.of(Texts.get(l, "top.you",
                    Texts.arg("rank", board.rank(p.getUniqueId())), Texts.arg("time", TimeFormat.clock(mine.ms(), l)))),
                    false), null);
        }
        m.set(36, Items.icon(Material.ARROW, Texts.get(l, "menu.back"), List.of(), false), (who, right) -> {
            if (fromCourses) {
                courses(who);
            } else {
                book(who);
            }
        });
        m.set(44, Items.icon(Material.EMERALD_BLOCK, Texts.get(l, "menu.click.start"), List.of(Flights.courseName(c, l)),
                false), (who, right) -> {
                    who.closeInventory();
                    pl.flights().select(who, c);
                });
        m.open(p);
        click(p);
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /** Zeilen umbrechen für Gegenstands-Beschreibungen. */
    static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > WRAP) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
