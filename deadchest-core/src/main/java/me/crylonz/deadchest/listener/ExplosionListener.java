package me.crylonz.deadchest.listener;

import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.db.InMemoryChestStore;
import me.crylonz.deadchest.utils.ConfigKey;
import org.bukkit.block.Block;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static me.crylonz.deadchest.DeadChestLoader.config;
import static me.crylonz.deadchest.utils.Utils.generateLog;
import static me.crylonz.deadchest.utils.Utils.isGraveBlock;

public class ExplosionListener implements Listener {

    @EventHandler
    public void onEntityExplodeEvent(EntityExplodeEvent e) {
        if (DeadChestLoader.hasWindCharge && e.getEntity() instanceof WindCharge) {
            return;
        }
        chestExplosionHandler(e);
    }

    @EventHandler
    public void onBlockExplodeEvent(BlockExplodeEvent e) {
        chestExplosionHandler(e);
    }

    public void chestExplosionHandler(Event e) {
        List<Block> blocklist = new ArrayList<>();
        if (e instanceof EntityExplodeEvent) {
            blocklist = ((EntityExplodeEvent) e).blockList();
        } else if (e instanceof BlockExplodeEvent) {
            blocklist = ((BlockExplodeEvent) e).blockList();
        }

        if (!blocklist.isEmpty()) {
            final InMemoryChestStore inMemoryChestStore = DeadChestLoader.getChestDataCache();
            for (int i = 0; i < blocklist.size(); ++i) {
                Block block = blocklist.get(i);
                if (!isGraveBlock(block.getType())) {
                    continue;
                }
                final ChestData chestData = inMemoryChestStore.getChestData(block.getLocation());

                if (chestData != null) {
                    if (config.getBoolean(ConfigKey.INDESTRUCTIBLE_CHEST)) {
                        blocklist.remove(block);
                        generateLog("Deadchest of [" + chestData.getPlayerName() + "] was protected from explosion in " + Objects.requireNonNull(chestData.getChestLocation().getWorld()).getName());
                    } else {
                        inMemoryChestStore.removeChestData(chestData);
                        generateLog("Deadchest of [" + chestData.getPlayerName() + "] was blown up in " + Objects.requireNonNull(chestData.getChestLocation().getWorld()).getName());
                    }
                }
            }
        }
    }
}
