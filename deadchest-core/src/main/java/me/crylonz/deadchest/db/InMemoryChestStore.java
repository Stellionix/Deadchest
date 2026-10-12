package me.crylonz.deadchest.db;

import me.crylonz.deadchest.ChestData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory store of active {@link ChestData} entries while the plugin is running.
 * <p>
 * This class keeps two synchronized indexes:
 * - {@code chestDataMap}: location -> chest, used for direct runtime lookup/interactions by block location.
 * - {@code players}: player UUID -> set of chest locations, used as a per-player index.
 * <p>
 * The {@code players} map does not store player objects; it stores ownership links so the plugin can
 * efficiently count/list/remove a player's chests without scanning all entries in {@code chestDataMap}
 * each time.
 * <p>
 * Persistence is handled by {@link ChestDataRepository}; this store is the runtime state layer.
 */
public class InMemoryChestStore {
    private final Map<Location, ChestData> chestDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Location>> playerChestLocations = new ConcurrentHashMap<>();

    /**
     * Adds a chest entry to both in-memory indexes.
     *
     * @param chestData chest data to add
     */
    public void addChestData(final ChestData chestData) {
        if (me.crylonz.deadchest.RecoveryJournal.blocked(chestData.getId())) return;
        if (me.crylonz.deadchest.PlayerTransfers.blocked(chestData.getId())) chestData.beginCreation();
        me.crylonz.deadchest.ChestLifecycle.created(chestData);
        addPlayerData(chestData);
        chestDataMap.put(normalizeLocation(chestData.getChestLocation()), chestData);

    }

    /**
     * Upserts a batch of chest entries in memory and persists them asynchronously.
     * If an entry already exists at the same location, the old player index link is removed first.
     *
     * @param chestThatNeedsBeUpdated chests to insert/update
     */
    public void addListOfChestData(final Set<ChestData> chestThatNeedsBeUpdated) {
        chestThatNeedsBeUpdated.forEach((chestData) -> {
            if (chestData == null || me.crylonz.deadchest.RecoveryJournal.blocked(chestData.getId())) {
                return;
            }
            Location normalizedLocation = normalizeLocation(chestData.getChestLocation());
            ChestData old = chestDataMap.get(normalizedLocation);
            if (old != null) {
                removePlayerData(old);
            }
            addPlayerData(chestData);
            chestDataMap.put(normalizedLocation, chestData);
        });
        ChestDataRepository.saveAllAsync(chestThatNeedsBeUpdated);
    }

    /**
     * Replaces the current in-memory state with the provided chest list.
     * Both indexes are cleared and rebuilt from scratch.
     *
     * @param chests new runtime chest dataset
     */
    public void setChestData(final List<ChestData> chests) {
        if (chests != null) {
            chestDataMap.clear();
            playerChestLocations.clear();
            chests.forEach(this::addChestData);
        }
    }

    /**
     * Returns chest data stored at the given location.
     *
     * @param location chest location key
     * @return matching chest data or {@code null} when absent
     */
    @Nullable
    public ChestData getChestData(@Nonnull final Location location) {
        return chestDataMap.get(normalizeLocation(location));
    }

    /**
     * Returns an immutable snapshot of locations linked to the given player.
     *
     * @param player player to query
     * @return immutable set of locations or {@code null} if the player has no chest
     */
    @Nullable
    public Set<Location> getPlayerLinkedData(@Nonnull final Player player) {
        final Set<Location> locations = playerChestLocations.get(player.getUniqueId());
        if (locations == null) {
            return null;
        }
        return Collections.unmodifiableSet(new HashSet<>(locations));
    }

    /**
     * Iterates over all chest entries owned by a player and forwards them to a consumer.
     *
     * @param player       player to query
     * @param dataConsumer callback invoked for each linked chest
     */
    public void getPlayerLinkedDeadChestData(@Nonnull final Player player, @Nonnull final Consumer<ChestData> dataConsumer) {
        final Set<Location> locations = getPlayerLinkedData(player);
        if (locations != null) {
            locations.forEach(location -> {
                final ChestData chestData = getChestData(location);
                if (chestData != null) {
                    dataConsumer.accept(chestData);
                }
            });
        }
    }

    /**
     * Checks whether a chest already exists in memory at the same location.
     *
     * @param chestData chest to check
     * @return {@code true} if present in memory, otherwise {@code false}
     */
    public boolean contains(@Nonnull final ChestData chestData) {
        return getChestData(chestData.getChestLocation()) != null;
    }

