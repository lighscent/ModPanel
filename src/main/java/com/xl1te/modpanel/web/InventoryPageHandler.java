package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.database.DatabaseManager;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class InventoryPageHandler implements HttpHandler {
    private ColoredLogger logger;
    private boolean ipWhitelistEnabled;
    private List<String> ipWhitelist;
    private DatabaseManager databaseManager;
    private final Map<String, String> htmlCache = new HashMap<>();

    public InventoryPageHandler(ColoredLogger logger, boolean ipWhitelistEnabled, List<String> ipWhitelist,
            DatabaseManager databaseManager) {
        this.logger = logger;
        this.ipWhitelistEnabled = ipWhitelistEnabled;
        this.ipWhitelist = ipWhitelist;
        this.databaseManager = databaseManager;

        // Pre-load and cache pages
        preloadPages();
    }

    private void preloadPages() {
        htmlCache.put("/public/pages/inventory.html", loadHtmlPage("/public/pages/inventory.html"));
        htmlCache.put("/public/pages/403.html", loadHtmlPage("/public/pages/403.html"));
    }

    private String getCachedPage(String path) {
        return htmlCache.getOrDefault(path, "<h1>Error</h1><p>Page not found.</p>");
    }

    private String loadHtmlPage(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                logger.warning("HTML page not found: " + path);
                return "<h1>Error</h1><p>Page not found.</p>";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("Failed to load HTML page: " + path + " - " + e.getMessage());
            return "<h1>Error</h1><p>Failed to load page.</p>";
        }
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        String clientIP = t.getRemoteAddress().getAddress().getHostAddress();
        boolean allowed = true;
        if (ipWhitelistEnabled && !ipWhitelist.contains(clientIP)) {
            allowed = false;
            String response = getCachedPage("/public/pages/403.html");
            t.getResponseHeaders().set("Content-Type", "text/html");
            t.sendResponseHeaders(403, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
            logger.warning("Blocked inventory page access from non-whitelisted IP: " + clientIP);
        } else {
            String response = getCachedPage("/public/pages/inventory.html");
            t.getResponseHeaders().set("Content-Type", "text/html");
            t.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
        databaseManager.logAccess(clientIP, allowed);
    }
}