package me.crylonz.deadchest;

import me.crylonz.deadchest.commands.GivebackDelivery;
import me.crylonz.deadchest.commands.GivebackStrategy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.concurrent.CompletableFuture;

/** Shared Pro transfer path: claim on the chest, world cleanup on its region, delivery on the player. */
public final class ChestTransfers {
    public static boolean giveBack(Player player, ChestData chest, GivebackStrategy strategy, String actor) {
        ChestData active = DeadChestLoader.getChestData(chest.getChestLocation());
        if (active == null || !active.getId().equals(chest.getId())) return false;
        return ChestLifecycle.transitionAsync(active, ChestLifecycle.Outcome.GIVEBACK, actor, () -> {
            CompletableFuture<Void> delivered = new CompletableFuture<>();
            remove(active);
            DeadChestLoader.getSchedulerAdapter().executeForEntity(player, () -> {
                if (!player.isOnline()) { delivered.completeExceptionally(new IllegalStateException("Player disconnected")); return; }
                try { GivebackDelivery.deliver(player, active, strategy); delivered.complete(null); }
                catch (Exception error) { delivered.completeExceptionally(error); }
            });
            return delivered;
        });
    }
    public static void remove(ChestData chest) {
        if (chest.getChestLocation().getWorld() != null) chest.getChestLocation().getBlock().setType(Material.AIR);
        DeadChestLoader.getChestDataCache().removeChestData(chest);
    }
    private ChestTransfers() {}
}