    /**
     * Indicates whether there are no active chest entries in memory.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return chestDataMap.isEmpty();
    }

    /**
     * Returns a read-only view of the location -> chest index.
     *
     * @return immutable map of all in-memory chest entries
     */
    public Map<Location, ChestData> getAllChestData() {
        return Collections.unmodifiableMap(chestDataMap);
    }

    /**
     * Returns the number of chests currently linked to a player.
     *
     * @param player player to query
     * @return chest count for the player
     */
    public int getPlayerChestAmount(@Nonnull final Player player) {
        final Set<Location> locations = playerChestLocations.get(player.getUniqueId());
        if (locations != null) {
            return locations.size();
        }
        return 0;
    }

    /**
     * Returns an immutable snapshot list of all in-memory chest entries.
     *
     * @return immutable chest list snapshot
     */
    public List<ChestData> getChestData() {
        return Collections.unmodifiableList(new ArrayList<>(chestDataMap.values()));
    }

    /**
     * Removes a single chest from world/memory and schedules database deletion.
     *
     * @param chest chest to remove
     */
    public void removeChestData(@Nonnull final ChestData chest) {
        if (me.crylonz.deadchest.ChestLifecycle.enabled() && !chest.isTransitioning()) {
            me.crylonz.deadchest.ChestLifecycle.transition(chest, me.crylonz.deadchest.ChestLifecycle.Outcome.DELETED,
                    "system/API", () -> removeChestData(chest));
            return;
        }
        chest.removeArmorStand();
        chest.remove();
        removePlayerData(chest);
        chestDataMap.remove(normalizeLocation(chest.getChestLocation()));
    }

    /** Transfer coordinator already committed the UUID-specific deletion. */
    public void forgetChestData(ChestData chest) {
        Location key=normalizeLocation(chest.getChestLocation());
        ChestData current=chestDataMap.get(key);
        if (current!=null && current.getId().equals(chest.getId()) && chestDataMap.remove(key,current)) removePlayerData(current);
    }

    /**
     * Removes multiple chests from world/memory and schedules batch database deletion.
     *
     * @param chests chest collection to remove
     */
    public void removeChestDataList(@Nonnull final Collection<ChestData> chests) {
        if (!me.crylonz.deadchest.ChestLifecycle.enabled()) {
            chests.forEach(chestData -> {
                chestData.removeArmorStand();
                this.chestDataMap.remove(normalizeLocation(chestData.getChestLocation()));
                removePlayerData(chestData);
            });
            ChestDataRepository.removeBatchAsync(chests);
            return;
        }
        chests.forEach(this::removeChestData);
    }

    /**
     * Clears all in-memory state and schedules full database cleanup.
     */
    public void clearChestData() {
        if (!me.crylonz.deadchest.ChestLifecycle.enabled()) {
            chestDataMap.clear(); playerChestLocations.clear(); ChestDataRepository.clearAsync(); return;
        }
        new ArrayList<>(chestDataMap.values()).forEach(this::removeChestData);
    }

    /**
     * Persists the current in-memory dataset synchronously.
     */
    public void save() {
        ChestDataRepository.batchSave(chestDataMap.values());
    }

    /**
     * Adds/updates the ownership index entry for one chest.
     *
     * @param chestData chest whose owner index must be updated
     */
    private void addPlayerData(final ChestData chestData) {
        if (chestData.getPlayerUUID() == null) {
            return;
        }
        playerChestLocations.computeIfAbsent(chestData.getPlayerUUID(), uuid -> ConcurrentHashMap.newKeySet())
                .add(normalizeLocation(chestData.getChestLocation()));
    }

    /**
     * Removes one ownership link from the player index and prunes empty sets.
     *
     * @param chestData chest whose owner index must be removed
     */
    private void removePlayerData(final ChestData chestData) {
        if (chestData.getPlayerUUID() == null) return;
        final UUID playerUUID = chestData.getPlayerUUID();
        Set<Location> locations = playerChestLocations.get(playerUUID);
        if (locations != null) {
            locations.remove(normalizeLocation(chestData.getChestLocation()));
            if (locations.isEmpty()) {
                playerChestLocations.remove(playerUUID);
            }
        }
    }

    /**
     * Normalizes a location to block precision for stable map keys.
     * <p>
     * This removes yaw/pitch and keeps only world + block coordinates,
     * so lookups remain consistent between runtime and data loaded from storage.
     *
     * @param location source location
     * @return normalized block-based location key
     */
    private Location normalizeLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
