package com.xl1te.modpanel;

import com.xl1te.modpanel.commands.MPCommand;
import com.xl1te.modpanel.config.PluginConfig;
import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.discord.DiscordBot;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.utils.VersionCheck;
import com.xl1te.modpanel.web.WebServer;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private BestLogger bestLogger;
    private WebServer webServer;
    private DiscordBot discordBot;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        String version = this.getDescription().getVersion();

        this.bestLogger = new BestLogger(this);
        new Metrics(this, 28745);

        try {
            VersionCheck updater = new VersionCheck(new BestLogger(this), version);
            updater.checkForUpdates();
        } catch (Exception e) {
            bestLogger.severe("Failed to check for updates: " + e.getMessage());
        }

        try {
            PluginConfig.updateConfig(this);
        } catch (Exception e) {
            bestLogger.severe("Failed to load configuration: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            this.databaseManager = new DatabaseManager(this, bestLogger);
            this.databaseManager.initialize();

            this.webServer = new WebServer(this, bestLogger);
            this.webServer.startWebServer();

            this.discordBot = new DiscordBot(this, bestLogger);
            this.discordBot.startBot();

            getCommand("mp").setExecutor(new MPCommand(this, bestLogger));

            bestLogger.info("ModPanel enabled successfully!");
        } catch (Exception e) {
            bestLogger.severe("Fatal error during server startup: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stopWebServer();
        }
        if (discordBot != null) {
            discordBot.stopBot();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        bestLogger.info("ModPanel has been disabled.");
    }

    public BestLogger getBestLogger() {
        return bestLogger;
    }

    public WebServer getWebServer() {
        return webServer;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}