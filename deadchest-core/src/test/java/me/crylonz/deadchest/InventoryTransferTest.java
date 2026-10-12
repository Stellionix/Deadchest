package me.crylonz.deadchest;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryTransferTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void mergesPartOfAStackWithoutChangingInputsOrLosingOriginalSlots() {
        ItemStack[] inventory = new ItemStack[41];
        Arrays.fill(inventory, 0, 36, new ItemStack(Material.STONE,64));
        inventory[0] = new ItemStack(Material.DIAMOND,60);
        List<ItemStack> chest = Arrays.asList(null,new ItemStack(Material.DIAMOND,17));
        InventoryTransfer.Packing result = InventoryTransfer.partial(inventory,chest,true);
        assertEquals(4,result.moved); assertEquals(64,result.inventory[0].getAmount());
        assertNull(result.remaining.get(0)); assertEquals(13,result.remaining.get(1).getAmount());
        assertEquals(60,inventory[0].getAmount()); assertEquals(17,chest.get(1).getAmount());
        assertNull(InventoryTransfer.pack(inventory,chest,true));
    }

    @Test void keepsUnrecoveredArmorInItsSlotAndRestoresOffhandWhenFree() {
        ItemStack[] inventory = new ItemStack[41];
        Arrays.fill(inventory, 0, 36, new ItemStack(Material.STONE,64));
        inventory[36] = new ItemStack(Material.DIAMOND_BOOTS);
        List<ItemStack> chest = new ArrayList<>(Collections.nCopies(41,null));
        chest.set(36,new ItemStack(Material.IRON_BOOTS)); chest.set(40,new ItemStack(Material.SHIELD));
        InventoryTransfer.Packing result = InventoryTransfer.partial(inventory,chest,true);
        assertEquals(1,result.moved); assertEquals(Material.SHIELD,result.inventory[40].getType());
        assertEquals(Material.IRON_BOOTS,result.remaining.get(36).getType()); assertNull(result.remaining.get(40));
    }

    @Test void doesNotPutOrdinaryOverflowInArmorOrOffhandSlots() {
        ItemStack[] inventory = new ItemStack[41];
        Arrays.fill(inventory, 0, 36, new ItemStack(Material.STONE,64));
        InventoryTransfer.Packing result = InventoryTransfer.partial(inventory,Arrays.asList(new ItemStack(Material.DIAMOND,17)),true);
        assertEquals(0,result.moved); assertEquals(17,result.remaining.get(0).getAmount());
        for (int i=36;i<41;i++) assertNull(result.inventory[i]);
    }
}
