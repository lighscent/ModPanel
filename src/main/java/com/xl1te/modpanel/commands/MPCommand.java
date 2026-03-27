package com.xl1te.modpanel.commands;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.config.PluginConfig;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.commands.web.WebCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MPCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final BestLogger logger;

    public MPCommand(Main plugin, BestLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(logger.processMessage(message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendMessage(sender, "§cUsage: /mp <subcommand>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("reload")) {
            try {
                PluginConfig.updateConfig(plugin);
                sendMessage(sender, "§aConfiguration reloaded successfully!");
                logger.info("Configuration reloaded by " + sender.getName());
            } catch (Exception e) {
                sendMessage(sender, "§cFailed to reload configuration: " + e.getMessage());
                logger.severe("Failed to reload configuration: " + e.getMessage());
            }
            return true;
        }

        if (subCommand.equals("web")) {
            String[] webArgs = new String[args.length - 1];
            System.arraycopy(args, 1, webArgs, 0, args.length - 1);
            return new WebCommand(plugin, logger).execute(sender, webArgs);
        }

        sendMessage(sender, "§cUnknown subcommand: " + subCommand);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("reload", "web"), args[0]);
        }

        if (args[0].equalsIgnoreCase("web")) {
            String[] webArgs = Arrays.copyOfRange(args, 1, args.length);
            return new WebCommand(plugin, logger).tabComplete(sender, webArgs);
        }

        return new ArrayList<>();
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
}
