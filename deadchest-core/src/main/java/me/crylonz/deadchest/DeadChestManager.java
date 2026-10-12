package me.crylonz.deadchest;

import me.crylonz.deadchest.db.InMemoryChestStore;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.EffectAnimationStyle;
import me.crylonz.deadchest.utils.ExpiredActionType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static me.crylonz.deadchest.DeadChestLoader.*;
import static me.crylonz.deadchest.utils.Utils.*;

public class DeadChestManager {
    private static final Map<EffectAnimationStyle, Particle> styleParticles = new ConcurrentHashMap<>();
    private static final Set<EffectAnimationStyle> unresolvedParticleStyles = EnumSet.noneOf(EffectAnimationStyle.class);

    /**
     * Remove all active deadchests
     *
     * @return number of deadchests removed
     */
    public static int cleanAllDeadChests() { return cleanAllDeadChests("system"); }
    public static int cleanAllDeadChests(String actor) {
        int chestDataRemoved = 0;
        final InMemoryChestStore inMemoryChestStore = DeadChestLoader.getChestDataCache();
        final Map<Location, ChestData> chestDataList = inMemoryChestStore.getAllChestData();

        if (chestDataList != null && !chestDataList.isEmpty()) {
            for (final ChestData chestData : chestDataList.values()) {
                if (chestData != null) {
                    DeadChestLoader.getSchedulerAdapter().executeAtLocation(chestData.getChestLocation(), () -> removeDeadChest(chestData, actor));
                    chestDataRemoved++;
                }
            }
        }
        return chestDataRemoved;
    }

    /**
     * Generate a hologram at the given position
     *
     * @param location position to place
     * @param text     text to display
     * @param shiftX   x shifting
     * @param shiftY   y shifting
     * @param shiftZ   z shifting
     * @return the generated armorstand
     */
    public static ArmorStand generateHologram(Location location, String text, float shiftX, float shiftY, float shiftZ, boolean isTimer) {
        if (location != null && location.getWorld() != null) {
            Location holoLoc = new Location(location.getWorld(),
                    location.getX() + shiftX,
                    location.getY() + shiftY + 2,
                    location.getZ() + shiftZ);

            ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(holoLoc, EntityType.ARMOR_STAND);
            armorStand.setInvulnerable(true);
            armorStand.setSmall(true);
            armorStand.setGravity(false);
            armorStand.setCanPickupItems(false);
            armorStand.setVisible(false);
            armorStand.setCollidable(false);
            armorStand.setMetadata("deadchest", new FixedMetadataValue(plugin, isTimer));
            armorStand.setCustomName(text);
            armorStand.setSilent(true);
            armorStand.setMarker(true);
            armorStand.setCustomNameVisible(true);

            return armorStand;
        }
        return null;
    }

    /**
     * get the number of deadchest for a player
     *
     * @param p player
     * @return number of deadchests
     */
    public static int playerDeadChestAmount(Player p) {
        int count = 0;
        if (p != null) {
            count = DeadChestLoader.getChestDataCache().getPlayerChestAmount(p);
        }
        return count;
    }

    /**
     * Regeneration of metadata for the holograms linked to a single chest.
     */
    static void reloadMetaData(ChestData chestData, Collection<Entity> nearbyEntities) {
        if (chestData == null || nearbyEntities == null) {
            return;
        }

        for (Entity nearbyEntity : nearbyEntities) {
            if (nearbyEntity.getUniqueId().equals(chestData.getHolographicOwnerId())) {
                nearbyEntity.setMetadata("deadchest", new FixedMetadataValue(DeadChestLoader.plugin, false));
                nearbyEntity.setMetadata(HOLOGRAM_LINE_KEY, new FixedMetadataValue(DeadChestLoader.plugin, "owner"));
            } else if (nearbyEntity.getUniqueId().equals(chestData.getHolographicStatusId())) {
                nearbyEntity.setMetadata("deadchest", new FixedMetadataValue(DeadChestLoader.plugin, false));
                nearbyEntity.setMetadata(HOLOGRAM_LINE_KEY, new FixedMetadataValue(DeadChestLoader.plugin, "status"));
            } else if (nearbyEntity.getUniqueId().equals(chestData.getHolographicTimerId())) {
                nearbyEntity.setMetadata("deadchest", new FixedMetadataValue(DeadChestLoader.plugin, true));
                nearbyEntity.setMetadata(HOLOGRAM_LINE_KEY, new FixedMetadataValue(DeadChestLoader.plugin, "timer"));
            }
        }
    }

