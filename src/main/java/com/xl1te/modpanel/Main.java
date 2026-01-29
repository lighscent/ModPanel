package com.xl1te.modpanel;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.utils.VersionCheck;
import com.xl1te.modpanel.web.WebServer;
import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.commands.MpCommand;
import com.xl1te.modpanel.listeners.PlayerListener;

public class Main extends JavaPlugin {
    private ColoredLogger coloredLogger;
    private WebServer webServer;
    private DatabaseManager databaseManager;
    private PlayerListener playerListener;

    @Override
    public void onEnable() {
        int pluginId = 28745;
        Metrics metrics = new Metrics(this, pluginId);
        coloredLogger = new ColoredLogger(getLogger());
        saveDefaultConfig();

        databaseManager = new DatabaseManager(coloredLogger, "./plugins/ModPanel/data");

        VersionCheck versionCheck = new VersionCheck();
        String current = versionCheck.getCurrentVersion();
        String latest = versionCheck.getLatestVersion();

        if (current != null && latest != null && !current.equals(latest)) {
            if (versionCheck.isSecurityUpdate(latest)) {
                coloredLogger.severe(
                        "SECURITY UPDATE AVAILABLE! A critical security update (" + latest + ") is available.");
                coloredLogger.severe(
                        "Please update immediately at: https://modrinth.com/plugin/modpanel");
            } else {
                coloredLogger.warning(
                        "A new version of ModPanel (" + latest
                                + ") is available! Download it here: https://modrinth.com/plugin/modpanel");
            }
        }

        // Initialize and start web server
        int port = getConfig().getInt("server-port", 9999);
        boolean ipWhitelistEnabled = getConfig().getBoolean("ip-whitelist-enabled", false);
        List<String> ipWhitelist = getConfig().getStringList("ip-whitelist");
        webServer = new WebServer(coloredLogger, port, ipWhitelistEnabled, ipWhitelist, databaseManager, this);
        webServer.startWebServer();

        // register /mp command
        MpCommand mpCommand = new MpCommand(this, coloredLogger, webServer, databaseManager);
        getCommand("mp").setExecutor(mpCommand);
        getCommand("mp").setTabCompleter(mpCommand);

        // register player listener
        playerListener = new PlayerListener(this, coloredLogger, databaseManager);
        getServer().getPluginManager().registerEvents(playerListener, this);

        coloredLogger.info("ModPanel has been enabled!");
    }

    @Override
    public void onDisable() {
        coloredLogger.severe("ModPanel has been disabled!");

        // Save all online players' inventories before shutdown
        if (playerListener != null) {
            playerListener.saveAllOnlinePlayersInventory();
        }

        if (webServer != null) {
            webServer.stopWebServer();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public void setWebServer(WebServer webServer) {
        this.webServer = webServer;
    }
}