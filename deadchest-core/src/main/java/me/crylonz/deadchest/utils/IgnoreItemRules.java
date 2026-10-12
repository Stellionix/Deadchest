package me.crylonz.deadchest.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class IgnoreItemRules {

    private IgnoreItemRules() {
    }

    public static List<Object> normalizeEntries(Object rawValue) {
        List<Object> normalized = new ArrayList<>();
        if (!(rawValue instanceof Collection)) {
            return normalized;
        }

        Collection<?> collection = (Collection<?>) rawValue;

        for (Object entry : collection) {
            Object normalizedEntry = normalizeEntry(entry);
            if (normalizedEntry != null) {
                normalized.add(normalizedEntry);
            }
        }

        return normalized;
    }

    public static Object normalizeEntry(Object rawEntry) {
        if (rawEntry instanceof String) {
            String normalizedName = ((String) rawEntry).trim().toUpperCase();
            Material material = Material.getMaterial(normalizedName);
            return material == null || me.crylonz.deadchest.utils.LegacySupport.isAir(material) ? null : normalizedName;
        }

        ItemStack itemStack = asItemStack(rawEntry);
        if (itemStack == null || me.crylonz.deadchest.utils.LegacySupport.isAir(itemStack.getType())) {
            return null;
        }

        ItemStack normalizedItem = itemStack.clone();
        normalizedItem.setAmount(1);
        return isSimpleMaterialOnly(normalizedItem) ? normalizedItem.getType().name() : normalizedItem;
    }

    public static boolean matches(Object entry, ItemStack candidate) {
        if (candidate == null || me.crylonz.deadchest.utils.LegacySupport.isAir(candidate.getType())) {
            return false;
        }

        if (entry instanceof String) {
            return candidate.getType().name().equalsIgnoreCase((String) entry);
        }

        ItemStack ruleItem = asItemStack(entry);
        if (ruleItem == null || me.crylonz.deadchest.utils.LegacySupport.isAir(ruleItem.getType())) {
            return false;
        }

        ItemStack normalizedRule = ruleItem.clone();
        normalizedRule.setAmount(1);
        ItemStack normalizedCandidate = candidate.clone();
        normalizedCandidate.setAmount(1);
        return normalizedRule.isSimilar(normalizedCandidate);
    }

    public static ItemStack toDisplayItem(Object entry) {
        if (entry instanceof String) {
            Material material = Material.getMaterial((String) entry);
            return material == null || me.crylonz.deadchest.utils.LegacySupport.isAir(material) ? null : new ItemStack(material, 1);
        }

        ItemStack itemStack = asItemStack(entry);
        if (itemStack == null || me.crylonz.deadchest.utils.LegacySupport.isAir(itemStack.getType())) {
            return null;
        }

        ItemStack displayItem = itemStack.clone();
        displayItem.setAmount(1);
        return displayItem;
    }

    public static List<Object> fromInventory(ItemStack[] contents) {
        LinkedHashSet<Object> entries = new LinkedHashSet<>();
        if (contents == null) {
            return new ArrayList<>();
        }

        for (ItemStack itemStack : contents) {
            Object normalizedEntry = normalizeEntry(itemStack);
            if (normalizedEntry != null) {
                entries.add(normalizedEntry);
            }
        }

        return new ArrayList<>(entries);
    }

    private static ItemStack asItemStack(Object rawEntry) {
        if (rawEntry instanceof ItemStack) {
            return (ItemStack) rawEntry;
        }

        if (rawEntry instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> serialized = (Map<String, Object>) rawEntry;
                return ItemStack.deserialize(serialized);
            } catch (ClassCastException | IllegalArgumentException ignored) {
                return null;
            }
        }

        return null;
    }

    private static boolean isSimpleMaterialOnly(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }

        return !itemStack.hasItemMeta();
    }
}
