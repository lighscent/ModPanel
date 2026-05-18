package com.xl1te.modpanel.web;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.web.auth.IPBanManager;
import com.xl1te.modpanel.web.auth.IPWhitelist;
import com.xl1te.modpanel.web.auth.SessionManager;
import com.xl1te.modpanel.web.api.ApiController;
import com.xl1te.modpanel.web.auth.SessionManager.Session;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class WebServer {

    private final Main plugin;
    private final BestLogger logger;
    private Javalin app;
    private SessionManager sessionManager;

    public WebServer(Main plugin, BestLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.sessionManager = new SessionManager();
    }

    public void startWebServer() {
        int port = plugin.getConfig().getInt("web-server.port", 9999);
        boolean proxyMode = plugin.getConfig().getBoolean("web-server.proxy-mode", false);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());

        try {
            app = Javalin.create(config -> {
                config.staticFiles.add(staticFileConfig -> {
                    staticFileConfig.directory = "public";
                    staticFileConfig.location = Location.CLASSPATH;
                });
                config.http.defaultContentType = "application/json";
            });

            IPBanManager banManager = new IPBanManager(plugin, logger,
                    plugin.getDatabaseManager().getIpBanRepository());
            HtmlController htmlController = new HtmlController(logger, proxyMode);
            IPWhitelist whitelist = new IPWhitelist(banManager, plugin.getDatabaseManager(), logger, proxyMode);
            ApiController apiController = new ApiController(plugin, sessionManager, logger);

            app.before("/", whitelist);
            app.before("/{page}", whitelist);

            app.get("/", ctx -> ctx.redirect("/index"));
            app.get("/{page}", htmlController::serveHtml);

            app.get("/items/{name}", ctx -> {
                String name = ctx.pathParam("name");
                java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("assets/minecraft/" + name);
                if (is != null) {
                    ctx.contentType("image/png").result(is);
                } else {
                    ctx.status(404).result("Not found");
                }
            });

            app.post("/api/auth/login", apiController::handle);
            app.post("/api/auth/logout", apiController::handle);
            app.get("/api/stats", wrapApi(apiController));
            app.get("/api/players", wrapApi(apiController));
            app.get("/api/players/detail", wrapApi(apiController));
            app.get("/api/users", wrapApi(apiController));
            app.post("/api/users/create", wrapApi(apiController));
            app.post("/api/users/update", wrapApi(apiController));
            app.post("/api/users/delete", wrapApi(apiController));
            app.post("/api/permissions", wrapApi(apiController));
            app.get("/api/whitelist", wrapApi(apiController));

            app.start(port);
            logger.info("Web server started on port: " + port);
        } catch (Exception e) {
            logger.severe("Error during server startup: " + e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }

    private void checkApiAuth(io.javalin.http.Context ctx, ApiController apiController) {
        String auth = ctx.header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            ctx.status(401).result("{\"error\":\"Unauthorized\"}");
            return;
        }
        Session session = sessionManager.getSession(auth.substring(7));
        if (session == null) {
            ctx.status(401).result("{\"error\":\"Unauthorized\"}");
            return;
        }
        ctx.attribute("session", session);
        try {
            apiController.handle(ctx);
        } catch (Exception e) {
            logger.severe("API error: " + e.getMessage());
            ctx.status(500).result("{\"error\":\"Internal server error\"}");
        }
    }

    private io.javalin.http.Handler wrapApi(ApiController apiController) {
        return ctx -> checkApiAuth(ctx, apiController);
    }

    public void stopWebServer() {
        if (app != null) {
            app.stop();
            logger.info("Web server stopped.");
        }
    }
}