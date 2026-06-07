package me.crylonz.deadchest.listener;

import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.Permission;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.IgnoreItemRules;
import me.crylonz.deadchest.utils.Utils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.Damageable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static me.crylonz.deadchest.DeadChestLoader.*;
import static me.crylonz.deadchest.DeadChestManager.playerDeadChestAmount;
import static me.crylonz.deadchest.utils.ConfigKey.GENERATE_DEADCHEST_IN_CREATIVE;
import static me.crylonz.deadchest.utils.ConfigKey.KEEP_INVENTORY_ON_PVP_DEATH;
import static me.crylonz.deadchest.utils.ExpUtils.getTotalExperienceToStore;
import static me.crylonz.deadchest.utils.Utils.*;

public class PlayerDeathListener implements Listener {

    // Keep array order explicit when used
    private static final int HOLO_TIME = 0;
    private static final int HOLO_STATUS = 1;
    private static final int HOLO_NAME = 2;

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeathEvent(PlayerDeathEvent event) {

        // 1) Early exits
        if (keepInventoryAlreadyOn(event)) return;
        if (disallowedEndGeneration(event)) return;

        final Player player = event.getEntity().getPlayer();
        if (playerOrWorldDisallowsGeneration(player)) return;
        if (pvpKeepInventoryCase(event, player)) return;

        if (player.getInventory().isEmpty()) {
            generateLog("Player [" + player.getName() + "] died without inventory : No Deadchest generated");
            return;
        }

        // 2) Permissions & quotas
        if (!(worldGuardCheck(player) && (player.hasPermission(Permission.GENERATE.label)
                || !config.getBoolean(ConfigKey.REQUIRE_PERMISSION_TO_GENERATE))))
            return;

        if (!underPerPlayerLimit(player)) return;


        // 3) Block/Vehicle Location & Constraints
        World world = player.getWorld();
        Location location = player.getLocation();

        if (disallowedFluidOrRailOrMinecart(player, location)) return;

        // 4) Position adjustments (world bottom/top, doors, solids, fragile surface)
        location = adjustLocationForWorldBounds(world, location, player);
        if (location == null) return; // keep original behavior (message + return)

        location = adjustForDoorsAndSolids(world, location);
        adjustForGroundBlocks(world, location);

        Block block = world.getBlockAt(location);

        // 5) Inventory cleaning (vanishing, excluded/ignored items, durability, XP)
        sanitizeInventoryOnDeath(event, player);

        // 6) Preparing items to be stored (same slots, null kept)
        ItemStack[] original = player.getInventory().getContents();
        ItemStack[] itemsToStore = prepareItemsToStore(original);

        // Check if there's at least one valid item to store
        boolean hasSomethingToStore = Arrays.stream(itemsToStore)
                .anyMatch(Objects::nonNull);

        if (!hasSomethingToStore) {
            generateLog("Player [" + player.getName() + "] died but no valid items remain to store. No Deadchest generated");
            return;
        }

        // 7) Chest type + holograms
        generateDeadChest(block, player);
        ArmorStand[] holos = createHolograms(block, event.getEntity().getDisplayName());

        // 8) Building & saving the DeadChest (ChestData), then restoring player inventory
        buildAndSaveChestData(player, block, holos[HOLO_TIME], holos[HOLO_NAME], holos[HOLO_STATUS], itemsToStore);

        // 9) Clean up drops & remove remaining items on player side
        clearEventDropsAndPlayerInventory(event, player);

        // 10) Position message (optional), persistence & logs
        maybeSendPosition(player, block);
        persistAndLog(player, block, itemsToStore);
    }

    private boolean keepInventoryAlreadyOn(PlayerDeathEvent e) {
        if (e.getKeepInventory()) {
            generateLog("Keep Inventory is set to ON. No Deadchest generated");
            return true;
        }
        return false;
    }

    private boolean disallowedEndGeneration(PlayerDeathEvent e) {
        if (checkTheEndGeneration(e.getEntity())) {
            generateLog("Player dies in the end and " + ConfigKey.GENERATE_IN_THE_END + " is set to false. No Deadchest generated");
            return true;
        }
        return false;
    }

