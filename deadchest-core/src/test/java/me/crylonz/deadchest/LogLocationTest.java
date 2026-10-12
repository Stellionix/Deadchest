package me.crylonz.deadchest;

import me.crylonz.deadchest.utils.Utils;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogLocationTest {
    @TempDir Path directory;
    private Plugin previousPlugin;
    private Logger previousLogger;

    @BeforeEach
    void saveGlobals() {
        previousPlugin = DeadChestLoader.plugin;
        previousLogger = DeadChestLoader.log;
        DeadChestLoader.log = mock(Logger.class);
    }

    @AfterEach
    void restoreGlobals() {
        DeadChestLoader.plugin = previousPlugin;
        DeadChestLoader.log = previousLogger;
    }

    @Test
    void proOnlyInstallationCreatesAndAppendsItsOwnLog() throws Exception {
        assertLog("Deadchest-Pro");
    }

    @Test
    void freeInstallationKeepsItsOwnLog() throws Exception {
        assertLog("DeadChest");
    }

    private void assertLog(String name) throws Exception {
        Path folder = directory.resolve(name);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        DeadChestLoader.plugin = plugin;
        Utils.generateLog("first émeraude");
        Utils.generateLog("second entry");
        Path log = folder.resolve("deadchest.log");
        assertTrue(Files.exists(log), "Log must use the active plugin data folder");
        var lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).endsWith("first émeraude"));
        assertTrue(lines.get(1).endsWith("second entry"));
        verify(DeadChestLoader.log, never()).warning(anyString());
    }
}
