package me.crylonz.deadchest;

import me.crylonz.deadchest.commands.DCCommandExecutor;
import me.crylonz.deadchest.commands.DCTabCompletion;
import me.crylonz.deadchest.db.*;
import me.crylonz.deadchest.deps.worldguard.WorldGuardSoftDependenciesChecker;
import me.crylonz.deadchest.legacy.OldChestData;
import me.crylonz.deadchest.scheduler.SchedulerAdapter;
import me.crylonz.deadchest.scheduler.SchedulerTaskHandle;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.DeadChestConfig;
import me.crylonz.deadchest.utils.EffectAnimationStyle;
import me.crylonz.deadchest.utils.IgnoreItemRules;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;

import static me.crylonz.deadchest.DeadChestManager.*;
import static me.crylonz.deadchest.db.IgnoreItemListRepository.clearIgnoreItems;
import static me.crylonz.deadchest.db.IgnoreItemListRepository.loadIgnoreItems;
import static me.crylonz.deadchest.utils.Utils.generateLog;

public class DeadChestLoader {

    public static Logger log = Logger.getLogger("Minecraft");
    public static FileManager fileManager;
    private static final InMemoryChestStore chestData = new InMemoryChestStore();

    public static WorldGuardSoftDependenciesChecker wgsdc = null;
    public static ArrayList<Material> graveBlocks = new ArrayList<>();
    public static Localization local;
    public static JavaPlugin javaPlugin;
    public static Plugin plugin;

    public static Inventory ignoreList;

    public static boolean bstats = true;
    public static boolean isChangesNeedToBeSave = false;
    public static boolean hasWindCharge;

    public static DeadChestConfig config;

    public static SQLite db;
    public static SQLExecutor sqlExecutor = new SQLExecutor();
    private SchedulerTaskHandle maintenanceTask;
    private SchedulerTaskHandle animationTask;
    private static SchedulerAdapter scheduler;
    private static Plugin schedulerPluginOwner;

    public DeadChestLoader(Plugin dcPlugin, JavaPlugin dcjavaPlugin) {
        super();
        javaPlugin = dcjavaPlugin;
        plugin = dcPlugin;
        scheduler = new SchedulerAdapter(dcPlugin);
        schedulerPluginOwner = dcPlugin;
    }

    public void enable() {

        // db parts
        db = new SQLite(plugin);
        db.init();
        IgnoreItemListRepository.initTable();

        local = new Localization(plugin);
        ignoreList = Bukkit.createInventory(new IgnoreInventoryHolder(), 36, local.get("gui.ignore-list.title"));
        config = new DeadChestConfig(plugin);
        fileManager = new FileManager(plugin);

        ChestDataRepository.initTable(/* migrate old chestData.yml config */ OldChestData::migrateOldChestData);

        ChestDataRepository.findAllAsync(chestData::setChestData, plugin);

        registerConfig();
        initializeConfig();

        if (config.getBoolean(ConfigKey.AUTO_CLEANUP_ON_START)) {
            cleanAllDeadChests();
        }


        // Which block can be used as grave ?
        graveBlocks.add(Material.CHEST);
        graveBlocks.add(Material.ENDER_CHEST);

        Material head = Material.getMaterial("PLAYER_HEAD");
        if (head == null)
            head = Material.getMaterial("SKULL");
        if (head != null)
            graveBlocks.add(head);

        final Material barrel = Material.getMaterial("BARREL");
        if (barrel != null)
            graveBlocks.add(barrel);
        final Material shulkerBox = Material.getMaterial("SHULKER_BOX");
        if (shulkerBox != null)
            graveBlocks.add(shulkerBox);

        Objects.requireNonNull(javaPlugin.getCommand("dc"), "Command dc not found")
                .setExecutor(new DCCommandExecutor(this));

        Objects.requireNonNull(javaPlugin.getCommand("dc")).setTabCompleter(new DCTabCompletion());

        launchRepeatingTask();
        checkIfHasWindCharge();
    }


    public void load() {
        boolean worldGuardEnabled = javaPlugin.getConfig().getBoolean(ConfigKey.WORLD_GUARD_DETECTION.toString());
        if (!worldGuardEnabled) {
            for (String legacyKey : ConfigKey.WORLD_GUARD_DETECTION.aliases()) {
                if (javaPlugin.getConfig().contains(legacyKey)) {
                    worldGuardEnabled = javaPlugin.getConfig().getBoolean(legacyKey);
                    break;
                }
            }
        }

        if (worldGuardEnabled) {
            try {
                wgsdc = new WorldGuardSoftDependenciesChecker();
                wgsdc.load();
                log.info("[DeadChest] Worldguard detected : Support is enabled");

            } catch (NoClassDefFoundError e) {
                log.info("[DeadChest] Worldguard not detected : Support is disabled");
            }
        } else {
            log.info("[DeadChest] Worldguard support disabled by user");
        }
    }

