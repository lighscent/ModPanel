package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.database.DatabaseManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

public class RemoveItemApiHandler implements HttpHandler {
    private ColoredLogger logger;
    private boolean ipWhitelistEnabled;
    private List<String> ipWhitelist;
    private DatabaseManager databaseManager;
    private JavaPlugin plugin;
    private InventoryUtils inventoryUtils;

    public RemoveItemApiHandler(ColoredLogger logger, boolean ipWhitelistEnabled, List<String> ipWhitelist,
            DatabaseManager databaseManager, JavaPlugin plugin, InventoryUtils inventoryUtils) {
        this.logger = logger;
        this.ipWhitelistEnabled = ipWhitelistEnabled;
        this.ipWhitelist = ipWhitelist;
        this.databaseManager = databaseManager;
        this.plugin = plugin;
        this.inventoryUtils = inventoryUtils;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        String clientIP = t.getRemoteAddress().getAddress().getHostAddress();
        boolean allowed = true;
        if (ipWhitelistEnabled && !ipWhitelist.contains(clientIP)) {
            allowed = false;
            t.sendResponseHeaders(403, 0);
            t.getResponseBody().close();
            logger.warning("Blocked remove item API access from non-whitelisted IP: " + clientIP);
        } else if (!"POST".equals(t.getRequestMethod())) {
            t.sendResponseHeaders(405, 0);
            t.getResponseBody().close();
        } else {
            // Parse POST data
            String requestBody = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String[] params = requestBody.split("&");

            String playerUUID = null;
            int slot = -1;

            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);

                    switch (key) {
                        case "uuid":
                            playerUUID = value;
                            break;
                        case "slot":
                            try {
                                slot = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                slot = -1;
                            }
                            break;
                    }
                }
            }

            if (playerUUID == null || slot == -1) {
                String response = "{\"error\":\"Missing required parameters\"}";
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            } else {
                // Check player existence outside main thread logic
                final String finalUUID = playerUUID;
                final int finalSlot = slot;

                String pName = databaseManager.getPlayerName(finalUUID);
                boolean exists = pName != null;
                boolean success = false;

                if (exists) {
                    try {
                        // All Bukkit API calls must be synchronous
                        // We use a Future to get result back from main thread
                        Boolean operationResult = plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                            Player p = plugin.getServer().getPlayer(java.util.UUID.fromString(finalUUID));
                            // If player is online, we pass the Player object, otherwise null
                            // The InventoryUtils handles the online/offline logic details
                            return inventoryUtils.removeItemFromSlot(finalUUID, finalSlot, p);
                        }).get();

                        success = operationResult != null && operationResult;
                    } catch (InterruptedException | ExecutionException e) {
                        logger.warning("Error processing remove item task: " + e.getMessage());
                        success = false;
                    }
                }

                String response = "{\"success\":" + success + "}";
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }
    }
}
