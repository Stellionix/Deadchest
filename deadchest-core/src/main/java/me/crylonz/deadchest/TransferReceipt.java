package me.crylonz.deadchest;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Entity tags are part of the same player NBT as inventory, including on Bukkit 1.13. */
public final class TransferReceipt {
    public static final String PREFIX = "deadchest.tx.";
    public static boolean present(Player player, String id) { return player.getScoreboardTags().contains(PREFIX + id); }

    public static void apply(Player player, String id, Runnable mutation) {
        if (present(player, id)) return;
        ItemStack[] inventory = player.getInventory().getContents().clone();
        for (int i=0;i<inventory.length;i++) if (inventory[i]!=null) inventory[i]=inventory[i].clone();
        int level = player.getLevel(), total = player.getTotalExperience(); float exp = player.getExp();
        if (!player.addScoreboardTag(PREFIX + id)) throw new IllegalStateException("Cannot reserve transfer receipt");
        try {
            mutation.run();
            player.saveData();
        } catch (RuntimeException | Error e) {
            // Keep memory consistent with the absent receipt if a plugin/IO call fails mid-mutation.
            player.getInventory().setContents(inventory);
            player.setLevel(level); player.setExp(exp); player.setTotalExperience(total);
            player.removeScoreboardTag(PREFIX + id);
            throw e;
        }
    }
    private TransferReceipt() {}
}
