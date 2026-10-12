package me.crylonz.deadchest;

import me.crylonz.deadchest.utils.Utils;
import me.crylonz.deadchest.utils.ItemBytes;
import me.crylonz.deadchest.db.ChestDataRepository;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Receipt-driven recovery of transfers between Minecraft player data and SQLite. */
public final class PlayerTransfers implements Listener {
    private static volatile PlayerTransfers instance;
    private final Path path;
    private final TransferStore store;
    private volatile boolean ready=true;
    private final Map<String,Properties> pending = new ConcurrentHashMap<>();
    private final Set<String> busy = ConcurrentHashMap.newKeySet();
    private final Map<String,CompletableFuture<Boolean>> waiting = new ConcurrentHashMap<>();
    private final Map<UUID, PendingClick> clicks = new ConcurrentHashMap<>();

    private static final class PendingClick {
        final ChestData chest;
        final UUID player;
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        PendingClick(ChestData chest, Player player) { this.chest=chest; this.player=player.getUniqueId(); }
    }

    private PlayerTransfers(Path path) {
        this.path=path; store=new TransferStore(path);
        for (Properties p:store.pending()) pending.put(p.getProperty("id"),p);
    }
    public static synchronized PlayerTransfers active() {
        Path path=DeadChestLoader.plugin.getDataFolder().toPath().resolve("data.db").toAbsolutePath();
        if (instance==null || !path.equals(instance.path)) instance=new PlayerTransfers(path);
        return instance;
    }
    public static synchronized void initialize() { instance=null; active(); }
    public void awaitHydration() { ready=false; }
    public void hydrated() { ready=true; }
    public boolean isReady() { return ready; }
    public static boolean blocked(UUID chest) {
        PlayerTransfers current=instance;
        return chest!=null && current!=null && (current.clicks.containsKey(chest)
                || current.pending.values().stream().anyMatch(p->chest.toString().equals(p.getProperty("chest"))));
    }

    private synchronized Properties prepare(ChestData chest, String kind, UUID recipient) {
        Properties p=TransferSnapshot.encode(chest,kind,recipient);
        store.insert(p); pending.put(p.getProperty("id"),p); busy.add(p.getProperty("id"));
        return p;
    }

    public Properties prepareCreation(ChestData chest, Player player) {
        return prepare(chest,"CREATE",player.getUniqueId());
    }
    public void creationSaved(Properties operation, ChestData chest, Player player) {
        finish(operation,chest,true,player);
    }
    public void failed(Properties operation, Throwable error) {
        busy.remove(operation.getProperty("id"));
        DeadChestLoader.log.log(java.util.logging.Level.WARNING,"DeadChest transfer will retry: "+operation.getProperty("id"),error);
    }

    public CompletableFuture<Boolean> recover(ChestData chest, Player player) {
        if (!chest.claimTransition()) return CompletableFuture.completedFuture(false);
        PendingClick click = new PendingClick(chest, player);
        CompletableFuture<Boolean> result = click.result;
        clicks.put(chest.getId(), click);
        // Packing, durable intent and player save execute together on the player's owning thread.
        DeadChestLoader.getSchedulerAdapter().runForEntity(player, () -> {
            if (clicks.get(chest.getId()) != click) return;
            Properties operation = null;
            try {
                if (!player.isOnline() || player.isDead()) { unlock(chest, result); return; }
                InventoryTransfer.Packing packing = InventoryTransfer.partial(player.getInventory().getContents(), chest.getInventory(), true);
                if (packing.moved == 0 && (packing.hasRemainder() || chest.getXpStored() == 0)) {
                    unlock(chest, result); return;
                }
                operation = TransferSnapshot.encode(chest, "PICKUP", player.getUniqueId());
                operation.setProperty("remaining", Base64.getEncoder().encodeToString(ItemBytes.toBytesList(packing.remaining)));
                store.insert(operation);
                String id = operation.getProperty("id");
                busy.add(id); pending.put(id, operation);
                clicks.remove(chest.getId(), click);
                TransferReceipt.apply(player, id, () -> {
                    player.getInventory().setContents(packing.inventory);
                    player.giveExp(chest.getXpStored());
                });
                apply(operation, player, result);
            } catch (Throwable error) {
                if (operation != null && pending.containsKey(operation.getProperty("id"))) {
                    failed(operation, error); result.completeExceptionally(error);
                } else {
                    DeadChestLoader.log.log(java.util.logging.Level.WARNING,"Cannot prepare chest pickup",error);
                    unlock(chest, result);
                }
            } finally {
                clicks.remove(chest.getId(), click);
            }
        });
        return result;
    }

