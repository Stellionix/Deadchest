package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.Permission;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.PermissionUtils;
import org.bukkit.Bukkit;

import static me.crylonz.deadchest.DeadChestLoader.*;

public class DCCommandRegistrationService extends DCCommandRegistration {

    private final ReloadCommands reloadCommands;
    private final RepairCommands repairCommands;
    private final RemoveCommands removeCommands;
    private final ListCommands listCommands;
    private final GivebackCommands givebackCommands;
    private final ConfigCommands configCommands;
    private final IgnoreCommands ignoreCommands;

    public DCCommandRegistrationService(DeadChestLoader plugin) {
        super(plugin);
        this.reloadCommands = new ReloadCommands(this);
        this.repairCommands = new RepairCommands(this);
        this.removeCommands = new RemoveCommands(this);
        this.listCommands = new ListCommands(this);
        this.givebackCommands = new GivebackCommands(this);
        this.configCommands = new ConfigCommands(this);
        this.ignoreCommands = new IgnoreCommands(this);
    }

    public void registerReload() {
        reloadCommands.registerReload();
    }

    public void registerRepairForce() {
        repairCommands.registerRepairForce();
    }

    public void registerRepair() {
        repairCommands.registerRepair();
    }

    public void registerRemoveInfinite() {
        removeCommands.registerRemoveInfinite();
    }

    public void registerRemoveAll() {
        removeCommands.registerRemoveAll();
    }

    public void registerRemoveOwn() {
        removeCommands.registerRemoveOwn();
    }

    public void registerRemoveOther() {
        removeCommands.registerRemoveOther();
    }

    public void registerListOwn() {
        listCommands.registerListOwn();
    }

    public void registerListOther() {
        listCommands.registerListOther();
    }

    public void registerGiveBack() {
        givebackCommands.registerGiveBack();
    }

    public void registerConfigOverview() {
        configCommands.registerConfigOverview();
    }

    public void registerConfigGet() {
        configCommands.registerConfigGet();
    }

    public void registerConfigSet() {
        configCommands.registerConfigSet();
    }

    public void registerConfigReset() {
        configCommands.registerConfigReset();
    }

    public void registerConfigEdit() {
        configCommands.registerConfigEdit();
    }

    public void registerIgnoreList() {
        ignoreCommands.registerIgnoreList();
    }

    ConfigKey requireConfigKey(String rawPath) {
        ConfigKey key = DCConfigCommandSupport.resolveKey(rawPath);
        if (key == null) {
            sender.sendMessage(local.prefixed("commands.config.error.unknown-key", rawPath));
        }
        return key;
    }

    boolean ensureConfigPermission() {
        if (player == null) {
            return true;
        }
        if (PermissionUtils.hasAdminOr(player, Permission.CONFIG)) {
            return true;
        }
        sender.sendMessage(local.prefixed("commands.error.no-permission"));
        return false;
    }

    void reloadPluginConfiguration() {
        DeadChestLoader.plugin.reloadConfig();
        plugin.registerConfig();
        local.reloadLanguage(config.getString(ConfigKey.LOCALIZATION_LANGUAGE));
        ignoreList = Bukkit.createInventory(new me.crylonz.deadchest.IgnoreInventoryHolder(), 36, local.get("gui.ignore-list.title"));
        loadIgnoreIntoInventoryFromConfig(ignoreList);
    }

    void openIgnoredItemsEditor() {
        if (player == null) {
            sender.sendMessage(local.prefixed("commands.error.player-only"));
            return;
        }

        DeadChestLoader.getSchedulerAdapter().executeForEntity(player, () -> player.openInventory(ignoreList));
    }
}
