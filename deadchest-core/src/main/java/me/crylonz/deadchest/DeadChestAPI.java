package me.crylonz.deadchest;

import me.crylonz.deadchest.db.InMemoryChestStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DeadChestAPI {

    /**
     * Get a list of chest for the given player
     *
     * @param player player to inspect
     * @return chest snapshot list
     */
    public static List<ChestData> getChests(Player player) {

        List<ChestData> chestDataList = new ArrayList<>();
        DeadChestLoader.getChestDataCache().getPlayerLinkedDeadChestData(player, chestData ->
                chestDataList.add(new ChestData(chestData))
        );
        return chestDataList;
    }

    /**
     * Give back the last Deadchest of a player. The player need to be online
     *
     * @param player target player
     * @param chest chest to return
     * @return true if chest is returned to its owner
     */
    public static boolean giveBackChest(Player player, ChestData chest) {
        if (chest == null || PlayerTransfers.blocked(chest.getId())) return false;
        if (ChestLifecycle.enabled()) {
            return ChestTransfers.giveBack(player, chest, me.crylonz.deadchest.commands.GivebackStrategy.GROUND, "API");
        }
        if (DeadChestLoader.getSchedulerAdapter().isFoliaLikeRuntime()) {
            throw new IllegalStateException("Use giveBackChestAsync on Folia; blocking waits are unsafe");
        }

        if (player.isOnline()) {
            for (ItemStack itemStack : chest.getInventory()) {
                if (itemStack != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                }
            }
            return removeChest(chest);
        }
        return false;
    }

    public static CompletableFuture<Boolean> giveBackChestAsync(Player player, ChestData chest) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (player == null || chest == null || !player.isOnline() || PlayerTransfers.blocked(chest.getId())) {
            future.complete(false);
            return future;
        }

        if (ChestLifecycle.enabled()) {
            future.complete(ChestTransfers.giveBack(player, chest, me.crylonz.deadchest.commands.GivebackStrategy.GROUND, "API"));
            return future;
        }
        DeadChestLoader.getSchedulerAdapter().executeForEntity(player, () -> {
            for (ItemStack itemStack : chest.getInventory()) {
                if (itemStack != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                }
            }

            removeChestAsync(chest).whenComplete((removed, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                    return;
                }
                future.complete(Boolean.TRUE.equals(removed));
            });
        });

        return future;
    }

    /**
     * Remove a player chest
     *
     * @param chest chest to remove
     * @return true if the chest is correctly removed
     */
    public static boolean removeChest(ChestData chest) {
        if (ChestLifecycle.enabled()) {
            ChestData active = chest == null ? null : DeadChestLoader.getChestData(chest.getChestLocation());
            return active != null && active.getId().equals(chest.getId()) && ChestLifecycle.transition(active, ChestLifecycle.Outcome.DELETED, "API", () -> ChestTransfers.remove(active));
        }
        if (DeadChestLoader.getSchedulerAdapter().isFoliaLikeRuntime()) {
            throw new IllegalStateException("Use removeChestAsync on Folia; blocking waits are unsafe");
        }

        World world = Bukkit.getWorld(chest.getWorldName());
        final InMemoryChestStore chestDataStore = DeadChestLoader.getChestDataCache();

        if (world != null && chestDataStore.getChestData(chest.getChestLocation()) != null) {
            world.getBlockAt(chest.getChestLocation()).setType(Material.AIR);
            chestDataStore.removeChestData(chest);
            return true;
        }
        return false;
    }

    public static CompletableFuture<Boolean> removeChestAsync(ChestData chest) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (chest == null) {
            future.complete(false);
            return future;
        }

        final InMemoryChestStore chestDataStore = DeadChestLoader.getChestDataCache();
        if (ChestLifecycle.enabled()) {
            future.complete(removeChest(chest)); return future;
        }
        if (chestDataStore.getChestData(chest.getChestLocation()) == null) {
            future.complete(false);
            return future;
        }

        DeadChestLoader.getSchedulerAdapter().executeAtLocation(chest.getChestLocation(), () -> {
            World world = Bukkit.getWorld(chest.getWorldName());
            if (world == null || chestDataStore.getChestData(chest.getChestLocation()) == null) {
                future.complete(false);
                return;
            }

            world.getBlockAt(chest.getChestLocation()).setType(Material.AIR);
            chestDataStore.removeChestData(chest);
            future.complete(true);
        });

        return future;
    }
}
