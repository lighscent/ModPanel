package com.xl1te.modpanel.web;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.web.auth.IPWhitelist;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.util.List;

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
        List<String> allowedIPs = plugin.getConfig().getStringList("server.whitelist.ips");

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());
        try {
            app = Javalin.create(config -> {
                config.staticFiles.add(staticFileConfig -> {
                    staticFileConfig.directory = "public";
                    staticFileConfig.location = Location.CLASSPATH;
                });
            });

            app.before(new IPWhitelist(allowedIPs, logger));

            app.get("/", ctx -> ctx.result("ModPanel Javalin server is running"));

            app.start(port);
            logger.info("Web server started on port: " + port);

            if (!allowedIPs.isEmpty()) {
                logger.info("IP whitelisting is enabled. Allowed IPs: " + allowedIPs);
            } else {
                logger.warning("IP whitelisting is active but NO IPs are allowed. ALL access will be blocked!");
            }
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
}