    private void unlock(ChestData chest, CompletableFuture<Boolean> result) {
        DeadChestLoader.getSchedulerAdapter().runAtLocation(chest.getChestLocation(), () -> {
            chest.cancelUnpreparedTransition(); result.complete(false);
        });
    }
    public CompletableFuture<Boolean> restore(ChestData chest, Player player) {
        if (blocked(chest.getId())) return CompletableFuture.completedFuture(false);
        return dispatch(prepare(chest,"RESTORE",player.getUniqueId()),player);
    }

    private CompletableFuture<Boolean> dispatch(Properties operation, Player player) {
        CompletableFuture<Boolean> result=new CompletableFuture<>();
        String id=operation.getProperty("id"); waiting.put(id,result);
        result.whenComplete((ok,error)->waiting.remove(id,result));
        DeadChestLoader.getSchedulerAdapter().runForEntity(player,()->apply(operation,player,result));
        // If the entity retires before this task, retry when it next joins.
        return result;
    }
    private void apply(Properties operation, Player player, CompletableFuture<Boolean> result) {
        String id=operation.getProperty("id");
        try {
            if (!player.isOnline() || player.isDead()) { busy.remove(id); result.complete(false); return; }
            ChestData chest=TransferSnapshot.decode(operation);
            boolean received=TransferReceipt.present(player,id);
            if ("PICKUP".equals(operation.getProperty("kind"))) {
                // After a crash, settle the exact saved remainder or roll back an unapplied click.
                // Never deliver more items merely because the player later frees inventory space.
                if (received) {
                    chest.setInventory(ItemBytes.fromBytesList(Base64.getDecoder().decode(operation.getProperty("remaining"))));
                    chest.setXpStored(0);
                }
                finish(operation,chest,received,player).whenComplete((ok,error)->complete(result,ok,error));
                return;
            }
            if ("CREATE".equals(operation.getProperty("kind"))) {
                // No receipt: Minecraft still holds the pre-death inventory. Roll back only the chest.
                finish(operation,chest,received,player).whenComplete((ok,error)->complete(result,ok,error));
                return;
            }
            if (!received) {
                ItemStack[] packed=InventoryTransfer.pack(player.getInventory().getContents(),chest.getInventory(),"RECOVER".equals(operation.getProperty("kind")));
                if (packed==null) { busy.remove(id); result.complete(false); return; }
                TransferReceipt.apply(player,id,()->{
                    player.getInventory().setContents(packed); player.giveExp(chest.getXpStored());
                });
            }
            finish(operation,chest,true,player).whenComplete((ok,error)->complete(result,ok,error));
        } catch (Throwable e) { failed(operation,e); result.completeExceptionally(e); }
    }
    private static void complete(CompletableFuture<Boolean> future, Boolean value, Throwable error) {
        if (error==null) future.complete(value); else future.completeExceptionally(error);
    }

