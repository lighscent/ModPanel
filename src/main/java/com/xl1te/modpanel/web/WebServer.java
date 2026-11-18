package com.xl1te.modpanel.web;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.web.auth.ConnectionLimiter;
import com.xl1te.modpanel.web.auth.IPWhitelist;
import com.xl1te.modpanel.web.auth.SessionManager;
import com.xl1te.modpanel.web.api.ApiController;
import com.xl1te.modpanel.web.api.ApiSupport;
import com.xl1te.modpanel.web.api.LiveApi;
import com.xl1te.modpanel.web.api.LiveHub;
import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.staticfiles.Location;

public class WebServer {

    private final Main plugin;
    private final BestLogger logger;
    private Javalin app;
    private SessionManager sessionManager;
    private final LiveHub liveHub;

    public WebServer(Main plugin, BestLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.sessionManager = new SessionManager();
        this.liveHub = new LiveHub(plugin, new ApiSupport(plugin, logger));
    }

    public LiveHub getLiveHub() {
        return liveHub;
    }

    public void startWebServer() {
        int port = plugin.getConfig().getInt("web-server.port", 9999);
        boolean proxyMode = plugin.getConfig().getBoolean("web-server.proxy-mode", false);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());

        try {
            ConnectionLimiter connectionLimiter = new ConnectionLimiter(
                    plugin.getConfig().getBoolean("web-server.connection-limit.enabled", true),
                    plugin.getConfig().getInt("web-server.connection-limit.ban-after", 10),
                    plugin.getConfig().getLong("web-server.connection-limit.window-seconds", 60),
                    plugin.getConfig().getLong("web-server.connection-limit.ban-duration", 600),
                    plugin.getConfig().getInt("web-server.connection-limit.permanent-ban-threshold", 2));
            HtmlController htmlController = new HtmlController(logger);
            IPWhitelist whitelist = new IPWhitelist(connectionLimiter, plugin.getDatabaseManager(), logger, proxyMode,
                    plugin.getConfig().getBoolean("ip-whitelist-enabled", true),
                    plugin.getConfig().getStringList("ip-whitelist"));
            ApiController apiController = new ApiController(plugin, sessionManager, logger, proxyMode);
            LiveApi liveApi = new LiveApi(sessionManager, liveHub, logger);

            sessionManager.start();
            liveHub.reset();
            liveHub.startHeartbeat();

            app = Javalin.create(config -> {
                config.staticFiles.add(staticFileConfig -> {
                    staticFileConfig.directory = "public";
                    staticFileConfig.location = Location.CLASSPATH;
                });
                config.http.defaultContentType = "application/json";
                // Le startup-watcher de Javalin démarre un Thread sans nom que app.stop()
                // n'arrête pas : fuite de classloader au unload/reload. Inutile ici.
                config.startup.startupWatcherEnabled = false;

                RoutesConfig routes = config.routes;
                routes.before(ctx -> {
                    ctx.header("X-Content-Type-Options", "nosniff");
                    ctx.header("X-Frame-Options", "SAMEORIGIN");
                    ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");
                    ctx.header("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
                });
                routes.before("/", whitelist);
                routes.before("/{page}", whitelist);
                routes.before(ctx -> {
                    String path = ctx.path();
                    if (path.startsWith("/api/") && !path.startsWith("/api/auth/")) {
                        whitelist.handle(ctx);
                    }
                });

                routes.get("/", ctx -> ctx.redirect("/index"));
                // /dashboard, /players, /users et /whitelist sont servis par /{page} :
                // un fichier public/html/<page>.html par page.
                routes.get("/favicon.ico", ctx -> {
                    java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("public/img/favicon.ico");
                    if (is != null) {
                        ctx.contentType("image/x-icon").result(is);
                    } else {
                        ctx.status(404).result("Not found");
                    }
                });

                routes.get("/{page}", htmlController::serveHtml);
                routes.before("/players/{name}", whitelist);
                routes.get("/players/{name}", htmlController::servePlayer);

                routes.get("/items/{name}", ctx -> {
                    String name = ctx.pathParam("name");
                    if (!name.matches("[a-z0-9_(). -]+\\.png")) {
                        ctx.status(404).result("Not found");
                        return;
                    }
                    java.io.InputStream is = getClass().getClassLoader()
                            .getResourceAsStream("assets/minecraft/" + name);
                    if (is != null) {
                        ctx.contentType("image/png").result(is);
                    } else {
                        ctx.status(404).result("Not found");
                    }
                });

                apiController.registerApiRoutes(routes);

                routes.wsBeforeUpgrade("/api/live", whitelist::handle);
                routes.ws("/api/live", ws -> {
                    ws.onConnect(liveApi::handleConnect);
                    ws.onClose(liveApi::handleClose);
                    ws.onError(liveApi::handleError);
                });
            });

            app.start(port);
            logger.info("Web server started on port: " + port);
        } catch (Exception e) {
            app = null;
            logger.severe("Error during server startup: " + e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }

    public void reload() {
        stopWebServer();
        startWebServer();
    }

    public void stopWebServer() {
        sessionManager.stop();
        liveHub.closeAll();
        if (app != null) {
            app.stop();
            app = null;
            logger.info("Web server stopped.");
        }
    }
}