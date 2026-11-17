package de.moinmorni.adlerflug.net;

import de.moinmorni.api.MoinMorni;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Adlerflug im Netzwerk: alles über die Schnittstelle des Netzwerk-Plugins. Die Schnittstelle wird bei jedem Aufruf neu
 * geholt – lädt jemand das Netzwerk-Plugin neu, stimmt sie trotzdem.
 *
 * <p>Nachfrage vor der Lobby und „fliegt gerade“ gibt es erst ab Version 2 der Schnittstelle. Ein älteres
 * Netzwerk-Plugin kennt sie nicht ({@link LinkageError}): dann geht „zur Lobby“ sofort, der Rest entfällt – alles
 * andere läuft wie bisher.
 */
final class Connected implements Network {

    private final Logger log;
    private volatile boolean olderNetwork;

    private Connected(Logger log) {
        this.log = log;
    }

    /** null, wenn das Netzwerk-Plugin keine Schnittstelle anbietet. */
    static Network create(Logger log) {
        return MoinMorni.get().isPresent() ? new Connected(log) : null;
    }

    private static Optional<MoinMorni> api() {
        return MoinMorni.get();
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public String language(Player player) {
        String lang = api().map(a -> a.language(player.getUniqueId())).orElse("de");
        return "en".equals(lang) ? "en" : "de";
    }

    @Override
    public void stats(UUID player, Map<String, Long> add, Map<String, Long> best) {
        // Zeiten: kleiner ist besser. Zähler und Bestwerte getrennt melden, damit „lowerIsBetter“ nur Zeiten betrifft.
        api().ifPresent(a -> a.stats(MODE, player, add, best, true));
    }

    @Override
    public void state(String state, int players) {
        api().ifPresent(a -> a.state(MODE, state, players));
    }

    @Override
    public void toLobby(Player player) {
        api().ifPresentOrElse(a -> a.toLobby(player), () -> player.kick(Standalone.KICK));
    }

    @Override
    public void askToLobby(Player player) {
        Optional<MoinMorni> api = api();
        if (api.isEmpty()) {
            player.kick(Standalone.KICK);
            return;
        }
        if (!olderNetwork) {
            try {
                api.get().askToLobby(player);
                return;
            } catch (LinkageError e) {
                older(e);
            }
        }
        api.get().toLobby(player);
    }

    @Override
    public void playing(Player player, boolean playing) {
        if (olderNetwork) {
            return;
        }
        api().ifPresent(a -> {
            try {
                a.playing(player, playing);
            } catch (LinkageError e) {
                older(e);
            }
        });
    }

    /** Älteres Netzwerk-Plugin (Schnittstelle 1): einmal melden, danach die neuen Methoden nicht mehr versuchen. */
    private void older(LinkageError e) {
        if (!olderNetwork) {
            olderNetwork = true;
            log.warning("Das Netzwerk-Plugin ist älter (" + e + "): keine Nachfrage vor der Lobby – alles andere läuft "
                    + "wie bisher.");
        }
    }

    @Override
    public CompletableFuture<Optional<Party>> party(UUID player) {
        Optional<MoinMorni> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return api.get().party(player).thenApply(p -> p.map(party -> new Party(party.leader(),
                party.members().stream().map(MoinMorni.Party.Member::uuid).toList())))
                .exceptionally(e -> Optional.empty());
    }
}
