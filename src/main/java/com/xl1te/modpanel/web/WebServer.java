package com.xl1te.modpanel.web;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.web.auth.IPBanManager;
import com.xl1te.modpanel.web.auth.IPWhitelist;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class WebServer {

    private final Main plugin;
    private final BestLogger logger;
    private Javalin app;

    public WebServer(Main plugin, BestLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void startWebServer() {
        int port = plugin.getConfig().getInt("server.port", 9999);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());

        try {
            app = Javalin.create(config -> {
                config.staticFiles.add(staticFileConfig -> {
                    staticFileConfig.directory = "public";
                    staticFileConfig.location = Location.CLASSPATH;
                });
            });

            IPBanManager banManager = new IPBanManager(plugin, logger,
                    plugin.getDatabaseManager().getIpBanRepository());
            app.before("/", new IPWhitelist(banManager, plugin.getDatabaseManager(), logger));
            app.before("/index.html", new IPWhitelist(banManager, plugin.getDatabaseManager(), logger));

            app.get("/", ctx -> {
                String clientIP = getClientIP(ctx);
                logger.info("Received request from IP: " + clientIP);
                ctx.result("Welcome to ModPanel Web Interface!");
            });

            app.start(port);
            logger.info("Web server started on port: " + port);
        } catch (Exception e) {
            logger.severe("Error during server startup: " + e.getMessage());
            e.printStackTrace();
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }

    public void stopWebServer() {
        if (app != null) {
            app.stop();
            logger.info("Web server stopped.");
        }
    }

    private String getClientIP(io.javalin.http.Context ctx) {
        String xForwardedFor = ctx.header("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return ctx.req().getRemoteAddr();
    }
}
