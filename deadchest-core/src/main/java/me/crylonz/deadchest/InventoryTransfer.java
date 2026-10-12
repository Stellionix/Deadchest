package me.crylonz.deadchest;

import org.bukkit.inventory.ItemStack;
import java.util.*;
import me.crylonz.deadchest.utils.LegacySupport;

/** All-or-nothing packing; overflow waits in the durable transfer, never disappears on the ground. */
public final class InventoryTransfer {
    public static ItemStack[] pack(ItemStack[] before, List<ItemStack> items, boolean originalSlots) {
        ItemStack[] result = Arrays.stream(before).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        List<ItemStack> remaining = new ArrayList<>();
        for (int i=0;i<items.size();i++) {
            ItemStack item=items.get(i);
            if (empty(item)) continue;
            if (originalSlots && i<result.length && empty(result[i])) result[i]=item.clone();
            else remaining.add(item);
        }
        int storage=Math.min(36,result.length);
        for (ItemStack item : remaining) {
            int left=item.getAmount();
            for (int i=0;i<storage;i++) if (!empty(result[i]) && result[i].isSimilar(item)) {
                int amount=Math.min(left,Math.max(0,result[i].getMaxStackSize()-result[i].getAmount()));
                result[i].setAmount(result[i].getAmount()+amount); left-=amount;
            }
            for (int i=0;i<storage && left>0;i++) if (empty(result[i])) {
                int amount=Math.min(left,item.getMaxStackSize()); result[i]=item.clone(); result[i].setAmount(amount); left-=amount;
            }
            if (left>0) return null;
        }
        return result;
    }
    private static boolean empty(ItemStack i) { return i==null || LegacySupport.isAir(i.getType()); }
    private InventoryTransfer() {}
}
