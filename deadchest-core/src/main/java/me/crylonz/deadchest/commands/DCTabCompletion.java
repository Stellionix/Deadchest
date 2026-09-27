package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.Permission;
import me.crylonz.deadchest.utils.ConfigKey;
import me.crylonz.deadchest.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class DCTabCompletion implements TabCompleter {

    private final List<String> list = new ArrayList<>();

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        list.clear();
        if (!cmd.getName().equalsIgnoreCase("dc")) {
            return list;
        }

        Player player = sender instanceof Player ? (Player) sender : null;
        boolean admin = sender.hasPermission(Permission.ADMIN.label);
        boolean configAccess = admin || (player != null && player.hasPermission(Permission.CONFIG.label));

        if (args.length == 1) {
            if (admin) {
                list.add("reload");
                list.add("removeinfinite");
                list.add("removeall");
                list.add("repair");
                list.add("ignore");
                list.add("config");
            }

            if (player != null && PermissionUtils.hasAdminOrOneOf(player, PermissionUtils.LIST_ALL)) {
                list.add("remove");
            }

            if (player != null && PermissionUtils.hasAdminOr(player, Permission.GIVEBACK)) {
                list.add("giveback");
            }

            if (player != null && PermissionUtils.hasAdminOrOneOf(player, PermissionUtils.REMOVE_ALL)) {
                list.add("list");
            }
            return filterSuggestions(args[0]);
        }

        if (args.length == 2) {
            if ("remove".equalsIgnoreCase(args[0]) && player != null && PermissionUtils.hasAdminOr(player, Permission.REMOVE_OTHER)) {
                addOnlinePlayers();
            }
            if ("repair".equalsIgnoreCase(args[0]) && admin) {
                list.add("force");
            }
            if ("list".equalsIgnoreCase(args[0]) && player != null && PermissionUtils.hasAdminOr(player, Permission.LIST_OTHER)) {
                list.add("all");
                addOnlinePlayers();
            }
            if ("giveback".equalsIgnoreCase(args[0]) && player != null && PermissionUtils.hasAdminOr(player, Permission.GIVEBACK)) {
                list.add("list");
                list.add("preview");
                list.add("latest");
                list.add("oldest");
                list.add("all");
                list.add("id");
                addOnlinePlayers();
            }
            if ("config".equalsIgnoreCase(args[0]) && configAccess) {
                list.addAll(DCConfigCommandSupport.actions());
            }
            return filterSuggestions(args[1]);
        }

        if ("giveback".equalsIgnoreCase(args[0]) && player != null && PermissionUtils.hasAdminOr(player, Permission.GIVEBACK)) {
            if (args.length == 3) {
                if ("preview".equalsIgnoreCase(args[1])) {
                    list.add("list");
                    addOnlinePlayers();
                    return filterSuggestions(args[2]);
                }
                if ("list".equalsIgnoreCase(args[1])) {
                    addOnlinePlayers();
                } else {
                    list.add("latest");
                    list.add("oldest");
                    list.add("all");
                    list.add("id");
                    list.add("inventory");
                    list.add("ground");
                }
                return filterSuggestions(args[2]);
            }

            if (args.length == 4 && "preview".equalsIgnoreCase(args[1])) {
                if ("list".equalsIgnoreCase(args[2])) {
                    addOnlinePlayers();
                } else {
                    list.add("latest");
                    list.add("oldest");
                    list.add("all");
                    list.add("id");
                    list.add("inventory");
                    list.add("ground");
                }
                return filterSuggestions(args[3]);
            }

            if (args.length == 5 && "preview".equalsIgnoreCase(args[1]) && "id".equalsIgnoreCase(args[3])) {
                list.add("1");
                list.add("2");
                list.add("3");
                return filterSuggestions(args[4]);
            }

            if (args.length == 5 && "preview".equalsIgnoreCase(args[1]) && !"id".equalsIgnoreCase(args[3])) {
                list.add("inventory");
                list.add("ground");
                return filterSuggestions(args[4]);
            }

            if (args.length == 6 && "preview".equalsIgnoreCase(args[1]) && "id".equalsIgnoreCase(args[3])) {
                list.add("inventory");
                list.add("ground");
                return filterSuggestions(args[5]);
            }

            if (args.length == 4 && "id".equalsIgnoreCase(args[2])) {
                list.add("1");
                list.add("2");
                list.add("3");
                return filterSuggestions(args[3]);
            }

            if (args.length == 4 && !"id".equalsIgnoreCase(args[2])) {
                list.add("inventory");
                list.add("ground");
                return filterSuggestions(args[3]);
            }

            if (args.length == 5 && "id".equalsIgnoreCase(args[2])) {
                list.add("inventory");
                list.add("ground");
                return filterSuggestions(args[4]);
            }
        }

        if (!"config".equalsIgnoreCase(args[0]) || !configAccess) {
            return Collections.emptyList();
        }

        if (args.length == 3) {
            if (DCConfigCommandSupport.ACTION_EDIT.equalsIgnoreCase(args[1])) {
                list.addAll(DCConfigCommandSupport.interactiveEditKeys());
            } else {
                list.addAll(DCConfigCommandSupport.keys());
            }
            return filterSuggestions(args[2]);
        }

        if (args.length == 4 && DCConfigCommandSupport.ACTION_SET.equalsIgnoreCase(args[1])) {
            ConfigKey key = DCConfigCommandSupport.resolveKey(args[2]);
            list.addAll(DCConfigCommandSupport.valueSuggestions(key));
            return filterSuggestions(args[3]);
        }

        return Collections.emptyList();
    }

    private void addOnlinePlayers() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            list.add(onlinePlayer.getName());
        }
    }

    private List<String> filterSuggestions(String token) {
        String prefix = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return list.stream()
                .filter(entry -> entry.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
