package me.crylonz.deadchest;

import me.crylonz.deadchest.utils.ItemBytes;
import org.bukkit.*;
import java.util.*;

final class TransferSnapshot {
    static Properties encode(ChestData c, String kind, UUID recipient) {
        Properties p = new Properties();
        p.setProperty("id", UUID.randomUUID().toString());
        p.setProperty("kind", kind); p.setProperty("recipient", recipient.toString());
        if ("CREATE".equals(kind)) p.setProperty("material", c.getChestLocation().getBlock().getType().name());
        p.setProperty("chest", c.getId().toString()); p.setProperty("owner", c.getPlayerStringUUID());
        p.setProperty("name", c.getPlayerName()); p.setProperty("world", c.getWorldName());
        p.setProperty("date", Long.toString(c.getChestDate().getTime()));
        p.setProperty("infinite", Boolean.toString(c.isInfinity()));
        p.setProperty("removed", Boolean.toString(c.isRemovedBlock()));
        p.setProperty("xp", Integer.toString(c.getXpStored()));
        p.setProperty("items", Base64.getEncoder().encodeToString(ItemBytes.toBytesList(c.getInventory())));
        location(p, "chest.", c.getChestLocation()); location(p, "holo.", c.getHolographicTimer());
        uuid(p, "timer", c.getHolographicTimerId()); uuid(p, "status", c.getHolographicStatusId());
        uuid(p, "ownerHolo", c.getHolographicOwnerId()); uuid(p, "killer", c.getKillerUUID());
        return p;
    }
    private static void location(Properties p, String prefix, Location l) {
        p.setProperty(prefix+"x", Double.toString(l.getX())); p.setProperty(prefix+"y", Double.toString(l.getY()));
        p.setProperty(prefix+"z", Double.toString(l.getZ())); p.setProperty(prefix+"yaw", Float.toString(l.getYaw()));
        p.setProperty(prefix+"pitch", Float.toString(l.getPitch()));
    }
    private static void uuid(Properties p, String key, UUID id) { if (id != null) p.setProperty(key, id.toString()); }
    private static UUID uuid(Properties p, String key) { String s = p.getProperty(key); return s == null ? null : UUID.fromString(s); }
    private static Location location(Properties p, String prefix, World world) {
        return new Location(world, Double.parseDouble(p.getProperty(prefix+"x")), Double.parseDouble(p.getProperty(prefix+"y")),
                Double.parseDouble(p.getProperty(prefix+"z")), Float.parseFloat(p.getProperty(prefix+"yaw")), Float.parseFloat(p.getProperty(prefix+"pitch")));
    }
    static ChestData decode(Properties p) {
        World world = Bukkit.getWorld(p.getProperty("world"));
        if (world == null) throw new IllegalStateException("Transfer world is not loaded: " + p.getProperty("world"));
        ChestData c = new ChestData(ItemBytes.fromBytesList(Base64.getDecoder().decode(p.getProperty("items"))),
                location(p,"chest.",world),p.getProperty("name"),uuid(p,"owner"),new Date(Long.parseLong(p.getProperty("date"))),
                Boolean.parseBoolean(p.getProperty("infinite")),Boolean.parseBoolean(p.getProperty("removed")),
                location(p,"holo.",world),uuid(p,"timer"),uuid(p,"status"),uuid(p,"ownerHolo"),uuid(p,"killer"),
                p.getProperty("world"),Integer.parseInt(p.getProperty("xp")));
        c.loadPersistentId(uuid(p,"chest")); return c;
    }
    private TransferSnapshot() {}
}
