package me.crylonz.deadchest.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.block.BlockMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.Localization;
import me.crylonz.deadchest.db.InMemoryChestStore;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.DeadChestConfig;
import me.crylonz.deadchest.utils.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class BlockBreakListenerTest {

    private PlayerMock player;
    private WorldMock world;
    private BlockMock block;
    private BlockBreakListener listener;

    private static List<ChestData> chestDataBackup;
    private InMemoryChestStore chestData;

    @BeforeAll
    static void beforeAll() {
        MockBukkit.mock();
    }

    @AfterAll
    static void afterAll() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        world = new WorldMock();
        player = new PlayerMock(MockBukkit.getMock(), "TestPlayer");
        block = world.getBlockAt(0, 64, 0);
        listener = new BlockBreakListener();

        chestData = DeadChestLoader.getChestDataCache();
        final Map<Location, ChestData> chestDataList = chestData.getAllChestData();
        if (chestDataList == null) {
            chestData.setChestData(new ArrayList<>());
        }
    }

    @AfterEach
    void tearDown() {
        DeadChestLoader.getChestDataCache().clearChestData();
    }

    @Test
    void testEventCancelledWhenIndestructibleChestAndMatchingLocation() {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.isGraveBlock(Material.CHEST)).thenReturn(true);

            DeadChestLoader.config = mock(DeadChestConfig.class);
            when(DeadChestLoader.config.getBoolean(ConfigKey.INDESTRUCTIBLE_CHEST)).thenReturn(true);
            when(DeadChestLoader.config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)).thenReturn(true);

            Localization localization = new Localization();
            Map<String, Object> values = new HashMap<>();
            values.put("common.prefix", "[DeadChest] ");
            values.put("chest.not-owner", "This is not your Deadchest!");
            localization.set(values);
            DeadChestLoader.local = localization;

            ChestData cd = mock(ChestData.class);
            when(cd.getChestLocation()).thenReturn(block.getLocation());
            when(cd.getPlayerUUID()).thenReturn(java.util.UUID.randomUUID());
            chestData.clearChestData();
            chestData.addChestData(cd);

            block.setType(Material.CHEST);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            listener.onBlockBreakEvent(event);

            assertTrue(event.isCancelled(), "Event should be cancelled for indestructible chest");
            assertTrue(player.nextMessage().contains("not your Deadchest"));
        }
    }

    @Test
    void ownerBreakingDuringPickupIsProtectedWithoutFalseDenial() {
        DeadChestLoader.config = mock(DeadChestConfig.class);
        when(DeadChestLoader.config.getBoolean(ConfigKey.INDESTRUCTIBLE_CHEST)).thenReturn(true);
        when(DeadChestLoader.config.getBoolean(ConfigKey.ONLY_OWNER_CAN_OPEN_CHEST)).thenReturn(true);
        ChestData cd = mock(ChestData.class);
        when(cd.getChestLocation()).thenReturn(block.getLocation());
        when(cd.getPlayerUUID()).thenReturn(player.getUniqueId());
        when(cd.isTransitioning()).thenReturn(true);
        chestData.addChestData(cd);
        block.setType(Material.CHEST);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        listener.onBlockBreakEvent(event);
        assertTrue(event.isCancelled());
        assertNull(player.nextMessage());
    }

    @Test
    void testEventNotCancelledWhenNotGraveBlock() {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.isGraveBlock(Material.DIRT)).thenReturn(false);

            block.setType(Material.DIRT);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            listener.onBlockBreakEvent(event);

            assertFalse(event.isCancelled(), "Event should not be cancelled if not a grave block");
        }
    }
}
