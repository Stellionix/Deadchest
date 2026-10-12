package me.crylonz.deadchest;

import me.crylonz.deadchest.utils.LegacySupport;
import me.crylonz.deadchest.db.ChestDataRepository;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

@SerializableAs("ChestData")
public final class ChestData {

    private java.util.concurrent.atomic.AtomicBoolean transitioning = new java.util.concurrent.atomic.AtomicBoolean();
    private java.util.concurrent.atomic.AtomicBoolean creating = new java.util.concurrent.atomic.AtomicBoolean();
    public boolean claimTransition() { return !creating.get() && transitioning.compareAndSet(false, true); }
    public void cancelUnpreparedTransition() { transitioning.set(false); }
    public boolean isTransitioning() { return creating.get() || transitioning.get(); }
    public void beginCreation() { creating.set(true); }
    public void creationSaved() { creating.set(false); }
    private UUID chestId = UUID.randomUUID();
    public UUID getId() { return chestId; }
    /** Repository-only hydration; never change the identity of an active chest. */
    public void loadPersistentId(UUID id) { this.chestId = java.util.Objects.requireNonNull(id); }
    private List<ItemStack> inventory;
    private Location chestLocation;
    private String playerName;
    private UUID playerUUID;
    private Date chestDate;
    private boolean isInfinity;
    private boolean isRemovedBlock;
    private Location holographicTimer;
    private UUID holographicTimerId;
    private UUID holographicStatusId;
    private UUID holographicOwnerId;
    private UUID killerUUID;
    private String worldName;

    private int xpStored;

    public ChestData(ChestData chest) {
        this.chestId = chest.getId();
        this.transitioning = chest.transitioning;
        this.creating = chest.creating;
        this.inventory = new ArrayList<>();
        for (ItemStack item : chest.getInventory()) this.inventory.add(item == null ? null : item.clone());
        this.chestLocation = chest.getChestLocation().clone();
        this.playerName = chest.getPlayerName();
        this.playerUUID = chest.getPlayerUUID();
        this.chestDate = chest.getChestDate();
        this.isRemovedBlock = chest.isRemovedBlock();
        this.isInfinity = chest.isInfinity();
        this.holographicTimer = chest.getHolographicTimer();
        this.holographicTimerId = chest.getHolographicTimerId();
        this.holographicStatusId = chest.getHolographicStatusId();
        this.holographicOwnerId = chest.getHolographicOwnerId();
        this.killerUUID = chest.getKillerUUID();
        this.worldName = chest.getWorldName();
        this.xpStored = chest.getXpStored();
    }

    public ChestData(final Inventory inv,
                     final Location chestLocation,
                     final Player p,
                     final boolean isInfinity,
                     final ArmorStand asTimer,
                     final ArmorStand owner,
                     final int xpToStore) {

        if (p != null) {
            this.inventory = Arrays.asList(inv.getContents());
            this.chestLocation = chestLocation.clone();
            this.playerName = p.getName();
            this.playerUUID = p.getUniqueId();
            this.chestDate = new Date();
            this.isInfinity = isInfinity;
            this.isRemovedBlock = false;
            this.holographicTimer = asTimer.getLocation().clone();
            this.holographicTimerId = asTimer.getUniqueId();
            this.holographicStatusId = null;
            this.holographicOwnerId = owner.getUniqueId();
            this.killerUUID = p.getKiller() == null ? null : p.getKiller().getUniqueId();
            this.xpStored = xpToStore;
            if (chestLocation.getWorld() != null)
                this.worldName = chestLocation.getWorld().getName();
        }
    }

    public ChestData(final List<ItemStack> inventory,
                     final Location chestLocation,
                     final String playerName,
                     final UUID playerUUID,
                     final Date chestDate,
                     final boolean isInfinity,
                     final boolean isRemovedBlock,
                     final Location holographicTimer,
                     final UUID asTimerId,
                     final UUID asStatusId,
                     final UUID asOwnerId,
                     final String worldName,
                     final int xpStored) {
        this(inventory, chestLocation, playerName, playerUUID, chestDate, isInfinity, isRemovedBlock, holographicTimer, asTimerId, asStatusId, asOwnerId, null, worldName, xpStored);
    }

    public ChestData(final List<ItemStack> inventory,
                     final Location chestLocation,
                     final String playerName,
                     final UUID playerUUID,
                     final Date chestDate,
                     final boolean isInfinity,
                     final boolean isRemovedBlock,
                     final Location holographicTimer,
                     final UUID asTimerId,
                     final UUID asStatusId,
                     final UUID asOwnerId,
                     final UUID killerUUID,
                     final String worldName,
                     final int xpStored) {
        this.inventory = inventory;
        this.chestLocation = chestLocation;
        this.playerName = playerName;
        this.playerUUID = playerUUID;
        this.chestDate = chestDate;
        this.isRemovedBlock = isRemovedBlock;
        this.isInfinity = isInfinity;
        this.holographicTimer = holographicTimer;
        this.holographicTimerId = asTimerId;
        this.holographicStatusId = asStatusId;
        this.holographicOwnerId = asOwnerId;
        this.killerUUID = killerUUID;
        this.worldName = worldName;
        this.xpStored = xpStored;
    }

