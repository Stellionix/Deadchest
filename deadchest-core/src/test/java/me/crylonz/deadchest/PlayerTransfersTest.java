package me.crylonz.deadchest;

import be.seeseemelk.mockbukkit.*;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.crylonz.deadchest.db.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerTransfersTest {
    ServerMock server; WorldMock world; PlayerMock player;
    Set<String> receipts;
    @BeforeEach void setup() {
        server=MockBukkit.mock(); world=new WorldMock() { @Override public boolean isChunkForceLoaded(int x,int z) { return false; } }; server.addWorld(world);
        DeadChestLoader.plugin=MockBukkit.createMockPlugin(); ChestLifecycle.observe(null);
        DeadChestLoader.db=new SQLite(DeadChestLoader.plugin); DeadChestLoader.db.init();
        DeadChestLoader.sqlExecutor=mock(SQLExecutor.class);
        doAnswer(a->{((Runnable)a.getArgument(0)).run();return null;}).when(DeadChestLoader.sqlExecutor).runAsync(any());
        ChestDataRepository.initTable(()->{}); PlayerTransfers.initialize();
        DeadChestLoader.getChestDataCache().setChestData(new ArrayList<>());
        player=spy(server.addPlayer("Transfer")); receipts=new HashSet<>();
        doReturn(receipts).when(player).getScoreboardTags();
        doAnswer(a->receipts.add(a.getArgument(0))).when(player).addScoreboardTag(anyString());
        doAnswer(a->receipts.remove(a.getArgument(0))).when(player).removeScoreboardTag(anyString());
        doNothing().when(player).saveData();
        DeadChestLoader.graveBlocks.add(Material.CHEST);
    }
    @AfterEach void tearDown() { ChestLifecycle.observe(null); DeadChestLoader.db.close(); MockBukkit.unmock(); }
    ChestData chest() {
        Location l=new Location(world,0,65,0); l.getBlock().setType(Material.CHEST);
        return new ChestData(Arrays.asList(new ItemStack(Material.DIAMOND,17)),l,"Transfer",player.getUniqueId(),new Date(),false,false,
                l,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),world.getName(),100);
    }
    TransferStore store() { return new TransferStore(DeadChestLoader.plugin.getDataFolder().toPath().resolve("data.db")); }
    void restartAndResume() { PlayerTransfers.initialize(); PlayerTransfers.active().resume(player); server.getScheduler().performTicks(8); }
    int diamonds() { return Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(i->i.getType()==Material.DIAMOND).mapToInt(ItemStack::getAmount).sum(); }

    @Test void absentReceiptRetriesExactlyOnceAcrossRestarts() {
        ChestData c=chest(); ChestDataRepository.save(c);
        Properties op=TransferSnapshot.encode(c,"RECOVER",player.getUniqueId()); store().insert(op);
        restartAndResume();
        assertEquals(17,diamonds()); assertEquals(100,player.getTotalExperience()); assertTrue(store().pending().isEmpty());
        restartAndResume(); assertEquals(17,diamonds()); assertEquals(100,player.getTotalExperience());
        assertTrue(ChestDataRepository.findAll().isEmpty());
    }
    @Test void savedReceiptFinishesSqlWithoutGivingAgain() {
        ChestData c=chest(); ChestDataRepository.save(c);
        Properties op=TransferSnapshot.encode(c,"RECOVER",player.getUniqueId()); store().insert(op);
        TransferReceipt.apply(player,op.getProperty("id"),()->{player.getInventory().addItem(new ItemStack(Material.DIAMOND,17));player.giveExp(100);});
        restartAndResume(); assertEquals(17,diamonds()); assertEquals(100,player.getTotalExperience()); assertTrue(store().pending().isEmpty());
    }
    @Test void creationWithoutReceiptRollsBackChestNotInventory() {
        ChestData c=chest(); ChestDataRepository.save(c);
        player.getInventory().addItem(new ItemStack(Material.DIAMOND,17));
        store().insert(TransferSnapshot.encode(c,"CREATE",player.getUniqueId()));
        restartAndResume(); assertEquals(17,diamonds()); assertTrue(ChestDataRepository.findAll().isEmpty()); assertTrue(store().pending().isEmpty());
    }
    @Test void creationWithReceiptRecreatesMissingSqlAndUnlocksChest() {
        ChestData c=chest(); Properties op=TransferSnapshot.encode(c,"CREATE",player.getUniqueId()); store().insert(op);
        receipts.add(TransferReceipt.PREFIX+op.getProperty("id"));
        restartAndResume(); assertEquals(1,ChestDataRepository.findAll().size()); assertEquals(0,diamonds());
        assertTrue(DeadChestLoader.getChestData(c.getChestLocation()).claimTransition()); assertTrue(store().pending().isEmpty());
    }
    @Test void recreationPreservesConfiguredGraveMaterial() {
        ChestData c=chest(); c.getChestLocation().getBlock().setType(Material.BARREL);
        Properties op=TransferSnapshot.encode(c,"CREATE",player.getUniqueId());store().insert(op);
        c.getChestLocation().getBlock().setType(Material.AIR);receipts.add(TransferReceipt.PREFIX+op.getProperty("id"));
        restartAndResume();assertEquals(Material.BARREL,c.getChestLocation().getBlock().getType());
    }
    @Test void pendingReceiptTransferCannotAlsoBeDeliveredByLegacyApi() {
        ChestData c=chest();DeadChestLoader.getChestDataCache().addChestData(c);
        PlayerTransfers.active().recover(c,player);
        assertFalse(DeadChestAPI.giveBackChest(player,c));
        assertFalse(DeadChestAPI.giveBackChestAsync(player,c).join());
        server.getScheduler().performTicks(8);assertEquals(17,diamonds());assertTrue(store().pending().isEmpty());
    }
    @Test void fullInventoryWaitsThenDeliversWithoutDrops() {
        for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        ChestData c=chest(); store().insert(TransferSnapshot.encode(c,"RESTORE",player.getUniqueId()));
        restartAndResume(); assertEquals(1,store().pending().size()); assertEquals(0,diamonds()); assertEquals(0,player.getTotalExperience());
        player.getInventory().setItem(0,null); PlayerTransfers.active().resume(player); server.getScheduler().performTicks(8);
        assertEquals(17,diamonds()); assertEquals(100,player.getTotalExperience()); assertTrue(store().pending().isEmpty());
    }
    @Test void receiptIsSavedTogetherWithItemsAndXp() {
        String id=UUID.randomUUID().toString();
        doAnswer(a->{assertTrue(TransferReceipt.present(player,id));assertEquals(17,diamonds());assertEquals(100,player.getTotalExperience());return null;}).when(player).saveData();
        TransferReceipt.apply(player,id,()->{player.getInventory().addItem(new ItemStack(Material.DIAMOND,17));player.giveExp(100);});
        TransferReceipt.apply(player,id,()->fail("Already received")); verify(player,times(1)).saveData();
    }
    @Test void failedMutationRollsBackItemsXpAndReceipt() {
        String id=UUID.randomUUID().toString(); player.giveExp(20);
        assertThrows(IllegalStateException.class,()->TransferReceipt.apply(player,id,()->{
            player.getInventory().addItem(new ItemStack(Material.DIAMOND,17));player.giveExp(100);throw new IllegalStateException("failure");
        }));
        assertEquals(0,diamonds());assertEquals(20,player.getTotalExperience());assertFalse(TransferReceipt.present(player,id));verify(player,never()).saveData();
    }
    @Test void sqlHistoryFailureLeavesReceiptForSafeRetry() {
        ChestLifecycle.Observer observer=mock(ChestLifecycle.Observer.class); ChestLifecycle.observe(observer);
        java.util.concurrent.CompletableFuture<Void> failure=new java.util.concurrent.CompletableFuture<>();failure.completeExceptionally(new IllegalStateException("history unavailable"));
        when(observer.reconcile(any(),anyString(),anyString())).thenReturn(failure);
        ChestData c=chest();store().insert(TransferSnapshot.encode(c,"RECOVER",player.getUniqueId()));
        restartAndResume();assertEquals(17,diamonds());assertEquals(1,store().pending().size());
        when(observer.reconcile(any(),anyString(),anyString())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        restartAndResume();assertEquals(17,diamonds());assertEquals(100,player.getTotalExperience());assertTrue(store().pending().isEmpty());
        ChestLifecycle.observe(null);
    }
}
