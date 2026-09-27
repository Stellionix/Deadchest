package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.Permission;

import static me.crylonz.deadchest.DeadChestLoader.local;

final class IgnoreCommands extends DCSubCommandRegistration {

    IgnoreCommands(DCCommandRegistrationService service) {
        super(service);
    }

    void registerIgnoreList() {
        registerCommand("dc ignore", Permission.ADMIN.label, () -> {
            sender().sendMessage(local.prefixed("commands.config.deprecated.ignore"));
            service.openIgnoredItemsEditor();
        });
    }
}
