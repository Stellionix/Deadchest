package me.crylonz.deadchest;

import me.crylonz.deadchest.utils.ItemBytes;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Write-ahead recovery snapshots shared by the free and Pro editions.
 * An interrupted operation is never replayed automatically: Minecraft player data
 * and the chest database cannot participate in the same transaction.
 */
public final class RecoveryJournal {
    private static volatile RecoveryJournal current;
    private final Path directory;
    private final Set<UUID> review = new HashSet<>();

    public RecoveryJournal(Path directory) {
        this.directory = directory;
        if (Files.exists(directory)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.properties")) {
                for (Path file : files) {
                    UUID id = UUID.fromString(file.getFileName().toString().replace(".properties", ""));
                    review.add(id);
                    DeadChestLoader.log.severe("DeadChest requires persistence REVIEW: " + id + " (" + file + ")");
                }
            } catch (IOException | IllegalArgumentException e) {
                throw new IllegalStateException("Cannot read DeadChest recovery journal", e);
            }
        }
    }

    public static synchronized RecoveryJournal active() {
        Path path = DeadChestLoader.plugin.getDataFolder().toPath().resolve("recovery-journal").toAbsolutePath();
        if (current == null || !current.directory.equals(path)) current = new RecoveryJournal(path);
        return current;
    }
    public static synchronized void initialize() { current = null; active(); }
    public static boolean blocked(UUID id) { return current != null && current.requiresReview(id); }
    public boolean pending(UUID id) { return Files.exists(directory.resolve(id + ".properties")); }

    public synchronized boolean requiresReview(UUID id) { return review.contains(id); }

    public void begin(ChestData chest, String operation, UUID recipient) {
        Properties data = new Properties();
        data.setProperty("format", "1");
        data.setProperty("operation", operation);
        data.setProperty("chest", chest.getId().toString());
        data.setProperty("owner", chest.getPlayerUUID().toString());
        data.setProperty("recipient", recipient.toString());
        data.setProperty("world", chest.getWorldName());
        data.setProperty("x", String.valueOf(chest.getChestLocation().getBlockX()));
        data.setProperty("y", String.valueOf(chest.getChestLocation().getBlockY()));
        data.setProperty("z", String.valueOf(chest.getChestLocation().getBlockZ()));
        data.setProperty("xp", String.valueOf(chest.getXpStored()));
        data.setProperty("items", Base64.getEncoder().encodeToString(ItemBytes.toBytesList(chest.getInventory())));
        write(chest.getId(), data);
    }

    // Package visible for filesystem failure/restart tests without a Minecraft runtime.
    synchronized void write(UUID id, Properties data) {
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(id + ".properties");
            if (Files.exists(target)) throw new IOException("Unresolved operation already exists: " + id);
            temporary = Files.createTempFile(directory, id.toString(), ".tmp");
            try (FileOutputStream out = new FileOutputStream(temporary.toFile())) {
                data.store(out, "Unfinished DeadChest operation; compare player data before manual recovery");
                out.getFD().sync();
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target); }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot persist DeadChest recovery intent " + id, e);
        }
    }

    public synchronized void complete(UUID id) {
        if (review.contains(id)) throw new IllegalStateException("Manual review required: " + id);
        try { Files.delete(directory.resolve(id + ".properties")); }
        catch (IOException e) { throw new IllegalStateException("Cannot acknowledge DeadChest operation " + id, e); }
    }
}