    private CompletableFuture<Boolean> finish(Properties operation, ChestData chest, boolean received, Player player) {
        CompletableFuture<Boolean> result=new CompletableFuture<>();
        String kind=operation.getProperty("kind"), id=operation.getProperty("id");
        boolean create="CREATE".equals(kind) && received;
        boolean keepPickup = "PICKUP".equals(kind) && (!received || chest.getInventory().stream()
                .anyMatch(i -> i != null && i.getType() != Material.AIR && i.getAmount() > 0));
        DeadChestLoader.sqlExecutor.runAsync(()->{
            try {
                if (keepPickup) {
                    try (PreparedStatement s = DeadChestLoader.db.connection().prepareStatement(
                            "UPDATE chest_data SET inventory=?,xp_stored=? WHERE chest_uuid=?")) {
                        s.setBytes(1,ItemBytes.toBytesList(chest.getInventory())); s.setInt(2,chest.getXpStored());
                        s.setString(3,chest.getId().toString());
                        if (s.executeUpdate()==0 && ChestDataRepository.save(chest))
                            throw new IllegalStateException("Another chest occupies this location");
                    }
                } else if (create) {
                    boolean exists;
                    try (PreparedStatement s=DeadChestLoader.db.connection().prepareStatement("SELECT 1 FROM chest_data WHERE chest_uuid=?")) {
                        s.setString(1,chest.getId().toString()); try(ResultSet r=s.executeQuery()){exists=r.next();}
                    }
                    if (!exists && ChestDataRepository.save(chest)) throw new IllegalStateException("Another chest occupies this location");
                } else if (!"RESTORE".equals(kind)) {
                    try (PreparedStatement s=DeadChestLoader.db.connection().prepareStatement("DELETE FROM chest_data WHERE chest_uuid=?")) {
                        s.setString(1,chest.getId().toString()); s.executeUpdate();
                    }
                }
                String outcome=(create || keepPickup) ? "ACTIVE" : "CREATE".equals(kind) ? "DELETED" : "RESTORE".equals(kind) ? "RESTORED" : "RECOVERED";
                ChestLifecycle.reconcile(chest,outcome,operation.getProperty("recipient")).whenComplete((v,error)->{
                    if (error!=null) { failed(operation,error); result.completeExceptionally(error); return; }
                    DeadChestLoader.getSchedulerAdapter().runAtLocation(chest.getChestLocation(),()->{
                        try {
                            if (!"RESTORE".equals(kind)) {
                                ChestData existing=DeadChestLoader.getChestData(chest.getChestLocation());
                                if (existing!=null && !existing.getId().equals(chest.getId())) throw new IllegalStateException("Chest location is occupied");
                                if (create) {
                                    if (chest.getChestLocation().getBlock().getType()==Material.AIR)
                                        chest.getChestLocation().getBlock().setType(Material.valueOf(operation.getProperty("material","CHEST")));
                                } else if (!keepPickup) {
                                    if (Utils.isGraveBlock(chest.getChestLocation().getBlock().getType()))
                                        chest.getChestLocation().getBlock().setType(Material.AIR);
                                    chest.removeArmorStand();
                                }
                            }
                            store.complete(id);
                            pending.remove(id); busy.remove(id);
                            if (create || keepPickup) {
                                chest.creationSaved(); DeadChestLoader.getChestDataCache().addChestData(chest);
                            } else if (!"RESTORE".equals(kind)) DeadChestLoader.getChestDataCache().forgetChestData(chest);
                            result.complete(true);
                            DeadChestLoader.getSchedulerAdapter().runForEntity(player,()->player.removeScoreboardTag(TransferReceipt.PREFIX+id));
                        } catch(Throwable e) { failed(operation,e); result.completeExceptionally(e); }
                    });
                });
            } catch(Throwable e) { failed(operation,e); result.completeExceptionally(e); }
        });
        return result;
    }

    /** Called on the player's owning thread, after playerdata has been loaded. */
    public void resume(Player player) {
        if (!ready) return;
        for (String tag:new HashSet<>(player.getScoreboardTags()))
            if (tag.startsWith(TransferReceipt.PREFIX) && !pending.containsKey(tag.substring(TransferReceipt.PREFIX.length()))) player.removeScoreboardTag(tag);
        for (Properties p:pending.values()) if (player.getUniqueId().toString().equals(p.getProperty("recipient")) && busy.add(p.getProperty("id")))
            apply(p,player,new CompletableFuture<>());
    }
    public void tick() {
        for (PendingClick click : clicks.values()) {
            if (Bukkit.getPlayer(click.player)==null && clicks.remove(click.chest.getId(),click))
                unlock(click.chest,click.result);
        }
        for(Properties p:pending.values()) if(Bukkit.getPlayer(UUID.fromString(p.getProperty("recipient")))==null) retired(p);
        for (Player player:Bukkit.getOnlinePlayers()) DeadChestLoader.getSchedulerAdapter().runForEntity(player,()->resume(player));
    }
    private void retired(Properties p) {
        String id=p.getProperty("id"); busy.remove(id);
        CompletableFuture<Boolean> f=waiting.remove(id); if(f!=null)f.complete(false);
    }
    @EventHandler public void join(PlayerJoinEvent event) { DeadChestLoader.getSchedulerAdapter().runForEntity(event.getPlayer(),()->resume(event.getPlayer())); }
    @EventHandler public void respawn(PlayerRespawnEvent event) { DeadChestLoader.getSchedulerAdapter().runForEntity(event.getPlayer(),()->resume(event.getPlayer())); }
    @EventHandler public void close(InventoryCloseEvent event) { if(event.getPlayer() instanceof Player) DeadChestLoader.getSchedulerAdapter().runForEntity(event.getPlayer(),()->resume((Player)event.getPlayer())); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        for(Properties p:pending.values()) if(event.getPlayer().getUniqueId().toString().equals(p.getProperty("recipient"))) retired(p);
    }
}
