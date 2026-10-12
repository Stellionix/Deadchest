package me.crylonz.deadchest.utils;

import me.crylonz.deadchest.DeadChestLoader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import static me.crylonz.deadchest.DeadChestLoader.*;
import static me.crylonz.deadchest.DeadChestManager.generateHologram;

public class Utils {
    public static final String HOLOGRAM_LINE_KEY = "deadchest-line";


    public static void generateLog(String message) {
        File log = new File(DeadChestLoader.plugin.getDataFolder(), "deadchest.log");
        try {
            Files.createDirectories(log.toPath().getParent());
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date(System.currentTimeMillis());
            String finalMsg = "[" + formatter.format(date) + "] " + message + "\n";
            Files.write(log.toPath(), finalMsg.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            DeadChestLoader.log.warning("Can't write log for Deadchest : " + e);
        }
    }

    public static Location getFreeBlockAroundThisPlace(World world, Location loc) {
        Location newLoc = loc.clone();
        if (world.getBlockAt(loc).isEmpty()) {
            return newLoc;
        }

        if (world.getBlockAt(newLoc.clone().add(1, 0, 0)).isEmpty()) {
            return newLoc.add(1, 0, 0);
        }

        if (world.getBlockAt(newLoc.clone().add(-1, 0, 0)).isEmpty()) {
            return newLoc.add(-1, 0, 0);
        }

        if (world.getBlockAt(newLoc.clone().add(0, 1, 0)).isEmpty()) {
            return newLoc.add(0, 1, 0);
        }

        if (world.getBlockAt(newLoc.clone().add(0, -1, 0)).isEmpty()) {
            return newLoc.add(0, -1, 0);
        }

        return null;

    }


    public static boolean isBefore1_18() {
        return Bukkit.getVersion().contains("1.17") || isBefore1_17();
    }

    public static boolean isBefore1_17() {
        return Bukkit.getVersion().contains("1.16") || isBefore1_16();
    }

    public static boolean isBefore1_16() {
        return (Bukkit.getVersion().contains("1.15")
                || Bukkit.getVersion().contains("1.14")
                || Bukkit.getVersion().contains("1.13")
                || Bukkit.getVersion().contains("1.12"));
    }

    public static boolean isGraveBlock(Material material) {
        return graveBlocks.contains(material);
    }

    public static int computeMinHeight() {
        if (isBefore1_18()) {
            return 1;
        } else {
            return -64;
        }
    }

    public static boolean checkTheEndGeneration(Entity player) {
        return player.getWorld().getEnvironment().equals(World.Environment.THE_END) &&
                !config.getBoolean(ConfigKey.GENERATE_IN_THE_END);
    }

    public static void generateDeadChest(Block block, Player player) {
        switch (config.getInt(ConfigKey.DROP_BLOCK)) {
            case 2:
                Material head = Material.getMaterial("PLAYER_HEAD");
                if (head == null)
                    head = Material.getMaterial("SKULL");
                if (head != null)
                    block.setType(head);
                else
                    block.setType(Material.CHEST);
                try {
                    block.setMetadata("deadchest", new FixedMetadataValue(plugin, true));
                } catch (NoClassDefFoundError ignore) {
                }
                BlockState state = block.getState();
                Skull skull = (Skull) state;
                if (player != null) {
                    skull.setOwningPlayer(player);
                }
                skull.update();
                break;
            case 3:
                Material barrel = Material.getMaterial("BARREL");
                block.setType(barrel == null ? Material.CHEST : barrel);
                break;
            case 4:
                block.setType(Material.SHULKER_BOX);
                break;
            case 5:
                block.setType(Material.ENDER_CHEST);
                break;
            default:
                block.setType(Material.CHEST);
        }
    }

    public static ArmorStand[] createHolograms(Block block, String deathDisplayName) {
        String statusLine = local.get("hologram.loading");
        ArmorStand holoStatus = generateHologram(block.getLocation(), statusLine, 0.5f, -0.85f, 0.5f, false);
        if (holoStatus != null) {
            holoStatus.setMetadata(HOLOGRAM_LINE_KEY, new FixedMetadataValue(plugin, "status"));
        }

        String firstLine = local.format("hologram.owner", deathDisplayName);
        ArmorStand holoName = generateHologram(block.getLocation(), firstLine, 0.5f, -1.05f, 0.5f, false);
        if (holoName != null) {
            holoName.setMetadata(HOLOGRAM_LINE_KEY, new FixedMetadataValue(plugin, "owner"));
        }

        String secondLine = local.get("hologram.loading");
        ArmorStand holoTime = generateHologram(block.getLocation(), secondLine, 0.5f, -1.25f, 0.5f, true);
        if (holoTime != null) {
            holoTime.setMetadata(HOLOGRAM_LINE_KEY, new FixedMetadataValue(plugin, "timer"));
        }

        return new ArmorStand[]{holoTime, holoStatus, holoName};
    }
}

