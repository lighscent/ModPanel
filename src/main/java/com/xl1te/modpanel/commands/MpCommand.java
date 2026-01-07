package com.xl1te.modpanel.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.web.WebServer;
import java.net.ServerSocket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MpCommand implements CommandExecutor, TabCompleter {
    private Main plugin;
    private ColoredLogger coloredLogger;
    private WebServer webServer;
    private DatabaseManager databaseManager;

    public MpCommand(Main plugin, ColoredLogger coloredLogger, WebServer webServer, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.coloredLogger = coloredLogger;
        this.webServer = webServer;
        this.databaseManager = databaseManager;
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mp.admin")) {
            sender.sendMessage("You do not have permission to use ModPanel commands.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: /mp <reload|help>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("mp.admin")) {
                    sender.sendMessage("You do not have permission to reload ModPanel.");
                    return true;
                }
                plugin.reloadConfig();
                webServer.stopWebServer();
                int newPort = plugin.getConfig().getInt("server-port", 9999);
                boolean newIpWhitelistEnabled = plugin.getConfig().getBoolean("ip-whitelist-enabled", false);
                List<String> newIpWhitelist = plugin.getConfig().getStringList("ip-whitelist"); // Wait for port to be
                                                                                                // available
                int attempts = 0;
                while (!isPortAvailable(newPort) && attempts < 10) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    attempts++;
                }
                if (!isPortAvailable(newPort)) {
                    sender.sendMessage("Failed to restart web server: Port " + newPort + " is still in use.");
                    coloredLogger.warning("Failed to restart web server after reload: Port in use");
                    return true;
                }
                WebServer newWebServer = new WebServer(coloredLogger, newPort, newIpWhitelistEnabled, newIpWhitelist,
                        databaseManager, plugin);
                newWebServer.startWebServer();
                plugin.setWebServer(newWebServer);
                sender.sendMessage("ModPanel has been reloaded!");
                coloredLogger.info("ModPanel reloaded by " + sender.getName());
                break;
            case "help":
                sender.sendMessage("Available ModPanel commands:");
                sender.sendMessage("- /mp reload: Reload the plugin configuration");
                sender.sendMessage("- /mp help: Show this list");
                break;
            default:
                sender.sendMessage("Unknown subcommand. Use /mp help for help.");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("reload".startsWith(partial)) {
                completions.add("reload");
            }
            if ("help".startsWith(partial)) {
                completions.add("help");
            }
        }
        return completions;
    }
}