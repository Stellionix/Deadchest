package me.crylonz.deadchest.utils;

import me.crylonz.deadchest.Localization;

import java.util.*;

public enum ConfigKey {
    AUTO_UPDATE("updates.auto-check", "auto-update"),
    INDESTRUCTIBLE_CHEST("chest.indestructible", "IndestructibleChest", "IndestuctibleChest"),
    ONLY_OWNER_CAN_OPEN_CHEST("chest.owner-only-open", "OnlyOwnerCanOpenDeadChest"),
    DEADCHEST_DURATION("chest.duration-seconds", "DeadChestDuration"),
    MAX_DEAD_CHEST_PER_PLAYER("chest.max-per-player", "maxDeadChestPerPlayer"),
    LOG_DEADCHEST_ON_CONSOLE("logging.deadchest-create-to-console", "logDeadChestOnConsole"),
    REQUIRE_PERMISSION_TO_GENERATE("permissions.require-generate", "RequirePermissionToGenerate"),
    REQUIRE_PERMISSION_TO_GET_CHEST("permissions.require-claim", "RequirePermissionToGetChest"),
    REQUIRE_PERMISSION_TO_LIST_OWN("permissions.require-list-own", "RequirePermissionToListOwn"),
    AUTO_CLEANUP_ON_START("maintenance.cleanup-on-startup", "AutoCleanupOnStart"),
    GENERATE_DEADCHEST_IN_CREATIVE("generation.allow-in-creative", "GenerateDeadChestInCreative"),
    DISPLAY_POSITION_ON_DEATH("messages.display-position-on-death", "DisplayDeadChestPositionOnDeath"),
    ITEMS_DROPPED_AFTER_TIMEOUT("chest.drop-items-on-timeout", "ItemsDroppedAfterTimeOut"),
    LOOT_ENABLED("chest.loot.enabled", "chest.lootable-after-timeout"),
    LOOT_PUBLIC_DURATION("chest.loot.public-duration-seconds"),
    LOOT_DROP_ITEMS_ON_TIMEOUT("chest.loot.drop-items-on-timeout"),
    LOOT_PUBLIC_ACCESS_OWNER("chest.loot.public-access.owner"),
    LOOT_PUBLIC_ACCESS_KILLER("chest.loot.public-access.killer"),
    LOOT_PUBLIC_ACCESS_OTHER_PLAYERS("chest.loot.public-access.other-players"),
    WORLD_GUARD_DETECTION("integrations.worldguard.enabled", "EnableWorldGuardDetection"),
    WORLD_GUARD_FLAG_DEFAULT("integrations.worldguard.default-allow", "EnableWorldGuardFlagDefault"),
    DROP_MODE("chest.recovery-mode", "DropMode"),
    DROP_BLOCK("chest.block-type", "DropBlock"),
    GENERATE_ON_LAVA("generation.allow-on-lava", "GenerateOnLava"),
    GENERATE_ON_WATER("generation.allow-on-water", "GenerateOnWater"),
    GENERATE_ON_RAILS("generation.allow-on-rails", "GenerateOnRails"),
    GENERATE_IN_MINECART("generation.allow-in-minecart", "GenerateInMinecart"),
    GENERATE_IN_THE_END("generation.allow-in-end-worlds", "GenerateInTheEnd"),
    EXCLUDED_WORLDS("filters.excluded-worlds", "ExcludedWorld"),
    EXCLUDED_ITEMS("filters.excluded-items", "ExcludedItems"),
    IGNORED_ITEMS("filters.ignored-items", "IgnoredItems"),
    STORE_XP("xp.store-on-death", "StoreXP"),
    STORE_XP_PERCENTAGE("xp.store-percentage", "StoreXPPercentage"),
    KEEP_INVENTORY_ON_PVP_DEATH("pvp.keep-inventory-on-player-kill", "KeepInventoryOnPvpDeath"),
    ITEM_DURABILITY_LOSS_ON_DEATH("durability.loss-on-death-percent", "item-durability-loss-on-death"),
    EFFECT_ANIMATION_ENABLED("visuals.effect-animation.enabled"),
    EFFECT_ANIMATION_STYLE("visuals.effect-animation.style"),
    EFFECT_ANIMATION_RADIUS("visuals.effect-animation.radius"),
    EFFECT_ANIMATION_SPEED("visuals.effect-animation.speed"),
    PICKUP_ANIMATION_ENABLED("visuals.pickup-animation.enabled"),
    PICKUP_ANIMATION_PARTICLE("visuals.pickup-animation.particle"),
    PICKUP_ANIMATION_COUNT("visuals.pickup-animation.count"),
    PICKUP_ANIMATION_OFFSET_X("visuals.pickup-animation.offset-x"),
    PICKUP_ANIMATION_OFFSET_Y("visuals.pickup-animation.offset-y"),
    PICKUP_ANIMATION_OFFSET_Z("visuals.pickup-animation.offset-z"),
    PICKUP_ANIMATION_SPEED("visuals.pickup-animation.speed"),
    PICKUP_ANIMATION_Y_SHIFT("visuals.pickup-animation.y-shift"),
    PICKUP_SOUND_ENABLED("visuals.sound.pickup.enabled"),
    PICKUP_SOUND_NAME("visuals.sound.pickup.name"),
    PICKUP_SOUND_VOLUME("visuals.sound.pickup.volume"),
    PICKUP_SOUND_PITCH("visuals.sound.pickup.pitch"),
    LOCALIZATION_LANGUAGE("localization.language", "language");

