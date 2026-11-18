package com.xl1te.modpanel;

import com.xl1te.modpanel.commands.MPCommand;
import com.xl1te.modpanel.config.PluginConfig;
import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.discord.DiscordBot;
import com.xl1te.modpanel.listeners.PlayerLiveListener;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.utils.VersionCheck;
import com.xl1te.modpanel.web.WebServer;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends JavaPlugin {

    private BestLogger bestLogger;
    private WebServer webServer;
    private DiscordBot discordBot;
    private DatabaseManager databaseManager;
    private ExecutorService asyncExecutor;

    @Override
    public void onEnable() {
        String version = this.getDescription().getVersion();

        this.bestLogger = new BestLogger(this);
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ModPanel-Async");
            t.setDaemon(true);
            return t;
        });
        new Metrics(this, 28745);

        try {
            VersionCheck updater = new VersionCheck(bestLogger, version, asyncExecutor);
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

            createDefaultAdmin();

            this.webServer = new WebServer(this, bestLogger);
            this.webServer.startWebServer();

            getServer().getPluginManager().registerEvents(
                    new PlayerLiveListener(this, webServer.getLiveHub()), this);

            this.discordBot = new DiscordBot(this, bestLogger, asyncExecutor);
            this.discordBot.startBot();

            getCommand("mp").setExecutor(new MPCommand(this, bestLogger));

            bestLogger.info("ModPanel enabled successfully!");
        } catch (Exception e) {
            bestLogger.severe("Fatal error during server startup: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void createDefaultAdmin() {
        try {
            var repo = databaseManager.getUserRepository();
            var perms = databaseManager.getPermissionRepository();
            if (repo.findByUsername("admin").isEmpty()) {
                var admin = new com.xl1te.modpanel.database.models.User(
                        0, "admin",
                        com.xl1te.modpanel.web.auth.PasswordHasher.hash("admin123"),
                        "admin", true,
                        java.time.LocalDateTime.now(), null);
                int adminId = repo.create(admin);
                perms.grant(adminId, "modpanel.stats");
                perms.grant(adminId, "modpanel.players");
                perms.grant(adminId, "modpanel.player.detail");
                perms.grant(adminId, "modpanel.users.view");
                perms.grant(adminId, "modpanel.users.manage");
                perms.grant(adminId, "modpanel.whitelist");
                repo.setPasswordChangeRequired(adminId);
                bestLogger.info(
                        "Default admin user created (admin / admin123) - password change required at first login!");
            } else {
                var admin = repo.findByUsername("admin");
                if (admin.isPresent()
                        && com.xl1te.modpanel.web.auth.PasswordHasher.verify("admin123",
                                admin.get().getPasswordHash())) {
                    repo.setPasswordChangeRequired(admin.get().getId());
                    bestLogger.warning("Default admin password still in use - password change required at next login!");
                }
            }
        } catch (Exception e) {
            bestLogger.severe("Failed to create default admin: " + e.getMessage());
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
        if (asyncExecutor != null) {
            asyncExecutor.shutdownNow();
        }
        if (bestLogger != null) {
            bestLogger.info("ModPanel has been disabled.");
        }
    }

    public ExecutorService getAsyncExecutor() {
        return asyncExecutor;
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