package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.ChestData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public final class GivebackDelivery {

    private GivebackDelivery() {
    }

    public static void deliver(Player player, ChestData chestData, GivebackStrategy strategy) {
        if (player == null || chestData == null || strategy == null) {
            return;
        }

        deliver(player, chestData.getInventory(), chestData.getXpStored(), strategy);
    }

    public static void deliver(Player player, List<ItemStack> items, int xpStored, GivebackStrategy strategy) {
        if (player == null || items == null || strategy == null) {
            return;
        }

        switch (strategy) {
            case INVENTORY:
                restoreInventory(player, items, xpStored);
                return;
            case GROUND:
                dropAtPlayer(player, items, xpStored);
                return;
            default:
        }
    }

    private static void restoreInventory(Player player, List<ItemStack> items, int xpStored) {
        PlayerInventory playerInventory = player.getInventory();
        player.giveExp(xpStored);

        List<ItemStack> overflow = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (item == null) {
                continue;
            }

            if (i < playerInventory.getSize()
                    && (playerInventory.getItem(i) == null || playerInventory.getItem(i).getType() == Material.AIR)) {
                playerInventory.setItem(i, item);
            } else {
                overflow.add(item);
            }
        }

        for (ItemStack item : overflow) {
            if (playerInventory.firstEmpty() != -1) {
                playerInventory.addItem(item);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    private static void dropAtPlayer(Player player, List<ItemStack> items, int xpStored) {
        World world = player.getWorld();
        Location location = player.getLocation();
        for (ItemStack item : items) {
            if (item != null) {
                world.dropItemNaturally(location, item);
            }
        }
        player.giveExp(xpStored);
    }
}