    private static final Map<String, ConfigKey> BY_CANONICAL = new HashMap<>();

    static {
        for (ConfigKey key : values()) {
            BY_CANONICAL.put(key.canonicalPath, key);
        }
    }

    private final String canonicalPath;
    private final String[] aliases;

    ConfigKey(final String canonicalPath, final String... aliases) {
        this.canonicalPath = canonicalPath;
        this.aliases = aliases;
    }

    public String canonicalPath() {
        return canonicalPath;
    }

    public String[] aliases() {
        return Arrays.copyOf(aliases, aliases.length);
    }

    public Object defaultValue() {
        switch (this) {
            case AUTO_UPDATE:
            case INDESTRUCTIBLE_CHEST:
            case ONLY_OWNER_CAN_OPEN_CHEST:
            case GENERATE_DEADCHEST_IN_CREATIVE:
            case DISPLAY_POSITION_ON_DEATH:
            case LOOT_PUBLIC_ACCESS_OWNER:
            case LOOT_PUBLIC_ACCESS_KILLER:
            case LOOT_PUBLIC_ACCESS_OTHER_PLAYERS:
            case GENERATE_ON_LAVA:
            case GENERATE_ON_WATER:
            case GENERATE_ON_RAILS:
            case GENERATE_IN_MINECART:
            case GENERATE_IN_THE_END:
            case EFFECT_ANIMATION_ENABLED:
            case PICKUP_ANIMATION_ENABLED:
            case PICKUP_SOUND_ENABLED:
                return true;
            case LOG_DEADCHEST_ON_CONSOLE:
            case REQUIRE_PERMISSION_TO_GENERATE:
            case REQUIRE_PERMISSION_TO_GET_CHEST:
            case REQUIRE_PERMISSION_TO_LIST_OWN:
            case AUTO_CLEANUP_ON_START:
            case ITEMS_DROPPED_AFTER_TIMEOUT:
            case LOOT_ENABLED:
            case LOOT_DROP_ITEMS_ON_TIMEOUT:
            case WORLD_GUARD_DETECTION:
            case WORLD_GUARD_FLAG_DEFAULT:
            case STORE_XP:
            case KEEP_INVENTORY_ON_PVP_DEATH:
                return false;
            case DEADCHEST_DURATION:
            case LOOT_PUBLIC_DURATION:
                return 300;
            case MAX_DEAD_CHEST_PER_PLAYER:
                return 15;
            case STORE_XP_PERCENTAGE:
                return 100;
            case ITEM_DURABILITY_LOSS_ON_DEATH:
                return 0;
            case PICKUP_ANIMATION_COUNT:
                return 22;
            case EFFECT_ANIMATION_RADIUS:
                return 0.8D;
            case EFFECT_ANIMATION_SPEED:
                return 1.1D;
            case PICKUP_ANIMATION_OFFSET_X:
                return 0.45D;
            case PICKUP_ANIMATION_OFFSET_Y:
                return 0.5D;
            case PICKUP_ANIMATION_OFFSET_Z:
                return 0.45D;
            case PICKUP_ANIMATION_SPEED:
                return 0.08D;
            case PICKUP_ANIMATION_Y_SHIFT:
                return 0.55D;
            case PICKUP_SOUND_VOLUME:
                return 1.2D;
            case PICKUP_SOUND_PITCH:
                return 1.0D;
            case DROP_MODE:
                return "inventory";
            case DROP_BLOCK:
                return "chest";
            case EFFECT_ANIMATION_STYLE:
                return "ender";
            case PICKUP_ANIMATION_PARTICLE:
                return "FIREWORK";
            case PICKUP_SOUND_NAME:
                return "ENTITY_PLAYER_LEVELUP";
            case LOCALIZATION_LANGUAGE:
                return "en";
            case EXCLUDED_WORLDS:
            case EXCLUDED_ITEMS:
            case IGNORED_ITEMS:
                return Collections.emptyList();
            default:
                throw new IllegalStateException("Unsupported config key: " + this);
        }
    }

