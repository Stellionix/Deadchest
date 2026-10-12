package me.crylonz.deadchest.listener;


import me.crylonz.deadchest.utils.LegacySupport;
import me.crylonz.deadchest.IgnoreInventoryHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import static me.crylonz.deadchest.DeadChestLoader.*;

public class InventoryClickListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isDeadchestGui(event.getInventory())) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        event.setCancelled(true);

        final Inventory clickedInv = event.getClickedInventory();
        final int slot = event.getSlot();

        if (clickedInv == null) return;

        // first case : removing item from ignore list
        if (clickedInv == event.getInventory()) {
            final ItemStack clicked = event.getCurrentItem();
            if (clicked == null || LegacySupport.isAir(clicked.getType())) return;

            getSchedulerAdapter().runForEntity(event.getWhoClicked(), () -> {
                if (clicked.getAmount() <= 1) {
                    clickedInv.setItem(slot, null);
                } else {
                    ItemStack newStack = clicked.clone();
                    newStack.setAmount(clicked.getAmount() - 1);
                    clickedInv.setItem(slot, newStack);
                }
                saveIgnoreInventoryToConfig(ignoreList);
            });
            return;
        }

        // second case : adding item to ignore list
        if (clickedInv == event.getWhoClicked().getInventory()) {
            final ItemStack src = event.getCurrentItem();
            if (src == null || LegacySupport.isAir(src.getType())) return;

            if (!containsSimilarIgnoreItem(src)) {
                getSchedulerAdapter().runForEntity(event.getWhoClicked(), () -> {
                    ItemStack item = src.clone();
                    item.setAmount(1);
                    ignoreList.addItem(item);
                    saveIgnoreInventoryToConfig(ignoreList);
                });
            }
        }
    }


    private boolean isDeadchestGui(Inventory inventory) {
        return inventory.getHolder() instanceof IgnoreInventoryHolder;
    }

    private boolean containsSimilarIgnoreItem(ItemStack candidate) {
        if (ignoreList == null || candidate == null || LegacySupport.isAir(candidate.getType())) {
            return false;
        }

        final ItemStack normalizedCandidate = candidate.clone();
        normalizedCandidate.setAmount(1);

        for (ItemStack ignoredItem : ignoreList.getContents()) {
            if (ignoredItem != null && ignoredItem.isSimilar(normalizedCandidate)) {
                return true;
            }
        }

        return false;
    }
}
