package com.xl1te.modpanel.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BestLogger {

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\b");
    private static final Pattern IPV6_PATTERN =
            Pattern.compile("\\b([0-9a-fA-F]{1,4}:([0-9a-fA-F]{1,4}:){2,6})(?:[0-9a-fA-F]{1,4}|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b");

    private final JavaPlugin plugin;

    public BestLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String processMessage(String message) {
        if (plugin.getConfig().getBoolean("web-server.ip-privacy", false)) {
            String result = IPV4_PATTERN.matcher(message).replaceAll("$1.XX.XX");
            result = IPV6_PATTERN.matcher(result).replaceAll("$1:XXXX");
            return result;
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
