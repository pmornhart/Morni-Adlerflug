package de.moinmorni.adlerflug.net;

import de.moinmorni.adlerflug.ui.Texts;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Adlerflug ohne Netzwerk: Sprache aus dem Spiel, keine Party, Statistik nur lokal, „Lobby“ = freundlich verabschieden. */
final class Standalone implements Network {

    static final Component KICK = Texts.get("de", "lobby.kick");

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public String language(Player player) {
        Locale l = player.locale();
        return l != null && "de".equals(l.getLanguage()) ? "de" : (l == null ? "de" : "en");
    }

    @Override
    public void stats(UUID player, Map<String, Long> add, Map<String, Long> best) {
        // ohne Netzwerk nur lokal
    }

    @Override
    public void state(String state, int players) {
        // niemand fragt
    }

    @Override
    public void toLobby(Player player) {
        player.kick(Texts.get(language(player), "lobby.kick"));
    }

    @Override
    public void askToLobby(Player player) {
        toLobby(player);
    }

    @Override
    public void playing(Player player, boolean playing) {
        // ohne Netzwerk fragt niemand nach
    }

    @Override
    public CompletableFuture<Optional<Party>> party(UUID player) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
