package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpServer;
import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.database.DatabaseManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

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

    public WebServer(ColoredLogger logger, int port, boolean ipWhitelistEnabled, List<String> ipWhitelist,
            DatabaseManager databaseManager, JavaPlugin plugin) {
        this.logger = logger;
        this.port = port;
        this.ipWhitelistEnabled = ipWhitelistEnabled;
        this.ipWhitelist = ipWhitelist;
        this.databaseManager = databaseManager;
        this.plugin = plugin;
        this.inventoryUtils = new InventoryUtils(logger, databaseManager);
    }

    public void startWebServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/", new RootHandler(logger, ipWhitelistEnabled, ipWhitelist, databaseManager));

            server.createContext("/api/players",
                    new PlayersApiHandler(logger, ipWhitelistEnabled, ipWhitelist, databaseManager, plugin));

            server.createContext("/api/inventory", new InventoryApiHandler(logger, ipWhitelistEnabled, ipWhitelist,
                    databaseManager, plugin, inventoryUtils));

            server.createContext("/inventory.html",
                    new InventoryPageHandler(logger, ipWhitelistEnabled, ipWhitelist, databaseManager));

            server.createContext("/api/move-item", new MoveItemApiHandler(logger, ipWhitelistEnabled, ipWhitelist,
                    databaseManager, plugin, inventoryUtils));

            server.start();
            logger.info("ModPanel Web Server started on port " + port);
        } catch (

        IOException e) {
            e.printStackTrace();
        }
    }

    public void stopWebServer() {
        if (server != null) {
            server.stop(0);
            logger.severe("ModPanel Web Server stopped");
        }
    }
}
