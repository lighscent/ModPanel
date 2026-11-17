package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.web.auth.IPBanException;
import com.xl1te.modpanel.web.auth.LoginRateLimiter;
import com.xl1te.modpanel.web.auth.SessionManager;
import com.xl1te.modpanel.web.auth.SessionManager.Session;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Table de routage de l'API : ajouter un endpoint = ajouter une ligne.
 * Auth de session, permissions et mapping d'erreurs sont centralisés ici ;
 * le métier est réparti dans AuthApi, GameApi, UserApi et WhitelistApi.
 */
public class ApiController implements Handler {

    private static final String PERM_USERS_MANAGE = "modpanel.users.manage";

    private record ApiEndpoint(HandlerType method, String path, boolean auth, String permission, Handler handler) {
    }

    private final SessionManager sessionManager;
    private final BestLogger logger;
    private final List<ApiEndpoint> apiEndpoints;
    private final Map<String, ApiEndpoint> endpointsByPath;

    public ApiController(Main plugin, SessionManager sessionManager, BestLogger logger, boolean proxyMode) {
        this.sessionManager = sessionManager;
        this.logger = logger;
        ApiSupport support = new ApiSupport(plugin, logger);

        LoginRateLimiter loginLimiter = new LoginRateLimiter(
                plugin.getConfig().getBoolean("web-server.login-limit.enabled", true),
                plugin.getConfig().getInt("web-server.login-limit.max-attempts", 5),
                plugin.getConfig().getLong("web-server.login-limit.window-seconds", 300));

        AuthApi auth = new AuthApi(plugin, sessionManager, support, loginLimiter, proxyMode);
        GameApi game = new GameApi(plugin, support);
        UserApi users = new UserApi(plugin, sessionManager, support);
        WhitelistApi whitelist = new WhitelistApi(plugin, support);

        this.apiEndpoints = List.of(
                new ApiEndpoint(HandlerType.POST, "/api/auth/login", false, null, auth::handleLogin),
                new ApiEndpoint(HandlerType.POST, "/api/auth/logout", false, null, auth::handleLogout),
                new ApiEndpoint(HandlerType.POST, "/api/auth/password", false, null, auth::handlePasswordChange),
                new ApiEndpoint(HandlerType.GET, "/api/auth/session", true, null, auth::handleSession),
                new ApiEndpoint(HandlerType.GET, "/api/stats", true, "modpanel.stats", game::handleStats),
                new ApiEndpoint(HandlerType.GET, "/api/players", true, "modpanel.players", game::handlePlayers),
                new ApiEndpoint(HandlerType.GET, "/api/players/detail", true, "modpanel.player.detail",
                        game::handlePlayerDetail),
                new ApiEndpoint(HandlerType.GET, "/api/users", true, "modpanel.users.view", users::handleUsers),
                new ApiEndpoint(HandlerType.POST, "/api/users/create", true, PERM_USERS_MANAGE,
                        users::handleUserCreate),
                new ApiEndpoint(HandlerType.POST, "/api/users/update", true, PERM_USERS_MANAGE,
                        users::handleUserUpdate),
                new ApiEndpoint(HandlerType.POST, "/api/users/delete", true, PERM_USERS_MANAGE,
                        users::handleUserDelete),
                new ApiEndpoint(HandlerType.POST, "/api/permissions", true, PERM_USERS_MANAGE,
                        users::handlePermissions),
                new ApiEndpoint(HandlerType.GET, "/api/whitelist", true, "modpanel.whitelist",
                        whitelist::handleWhitelist));

        Map<String, ApiEndpoint> byPath = new HashMap<>();
        for (ApiEndpoint endpoint : apiEndpoints) {
            if (byPath.putIfAbsent(endpoint.path(), endpoint) != null) {
                throw new IllegalStateException("Duplicate API path: " + endpoint.path());
            }
        }
        this.endpointsByPath = Map.copyOf(byPath);
    }

    public void registerApiRoutes(RoutesConfig routes) {
        for (ApiEndpoint endpoint : apiEndpoints) {
            routes.addHttpHandler(endpoint.method(), endpoint.path(), this::handle);
        }
    }

    @Override
    public void handle(Context ctx) {
        String path = ctx.path();
        ApiEndpoint endpoint = endpointsByPath.get(path);
        if (endpoint == null) {
            ApiSupport.error(ctx, 404, "Not found");
            return;
        }
        try {
            if (endpoint.auth()) {
                Session session = getAuthenticatedSession(ctx);
                if (session == null) {
                    ApiSupport.error(ctx, 401, "Unauthorized");
                    return;
                }
                ctx.attribute(ApiSupport.SESSION_ATTRIBUTE, session);
                if (endpoint.permission() != null && !hasPermission(session, endpoint.permission())) {
                    ApiSupport.error(ctx, 403, "Permission denied");
                    return;
                }
            }
            endpoint.handler().handle(ctx);
        } catch (IPBanException e) {
            ApiSupport.error(ctx, 429, e.getMessage());
        } catch (TimeoutException e) {
            ApiSupport.error(ctx, 503, "Server busy, try again");
        } catch (Exception e) {
            logger.severe("API error on " + path + ": " + e.getMessage());
            ApiSupport.error(ctx, 500, "Internal server error");
        }
    }

    private Session getAuthenticatedSession(Context ctx) {
        String token = SessionManager.extractToken(ctx);
        if (token == null)
            return null;
        return sessionManager.getSession(token);
    }

    private boolean hasPermission(Session session, String permission) {
        if (!session.enabled())
            return false;
        if (session.role().equals("admin") || session.role().equals("owner"))
            return true;
        return session.permissions().contains(permission);
    }
}
