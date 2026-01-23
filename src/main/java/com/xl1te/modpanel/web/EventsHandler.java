package com.xl1te.modpanel.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.xl1te.modpanel.utils.ColoredLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class EventsHandler implements HttpHandler {
    private final ColoredLogger logger;
    private final boolean ipWhitelistEnabled;
    private final List<String> ipWhitelist;
    private static final Set<HttpExchange> clients = Collections.synchronizedSet(new HashSet<>());

    public EventsHandler(ColoredLogger logger, boolean ipWhitelistEnabled, List<String> ipWhitelist) {
        this.logger = logger;
        this.ipWhitelistEnabled = ipWhitelistEnabled;
        this.ipWhitelist = ipWhitelist;

        // Keep-alive ping to prevent timeouts
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            broadcast("ping");
        }, 15, 15, TimeUnit.SECONDS);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        if (ipWhitelistEnabled && !ipWhitelist.contains(clientIp)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        clients.add(exchange);
    }

    public static void broadcast(String data) {
        synchronized (clients) {
            Iterator<HttpExchange> it = clients.iterator();
            while (it.hasNext()) {
                HttpExchange client = it.next();
                try {
                    OutputStream os = client.getResponseBody();
                    os.write(("data: " + data + "\n\n").getBytes());
                    os.flush();
                } catch (IOException e) {
                    it.remove();
                }
            }
        }
    }
}
