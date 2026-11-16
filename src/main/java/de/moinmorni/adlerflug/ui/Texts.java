package de.moinmorni.adlerflug.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Alle Texte von Adlerflug auf Deutsch (Standard) und Englisch, im MiniMessage-Format. Platzhalter wie {@code <time>}
 * werden beim Senden gefüllt ({@link #arg(String, Object)} für Text, sicher für Spielernamen; {@link #comp(String,
 * Component)} für fertige Bausteine).
 */
public final class Texts {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gradient:#f4a259:#ffe8b0><bold>Adlerflug</bold></gradient> <dark_gray>»</dark_gray> ";

    private static final Map<String, String[]> T = new LinkedHashMap<>();

    private Texts() {
    }

    private static void t(String key, String de, String en) {
        if (T.put(key, new String[] {de, en}) != null) {
            throw new IllegalStateException("Text doppelt: " + key);
        }
    }

    static {
        t("on", "an", "on");
        t("off", "aus", "off");

        // ---------- Begrüßung ----------
        t("welcome.title", "<gradient:#f4a259:#ffe8b0><bold>Adlerflug</bold></gradient>",
                "<gradient:#f4a259:#ffe8b0><bold>Eagle Flight</bold></gradient>");
        t("welcome.subtitle", "<gray>Mit Flügeln durch die Ringe – wähle eine Strecke</gray>",
                "<gray>Fly through the rings – pick a course</gray>");
        t("welcome.chat", "Servus im <white>Adlerhorst</white>! Mit der <gold>Flugkarte</gold> oder an den Wegweisern "
                + "wählst du eine Strecke, im <gold>Flugbuch</gold> stehen die Bestzeiten.",
                "Welcome to the <white>Eagle's Nest</white>! Use the <gold>flight map</gold> or the signposts to pick a "
                        + "course – the best times are in the <gold>flight book</gold>.");
        t("welcome.how", "<gray>So geht's: Lauf von der Schanze, die Flügel gehen von selbst auf. Mit der Maus lenkst "
                + "du (nach unten = schneller, nach oben = steigen). Flieg der Reihe nach durch alle Ringe – "
                + "<aqua>Aufwind-Säulen</aqua> heben dich an, <aqua>blaue Schub-Ringe</aqua> geben Schwung.</gray>",
                "<gray>How it works: run off the ramp, your wings open by themselves. Steer with the mouse (down = "
                        + "faster, up = climb). Fly through every ring in order – <aqua>updrafts</aqua> lift you, "
                        + "<aqua>blue boost rings</aqua> push you forward.</gray>");
        t("welcome.weekly", "<gray>Strecke der Woche:</gray> <course> <gray>– schaff sie bis Montag für das Abzeichen</gray> "
                + "<badge><gray>!</gray>", "<gray>Course of the week:</gray> <course> <gray>– finish it by Monday to earn "
                + "the</gray> <badge> <gray>badge!</gray>");
        t("badge", "<#f4a259>▲ Wochen-Adler</#f4a259>", "<#f4a259>▲ Eagle of the week</#f4a259>");
        t("lobby.kick", "<gold>MoinMorni</gold>\n<gray>Zurück in die Lobby: Bitte verbinde dich neu mit dem Netzwerk.</gray>",
                "<gold>MoinMorni</gold>\n<gray>Back to the lobby: please reconnect to the network.</gray>");
        t("free.back", "<gray>Gelandet – zurück im Adlerhorst. Eine Strecke wählst du mit der <gold>Flugkarte</gold>.</gray>",
                "<gray>Landed – back at the Eagle's Nest. Pick a course with the <gold>flight map</gold>.</gray>");

        // ---------- Gegenstände ----------
        t("item.wings", "<gold><bold>Adlerflügel</bold></gold>", "<gold><bold>Eagle wings</bold></gold>");
        t("item.wings.lore", "<gray>Gehen beim Absprung von selbst auf</gray>", "<gray>Open by themselves when you jump off</gray>");
        t("item.menu", "<gold><bold>Flugkarte</bold></gold> <gray>– Strecke wählen</gray>",
                "<gold><bold>Flight map</bold></gold> <gray>– pick a course</gray>");
        t("item.menu.lore", "<gray>Rechtsklick: alle Strecken</gray>", "<gray>Right-click: all courses</gray>");
        t("item.top", "<yellow><bold>Flugbuch</bold></yellow> <gray>– Bestenlisten</gray>",
                "<yellow><bold>Flight book</bold></yellow> <gray>– leaderboards</gray>");
        t("item.top.lore", "<gray>Rechtsklick: Bestzeiten aller Strecken</gray>",
                "<gray>Right-click: best times of every course</gray>");
        t("item.weekly", "<#f4a259><bold>Strecke der Woche</bold></#f4a259>", "<#f4a259><bold>Course of the week</bold></#f4a259>");
        t("item.weekly.lore", "<gray>Rechtsklick: <white><course></white> fliegen</gray>",
                "<gray>Right-click: fly <white><course></white></gray>");
        t("item.hide.shown", "<green>Mitspieler: sichtbar</green>", "<green>Other players: visible</green>");
        t("item.hide.hidden", "<gray>Mitspieler: ausgeblendet</gray>", "<gray>Other players: hidden</gray>");
        t("item.hide.lore", "<gray>Rechtsklick: umschalten</gray>", "<gray>Right-click: toggle</gray>");
        t("item.lobby", "<red><bold>Zurück ins Dorf</bold></red> <gray>(Lobby)</gray>",
                "<red><bold>Back to the village</bold></red> <gray>(lobby)</gray>");
        t("item.back", "<aqua><bold>Zurück zum letzten Ring</bold></aqua>", "<aqua><bold>Back to the last ring</bold></aqua>");
        t("item.back.lore", "<gray>Rechtsklick oder Taste F – die Zeit läuft weiter</gray>",
                "<gray>Right-click or key F – the clock keeps running</gray>");
        t("item.restart", "<green><bold>Neustart</bold></green> <gray>(zurück auf die Schanze)</gray>",
                "<green><bold>Restart</bold></green> <gray>(back to the ramp)</gray>");
        t("item.pick", "<light_purple><bold>Ab Ring …</bold></light_purple> <gray>(Übung)</gray>",
                "<light_purple><bold>From ring …</bold></light_purple> <gray>(practice)</gray>");
        t("item.pick.lore", "<gray>Rechtsklick: an einem Ring deiner Wahl einsteigen</gray>",
                "<gray>Right-click: start at a ring of your choice</gray>");
        t("item.giveup", "<red><bold>Aufgeben</bold></red>", "<red><bold>Give up</bold></red>");
        t("item.again", "<green><bold>Nochmal fliegen</bold></green>", "<green><bold>Fly again</bold></green>");
        t("item.base", "<gold><bold>Zum Adlerhorst</bold></gold>", "<gold><bold>To the Eagle's Nest</bold></gold>");
        t("item.race", "<yellow><bold>Beim Rennen mitmachen</bold></yellow>", "<yellow><bold>Join the race</bold></yellow>");
        t("item.race.lore", "<gray><leader> fliegt <white><course></white> – Rechtsklick zum Mitmachen</gray>",
                "<gray><leader> is flying <white><course></white> – right-click to join</gray>");

        // ---------- Flug ----------
        t("flight.sub", "<diff> <dark_gray>·</dark_gray> <gray><n> Ringe</gray>", "<diff> <dark_gray>·</dark_gray> <gray><n> rings</gray>");
        t("flight.welcome", "<course> <dark_gray>(</dark_gray><diff><dark_gray>)</dark_gray> – <white><n></white> Ringe. "
                + "Lauf von der Schanze, die Zeit startet beim Absprung!", "<course> <dark_gray>(</dark_gray><diff>"
                + "<dark_gray>)</dark_gray> – <white><n></white> rings. Run off the ramp, the clock starts when you jump!");
        t("flight.ready", "<yellow>Lauf von der Schanze – die Zeit startet beim Absprung</yellow>",
                "<yellow>Run off the ramp – the clock starts when you jump</yellow>");
        t("flight.go", "<green><bold>Los!</bold></green> <gray>Flügel auf – zum ersten Ring!</gray>",
                "<green><bold>Go!</bold></green> <gray>Wings out – to the first ring!</gray>");
        t("flight.ring", "<gold>Ring <n>/<total></gold> <white><time></white><delta>",
                "<gold>Ring <n>/<total></gold> <white><time></white><delta>");
        t("flight.boost", "<aqua>Schub!</aqua> <gray>Der Schub-Ring schiebt dich nach vorn.</gray>",
                "<aqua>Boost!</aqua> <gray>The boost ring pushes you forward.</gray>");
        t("flight.back.missed", "<red>Ring <n> verpasst</red> <gray>– zurück zum letzten Ring.</gray>",
                "<red>Missed ring <n></red> <gray>– back to the last ring.</gray>");
        t("flight.back.off", "<red>Zu weit von der Strecke weg</red> <gray>– zurück zum letzten Ring.</gray>",
                "<red>Too far off the course</red> <gray>– back to the last ring.</gray>");
        t("flight.back.fell", "<red>Abgestürzt</red> <gray>– zurück zum letzten Ring.</gray>",
                "<red>Crashed</red> <gray>– back to the last ring.</gray>");
        t("flight.back.landed", "<red>Gelandet</red> <gray>– zurück zum letzten Ring.</gray>",
                "<red>Landed</red> <gray>– back to the last ring.</gray>");
        t("flight.back.water", "<red>Ins Wasser gefallen</red> <gray>– zurück zum letzten Ring.</gray>",
                "<red>Splashdown</red> <gray>– back to the last ring.</gray>");
        t("flight.back.self", "<gray>Zurück zum letzten Ring.</gray>", "<gray>Back to the last ring.</gray>");
        t("flight.back.ring", "<aqua>Weiter ab Ring <n></aqua>", "<aqua>Carry on from ring <n></aqua>");
        t("flight.back.start", "<aqua>Zurück auf der Schanze</aqua>", "<aqua>Back on the ramp</aqua>");
        t("flight.back.short", "<aqua>↺ zurück</aqua>", "<aqua>↺ back</aqua>");
        t("flight.restart", "<green>Neustart – zurück auf der Schanze</green>", "<green>Restart – back on the ramp</green>");
        t("flight.giveup", "<gray>Flug abgebrochen. Bis zum nächsten Mal!</gray>", "<gray>Flight cancelled. See you next time!</gray>");
        t("hud.ring", "<gold>Ring <n>/<total></gold>", "<gold>Ring <n>/<total></gold>");
        t("hud.practice", "<light_purple>Übung</light_purple>", "<light_purple>practice</light_purple>");

        // ---------- Ziel ----------
        t("finish.title", "<gradient:#f4a259:#ffe8b0><bold>Ziel!</bold></gradient>",
                "<gradient:#f4a259:#ffe8b0><bold>Finish!</bold></gradient>");
        t("finish.sub", "<white><time></white> <medal>", "<white><time></white> <medal>");
        t("finish.first", "Erster Flug auf <course>: <white><time></white>!",
                "First flight on <course>: <white><time></white>!");
        t("finish.pb", "<green>Neue Bestzeit!</green> <white><time></white> <delta> auf <course>",
                "<green>New personal best!</green> <white><time></white> <delta> on <course>");
        t("finish.nopb", "<white><time></white> <delta> <gray>– deine Bestzeit bleibt <white><best></white></gray>",
                "<white><time></white> <delta> <gray>– your best stays <white><best></white></gray>");
        t("finish.medal", "Neue Medaille: <medal>!", "New medal: <medal>!");
        t("finish.eagle.all", "<gold><player></gold> <gray>hat auf</gray> <course> <gray>die</gray> "
                + "<#f4a259>▲ Adler-Medaille</#f4a259> <gray>erflogen!</gray>", "<gold><player></gold> <gray>earned the</gray> "
                + "<#f4a259>▲ Eagle medal</#f4a259> <gray>on</gray> <course><gray>!</gray>");
        t("finish.next", "<gray>Bis</gray> <medal> <gray>fehlen noch <white><diff></white> s.</gray>",
                "<gray><white><diff></white> s to go for</gray> <medal><gray>.</gray>");
        t("finish.rank", "<gray>Platz <white><rank></white> von <white><total></white> auf</gray> <course>",
                "<gray>Rank <white><rank></white> of <white><total></white> on</gray> <course>");
        t("finish.record", "<gold><bold>Streckenrekord!</bold></gold> <white><player></white> <gray>fliegt</gray> <course> "
                + "<gray>in</gray> <white><time></white>", "<gold><bold>Course record!</bold></gold> <white><player></white> "
                + "<gray>flies</gray> <course> <gray>in</gray> <white><time></white>");
        t("finish.weekly", "<#f4a259>Strecke der Woche:</#f4a259> <gray>Platz <white><rank></white></gray>",
                "<#f4a259>Course of the week:</#f4a259> <gray>rank <white><rank></white></gray>");
        t("finish.practice", "<light_purple>Übungsflug:</light_purple> <white><time></white> <gray>(zählt nicht)</gray>",
                "<light_purple>Practice flight:</light_purple> <white><time></white> <gray>(not counted)</gray>");
        t("finish.toofast", "<red>Zu schnell, um wahr zu sein</red> <gray>(<time>) – nicht gewertet.</gray>",
                "<red>Too fast to be true</red> <gray>(<time>) – not counted.</gray>");
        t("weekly.badge", "Du hast das Abzeichen <#f4a259>▲ Wochen-Adler</#f4a259> – bis Montag neben deinem Namen!",
                "You earned the <#f4a259>▲ Eagle of the week</#f4a259> badge – next to your name until Monday!");
        t("weekly.broadcast", "<white><player></white> <gray>ist</gray> <#f4a259>▲ Wochen-Adler</#f4a259><gray>!</gray>",
                "<white><player></white> <gray>is an</gray> <#f4a259>▲ Eagle of the week</#f4a259><gray>!</gray>");

        // ---------- Übung, Geist, Mitspieler ----------
        t("practice.on", "<light_purple>Übungsmodus an</light_purple> <gray>– Zeiten zählen nicht, mit „Ab Ring …“ "
                + "steigst du an jedem Ring ein.</gray>", "<light_purple>Practice mode on</light_purple> <gray>– times "
                + "don't count; with “From ring …” you can start at any ring.</gray>");
        t("practice.off", "<gray>Übungsmodus aus – Zeiten zählen wieder.</gray>",
                "<gray>Practice mode off – times count again.</gray>");
        t("practice.from", "<light_purple>Übung:</light_purple> <gray>Einstieg vor Ring <white><n></white> auf</gray> <course>",
                "<light_purple>Practice:</light_purple> <gray>starting before ring <white><n></white> on</gray> <course>");
        t("ghost.on", "<aqua>Geist an</aqua> <gray>– ein kleiner Adler aus Funken fliegt deine Bestzeit mit.</gray>",
                "<aqua>Ghost on</aqua> <gray>– a little eagle of sparks flies your best time with you.</gray>");
        t("ghost.off", "<gray>Geist aus.</gray>", "<gray>Ghost off.</gray>");
        t("hide.hidden", "<gray>Mitspieler ausgeblendet.</gray>", "<gray>Other players hidden.</gray>");
        t("hide.shown", "<gray>Mitspieler wieder sichtbar.</gray>", "<gray>Other players visible again.</gray>");

        // ---------- Party-Rennen ----------
        t("race.invite", "<yellow><leader></yellow> <gray>startet ein Rennen auf</gray> <course><gray>! Mach in "
                + "<white><sec></white> s mit (Feuerwerksstern oder <white>/adlerflug mitmachen</white>).</gray>",
                "<yellow><leader></yellow> <gray>is starting a race on</gray> <course><gray>! Join within "
                        + "<white><sec></white> s (firework star or <white>/adlerflug mitmachen</white>).</gray>");
        t("race.wait", "<gray>Warte auf die Party … noch <white><sec></white> s</gray>",
                "<gray>Waiting for your party … <white><sec></white> s left</gray>");
        t("race.joined", "<white><player></white> <gray>fliegt mit!</gray>", "<white><player></white> <gray>joins the race!</gray>");
        t("race.none", "<gray>Gerade gibt es kein Rennen zum Mitmachen.</gray>", "<gray>There's no race to join right now.</gray>");
        t("race.solo", "<gray>Niemand ist mitgekommen – du fliegst allein.</gray>", "<gray>Nobody joined – you fly alone.</gray>");
        t("race.go", "<green><bold>LOS!</bold></green>", "<green><bold>GO!</bold></green>");
        t("race.finish", "<white><player></white> <gray>ist im Ziel:</gray> <white><time></white> <gray>(Platz "
                + "<white><place></white>)</gray>", "<white><player></white> <gray>finished:</gray> <white><time></white> "
                + "<gray>(place <white><place></white>)</gray>");
        t("race.over", "<gold>Rennen vorbei!</gold> <gray>Sieger:</gray> <white><player></white>",
                "<gold>Race over!</gold> <gray>Winner:</gray> <white><player></white>");

        // ---------- Menüs ----------
        t("menu.courses", "Adlerflug – Strecken", "Eagle Flight – courses");
        t("menu.rings", "<gray><n> Ringe</gray>", "<gray><n> rings</gray>");
        t("menu.best", "<gray>Deine Bestzeit:</gray> <white><time></white> <medal>",
                "<gray>Your best:</gray> <white><time></white> <medal>");
        t("menu.best.none", "<gray>Noch nicht geflogen</gray>", "<gray>Not flown yet</gray>");
        t("menu.rank", "<gray>Platz <white><rank></white> von <white><total></white></gray>",
                "<gray>Rank <white><rank></white> of <white><total></white></gray>");
        t("menu.medals", "<gray>Medaillen:</gray> <list>", "<gray>Medals:</gray> <list>");
        t("menu.record", "<gray>Rekord:</gray> <white><time></white> <gray>von <player></gray>",
                "<gray>Record:</gray> <white><time></white> <gray>by <player></gray>");
        t("menu.record.none", "<gray>Noch kein Rekord – sei der Erste!</gray>", "<gray>No record yet – be the first!</gray>");
        t("menu.weeklytag", "<#f4a259>★ Strecke der Woche</#f4a259>", "<#f4a259>★ Course of the week</#f4a259>");
        t("menu.weekly.left", "<gray>noch <white><left></white></gray>", "<gray><white><left></white> left</gray>");
        t("menu.click.start", "<green>Linksklick: losfliegen</green>", "<green>Left-click: fly</green>");
        t("menu.click.top", "<yellow>Rechtsklick: Bestenliste</yellow>", "<yellow>Right-click: leaderboard</yellow>");
        t("menu.click.open", "<yellow>Klick: Bestenliste</yellow>", "<yellow>Click: leaderboard</yellow>");
        t("menu.practice", "<light_purple>Übungsmodus: <state></light_purple>", "<light_purple>Practice mode: <state></light_purple>");
        t("menu.practice.lore", "<gray>Zeiten zählen nicht, Einstieg an jedem Ring</gray>",
                "<gray>Times don't count, start at any ring</gray>");
        t("menu.ghost", "<aqua>Geist: <state></aqua>", "<aqua>Ghost: <state></aqua>");
        t("menu.ghost.lore", "<gray>Ein Adler aus Funken fliegt deine Bestzeit mit</gray>",
                "<gray>An eagle of sparks flies your best time with you</gray>");
        t("menu.close", "<red>Schließen</red>", "<red>Close</red>");
        t("menu.book", "Flugbuch", "Flight book");
        t("menu.stats", "<gold>Deine Flüge</gold>", "<gold>Your flights</gold>");
        t("menu.top", "Bestenliste: <course>", "Leaderboard: <course>");
        t("menu.weekly", "Strecke der Woche", "Course of the week");
        t("menu.entry", "<gold><rank>.</gold> <white><player></white>", "<gold><rank>.</gold> <white><player></white>");
        t("menu.entry.time", "<white><time></white> <medal>", "<white><time></white> <medal>");
        t("menu.entry.when", "<gray>am <date></gray>", "<gray>on <date></gray>");
        t("menu.you", "<green>Du</green>", "<green>You</green>");
        t("menu.back", "<gray>Zurück</gray>", "<gray>Back</gray>");
        t("menu.pick", "Übung: <course>", "Practice: <course>");
        t("menu.pick.start", "<green>Von der Schanze</green>", "<green>From the ramp</green>");
        t("menu.pick.ring", "<gold>Ab Ring <n></gold>", "<gold>From ring <n></gold>");
        t("menu.pick.normal", "<gray>Einstieg kurz davor in der Luft</gray>", "<gray>Start just before it, in the air</gray>");
        t("menu.pick.boost", "<aqua>Schub-Ring</aqua> <gray>– Einstieg kurz davor</gray>",
                "<aqua>Boost ring</aqua> <gray>– start just before it</gray>");
        t("top.empty", "<gray>Noch keine Zeiten – flieg als Erster!</gray>", "<gray>No times yet – fly first!</gray>");
        t("top.head", "Bestenliste <course>", "Leaderboard <course>");
        t("top.line", "<gold><rank>.</gold> <white><player></white> <gray>–</gray> <white><time></white> <medal>",
                "<gold><rank>.</gold> <white><player></white> <gray>–</gray> <white><time></white> <medal>");
        t("top.you", "<green>Du: Platz <rank> mit <time></green>", "<green>You: rank <rank> with <time></green>");
        t("week.head", "Strecke der Woche: <course> <gray>(noch <left>)</gray>",
                "Course of the week: <course> <gray>(<left> left)</gray>");

        // ---------- Statistik ----------
        t("stats.head", "Flugbuch von <white><player></white>", "Flight book of <white><player></white>");
        t("stats.total", "<gray>Im Ziel:</gray> <white><finishes></white> <gray>· Flüge:</gray> <white><flights></white> "
                + "<gray>· Ringe:</gray> <white><rings></white>", "<gray>Finished:</gray> <white><finishes></white> "
                + "<gray>· flights:</gray> <white><flights></white> <gray>· rings:</gray> <white><rings></white>");
        t("stats.medals", "<gray>Medaillen:</gray> <medals>", "<gray>Medals:</gray> <medals>");
        t("stats.badge", "<gray>Diese Woche:</gray> <badge>", "<gray>This week:</gray> <badge>");
        t("stats.course", "<course><gray>:</gray> <white><time></white> <medal> <gray>(Platz <rank>)</gray>",
                "<course><gray>:</gray> <white><time></white> <medal> <gray>(rank <rank>)</gray>");
        t("stats.course.none", "<course><gray>: noch nicht geflogen</gray>", "<course><gray>: not flown yet</gray>");

        // ---------- Tafeln ----------
        t("board.hello", "<gradient:#f4a259:#ffe8b0><bold>Adlerhorst</bold></gradient>\n<gray>Wähle eine Strecke am "
                + "Wegweiser oder mit der Flugkarte.</gray>\n<gray>Oben auf dem Turm: Freiflug-Schanze!</gray>",
                "<gradient:#f4a259:#ffe8b0><bold>Eagle's Nest</bold></gradient>\n<gray>Pick a course at a signpost or "
                        + "with the flight map.</gray>\n<gray>Up the tower: free-flight ramp!</gray>");
        t("board.jump", "<aqua><bold>Freiflug-Schanze</bold></aqua>\n<gray>Ohne Zeit – einfach losfliegen!</gray>",
                "<aqua><bold>Free-flight ramp</bold></aqua>\n<gray>No timer – just fly!</gray>");
        t("board.click", "<yellow>» Schild anklicken zum Losfliegen «</yellow>", "<yellow>» Click the sign to fly «</yellow>");
        t("board.empty", "<gray>Noch keine Zeiten</gray>", "<gray>No times yet</gray>");
        t("board.finish", "<white><bold>Ziel</bold></white>", "<white><bold>Finish</bold></white>");
        t("board.updraft", "<aqua>↑ Aufwind ↑</aqua>", "<aqua>↑ Updraft ↑</aqua>");
        t("board.landing", "<gray>Landeplatz – Bestenliste</gray>", "<gray>Landing pad – leaderboard</gray>");
        t("board.start", "<gray>Start:</gray> <course>\n<diff>\n<gray>Lauf los – die Zeit startet an der Kante</gray>",
                "<gray>Start:</gray> <course>\n<diff>\n<gray>Run – the clock starts at the edge</gray>");
        t("board.weekly", "<#f4a259><bold>★ Strecke der Woche ★</bold></#f4a259>",
                "<#f4a259><bold>★ Course of the week ★</bold></#f4a259>");
        t("board.weekly.left", "<gray>noch <white><left></white> · Abzeichen:</gray> <badge>",
                "<gray><white><left></white> left · badge:</gray> <badge>");
        t("board.weekly.done", "<gray>Schon geschafft: <white><n></white></gray>", "<gray>Done so far: <white><n></white></gray>");

        // ---------- Befehle ----------
        t("cmd.help", "<gray>Befehle: <white>/adlerflug</white> (Strecken), <white>start \\<strecke></white>, "
                + "<white>top \\<strecke></white>, <white>stats</white>, <white>woche</white>, <white>uebung</white>, "
                + "<white>geist</white>, <white>verstecken</white>, <white>ring</white>, <white>neustart</white>, "
                + "<white>aufgeben</white>, <white>basislager</white>, <white>mitmachen</white>, <white>lobby</white></gray>",
                "<gray>Commands: <white>/adlerflug</white> (courses), <white>start \\<strecke></white>, "
                        + "<white>top \\<strecke></white>, <white>stats</white>, <white>woche</white> (week), "
                        + "<white>uebung</white> (practice), <white>geist</white> (ghost), <white>verstecken</white> "
                        + "(hide), <white>ring</white> (back), <white>neustart</white>, <white>aufgeben</white>, "
                        + "<white>basislager</white>, <white>mitmachen</white> (join race), <white>lobby</white></gray>");
        t("cmd.notready", "<gray>Die Berge werden gerade aufgebaut – gleich geht's los!</gray>",
                "<gray>The mountains are being built – just a moment!</gray>");
        t("cmd.player", "<gray>Das geht nur im Spiel.</gray>", "<gray>Only possible in-game.</gray>");
        t("cmd.perm", "<red>Dafür fehlt dir das Recht.</red>", "<red>You don't have permission for that.</red>");
        t("cmd.unknown", "<gray>Niemanden gefunden.</gray>", "<gray>Nobody found.</gray>");
        t("cmd.course", "<gray>Welche Strecke? <white><list></white></gray>", "<gray>Which course? <white><list></white></gray>");
        t("cmd.notflying", "<gray>Du fliegst gerade keine Strecke.</gray>", "<gray>You're not flying a course right now.</gray>");
        t("admin.rebuild", "<gray>Die Welt entsteht beim nächsten Neustart neu.</gray>",
                "<gray>The world will be rebuilt on the next restart.</gray>");
        t("admin.reset", "<gray>Zeiten von <white><player></white> gelöscht (<what>).</gray>",
                "<gray>Times of <white><player></white> deleted (<what>).</gray>");

        // ---------- Zeit ----------
        t("time.days", "<d> Tagen <h> Std.", "<d> days <h> h");
        t("time.hours", "<h> Std. <m> Min.", "<h> h <m> min");
    }

    /** Text in der Sprache (de/en) mit Platzhaltern. Fehlt ein Schlüssel, steht er selbst da. */
    public static Component get(String lang, String key, TagResolver... args) {
        return MM.deserialize(raw(lang, key), args);
    }

    /** Mit Vorsilbe „Adlerflug »“ für den Chat. */
    public static Component chat(String lang, String key, TagResolver... args) {
        return MM.deserialize(PREFIX + raw(lang, key), args);
    }

    public static String raw(String lang, String key) {
        String[] v = T.get(key);
        if (v == null) {
            return key;
        }
        return "en".equals(lang) ? v[1] : v[0];
    }

    public static boolean has(String key) {
        return T.containsKey(key);
    }

    /** Platzhalter mit Text (ohne Formatierung, sicher für Spielernamen). */
    public static TagResolver arg(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Platzhalter mit fertigem Baustein. */
    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }

    /** Alle Schlüssel (für den Test, dass jede Sprache jeden Text hat). */
    public static Set<String> keys() {
        return T.keySet();
    }
}
