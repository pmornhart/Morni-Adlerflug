package de.moinmorni.adlerflug.game;

import de.moinmorni.adlerflug.AdlerflugPlugin;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.courses.Basecamp;
import de.moinmorni.adlerflug.ui.Items;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Klicks mit den Gegenständen der Schnellleiste und auf die Schilder der Stationen. Taste F (Hände tauschen) bringt
 * unterwegs zurück zum letzten Ring.
 */
public final class Controls implements Listener {

    /** Doppelklicks (Hand + Neben-Hand, Block + Luft) nicht doppelt auswerten. */
    private static final long COOLDOWN_MS = 250;

    private final AdlerflugPlugin pl;
    private final Map<UUID, Long> last = new HashMap<>();

    public Controls(AdlerflugPlugin pl) {
        this.pl = pl;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!p.getWorld().equals(pl.world()) || event.getAction() == Action.PHYSICAL) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null) {
            Course c = stationAt(block);
            if (c != null) {
                event.setCancelled(true);
                if (debounce(p)) {
                    pl.flights().select(p, c);
                }
                return;
            }
        }
        String id = Items.id(event.getItem());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        if (!debounce(p)) {
            return;
        }
        switch (id.contains(":") ? id.substring(0, id.indexOf(':')) : id) {
            case Items.MENU -> pl.menus().courses(p);
            case Items.TOP -> pl.menus().book(p);
            case Items.WEEKLY -> {
                Course w = pl.weeklyCourse();
                if (w != null) {
                    pl.flights().select(p, w);
                }
            }
            case Items.HIDE -> pl.toggleHide(p);
            case Items.LOBBY -> pl.net().toLobby(p);
            case Items.BACK -> pl.flights().backToRing(p);
            case Items.RESTART -> pl.flights().restart(p);
            case Items.GIVEUP -> pl.flights().giveUp(p);
            case Items.PICK -> {
                Course c = pl.flights().courseOf(p.getUniqueId());
                if (c != null) {
                    pl.menus().rings(p, c);
                }
            }
            case Items.AGAIN -> pl.model().course(id.substring(id.indexOf(':') + 1)).ifPresent(c -> pl.flights().select(p, c));
            case Items.BASE -> pl.toBasecamp(p);
            case Items.RACE -> pl.flights().joinRace(p);
            default -> {
            }
        }
    }

    /** Taste F unterwegs: zurück zum letzten Ring. */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (!p.getWorld().equals(pl.world())) {
            return;
        }
        event.setCancelled(true);
        if (pl.flights().flying(p.getUniqueId()) && debounce(p)) {
            pl.flights().backToRing(p);
        }
    }

    private boolean debounce(Player p) {
        long now = System.currentTimeMillis();
        Long before = last.put(p.getUniqueId(), now);
        return before == null || now - before >= COOLDOWN_MS;
    }

    private Course stationAt(Block b) {
        for (Basecamp.Station s : pl.model().basecamp().stations()) {
            if (s.signX() == b.getX() && s.signY() == b.getY() && s.signZ() == b.getZ()) {
                return pl.model().course(s.course()).orElse(null);
            }
        }
        return null;
    }

    public void forget(UUID id) {
        last.remove(id);
    }
}