    public ConfigValueType valueType() {
        switch (this) {
            case AUTO_UPDATE:
            case INDESTRUCTIBLE_CHEST:
            case ONLY_OWNER_CAN_OPEN_CHEST:
            case LOG_DEADCHEST_ON_CONSOLE:
            case REQUIRE_PERMISSION_TO_GENERATE:
            case REQUIRE_PERMISSION_TO_GET_CHEST:
            case REQUIRE_PERMISSION_TO_LIST_OWN:
            case AUTO_CLEANUP_ON_START:
            case GENERATE_DEADCHEST_IN_CREATIVE:
            case DISPLAY_POSITION_ON_DEATH:
            case ITEMS_DROPPED_AFTER_TIMEOUT:
            case LOOT_ENABLED:
            case LOOT_DROP_ITEMS_ON_TIMEOUT:
            case LOOT_PUBLIC_ACCESS_OWNER:
            case LOOT_PUBLIC_ACCESS_KILLER:
            case LOOT_PUBLIC_ACCESS_OTHER_PLAYERS:
            case WORLD_GUARD_DETECTION:
            case WORLD_GUARD_FLAG_DEFAULT:
            case GENERATE_ON_LAVA:
            case GENERATE_ON_WATER:
            case GENERATE_ON_RAILS:
            case GENERATE_IN_MINECART:
            case GENERATE_IN_THE_END:
            case STORE_XP:
            case KEEP_INVENTORY_ON_PVP_DEATH:
            case EFFECT_ANIMATION_ENABLED:
            case PICKUP_ANIMATION_ENABLED:
            case PICKUP_SOUND_ENABLED:
                return ConfigValueType.BOOLEAN;
            case DEADCHEST_DURATION:
            case MAX_DEAD_CHEST_PER_PLAYER:
            case LOOT_PUBLIC_DURATION:
            case PICKUP_ANIMATION_COUNT:
            case STORE_XP_PERCENTAGE:
            case ITEM_DURABILITY_LOSS_ON_DEATH:
                return ConfigValueType.INTEGER;
            case EFFECT_ANIMATION_RADIUS:
            case EFFECT_ANIMATION_SPEED:
            case PICKUP_ANIMATION_OFFSET_X:
            case PICKUP_ANIMATION_OFFSET_Y:
            case PICKUP_ANIMATION_OFFSET_Z:
            case PICKUP_ANIMATION_SPEED:
            case PICKUP_ANIMATION_Y_SHIFT:
            case PICKUP_SOUND_VOLUME:
            case PICKUP_SOUND_PITCH:
                return ConfigValueType.DOUBLE;
            case DROP_MODE:
            case DROP_BLOCK:
            case EFFECT_ANIMATION_STYLE:
            case PICKUP_ANIMATION_PARTICLE:
            case PICKUP_SOUND_NAME:
            case LOCALIZATION_LANGUAGE:
                return ConfigValueType.STRING;
            case EXCLUDED_WORLDS:
            case EXCLUDED_ITEMS:
            case IGNORED_ITEMS:
                return ConfigValueType.STRING_LIST;
            default:
                throw new IllegalStateException("Unsupported config key: " + this);
        }
    }

    public List<String> suggestedValues() {
        switch (this) {
            case AUTO_UPDATE:
            case INDESTRUCTIBLE_CHEST:
            case ONLY_OWNER_CAN_OPEN_CHEST:
            case LOG_DEADCHEST_ON_CONSOLE:
            case REQUIRE_PERMISSION_TO_GENERATE:
            case REQUIRE_PERMISSION_TO_GET_CHEST:
            case REQUIRE_PERMISSION_TO_LIST_OWN:
            case AUTO_CLEANUP_ON_START:
            case GENERATE_DEADCHEST_IN_CREATIVE:
            case DISPLAY_POSITION_ON_DEATH:
            case ITEMS_DROPPED_AFTER_TIMEOUT:
            case LOOT_ENABLED:
            case LOOT_DROP_ITEMS_ON_TIMEOUT:
            case LOOT_PUBLIC_ACCESS_OWNER:
            case LOOT_PUBLIC_ACCESS_KILLER:
            case LOOT_PUBLIC_ACCESS_OTHER_PLAYERS:
            case WORLD_GUARD_DETECTION:
            case WORLD_GUARD_FLAG_DEFAULT:
            case GENERATE_ON_LAVA:
            case GENERATE_ON_WATER:
            case GENERATE_ON_RAILS:
            case GENERATE_IN_MINECART:
            case GENERATE_IN_THE_END:
            case STORE_XP:
            case KEEP_INVENTORY_ON_PVP_DEATH:
            case EFFECT_ANIMATION_ENABLED:
            case PICKUP_ANIMATION_ENABLED:
            case PICKUP_SOUND_ENABLED:
                return Arrays.asList("true", "false");
            case DROP_MODE:
                return Arrays.asList("inventory", "ground-drop");
            case DROP_BLOCK:
                return Arrays.asList("chest", "player-head", "barrel", "shulker-box", "ender-chest");
            case EFFECT_ANIMATION_STYLE:
                return Arrays.asList("soul", "flame", "ender");
            case LOCALIZATION_LANGUAGE:
                return Localization.getBundledLanguages();
            default:
                return Collections.emptyList();
        }
    }

    public boolean supportsInteractiveEdit() {
        return this == IGNORED_ITEMS;
    }

    public static ConfigKey fromCanonicalPath(String path) {
        return BY_CANONICAL.get(path);
    }

    @Override
    public String toString() {
        return canonicalPath;
    }
}
