package me.crylonz.deadchest.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.crylonz.deadchest.*;
import me.crylonz.deadchest.db.ChestDataRepository;
import me.crylonz.deadchest.db.SQLExecutor;
import me.crylonz.deadchest.db.SQLite;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.DeadChestConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DCCommandRegistrationServiceTest {

    private ServerMock server;
    private DCCommandRegistrationService service;

    @BeforeEach
    public void setUp() throws Exception {
        server = MockBukkit.mock();
        Localization localization = new Localization();
        Map<String, Object> values = new HashMap<>();
        values.put("common.prefix", "[DeadChest] ");
        values.put("commands.error.player-only", "Command must be called by a player");
        values.put("commands.config.deprecated.ignore", "Ignore command is deprecated");
        values.put("commands.config.usage", "Config usage");
        values.put("commands.config.get.success", "{0} = {1}");
        values.put("commands.config.set.success", "Set {0} = {1}");
        values.put("commands.config.reset.success", "Reset {0} = {1}");
        values.put("commands.config.edit.unsupported", "{0} cannot be edited interactively");
        values.put("commands.config.error.unknown-key", "Unknown config key: {0}");
        values.put("commands.config.error.invalid-value", "Invalid value for {0}: {1} ({2})");
        values.put("commands.giveback.none", "No active deadchest found for {0}");
        values.put("commands.giveback.invalid-id", "Unknown deadchest id: {0}");
        values.put("commands.giveback.invalid-strategy", "Unknown giveback strategy: {0}");
        values.put("commands.giveback.list.title", "Deadchests for {0} ({1}):");
        values.put("commands.giveback.preview.list.title", "Giveback preview list for {0} ({1}):");
        values.put("commands.giveback.list.entry", "ID {0} @ {2} {3} {4} {5} items={6} xp={7}");
        values.put("commands.giveback.preview.summary", "Preview {0} {1} {2} {3} {4} {5} {6} {7}");
        values.put("commands.giveback.preview.entry", "PreviewEntry {0} @ {2} {3} {4} {5} items={6} xp={7}");
        values.put("commands.giveback.target-not-found", "This player is offline or doesn't have any active deadchest");
        values.put("commands.giveback.success.sender", "Returned {0} deadchest(s) to {1} using {2}");
        values.put("commands.giveback.success.target", "You have retrieved {0} deadchest(s) using {1}");
        values.put("commands.giveback.success.sender.detailed", "Returned {0} deadchest(s) to {1} using {2}");
        values.put("commands.giveback.success.target.detailed", "You have retrieved {0} deadchest(s) using {1}");
        values.put("commands.giveback.queued.sender", "Queued {0} deadchest(s) for {1} using {2}");
        values.put("commands.giveback.pending-delivered", "You have received {0} queued deadchest(s)");
        localization.set(values);
        DeadChestLoader.local = localization;
        DeadChestLoader.ignoreList = server.createInventory(null, 9);
        DeadChestLoader.plugin = MockBukkit.createMockPlugin();
        DeadChestLoader.getChestDataCache().setChestData(new ArrayList<>());
        DeadChestLoader.sqlExecutor = new SQLExecutor();
        DeadChestLoader.db = new SQLite(DeadChestLoader.plugin);
        Path dbPath = DeadChestLoader.plugin.getDataFolder().toPath().resolve("data.db");
        Files.createDirectories(DeadChestLoader.plugin.getDataFolder().toPath());
        Files.deleteIfExists(dbPath);
        DeadChestLoader.db.init();
        ChestDataRepository.initTable(() -> {
        });
        PendingGivebackRepository.initialize(DeadChestLoader.plugin);
        awaitAsyncDb();
        DeadChestLoader.config = new DeadChestConfig(DeadChestLoader.plugin);
        for (ConfigKey key : ConfigKey.values()) {
            DeadChestLoader.config.register(key);
        }
        service = new DCCommandRegistrationService(mock(DeadChestLoader.class));
    }

    @AfterEach
    public void tearDown() {
        DeadChestLoader.sqlExecutor.shutdown();
        DeadChestLoader.db.close();
        DeadChestLoader.sqlExecutor = new SQLExecutor();
        MockBukkit.unmock();
    }

    @Test
    public void registerRemoveOwnRequiresPlayerContext() {
        CommandSender console = mock(CommandSender.class);

        service.register(console, new String[]{"remove"});
        service.registerRemoveOwn();

        assertTrue(service.isCommandSucceed());
        verify(console, atLeastOnce()).sendMessage(contains("Command must be called by a player"));
    }

    @Test
    public void registerIgnoreListOpensInventoryForAdminPlayer() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.ADMIN.label, true);
        Inventory ignoreInventory = DeadChestLoader.ignoreList;

        service.register(admin, new String[]{"ignore"});
        service.registerIgnoreList();

        assertTrue(service.isCommandSucceed());
        assertNotNull(admin.getOpenInventory());
        assertSame(ignoreInventory, admin.getOpenInventory().getTopInventory());
    }

    @Test
    public void registerConfigSetUpdatesCanonicalYamlKey() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.CONFIG.label, true);

        service.register(admin, new String[]{"config", "set", "localization.language", "fr"});
        service.registerConfigSet();

        assertTrue(service.isCommandSucceed());
        assertEquals("fr", DeadChestLoader.config.getString(ConfigKey.LOCALIZATION_LANGUAGE));
        assertEquals("fr", DeadChestLoader.plugin.getConfig().getString(ConfigKey.LOCALIZATION_LANGUAGE.toString()));
    }

    @Test
    public void registerConfigEditRejectsNonInteractiveKeys() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.CONFIG.label, true);

        service.register(admin, new String[]{"config", "edit", "localization.language"});
        service.registerConfigEdit();

        assertTrue(service.isCommandSucceed());
        assertNotSame(DeadChestLoader.ignoreList, admin.getOpenInventory().getTopInventory());
    }

    @Test
    public void registerGiveBackClassicRemovesTrackedChestForTargetPlayer() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData chestData = chestDataAt(40, target);
        DeadChestLoader.getChestDataCache().addChestData(chestData);
        target.getWorld().getBlockAt(chestData.getChestLocation()).setType(Material.CHEST);

        service.register(admin, new String[]{"giveback", target.getName()});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        assertNull(DeadChestLoader.getChestDataCache().getChestData(chestData.getChestLocation()));
        assertEquals(Material.AIR, target.getWorld().getBlockAt(chestData.getChestLocation()).getType());
    }

    @Test
    public void registerGiveBackListShowsSortedEntries() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData older = chestDataAt(20, target);
        ChestData newer = chestDataAt(21, target);
        DeadChestLoader.getChestDataCache().addChestData(newer);
        DeadChestLoader.getChestDataCache().addChestData(older);

        service.register(admin, new String[]{"giveback", "list", target.getName()});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(older.getChestLocation()));
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(newer.getChestLocation()));
        assertFalse(drainMessages(admin).stream().anyMatch(message -> message.contains("for list")));
    }

    @Test
    public void registerGiveBackDefaultSelectsLatestChestOnly() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData older = chestDataAt(20, target);
        ChestData newer = chestDataAt(21, target);
        DeadChestLoader.getChestDataCache().addChestData(older);
        DeadChestLoader.getChestDataCache().addChestData(newer);
        target.getWorld().getBlockAt(older.getChestLocation()).setType(Material.CHEST);
        target.getWorld().getBlockAt(newer.getChestLocation()).setType(Material.CHEST);

        service.register(admin, new String[]{"giveback", target.getName()});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(older.getChestLocation()));
        assertNull(DeadChestLoader.getChestDataCache().getChestData(newer.getChestLocation()));
        assertEquals(Material.CHEST, target.getWorld().getBlockAt(older.getChestLocation()).getType());
        assertEquals(Material.AIR, target.getWorld().getBlockAt(newer.getChestLocation()).getType());
        assertTrue(drainMessages(admin).stream().anyMatch(message -> message.contains("Returned 1 deadchest(s)")));
    }

    @Test
    public void registerGiveBackLatestSelectsLatestChestOnly() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData older = chestDataAt(20, target);
        ChestData newer = chestDataAt(21, target);
        DeadChestLoader.getChestDataCache().addChestData(older);
        DeadChestLoader.getChestDataCache().addChestData(newer);
        target.getWorld().getBlockAt(older.getChestLocation()).setType(Material.CHEST);
        target.getWorld().getBlockAt(newer.getChestLocation()).setType(Material.CHEST);

        service.register(admin, new String[]{"giveback", target.getName(), "latest"});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(older.getChestLocation()));
        assertNull(DeadChestLoader.getChestDataCache().getChestData(newer.getChestLocation()));
        assertEquals(Material.CHEST, target.getWorld().getBlockAt(older.getChestLocation()).getType());
        assertEquals(Material.AIR, target.getWorld().getBlockAt(newer.getChestLocation()).getType());
    }

    @Test
    public void registerGiveBackOldestSelectsOldestChest() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData older = chestDataAt(20, target);
        ChestData newer = chestDataAt(21, target);
        DeadChestLoader.getChestDataCache().addChestData(newer);
        DeadChestLoader.getChestDataCache().addChestData(older);
        target.getWorld().getBlockAt(older.getChestLocation()).setType(Material.CHEST);
        target.getWorld().getBlockAt(newer.getChestLocation()).setType(Material.CHEST);

        service.register(admin, new String[]{"giveback", target.getName(), "oldest"});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        assertNull(DeadChestLoader.getChestDataCache().getChestData(older.getChestLocation()));
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(newer.getChestLocation()));
    }

    @Test
    public void registerGiveBackIdSelectsRequestedChest() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData older = chestDataAt(20, target);
        ChestData newer = chestDataAt(21, target);
        DeadChestLoader.getChestDataCache().addChestData(older);
        DeadChestLoader.getChestDataCache().addChestData(newer);
        target.getWorld().getBlockAt(older.getChestLocation()).setType(Material.CHEST);
        target.getWorld().getBlockAt(newer.getChestLocation()).setType(Material.CHEST);

        service.register(admin, new String[]{"giveback", target.getName(), "id", "2"});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(older.getChestLocation()));
        assertNull(DeadChestLoader.getChestDataCache().getChestData(newer.getChestLocation()));
    }

    @Test
    public void registerGiveBackIdRejectsInvalidStrategyWithoutRemovingChest() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData older = chestDataAt(20, target);
        ChestData newer = chestDataAt(21, target);
        DeadChestLoader.getChestDataCache().addChestData(older);
        DeadChestLoader.getChestDataCache().addChestData(newer);
        target.getWorld().getBlockAt(older.getChestLocation()).setType(Material.CHEST);
        target.getWorld().getBlockAt(newer.getChestLocation()).setType(Material.CHEST);

        service.register(admin, new String[]{"giveback", target.getName(), "id", "2", "banana"});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(older.getChestLocation()));
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(newer.getChestLocation()));
        assertEquals(Material.CHEST, target.getWorld().getBlockAt(older.getChestLocation()).getType());
        assertEquals(Material.CHEST, target.getWorld().getBlockAt(newer.getChestLocation()).getType());
        assertTrue(drainMessages(admin).stream().anyMatch(message -> message.contains("banana")));
    }

    @Test
    public void registerGiveBackQueuesOfflineTargetAndDeliversOnNextLogin() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData chestData = chestDataAt(60, target);
        DeadChestLoader.getChestDataCache().addChestData(chestData);
        ChestDataRepository.save(chestData);
        assertEquals(1, ChestDataRepository.findAll().size());
        target.getWorld().getBlockAt(chestData.getChestLocation()).setType(Material.CHEST);
        assertTrue(target.disconnect());

        service.register(admin, new String[]{"giveback", target.getName(), "latest", "inventory"});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        assertNull(DeadChestLoader.getChestDataCache().getChestData(chestData.getChestLocation()));
        assertTrue(ChestDataRepository.findAll().isEmpty());
        assertEquals(Material.AIR, target.getWorld().getBlockAt(chestData.getChestLocation()).getType());

        assertEquals(1, countPendingGivebacks(target.getUniqueId()));

        assertTrue(target.reconnect());
        assertEquals(1, PendingGivebackRepository.deliverPending(target));
        assertNotNull(target.getInventory().getItem(0));
        assertEquals(Material.DIAMOND, target.getInventory().getItem(0).getType());

        assertEquals(0, countPendingGivebacks(target.getUniqueId()));
    }

    @Test
    public void registerGiveBackPreviewDoesNotRemoveTrackedChest() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData chestData = chestDataAt(50, target);
        DeadChestLoader.getChestDataCache().addChestData(chestData);
        target.getWorld().getBlockAt(chestData.getChestLocation()).setType(Material.CHEST);

        service.register(admin, new String[]{"giveback", "preview", target.getName()});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(chestData.getChestLocation()));
        assertEquals(Material.CHEST, target.getWorld().getBlockAt(chestData.getChestLocation()).getType());
        assertTrue(admin.nextMessage().contains("Preview"));
    }

    @Test
    public void registerGiveBackPreviewListShowsEntriesWithoutApplying() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        ChestData chestData = chestDataAt(51, target);
        DeadChestLoader.getChestDataCache().addChestData(chestData);

        service.register(admin, new String[]{"giveback", "preview", "list", target.getName()});
        service.registerGiveBack();

        assertTrue(service.isCommandSucceed());
        List<String> messages = drainMessages(admin);
        assertTrue(messages.stream().anyMatch(message -> message.contains("Giveback preview list")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("ID 1")));
        assertNotNull(DeadChestLoader.getChestDataCache().getChestData(chestData.getChestLocation()));
    }

    @Test
    public void registerRemoveOtherClassicRemovesTrackedChestForOnlineTargetPlayer() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Steve");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.REMOVE_OTHER.label, true);

        ChestData chestData = chestDataAt(41, target);
        DeadChestLoader.getChestDataCache().addChestData(chestData);
        target.getWorld().getBlockAt(chestData.getChestLocation()).setType(Material.CHEST);

        service.register(admin, new String[]{"remove", target.getName()});
        service.registerRemoveOther();

        assertTrue(service.isCommandSucceed());
        assertNull(DeadChestLoader.getChestDataCache().getChestData(chestData.getChestLocation()));
        assertEquals(Material.AIR, target.getWorld().getBlockAt(chestData.getChestLocation()).getType());
    }

    private ChestData chestDataAt(int x, PlayerMock player) {
        UUID timerId = UUID.nameUUIDFromBytes(("timer-" + x).getBytes());
        UUID ownerId = UUID.nameUUIDFromBytes(("owner-" + x).getBytes());
        Location chestLocation = new Location(player.getWorld(), x, 64, x, 0f, 0f);
        Location holoLocation = new Location(player.getWorld(), x, 65, x, 0f, 0f);

        List<ItemStack> inventory = new ArrayList<>();
        inventory.add(new ItemStack(Material.DIAMOND, 1));

        return new ChestData(
                inventory,
                chestLocation,
                player.getName(),
                player.getUniqueId(),
                new Date(1_700_000_000_000L + x),
                false,
                false,
                holoLocation,
                timerId,
                null,
                ownerId,
                player.getWorld().getName(),
                0
        );
    }

    private int countPendingGivebacks(UUID playerUuid) throws Exception {
        try (PreparedStatement ps = DeadChestLoader.db.connection().prepareStatement(
                "SELECT COUNT(*) FROM pending_givebacks WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
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
    }

    private List<String> drainMessages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        while (true) {
            try {
                String message = player.nextMessage();
                if (message == null) {
                    break;
                }
                messages.add(message);
            } catch (Throwable ignored) {
                break;
            }
        }
        return messages;
    }
}
