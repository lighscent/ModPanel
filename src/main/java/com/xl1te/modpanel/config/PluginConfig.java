package com.xl1te.modpanel.config;

import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class PluginConfig {

    public static void updateConfig(JavaPlugin plugin) {
        plugin.saveDefaultConfig();

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            ConfigUpdater.update(plugin, "config.yml", configFile, Collections.emptyList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        plugin.reloadConfig();
    }
}