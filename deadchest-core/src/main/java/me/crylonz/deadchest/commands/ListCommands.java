package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.Permission;
import me.crylonz.deadchest.utils.ConfigKey;

import java.util.Date;
import java.util.Map;

import static me.crylonz.deadchest.DeadChestLoader.*;

final class ListCommands extends DCSubCommandRegistration {

    ListCommands(DCCommandRegistrationService service) {
        super(service);
    }

    void registerListOwn() {
        registerCommand("dc list", null, () -> {
            if (player() == null) {
                sender().sendMessage(local.prefixed("commands.error.player-only"));
                return;
            }

            if (player().hasPermission(Permission.LIST_OWN.label) || !config.getBoolean(ConfigKey.REQUIRE_PERMISSION_TO_LIST_OWN)) {
                Date now = new Date();
                final Map<org.bukkit.Location, ChestData> chestDataList = getChestDataCache().getAllChestData();
                if (!chestDataList.isEmpty()) {
                    sender().sendMessage(local.prefixed("commands.list.title.own"));
                    for (ChestData data : chestDataList.values()) {
                        if (data.getPlayerUUID().equals(player().getUniqueId())) {
                            displayChestData(now, data);
                        }
                    }
                } else {
                    player().sendMessage(local.prefixed("commands.list.none.player"));
                }
            }
        });
    }

    void registerListOther() {
        registerCommand("dc list {0}", Permission.LIST_OTHER.label, () -> {
            Date now = new Date();
            final Map<org.bukkit.Location, ChestData> chestDataList = getChestDataCache().getAllChestData();

            if (args()[1].equalsIgnoreCase("all")) {
                if (!chestDataList.isEmpty()) {
                    sender().sendMessage(local.prefixed("commands.list.title.all"));
                    for (ChestData data : chestDataList.values()) {
                        displayChestData(now, data);
                    }
                } else {
                    sender().sendMessage(local.prefixed("commands.list.none.global"));
                }
                return;
            }

            if (!chestDataList.isEmpty()) {
                sender().sendMessage(local.prefixed("commands.list.title.player", args()[1]));
                for (ChestData data : chestDataList.values()) {
                    if (data.getPlayerName().equalsIgnoreCase(args()[1])) {
                        displayChestData(now, data);
                    }
                }
            } else {
                sender().sendMessage(local.prefixed("commands.list.none.global"));
            }
        });
    }

    private void displayChestData(Date now, ChestData chestData) {
        String worldName = chestData.getChestLocation().getWorld() != null ?
                chestData.getChestLocation().getWorld().getName() : local.get("common.unknown-world");

        if (chestData.isInfinity() || config.getInt(ConfigKey.DEADCHEST_DURATION) == 0) {
            sender().sendMessage(local.format(
                    "commands.list.entry.infinity",
                    worldName,
                    chestData.getChestLocation().getX(),
                    chestData.getChestLocation().getY(),
                    chestData.getChestLocation().getZ(),
                    local.get("chest.time-left")
            ));
            return;
        }

        long diff = now.getTime() - (chestData.getChestDate().getTime() + config.getInt(ConfigKey.DEADCHEST_DURATION) * 1000L);
        long diffSeconds = Math.abs(diff / 1000 % 60);
        long diffMinutes = Math.abs(diff / (60 * 1000) % 60);
        long diffHours = Math.abs(diff / (60 * 60 * 1000));

        sender().sendMessage(local.format(
                "commands.list.entry.timed",
                chestData.getChestLocation().getX(),
                chestData.getChestLocation().getY(),
                chestData.getChestLocation().getZ(),
                diffHours,
                diffMinutes,
                diffSeconds,
                local.get("chest.time-left")
        ));
    }
}