    public void disable() {
        scheduler.cancelTask(maintenanceTask);
        scheduler.cancelTask(animationTask);

        ChestDataRepository.saveAllAsync(getChestDataCache().getAllChestData().values());
        sqlExecutor.shutdown();
        db.close();
    }

    public static InMemoryChestStore getChestDataCache() {
        return chestData;
    }

    @Nullable
    public static ChestData getChestData(@Nonnull Location location) {
        return chestData.getChestData(location);
    }

    public static SchedulerAdapter getSchedulerAdapter() {
        if (scheduler == null || plugin != schedulerPluginOwner) {
            scheduler = new SchedulerAdapter(plugin);
            schedulerPluginOwner = plugin;
        }
        return scheduler;
    }

    public void registerConfig() {
        config.register(ConfigKey.AUTO_UPDATE.toString(), true);
        config.register(ConfigKey.INDESTRUCTIBLE_CHEST.toString(), true);
        config.register(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST.toString(), true);
        config.register(ConfigKey.DEADCHEST_DURATION.toString(), 300);
        config.register(ConfigKey.MAX_DEAD_CHEST_PER_PLAYER.toString(), 15);
        config.register(ConfigKey.LOG_DEADCHEST_ON_CONSOLE.toString(), false);
        config.register(ConfigKey.REQUIRE_PERMISSION_TO_GENERATE.toString(), false);
        config.register(ConfigKey.REQUIRE_PERMISSION_TO_GET_CHEST.toString(), false);
        config.register(ConfigKey.REQUIRE_PERMISSION_TO_LIST_OWN.toString(), false);
        config.register(ConfigKey.AUTO_CLEANUP_ON_START.toString(), false);
        config.register(ConfigKey.GENERATE_DEADCHEST_IN_CREATIVE.toString(), true);
        config.register(ConfigKey.DISPLAY_POSITION_ON_DEATH.toString(), true);
        config.register(ConfigKey.ITEMS_DROPPED_AFTER_TIMEOUT.toString(), false);
        config.register(ConfigKey.LOOT_ENABLED.toString(), false);
        config.register(ConfigKey.LOOT_PUBLIC_DURATION.toString(), 300);
        config.register(ConfigKey.LOOT_DROP_ITEMS_ON_TIMEOUT.toString(), false);
        config.register(ConfigKey.LOOT_PUBLIC_ACCESS_OWNER.toString(), true);
        config.register(ConfigKey.LOOT_PUBLIC_ACCESS_KILLER.toString(), true);
        config.register(ConfigKey.LOOT_PUBLIC_ACCESS_OTHER_PLAYERS.toString(), true);
        config.register(ConfigKey.WORLD_GUARD_DETECTION.toString(), false);
        config.register(ConfigKey.WORLD_GUARD_FLAG_DEFAULT.toString(), false);
        config.register(ConfigKey.DROP_MODE.toString(), "inventory-then-ground");
        config.register(ConfigKey.DROP_BLOCK.toString(), "chest");
        config.register(ConfigKey.ITEM_DURABILITY_LOSS_ON_DEATH.toString(), 0);
        config.register(ConfigKey.EFFECT_ANIMATION_ENABLED.toString(), true);
        config.register(ConfigKey.EFFECT_ANIMATION_STYLE.toString(), EffectAnimationStyle.SOUL.id());
        config.register(ConfigKey.EFFECT_ANIMATION_RADIUS.toString(), 0.8D);
        config.register(ConfigKey.EFFECT_ANIMATION_SPEED.toString(), 1.1D);
        config.register(ConfigKey.PICKUP_ANIMATION_ENABLED.toString(), true);
        config.register(ConfigKey.PICKUP_ANIMATION_PARTICLE.toString(), "TOTEM");
        config.register(ConfigKey.PICKUP_ANIMATION_COUNT.toString(), 22);
        config.register(ConfigKey.PICKUP_ANIMATION_OFFSET_X.toString(), 0.45D);
        config.register(ConfigKey.PICKUP_ANIMATION_OFFSET_Y.toString(), 0.5D);
        config.register(ConfigKey.PICKUP_ANIMATION_OFFSET_Z.toString(), 0.45D);
        config.register(ConfigKey.PICKUP_ANIMATION_SPEED.toString(), 0.08D);
        config.register(ConfigKey.PICKUP_ANIMATION_Y_SHIFT.toString(), 0.55D);
        config.register(ConfigKey.PICKUP_SOUND_ENABLED.toString(), true);
        config.register(ConfigKey.PICKUP_SOUND_NAME.toString(), "ENTITY_EXPERIENCE_ORB_PICKUP");
        config.register(ConfigKey.PICKUP_SOUND_VOLUME.toString(), 1.2D);
        config.register(ConfigKey.PICKUP_SOUND_PITCH.toString(), 1.0D);
        config.register(ConfigKey.GENERATE_ON_LAVA.toString(), true);
        config.register(ConfigKey.GENERATE_ON_WATER.toString(), true);
        config.register(ConfigKey.GENERATE_ON_RAILS.toString(), true);
        config.register(ConfigKey.GENERATE_IN_MINECART.toString(), true);
        config.register(ConfigKey.GENERATE_IN_THE_END.toString(), true);
        config.register(ConfigKey.EXCLUDED_WORLDS.toString(), Collections.emptyList());
        config.register(ConfigKey.EXCLUDED_ITEMS.toString(), Collections.emptyList());
        config.register(ConfigKey.IGNORED_ITEMS.toString(), Collections.emptyList());
        config.register(ConfigKey.STORE_XP.toString(), false);
        config.register(ConfigKey.STORE_XP_PERCENTAGE.toString(), 100);
        config.register(ConfigKey.KEEP_INVENTORY_ON_PVP_DEATH.toString(), false);
        config.register(ConfigKey.LOCALIZATION_LANGUAGE.toString(), "en");
    }

