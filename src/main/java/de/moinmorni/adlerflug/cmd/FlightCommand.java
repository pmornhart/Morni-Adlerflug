package de.moinmorni.adlerflug.cmd;

import de.moinmorni.adlerflug.AdlerflugPlugin;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.game.Flights;
import de.moinmorni.adlerflug.stats.Leaderboard;
import de.moinmorni.adlerflug.stats.PlayerRecord;
import de.moinmorni.adlerflug.time.TimeFormat;
import de.moinmorni.adlerflug.ui.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** {@code /adlerflug} – alles, was die Gegenstände können, auch als Befehl (z. B. für Bedrock oder Tastatur-Fans). */
public final class FlightCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("start", "top", "stats", "woche", "tag", "uebung", "geist",
            "verstecken", "ring", "neustart", "aufgeben", "basislager", "mitmachen", "lobby", "hilfe");

    private final AdlerflugPlugin pl;

    public FlightCommand(AdlerflugPlugin pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = sender instanceof Player pp ? pp : null;
        String l = p == null ? "de" : pl.lang(p);
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!pl.worlds().ready()) {
            sender.sendMessage(Texts.chat(l, "cmd.notready"));
            return true;
        }
        switch (sub) {
            case "" -> {
                if (p == null) {
                    sender.sendMessage(Texts.chat(l, "cmd.help"));
                } else {
                    pl.menus().courses(p);
                }
            }
            case "hilfe", "help", "?" -> sender.sendMessage(Texts.chat(l, "cmd.help"));
            case "start" -> {
                if (p == null) {
                    sender.sendMessage(Texts.chat(l, "cmd.player"));
                    return true;
                }
                course(args, 1, sender, l).ifPresent(c -> pl.flights().select(p, c));
            }
            case "top" -> course(args, 1, sender, l).ifPresent(c -> top(sender, l, c));
            case "stats" -> stats(sender, l, p, args.length > 1 ? args[1] : null);
            case "woche", "week" -> week(sender, l);
            case "tag", "heute", "day", "today" -> day(sender, l, p, args.length > 1 && "start".equalsIgnoreCase(args[1]));
            case "uebung", "übung", "practice" -> {
                if (p != null) {
                    pl.flights().togglePractice(p);
                }
            }
            case "geist", "ghost" -> {
                if (p != null) {
                    pl.toggleGhost(p);
                }
            }
            case "verstecken", "hide" -> {
                if (p != null) {
                    pl.toggleHide(p);
                }
            }
            case "ring", "zurueck", "zurück", "back" -> {
                if (p != null) {
                    pl.flights().backToRing(p);
                }
            }
            case "neustart", "restart" -> {
                if (p != null) {
                    pl.flights().restart(p);
                }
            }
            case "aufgeben", "giveup" -> {
                if (p != null) {
                    pl.flights().giveUp(p);
                }
            }
            case "basislager", "base" -> {
                if (p != null) {
                    pl.flights().abandon(p);
                    pl.toBasecamp(p);
                }
            }
            case "mitmachen", "join" -> {
                if (p != null) {
                    pl.flights().joinRace(p);
                }
            }
            case "lobby" -> {
                if (p != null) {
                    pl.net().askToLobby(p);
                }
            }
            default -> {
                Optional<Course> c = pl.course(sub);
                if (c.isPresent() && p != null) {
                    pl.flights().select(p, c.get());
                } else {
                    sender.sendMessage(Texts.chat(l, "cmd.help"));
                }
            }
        }
        return true;
    }

    Optional<Course> course(String[] args, int i, CommandSender sender, String l) {
        Optional<Course> c = args.length > i ? pl.course(args[i].toLowerCase(Locale.ROOT)) : Optional.empty();
        if (c.isEmpty()) {
            sender.sendMessage(Texts.chat(l, "cmd.course", Texts.arg("list",
                    String.join(", ", pl.courses().stream().map(x -> x.id).toList()))));
        }
        return c;
    }

    private void top(CommandSender sender, String l, Course c) {
        Leaderboard board = pl.store().board(c.id);
        sender.sendMessage(Texts.chat(l, "top.head", Texts.comp("course", Flights.courseName(c, l))));
        List<Leaderboard.Entry> top = board.top(10);
        if (top.isEmpty()) {
            sender.sendMessage(Texts.get(l, "top.empty"));
        }
        for (int i = 0; i < top.size(); i++) {
            Leaderboard.Entry e = top.get(i);
            sender.sendMessage(Texts.get(l, "top.line", Texts.arg("rank", i + 1), Texts.arg("player", e.name()),
                    Texts.arg("time", TimeFormat.clock(e.ms(), l)), Texts.comp("medal", Flights.medal(c.medal(e.ms()), l))));
        }
        if (sender instanceof Player p) {
            Leaderboard.Entry mine = board.of(p.getUniqueId());
            if (mine != null && board.rank(p.getUniqueId()) > 10) {
                sender.sendMessage(Texts.get(l, "top.you", Texts.arg("rank", board.rank(p.getUniqueId())),
                        Texts.arg("time", TimeFormat.clock(mine.ms(), l))));
            }
        }
    }

    private void stats(CommandSender sender, String l, Player self, String name) {
        PlayerRecord r;
        if (name == null) {
            if (self == null) {
                sender.sendMessage(Texts.chat(l, "cmd.player"));
                return;
            }
            r = pl.store().record(self.getUniqueId(), self.getName());
        } else {
            Player online = Bukkit.getPlayerExact(name);
            r = online != null ? pl.store().record(online.getUniqueId(), online.getName()) : pl.store().findByName(name);
        }
        if (r == null) {
            sender.sendMessage(Texts.chat(l, "cmd.unknown"));
            return;
        }
        sender.sendMessage(Texts.chat(l, "stats.head", Texts.arg("player", r.name)));
        sender.sendMessage(Texts.get(l, "stats.total", Texts.arg("finishes", r.finishes), Texts.arg("flights", r.flights),
                Texts.arg("rings", r.rings)));
        sender.sendMessage(Texts.get(l, "stats.medals", Texts.comp("medals", pl.medalCount(r, l))));
        if (r.weeklyDone(pl.store().weekId())) {
            sender.sendMessage(Texts.get(l, "stats.badge", Texts.comp("badge", Texts.get(l, "badge"))));
        }
        if (r.days > 0) {
            sender.sendMessage(Texts.get(l, "stats.days", Texts.arg("n", r.days)));
        }
        for (Course c : pl.courses()) {
            long best = r.best(c.id);
            if (best > 0) {
                sender.sendMessage(Texts.get(l, "stats.course", Texts.comp("course", Flights.courseName(c, l)),
                        Texts.arg("time", TimeFormat.clock(best, l)), Texts.comp("medal", Flights.medal(c.medal(best), l)),
                        Texts.arg("rank", pl.store().board(c.id).rank(r.uuid))));
            } else {
                sender.sendMessage(Texts.get(l, "stats.course.none", Texts.comp("course", Flights.courseName(c, l))));
            }
        }
    }

    private void week(CommandSender sender, String l) {
        Course w = pl.weeklyCourse();
        if (w == null) {
            return;
        }
        sender.sendMessage(Texts.chat(l, "week.head", Texts.comp("course", Flights.courseName(w, l)),
                Texts.arg("left", pl.weekLeft(l))));
        List<Leaderboard.Entry> top = pl.store().weekly().top(10);
        if (top.isEmpty()) {
            sender.sendMessage(Texts.get(l, "top.empty"));
        }
        for (int i = 0; i < top.size(); i++) {
            Leaderboard.Entry e = top.get(i);
            sender.sendMessage(Texts.get(l, "top.line", Texts.arg("rank", i + 1), Texts.arg("player", e.name()),
                    Texts.arg("time", TimeFormat.clock(e.ms(), l)), Texts.comp("medal", Flights.medal(w.medal(e.ms()), l))));
        }
    }

    /** Strecke des Tages: Bedingungen und Tages-Bestenliste (mit {@code start} gleich losfliegen). */
    private void day(CommandSender sender, String l, Player p, boolean start) {
        var d = pl.daily();
        if (d == null) {
            sender.sendMessage(Texts.chat(l, "daily.building"));
            return;
        }
        if (start && p != null) {
            pl.flights().select(p, d.course());
            return;
        }
        sender.sendMessage(Texts.chat(l, "daily.head", Texts.comp("conditions", pl.dailyText(l))));
        top(sender, l, d.course());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).forEach(out::add);
        } else if (args.length == 2 && List.of("start", "top").contains(args[0].toLowerCase(Locale.ROOT))) {
            pl.courses().stream().map(c -> c.id).filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .forEach(out::add);
        } else if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).forEach(out::add);
        }
        return out;
    }
}
