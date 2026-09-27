package me.crylonz.deadchest.listener;

import me.crylonz.deadchest.PendingGivebackRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import static me.crylonz.deadchest.DeadChestLoader.local;

public class PendingGivebackJoinListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int delivered = PendingGivebackRepository.deliverPending(player);
        if (delivered > 0) {
            player.sendMessage(local.prefixed("commands.giveback.pending-delivered", delivered));
        }
    }
}
