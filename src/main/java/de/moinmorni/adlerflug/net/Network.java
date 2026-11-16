package de.moinmorni.adlerflug.net;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Verbindung zum MoinMorni-Netzwerk. Läuft das Netzwerk-Plugin („MoinMorni“), geht alles über dessen Schnittstelle
 * ({@code de.moinmorni.api.MoinMorni}); sonst läuft Adlerflug allein: Sprache aus dem Spiel des Spielers, keine
 * Netzwerk-Statistik, „zur Lobby“ trennt die Verbindung mit einem Hinweis.
 *
 * <p>Nur {@link Connected} kennt die Netzwerk-Klassen – ohne Netzwerk-Plugin wird sie nie geladen.
 */
public interface Network {

    /** Name dieses Modus im Netzwerk (Statistiken, Zustand). */
    String MODE = "adlerflug";

    /** Läuft das Netzwerk-Plugin? */
    boolean connected();

    /** „de“ oder „en“. */
    String language(Player player);

    /** Statistiken melden: {@code add} hochzählen, {@code best} als Bestwert (kleiner ist besser – Zeiten). */
    void stats(UUID player, Map<String, Long> add, Map<String, Long> best);

    /** Zustand für Lobby-Figur und Webseite: „open“ mit Spielerzahl. */
    void state(String state, int players);

    /** Zurück in die Lobby (ohne Netzwerk: Verbindung trennen mit Hinweis). */
    void toLobby(Player player);

    /** Party des Spielers (leer ohne Party oder ohne Netzwerk). Kommt auf irgendeinem Thread zurück. */
    CompletableFuture<Optional<Party>> party(UUID player);

    /** Party: Anführer und Mitglieder. */
    record Party(UUID leader, List<UUID> members) {
    }

    /** Netzwerk suchen: mit Plugin „MoinMorni“ die echte Verbindung, sonst allein. */
    static Network connect(Plugin plugin) {
        Logger log = plugin.getLogger();
        if (plugin.getServer().getPluginManager().getPlugin("MoinMorni") != null) {
            try {
                Network n = Connected.create();
                if (n != null) {
                    log.info("Mit dem MoinMorni-Netzwerk verbunden.");
                    return n;
                }
                log.warning("Netzwerk-Plugin gefunden, aber ohne Schnittstelle für Spielmodi – Adlerflug läuft allein.");
            } catch (LinkageError | RuntimeException e) {
                log.warning("Netzwerk-Schnittstelle passt nicht (" + e + ") – Adlerflug läuft allein.");
            }
        } else {
            log.info("Kein MoinMorni-Netzwerk gefunden – Adlerflug läuft allein (Statistik nur lokal).");
        }
        return new Standalone();
    }
}