    private void initializeConfig() {

        // plugin config file
        if (!fileManager.getConfigFile().exists()) {
            plugin.saveDefaultConfig();
        } else {
            config.updateConfig();
        }
        plugin.reloadConfig();

        // localization jsons
        local.reloadLanguage(config.getString(ConfigKey.LOCALIZATION_LANGUAGE));

        // ignore list inventory title now follows selected language
        ignoreList = Bukkit.createInventory(new IgnoreInventoryHolder(), 36, local.get("gui.ignore-list.title"));
        migrateLegacyIgnoreItemsToConfig();
        loadIgnoreIntoInventoryFromConfig(ignoreList);
    }

    public static void loadIgnoreIntoInventoryFromConfig(Inventory inventory) {
        if (inventory == null) {
            return;
        }

        inventory.clear();
        for (Object ignoredEntry : config.getIgnoredEntries()) {
            ItemStack displayItem = IgnoreItemRules.toDisplayItem(ignoredEntry);
            if (displayItem != null) {
                inventory.addItem(displayItem);
            }
        }
    }

    public static void saveIgnoreInventoryToConfig(Inventory inventory) {
        config.setIgnoredEntries(IgnoreItemRules.fromInventory(inventory == null ? null : inventory.getContents()));
        loadIgnoreIntoInventoryFromConfig(inventory);
    }

    private void migrateLegacyIgnoreItemsToConfig() {
        if (!config.getIgnoredEntries().isEmpty()) {
            return;
        }

        List<Object> migratedItems = IgnoreItemRules.fromInventory(loadIgnoreItems().toArray(new ItemStack[0]));

        if (migratedItems.isEmpty()) {
            return;
        }

        config.setIgnoredEntries(migratedItems);
        clearIgnoreItems();
        log.info("[DeadChest] Migrated legacy /dc ignore entries from SQLite to config.yml filters.ignored-items.");
    }

    public static void handleEvent() {
        final Map<Location, ChestData> allChestData = getChestDataCache().getAllChestData();
        if (!allChestData.isEmpty()) {
            final Date now = new Date();
            for (ChestData chestData : allChestData.values()) {
                if (chestData == null) {
                    generateLog("Deadchest of [null] has invalid data set.");
                    continue;
                }

                scheduler.executeAtLocation(chestData.getChestLocation(), () -> handleChestTick(chestData, now));
            }
        }
    }

    private void launchRepeatingTask() {
        maintenanceTask = scheduler.runGlobalRepeating(DeadChestLoader::handleEvent, 20L, 20L);
        animationTask = scheduler.runGlobalRepeating(DeadChestLoader::handleAnimationEvent, 20L, 4L);
    }

    public static void handleAnimationEvent() {
        if (!config.getBoolean(ConfigKey.EFFECT_ANIMATION_ENABLED)) {
            return;
        }

        final Map<Location, ChestData> allChestData = getChestDataCache().getAllChestData();
        if (allChestData.isEmpty()) {
            return;
        }

        final long nowMs = System.currentTimeMillis();
        for (ChestData chestData : allChestData.values()) {
            if (chestData != null) {
                scheduler.executeAtLocation(chestData.getChestLocation(), () -> animateSoulOrbit(chestData, nowMs));
            }
        }
    }

    public static EffectAnimationStyle getConfiguredAnimationStyle() {
        EffectAnimationStyle style = EffectAnimationStyle.fromInput(config.getString(ConfigKey.EFFECT_ANIMATION_STYLE));
        return style == null ? EffectAnimationStyle.SOUL : style;
    }

    public DeadChestConfig getDataConfig() {
        return config;
    }

    private void checkIfHasWindCharge(){
        try {
            Class.forName("org.bukkit.entity.WindCharge");
            hasWindCharge = true;
        } catch (ClassNotFoundException e) {
            hasWindCharge = false;
        }
    }
}