    private boolean playerOrWorldDisallowsGeneration(Player p) {
        if (p == null
                || config.getArray(ConfigKey.EXCLUDED_WORLDS).contains(p.getWorld().getName())
                || (!config.getBoolean(GENERATE_DEADCHEST_IN_CREATIVE)) && p.getGameMode().equals(GameMode.CREATIVE)) {
            generateLog("Player dies in an excluded world or dies in creative with " + GENERATE_DEADCHEST_IN_CREATIVE + " set to false. No Deadchest generated");
            return true;
        }
        return false;
    }

    private boolean pvpKeepInventoryCase(PlayerDeathEvent e, Player p) {
        if (config.getBoolean(KEEP_INVENTORY_ON_PVP_DEATH)) {
            if (p.getKiller() != null) {
                e.setKeepInventory(true);
                e.getDrops().clear();
                generateLog("Player dies in PVP and " + KEEP_INVENTORY_ON_PVP_DEATH + " set to true. No Deadchest generated");
                return true;
            }
        }
        return false;
    }

    private boolean underPerPlayerLimit(Player p) {
        return (playerDeadChestAmount(p) < config.getInt(ConfigKey.MAX_DEAD_CHEST_PER_PLAYER) ||
                config.getInt(ConfigKey.MAX_DEAD_CHEST_PER_PLAYER) == 0) && p.getMetadata("NPC").isEmpty();
    }

    private boolean disallowedFluidOrRailOrMinecart(Player p, Location loc) {
        final Block block = loc.getBlock();
        final Material blockType = block.getType();

        // --- Fluids ---
        if (!config.getBoolean(ConfigKey.GENERATE_ON_LAVA) && blockType == Material.LAVA) {
            generateLog("Player dies in lava : No deadchest generated");
            return true;
        }
        if (!config.getBoolean(ConfigKey.GENERATE_ON_WATER) && blockType == Material.WATER) {
            generateLog("Player dies in water : No deadchest generated");
            return true;
        }

        // --- Rails (apply GENERATE_ON_RAILS to ALL rail types) ---
        // Tag.RAILS includes: RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL
        final boolean isAnyRail = Tag.RAILS.isTagged(blockType);
        final boolean allowRails = config.getBoolean(ConfigKey.GENERATE_ON_RAILS);

        if (isAnyRail && !allowRails) {
            // Keep informative logging as before
            log.warning("Block type at death: " + blockType);
            log.warning("GENERATE_ON_RAILS=" + allowRails);
            generateLog("Player dies on rails : No deadchest generated");
            return true;
        }

        // --- Minecart ---
        if (!config.getBoolean(ConfigKey.GENERATE_IN_MINECART) && p.getVehicle() != null) {
            if (p.getVehicle().getType().equals(EntityType.MINECART)) {
                generateLog("Player dies in a minecart : No deadchest generated");
                return true;
            }
        }

        return false;
    }

    /**
     * Handles the bottom/top of the world and the "no air found" case (message & return).
     * Returns a usable location or null if the location must be abandoned.
     */
    private Location adjustLocationForWorldBounds(World world, Location loc, Player p) {
        int minHeight = computeMinHeight();

        // Bottom of the world
        if (loc.getY() < minHeight) {
            loc.setY(world.getHighestBlockYAt((int) loc.getX(), (int) loc.getZ()) + 1);
            if (loc.getY() < minHeight) loc.setY(minHeight);
            return loc;
        }

        // Top of the world
        if (loc.getBlockY() >= world.getMaxHeight()) {
            int y = world.getMaxHeight() - 1;
            loc.setY(y);

            while (world.getBlockAt(loc).getType() != Material.AIR && y > 0) {
                y--;
                loc.setY(y);
            }

            if (y < 1) {
                p.sendMessage(local.prefixed("death.not-generated"));
                return null;
            }
            return loc;
        }

        // Standard case -> handled in adjustForDoorsAndSolids
        return loc;
    }

