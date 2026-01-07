package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.database.DatabaseManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

public class MoveItemApiHandler implements HttpHandler {
    private ColoredLogger logger;
    private boolean ipWhitelistEnabled;
    private List<String> ipWhitelist;
    private DatabaseManager databaseManager;
    private JavaPlugin plugin;
    private InventoryUtils inventoryUtils;

    public MoveItemApiHandler(ColoredLogger logger, boolean ipWhitelistEnabled, List<String> ipWhitelist,
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
            logger.warning("Blocked move item API access from non-whitelisted IP: " + clientIP);
        } else if (!"POST".equals(t.getRequestMethod())) {
            t.sendResponseHeaders(405, 0);
            t.getResponseBody().close();
        } else {
            // Parse POST data
            String requestBody = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String[] params = requestBody.split("&");

            String playerName = null;
            int fromSlot = -1;
            int toSlot = -1;

            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);

                    switch (key) {
                        case "player":
                            playerName = value;
                            break;
                        case "fromSlot":
                            try {
                                fromSlot = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                fromSlot = -1;
                            }
                            break;
                        case "toSlot":
                            try {
                                toSlot = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                toSlot = -1;
                            }
                            break;
                    }
                }
            }

            if (playerName == null || fromSlot == -1 || toSlot == -1) {
                String response = "{\"error\":\"Missing required parameters\"}";
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            } else {
                // Find the player
                Player targetPlayer = null;
                String playerUUID = null;
                for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                    if (onlinePlayer.getName().equals(playerName)) {
                        targetPlayer = onlinePlayer;
                        playerUUID = onlinePlayer.getUniqueId().toString();
                        break;
                    }
                }

                // If player is offline, get their UUID from database
                if (targetPlayer == null) {
                    playerUUID = databaseManager.getPlayerUUID(playerName);
                }

                if (playerUUID == null) {
                    String response = "{\"error\":\"Player not found\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(404, response.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                } else {
                    // Move item - works for both online and offline players
                    boolean success = inventoryUtils.moveItemInInventory(playerUUID, fromSlot, toSlot, targetPlayer);

                    String response = success ? "{\"success\":true}"
                            : "{\"success\":false,\"error\":\"Invalid slot or item movement\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                }
            }
        }
        databaseManager.logAccess(clientIP, allowed);
    }
}