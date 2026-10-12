package me.crylonz.deadchest.db;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteRepositoryTest {

    private ServerMock server;
    private WorldMock world;
    private Plugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();

        DeadChestLoader.plugin = plugin;
        DeadChestLoader.log = Logger.getLogger("DeadChestSQLiteTest");
        DeadChestLoader.sqlExecutor = new SQLExecutor();
        DeadChestLoader.db = new SQLite(plugin);

        Path dbPath = plugin.getDataFolder().toPath().resolve("data.db");
        Files.createDirectories(plugin.getDataFolder().toPath());
        Files.deleteIfExists(dbPath);

        DeadChestLoader.db.init();
        ChestDataRepository.initTable(() -> {
        });
        IgnoreItemListRepository.initTable();
        awaitAsyncDb();
    }

    @AfterEach
    void tearDown() {
        DeadChestLoader.sqlExecutor.shutdown();
        DeadChestLoader.db.close();
        DeadChestLoader.sqlExecutor = new SQLExecutor();
        MockBukkit.unmock();
    }

    @Test
    void saveAndFindAllRoundTrip() {
        ChestData expected = chestDataAt(10, Material.DIAMOND, 2, false, 12);

        boolean duplicate = ChestDataRepository.save(expected);
        assertFalse(duplicate);

        List<ChestData> loaded = ChestDataRepository.findAll();
        assertEquals(1, loaded.size());
        assertChestEquivalent(expected, loaded.get(0));
        assertEquals(expected.getId(), loaded.get(0).getId());
    }

    @Test
    void migrationCompletesCallbackAndKeepsPersistentIdentity() {
        ChestData chest = chestDataAt(19, Material.DIAMOND, 1, false, 1);
        ChestDataRepository.save(chest);
        java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean();
        ChestDataRepository.initTable(() -> called.set(true));
        awaitAsyncDb();
        assertTrue(called.get(), "Migration callback must not be skipped by a closed connection");
        assertEquals(chest.getId(), ChestDataRepository.findAll().get(0).getId());
    }

    @Test
    void saveReturnsDuplicateWhenSameCoordinates() {
        ChestData chest = chestDataAt(11, Material.IRON_INGOT, 3, false, 4);

        assertFalse(ChestDataRepository.save(chest));
        assertTrue(ChestDataRepository.save(chest));
        assertEquals(1, ChestDataRepository.findAll().size());
    }

    @Test
    void updateUpdatesExistingRow() {
        ChestData original = chestDataAt(12, Material.COAL, 5, false, 1);
        assertFalse(ChestDataRepository.save(original));

        ChestData updated = chestDataAt(12, Material.GOLD_INGOT, 8, true, 99);
        boolean inserted = ChestDataRepository.update(updated);
        assertFalse(inserted);

        List<ChestData> loaded = ChestDataRepository.findAll();
        assertEquals(1, loaded.size());
        assertChestEquivalent(updated, loaded.get(0));
    }

    @Test
    void updateInsertsWhenRowMissing() {
        ChestData chest = chestDataAt(13, Material.REDSTONE, 4, false, 7);

        boolean inserted = ChestDataRepository.update(chest);
        assertTrue(inserted);
        assertEquals(1, ChestDataRepository.findAll().size());
    }

    @Test
    void removeAndClearWork() {
        ChestData chest1 = chestDataAt(20, Material.APPLE, 1, false, 0);
        ChestData chest2 = chestDataAt(21, Material.BREAD, 2, false, 0);

        ChestDataRepository.save(chest1);
        ChestDataRepository.save(chest2);
        assertEquals(2, ChestDataRepository.findAll().size());

        ChestDataRepository.remove(chest1);
        assertEquals(1, ChestDataRepository.findAll().size());

        ChestDataRepository.clear();
        assertTrue(ChestDataRepository.findAll().isEmpty());
    }

    @Test
    void ignoreListSaveAndLoadRoundTrip() {
        Inventory source = Bukkit.createInventory(null, 9);
        source.setItem(1, new ItemStack(Material.DIAMOND, 2));
        source.setItem(6, new ItemStack(Material.GOLDEN_APPLE, 1));

        IgnoreItemListRepository.saveIgnoreIntoInventory(source);
        awaitAsyncDb();

        Inventory destination = Bukkit.createInventory(null, 9);
        IgnoreItemListRepository.loadIgnoreIntoInventory(destination);
        awaitAsyncDb();

        assertNotNull(destination.getItem(1));
        assertEquals(Material.DIAMOND, destination.getItem(1).getType());
        assertEquals(2, destination.getItem(1).getAmount());

        assertNotNull(destination.getItem(6));
        assertEquals(Material.GOLDEN_APPLE, destination.getItem(6).getType());
        assertEquals(1, destination.getItem(6).getAmount());
    }

    @Test
    void dataPersistsAcrossReinitialization() {
        ChestData chest = chestDataAt(30, Material.EMERALD, 3, false, 17);
        ChestDataRepository.save(chest);

        DeadChestLoader.db.close();
        DeadChestLoader.db = new SQLite(plugin);
        DeadChestLoader.db.init();
        ChestDataRepository.initTable(() -> {
        });
        awaitAsyncDb();

        List<ChestData> loaded = ChestDataRepository.findAll();
        assertEquals(1, loaded.size());
        assertChestEquivalent(chest, loaded.get(0));
    }

    @Test
    void findAllAsyncReturnsRowsForStartupRecovery() {
        ChestData chest = chestDataAt(40, Material.LAPIS_LAZULI, 9, false, 23);
        ChestDataRepository.save(chest);

        AtomicReference<List<ChestData>> loaded = new AtomicReference<>();
        ChestDataRepository.findAllAsync(loaded::set, plugin);

        awaitAsyncDb();
        server.getScheduler().performTicks(1L);

        List<ChestData> result = loaded.get();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertChestEquivalent(chest, result.get(0));
    }

    @Test
    void batchSaveMixedInsertAndUpdateWorksInSinglePass() {
        ChestData existing = chestDataAt(50, Material.IRON_INGOT, 1, false, 1);
        ChestDataRepository.save(existing);

        ChestData updatedExisting = chestDataAt(50, Material.DIAMOND, 7, true, 77);
        ChestData brandNew = chestDataAt(51, Material.GOLD_INGOT, 4, false, 9);

        List<ChestData> batch = new ArrayList<>();
        batch.add(updatedExisting);
        batch.add(brandNew);

        ChestDataRepository.batchSave(batch);

        List<ChestData> loaded = ChestDataRepository.findAll();
        assertEquals(2, loaded.size());
        assertChestEquivalent(updatedExisting, findByChestX(loaded, 50));
        assertChestEquivalent(brandNew, findByChestX(loaded, 51));
    }

    @Test
    void saveAllReplacesDataset() {
        ChestData old = chestDataAt(60, Material.COAL, 1, false, 0);
        ChestDataRepository.save(old);
        assertEquals(1, ChestDataRepository.findAll().size());

        ChestData newOne = chestDataAt(61, Material.EMERALD, 3, false, 13);
        ChestData newTwo = chestDataAt(62, Material.REDSTONE, 2, true, 5);

        List<ChestData> replacement = new ArrayList<>();
        replacement.add(newOne);
        replacement.add(newTwo);

        ChestDataRepository.saveAll(replacement);

        List<ChestData> loaded = ChestDataRepository.findAll();
        assertEquals(2, loaded.size());
        assertChestEquivalent(newOne, findByChestX(loaded, 61));
        assertChestEquivalent(newTwo, findByChestX(loaded, 62));
    }

    @Test
    void saveAllAsyncPersistsRows() {
        List<ChestData> chests = new ArrayList<>();
        chests.add(chestDataAt(70, Material.APPLE, 1, false, 0));
        chests.add(chestDataAt(71, Material.BREAD, 1, false, 0));

        ChestDataRepository.saveAllAsync(chests);
        awaitAsyncDb();

        List<ChestData> loaded = ChestDataRepository.findAll();
        assertEquals(2, loaded.size());
        assertNotNull(findByChestX(loaded, 70));
        assertNotNull(findByChestX(loaded, 71));
    }

    @Test
    void removeBatchRemovesOnlyRequestedChests() {
        ChestData chestA = chestDataAt(80, Material.STONE, 1, false, 0);
        ChestData chestB = chestDataAt(81, Material.DIRT, 1, false, 0);
        ChestData chestC = chestDataAt(82, Material.SAND, 1, false, 0);

        ChestDataRepository.save(chestA);
        ChestDataRepository.save(chestB);
        ChestDataRepository.save(chestC);

        List<ChestData> remove = new ArrayList<>();
        remove.add(chestA);
        remove.add(chestC);

        ChestDataRepository.remove(remove);

        List<ChestData> loaded = ChestDataRepository.findAll();
        assertEquals(1, loaded.size());
        assertChestEquivalent(chestB, loaded.get(0));
    }

    @Test
    void removeBatchWithEmptyCollectionDoesNothing() {
        ChestData chest = chestDataAt(83, Material.COBBLESTONE, 2, false, 0);
        ChestDataRepository.save(chest);

        ChestDataRepository.remove(new ArrayList<>());

        List<ChestData> loaded = ChestDataRepository.findAll();
        assertEquals(1, loaded.size());
        assertChestEquivalent(chest, loaded.get(0));
    }

    @Test
    void saveAsyncReturnsDuplicateState() throws Exception {
        ChestData chest = chestDataAt(90, Material.DIAMOND, 2, false, 0);
        ChestDataRepository.save(chest);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> duplicate = new AtomicReference<>();
        ChestDataRepository.saveAsync(chest, value -> {
            duplicate.set(value);
            latch.countDown();
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(Boolean.TRUE.equals(duplicate.get()));
    }

    @Test
    void updateAsyncReturnsInsertedThenUpdatedState() throws Exception {
        ChestData first = chestDataAt(91, Material.IRON_INGOT, 1, false, 0);

        CountDownLatch firstLatch = new CountDownLatch(1);
        AtomicReference<Boolean> insertedState = new AtomicReference<>();
        ChestDataRepository.updateAsync(first, value -> {
            insertedState.set(value);
            firstLatch.countDown();
        });
        assertTrue(firstLatch.await(3, TimeUnit.SECONDS));
        assertTrue(Boolean.TRUE.equals(insertedState.get()));

        ChestData second = chestDataAt(91, Material.GOLD_INGOT, 8, true, 11);
        CountDownLatch secondLatch = new CountDownLatch(1);
        AtomicReference<Boolean> updateState = new AtomicReference<>();
        ChestDataRepository.updateAsync(second, value -> {
            updateState.set(value);
            secondLatch.countDown();
        });
        assertTrue(secondLatch.await(3, TimeUnit.SECONDS));
        assertFalse(Boolean.TRUE.equals(updateState.get()));

        ChestData loaded = findByChestX(ChestDataRepository.findAll(), 91);
        assertChestEquivalent(second, loaded);
    }

    @Test
    void removeAsyncAndClearAsyncWork() {
        ChestData chestA = chestDataAt(92, Material.STICK, 1, false, 0);
        ChestData chestB = chestDataAt(93, Material.TORCH, 2, false, 0);
        ChestDataRepository.save(chestA);
        ChestDataRepository.save(chestB);

        ChestDataRepository.removeAsync(chestA);
        awaitAsyncDb();

        List<ChestData> afterRemove = ChestDataRepository.findAll();
        assertEquals(1, afterRemove.size());
        assertEquals(93, afterRemove.get(0).getChestLocation().getBlockX());

        ChestDataRepository.clearAsync();
        awaitAsyncDb();
        assertTrue(ChestDataRepository.findAll().isEmpty());
    }

    @Test
    void ignoreListLoadSkipsOutOfBoundSlots() throws Exception {
        try (Connection conn = DeadChestLoader.db.connection();
             PreparedStatement insert = conn.prepareStatement("INSERT INTO ignore_items(slot, data) VALUES(?, ?)")) {
            insert.setInt(1, 1);
            insert.setBytes(2, me.crylonz.deadchest.utils.ItemBytes.toBytes(new ItemStack(Material.DIAMOND, 1)));
            insert.addBatch();
            insert.setInt(1, 99);
            insert.setBytes(2, me.crylonz.deadchest.utils.ItemBytes.toBytes(new ItemStack(Material.GOLD_INGOT, 1)));
            insert.addBatch();
            insert.executeBatch();
        }

        Inventory destination = Bukkit.createInventory(null, 9);
        IgnoreItemListRepository.loadIgnoreIntoInventory(destination);
        awaitAsyncDb();

        assertNotNull(destination.getItem(1));
        assertEquals(Material.DIAMOND, destination.getItem(1).getType());

        Set<Material> found = new HashSet<>();
        for (ItemStack item : destination.getContents()) {
            if (item != null) found.add(item.getType());
        }
        assertFalse(found.contains(Material.GOLD_INGOT));
    }

    @Test
    void sqliteConnectionReopensAfterClose() throws Exception {
        Connection first = DeadChestLoader.db.connection();
        assertFalse(first.isClosed());

        DeadChestLoader.db.close();

        Connection reopened = DeadChestLoader.db.connection();
        assertNotNull(reopened);
        assertFalse(reopened.isClosed());
    }

    @Test
    void initTableRebuildsLocationIndexWhenBroken() throws Exception {
        try (Statement st = DeadChestLoader.db.connection().createStatement()) {
            st.executeUpdate("DROP INDEX IF EXISTS idx_chest_location");
            st.executeUpdate("CREATE INDEX idx_chest_location ON chest_data(chest_world)");
        }

        ChestDataRepository.initTable(() -> {
        });
        awaitAsyncDb();

        List<String> columns = new ArrayList<>();
        try (Statement st = DeadChestLoader.db.connection().createStatement();
             ResultSet rs = st.executeQuery("PRAGMA index_info('idx_chest_location')")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }

        assertEquals(4, columns.size());
        assertEquals("chest_world", columns.get(0));
        assertEquals("chest_x", columns.get(1));
        assertEquals("chest_y", columns.get(2));
        assertEquals("chest_z", columns.get(3));
    }

    private void awaitAsyncDb() {
        CountDownLatch latch = new CountDownLatch(1);
        DeadChestLoader.sqlExecutor.runAsync(latch::countDown);
        try {
            assertTrue(latch.await(3, TimeUnit.SECONDS), "SQL async queue did not flush in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting SQL queue", e);
        }
        server.getScheduler().performTicks(1L);
    }

    private ChestData findByChestX(List<ChestData> chests, int x) {
        for (ChestData chest : chests) {
            if (chest.getChestLocation().getBlockX() == x) {
                return chest;
            }
        }
        fail("No chest found at x=" + x);
        return null;
    }

    private ChestData chestDataAt(int x, Material material, int amount, boolean infinity, int xp) {
        UUID playerId = UUID.nameUUIDFromBytes(("player-" + x).getBytes());
        UUID timerId = UUID.nameUUIDFromBytes(("timer-" + x).getBytes());
        UUID ownerId = UUID.nameUUIDFromBytes(("owner-" + x).getBytes());

        Location chestLocation = new Location(world, x, 64, x, 90f, 10f);
        Location holoLocation = new Location(world, x, 65, x, 0f, 0f);

        List<ItemStack> inventory = new ArrayList<>();
        inventory.add(new ItemStack(material, amount));
        inventory.add(new ItemStack(Material.STONE, 1));

        return new ChestData(
                inventory,
                chestLocation,
                "Steve",
                playerId,
                new Date(1_700_000_000_000L + x),
                infinity,
                false,
                holoLocation,
                timerId,
                null,
                ownerId,
                world.getName(),
                xp
        );
    }

    private void assertChestEquivalent(ChestData expected, ChestData actual) {
        assertEquals(expected.getPlayerUUID(), actual.getPlayerUUID());
        assertEquals(expected.getPlayerName(), actual.getPlayerName());
        assertEquals(expected.getChestDate(), actual.getChestDate());
        assertEquals(expected.isInfinity(), actual.isInfinity());
        assertEquals(expected.isRemovedBlock(), actual.isRemovedBlock());
        assertEquals(expected.getHolographicTimerId(), actual.getHolographicTimerId());
        assertEquals(expected.getHolographicStatusId(), actual.getHolographicStatusId());
        assertEquals(expected.getHolographicOwnerId(), actual.getHolographicOwnerId());
        assertEquals(expected.getWorldName(), actual.getWorldName());
        assertEquals(expected.getXpStored(), actual.getXpStored());

        assertNotNull(actual.getChestLocation().getWorld());
        assertEquals(expected.getChestLocation().getWorld().getName(), actual.getChestLocation().getWorld().getName());
        assertEquals(expected.getChestLocation().getBlockX(), actual.getChestLocation().getBlockX());
        assertEquals(expected.getChestLocation().getBlockY(), actual.getChestLocation().getBlockY());
        assertEquals(expected.getChestLocation().getBlockZ(), actual.getChestLocation().getBlockZ());

        assertNotNull(actual.getHolographicTimer().getWorld());
        assertEquals(expected.getHolographicTimer().getWorld().getName(), actual.getHolographicTimer().getWorld().getName());
        assertEquals(expected.getHolographicTimer().getBlockX(), actual.getHolographicTimer().getBlockX());
        assertEquals(expected.getHolographicTimer().getBlockY(), actual.getHolographicTimer().getBlockY());
        assertEquals(expected.getHolographicTimer().getBlockZ(), actual.getHolographicTimer().getBlockZ());

        assertEquals(expected.getInventory().size(), actual.getInventory().size());
        assertEquals(expected.getInventory().get(0).getType(), actual.getInventory().get(0).getType());
        assertEquals(expected.getInventory().get(0).getAmount(), actual.getInventory().get(0).getAmount());
    }
}
