package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpServer;
import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.utils.PublicIpUtil;
import com.xl1te.modpanel.database.DatabaseManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.bukkit.plugin.java.JavaPlugin;

public class WebServer {
        private HttpServer server;
        private ColoredLogger logger;
        private int port;
        private boolean ipWhitelistEnabled;
        private List<String> ipWhitelist;
        private DatabaseManager databaseManager;
        private JavaPlugin plugin;
        private InventoryUtils inventoryUtils;
        private ExecutorService webExecutor;

        public WebServer(ColoredLogger logger, int port, boolean ipWhitelistEnabled, List<String> ipWhitelist,
                        DatabaseManager databaseManager, JavaPlugin plugin) {
                this.logger = logger;
                this.port = port;
                this.ipWhitelistEnabled = ipWhitelistEnabled;
                this.ipWhitelist = ipWhitelist;
                this.databaseManager = databaseManager;
                this.plugin = plugin;
                this.inventoryUtils = new InventoryUtils(logger, databaseManager);
                this.webExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        }

        public void startWebServer() {
                try {
                        server = HttpServer.create(new InetSocketAddress(port), 0);
                        server.setExecutor(webExecutor);

                        server.createContext("/",
                                        new RootHandler(logger, ipWhitelistEnabled, ipWhitelist, databaseManager));

                        server.createContext("/api/players",
                                        new PlayersApiHandler(logger, ipWhitelistEnabled, ipWhitelist, databaseManager,
                                                        plugin));

                        server.createContext("/api/inventory",
                                        new InventoryApiHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        databaseManager, plugin, inventoryUtils));

                        server.createContext("/profile",
                                        new InventoryPageHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        databaseManager));

                        server.createContext("/api/move-item",
                                        new MoveItemApiHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        databaseManager, plugin, inventoryUtils));

                        server.createContext("/api/remove-item",
                                        new RemoveItemApiHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        databaseManager, plugin, inventoryUtils));

                        server.createContext("/api/events", new EventsHandler(logger, ipWhitelistEnabled, ipWhitelist));

                        server.createContext("/api/version",
                                        new VersionApiHandler(logger, ipWhitelistEnabled, ipWhitelist));

                        // CSS files
                        server.createContext("/colors.css",
                                        new StaticFileHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        "/public/css/colors.css", "text/css"));

                        server.createContext("/global.css",
                                        new StaticFileHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        "/public/css/global.css", "text/css"));

                        server.createContext("/index.css",
                                        new StaticFileHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        "/public/css/index.css", "text/css"));

                        server.createContext("/inventory.css",
                                        new StaticFileHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        "/public/css/inventory.css", "text/css"));

                        server.createContext("/403.css", new StaticFileHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                        "/public/css/403.css", "text/css"));

                        // Profile Modules
                        server.createContext("/profile/header.html",
                                        new StaticFileHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        "/public/pages/profile/header.html", "text/html"));
                        server.createContext("/profile/footer.html",
                                        new StaticFileHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        "/public/pages/profile/footer.html", "text/html"));
                        server.createContext("/profile/scripts.js",
                                        new StaticFileHandler(logger, ipWhitelistEnabled, ipWhitelist,
                                                        "/public/pages/profile/scripts.js", "application/javascript"));

                        // Assets
                        server.createContext("/assets/minecraft/",
                                        new AssetsHandler(logger, ipWhitelistEnabled, ipWhitelist));

                        server.start();
                        String publicIp = PublicIpUtil.getPublicIp();
                        if (publicIp != null) {
                                logger.info("ModPanel Web Server is accessible at http://" + publicIp + ":" + port);
                        } else {
                                logger.info("ModPanel Web Server started on port " + port);
                        }
                } catch (IOException e) {
                        logger.severe("Failed to start ModPanel Web Server on port " + port + ": " + e.getMessage());
                }
        }

        public void stopWebServer() {
                if (server != null) {
                        server.stop(0);
                        if (webExecutor != null) {
                                webExecutor.shutdown();
                        }
                        logger.severe("ModPanel Web Server stopped");
                }
        }
}
