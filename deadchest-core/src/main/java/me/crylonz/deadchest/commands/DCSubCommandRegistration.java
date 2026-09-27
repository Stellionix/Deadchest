package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.DeadChestLoader;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

abstract class DCSubCommandRegistration {
    protected final DCCommandRegistrationService service;

    protected DCSubCommandRegistration(DCCommandRegistrationService service) {
        this.service = service;
    }

    protected DeadChestLoader plugin() {
        return service.plugin;
    }

    protected CommandSender sender() {
        return service.sender;
    }

    protected String[] args() {
        return service.args;
    }

    protected Player player() {
        return service.player;
    }

    protected void registerCommand(String command, String permission, Runnable commandRunnable) {
        service.registerCommand(command, permission, commandRunnable);
    }
}
