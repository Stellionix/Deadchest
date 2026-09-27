package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.Permission;

import static me.crylonz.deadchest.DeadChestLoader.local;

final class ReloadCommands extends DCSubCommandRegistration {

    ReloadCommands(DCCommandRegistrationService service) {
        super(service);
    }

    void registerReload() {
        registerCommand("dc reload", Permission.ADMIN.label, () -> {
            service.reloadPluginConfiguration();
            sender().sendMessage(local.prefixed("commands.reload.success"));
        });
    }
}
