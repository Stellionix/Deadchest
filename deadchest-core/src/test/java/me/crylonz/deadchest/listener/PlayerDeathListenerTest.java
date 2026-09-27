package me.crylonz.deadchest.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.block.BlockMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import be.seeseemelk.mockbukkit.inventory.InventoryMock;
import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.DeadChestManager;
import me.crylonz.deadchest.Localization;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.DeadChestConfig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static me.crylonz.deadchest.utils.ConfigKey.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerDeathListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PlayerDeathListener listener;

    // Static mock for holograms
    private MockedStatic<DeadChestManager> hologramMock;

    // Helpers
    private DeadChestConfig cfg;
    private Inventory ignoreListInv;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new WorldMock(Material.GRASS_BLOCK, 64);
        server.addWorld(world);

        player = server.addPlayer("Steve");
        player.teleport(new Location(world, 0, 65, 0)); // air block by default above ground

        // Minimal plugin + statics
        DeadChestLoader.plugin = MockBukkit.createMockPlugin();
        DeadChestLoader.log = Logger.getLogger("DeadChestTest");

        DeadChestLoader.getChestDataCache().setChestData(new ArrayList<>());
        DeadChestLoader.fileManager = mock(me.crylonz.deadchest.FileManager.class);

        // Localization stub
        DeadChestLoader.local = new Localization();

        // Ignore-list inventory present to avoid NPE in cleanup
        ignoreListInv = new InventoryMock(null, InventoryType.CHEST);
        DeadChestLoader.ignoreList = ignoreListInv;

        // Default config (happy path) – each test can override specific keys
        cfg = mock(DeadChestConfig.class);

        // Booleans
        when(cfg.getBoolean(REQUIRE_PERMISSION_TO_GENERATE)).thenReturn(false);
        when(cfg.getBoolean(GENERATE_ON_LAVA)).thenReturn(true);
        when(cfg.getBoolean(GENERATE_ON_WATER)).thenReturn(true);
        when(cfg.getBoolean(ConfigKey.GENERATE_ON_RAILS)).thenReturn(true);
        when(cfg.getBoolean(ConfigKey.GENERATE_IN_MINECART)).thenReturn(true);
        when(cfg.getBoolean(ConfigKey.STORE_XP)).thenReturn(false);
        when(cfg.getBoolean(ConfigKey.DISPLAY_POSITION_ON_DEATH)).thenReturn(false);
        when(cfg.getBoolean(ConfigKey.LOG_DEADCHEST_ON_CONSOLE)).thenReturn(false);
        when(cfg.getBoolean(GENERATE_DEADCHEST_IN_CREATIVE)).thenReturn(true);
        when(cfg.getBoolean(KEEP_INVENTORY_ON_PVP_DEATH)).thenReturn(false);

        // Ints
        when(cfg.getInt(ConfigKey.MAX_DEAD_CHEST_PER_PLAYER)).thenReturn(10);
        when(cfg.getInt(ConfigKey.ITEM_DURABILITY_LOSS_ON_DEATH)).thenReturn(0);
        when(cfg.getInt(ConfigKey.DROP_BLOCK)).thenReturn(0); // default CHEST

        // Arrays
        when(cfg.getArray(ConfigKey.EXCLUDED_WORLDS)).thenReturn(new ArrayList<>());
        when(cfg.getArray(ConfigKey.EXCLUDED_ITEMS)).thenReturn(new ArrayList<>());
        when(cfg.getArray(ConfigKey.IGNORED_ITEMS)).thenReturn(new ArrayList<>());
        when(cfg.getIgnoredEntries()).thenReturn(new ArrayList<>());

        DeadChestLoader.config = cfg;

        // Stub hologram creation to avoid spawning entities
        hologramMock = mockStatic(DeadChestManager.class);
        ArmorStand fakeStand = mock(ArmorStand.class);
        when(fakeStand.getLocation()).thenReturn(new Location(world, 0, 65, 0)); // évite NPE
        hologramMock.when(() -> DeadChestManager.generateHologram(
                any(Location.class), anyString(), anyFloat(), anyFloat(), anyFloat(), anyBoolean()
        )).thenReturn(fakeStand);

        listener = new PlayerDeathListener();
    }

    @AfterEach
    void tearDown() {
        if (hologramMock != null) {
            hologramMock.close();
        }
        MockBukkit.unmock();
    }

    /**
     * Utility to build a fresh PlayerDeathEvent with default values
     */
    private PlayerDeathEvent deathEvent() {
        List<ItemStack> drops = new ArrayList<>();
        // PlayerDeathEvent(player, drops, droppedExp, deathMessage)
        return new PlayerDeathEvent(player, drops, 0, "");
    }

    // -------------------- TESTS --------------------

    @Test
    void keepInventoryEarlyExit() {
        PlayerDeathEvent evt = deathEvent();
        evt.setKeepInventory(true);

        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(), "No chest should be created when keepInventory is already true");
    }

    @Test
    void excludedWorldEarlyExit() {
        when(cfg.getArray(ConfigKey.EXCLUDED_WORLDS)).thenReturn(new ArrayList<>(Collections.singleton(world.getName())));
        PlayerDeathEvent evt = deathEvent();

        // Give player one item so it would create chest if not excluded
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 1));

        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(), "No chest in excluded world");
    }

    @Test
    void creativeDisallowedEarlyExit() {
        when(cfg.getBoolean(GENERATE_DEADCHEST_IN_CREATIVE)).thenReturn(false);
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 1));

        PlayerDeathEvent evt = deathEvent();
        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(), "No chest in creative when disabled");
    }

    @Test
    void pvpKeepInventoryCase() {
        when(cfg.getBoolean(KEEP_INVENTORY_ON_PVP_DEATH)).thenReturn(true);
        PlayerMock killer = server.addPlayer("Alex");
        player.setKiller(killer); // PlayerMock API supports this

        // Drops that should be cleared by the listener in PVP keep-inv case
        PlayerDeathEvent evt = deathEvent();
        evt.getDrops().add(new ItemStack(Material.IRON_INGOT, 1));

        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(), "No chest in PVP keep-inventory case");
        assertTrue(evt.getDrops().isEmpty(), "Drops must be cleared in PVP keep-inventory case");
        assertTrue(evt.getKeepInventory(), "keepInventory should be set to true");
    }

    @Test
    void railsDisallowedStopsGeneration() {
        // Disallow on any rail
        when(cfg.getBoolean(ConfigKey.GENERATE_ON_RAILS)).thenReturn(false);

        // Put a rail under player
        Location loc = player.getLocation().clone();
        BlockMock block = world.getBlockAt(loc);
        block.setType(Material.RAIL);

        // Player has something to store
        player.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 1));

        PlayerDeathEvent evt = deathEvent();
        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(), "No chest when death occurs on rails and rails are disabled");
    }

    @Test
    void happyPath_generatesChestAndSaves() {
        // Ensure not on special blocks
        Location loc = player.getLocation();
        world.getBlockAt(loc).setType(Material.AIR);

        // Give inventory to store
        player.getInventory().setItem(0, new ItemStack(Material.EMERALD, 1));

        PlayerDeathEvent evt = deathEvent();

        listener.onPlayerDeathEvent(evt);

        final ArrayList<ChestData> allChestData = new ArrayList<>(DeadChestLoader.getChestDataCache().getAllChestData().values());
        assertEquals(1, allChestData.size(), "One ChestData should be created");
        ChestData cd =  allChestData.get(0);
        assertNotNull(cd.getChestLocation(), "Chest location should be set");

        // The block at the location should be set to CHEST (drop block default = 0)
        assertEquals(Material.CHEST, world.getBlockAt(cd.getChestLocation()).getType(),
                "Block at chest location must be a CHEST");

    }

    @ParameterizedTest(name = "death on {0} should NOT generate chest when disabled")
    @CsvSource({
            "LAVA, GENERATE_ON_LAVA",
            "WATER, GENERATE_ON_WATER"
    })
    void deathInFluidStopsGeneration(Material fluid, ConfigKey key) {
        when(cfg.getBoolean(key)).thenReturn(false);

        Location loc = player.getLocation();
        world.getBlockAt(loc).setType(fluid);

        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));

        PlayerDeathEvent evt = deathEvent();
        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(),
                () -> "No chest should be generated when dying in " + fluid + " and " + key + " disabled");
    }

    @ParameterizedTest(name = "death on {0} rail should NOT generate chest when GENERATE_ON_RAILS=false")
    @EnumSource(value = Material.class, names = {
            "RAIL", "POWERED_RAIL", "DETECTOR_RAIL", "ACTIVATOR_RAIL"
    })
    void deathOnRailStopsGeneration(Material railType) {
        when(cfg.getBoolean(ConfigKey.GENERATE_ON_RAILS)).thenReturn(false);

        Location loc = player.getLocation();
        world.getBlockAt(loc).setType(railType);

        player.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT));

        PlayerDeathEvent evt = deathEvent();
        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(),
                () -> "No chest should be generated when dying on " + railType + " and rails disabled");
    }

    @Test
    void minecartDisallowedStopsGeneration() {
        Player mockedPlayer = mock(Player.class);

        World mockedWorld = mock(World.class);
        when(mockedWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);

        Location deathLoc = new Location(mockedWorld, 0, 65, 0);
        when(mockedPlayer.getWorld()).thenReturn(mockedWorld);
        when(mockedPlayer.getLocation()).thenReturn(deathLoc);

        Minecart minecart = mock(Minecart.class);
        when(minecart.getType()).thenReturn(EntityType.MINECART);
        when(minecart.getWorld()).thenReturn(mockedWorld);
        when(mockedPlayer.getVehicle()).thenReturn(minecart);

        when(cfg.getBoolean(ConfigKey.GENERATE_IN_MINECART)).thenReturn(false);

        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.iterator()).thenReturn(
                Collections.singletonList(new ItemStack(Material.DIAMOND)).listIterator()
        );
        when(mockedPlayer.getInventory()).thenReturn(inv);

        PlayerDeathEvent evt = new PlayerDeathEvent(mockedPlayer, new ArrayList<>(), 0, "");

        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(), "No chest if dead in minecart and disabled");
    }

    @Test
    void deathInTheEndStopsGeneration() {
        World mockedWorld = mock(World.class);
        when(mockedWorld.getEnvironment()).thenReturn(World.Environment.THE_END);

        Player mockedPlayer = mock(Player.class);
        when(mockedPlayer.getWorld()).thenReturn(mockedWorld);

        when(cfg.getBoolean(ConfigKey.GENERATE_IN_THE_END)).thenReturn(false);

        PlayerDeathEvent evt = new PlayerDeathEvent(mockedPlayer, new ArrayList<>(), 0, "");
        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(), "No chest in the End when disabled");
    }

    @Test
    void maxDeadChestPerPlayerStopsGeneration() {
        hologramMock.when(() -> DeadChestManager.playerDeadChestAmount(any()))
                .thenReturn(10);

        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        when(cfg.getInt(ConfigKey.MAX_DEAD_CHEST_PER_PLAYER)).thenReturn(10);

        PlayerDeathEvent evt = deathEvent();
        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(), "No chest if player already at max");
    }

    @Test
    void durabilityLossRemovesBrokenItems() {
        when(cfg.getInt(ConfigKey.ITEM_DURABILITY_LOSS_ON_DEATH)).thenReturn(100);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.setDurability((short) (sword.getType().getMaxDurability() - 1)); // presque cassé
        player.getInventory().setItem(0, sword);

        PlayerDeathEvent evt = deathEvent();
        listener.onPlayerDeathEvent(evt);

        assertNull(player.getInventory().getItem(0), "Broken item should be removed after durability loss");
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "BOW", "CROSSBOW", "SHIELD", "ELYTRA", "TRIDENT", "FISHING_ROD",
            "SHEARS", "FLINT_AND_STEEL", "DIAMOND_SWORD", "DIAMOND_CHESTPLATE"
    })
    void durabilityLossAppliesToEveryKindOfEquipment(Material material) throws Exception {
        when(cfg.getInt(ConfigKey.ITEM_DURABILITY_LOSS_ON_DEATH)).thenReturn(10);
        player.getInventory().setItem(0, new ItemStack(material));
        java.lang.reflect.Method method = PlayerDeathListener.class
                .getDeclaredMethod("applyDurabilityLoss", PlayerInventory.class);
        method.setAccessible(true);
        method.invoke(listener, player.getInventory());
        org.bukkit.inventory.meta.Damageable meta = (org.bukkit.inventory.meta.Damageable)
                player.getInventory().getItem(0).getItemMeta();
        assertEquals((int) (material.getMaxDurability() * 0.1), meta.getDamage());
    }

    @Test
    void durabilityLossDoesNotWriteMetadataOnOrdinaryItems() throws Exception {
        when(cfg.getInt(ConfigKey.ITEM_DURABILITY_LOSS_ON_DEATH)).thenReturn(10);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.getItemMeta()).thenReturn(mock(org.bukkit.inventory.meta.Damageable.class));
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[]{item});
        java.lang.reflect.Method method = PlayerDeathListener.class
                .getDeclaredMethod("applyDurabilityLoss", PlayerInventory.class);
        method.setAccessible(true);
        method.invoke(listener, inventory);
        verify(item, never()).setItemMeta(any());
    }


    @Test
    void displayPositionMessageOnDeath() {
        when(cfg.getBoolean(ConfigKey.DISPLAY_POSITION_ON_DEATH)).thenReturn(true);

        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        PlayerDeathEvent evt = deathEvent();
        listener.onPlayerDeathEvent(evt);

        assertFalse(player.nextMessage().isEmpty(), "Player should receive position message");
    }

    @Test
    void ignoredItemsFromConfigUseVanillaDropBehavior() {
        ItemStack ignored = new ItemStack(Material.DIAMOND, 1);
        ItemStack stored = new ItemStack(Material.EMERALD, 1);

        when(cfg.getIgnoredEntries()).thenReturn(new ArrayList<>(Collections.singletonList("DIAMOND")));
        player.getInventory().setItem(0, ignored.clone());
        player.getInventory().setItem(1, stored.clone());

        PlayerDeathEvent evt = deathEvent();
        evt.getDrops().add(ignored.clone());
        evt.getDrops().add(stored.clone());

        listener.onPlayerDeathEvent(evt);

        final ArrayList<ChestData> allChestData = new ArrayList<>(DeadChestLoader.getChestDataCache().getAllChestData().values());
        assertEquals(1, allChestData.size(), "One ChestData should be created");

        assertEquals(1, evt.getDrops().size(), "Only ignored items should remain in vanilla drops");
        assertEquals(Material.DIAMOND, evt.getDrops().get(0).getType(), "Ignored item should remain in drops");
        assertFalse(player.getInventory().contains(Material.EMERALD), "Stored items should be removed from player inventory");
    }

    @Test
    void customIgnoredItemFromConfigUsesExactMetaMatch() {
        ItemStack ignored = new ItemStack(Material.DIAMOND, 1);
        ignored.editMeta(meta -> meta.setDisplayName("custom"));

        ItemStack sameMaterialDifferentMeta = new ItemStack(Material.DIAMOND, 1);
        ItemStack stored = new ItemStack(Material.EMERALD, 1);

        when(cfg.getIgnoredEntries()).thenReturn(new ArrayList<>(Collections.singletonList(ignored.clone())));
        player.getInventory().setItem(0, ignored.clone());
        player.getInventory().setItem(1, sameMaterialDifferentMeta);
        player.getInventory().setItem(2, stored.clone());

        PlayerDeathEvent evt = deathEvent();
        evt.getDrops().add(ignored.clone());
        evt.getDrops().add(sameMaterialDifferentMeta.clone());
        evt.getDrops().add(stored.clone());

        listener.onPlayerDeathEvent(evt);

        assertEquals(1, evt.getDrops().size(), "Only the exact custom ignored item should remain in vanilla drops");
        assertEquals("custom", evt.getDrops().get(0).getItemMeta().getDisplayName());
        assertFalse(player.getInventory().contains(Material.EMERALD), "Stored items should be removed from player inventory");
        assertFalse(player.getInventory().containsAtLeast(sameMaterialDifferentMeta, 1), "Non-ignored same-material items should be consumed by DeadChest");
    }

    @Test
    void persistSkipsWhenFileManagerNull() {
        DeadChestLoader.fileManager = null;

        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        PlayerDeathEvent evt = deathEvent();
        listener.onPlayerDeathEvent(evt);

        assertEquals(1, DeadChestLoader.getChestDataCache().getAllChestData().size(), "Chest still created even if fileManager is null");
    }

    @Test
    void noChestWhenInventoryEmpty() {
        PlayerDeathEvent evt = deathEvent();
        listener.onPlayerDeathEvent(evt);

        assertTrue(DeadChestLoader.getChestDataCache().isEmpty(), "No chest should be generated with empty inventory");
    }

    @ParameterizedTest(name = "slot {0} with Vanishing should be cleared")
    @CsvSource({
            "helmet",
            "chestplate",
            "leggings",
            "boots",
            "offhand"
    })
    void vanishingArmorAndOffhandAreCleared(String slot) {
        PlayerInventory inv = mock(PlayerInventory.class, RETURNS_DEEP_STUBS);
        ItemStack cursed = new ItemStack(Material.DIAMOND);
        cursed.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);

        when(inv.getContents()).thenReturn(new ItemStack[]{});

        switch (slot) {
            case "helmet" -> when(inv.getHelmet()).thenReturn(cursed);
            case "chestplate" -> when(inv.getChestplate()).thenReturn(cursed);
            case "leggings" -> when(inv.getLeggings()).thenReturn(cursed);
            case "boots" -> when(inv.getBoots()).thenReturn(cursed);
            case "offhand" -> when(inv.getItemInOffHand()).thenReturn(cursed);
        }

        new PlayerDeathListener().removeVanishingItems(inv);

        switch (slot) {
            case "helmet" -> verify(inv).setHelmet(null);
            case "chestplate" -> verify(inv).setChestplate(null);
            case "leggings" -> verify(inv).setLeggings(null);
            case "boots" -> verify(inv).setBoots(null);
            case "offhand" -> verify(inv).setItemInOffHand(null);
        }
    }
}