    public static boolean replaceDeadChestIfItDisappears(ChestData chestData) {
        World world = chestData.getChestLocation().getWorld();
        Location hologramSearchLocation = getHologramSearchLocation(chestData);

        if (world == null || hologramSearchLocation == null) {
            return false;
        }

        Collection<Entity> entityList = world.getNearbyEntities(hologramSearchLocation, 1.0, 1.0, 1.0);
        boolean isLinkedToDeadchest = entityList.stream().anyMatch(entity ->
                entity.getUniqueId().equals(chestData.getHolographicOwnerId()) ||
                        entity.getUniqueId().equals(chestData.getHolographicStatusId()) ||
                        entity.getUniqueId().equals(chestData.getHolographicTimerId())
        );

        boolean needToUpdateData = false;

        Block block = world.getBlockAt(chestData.getChestLocation());
        if (!isGraveBlock(block.getType())) {
            generateDeadChest(block, Bukkit.getPlayer(chestData.getPlayerUUID()));
            generateLog("Deadchest of [" + chestData.getPlayerName() + "] was corrupted. Deadchest fixed!");
            needToUpdateData = true;
        }

        if (!isLinkedToDeadchest) {
            for (Entity entity : entityList) {
                if (entity instanceof ArmorStand) {
                    entity.remove();
                }
            }

            ArmorStand[] holos = createHolograms(block, chestData.getPlayerName());
            chestData.setHolographicTimerId(holos[0].getUniqueId());
            chestData.setHolographicStatusId(holos[1].getUniqueId());
            chestData.setHolographicOwnerId(holos[2].getUniqueId());
            generateLog("Hologram Deadchest of [" + chestData.getPlayerName() + "] was corrupted. Hologram fixed!");
            needToUpdateData = true;
        }

        return needToUpdateData;
    }


    public static ExpiredActionType handleExpirateDeadChest(ChestData chestData, Date date) {
        if (hasChestExpired(chestData, date)) {
            final boolean dropItemsAfterTimeout = shouldDropItemsWhenChestExpires();

            Location loc = chestData.getChestLocation();

            if (loc.getWorld() != null) {
                if (!chestData.isRemovedBlock()) {
                    chestData.setRemovedBlock(true);
                    loc.getWorld().getBlockAt(loc).setType(Material.AIR);
                }
                if (dropItemsAfterTimeout) {
                    for (ItemStack itemStack : chestData.getInventory()) {
                        if (itemStack != null) {
                            loc.getWorld().dropItemNaturally(loc, itemStack);
                        }
                    }
                    chestData.cleanInventory();
                }
            }
            if (chestData.removeArmorStand()) {
                return ExpiredActionType.REMOVED_ARMORSTAND;
            }
            return ExpiredActionType.FAIL_REMOVE_ARMORSTAND;
        }
        return ExpiredActionType.NOT_EXPIRED;
    }

    public static boolean hasChestExpired(ChestData chestData, Date date) {
        if (chestData == null || date == null) {
            return false;
        }

        final long expirationTime = getExpirationTimeMillis(chestData, date);
        if (expirationTime < 0L) {
            return false;
        }

        return expirationTime < date.getTime();
    }

    public static boolean isPublicLootPhase(ChestData chestData, Date date) {
        if (chestData == null || date == null || chestData.isInfinity() || !config.getBoolean(ConfigKey.LOOT_ENABLED)) {
            return false;
        }

        final int privateDuration = config.getInt(ConfigKey.DEADCHEST_DURATION);
        if (privateDuration == 0) {
            return false;
        }

        final long privateEnd = chestData.getChestDate().getTime() + privateDuration * 1000L;
        if (date.getTime() <= privateEnd) {
            return false;
        }

        final int publicDuration = config.getInt(ConfigKey.LOOT_PUBLIC_DURATION);
        if (publicDuration == 0) {
            return true;
        }

        return date.getTime() <= privateEnd + publicDuration * 1000L;
    }

    public static long getCurrentPhaseRemainingMillis(ChestData chestData, Date date) {
        if (chestData == null || date == null || chestData.isInfinity()) {
            return -1L;
        }

        final int privateDuration = config.getInt(ConfigKey.DEADCHEST_DURATION);
        if (privateDuration == 0) {
            return -1L;
        }

        final long now = date.getTime();
        final long privateEnd = chestData.getChestDate().getTime() + privateDuration * 1000L;
        if (now <= privateEnd) {
            return privateEnd - now;
        }

        if (!config.getBoolean(ConfigKey.LOOT_ENABLED)) {
            return 0L;
        }

        final int publicDuration = config.getInt(ConfigKey.LOOT_PUBLIC_DURATION);
        if (publicDuration == 0) {
            return -1L;
        }

        final long publicEnd = privateEnd + publicDuration * 1000L;
        return Math.max(0L, publicEnd - now);
    }

