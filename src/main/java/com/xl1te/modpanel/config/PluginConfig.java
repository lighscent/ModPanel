package com.xl1te.modpanel.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Fusionne la config embarquée dans celle du serveur : seules les clés
 * manquantes sont ajoutées, les valeurs de l'utilisateur sont préservées.
 * Délègue le parsing à SnakeYAML via Bukkit (indentation, listes, ":" dans
 * les valeurs gérés) au lieu d'un parseur maison.
 */
public class PluginConfig {

    public static void updateConfig(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            return;
        }

        try (InputStream is = plugin.getResource("config.yml")) {
            if (is == null) {
                plugin.reloadConfig();
                return;
            }
            FileConfiguration defaults = YamlConfiguration
                    .loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
            FileConfiguration config = plugin.getConfig();
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            plugin.saveConfig();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to merge default config: " + e.getMessage());
        }
        plugin.reloadConfig();
    }
}
