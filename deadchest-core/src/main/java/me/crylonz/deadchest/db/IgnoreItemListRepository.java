package me.crylonz.deadchest.db;

import me.crylonz.deadchest.utils.ItemBytes;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static me.crylonz.deadchest.DeadChestLoader.*;

public class IgnoreItemListRepository {

    public static void initTable() {
        sqlExecutor.runAsync(() -> {
            try (Statement st = db.connection().createStatement()) {
                st.execute(
                        "CREATE TABLE IF NOT EXISTS ignore_items (" +
                                "slot INTEGER PRIMARY KEY, " +
                                "data BLOB NOT NULL)"
                );
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create ignore_items schema", e);
            }
        });
    }

    public static void loadIgnoreIntoInventory(Inventory inv) {
        sqlExecutor.runAsync(() -> {
            try (PreparedStatement request = db.connection().prepareStatement(
                    "SELECT slot, data FROM ignore_items");
                 ResultSet result = request.executeQuery()) {

                ItemStack[] contents = new ItemStack[inv.getSize()];
                boolean any = false;

                while (result.next()) {
                    int slot = result.getInt("slot");
                    if (slot < 0 || slot >= contents.length) continue;
                    contents[slot] = ItemBytes.fromBytes(result.getBytes("data"));
                    any = true;
                }

                if (any) {
                    inv.setContents(contents);
                    log.info("[DeadChest] Ignore list loaded from SQLite.");
                } else {
                    log.info("[DeadChest] Ignore list empty (no SQLite row)");
                }

            } catch (SQLException e) {
                log.severe("[DeadChest] Failed to load ignore list from SQLite: " + e.getMessage());
            }
        });
    }

    public static void saveIgnoreIntoInventory(Inventory inv) {
        sqlExecutor.runAsync(() -> {
            try (Statement clear = db.connection().createStatement()) {
                clear.executeUpdate("DELETE FROM ignore_items");
            } catch (SQLException e) {
                log.severe("[DeadChest] Failed to clear ignore_items: " + e.getMessage());
            }

            String sql = "INSERT INTO ignore_items(slot, data) VALUES(?, ?)";
            try (PreparedStatement request = db.connection().prepareStatement(sql)) {
                ItemStack[] contents = inv.getContents();
                for (int slot = 0; slot < contents.length; slot++) {
                    byte[] bytes = ItemBytes.toBytes(contents[slot]);
                    if (bytes == null || bytes.length == 0) continue;
                    request.setInt(1, slot);
                    request.setBytes(2, bytes);
                    request.addBatch();
                }
                request.executeBatch();
                log.info("[DeadChest] Ignore list saved to SQLite.");
            } catch (SQLException e) {
                log.severe("[DeadChest] Failed to save ignore list to SQLite: " + e.getMessage());
            }
        });
    }

    public static List<ItemStack> loadIgnoreItems() {
        List<ItemStack> items = new ArrayList<>();

        try (PreparedStatement request = db.connection().prepareStatement("SELECT data FROM ignore_items ORDER BY slot");
             ResultSet result = request.executeQuery()) {
            while (result.next()) {
                ItemStack item = ItemBytes.fromBytes(result.getBytes("data"));
                if (item != null && !me.crylonz.deadchest.utils.LegacySupport.isAir(item.getType())) {
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            log.severe("[DeadChest] Failed to read ignore list from SQLite: " + e.getMessage());
        }

        return items;
    }

    public static void clearIgnoreItems() {
        try (Statement clear = db.connection().createStatement()) {
            clear.executeUpdate("DELETE FROM ignore_items");
        } catch (SQLException e) {
            log.severe("[DeadChest] Failed to clear legacy ignore_items: " + e.getMessage());
        }
    }
}
