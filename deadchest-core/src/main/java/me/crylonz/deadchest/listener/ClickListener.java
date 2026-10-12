package me.crylonz.deadchest.listener;

import me.crylonz.deadchest.ChestLifecycle;
import me.crylonz.deadchest.PlayerTransfers;
import me.crylonz.deadchest.RecoveryJournal;
import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.DeadchestPickUpEvent;
import me.crylonz.deadchest.Permission;
import me.crylonz.deadchest.utils.ConfigKey;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static me.crylonz.deadchest.DeadChestLoader.config;
import static me.crylonz.deadchest.DeadChestLoader.local;
import static me.crylonz.deadchest.DeadChestManager.isPublicLootPhase;
import static me.crylonz.deadchest.utils.Utils.generateLog;
import static me.crylonz.deadchest.utils.Utils.isGraveBlock;

public class ClickListener implements Listener {

    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (isNearGraveChest(e)) {
                e.setCancelled(true);
                return;
            }
        }

        Block block = e.getClickedBlock();
        if (block != null && isGraveBlock(block.getType())) {
            if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                handleChestInteraction(e, block);
            }
        }
    }

    /**
     * Checks if the player clicks near a DeadChest
     */
    protected boolean isNearGraveChest(PlayerInteractEvent e) {
        final Block clickedBlock = e.getClickedBlock();
        if (clickedBlock == null) {
            return false;
        }
        ChestData chestData = DeadChestLoader.getChestData(clickedBlock.getLocation());
        if (chestData != null) {
            return true;
        }

        for (BlockFace face : CHECK_FACES) {
            final Block relative = clickedBlock.getRelative(face);
            final Location chestLoc = relative.getLocation();
            chestData = DeadChestLoader.getChestData(chestLoc);

            if (chestData != null) {
                return true;
            }

        }
        return false;
    }

    /**
     * Handles the primary interaction with a DeadChest
     */
    protected void handleChestInteraction(PlayerInteractEvent e, Block block) {
        Player player = e.getPlayer();
        UUID playerUUID = player.getUniqueId();
        boolean playerHasPermission = player.hasPermission(Permission.CHESTPASS.label);
        final ChestData chestData = DeadChestLoader.getChestData(block.getLocation());
        if (chestData == null || chestData.isTransitioning()) return;
        if (canOpenChest(chestData, player, playerUUID, playerHasPermission)) {
            processChestPickup(e, chestData, block, player);
        } else {
            denyChestAccess(e, player);
        }
    }

    /**
     * Checks if the player has the right to open the chest
     */
    public static boolean canOpenChest(ChestData cd, Player player, UUID playerUUID, boolean hasPerm) {
        if (isPublicLootPhase(cd, new Date())) return isPublicLootAccessible(cd, playerUUID);
        if (!config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)) return true;
        if (playerUUID.equals(cd.getPlayerUUID())) return true;
        if (hasPerm) return true;
        return false;
    }

    private static boolean isPublicLootAccessible(ChestData chestData, UUID playerUUID) {
        if (chestData == null || playerUUID == null || !isPublicLootPhase(chestData, new Date())) {
            return false;
        }

        if (playerUUID.equals(chestData.getPlayerUUID())) {
            return config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OWNER);
        }

        if (playerUUID.equals(chestData.getKillerUUID())) {
            return config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_KILLER);
        }

        return config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OTHER_PLAYERS);
    }

    /**
     * Denies access to DeadChest
     */
    private void denyChestAccess(PlayerInteractEvent e, Player player) {
        e.setCancelled(true);
        player.sendMessage(local.prefixed("chest.not-owner"));
    }

    /**
     * Complete DeadChest Recovery Process
     */
    private void processChestPickup(PlayerInteractEvent e, ChestData cd, Block block, Player player) {
        if (!hasGetPermission(player)) {
            e.setCancelled(true);
            return;
        }

        DeadchestPickUpEvent deadchestPickUpEvent = new DeadchestPickUpEvent(cd);
        Bukkit.getServer().getPluginManager().callEvent(deadchestPickUpEvent);

        if (deadchestPickUpEvent.isCancelled()) {
            e.setCancelled(true);
            return;
        }

        if (config.getInt(ConfigKey.DROP_MODE) == 1) {
            PlayerTransfers.active().recover(cd, player).thenAccept(done -> {
                if (!done) return;
                DeadChestLoader.getSchedulerAdapter().runAtLocation(block.getLocation(), () -> playPickupAnimation(block));
                DeadChestLoader.getSchedulerAdapter().runForEntity(player, () -> playPickupSound(player.getLocation().getBlock(), player));
            });
            return;
        }

        ChestLifecycle.transitionAsync(cd, ChestLifecycle.Outcome.RECOVERED,
                player.getUniqueId().toString(), () -> {
                    RecoveryJournal.active().begin(cd, "RECOVER", player.getUniqueId());
                    java.util.concurrent.CompletableFuture<Void> delivered = new java.util.concurrent.CompletableFuture<>();
                    block.setType(Material.AIR);
                    DeadChestLoader.getChestDataCache().removeChestData(cd);
                    playPickupAnimation(block);
                    DeadChestLoader.getSchedulerAdapter().executeForEntity(player, () -> {
                        try {
                            if (!player.isOnline()) throw new IllegalStateException("Player disconnected");
                            restoreOrDropInventory(cd, player, player.getLocation().getBlock());
                            player.saveData();
                            playPickupSound(player.getLocation().getBlock(), player); delivered.complete(null);
                        } catch (Exception ex) { delivered.completeExceptionally(ex); }
                    });
                    return delivered;
                });
    }

    /**
     * Checks if the player has permission to retrieve a chest
     */
    private boolean hasGetPermission(Player player) {
        if (config.getBoolean(ConfigKey.REQUIRE_PERMISSION_TO_GET_CHEST)
                && !player.hasPermission(Permission.GET.label)) {
            generateLog("Player [" + player.getName() + "] needs deadchest.get permission");
            player.sendMessage(local.prefixed("chest.no-permission-open"));
            return false;
        }
        return true;
    }

    /**
     * Restore inventory or drop items depending on the mode
     */
    private void restoreOrDropInventory(ChestData cd, Player player, Block block) {
        if (config.getInt(ConfigKey.DROP_MODE) == 1) {
            restoreInventory(cd, player, block.getWorld());
        } else {
            dropInventory(cd, block);
        }
    }

    /**
     * Restores inventory directly to the player
     */
    private void restoreInventory(ChestData cd, Player player, World world) {
        PlayerInventory playerInventory = player.getInventory();
        player.giveExp(cd.getXpStored());

        ItemStack[] originalContents = cd.getInventory().toArray(new ItemStack[0]);
        List<ItemStack> slotReplacedItems = new ArrayList<>();

        // First pass: Restore items to their original inventory positions
        for (int i = 0; i < originalContents.length; i++) {
            ItemStack item = originalContents[i];
            if (item != null) {
                if (i < playerInventory.getSize()
                        && (playerInventory.getItem(i) == null || playerInventory.getItem(i).getType() == Material.AIR)) {
                    playerInventory.setItem(i, item);
                } else {
                    slotReplacedItems.add(item);
                }
            }
        }

        // Second pass: Restore items that would have replaced existing items
        // into empty slots or drop them if there are no available slots.
        for (ItemStack i : slotReplacedItems) {
            if (playerInventory.firstEmpty() != -1) {
                playerInventory.addItem(i);
            } else {
                world.dropItemNaturally(player.getLocation(), i);
            }
        }
    }

    /**
     * Drops the contents of the chest to the ground
     */
    private void dropInventory(ChestData cd, Block block) {
        World world = block.getWorld();
        for (ItemStack i : cd.getInventory()) {
            if (i != null) {
                world.dropItemNaturally(block.getLocation(), i);
            }
        }
        if (cd.getXpStored() != 0) {
            world.spawn(block.getLocation(), ExperienceOrb.class).setExperience(cd.getXpStored());
        }
    }

    /**
     * Removes chest after recovery
     */
    private void cleanupChest(ChestData cd, Block block, Player player) {
        block.setType(Material.AIR);
        DeadChestLoader.getChestDataCache().removeChestData(cd);
        playPickupAnimation(block);
        playPickupSound(block, player);
    }

    private void playPickupAnimation(Block block) {
        if (!config.getBoolean(ConfigKey.PICKUP_ANIMATION_ENABLED)) {
            return;
        }

        final Particle particle = resolveParticle(config.getString(ConfigKey.PICKUP_ANIMATION_PARTICLE));
        final int count = Math.max(1, Math.min(config.getInt(ConfigKey.PICKUP_ANIMATION_COUNT), 250));
        final double offsetX = clamp(config.getDouble(ConfigKey.PICKUP_ANIMATION_OFFSET_X), 0.0D, 3.0D);
        final double offsetY = clamp(config.getDouble(ConfigKey.PICKUP_ANIMATION_OFFSET_Y), 0.0D, 3.0D);
        final double offsetZ = clamp(config.getDouble(ConfigKey.PICKUP_ANIMATION_OFFSET_Z), 0.0D, 3.0D);
        final double speed = clamp(config.getDouble(ConfigKey.PICKUP_ANIMATION_SPEED), 0.0D, 2.0D);
        final double yShift = clamp(config.getDouble(ConfigKey.PICKUP_ANIMATION_Y_SHIFT), -0.5D, 2.5D);

        final Location center = block.getLocation().clone().add(0.5D, yShift, 0.5D);
        block.getWorld().spawnParticle(particle, center, count, offsetX, offsetY, offsetZ, speed);
    }

    private Particle resolveParticle(String particleName) {
        if (particleName != null && !particleName.trim().isEmpty()) {
            try {
                return Particle.valueOf(particleName.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        try { return Particle.valueOf("TOTEM_OF_UNDYING"); } catch (IllegalArgumentException ex) { return Particle.valueOf("TOTEM"); }
    }

    private void playPickupSound(Block block, Player player) {
        if (!config.getBoolean(ConfigKey.PICKUP_SOUND_ENABLED)) {
            return;
        }

        Sound sound = resolveSound(config.getString(ConfigKey.PICKUP_SOUND_NAME));
        float volume = (float) clamp(config.getDouble(ConfigKey.PICKUP_SOUND_VOLUME), 0.0D, 10.0D);
        float pitch = (float) clamp(config.getDouble(ConfigKey.PICKUP_SOUND_PITCH), 0.2D, 2.0D);
        player.playSound(block.getLocation(), sound, volume, pitch);
    }

    private Sound resolveSound(String soundName) {
        if (soundName != null && !soundName.trim().isEmpty()) {
            try {
                return (Sound) Sound.class.getMethod("valueOf", String.class).invoke(null, soundName.trim().toUpperCase());
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final BlockFace[] CHECK_FACES = {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    };
}
