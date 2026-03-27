package com.xl1te.modpanel;

import com.xl1te.modpanel.config.PluginConfig;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.web.WebServer;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private BestLogger bestLogger;
    private WebServer webServer;

    @Override
    public void onEnable() {
        this.bestLogger = new BestLogger(getLogger());
        new Metrics(this, 28745);

        try {
            PluginConfig.updateConfig(this);
        } catch (Exception e) {
            bestLogger.severe("Failed to load configuration: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            this.webServer = new WebServer(this, bestLogger);
            this.webServer.startWebServer();

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
        bestLogger.info("ModPanel has been disabled.");
    }

    public BestLogger getBestLogger() {
        return bestLogger;
    }
}