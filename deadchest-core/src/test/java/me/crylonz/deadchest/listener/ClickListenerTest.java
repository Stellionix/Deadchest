package me.crylonz.deadchest.listener;

import me.crylonz.deadchest.ChestLifecycle;
import me.crylonz.deadchest.PlayerTransfers;
import me.crylonz.deadchest.RecoveryJournal;
import me.crylonz.deadchest.TransferStore;
import me.crylonz.deadchest.db.ChestDataRepository;
import me.crylonz.deadchest.db.SQLExecutor;
import me.crylonz.deadchest.db.SQLite;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.block.BlockMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.FileManager;
import me.crylonz.deadchest.Localization;
import me.crylonz.deadchest.db.InMemoryChestStore;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.DeadChestConfig;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeadChest main interaction listener.
 * Covers inventory restore, drop mode, permission handling,
 * event cancellation and cleanup.
 */
class ClickListenerTest {

    private WorldMock world;
    private PlayerMock player;
    private BlockMock chestBlock;
    private ClickListener listener;
    private InMemoryChestStore deadChest;
    private org.mockito.MockedStatic<RecoveryJournal> journalMock;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        DeadChestLoader.plugin = MockBukkit.createMockPlugin();
        ChestLifecycle.observe(null);
        DeadChestLoader.sqlExecutor = mock(SQLExecutor.class);
        doAnswer(a -> { ((Runnable)a.getArgument(0)).run(); return null; }).when(DeadChestLoader.sqlExecutor).runAsync(any(Runnable.class));
        DeadChestLoader.db = new SQLite(DeadChestLoader.plugin);
        DeadChestLoader.db.init();
        ChestDataRepository.initTable(() -> {});
        clearInvocations(DeadChestLoader.sqlExecutor);
        PlayerTransfers.initialize();
        journalMock = mockStatic(RecoveryJournal.class);
        journalMock.when(RecoveryJournal::active)
                .thenReturn(mock(RecoveryJournal.class));
        world = new WorldMock() { @Override public boolean isChunkForceLoaded(int x,int z) { return false; } };
        MockBukkit.getMock().addWorld(world);
        player = spy(new PlayerMock(MockBukkit.getMock(), "Steve"));
        doNothing().when(player).saveData();
        java.util.Set<String> receipts = new java.util.HashSet<>();
        doReturn(receipts).when(player).getScoreboardTags();
        doAnswer(a -> receipts.add(a.getArgument(0))).when(player).addScoreboardTag(anyString());
        doAnswer(a -> receipts.remove(a.getArgument(0))).when(player).removeScoreboardTag(anyString());
        doReturn(true).when(player).isOnline();
        chestBlock = world.getBlockAt(0, 64, 0);
        chestBlock.setType(Material.CHEST);
        DeadChestLoader.graveBlocks.add(Material.CHEST);

        deadChest = DeadChestLoader.getChestDataCache();
        deadChest.setChestData(new ArrayList<>());

        DeadChestLoader.local = mock(Localization.class);
        DeadChestLoader.fileManager = mock(FileManager.class);
        when(DeadChestLoader.local.prefixed("chest.not-owner")).thenReturn("[DC] You are not the owner");
        when(DeadChestLoader.local.prefixed("chest.no-permission-open")).thenReturn("[DC] You cannot take this chest");

