package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.database.DatabaseManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

public class PlayersApiHandler implements HttpHandler {
    private ColoredLogger logger;
    private boolean ipWhitelistEnabled;
    private List<String> ipWhitelist;
    private DatabaseManager databaseManager;
    private JavaPlugin plugin;

    public PlayersApiHandler(ColoredLogger logger, boolean ipWhitelistEnabled, List<String> ipWhitelist,
            DatabaseManager databaseManager, JavaPlugin plugin) {
        this.logger = logger;
        this.ipWhitelistEnabled = ipWhitelistEnabled;
        this.ipWhitelist = ipWhitelist;
        this.databaseManager = databaseManager;
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        String clientIP = t.getRemoteAddress().getAddress().getHostAddress();
        boolean allowed = true;
        if (ipWhitelistEnabled && !ipWhitelist.contains(clientIP)) {
            allowed = false;
            t.sendResponseHeaders(403, 0);
            t.getResponseBody().close();
            logger.warning("Blocked API access from non-whitelisted IP: " + clientIP);
        } else {
            // Get online players safely on the main thread
            Set<String> onlineUUIDs = new HashSet<>();
            try {
                onlineUUIDs = plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                    Set<String> uuids = new HashSet<>();
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        uuids.add(player.getUniqueId().toString());
                    }
                    return uuids;
                }).get();
            } catch (InterruptedException | ExecutionException e) {
                logger.warning("Failed to get online players on main thread: " + e.getMessage());
            }

            // Get all players from DB
            List<String[]> allPlayers = databaseManager.getAllPlayers();

            // Build JSON response
            StringBuilder json = new StringBuilder();
            json.append("[");
            for (int i = 0; i < allPlayers.size(); i++) {
                String[] player = allPlayers.get(i);
                boolean online = onlineUUIDs.contains(player[0]);
                json.append("{");
                json.append("\"uuid\":\"").append(player[0]).append("\",");
                json.append("\"name\":\"").append(player[1]).append("\",");
                json.append("\"first_seen\":\"").append(player[2]).append("\",");
                json.append("\"last_seen\":\"").append(player[3]).append("\",");
                json.append("\"online\":").append(online);
                json.append("}");
                if (i < allPlayers.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]");

            String response = json.toString();
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
        databaseManager.logAccess(clientIP, allowed);
    }
}