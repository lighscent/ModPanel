package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.xl1te.modpanel.utils.ColoredLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class AssetsHandler implements HttpHandler {
    private final ColoredLogger logger;
    private final boolean ipWhitelistEnabled;
    private final List<String> ipWhitelist;

    public AssetsHandler(ColoredLogger logger, boolean ipWhitelistEnabled, List<String> ipWhitelist) {
        this.logger = logger;
        this.ipWhitelistEnabled = ipWhitelistEnabled;
        this.ipWhitelist = ipWhitelist;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();

        if (ipWhitelistEnabled && !ipWhitelist.contains(clientIP)) {
            String response = "403 Forbidden";
            exchange.sendResponseHeaders(403, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if (path.contains("..")) {
            String response = "400 Bad Request";
            exchange.sendResponseHeaders(400, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            return;
        }

        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            byte[] content = is.readAllBytes();

            String contentType = "application/octet-stream";
            if (path.endsWith(".png"))
                contentType = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg"))
                contentType = "image/jpeg";
            else if (path.endsWith(".gif"))
                contentType = "image/gif";
            else if (path.endsWith(".css"))
                contentType = "text/css";
            else if (path.endsWith(".js"))
                contentType = "application/javascript";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400"); // Cache for 1 day

            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        } catch (IOException e) {
            logger.warning("Error serving asset: " + path + " - " + e.getMessage());
            String response = "500 Internal Server Error";
            exchange.sendResponseHeaders(500, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
