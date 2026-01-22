package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.xl1te.modpanel.utils.ColoredLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class StaticFileHandler implements HttpHandler {
    private ColoredLogger logger;
    private boolean ipWhitelistEnabled;
    private List<String> ipWhitelist;
    private String resourcePath;
    private String contentType;

    public StaticFileHandler(ColoredLogger logger, boolean ipWhitelistEnabled, List<String> ipWhitelist,
            String resourcePath, String contentType) {
        this.logger = logger;
        this.ipWhitelistEnabled = ipWhitelistEnabled;
        this.ipWhitelist = ipWhitelist;
        this.resourcePath = resourcePath;
        this.contentType = contentType;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();

        if (ipWhitelistEnabled && !ipWhitelist.contains(clientIP)) {
            logger.warning("Access denied for IP: " + clientIP + " on path: " + exchange.getRequestURI().getPath());
            String response = "403 Forbidden";
            exchange.sendResponseHeaders(403, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            return;
        }

        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.warning("Static file not found: " + resourcePath);
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            byte[] content = is.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        } catch (IOException e) {
            logger.severe("Error serving static file: " + resourcePath + " - " + e.getMessage());
            String response = "500 Internal Server Error";
            exchange.sendResponseHeaders(500, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}