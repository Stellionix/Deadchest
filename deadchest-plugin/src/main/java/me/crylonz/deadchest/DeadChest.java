package me.crylonz.deadchest;

import me.crylonz.deadchest.listener.*;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.DeadChestUpdater;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;

import static me.crylonz.deadchest.DeadChestLoader.bstats;
import static me.crylonz.deadchest.utils.DeadChestUpdater.UpdateType.DEFAULT;


public class DeadChest extends JavaPlugin {

    private DeadChestLoader instance;

    public DeadChest() {
        super();
        instance = new DeadChestLoader(this, this);
    }

    protected DeadChest(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file);
        instance = new DeadChestLoader(this, this);
    }

    public void onEnable() {
        instance.enable();
        if (DeadChestLoader.config.getBoolean(ConfigKey.AUTO_UPDATE)) {
            DeadChestUpdater updater = new DeadChestUpdater(this, 322882, this.getFile(), DEFAULT, true);
        }

        if (bstats) {
            Metrics metrics = new Metrics(this, 11385);
        }

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new ArmorstandListener(), this);
        pm.registerEvents(new BlockBreakListener(), this);
        pm.registerEvents(new BlockFromToListener(), this);
        pm.registerEvents(new BlockPlaceEventListener(), this);
        pm.registerEvents(new ClickListener(), this);
        pm.registerEvents(new ExplosionListener(), this);
        pm.registerEvents(new InventoryClickListener(), this);
        pm.registerEvents(new PistonListener(), this);
        pm.registerEvents(new PlayerDeathListener(), this);
        pm.registerEvents(new PendingGivebackJoinListener(), this);

    }

    @Override
    public void onLoad() {
        instance.load();
    }

    public void onDisable() {
        instance.disable();
    }
}
