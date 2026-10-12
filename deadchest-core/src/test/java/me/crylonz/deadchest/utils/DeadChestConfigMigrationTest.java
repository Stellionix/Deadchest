package me.crylonz.deadchest.utils;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeadChestConfigMigrationTest {

    private Plugin plugin;
    private FileConfiguration fileConfiguration;

    @BeforeEach
    void setUp() {
        DeadChestConfig.clearCache();
        plugin = mock(Plugin.class);
        fileConfiguration = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(fileConfiguration);
    }

    @Test
    void replacesLegacyOverflowModeWithPartialInventoryMode() {
        fileConfiguration.set("chest.recovery-mode", "inventory-then-ground");
        DeadChestConfig config = new DeadChestConfig(plugin);
        config.register(ConfigKey.DROP_MODE);
        assertEquals("inventory", config.getString(ConfigKey.DROP_MODE));
        assertEquals(1, config.getInt(ConfigKey.DROP_MODE));
        assertEquals("inventory", fileConfiguration.getString("chest.recovery-mode"));
        assertEquals(Arrays.asList("inventory", "ground-drop"), ConfigKey.DROP_MODE.suggestedValues());
        verify(plugin).saveConfig();
    }

    @Test
    void readsLegacyV1KeysWithV2ConfigPaths() {
        fileConfiguration.set("auto-update", false);
        fileConfiguration.set("OnlyOwnerCanOpenDeadChest", false);
        fileConfiguration.set("IndestuctibleChest", false);
        fileConfiguration.set("DropMode", 2);
        fileConfiguration.set("DropBlock", 5);
        fileConfiguration.set("ExcludedWorld", Arrays.asList("world", "nether"));

        DeadChestConfig config = new DeadChestConfig(plugin);
        config.register(ConfigKey.AUTO_UPDATE.toString(), true);
        config.register(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST.toString(), true);
        config.register(ConfigKey.INDESTRUCTIBLE_CHEST.toString(), true);
        config.register(ConfigKey.DROP_MODE.toString(), "inventory-then-ground");
        config.register(ConfigKey.DROP_BLOCK.toString(), "chest");
        config.register(ConfigKey.LOOT_ENABLED.toString(), false);
        config.register(ConfigKey.LOOT_PUBLIC_DURATION.toString(), 300);
        config.register(ConfigKey.LOOT_DROP_ITEMS_ON_TIMEOUT.toString(), false);
        config.register(ConfigKey.LOOT_PUBLIC_ACCESS_OWNER.toString(), true);
        config.register(ConfigKey.LOOT_PUBLIC_ACCESS_KILLER.toString(), true);
        config.register(ConfigKey.LOOT_PUBLIC_ACCESS_OTHER_PLAYERS.toString(), true);
        config.register(ConfigKey.EXCLUDED_WORLDS.toString(), Arrays.asList());

        assertFalse(config.getBoolean(ConfigKey.AUTO_UPDATE));
        assertFalse(config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST));
        assertFalse(config.getBoolean(ConfigKey.INDESTRUCTIBLE_CHEST));
        assertEquals(2, config.getInt(ConfigKey.DROP_MODE));
        assertEquals(5, config.getInt(ConfigKey.DROP_BLOCK));
        assertFalse(config.getBoolean(ConfigKey.LOOT_ENABLED));
        assertEquals(300, config.getInt(ConfigKey.LOOT_PUBLIC_DURATION));
        assertFalse(config.getBoolean(ConfigKey.LOOT_DROP_ITEMS_ON_TIMEOUT));
        assertTrue(config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OWNER));
        assertTrue(config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_KILLER));
        assertTrue(config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OTHER_PLAYERS));
        assertEquals(Arrays.asList("world", "nether"), config.getArray(ConfigKey.EXCLUDED_WORLDS));
    }

    @Test
    void readsV2CanonicalKeysAndStringEnums() {
        fileConfiguration.set("updates.auto-check", true);
        fileConfiguration.set("chest.owner-only-open", true);
        fileConfiguration.set("chest.recovery-mode", "ground-drop");
        fileConfiguration.set("chest.block-type", "ender-chest");

        DeadChestConfig config = new DeadChestConfig(plugin);
        config.register(ConfigKey.AUTO_UPDATE.toString(), false);
        config.register(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST.toString(), false);
        config.register(ConfigKey.DROP_MODE.toString(), "inventory-then-ground");
        config.register(ConfigKey.DROP_BLOCK.toString(), "chest");

        assertTrue(config.getBoolean(ConfigKey.AUTO_UPDATE));
        assertTrue(config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST));
        assertEquals(2, config.getInt(ConfigKey.DROP_MODE));
        assertEquals(5, config.getInt(ConfigKey.DROP_BLOCK));
    }

    @Test
    void canonicalV2KeyTakesPrecedenceOverLegacyAlias() {
        fileConfiguration.set("DropMode", 1);
        fileConfiguration.set("chest.recovery-mode", "ground-drop");

        DeadChestConfig config = new DeadChestConfig(plugin);
        config.register(ConfigKey.DROP_MODE.toString(), "inventory-then-ground");

        assertEquals(2, config.getInt(ConfigKey.DROP_MODE));
    }

    @Test
    void ignoredItemsPreserveSerializedCustomItemStacks() {
        MockBukkit.mock();
        try {
            ItemStack customItem = new ItemStack(Material.DIAMOND_SWORD, 1);
            customItem.editMeta(meta -> meta.setDisplayName("Boss sword"));
            fileConfiguration.set("filters.ignored-items", Arrays.asList("DIAMOND", customItem));

            DeadChestConfig config = new DeadChestConfig(plugin);
            config.register(ConfigKey.IGNORED_ITEMS.toString(), Arrays.asList());

            assertEquals(2, config.getIgnoredEntries().size());
            assertEquals("DIAMOND", config.getIgnoredEntries().get(0));
            assertTrue(config.getIgnoredEntries().get(1) instanceof ItemStack);
            ItemStack loadedItem = (ItemStack) config.getIgnoredEntries().get(1);
            assertEquals(Material.DIAMOND_SWORD, loadedItem.getType());
            assertEquals("Boss sword", loadedItem.getItemMeta().getDisplayName());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void updateConfigMigratesFromLegacyBackupIntoFreshV2Template(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Path legacyPath = tempDir.resolve("config.legacy.yml");

        String legacyYaml = ""
                + "DeadChestDuration: 6000\n"
                + "IndestuctibleChest: false\n"
                + "GenerateOnLava: false\n";
        Files.writeString(configPath, legacyYaml);

        Plugin migrationPlugin = mock(Plugin.class);
        AtomicReference<FileConfiguration> configRef =
                new AtomicReference<>(YamlConfiguration.loadConfiguration(configPath.toFile()));

        when(migrationPlugin.getConfig()).thenAnswer(invocation -> configRef.get());
        when(migrationPlugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(migrationPlugin.getLogger()).thenReturn(Logger.getLogger("test"));

        doAnswer(invocation -> {
            String v2Template = ""
                    + "config-version: 2\n"
                    + "chest:\n"
                    + "  duration-seconds: 300\n"
                    + "  indestructible: true\n"
                    + "generation:\n"
                    + "  allow-on-lava: true\n";
            Files.writeString(configPath, v2Template);
            configRef.set(YamlConfiguration.loadConfiguration(configPath.toFile()));
            return null;
        }).when(migrationPlugin).saveDefaultConfig();

        doAnswer(invocation -> {
            configRef.set(YamlConfiguration.loadConfiguration(configPath.toFile()));
            return null;
        }).when(migrationPlugin).reloadConfig();
        doAnswer(invocation -> {
            configRef.get().save(configPath.toFile());
            return null;
        }).when(migrationPlugin).saveConfig();

        DeadChestConfig config = new DeadChestConfig(migrationPlugin);
        config.register(ConfigKey.DEADCHEST_DURATION.toString(), 300);
        config.register(ConfigKey.INDESTRUCTIBLE_CHEST.toString(), true);
        config.register(ConfigKey.GENERATE_ON_LAVA.toString(), true);

        config.updateConfig();

        FileConfiguration migrated = YamlConfiguration.loadConfiguration(configPath.toFile());

        assertTrue(Files.exists(legacyPath), "Legacy backup should be created");
        assertEquals(6000, migrated.getInt(ConfigKey.DEADCHEST_DURATION.toString()));
        assertFalse(migrated.getBoolean(ConfigKey.INDESTRUCTIBLE_CHEST.toString()));
        assertFalse(migrated.getBoolean(ConfigKey.GENERATE_ON_LAVA.toString()));
        assertEquals(2, migrated.getInt("config-version"));

        assertNull(migrated.get("DeadChestDuration"));
        assertNull(migrated.get("IndestuctibleChest"));
        assertNull(migrated.get("GenerateOnLava"));
    }

    @Test
    void updateConfigMigratesAllLegacyFieldsToV2(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("config.yml");

        String legacyYaml = ""
                + "auto-update: false\n"
                + "OnlyOwnerCanOpenDeadChest: false\n"
                + "DeadChestDuration: 6000\n"
                + "IndestuctibleChest: false\n"
                + "maxDeadChestPerPlayer: 42\n"
                + "logDeadChestOnConsole: true\n"
                + "RequirePermissionToGenerate: true\n"
                + "RequirePermissionToGetChest: true\n"
                + "RequirePermissionToListOwn: true\n"
                + "AutoCleanupOnStart: true\n"
                + "GenerateDeadChestInCreative: false\n"
                + "DisplayDeadChestPositionOnDeath: false\n"
                + "DropMode: 2\n"
                + "DropBlock: 4\n"
                + "ItemsDroppedAfterTimeOut: true\n"
                + "GenerateOnLava: false\n"
                + "GenerateOnWater: false\n"
                + "GenerateOnRails: false\n"
                + "GenerateInMinecart: false\n"
                + "GenerateInTheEnd: false\n"
                + "StoreXP: true\n"
                + "StoreXPPercentage: 90\n"
                + "KeepInventoryOnPvpDeath: true\n"
                + "EnableWorldGuardDetection: true\n"
                + "EnableWorldGuardFlagDefault: true\n"
                + "item-durability-loss-on-death: 2\n"
                + "ExcludedWorld:\n"
                + "  - world\n"
                + "  - world_nether\n"
                + "ExcludedItems:\n"
                + "  - DIAMOND\n"
                + "  - DROPPER\n"
                + "IgnoredItems:\n"
                + "  - STONE\n";
        Files.writeString(configPath, legacyYaml);

        Plugin migrationPlugin = mock(Plugin.class);
        AtomicReference<FileConfiguration> configRef =
                new AtomicReference<>(YamlConfiguration.loadConfiguration(configPath.toFile()));

        when(migrationPlugin.getConfig()).thenAnswer(invocation -> configRef.get());
        when(migrationPlugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(migrationPlugin.getLogger()).thenReturn(Logger.getLogger("test"));

        doAnswer(invocation -> {
            String v2Template = ""
                    + "config-version: 2\n"
                    + "updates:\n"
                    + "  auto-check: true\n"
                    + "chest:\n"
                    + "  owner-only-open: true\n"
                    + "  duration-seconds: 300\n"
                    + "  indestructible: true\n"
                    + "  max-per-player: 15\n"
                    + "  recovery-mode: inventory-then-ground\n"
                    + "  block-type: chest\n"
                    + "  drop-items-on-timeout: false\n"
                    + "  loot:\n"
                    + "    enabled: false\n"
                    + "    public-duration-seconds: 300\n"
                    + "    drop-items-on-timeout: false\n"
                    + "    public-access:\n"
                    + "      owner: true\n"
                    + "      killer: true\n"
                    + "      other-players: true\n"
                    + "permissions:\n"
                    + "  require-generate: false\n"
                    + "  require-claim: false\n"
                    + "  require-list-own: false\n"
                    + "maintenance:\n"
                    + "  cleanup-on-startup: false\n"
                    + "generation:\n"
                    + "  allow-in-creative: true\n"
                    + "  allow-on-lava: true\n"
                    + "  allow-on-water: true\n"
                    + "  allow-on-rails: true\n"
                    + "  allow-in-minecart: true\n"
                    + "  allow-in-end-worlds: true\n"
                    + "messages:\n"
                    + "  display-position-on-death: true\n"
                    + "xp:\n"
                    + "  store-on-death: false\n"
                    + "  store-percentage: 100\n"
                    + "pvp:\n"
                    + "  keep-inventory-on-player-kill: false\n"
                    + "integrations:\n"
                    + "  worldguard:\n"
                    + "    enabled: false\n"
                    + "    default-allow: false\n"
                    + "durability:\n"
                    + "  loss-on-death-percent: 0\n"
                    + "logging:\n"
                    + "  deadchest-create-to-console: false\n"
                    + "filters:\n"
                    + "  excluded-worlds:\n"
                    + "    - excluded_world_example\n"
                    + "  excluded-items:\n"
                    + "    - EXCLUDED_ITEM_EXAMPLE\n"
                    + "  ignored-items:\n"
                    + "    - IGNORED_ITEM_EXAMPLE\n";
            Files.writeString(configPath, v2Template);
            configRef.set(YamlConfiguration.loadConfiguration(configPath.toFile()));
            return null;
        }).when(migrationPlugin).saveDefaultConfig();

        doAnswer(invocation -> {
            configRef.set(YamlConfiguration.loadConfiguration(configPath.toFile()));
            return null;
        }).when(migrationPlugin).reloadConfig();
        doAnswer(invocation -> {
            configRef.get().save(configPath.toFile());
            return null;
        }).when(migrationPlugin).saveConfig();

        DeadChestConfig config = new DeadChestConfig(migrationPlugin);
        config.register(ConfigKey.AUTO_UPDATE.toString(), true);
        config.register(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST.toString(), true);
        config.register(ConfigKey.DEADCHEST_DURATION.toString(), 300);
        config.register(ConfigKey.INDESTRUCTIBLE_CHEST.toString(), true);
        config.register(ConfigKey.MAX_DEAD_CHEST_PER_PLAYER.toString(), 15);
        config.register(ConfigKey.LOG_DEADCHEST_ON_CONSOLE.toString(), false);
        config.register(ConfigKey.REQUIRE_PERMISSION_TO_GENERATE.toString(), false);
        config.register(ConfigKey.REQUIRE_PERMISSION_TO_GET_CHEST.toString(), false);
        config.register(ConfigKey.REQUIRE_PERMISSION_TO_LIST_OWN.toString(), false);
        config.register(ConfigKey.AUTO_CLEANUP_ON_START.toString(), false);
        config.register(ConfigKey.GENERATE_DEADCHEST_IN_CREATIVE.toString(), true);
        config.register(ConfigKey.DISPLAY_POSITION_ON_DEATH.toString(), true);
        config.register(ConfigKey.DROP_MODE.toString(), "inventory-then-ground");
        config.register(ConfigKey.DROP_BLOCK.toString(), "chest");
        config.register(ConfigKey.ITEMS_DROPPED_AFTER_TIMEOUT.toString(), false);
        config.register(ConfigKey.LOOT_ENABLED.toString(), false);
        config.register(ConfigKey.LOOT_PUBLIC_DURATION.toString(), 300);
        config.register(ConfigKey.LOOT_DROP_ITEMS_ON_TIMEOUT.toString(), false);
        config.register(ConfigKey.LOOT_PUBLIC_ACCESS_OWNER.toString(), true);
        config.register(ConfigKey.LOOT_PUBLIC_ACCESS_KILLER.toString(), true);
        config.register(ConfigKey.LOOT_PUBLIC_ACCESS_OTHER_PLAYERS.toString(), true);
        config.register(ConfigKey.GENERATE_ON_LAVA.toString(), true);
        config.register(ConfigKey.GENERATE_ON_WATER.toString(), true);
        config.register(ConfigKey.GENERATE_ON_RAILS.toString(), true);
        config.register(ConfigKey.GENERATE_IN_MINECART.toString(), true);
        config.register(ConfigKey.GENERATE_IN_THE_END.toString(), true);
        config.register(ConfigKey.STORE_XP.toString(), false);
        config.register(ConfigKey.STORE_XP_PERCENTAGE.toString(), 100);
        config.register(ConfigKey.KEEP_INVENTORY_ON_PVP_DEATH.toString(), false);
        config.register(ConfigKey.WORLD_GUARD_DETECTION.toString(), false);
        config.register(ConfigKey.WORLD_GUARD_FLAG_DEFAULT.toString(), false);
        config.register(ConfigKey.ITEM_DURABILITY_LOSS_ON_DEATH.toString(), 0);
        config.register(ConfigKey.EXCLUDED_WORLDS.toString(), Arrays.asList());
        config.register(ConfigKey.EXCLUDED_ITEMS.toString(), Arrays.asList());
        config.register(ConfigKey.IGNORED_ITEMS.toString(), Arrays.asList());

        config.updateConfig();

        FileConfiguration migrated = YamlConfiguration.loadConfiguration(configPath.toFile());

        assertEquals(2, migrated.getInt("config-version"));
        assertFalse(migrated.getBoolean("updates.auto-check"));
        assertFalse(migrated.getBoolean("chest.owner-only-open"));
        assertEquals(6000, migrated.getInt("chest.duration-seconds"));
        assertFalse(migrated.getBoolean("chest.indestructible"));
        assertEquals(42, migrated.getInt("chest.max-per-player"));
        assertTrue(migrated.getBoolean("logging.deadchest-create-to-console"));
        assertTrue(migrated.getBoolean("permissions.require-generate"));
        assertTrue(migrated.getBoolean("permissions.require-claim"));
        assertTrue(migrated.getBoolean("permissions.require-list-own"));
        assertTrue(migrated.getBoolean("maintenance.cleanup-on-startup"));
        assertFalse(migrated.getBoolean("generation.allow-in-creative"));
        assertFalse(migrated.getBoolean("messages.display-position-on-death"));
        assertEquals("ground-drop", migrated.getString("chest.recovery-mode"));
        assertEquals("shulker-box", migrated.getString("chest.block-type"));
        assertTrue(migrated.getBoolean("chest.drop-items-on-timeout"));
        assertFalse(migrated.getBoolean("chest.loot.enabled"));
        assertEquals(300, migrated.getInt("chest.loot.public-duration-seconds"));
        assertFalse(migrated.getBoolean("chest.loot.drop-items-on-timeout"));
        assertTrue(migrated.getBoolean("chest.loot.public-access.owner"));
        assertTrue(migrated.getBoolean("chest.loot.public-access.killer"));
        assertTrue(migrated.getBoolean("chest.loot.public-access.other-players"));
        assertFalse(migrated.getBoolean("generation.allow-on-lava"));
        assertFalse(migrated.getBoolean("generation.allow-on-water"));
        assertFalse(migrated.getBoolean("generation.allow-on-rails"));
        assertFalse(migrated.getBoolean("generation.allow-in-minecart"));
        assertFalse(migrated.getBoolean("generation.allow-in-end-worlds"));
        assertTrue(migrated.getBoolean("xp.store-on-death"));
        assertEquals(90, migrated.getInt("xp.store-percentage"));
        assertTrue(migrated.getBoolean("pvp.keep-inventory-on-player-kill"));
        assertTrue(migrated.getBoolean("integrations.worldguard.enabled"));
        assertTrue(migrated.getBoolean("integrations.worldguard.default-allow"));
        assertEquals(2, migrated.getInt("durability.loss-on-death-percent"));
        assertEquals(Arrays.asList("world", "world_nether"), migrated.getStringList("filters.excluded-worlds"));
        assertEquals(Arrays.asList("DIAMOND", "DROPPER"), migrated.getStringList("filters.excluded-items"));
        assertEquals(Arrays.asList("STONE"), migrated.getStringList("filters.ignored-items"));

        assertNull(migrated.get("auto-update"));
        assertNull(migrated.get("OnlyOwnerCanOpenDeadChest"));
        assertNull(migrated.get("DeadChestDuration"));
        assertNull(migrated.get("IndestuctibleChest"));
        assertNull(migrated.get("maxDeadChestPerPlayer"));
        assertNull(migrated.get("RequirePermissionToGenerate"));
        assertNull(migrated.get("RequirePermissionToGetChest"));
        assertNull(migrated.get("RequirePermissionToListOwn"));
        assertNull(migrated.get("AutoCleanupOnStart"));
        assertNull(migrated.get("GenerateDeadChestInCreative"));
        assertNull(migrated.get("DisplayDeadChestPositionOnDeath"));
        assertNull(migrated.get("DropMode"));
        assertNull(migrated.get("DropBlock"));
        assertNull(migrated.get("ItemsDroppedAfterTimeOut"));
        assertNull(migrated.get("GenerateOnLava"));
        assertNull(migrated.get("GenerateOnWater"));
        assertNull(migrated.get("GenerateOnRails"));
        assertNull(migrated.get("GenerateInMinecart"));
        assertNull(migrated.get("GenerateInTheEnd"));
        assertNull(migrated.get("StoreXP"));
        assertNull(migrated.get("StoreXPPercentage"));
        assertNull(migrated.get("KeepInventoryOnPvpDeath"));
        assertNull(migrated.get("EnableWorldGuardDetection"));
        assertNull(migrated.get("EnableWorldGuardFlagDefault"));
        assertNull(migrated.get("item-durability-loss-on-death"));
        assertNull(migrated.get("ExcludedWorld"));
        assertNull(migrated.get("ExcludedItems"));
        assertNull(migrated.get("IgnoredItems"));
    }
}
