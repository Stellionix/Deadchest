package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.DeadChestLoader;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static me.crylonz.deadchest.DeadChestLoader.local;

abstract class DCCommandRegistration {
    protected final DeadChestLoader plugin;

    // CommandSender params
    protected CommandSender sender = null;
    protected String[] args = null;

    protected Player player = null;
    protected boolean commandSucceed = false;
    private boolean badArgumentsMatched = false;
    private String badArgumentsCommandName = null;

    public DCCommandRegistration(DeadChestLoader plugin) {
        this.plugin = plugin;
    }

    /**
     * Need to be call at then beginning of onCommand to set up the context
     *
     * @param sender sender from onCommand
     * @param args   args from onCommand
     */
    public void register(CommandSender sender, String[] args) {
        this.sender = sender;
        this.args = args;
        this.commandSucceed = false;
        this.badArgumentsMatched = false;
        this.badArgumentsCommandName = null;

        player = sender instanceof Player ? (Player) sender : null;
    }

    /**
     * commandSucceed will be true if any command is call successfully
     *
     * @param succeed command call success status
     */
    protected void setCommandSucceed(boolean succeed) {
        this.commandSucceed = this.commandSucceed || succeed;
    }

    public boolean isCommandSucceed() {
        return commandSucceed;
    }

    /**
     * Check if the given command from onCommand is matching the command given in parameters and run the lambda if
     * matching.
     * <p>
     * the command must have syntax /<pluginPrefix> <commandName> [params...]
     * 0 to 5 params is possible
     *
     * @param command         command with params {x}
     * @param permission      permission needed to do the command (can be null)
     * @param commandRunnable function to call to apply the command
     * @return true if the command succeed else false
     */
    protected boolean checkCommand(String command,
                                   String permission,
                                   Runnable commandRunnable) {
        String[] commandParts = command.split(" ");
        if (args.length == 0 || !args[0].equalsIgnoreCase(commandParts[1])) {
            return false;
        }

        int expectedArgCount = commandParts.length - 1;
        int providedComparableArgs = Math.min(args.length, expectedArgCount);

        for (int i = 0; i < providedComparableArgs; ++i) {
            String expectedPart = commandParts[i + 1];
            if (isPlaceholder(expectedPart)) {
                continue;
            }
            if (!args[i].equalsIgnoreCase(expectedPart)) {
                return false;
            }
        }

        if (args.length < expectedArgCount) {
            if (!badArgumentsMatched) {
                badArgumentsMatched = true;
                badArgumentsCommandName = args[0];
            }
            return false;
        }

        if (args.length != expectedArgCount) {
            return false;
        }

        if (player != null && permission != null && !player.hasPermission(permission)) {
            return false;
        }

        commandRunnable.run();
        return true;
    }

    private boolean isPlaceholder(String token) {
        return token.startsWith("{") && token.endsWith("}");
    }

    public void registerCommand(String command, String permission, Runnable commandRunnable) {
        if (commandSucceed) {
            return;
        }
        setCommandSucceed(checkCommand(command, permission, commandRunnable));
    }

    public boolean hasBadArguments() {
        return badArgumentsMatched;
    }

    public void sendBadArgumentsFeedback() {
        sender.sendMessage(local.prefixed("commands.error.bad-args", badArgumentsCommandName));
    }
}
