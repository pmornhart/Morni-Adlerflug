package de.moinmorni.adlerflug.game;

import de.moinmorni.adlerflug.AdlerflugPlugin;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.courses.Basecamp;
import de.moinmorni.adlerflug.courses.Daily;
import de.moinmorni.adlerflug.flight.Ring;
import de.moinmorni.adlerflug.flight.Updraft;
import de.moinmorni.adlerflug.flight.Vec;
import de.moinmorni.adlerflug.flight.Wind;
import de.moinmorni.adlerflug.stats.Leaderboard;
import de.moinmorni.adlerflug.time.TimeFormat;
import de.moinmorni.adlerflug.ui.Texts;
import de.moinmorni.adlerflug.world.Holograms;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Tafeln: im Basislager je Strecke die Bestenliste über der Station (auch für die Strecke des Tages mit ihren
 * Bedingungen), „Strecke der Woche“ und die Begrüßung; auf den Strecken der Name über der Schanze, Nummern an den
 * Ringen, Gutschrift an den Bonus-Ringen, „Ziel“, „Aufwind“, „Wind“ und die Bestenliste am Landeplatz.
 */
public final class Boards {

    private static final int TOP = 10;

    private final AdlerflugPlugin pl;

    public Boards(AdlerflugPlugin pl) {
        this.pl = pl;
    }

    /** Alles anmelden (erscheint, sobald der Chunk geladen ist). */
    public void register(Holograms h) {
        Basecamp camp = pl.model().basecamp();
        for (Basecamp.Station s : camp.stations()) {
            String id = s.course();
            h.text("board:" + id, s.boardX(), s.boardY(), s.boardZ(), l -> station(id, l), true, 0.7f);
        }
        double[] w = camp.weeklyBoard();
        h.text("weekly", w[0], w[1], w[2], this::weekly, true, 0.8f);
        double[] hello = camp.welcomeBoard();
        h.text("hello", hello[0], hello[1], hello[2], l -> Texts.get(l, "board.hello"), true, 1.0f);
        double[] jump = camp.jumpBoard();
        h.text("jump", jump[0], jump[1], jump[2], l -> Texts.get(l, "board.jump"), true, 0.9f);
        for (Course c : pl.model().courses()) {
            registerCourse(h, c);
        }
    }

    /**
     * Tafeln einer Strecke: Name über der Schanze, Nummern an den Ringen (↔ gleitet, ↻ kreist, ▮▯ Takt-Tor),
     * Gutschrift an den Bonus-Ringen, „Ziel“, „Aufwind“, „Wind“ und die Bestenliste am Landeplatz.
     */
    public void registerCourse(Holograms h, Course c) {
        Vec line = c.start.line();
        h.text("start:" + c.id, line.x(), line.y() + 6.6, line.z(), l -> startLabel(c, l), true, 0.8f);
        for (Ring r : c.rings) {
            double reach = r.motion() == null ? 0 : r.motion().reach();
            Vec top = r.center().add(0, r.radius() + 2.4 + reach + (r.gate() != null ? 1.2 : 0), 0);
            if (r.finish()) {
                h.text("ring:" + c.id + ":" + r.index(), top.x(), top.y(), top.z(),
                        l -> Texts.get(l, "board.finish"), true, 1.6f);
            } else {
                int n = r.index() + 1;
                TextColor col = Flights.ringColour(r);
                String mark = r.kind() == Ring.Kind.BOOST ? " »" : r.gate() != null ? " ▮▯"
                        : r.motion() == null ? "" : r.motion().circles() ? " ↻" : " ↔";
                h.text("ring:" + c.id + ":" + r.index(), top.x(), top.y(), top.z(),
                        l -> Component.text(n + mark, col).decorate(TextDecoration.BOLD), false, 2.0f);
            }
        }
        for (int i = 0; i < c.bonus.size(); i++) {
            Ring b = c.bonus.get(i);
            Vec top = b.center().add(0, b.radius() + 2.2, 0);
            h.text("bonus:" + c.id + ":" + i, top.x(), top.y(), top.z(), l -> Texts.get(l, "board.bonus",
                    Texts.arg("time", TimeFormat.seconds(b.credit(), l))), true, 1.6f);
        }
        for (Updraft u : c.updrafts) {
            int ground = pl.model().terrain().height(u.x(), u.z());
            h.text("lift:" + c.id + ":" + (int) u.x(), u.x(), ground + 4.5, u.z(), l -> Texts.get(l, "board.updraft"),
                    true, 1.2f);
        }
        for (int i = 0; i < c.winds.size(); i++) {
            Wind w = c.winds.get(i);
            Vec at = windEntry(c, w);
            if (at != null) {
                h.text("wind:" + c.id + ":" + i, at.x(), at.y() + 7, at.z(), l -> Texts.get(l, "board.wind"), true, 1.4f);
            }
        }
        Vec land = c.landing.at();
        h.text("landing:" + c.id, land.x(), land.y() + 3.2, land.z(), l -> landing(c, l), true, 0.7f);
    }