    public static long getExpirationTimeMillis(ChestData chestData, Date date) {
        if (chestData == null || date == null || chestData.isInfinity()) {
            return -1L;
        }

        final int privateDuration = config.getInt(ConfigKey.DEADCHEST_DURATION);
        if (privateDuration == 0) {
            return -1L;
        }

        final long privateEnd = chestData.getChestDate().getTime() + privateDuration * 1000L;
        if (!config.getBoolean(ConfigKey.LOOT_ENABLED)) {
            return privateEnd;
        }

        final int publicDuration = config.getInt(ConfigKey.LOOT_PUBLIC_DURATION);
        if (publicDuration == 0) {
            return -1L;
        }

        return privateEnd + publicDuration * 1000L;
    }

    private static boolean shouldDropItemsWhenChestExpires() {
        if (config.getBoolean(ConfigKey.LOOT_ENABLED)) {
            return config.getBoolean(ConfigKey.LOOT_DROP_ITEMS_ON_TIMEOUT);
        }
        return config.getBoolean(ConfigKey.ITEMS_DROPPED_AFTER_TIMEOUT);
    }

    public static void updateTimer(ChestData chestData, Date date) {
        Location chestTimer = getHologramSearchLocation(chestData);

        if (chestTimer != null && chestTimer.getWorld() != null && chestData.isChunkLoaded()) {

            Collection<Entity> entityList = chestTimer.getWorld().getNearbyEntities(chestTimer, 1.0, 1.0, 1.0);
            for (Entity entity : entityList) {
                if (entity.getType().equals(EntityType.ARMOR_STAND)) {
                    if (!entity.hasMetadata("deadchest")) {
                        reloadMetaData(chestData, entityList);
                    }
                    String lineType = resolveHologramLineType(entity);
                    if ("timer".equals(lineType)) {
                        long remainingMillis = getCurrentPhaseRemainingMillis(chestData, date);
                        long diffSeconds = Math.abs(remainingMillis / 1000 % 60);
                        long diffMinutes = Math.abs(remainingMillis / (60 * 1000) % 60);
                        long diffHours = Math.abs(remainingMillis / (60 * 60 * 1000));

                        if (remainingMillis >= 0L) {
                            entity.setCustomName(local.format("hologram.timer", diffHours, diffMinutes, diffSeconds));
                        } else {
                            entity.setCustomName(local.get("chest.infinity"));
                        }
                    } else if ("status".equals(lineType)) {
                        entity.setCustomName(getAccessStateLabel(chestData, date));
                    } else {
                        entity.setCustomName(getOwnerHologramText(chestData, date));
                    }
                }
            }
        }
    }

    public static String getOwnerHologramText(ChestData chestData, Date date) {
        return local.format("hologram.owner", chestData.getPlayerName());
    }