        DeadChestLoader.config = mock(DeadChestConfig.class);
        listener = new ClickListener();
    }

    @AfterEach
    void tearDown() {
        journalMock.close();
        DeadChestLoader.db.close();
        MockBukkit.unmock();
    }

    private ChestData chest() {
        ChestData c=mock(ChestData.class);
        when(c.getPlayerUUID()).thenReturn(UUID.randomUUID());
        when(c.getPlayerStringUUID()).thenAnswer(a -> c.getPlayerUUID().toString());
        when(c.getPlayerName()).thenReturn("Steve");
        when(c.getWorldName()).thenReturn(world.getName());
        when(c.getChestDate()).thenReturn(new Date());
        when(c.getHolographicTimer()).thenReturn(chestBlock.getLocation());
        when(c.getHolographicTimerId()).thenReturn(UUID.randomUUID());
        when(c.getHolographicOwnerId()).thenReturn(UUID.randomUUID());
        return c;
    }

    @Test
    void testIsNearGraveChest_CancelsEvent() {
        ChestData cd = chest();
        when(cd.claimTransition()).thenReturn(true);
        when(cd.getId()).thenReturn(UUID.randomUUID());
        when(cd.getChestLocation()).thenReturn(chestBlock.getLocation());
        deadChest.addChestData(cd);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                new ItemStack(Material.STONE), chestBlock, BlockFace.UP);

        listener.onClick(event);
        MockBukkit.getMock().getScheduler().performTicks(6);
        assertTrue(event.isCancelled(), "Right click near DeadChest should cancel the event");
    }

    @Test
    void testClickOnDeadChest_NotOwner_Denied() {
        // Config requires ownership
        when(DeadChestLoader.config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)).thenReturn(true);

        ChestData cd = chest();
        when(cd.claimTransition()).thenReturn(true);
        when(cd.getId()).thenReturn(UUID.randomUUID());
        when(cd.getChestLocation()).thenReturn(chestBlock.getLocation());
        when(cd.getPlayerUUID()).thenReturn(UUID.randomUUID());
        deadChest.addChestData(cd);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                new ItemStack(Material.CHEST), chestBlock, BlockFace.UP);

        listener.onClick(event);
        MockBukkit.getMock().getScheduler().performTicks(6);

        assertTrue(event.isCancelled(), "Event should be cancelled for non-owner");
        assertEquals("[DC] You are not the owner", player.nextMessage());
    }

    @Test
    void testClickOnDeadChest_Owner_RestoreInventory() {
        // Config: owner can open, drop mode = 1 (restore)
        when(DeadChestLoader.config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.REQUIRE_PERMISSION_TO_GET_CHEST)).thenReturn(false);
        when(DeadChestLoader.config.getInt(ConfigKey.DROP_MODE)).thenReturn(1);

        ChestData cd = chest();
        when(cd.claimTransition()).thenReturn(true);
        when(cd.getId()).thenReturn(UUID.randomUUID());
        when(cd.getChestLocation()).thenReturn(chestBlock.getLocation());
        doReturn(player.getUniqueId()).when(cd).getPlayerUUID();
        when(cd.getInventory()).thenReturn(Arrays.asList(new ItemStack(Material.DIAMOND), new ItemStack(Material.APPLE)));
        when(cd.getXpStored()).thenReturn(5);
        deadChest.addChestData(cd);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                new ItemStack(Material.CHEST), chestBlock, BlockFace.UP);

        listener.onClick(event);
        MockBukkit.getMock().getScheduler().performTicks(6);

        PlayerInventory inv = player.getInventory();
        assertTrue(inv.contains(Material.DIAMOND), "Diamond should be restored to player inventory");
        assertTrue(inv.contains(Material.APPLE), "Apple should be restored to player inventory");
        verify(player).saveData();
        org.mockito.InOrder order = inOrder(player, DeadChestLoader.sqlExecutor);
        order.verify(player).saveData();
        order.verify(DeadChestLoader.sqlExecutor).runAsync(any(Runnable.class));
        assertEquals(Material.AIR, chestBlock.getType(), "Chest block should be removed after pickup");
        assertTrue(new TransferStore(DeadChestLoader.plugin.getDataFolder().toPath().resolve("data.db")).pending().isEmpty());
    }

    @Test
    void failedPlayerSaveDoesNotAcknowledgeRecovery() {
        when(DeadChestLoader.config.getInt(ConfigKey.DROP_MODE)).thenReturn(1);
        ChestData cd = chest();
        when(cd.claimTransition()).thenReturn(true);
        when(cd.getId()).thenReturn(UUID.randomUUID());
        when(cd.getChestLocation()).thenReturn(chestBlock.getLocation());
        when(cd.getInventory()).thenReturn(List.of(new ItemStack(Material.DIAMOND)));
        deadChest.addChestData(cd);
        doThrow(new IllegalStateException("disk unavailable")).when(player).saveData();
        listener.onClick(new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                new ItemStack(Material.STONE), chestBlock, BlockFace.UP));
        MockBukkit.getMock().getScheduler().performTicks(6);
        verify(player).saveData();
        verify(DeadChestLoader.sqlExecutor, never()).runAsync(any(Runnable.class));
    }

    @Test
    void testClickOnDeadChest_DropModeDropsItems() {
        // Config: drop mode = 2
        when(DeadChestLoader.config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)).thenReturn(false);
        when(DeadChestLoader.config.getInt(ConfigKey.DROP_MODE)).thenReturn(2);

        ChestData cd = chest();
        when(cd.claimTransition()).thenReturn(true);
        when(cd.getId()).thenReturn(UUID.randomUUID());
        when(cd.getChestLocation()).thenReturn(chestBlock.getLocation());
        when(cd.getInventory()).thenReturn(List.of(new ItemStack(Material.EMERALD)));
        when(cd.getXpStored()).thenReturn(10);
        deadChest.addChestData(cd);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                new ItemStack(Material.CHEST), chestBlock, BlockFace.UP);

        listener.onClick(event);
        MockBukkit.getMock().getScheduler().performTicks(6);

        assertEquals(Material.AIR, chestBlock.getType(), "Chest should be removed in drop mode");
        // World drops are not directly testable in MockBukkit (but you can spy WorldMock if needed)
    }

    @Test
    void testHasGetPermission_Denied() {
        when(DeadChestLoader.config.getBoolean(ConfigKey.REQUIRE_PERMISSION_TO_GET_CHEST)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)).thenReturn(false);

        ChestData cd = chest();
        when(cd.claimTransition()).thenReturn(true);
        when(cd.getId()).thenReturn(UUID.randomUUID());
        when(cd.getChestLocation()).thenReturn(chestBlock.getLocation());
        doReturn(player.getUniqueId()).when(cd).getPlayerUUID();
        deadChest.addChestData(cd);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                new ItemStack(Material.CHEST), chestBlock, BlockFace.UP);

        listener.onClick(event);
        MockBukkit.getMock().getScheduler().performTicks(6);

        assertTrue(event.isCancelled(), "Event should be cancelled when player lacks GET permission");
        assertEquals("[DC] You cannot take this chest", player.nextMessage());
    }

    @Test
    void testClickOnDeadChest_DuringPublicPhase_OtherPlayerCanLoot() {
        when(DeadChestLoader.config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.REQUIRE_PERMISSION_TO_GET_CHEST)).thenReturn(false);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_ENABLED)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OTHER_PLAYERS)).thenReturn(true);
        when(DeadChestLoader.config.getInt(ConfigKey.DEADCHEST_DURATION)).thenReturn(1);
        when(DeadChestLoader.config.getInt(ConfigKey.LOOT_PUBLIC_DURATION)).thenReturn(30);
        when(DeadChestLoader.config.getInt(ConfigKey.DROP_MODE)).thenReturn(1);

        ChestData cd = chest();
        when(cd.claimTransition()).thenReturn(true);
        when(cd.getId()).thenReturn(UUID.randomUUID());
        when(cd.getChestLocation()).thenReturn(chestBlock.getLocation());
        when(cd.getPlayerUUID()).thenReturn(UUID.randomUUID());
        when(cd.getChestDate()).thenReturn(new Date(System.currentTimeMillis() - 10_000L));
        when(cd.isInfinity()).thenReturn(false);
        when(cd.getInventory()).thenReturn(List.of(new ItemStack(Material.DIAMOND)));
        when(cd.getXpStored()).thenReturn(0);
        deadChest.addChestData(cd);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                new ItemStack(Material.CHEST), chestBlock, BlockFace.UP);

        listener.onClick(event);
        MockBukkit.getMock().getScheduler().performTicks(6);

        assertTrue(player.getInventory().contains(Material.DIAMOND), "Public phase chest should be lootable by other players");
        assertEquals(Material.AIR, chestBlock.getType(), "Looted public chest should be removed");
    }

    @Test
    void testClickOnDeadChest_DuringPublicPhase_KillerCanLootWhenEnabled() {
        PlayerMock killer = spy(new PlayerMock(MockBukkit.getMock(), "Alex"));
        doReturn(true).when(killer).isOnline();
        doNothing().when(killer).saveData();
        java.util.Set<String> receipts = new java.util.HashSet<>();
        doReturn(receipts).when(killer).getScoreboardTags();
        doAnswer(a -> receipts.add(a.getArgument(0))).when(killer).addScoreboardTag(anyString());
        doAnswer(a -> receipts.remove(a.getArgument(0))).when(killer).removeScoreboardTag(anyString());
        when(DeadChestLoader.config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.REQUIRE_PERMISSION_TO_GET_CHEST)).thenReturn(false);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_ENABLED)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OWNER)).thenReturn(false);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_KILLER)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OTHER_PLAYERS)).thenReturn(false);
        when(DeadChestLoader.config.getInt(ConfigKey.DEADCHEST_DURATION)).thenReturn(1);
        when(DeadChestLoader.config.getInt(ConfigKey.LOOT_PUBLIC_DURATION)).thenReturn(30);
        when(DeadChestLoader.config.getInt(ConfigKey.DROP_MODE)).thenReturn(1);

        ChestData cd = chest();
        when(cd.claimTransition()).thenReturn(true);
        when(cd.getId()).thenReturn(UUID.randomUUID());
        when(cd.getChestLocation()).thenReturn(chestBlock.getLocation());
        when(cd.getPlayerUUID()).thenReturn(UUID.randomUUID());
        doReturn(killer.getUniqueId()).when(cd).getKillerUUID();
        when(cd.getChestDate()).thenReturn(new Date(System.currentTimeMillis() - 10_000L));
        when(cd.isInfinity()).thenReturn(false);
        when(cd.getInventory()).thenReturn(List.of(new ItemStack(Material.EMERALD)));
        when(cd.getXpStored()).thenReturn(0);
        deadChest.addChestData(cd);

        PlayerInteractEvent event = new PlayerInteractEvent(killer, Action.LEFT_CLICK_BLOCK,
                new ItemStack(Material.CHEST), chestBlock, BlockFace.UP);

        listener.onClick(event);
        MockBukkit.getMock().getScheduler().performTicks(6);

        assertTrue(killer.getInventory().contains(Material.EMERALD), "Configured killer should be able to loot during public phase");
    }

    @Test
    void testClickOnDeadChest_DuringPublicPhase_OtherPlayerDeniedWhenDisabled() {
        when(DeadChestLoader.config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_ENABLED)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OTHER_PLAYERS)).thenReturn(false);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_KILLER)).thenReturn(false);
        when(DeadChestLoader.config.getBoolean(ConfigKey.LOOT_PUBLIC_ACCESS_OWNER)).thenReturn(true);
        when(DeadChestLoader.config.getInt(ConfigKey.DEADCHEST_DURATION)).thenReturn(1);
        when(DeadChestLoader.config.getInt(ConfigKey.LOOT_PUBLIC_DURATION)).thenReturn(30);

        ChestData cd = chest();
        when(cd.claimTransition()).thenReturn(true);
        when(cd.getId()).thenReturn(UUID.randomUUID());
        when(cd.getChestLocation()).thenReturn(chestBlock.getLocation());
        when(cd.getPlayerUUID()).thenReturn(UUID.randomUUID());
        when(cd.getKillerUUID()).thenReturn(UUID.randomUUID());
        when(cd.getChestDate()).thenReturn(new Date(System.currentTimeMillis() - 10_000L));
        when(cd.isInfinity()).thenReturn(false);
        deadChest.addChestData(cd);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                new ItemStack(Material.CHEST), chestBlock, BlockFace.UP);

        listener.onClick(event);
        MockBukkit.getMock().getScheduler().performTicks(6);

        assertTrue(event.isCancelled(), "Other player should be denied when public access is disabled");
        assertEquals("[DC] You are not the owner", player.nextMessage());
    }
}
