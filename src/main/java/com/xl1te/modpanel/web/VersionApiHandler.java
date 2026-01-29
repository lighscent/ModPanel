package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.utils.VersionCheck;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class VersionApiHandler implements HttpHandler {
    private final ColoredLogger logger;
    private final boolean ipWhitelistEnabled;
    private final List<String> ipWhitelist;
    private final VersionCheck versionCheck;

    public VersionApiHandler(ColoredLogger logger, boolean ipWhitelistEnabled, List<String> ipWhitelist) {
        this.logger = logger;
        this.ipWhitelistEnabled = ipWhitelistEnabled;
        this.ipWhitelist = ipWhitelist;
        this.versionCheck = new VersionCheck();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        if (ipWhitelistEnabled && !ipWhitelist.contains(clientIp)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        String current = versionCheck.getCurrentVersion();
        String latest = versionCheck.getLatestVersion();
        boolean updateAvailable = current != null && latest != null && !current.equals(latest);
        boolean isSecurity = versionCheck.isSecurityUpdate(latest);

        String json = String.format(
                "{\"current\": \"%s\", \"latest\": \"%s\", \"updateAvailable\": %b, \"securityUpdate\": %b}",
                current != null ? current : "unknown",
                latest != null ? latest : "unknown",
                updateAvailable,
                isSecurity);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