    public static String getAccessStateLabel(ChestData chestData, Date date) {
        if (isPublicLootPhase(chestData, date)) {
            return getPublicAccessStateLabel();
        }
        if (!config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)) {
            return local.get("hologram.state.open");
        }
        return local.get("hologram.state.private");
    }

    private static String getPublicAccessStateLabel() {
        final boolean owner = config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OWNER);
        final boolean killer = config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_KILLER);
        final boolean others = config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OTHER_PLAYERS);

        if (others) {
            return local.get("hologram.state.public");
        }
        if (killer && !owner) {
            return local.get("hologram.state.killer");
        }
        if (owner && !killer) {
            return local.get("hologram.state.owner");
        }
        return local.get("hologram.state.share");
    }

    public static void animateSoulOrbit(ChestData chestData, long nowMs) {
        if (chestData.isRemovedBlock() || !chestData.isChunkLoaded()) {
            return;
        }

        final Location chestLocation = chestData.getChestLocation();
        final World world = chestLocation.getWorld();
        if (world == null || !isGraveBlock(world.getBlockAt(chestLocation).getType())) {
            return;
        }

        final EffectAnimationStyle style = DeadChestLoader.getConfiguredAnimationStyle();
        final Particle soulParticle = resolveStyleParticle(style);
        if (soulParticle == null) {
            return;
        }

        final double radius = clamp(config.getDouble(ConfigKey.EFFECT_ANIMATION_RADIUS), 0.25D, 2.0D);
        final double speed = clamp(config.getDouble(ConfigKey.EFFECT_ANIMATION_SPEED), 0.1D, 8.0D);

        final double centerX = chestLocation.getX() + 0.5D;
        final double minY = chestLocation.getY() + 0.06D;
        final double maxY = chestLocation.getY() + 1.58D;
        final double centerZ = chestLocation.getZ() + 0.5D;

        final double basePhase = (nowMs / 1000.0D) * speed
                + (Math.abs(chestData.getPlayerUUID().hashCode()) % 360) * Math.PI / 180.0D;

        // Spawn two opposite "souls" to create an orbit effect around the chest.
        for (int i = 0; i < 2; i++) {
            final double angle = basePhase + (i * Math.PI);
            final double x = centerX + (Math.cos(angle) * radius);
            final double oscillation = (Math.sin(basePhase * 1.15D + i) + 1.0D) * 0.5D;
            final double y = minY + (maxY - minY) * oscillation;
            final double z = centerZ + (Math.sin(angle) * radius);

            world.spawnParticle(soulParticle, x, y, z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
        }

    }

    public static void handleChestTick(ChestData chestData, Date now) {
        if (chestData == null) {
            return;
        }

        World world = chestData.getChestLocation().getWorld();
        if (world == null) {
            return;
        }

        if (chestData.isTransitioning()) return;
        if (ChestLifecycle.enabled() && hasChestExpired(chestData, now)) {
            ChestLifecycle.transition(chestData, shouldDropItemsWhenChestExpires() ? ChestLifecycle.Outcome.DROPPED : ChestLifecycle.Outcome.EXPIRED,
                    "expiry", () -> {
                        handleExpirateDeadChest(chestData, now);
                        DeadChestLoader.getChestDataCache().removeChestData(chestData);
                    });
            return;
        }
        updateTimer(chestData, now);

        final ExpiredActionType expiredActionType = handleExpirateDeadChest(chestData, now);
        if (expiredActionType != ExpiredActionType.NOT_EXPIRED) {
            if (expiredActionType == ExpiredActionType.FAIL_REMOVE_ARMORSTAND) {
                chestData.update(ignored -> {
                });
            } else {
                DeadChestLoader.getChestDataCache().removeChestData(chestData);
                generateLog("Deadchest of [" + chestData.getPlayerName() + "] has expired in " + world.getName());
            }
            return;
        }

        if (!chestData.isChunkLoaded()) {
            return;
        }

        if (replaceDeadChestIfItDisappears(chestData)) {
            chestData.update(ignored -> {
            });
        }
    }

    public static void removeDeadChest(ChestData chestData) { removeDeadChest(chestData, "system"); }
    public static void removeDeadChest(ChestData chestData, String actor) {
        if (ChestLifecycle.enabled() && chestData != null) {
            ChestLifecycle.transition(chestData, ChestLifecycle.Outcome.DELETED, actor, () -> ChestTransfers.remove(chestData)); return;
        }
        if (chestData == null) {
            return;
        }

        Location loc = chestData.getChestLocation();
        World world = loc.getWorld();
        if (world != null) {
            world.getBlockAt(loc).setType(Material.AIR);
        }
        DeadChestLoader.getChestDataCache().removeChestData(chestData);
    }

    private static Particle resolveStyleParticle(EffectAnimationStyle style) {
        if (unresolvedParticleStyles.contains(style)) {
            return null;
        }
        if (styleParticles.containsKey(style)) {
            return styleParticles.get(style);
        }

        for (String particleName : style.particleCandidates()) {
            Particle resolved = valueOfOrNull(Particle.class, particleName);
            if (resolved != null) {
                styleParticles.put(style, resolved);
                return resolved;
            }
        }
        unresolvedParticleStyles.add(style);
        return null;
    }

    private static <T extends Enum<T>> T valueOfOrNull(Class<T> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Location getHologramSearchLocation(ChestData chestData) {
        if (chestData == null) {
            return null;
        }

        Location holographicTimer = chestData.getHolographicTimer();
        Location chestLocation = chestData.getChestLocation();
        if (holographicTimer == null) {
            return null;
        }
        if (chestLocation == null) {
            return holographicTimer;
        }

        World chestWorld = chestLocation.getWorld();
        if (chestWorld == null) {
            return holographicTimer;
        }

        if (holographicTimer.getWorld() == chestWorld) {
            return holographicTimer;
        }

        return new Location(
                chestWorld,
                holographicTimer.getX(),
                holographicTimer.getY(),
                holographicTimer.getZ(),
                holographicTimer.getYaw(),
                holographicTimer.getPitch()
        );
    }

    private static String resolveHologramLineType(Entity entity) {
        if (entity.hasMetadata(HOLOGRAM_LINE_KEY) && !entity.getMetadata(HOLOGRAM_LINE_KEY).isEmpty()) {
            return entity.getMetadata(HOLOGRAM_LINE_KEY).get(0).asString();
        }
        if (entity.hasMetadata("deadchest") && !entity.getMetadata("deadchest").isEmpty() && entity.getMetadata("deadchest").get(0).asBoolean()) {
            return "timer";
        }
        return "owner";
    }
}
