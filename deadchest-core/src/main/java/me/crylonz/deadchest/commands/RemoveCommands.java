package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.Permission;
import me.crylonz.deadchest.db.InMemoryChestStore;
import me.crylonz.deadchest.utils.ConfigKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;

import static me.crylonz.deadchest.DeadChestLoader.*;
import static me.crylonz.deadchest.DeadChestManager.cleanAllDeadChests;
import static me.crylonz.deadchest.DeadChestManager.removeDeadChest;

final class RemoveCommands extends DCSubCommandRegistration {

    RemoveCommands(DCCommandRegistrationService service) {
        super(service);
    }

    void registerRemoveInfinite() {
        registerCommand("dc removeinfinite", Permission.ADMIN.label, () -> {
            int count = 0;
            final InMemoryChestStore inMemoryChestStore = DeadChestLoader.getChestDataCache();
            final Map<Location, ChestData> chestDataMap = inMemoryChestStore.getAllChestData();

            if (chestDataMap != null && !chestDataMap.isEmpty()) {
                for (final ChestData chestData : chestDataMap.values()) {
                    if (chestData.getChestLocation().getWorld() != null &&
                            (chestData.isInfinity() || config.getInt(ConfigKey.DEADCHEST_DURATION) == 0)) {
                        getSchedulerAdapter().executeAtLocation(chestData.getChestLocation(), () -> removeDeadChest(chestData));
                        count++;
                    }
                }
            }

            sender().sendMessage(local.prefixed("commands.operation.deadchests-removed", count));
        });
    }

    void registerRemoveAll() {
        registerCommand("dc removeall", Permission.ADMIN.label, () -> {
            int count = cleanAllDeadChests();
            sender().sendMessage(local.prefixed("commands.operation.deadchests-removed", count));
        });
    }

    void registerRemoveOwn() {
        registerCommand("dc remove", Permission.REMOVE_OWN.label, () -> {
            if (player() != null) {
                removeAllDeadChestOfPlayer(player().getName());
            } else {
                sender().sendMessage(local.prefixed("commands.error.player-only"));
            }
        });
    }

    void registerRemoveOther() {
        registerCommand("dc remove {0}", Permission.REMOVE_OTHER.label, () -> removeAllDeadChestOfPlayer(args()[1]));
    }

    private void removeAllDeadChestOfPlayer(String playerName) {
        int count = 0;
        final InMemoryChestStore chestDataCache = getChestDataCache();
        final Map<Location, ChestData> chestDataList = chestDataCache.getAllChestData();
        final Player targetPlayer = Bukkit.getPlayer(playerName);

        if (chestDataList != null && !chestDataList.isEmpty()) {
            if (targetPlayer != null) {
                final Collection<Location> playerChestLocations = chestDataCache.getPlayerLinkedData(targetPlayer);
                if (playerChestLocations != null) {
                    for (Location chestLocation : playerChestLocations) {
                        ChestData chestData = chestDataCache.getChestData(chestLocation);
                        if (chestData == null) {
                            continue;
                        }

                        getSchedulerAdapter().executeAtLocation(chestData.getChestLocation(), () -> removeDeadChest(chestData));
                        count++;
                    }
                }
            } else {
                for (ChestData chestData : chestDataList.values()) {
                    if (chestData == null || !chestData.getPlayerName().equalsIgnoreCase(playerName)) {
                        continue;
                    }

                    getSchedulerAdapter().executeAtLocation(chestData.getChestLocation(), () -> removeDeadChest(chestData));
                    count++;
                }
            }
        }

        sender().sendMessage(local.prefixed("commands.operation.deadchests-removed-player", count, playerName));
    }
}
