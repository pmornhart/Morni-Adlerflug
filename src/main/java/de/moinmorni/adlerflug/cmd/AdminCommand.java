package de.moinmorni.adlerflug.cmd;

import de.moinmorni.adlerflug.AdlerflugPlugin;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.course.Pilots;
import de.moinmorni.adlerflug.flight.FlightSim;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.stats.PlayerRecord;
import de.moinmorni.adlerflug.ui.Texts;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /adleradmin} – nur zum Verwalten und Testen (Einstellungen gehören in die config.yml bzw. ins Panel):
 * Zustand, Test-Autopilot, Strecken-Daten, Zeiten löschen, Welt neu bauen, Strecken prüfen.
 */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("status", "start", "fliege", "strecke", "stationen", "reset", "neubau", "pruefen");

    private final AdlerflugPlugin pl;

    public AdminCommand(AdlerflugPlugin pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String l = sender instanceof Player p ? pl.lang(p) : "de";
        if (!sender.hasPermission("adlerflug.admin")) {
            sender.sendMessage(Texts.chat(l, "cmd.perm"));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status(sender);
            case "fliege" -> guide(sender, l, args);
            case "start" -> {
                Player p = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                Optional<Course> c = args.length > 2 ? pl.model().course(args[2]) : Optional.empty();
                if (p == null || c.isEmpty()) {
                    sender.sendMessage(Component.text("/adleradmin start <spieler> <strecke>"));
                } else {
                    pl.flights().select(p, c.get());
                    sender.sendMessage(Component.text(p.getName() + " steht auf der Schanze von " + c.get().id + "."));
                }
            }
            case "strecke" -> {
                Optional<Course> c = args.length > 1 ? pl.model().course(args[1]) : Optional.empty();
                if (c.isEmpty()) {
                    sender.sendMessage(Texts.chat(l, "cmd.course", Texts.arg("list",
                            String.join(", ", pl.model().courses().stream().map(x -> x.id).toList()))));
                } else {
                    sender.sendMessage(Component.text(json(c.get())));
                }
            }
            case "stationen" -> {
                StringBuilder b = new StringBuilder("[");
                for (var st : pl.model().basecamp().stations()) {
                    b.append(b.length() > 1 ? "," : "").append(String.format(Locale.ROOT,
                            "{\"course\":\"%s\",\"x\":%d,\"y\":%d,\"z\":%d}", st.course(), st.signX(), st.signY(),
                            st.signZ()));
                }
                sender.sendMessage(Component.text(b.append(']').toString()));
            }
            case "reset" -> reset(sender, l, args);
            case "neubau" -> {
                try {
                    pl.worlds().scheduleRebuild();
                    sender.sendMessage(Texts.chat(l, "admin.rebuild"));
                } catch (IOException e) {
                    sender.sendMessage(Component.text(e.getMessage()));
                }
            }
            case "pruefen" -> {
                List<String> out = new ArrayList<>();
                for (Course c : pl.model().courses()) {
                    FlightSim.Result r = FlightSim.fly(c.start.line(), c.start.launch(), c.rings, c.updrafts,
                            Pilots.aim(c), pl.model()::solid, 8000);
                    out.add(c.id + ": " + (r.finished() ? "ok " + r.finishMs() + " ms" : r.problem()));
                }
                sender.sendMessage(Component.text(String.join("\n", out)));
            }
            default -> sender.sendMessage(Component.text("/adleradmin " + String.join("|", SUBS)));
        }
        return true;
    }

    /** Eine Zeile je Spieler: name=… state=… (für Tests). */
    private void status(CommandSender sender) {
        List<String> lines = new ArrayList<>();
        lines.add("welt=" + (pl.worlds().ready() ? "bereit" : "baut") + " flieger=" + pl.flights().count()
                + " woche=" + (pl.weeklyCourse() == null ? "-" : pl.weeklyCourse().id));
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location at = p.getLocation();
            lines.add(String.format(Locale.ROOT, "spieler=%s lang=%s gliding=%s ground=%s pos=%.1f,%.1f,%.1f flight=[%s]",
                    p.getName(), pl.lang(p), p.isGliding(), p.isOnGround(), at.getX(), at.getY(), at.getZ(),
                    pl.flights().debug(p.getUniqueId())));
        }
        sender.sendMessage(Component.text(String.join("\n", lines)));
    }

    /** {@code /adleradmin fliege <spieler> [bis <ring>] [daneben <ring>]} – Ringe ab 1 gezählt. */
    private void guide(CommandSender sender, String l, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("/adleradmin fliege <spieler> [bis <ring>] [daneben <ring>]"));
            return;
        }
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) {
            sender.sendMessage(Texts.chat(l, "cmd.unknown"));
            return;
        }
        int until = -1;
        int miss = -1;
        for (int i = 2; i + 1 < args.length; i += 2) {
            try {
                int n = Integer.parseInt(args[i + 1]) - 1;
                if (args[i].equalsIgnoreCase("bis")) {
                    until = n;
                } else if (args[i].equalsIgnoreCase("daneben")) {
                    miss = n;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Zahl erwartet: " + args[i + 1]));
                return;
            }
        }
        boolean ok = pl.flights().guide(p, until, miss);
        sender.sendMessage(Component.text(ok ? "Autopilot für " + p.getName() + " an." : p.getName() + " fliegt keine Strecke."));
    }

    /** Strecke als JSON (für Tests und zum Nachsehen). */
    private static String json(Course c) {
        StringBuilder b = new StringBuilder();
        Vec s = c.start.stand();
        Vec line = c.start.line();
        Vec land = c.landing.at();
        b.append(String.format(Locale.ROOT, "{\"id\":\"%s\",\"name\":\"%s\",\"difficulty\":\"%s\",\"ms\":%d,"
                + "\"stand\":[%.2f,%.2f,%.2f],\"line\":[%.2f,%.2f,%.2f],\"yaw\":%.1f,\"landing\":[%.2f,%.2f,%.2f],"
                + "\"floor\":%.1f,\"maxOff\":%.1f,\"medals\":{\"adler\":%d,\"gold\":%d,\"silber\":%d,\"bronze\":%d},",
                c.id, c.nameDe, c.difficulty, c.designMs, s.x(), s.y(), s.z(), line.x(), line.y(), line.z(), c.start.yaw(),
                land.x(), land.y(), land.z(), c.floorY, c.maxOff, c.medals.adler(), c.medals.gold(), c.medals.silber(),
                c.medals.bronze()));
        b.append("\"rings\":[");
        for (Ring r : c.rings) {
            b.append(r.index() > 0 ? "," : "").append(String.format(Locale.ROOT,
                    "{\"i\":%d,\"kind\":\"%s\",\"c\":[%.2f,%.2f,%.2f],\"n\":[%.4f,%.4f,%.4f],\"r\":%.2f,\"tick\":%d}",
                    r.index(), r.kind(), r.center().x(), r.center().y(), r.center().z(), r.normal().x(), r.normal().y(),
                    r.normal().z(), r.radius(), c.tickBefore(r.index(), 0)));
        }
        b.append("],\"updrafts\":[");
        for (int i = 0; i < c.updrafts.size(); i++) {
            Updraft u = c.updrafts.get(i);
            b.append(i > 0 ? "," : "").append(String.format(Locale.ROOT, "{\"x\":%.1f,\"z\":%.1f,\"r\":%.1f,\"top\":%.1f}",
                    u.x(), u.z(), u.radius(), u.top()));
        }
        return b.append("],\"ticks\":").append(c.pathTicks()).append('}').toString();
    }

    private void reset(CommandSender sender, String l, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("/adleradmin reset <spieler> [strecke]"));
            return;
        }
        PlayerRecord r = pl.store().findByName(args[1]);
        if (r == null) {
            sender.sendMessage(Texts.chat(l, "cmd.unknown"));
            return;
        }
        String course = null;
        if (args.length > 2) {
            Optional<Course> c = pl.model().course(args[2]);
            if (c.isEmpty()) {
                sender.sendMessage(Texts.chat(l, "cmd.unknown"));
                return;
            }
            course = c.get().id;
        }
        pl.store().reset(r, course);
        pl.store().deleteGhosts(r.uuid, course);
        pl.flights().forgetGhosts(r.uuid);
        pl.boardsAll();
        sender.sendMessage(Texts.chat(l, "admin.reset", Texts.arg("player", r.name), Texts.arg("what",
                course == null ? "*" : course)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("adlerflug.admin")) {
            return out;
        }
        if (args.length == 1) {
            SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).forEach(out::add);
        } else if (args.length == 2 && List.of("fliege", "reset", "start").contains(args[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 2 && "strecke".equalsIgnoreCase(args[0])) {
            pl.model().courses().forEach(c -> out.add(c.id));
        }
        return out;
    }
}
