package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.ChestData;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.PendingGivebackRepository;
import me.crylonz.deadchest.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;

import static me.crylonz.deadchest.DeadChestLoader.local;

final class GivebackCommands extends DCSubCommandRegistration {

    GivebackCommands(DCCommandRegistrationService service) {
        super(service);
    }

    void registerGiveBack() {
        registerCommand("dc giveback preview list {2}", Permission.GIVEBACK.label, () -> previewGiveBackList(args()[3]));
        registerCommand("dc giveback preview {1}", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], "latest", null));
        registerCommand("dc giveback preview {1} latest", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], "latest", null));
        registerCommand("dc giveback preview {1} latest {3}", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], "latest", args()[4]));
        registerCommand("dc giveback preview {1} oldest", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], "oldest", null));
        registerCommand("dc giveback preview {1} oldest {3}", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], "oldest", args()[4]));
        registerCommand("dc giveback preview {1} all", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], "all", null));
        registerCommand("dc giveback preview {1} all {3}", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], "all", args()[4]));
        registerCommand("dc giveback preview {1} id {3}", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], "id", args()[4]));
        registerCommand("dc giveback preview {1} id {3} {4}", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], "id", args()[4] + " " + args()[5]));
        registerCommand("dc giveback preview {1} {2}", Permission.GIVEBACK.label, () -> previewGiveBackSelection(args()[2], args()[3], null));
        registerCommand("dc giveback list {1}", Permission.GIVEBACK.label, this::listGiveBackTargets);
        registerCommand("dc giveback {0} latest", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], "latest", null));
        registerCommand("dc giveback {0} latest {2}", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], "latest", args()[3]));
        registerCommand("dc giveback {0} oldest", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], "oldest", null));
        registerCommand("dc giveback {0} oldest {2}", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], "oldest", args()[3]));
        registerCommand("dc giveback {0} all", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], "all", null));
        registerCommand("dc giveback {0} all {2}", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], "all", args()[3]));
        registerCommand("dc giveback {0} id {2}", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], "id", args()[3]));
        registerCommand("dc giveback {0} id {2} {3}", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], "id", args()[3] + " " + args()[4]));
        registerCommand("dc giveback {0} {1}", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], args()[2], null));
        registerCommand("dc giveback {0}", Permission.GIVEBACK.label, () -> giveBackSelection(args()[1], "latest", null));
    }

    private void listGiveBackTargets() {
        sendGiveBackList(args()[2], false);
    }

    private void previewGiveBackList(String playerName) {
        sendGiveBackList(playerName, true);
    }

    private void sendGiveBackList(String playerName, boolean preview) {
        List<ChestData> chests = getSortedChestsForPlayer(playerName);
        if (chests.isEmpty()) {
            sender().sendMessage(local.prefixed("commands.giveback.none", playerName));
            return;
        }

        String titleKey = preview ? "commands.giveback.preview.list.title" : "commands.giveback.list.title";
        sender().sendMessage(local.prefixed(titleKey, playerName, chests.size()));
        for (int i = 0; i < chests.size(); i++) {
            ChestData chestData = chests.get(i);
            sender().sendMessage(local.prefixed(
                    "commands.giveback.list.entry",
                    i + 1,
                    formatChestDate(chestData),
                    getWorldName(chestData),
                    chestData.getChestLocation().getBlockX(),
                    chestData.getChestLocation().getBlockY(),
                    chestData.getChestLocation().getBlockZ(),
                    countStoredItems(chestData),
                    chestData.getXpStored()
            ));
        }
    }

    private void giveBackSelection(String playerName, String selector, String selectorArg) {
        List<ChestData> chests = getSortedChestsForPlayer(playerName);
        if (chests.isEmpty()) {
            sender().sendMessage(local.prefixed("commands.giveback.none", playerName));
            return;
        }

        GivebackStrategy strategy = parseGiveBackStrategy(selector, selectorArg);
        if (strategy == null) {
            return;
        }

        Player targetPlayer = resolveOnlinePlayer(chests.get(0));
        List<ChestData> selected = selectGiveBackTargets(chests, selector, selectorArg);
        if (selected == null) {
            return;
        }

        if (targetPlayer == null) {
            int queued = queueForOfflineDelivery(playerName, selected, strategy);
            if (queued == 0) {
                sender().sendMessage(local.prefixed("commands.giveback.target-not-found"));
                return;
            }
            sender().sendMessage(local.prefixed("commands.giveback.queued.sender", queued, playerName, strategy.token()));
            return;
        }

        int restored = 0;
        for (ChestData chestData : selected) {
            if (restoreChestToPlayer(targetPlayer, chestData, strategy)) {
                restored++;
            }
        }

        if (restored == 0) {
            sender().sendMessage(local.prefixed("commands.giveback.target-not-found"));
            return;
        }

        sender().sendMessage(local.prefixed("commands.giveback.success.sender.detailed", restored, playerName, strategy.token()));
        targetPlayer.sendMessage(local.prefixed("commands.giveback.success.target.detailed", restored, strategy.token()));
    }

    private void previewGiveBackSelection(String playerName, String selector, String selectorArg) {
        List<ChestData> chests = getSortedChestsForPlayer(playerName);
        if (chests.isEmpty()) {
            sender().sendMessage(local.prefixed("commands.giveback.none", playerName));
            return;
        }

        GivebackStrategy strategy = parseGiveBackStrategy(selector, selectorArg);
        if (strategy == null) {
            return;
        }

        List<ChestData> selected = selectGiveBackTargets(chests, selector, selectorArg);
        if (selected == null) {
            return;
        }

        boolean online = resolveOnlinePlayer(chests.get(0)) != null;
        int totalItems = 0;
        int totalXp = 0;
        for (ChestData chestData : selected) {
            totalItems += countStoredItems(chestData);
            totalXp += chestData.getXpStored();
        }

        sender().sendMessage(local.prefixed(
                "commands.giveback.preview.summary",
                playerName,
                selectorLabel(selector),
                strategy.token(),
                online ? "online" : "offline",
                online ? "instant restore" : "queued for next login",
                selected.size(),
                totalItems,
                totalXp
        ));

        for (ChestData chestData : selected) {
            int runtimeId = chests.indexOf(chestData) + 1;
            sender().sendMessage(local.prefixed(
                    "commands.giveback.preview.entry",
                    runtimeId,
                    formatChestDate(chestData),
                    getWorldName(chestData),
                    chestData.getChestLocation().getBlockX(),
                    chestData.getChestLocation().getBlockY(),
                    chestData.getChestLocation().getBlockZ(),
                    countStoredItems(chestData),
                    chestData.getXpStored()
            ));
        }
    }

    private List<ChestData> selectGiveBackTargets(List<ChestData> chests, String selector, String selectorArg) {
        if (GivebackStrategy.fromToken(selector) != null) {
            return java.util.Collections.singletonList(chests.get(chests.size() - 1));
        }

        if ("all".equalsIgnoreCase(selector)) {
            return new ArrayList<>(chests);
        }

        if ("oldest".equalsIgnoreCase(selector)) {
            return java.util.Collections.singletonList(chests.get(0));
        }

        if ("latest".equalsIgnoreCase(selector)) {
            return java.util.Collections.singletonList(chests.get(chests.size() - 1));
        }

        if ("id".equalsIgnoreCase(selector)) {
            int index;
            try {
                index = Integer.parseInt(extractIdToken(selectorArg));
            } catch (NumberFormatException ex) {
                sender().sendMessage(local.prefixed("commands.giveback.invalid-id", selectorArg));
                return null;
            }

            if (index < 1 || index > chests.size()) {
                sender().sendMessage(local.prefixed("commands.giveback.invalid-id", selectorArg));
                return null;
            }
            return java.util.Collections.singletonList(chests.get(index - 1));
        }

        sender().sendMessage(local.prefixed("commands.error.unknown"));
        return null;
    }

    private GivebackStrategy parseGiveBackStrategy(String selector, String selectorArg) {
        GivebackStrategy selectorStrategy = GivebackStrategy.fromToken(selector);
        if (selectorStrategy != null) {
            return selectorStrategy;
        }

        if (selectorArg == null || selectorArg.trim().isEmpty()) {
            return GivebackStrategy.defaultStrategy();
        }

        String[] parts = selectorArg.trim().split("\\s+");
        String token = parts.length == 1 ? parts[0] : parts[parts.length - 1];
        GivebackStrategy parsed = GivebackStrategy.fromToken(token);
        if (parsed != null) {
            return parsed;
        }

        if ("id".equalsIgnoreCase(selector) && parts.length == 1) {
            return GivebackStrategy.defaultStrategy();
        }

        sender().sendMessage(local.prefixed("commands.giveback.invalid-strategy", token));
        return null;
    }

    private String extractIdToken(String selectorArg) {
        if (selectorArg == null) {
            return "";
        }
        String[] parts = selectorArg.trim().split("\\s+");
        return parts.length == 0 ? "" : parts[0];
    }

    private List<ChestData> getSortedChestsForPlayer(String playerName) {
        List<ChestData> matches = new ArrayList<>();
        for (ChestData chestData : DeadChestLoader.getChestDataCache().getAllChestData().values()) {
            if (chestData != null && chestData.getPlayerName().equalsIgnoreCase(playerName)) {
                matches.add(chestData);
            }
        }

        matches.sort(Comparator
                .comparing(ChestData::getChestDate, Comparator.nullsLast(Date::compareTo))
                .thenComparing(this::getWorldName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(chest -> chest.getChestLocation().getBlockX())
                .thenComparingInt(chest -> chest.getChestLocation().getBlockY())
                .thenComparingInt(chest -> chest.getChestLocation().getBlockZ()));
        return matches;
    }

    private Player resolveOnlinePlayer(ChestData chestData) {
        if (chestData == null) {
            return null;
        }

        UUID playerUuid = chestData.getPlayerUUID();
        if (playerUuid != null) {
            Player target = Bukkit.getPlayer(playerUuid);
            if (target != null) {
                return target;
            }
        }

        return Bukkit.getPlayerExact(chestData.getPlayerName());
    }

    private boolean restoreChestToPlayer(Player targetPlayer, ChestData chestData, GivebackStrategy strategy) {
        if (targetPlayer == null || chestData == null || !targetPlayer.isOnline()) {
            return false;
        }

        DeadChestLoader.getSchedulerAdapter().executeForEntity(targetPlayer, () -> GivebackDelivery.deliver(targetPlayer, chestData, strategy));
        removeGivebackChest(chestData);
        return true;
    }

    private int queueForOfflineDelivery(String playerName, List<ChestData> selected, GivebackStrategy strategy) {
        int queued = 0;
        for (ChestData chestData : selected) {
            if (chestData.getPlayerUUID() == null) {
                continue;
            }
            if (!PendingGivebackRepository.queueChest(chestData.getPlayerUUID(), playerName, chestData, strategy)) {
                continue;
            }
            removeGivebackChest(chestData);
            queued++;
        }
        return queued;
    }

    private void removeGivebackChest(ChestData chestData) {
        if (chestData == null) {
            return;
        }

        World world = chestData.getWorldName() != null ? Bukkit.getWorld(chestData.getWorldName()) : null;
        if (world != null) {
            world.getBlockAt(chestData.getChestLocation()).setType(Material.AIR);
        }
        DeadChestLoader.getChestDataCache().removeChestData(chestData);
    }

    private String getWorldName(ChestData chestData) {
        try {
            if (chestData.getChestLocation().getWorld() != null) {
                return chestData.getChestLocation().getWorld().getName();
            }
        } catch (IllegalArgumentException ignored) {
        }
        return chestData.getWorldName() != null ? chestData.getWorldName() : local.get("common.unknown-world");
    }

    private String formatChestDate(ChestData chestData) {
        Date date = chestData.getChestDate();
        if (date == null) {
            return "?";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(date);
    }

    private int countStoredItems(ChestData chestData) {
        int count = 0;
        for (ItemStack itemStack : chestData.getInventory()) {
            if (itemStack != null) {
                count += itemStack.getAmount();
            }
        }
        return count;
    }

    private String selectorLabel(String selector) {
        if (GivebackStrategy.fromToken(selector) != null) {
            return "latest";
        }
        return selector == null || selector.trim().isEmpty() ? "latest" : selector.toLowerCase(Locale.ROOT);
    }
}
