package me.crylonz.deadchest;

import me.crylonz.deadchest.commands.GivebackDelivery;
import me.crylonz.deadchest.commands.GivebackStrategy;
import me.crylonz.deadchest.utils.ItemBytes;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static me.crylonz.deadchest.DeadChestLoader.db;

public final class PendingGivebackRepository {
    private static boolean initialized;

    private PendingGivebackRepository() {
    }

    public static synchronized void initialize(Plugin plugin) {
        try (Statement st = db.connection().createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS pending_givebacks (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "player_uuid TEXT NOT NULL," +
                            "player_name TEXT NOT NULL," +
                            "strategy TEXT NOT NULL," +
                            "xp_stored INTEGER NOT NULL," +
                            "inventory BLOB NOT NULL," +
                            "created_at BIGINT NOT NULL" +
                            ")"
            );
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_givebacks_player ON pending_givebacks(player_uuid, id)");
            initialized = true;
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to create pending_givebacks schema", ex);
        }
    }

    public static synchronized boolean queueChest(UUID playerUuid,
                                                  String playerName,
                                                  ChestData chestData,
                                                  GivebackStrategy strategy) {
        if (playerUuid == null || chestData == null || strategy == null) {
            return false;
        }

        ensureInitialized();
        String insertPending = "INSERT INTO pending_givebacks(player_uuid, player_name, strategy, xp_stored, inventory, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        String deleteChest = "DELETE FROM chest_data WHERE player_uuid = ? AND chest_world = ? AND chest_x = ? AND chest_y = ? AND chest_z = ?";

        Connection conn = null;
        boolean previousAutoCommit = true;
        try {
            conn = db.connection();
            previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(insertPending);
                 PreparedStatement delete = conn.prepareStatement(deleteChest)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, strategy.token());
                ps.setInt(4, chestData.getXpStored());
                ps.setBytes(5, ItemBytes.toBytesList(chestData.getInventory()));
                ps.setLong(6, System.currentTimeMillis());
                if (ps.executeUpdate() != 1) {
                    conn.rollback();
                    return false;
                }

                bindChestDelete(delete, chestData);
                delete.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            rollback(conn);
            DeadChestLoader.log.warning("[DeadChest] Could not queue pending giveback: " + ex.getMessage());
            return false;
        } finally {
            restoreAutoCommit(conn, previousAutoCommit);
        }
    }

    public static synchronized int deliverPending(Player player) {
        if (player == null) {
            return 0;
        }

        ensureInitialized();
        List<PendingGiveback> pending = findPending(player.getUniqueId());
        int delivered = 0;
        for (PendingGiveback giveback : pending) {
            GivebackStrategy strategy = GivebackStrategy.fromToken(giveback.strategy);
            if (strategy == null) {
                strategy = GivebackStrategy.defaultStrategy();
            }

            GivebackDelivery.deliver(player, giveback.items, giveback.xpStored, strategy);
            if (delete(giveback.id)) {
                delivered++;
            }
        }
        return delivered;
    }

    private static List<PendingGiveback> findPending(UUID playerUuid) {
        List<PendingGiveback> pending = new ArrayList<>();
        String sql = "SELECT id, strategy, xp_stored, inventory FROM pending_givebacks WHERE player_uuid = ? ORDER BY id";

        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pending.add(new PendingGiveback(
                            rs.getLong("id"),
                            rs.getString("strategy"),
                            rs.getInt("xp_stored"),
                            ItemBytes.fromBytesList(rs.getBytes("inventory"))
                    ));
                }
            }
        } catch (SQLException | RuntimeException ex) {
            DeadChestLoader.log.warning("[DeadChest] Could not read pending givebacks: " + ex.getMessage());
        }
        return pending;
    }

    private static boolean delete(long id) {
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM pending_givebacks WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            DeadChestLoader.log.warning("[DeadChest] Could not delete pending giveback: " + ex.getMessage());
            return false;
        }
    }

    private static void bindChestDelete(PreparedStatement ps, ChestData chestData) throws SQLException {
        Location chestLoc = chestData.getChestLocation();
        String worldName = chestLoc.getWorld() != null ? chestLoc.getWorld().getName() : chestData.getWorldName();
        if (worldName == null) {
            throw new SQLException("Pending giveback chest has no world name");
        }

        ps.setString(1, chestData.getPlayerStringUUID());
        ps.setString(2, worldName);
        ps.setInt(3, chestLoc.getBlockX());
        ps.setInt(4, chestLoc.getBlockY());
        ps.setInt(5, chestLoc.getBlockZ());
    }

    private static void rollback(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static void restoreAutoCommit(Connection conn, boolean previousAutoCommit) {
        if (conn == null) {
            return;
        }
        try {
            conn.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {
        }
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("PendingGivebackRepository is not initialized");
        }
    }

    private static final class PendingGiveback {
        private final long id;
        private final String strategy;
        private final int xpStored;
        private final List<ItemStack> items;

        private PendingGiveback(long id, String strategy, int xpStored, List<ItemStack> items) {
            this.id = id;
            this.strategy = strategy;
            this.xpStored = xpStored;
            this.items = items;
        }
    }
}