    /** Alle Tafeln einer Strecke weg (alte Strecke des Tages). */
    public void unregisterCourse(Holograms h, String id) {
        h.remove(x -> x.equals("start:" + id) || x.equals("landing:" + id) || x.startsWith("ring:" + id + ":")
                || x.startsWith("bonus:" + id + ":") || x.startsWith("lift:" + id + ":") || x.startsWith("wind:" + id + ":"));
    }

    /** Wo der Musterflug in eine Windzone hineinfliegt (oder null). */
    private static Vec windEntry(Course c, Wind w) {
        for (int t = 0; t < c.pathTicks(); t++) {
            Vec p = c.pathAt(t);
            if (w.contains(Course.center(p))) {
                return p;
            }
        }
        return null;
    }

    /** Station im Adlerhorst (auch die Strecke des Tages, die erst nach dem Start fertig gerechnet ist). */
    private Component station(String id, String l) {
        if (Daily.ID.equals(id)) {
            return dailyStation(l);
        }
        return pl.course(id).map(c -> station(c, l)).orElse(Component.empty());
    }

    private Component dailyStation(String l) {
        Daily.Today d = pl.daily();
        Component t = Texts.get(l, "board.daily").appendNewline().append(pl.dailyText(l));
        if (d == null) {
            return t;
        }
        Course c = d.course();
        t = t.appendNewline().append(Component.text("───────────────", NamedTextColor.DARK_GRAY));
        return t.appendNewline().append(lines(pl.store().board(c.id), c, l)).appendNewline()
                .append(Texts.get(l, "board.click"));
    }

    private Component station(Course c, String l) {
        Leaderboard board = pl.store().board(c.id);
        Component t = Flights.courseName(c, l).decorate(TextDecoration.BOLD);
        if (c.id.equals(pl.store().weekCourse())) {
            t = Component.text("★ ", TextColor.fromHexString("#f4a259")).append(t);
        }
        t = t.appendNewline().append(Flights.difficulty(c, l)).append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Texts.get(l, "menu.rings", Texts.arg("n", c.ringCount())));
        t = t.appendNewline().append(Component.text("───────────────", NamedTextColor.DARK_GRAY));
        return t.appendNewline().append(lines(board, c, l)).appendNewline().append(Texts.get(l, "board.click"));
    }

    private Component landing(Course c, String l) {
        return Flights.courseName(c, l).decorate(TextDecoration.BOLD).appendNewline()
                .append(Texts.get(l, "board.landing")).appendNewline()
                .append(Component.text("───────────────", NamedTextColor.DARK_GRAY)).appendNewline()
                .append(lines(pl.store().board(c.id), c, l));
    }

    private Component weekly(String l) {
        Course w = pl.weeklyCourse();
        if (w == null) {
            return Component.empty();
        }
        return Texts.get(l, "board.weekly").appendNewline()
                .append(Flights.courseName(w, l).decorate(TextDecoration.BOLD)).append(Component.text(" · "))
                .append(Flights.difficulty(w, l)).appendNewline()
                .append(Texts.get(l, "board.weekly.left", Texts.arg("left", pl.weekLeft(l)),
                        Texts.comp("badge", Texts.get(l, "badge")))).appendNewline()
                .append(Component.text("───────────────", NamedTextColor.DARK_GRAY)).appendNewline()
                .append(lines(pl.store().weekly(), w, l)).appendNewline()
                .append(Texts.get(l, "board.weekly.done", Texts.arg("n", pl.store().weeklyDone())));
    }

    private Component lines(Leaderboard board, Course c, String l) {
        List<Leaderboard.Entry> top = board.top(TOP);
        if (top.isEmpty()) {
            return Texts.get(l, "board.empty");
        }
        Component out = Component.empty();
        for (int i = 0; i < top.size(); i++) {
            Leaderboard.Entry e = top.get(i);
            TextColor rank = i == 0 ? TextColor.fromHexString("#ffd23f") : i == 1 ? TextColor.fromHexString("#c9d3dc")
                    : i == 2 ? TextColor.fromHexString("#d08a52") : NamedTextColor.GRAY;
            if (i > 0) {
                out = out.appendNewline();
            }
            out = out.append(Component.text((i + 1) + ". ", rank)).append(Component.text(e.name(), NamedTextColor.WHITE))
                    .append(Component.text("  " + TimeFormat.clock(e.ms(), l) + " ", NamedTextColor.AQUA))
                    .append(symbol(c, e.ms()));
        }
        return out;
    }

    private static Component symbol(Course c, long ms) {
        var m = c.medal(ms);
        return m == null ? Component.empty() : Component.text(m.symbol, TextColor.fromHexString(m.colour));
    }

    private Component startLabel(Course c, String l) {
        Component t = Texts.get(l, "board.start", Texts.comp("course", Flights.courseName(c, l).decorate(TextDecoration.BOLD)),
                Texts.comp("diff", Flights.difficulty(c, l)));
        if (c.id.equals(pl.store().weekCourse())) {
            t = Texts.get(l, "menu.weeklytag").appendNewline().append(t);
        }
        if (c.daily()) {
            t = t.appendNewline().append(pl.dailyText(l));
        } else if (!Flights.extrasText(c, l).isEmpty()) {
            t = t.appendNewline().append(Flights.extras(c, l));
        }
        return t;
    }
}