    /**
     * Handles doors/ladders/vines and vertical ascent until air is found if necessary.
     * IMPORTANT: re-read the material type after a possible relocation to avoid using stale data.
     */
    private Location adjustForDoorsAndSolids(World world, Location loc) {
        Material type = world.getBlockAt(loc).getType();

        if (type == Material.DARK_OAK_DOOR ||
                type == Material.ACACIA_DOOR ||
                type == Material.BIRCH_DOOR ||
                (!Utils.isBefore1_16() && type == Material.CRIMSON_DOOR) ||
                type == Material.IRON_DOOR ||
                type == Material.JUNGLE_DOOR ||
                type == Material.OAK_DOOR ||
                type == Material.SPRUCE_DOOR ||
                (!Utils.isBefore1_16() && type == Material.WARPED_DOOR) ||
                type == Material.VINE ||
                type == Material.LADDER) {

            Location tmpLoc = getFreeBlockAroundThisPlace(world, loc);
            if (tmpLoc != null) {
                loc = tmpLoc;
                // Re-read the material at the new location to avoid stale checks
                type = world.getBlockAt(loc).getType();
            }
        }

        if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR && type != Material.WATER) {
            while (world.getBlockAt(loc).getType() != Material.AIR &&
                    loc.getY() < world.getMaxHeight()) {
                loc.setY(loc.getY() + 1);
            }
        }
        return loc;
    }

    /**
     * Manages the surface: DIRT_PATH / FARMLAND / GRASS_PATH (depending on version).
     */
    private void adjustForGroundBlocks(World world, Location loc) {
        Location groundLocation = loc.clone();
        groundLocation.setY(groundLocation.getY() - 1);
        if (isBefore1_17() && world.getBlockAt(groundLocation).getType() == Material.valueOf("GRASS_PATH")
                || !isBefore1_17() && world.getBlockAt(groundLocation).getType() == Material.DIRT_PATH
                || world.getBlockAt(groundLocation).getType() == Material.FARMLAND) {
            loc.setY(loc.getY() + 1);
        }
    }



    private void sanitizeInventoryOnDeath(PlayerDeathEvent e, Player p) {
        // The order matters:
        // 1) remove vanishing items first,
        // 2) then remove excluded items,
        // 3) then apply durability loss on remaining,
        // 4) finally adjust XP drop if configured.
        removeVanishingItems(p.getInventory());
        removeExcludedItems(p.getInventory());
        applyDurabilityLoss(p.getInventory());
        if (config.getBoolean(ConfigKey.STORE_XP)) {
            e.setDroppedExp(0);
        }
    }

    void removeVanishingItems(PlayerInventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (hasVanishing(item)) {
                inv.remove(item);
            }
        }

        inv.setHelmet(clearIfVanishing(inv.getHelmet()));
        inv.setChestplate(clearIfVanishing(inv.getChestplate()));
        inv.setLeggings(clearIfVanishing(inv.getLeggings()));
        inv.setBoots(clearIfVanishing(inv.getBoots()));
        inv.setItemInOffHand(clearIfVanishing(inv.getItemInOffHand()));
    }

    private ItemStack clearIfVanishing(ItemStack item) {
        return hasVanishing(item) ? null : item;
    }

    private boolean hasVanishing(ItemStack item) {
        return item != null && item.getEnchantments().containsKey(Enchantment.VANISHING_CURSE);
    }

    private void removeExcludedItems(PlayerInventory inv) {
        final List<String> excluded = config.getArray(ConfigKey.EXCLUDED_ITEMS);
        for (String item : excluded) {
            if (item != null) {
                Material mat = Material.getMaterial(item.toUpperCase());
                if (mat != null) inv.remove(mat);
            }
        }
    }

    private void applyDurabilityLoss(PlayerInventory inv) {
        final int lossPct = config.getInt(ConfigKey.ITEM_DURABILITY_LOSS_ON_DEATH);
        if (lossPct <= 0) return;

        Arrays.stream(inv.getContents())
                .filter(Objects::nonNull)
                .filter(itemStack -> itemStack.getItemMeta() instanceof Damageable)
                .filter(this::isUsingDamage)
                .forEach(itemStack -> {
                    Damageable itemData = (Damageable) itemStack.getItemMeta();
                    int loss = (int) (itemStack.getType().getMaxDurability() * lossPct / 100.0);
                    int newDamage = itemData.getDamage() + loss;
                    itemData.setDamage(newDamage);

                    if (newDamage > itemStack.getType().getMaxDurability()) {
                        inv.remove(itemStack);
                    } else {
                        itemStack.setItemMeta(itemData);
                    }
                });
    }

    private boolean isUsingDamage(ItemStack itemStack) {
        Material type = itemStack.getType();
        if(Tag.ITEMS_TOOLS.isTagged(type) ){
            return true;
        }
        if(Tag.ITEMS_SWORDS.isTagged(type)){
            return true;
        }
        if(Tag.ITEMS_AXES.isTagged(type)){
            return true;
        }
        return itemStack.getItemMeta() instanceof ArmorMeta;
    }

    private ItemStack[] prepareItemsToStore(ItemStack[] playerInv) {
        ItemStack[] itemsToStore = new ItemStack[playerInv.length];
        for (int i = 0; i < playerInv.length; i++) {
            ItemStack item = playerInv[i];
            if (item != null && !isIgnoredItem(item)) {
                itemsToStore[i] = item;
            }
            // keep null to preserve slot positions
        }
        return itemsToStore;
    }

    private void buildAndSaveChestData(Player p, Block b, ArmorStand holoTime, ArmorStand holoName, ArmorStand holoStatus, ItemStack[] itemsToStore) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] snapshot = inv.getContents();
        inv.setContents(itemsToStore);
        DeadChestLoader.getChestDataCache().addChestData(cerateChestData(p, b, holoTime, holoName, holoStatus, inv));
        inv.setContents(snapshot);
    }

    private static ChestData cerateChestData(final Player p, final Block b, final ArmorStand holoTime, final ArmorStand holoName, final ArmorStand holoStatus, final PlayerInventory inv) {
        final ChestData chestData = new ChestData(
                inv,
                b.getLocation(),
                p,
                p.hasPermission(Permission.INFINITY_CHEST.label),
                holoTime,
                holoName,
                getTotalExperienceToStore(p)
        );
        chestData.setHolographicStatusId(holoStatus == null ? null : holoStatus.getUniqueId());
        chestData.save(containsChestOnLoc -> {
            if (containsChestOnLoc){
                generateLog("Could not generate deadchest, as dublicate exist in database on same location [" + p.getName() + "] in " + b.getWorld().getName() +
                        " at X:" + b.getX() + " Y:" + b.getY() + " Z:" + b.getZ());
            }
        });
        return chestData;
    }

    private void clearEventDropsAndPlayerInventory(PlayerDeathEvent e, Player p) {
        // Direct removeIf: no intermediate list needed
        e.getDrops().removeIf(drop -> drop != null && !isIgnoredItem(drop));

        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && !isIgnoredItem(item)) {
                p.getInventory().removeItem(item);
            }
        }
    }

    private boolean isIgnoredItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        for (Object ignoredEntry : config.getIgnoredEntries()) {
            if (IgnoreItemRules.matches(ignoredEntry, item)) {
                return true;
            }
        }

        return false;
    }

    private void maybeSendPosition(Player p, Block b) {
        if (config.getBoolean(ConfigKey.DISPLAY_POSITION_ON_DEATH)) {
            p.sendMessage(local.prefixed("death.position", b.getX(), b.getY(), b.getZ()));
        }
    }

    private void persistAndLog(Player p, Block b, ItemStack[] itemsToStore) {

        generateLog("New deadchest for [" + p.getName() + "] in " + b.getWorld().getName() +
                " at X:" + b.getX() + " Y:" + b.getY() + " Z:" + b.getZ());
        generateLog("Chest content : " + Arrays.asList(itemsToStore));

        if (config.getBoolean(ConfigKey.LOG_DEADCHEST_ON_CONSOLE)) {
            log.info("New deadchest for [" + p.getName() + "] at X:" + b.getX() + " Y:" + b.getY() + " Z:" + b.getZ());
        }
    }

    public static boolean worldGuardCheck(Player p) {
        if (wgsdc != null) {
            return wgsdc.worldGuardChecker(p);
        }
        return true;
    }
}
