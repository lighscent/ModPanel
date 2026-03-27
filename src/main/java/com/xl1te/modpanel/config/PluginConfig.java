package com.xl1te.modpanel.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginConfig {

    public static void updateConfig(JavaPlugin plugin) {
        plugin.saveDefaultConfig();

        FileConfiguration config = plugin.getConfig();

        config.addDefault("server.port", 9999);
        config.addDefault("server.whitelist.ips", java.util.Collections.emptyList());

        config.options().copyDefaults(true);
    }

}
