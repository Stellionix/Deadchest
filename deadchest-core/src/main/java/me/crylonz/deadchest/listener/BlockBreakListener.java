package me.crylonz.deadchest.listener;

import me.crylonz.deadchest.Permission;
import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.utils.ConfigKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import static me.crylonz.deadchest.DeadChestLoader.config;
import static me.crylonz.deadchest.DeadChestLoader.local;
import static me.crylonz.deadchest.utils.Utils.isGraveBlock;

public class BlockBreakListener implements Listener {

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent e) {
        if (isGraveBlock(e.getBlock().getType())) {
            if (config.getBoolean(ConfigKey.INDESTRUCTIBLE_CHEST)) {
                final ChestData chestData = DeadChestLoader.getChestData(e.getBlock().getLocation());
                if(chestData != null){
                    e.setCancelled(true);
                    // Breaking the block is forbidden even while its owner's asynchronous
                    // pickup is finishing. Only report an ownership denial for denied access.
                    if (!ClickListener.canOpenChest(chestData, e.getPlayer(),
                            e.getPlayer().getUniqueId(),
                            e.getPlayer().hasPermission(Permission.CHESTPASS.label))) {
                        e.getPlayer().sendMessage(local.prefixed("chest.not-owner"));
                    }
                }


            }
        }
    }
}
