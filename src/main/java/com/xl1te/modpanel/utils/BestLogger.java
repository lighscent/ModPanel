package com.xl1te.modpanel.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BestLogger {
    private final JavaPlugin plugin;

    public BestLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String processMessage(String message) {
        if (plugin.getConfig().getBoolean("web-server.ip-privacy", false)) {
            // Match IPv4 addresses and mask the last two octets
            Pattern pattern = Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\b");
            Matcher matcher = pattern.matcher(message);
            return matcher.replaceAll("$1.XX.XX");
        }
        return message;
    }

    public void info(String message) {
        plugin.getLogger().info("\u001B[32m" + processMessage(message) + "\u001B[0m");
    }

    public void warning(String message) {
        plugin.getLogger().warning("\u001B[33m" + processMessage(message) + "\u001B[0m");
    }

    public void severe(String message) {
        plugin.getLogger().severe("\u001B[31m" + processMessage(message) + "\u001B[0m");
    }

}
