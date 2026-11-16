package de.moinmorni.adlerflug.game;

import de.moinmorni.adlerflug.AdlerflugPlugin;
import de.moinmorni.adlerflug.course.Course;
import de.moinmorni.adlerflug.ui.Items;
import de.moinmorni.adlerflug.ui.Texts;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

/** Die Schnellleiste und die Flügel: im Basislager, unterwegs und nach dem Ziel. */
public final class Hotbar {

    private final AdlerflugPlugin pl;

    public Hotbar(AdlerflugPlugin pl) {
        this.pl = pl;
    }

    private PlayerInventory reset(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        p.setGameMode(p.getGameMode() == GameMode.CREATIVE && p.hasPermission("adlerflug.admin")
                ? GameMode.CREATIVE : GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setSaturation(20);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFireTicks(0);
        inv.setChestplate(Items.wings(pl.lang(p)));
        return inv;
    }

    /** Basislager: Strecken, Bestenliste, Strecke der Woche, Mitspieler, Lobby. */
    public void base(Player p) {
        String l = pl.lang(p);
        PlayerInventory inv = reset(p);
        inv.setItem(0, Items.make(Items.MENU, Material.FILLED_MAP, l, "item.menu", "item.menu.lore"));
        inv.setItem(1, Items.make(Items.TOP, Material.BOOK, l, "item.top", "item.top.lore"));
        Course weekly = pl.weeklyCourse();
        if (weekly != null) {
            inv.setItem(4, Items.make(Items.WEEKLY, Material.NETHER_STAR, l, "item.weekly", "item.weekly.lore",
                    Texts.arg("course", weekly.name(l))));
        }
        inv.setItem(7, hide(p));
        inv.setItem(8, Items.make(Items.LOBBY, Material.DARK_OAK_DOOR, l, "item.lobby", null));
        inv.setHeldItemSlot(0);
    }

    /** Unterwegs: zurück zum Ring, Neustart, (Übung: Ring wählen), Mitspieler, Aufgeben. */
    public void flight(Player p, boolean practice) {
        String l = pl.lang(p);
        PlayerInventory inv = reset(p);
        inv.setItem(0, Items.make(Items.BACK, Material.FEATHER, l, "item.back", "item.back.lore"));
        inv.setItem(1, Items.make(Items.RESTART, Material.EMERALD, l, "item.restart", null));
        if (practice) {
            inv.setItem(4, Items.make(Items.PICK, Material.TARGET, l, "item.pick", "item.pick.lore"));
        }
        inv.setItem(7, hide(p));
        inv.setItem(8, Items.make(Items.GIVEUP, Material.BARRIER, l, "item.giveup", null));
        inv.setHeldItemSlot(0);
    }

    /** Nach dem Ziel (auf dem Landeplatz): nochmal, andere Strecke, Bestenliste, Basislager. */
    public void after(Player p, Course c) {
        String l = pl.lang(p);
        PlayerInventory inv = reset(p);
        inv.setItem(0, Items.make(Items.AGAIN + ":" + c.id, Material.EMERALD, l, "item.again", null));
        inv.setItem(1, Items.make(Items.MENU, Material.FILLED_MAP, l, "item.menu", "item.menu.lore"));
        inv.setItem(2, Items.make(Items.TOP, Material.BOOK, l, "item.top", "item.top.lore"));
        inv.setItem(7, hide(p));
        inv.setItem(8, Items.make(Items.BASE, Material.CAMPFIRE, l, "item.base", null));
        inv.setHeldItemSlot(0);
    }

    /** Einladung zum Rennen: Feder in die Mitte der Leiste. */
    public void raceInvite(Player p, String leader, Course c) {
        String l = pl.lang(p);
        p.getInventory().setItem(4, Items.make(Items.RACE, Material.FIREWORK_STAR, l, "item.race", "item.race.lore",
                Texts.arg("leader", leader), Texts.arg("course", c.name(l))));
    }

    /** Gegenstand „Mitspieler sichtbar/ausgeblendet“ passend zum Zustand. */
    public org.bukkit.inventory.ItemStack hide(Player p) {
        String l = pl.lang(p);
        boolean hidden = pl.store().record(p.getUniqueId(), p.getName()).hideOthers;
        return Items.make(Items.HIDE, hidden ? Material.GRAY_DYE : Material.LIME_DYE, l,
                hidden ? "item.hide.hidden" : "item.hide.shown", "item.hide.lore");
    }

    /** Nur das Sichtbarkeits-Symbol tauschen (Rest der Leiste bleibt). */
    public void refreshHide(Player p) {
        for (int slot = 0; slot < 9; slot++) {
            if (Items.HIDE.equals(Items.id(p.getInventory().getItem(slot)))) {
                p.getInventory().setItem(slot, hide(p));
            }
        }
    }
}
