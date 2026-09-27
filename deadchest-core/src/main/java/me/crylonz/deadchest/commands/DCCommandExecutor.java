package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.DeadChestLoader;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import static me.crylonz.deadchest.DeadChestLoader.local;

public class DCCommandExecutor implements CommandExecutor {

    private final DeadChestLoader plugin;
    private final DCCommandRegistrationService commandRegistration;

    public DCCommandExecutor(DeadChestLoader deadChestLoader) {
        this.plugin = deadChestLoader;
        this.commandRegistration = new DCCommandRegistrationService(plugin);
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        commandRegistration.register(sender, args);

        commandRegistration.registerReload();           // dc reload
        commandRegistration.registerRepairForce();      // dc repair force
        commandRegistration.registerRepair();           // dc repair
        commandRegistration.registerRemoveInfinite();   // dc removeinfinate
        commandRegistration.registerRemoveAll();        // dc removeall
        commandRegistration.registerRemoveOwn();        // dc remove
        commandRegistration.registerRemoveOther();      // dc remove <PlayerName>
        commandRegistration.registerListOwn();          // dc list
        commandRegistration.registerListOther();        // dc list all | <PlayerName>
        commandRegistration.registerGiveBack();         // dc giveback <PlayerName>
        commandRegistration.registerConfigOverview();   // dc config
        commandRegistration.registerConfigGet();        // dc config get <path>
        commandRegistration.registerConfigSet();        // dc config set <path> <value>
        commandRegistration.registerConfigReset();      // dc config reset <path>
        commandRegistration.registerConfigEdit();       // dc config edit <path>
        commandRegistration.registerIgnoreList();       // dc ignore

        if (commandRegistration.isCommandSucceed()) {
            return true;
        }

        if (commandRegistration.hasBadArguments()) {
            commandRegistration.sendBadArgumentsFeedback();
            return true;
        }

        sender.sendMessage(local.prefixed("commands.error.unknown"));
        return false;
    }
}
