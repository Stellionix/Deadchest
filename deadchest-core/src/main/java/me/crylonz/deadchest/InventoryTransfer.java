package me.crylonz.deadchest;

import org.bukkit.inventory.ItemStack;
import java.util.*;
import me.crylonz.deadchest.utils.LegacySupport;

/** Simulates delivery without mutating either inventory. Remainders retain their original slots. */
public final class InventoryTransfer {
    public static ItemStack[] pack(ItemStack[] before, List<ItemStack> items, boolean originalSlots) {
        Packing packing = partial(before, items, originalSlots);
        return packing.hasRemainder() ? null : packing.inventory;
    }

    public static final class Packing {
        public final ItemStack[] inventory;
        public final List<ItemStack> remaining;
        public final int moved;
        private Packing(ItemStack[] inventory, List<ItemStack> remaining, int moved) {
            this.inventory = inventory; this.remaining = remaining; this.moved = moved;
        }
        public boolean hasRemainder() { return remaining.stream().anyMatch(i -> !empty(i)); }
    }

    public static Packing partial(ItemStack[] before, List<ItemStack> items, boolean originalSlots) {
        ItemStack[] result = Arrays.stream(before).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        List<ItemStack> remaining = new ArrayList<>(Collections.nCopies(items.size(), null));
        int moved = 0;
        for (int i=0;i<items.size();i++) {
            ItemStack item=items.get(i);
            if (empty(item)) continue;
            if (originalSlots && i<result.length && empty(result[i])) {
                result[i]=item.clone(); moved += item.getAmount();
            } else remaining.set(i, item.clone());
        }
        int storage=Math.min(36,result.length);
        for (int slot=0;slot<remaining.size();slot++) {
            ItemStack item = remaining.get(slot);
            if (empty(item)) continue;
            int left=item.getAmount();
            for (int i=0;i<storage;i++) if (!empty(result[i]) && result[i].isSimilar(item)) {
                int amount=Math.min(left,Math.max(0,result[i].getMaxStackSize()-result[i].getAmount()));
                result[i].setAmount(result[i].getAmount()+amount); left-=amount;
            }
            for (int i=0;i<storage && left>0;i++) if (empty(result[i])) {
                int amount=Math.min(left,item.getMaxStackSize()); result[i]=item.clone(); result[i].setAmount(amount); left-=amount;
            }
            moved += item.getAmount()-left;
            if (left==0) remaining.set(slot,null);
            else item.setAmount(left);
        }
        return new Packing(result,remaining,moved);
    }
    private static boolean empty(ItemStack i) { return i==null || LegacySupport.isAir(i.getType()); }
    private InventoryTransfer() {}
}
