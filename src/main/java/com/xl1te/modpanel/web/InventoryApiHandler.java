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

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class InventoryApiHandler implements HttpHandler {
    private ColoredLogger logger;
    private boolean ipWhitelistEnabled;
    private List<String> ipWhitelist;
    private DatabaseManager databaseManager;
    private JavaPlugin plugin;
    private InventoryUtils inventoryUtils;

    public InventoryApiHandler(ColoredLogger logger, boolean ipWhitelistEnabled, List<String> ipWhitelist,
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
            logger.warning("Blocked inventory API access from non-whitelisted IP: " + clientIP);
        } else {
            // Get query parameters
            String query = t.getRequestURI().getQuery();
            String playerName = null;

            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "player".equals(keyValue[0])) {
                        playerName = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        break;
                    }
                }
            }

            if (playerName == null) {
                String response = "{\"error\":\"Player name required\"}";
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            } else {
                // Find and serialize inventory safely on the main thread
                final String finalPlayerName = playerName;
                String inventoryJson = null;
                boolean isOnline = false;

                try {
                    inventoryJson = plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                        Player target = null;
                        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                            if (onlinePlayer.getName().equals(finalPlayerName)) {
                                target = onlinePlayer;
                                break;
                            }
                        }

                        if (target != null) {
                            return inventoryUtils.serializeInventoryJson(target.getInventory());
                        }
                        return null;
                    }).get();
                    if (inventoryJson != null)
                        isOnline = true;
                } catch (InterruptedException | ExecutionException e) {
                    logger.warning("Failed to get player inventory on main thread: " + e.getMessage());
                }

                StringBuilder json = new StringBuilder();
                json.append("{");

                if (isOnline) {
                    // Player is online, get their current inventory
                    json.append("\"player\":{\"name\":\"").append(playerName)
                            .append("\",\"online\":true},");
                    json.append("\"inventory\":");
                    json.append(inventoryJson);
                } else {
                    // Player is offline, load inventory from database
                    String playerUUID = databaseManager.getPlayerUUID(playerName);
                    if (playerUUID != null) {
                        json.append("\"player\":{\"name\":\"").append(playerName)
                                .append("\",\"online\":false},");
                        json.append("\"inventory\":");

                        // Load saved inventory data
                        ItemStack[] mainInventory = databaseManager.loadPlayerMainInventory(playerUUID);
                        ItemStack[] armorInventory = databaseManager.loadPlayerArmorInventory(playerUUID);
                        ItemStack offhandItem = databaseManager.loadPlayerOffhandItem(playerUUID);

                        json.append("{");
                        json.append("\"main\":[");
                        for (int i = 0; i < mainInventory.length; i++) {
                            if (i > 0)
                                json.append(",");
                            json.append(inventoryUtils.itemToJson(mainInventory[i]));
                        }
                        json.append("],");

                        json.append("\"armor\":[");
                        for (int i = 0; i < armorInventory.length; i++) {
                            if (i > 0)
                                json.append(",");
                            json.append(inventoryUtils.itemToJson(armorInventory[i]));
                        }
                        json.append("],");

                        json.append("\"offhand\":[");
                        json.append(inventoryUtils.itemToJson(offhandItem));
                        json.append("]");

                        json.append("}");
                    } else {
                        // Player not found in database
                        json.append("\"player\":{\"name\":\"").append(playerName)
                                .append("\",\"online\":false},");
                        json.append("\"inventory\":{\"main\":[],\"armor\":[],\"offhand\":[]}");
                    }
                }

                json.append("}");

                String response = json.toString();
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }
        databaseManager.logAccess(clientIP, allowed);
    }
}