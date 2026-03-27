package com.xl1te.modpanel.commands.web;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class WebCommand {

    private final Main plugin;
    private final BestLogger logger;

    public WebCommand(Main plugin, BestLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(logger.processMessage(message));
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendMessage(sender, "§cUsage: /mp web <subcommand>");
            sendMessage(sender, "§cSubcommands: whitelist");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("whitelist")) {
            return handleWhitelist(sender, args);
        }

        sendMessage(sender, "§cUnknown web subcommand: " + subCommand);
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of("whitelist");
        }

        if (args.length == 1) {
            return filter(List.of("whitelist"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            return filter(List.of("add", "remove", "addip", "removeip", "list"), args[1]);
        }

        if (args[0].equalsIgnoreCase("whitelist")) {
            if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                String prefix = args[2] == null ? "" : args[2].toLowerCase();
                var result = new ArrayList<String>();
                for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
                    String name = player.getName();
                    if (name.toLowerCase().startsWith(prefix)) {
                        result.add(name);
                    }
                }
                return result;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("addip")) {
                return List.of("<ip>");
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("removeip")) {
                String prefix = args[2] == null ? "" : args[2].toLowerCase();
                try {
                    var entries = plugin.getDatabaseManager().getWebWhitelistRepository().list();
                    var result = new ArrayList<String>();
                    for (var entry : entries) {
                        if (entry.getIp().toLowerCase().startsWith(prefix)) {
                            result.add(entry.getIp());
                        }
                    }
                    return result;
                } catch (Exception e) {
                    return List.of();
                }
            }
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")) {
                    return List.of("<ip>");
                }
            }
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(options);
        }
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private boolean handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "§cUsage: /mp web whitelist <add|remove|list> [ip]");
            return true;
        }

        String action = args[1].toLowerCase();
        var repo = plugin.getDatabaseManager().getWebWhitelistRepository();

        try {
            switch (action) {
                case "add" -> {
                    if (args.length != 3) {
                        sendMessage(sender, "§cUsage: /mp web whitelist add <player>");
                        return true;
                    }
                    String playerName = args[2];
                    var player = plugin.getServer().getPlayerExact(playerName);
                    if (player == null || !player.isOnline()) {
                        sendMessage(sender, "§cPlayer not online: " + playerName);
                        return true;
                    }
                    String ip = player.getAddress().getAddress().getHostAddress();
                    if (repo.exists(ip)) {
                        sendMessage(sender, "§ePlayer IP is already whitelisted: " + ip);
                    } else {
                        repo.add(ip);
                        sendMessage(sender, "§aPlayer IP added to web whitelist: " + ip);
                        logger.info("Web whitelist add (player) performed by " + sender.getName() + ": " + ip + " ("
                                + playerName + ")");
                    }
                    return true;
                }
                case "addip" -> {
                    if (args.length != 3) {
                        sendMessage(sender, "§cUsage: /mp web whitelist addip <ip>");
                        return true;
                    }
                    String ip = args[2];
                    if (repo.exists(ip)) {
                        sendMessage(sender, "§eIP is already whitelisted: " + ip);
                    } else {
                        repo.add(ip);
                        sendMessage(sender, "§aIP added to web whitelist: " + ip);
                        logger.info("Web whitelist addip performed by " + sender.getName() + ": " + ip);
                    }
                    return true;
                }
                case "remove" -> {
                    if (args.length != 3) {
                        sendMessage(sender, "§cUsage: /mp web whitelist remove <player>");
                        return true;
                    }
                    String playerName = args[2];
                    var player = plugin.getServer().getPlayerExact(playerName);
                    if (player == null || !player.isOnline()) {
                        sendMessage(sender, "§cPlayer not online: " + playerName);
                        return true;
                    }
                    String ip = player.getAddress().getAddress().getHostAddress();
                    if (!repo.exists(ip)) {
                        sendMessage(sender, "§ePlayer IP not found in whitelist: " + ip);
                    } else {
                        repo.remove(ip);
                        sendMessage(sender, "§aPlayer IP removed from web whitelist: " + ip);
                        logger.info("Web whitelist remove (player) performed by " + sender.getName() + ": " + ip + " ("
                                + playerName + ")");
                    }
                    return true;
                }
                case "removeip" -> {
                    if (args.length != 3) {
                        sendMessage(sender, "§cUsage: /mp web whitelist removeip <ip>");
                        return true;
                    }
                    String ip = args[2];
                    if (!repo.exists(ip)) {
                        sendMessage(sender, "§eIP not found in whitelist: " + ip);
                    } else {
                        repo.remove(ip);
                        sendMessage(sender, "§aIP removed from web whitelist: " + ip);
                        logger.info("Web whitelist removeip performed by " + sender.getName() + ": " + ip);
                    }
                    return true;
                }
                case "list" -> {
                    var entries = repo.list();
                    sendMessage(sender, "§aWeb whitelist entries (" + entries.size() + "): ");
                    for (var entry : entries) {
                        sendMessage(sender, " §7- " + entry.getIp() + " (added " + entry.getAddedAt() + ")");
                    }
                    return true;
                }
                default -> {
                    sendMessage(sender, "§cUnknown action for web whitelist: " + action);
                    return true;
                }
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Unexpected error" : e.getMessage();
            sendMessage(sender, "§cDatabase error: " + message);
            logger.severe("Web whitelist operation failed: " + message);
            e.printStackTrace();
            return true;
        }
    }
}