    public UUID getHolographicTimerId() {
        return holographicTimerId;
    }

    public UUID getHolographicOwnerId() {
        return holographicOwnerId;
    }

    public UUID getHolographicStatusId() {
        return holographicStatusId;
    }

    public void setHolographicOwnerId(UUID holographicOwnerId) {
        this.holographicOwnerId = holographicOwnerId;
    }

    public void setHolographicStatusId(UUID holographicStatusId) {
        this.holographicStatusId = holographicStatusId;
    }

    public void setHolographicTimerId(UUID holographicTimerId) {
        this.holographicTimerId = holographicTimerId;
    }

    public List<ItemStack> getInventory() {
        return inventory;
    }

    public void cleanInventory() {
        inventory = new ArrayList<>();
    }

    public void setInventory(List<ItemStack> inventory) {
        this.inventory = inventory;
    }

    public void setRemovedBlock(final boolean removedBlock) {
        this.isRemovedBlock = removedBlock;
    }

    public Location getChestLocation() {
        return chestLocation;
    }

    public String getPlayerName() {
        return playerName;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerStringUUID() {
        return playerUUID + "";
    }

    public Date getChestDate() {
        return chestDate;
    }

    public boolean isRemovedBlock() {
        return isRemovedBlock;
    }

    public boolean isInfinity() {
        return isInfinity;
    }

    public Location getHolographicTimer() {
        return holographicTimer;
    }

    public String getWorldName() {
        return worldName;
    }

    public UUID getKillerUUID() {
        return killerUUID;
    }

    public void setKillerUUID(UUID killerUUID) {
        this.killerUUID = killerUUID;
    }

    public boolean removeArmorStand() {
        final int radius = 1;
        final int armorStandShiftY = 1;

        if (chestLocation.getWorld() != null) {
            final boolean foliaRuntime = DeadChestLoader.getSchedulerAdapter().isFoliaLikeRuntime();
            final boolean shouldForceLoadChunk = !foliaRuntime;

            if (foliaRuntime && !isChunkLoaded()) {
                return false;
            }

            if (shouldForceLoadChunk) {
                LegacySupport.setForceLoaded(chestLocation.getChunk(), true);
            }

            Collection<Entity> entities = chestLocation.getWorld().getNearbyEntities(
                    new Location(
                            chestLocation.getWorld(),
                            chestLocation.getX(),
                            chestLocation.getY() + armorStandShiftY,
                            chestLocation.getZ()
                    ), radius, radius, radius);

            boolean isEmpty = entities.size() > 0;
            for (Entity entity : entities) {
                if (entity.getUniqueId().equals(holographicOwnerId)
                        || entity.getUniqueId().equals(holographicTimerId)
                        || entity.getUniqueId().equals(holographicStatusId)) {
                    entity.remove();
                }
            }

            if (shouldForceLoadChunk && isChunkForceLoaded()) {
                chestLocation.getWorld().unloadChunk(chestLocation.getBlockX() >> 4, chestLocation.getBlockZ() >> 4);
                LegacySupport.setForceLoaded(chestLocation.getChunk(), false);
            }

            return isEmpty;
        }
        return false;
    }

    public boolean isChunkLoaded() {
        return chestLocation.getWorld() == null ||
                chestLocation.getWorld().isChunkLoaded(
                        chestLocation.getBlockX() >> 4,
                        chestLocation.getBlockZ() >> 4
                );
    }

    public boolean isChunkForceLoaded() {
        return chestLocation.getWorld() == null ||
                LegacySupport.isForceLoaded(chestLocation.getWorld(),
                        chestLocation.getBlockX() >> 4,
                        chestLocation.getBlockZ() >> 4
                );
    }

    public int getXpStored() {
        return xpStored;
    }

    public void setXpStored(int xpStored) {
        this.xpStored = xpStored;
    }

    public void save(@Nonnull final Consumer<Boolean> containsChestOnLoc) {
        ChestDataRepository.saveAsync(this, containsChestOnLoc);
    }

    public void update(@Nonnull final Consumer<Boolean> containsChestOnLoc) {
        ChestDataRepository.updateAsync(this, containsChestOnLoc);
    }

    public void remove() {
        ChestDataRepository.removeAsync(this);
    }


    enum Indexes {WORLD_NAME, LOC_X, LOC_Y, LOC_Z}
}
