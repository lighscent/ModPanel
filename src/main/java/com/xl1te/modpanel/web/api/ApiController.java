package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.database.models.ServerStats;
import com.xl1te.modpanel.database.models.User;
import com.xl1te.modpanel.web.ClientIp;
import com.xl1te.modpanel.web.auth.PasswordHasher;
import com.xl1te.modpanel.web.auth.SessionManager;
import com.xl1te.modpanel.web.auth.SessionManager.Session;
import com.xl1te.modpanel.web.auth.IPBanException;
import com.xl1te.modpanel.web.auth.LoginRateLimiter;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.utils.GsonProvider;
import com.google.gson.reflect.TypeToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.config.RoutesConfig;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

public class ApiController implements Handler {

    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_PASSWORD = "password";

    private static final String PERM_USERS_MANAGE = "modpanel.users.manage";

    /** Table de routage unique : ajouter un endpoint = ajouter une ligne. */
    public record ApiEndpoint(HandlerType method, String path, boolean auth, String permission, Handler handler) {
    }

    private final List<ApiEndpoint> apiEndpoints = List.of(
            new ApiEndpoint(HandlerType.POST, "/api/auth/login", false, null, this::handleLogin),
            new ApiEndpoint(HandlerType.POST, "/api/auth/logout", false, null, this::handleLogout),
            new ApiEndpoint(HandlerType.POST, "/api/auth/password", false, null, this::handlePasswordChange),
            new ApiEndpoint(HandlerType.GET, "/api/auth/session", true, null, this::handleSession),
            new ApiEndpoint(HandlerType.GET, "/api/stats", true, "modpanel.stats", this::handleStats),
            new ApiEndpoint(HandlerType.GET, "/api/players", true, "modpanel.players", this::handlePlayers),
            new ApiEndpoint(HandlerType.GET, "/api/players/detail", true, "modpanel.player.detail",
                    this::handlePlayerDetail),
            new ApiEndpoint(HandlerType.GET, "/api/users", true, "modpanel.users.view", this::handleUsers),
            new ApiEndpoint(HandlerType.POST, "/api/users/create", true, PERM_USERS_MANAGE, this::handleUserCreate),
            new ApiEndpoint(HandlerType.POST, "/api/users/update", true, PERM_USERS_MANAGE, this::handleUserUpdate),
            new ApiEndpoint(HandlerType.POST, "/api/users/delete", true, PERM_USERS_MANAGE, this::handleUserDelete),
            new ApiEndpoint(HandlerType.POST, "/api/permissions", true, PERM_USERS_MANAGE, this::handlePermissions),
            new ApiEndpoint(HandlerType.GET, "/api/whitelist", true, "modpanel.whitelist", this::handleWhitelist));

    private final Map<String, ApiEndpoint> endpointsByPath = new HashMap<>();

    private static final Type BODY_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    /** Délai max d'attente du thread principal (collecte Bukkit). */
    private static final long SYNC_TIMEOUT_SECONDS = 5;

    // Bornes alignées sur le schéma (users.username VARCHAR(50), role VARCHAR(20),
    // user_permissions.permission VARCHAR(100)) + garde-fou mot de passe.
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{3,50}");
    private static final Pattern ROLE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,20}");
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("[A-Za-z0-9._*-]{1,100}");
    private static final int MAX_PASSWORD_LEN = 128;
    private static final int MAX_LOGIN_NAME_LEN = 50;

    private final Main plugin;
    private final SessionManager sessionManager;
    private final BestLogger logger;
    private final boolean proxyMode;
    private final LoginRateLimiter loginLimiter;

    public ApiController(Main plugin, SessionManager sessionManager, BestLogger logger, boolean proxyMode) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.logger = logger;
        this.proxyMode = proxyMode;
        this.loginLimiter = new LoginRateLimiter(
                plugin.getConfig().getBoolean("web-server.login-limit.enabled", true),
                plugin.getConfig().getInt("web-server.login-limit.max-attempts", 5),
                plugin.getConfig().getLong("web-server.login-limit.window-seconds", 300));
        for (ApiEndpoint endpoint : apiEndpoints) {
            endpointsByPath.put(endpoint.path(), endpoint);
        }
    }

    /** Enregistre toutes les routes API d'un coup (EndpointGroup unique). */
    public void registerApiRoutes(RoutesConfig routes) {
        for (ApiEndpoint endpoint : apiEndpoints) {
            if (endpoint.method() == HandlerType.GET) {
                routes.get(endpoint.path(), this::handle);
            } else if (endpoint.method() == HandlerType.POST) {
                routes.post(endpoint.path(), this::handle);
            } else {
                throw new IllegalArgumentException("Unsupported method: " + endpoint.method());
            }
        }
    }

    @Override
    public void handle(Context ctx) {
        String path = ctx.path();
        ApiEndpoint endpoint = endpointsByPath.get(path);
        if (endpoint == null) {
            ctx.status(404).result("{\"error\":\"Not found\"}");
            return;
        }
        try {
            if (endpoint.auth()) {
                Session session = getAuthenticatedSession(ctx);
                if (session == null) {
                    ctx.status(401).result("{\"error\":\"Unauthorized\"}");
                    return;
                }
                ctx.attribute("session", session);
                if (endpoint.permission() != null && !hasPermission(session, endpoint.permission())) {
                    ctx.status(403).result("{\"error\":\"Permission denied\"}");
                    return;
                }
            }
            endpoint.handler().handle(ctx);
        } catch (IPBanException e) {
            ctx.status(429).result("{\"error\":\"" + e.getMessage() + "\"}");
        } catch (TimeoutException e) {
            ctx.status(503).result("{\"error\":\"Server busy, try again\"}");
        } catch (Exception e) {
            logger.severe("API error on " + path + ": " + e.getMessage());
            ctx.status(500).result("{\"error\":\"Internal server error\"}");
        }
    }

    private void handleLogin(Context ctx) throws Exception {
        Map<String, Object> body = jsonBody(ctx);
        Object rawUsername = body.get(FIELD_USERNAME);
        Object rawPassword = body.get(FIELD_PASSWORD);
        String username = rawUsername == null ? null : rawUsername.toString();
        String password = rawPassword == null ? null : rawPassword.toString();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            ctx.status(400).result("{\"error\":\"Username and password required\"}");
            return;
        }
        if (username.length() > MAX_LOGIN_NAME_LEN || password.length() > MAX_PASSWORD_LEN) {
            ctx.status(400).result("{\"error\":\"Invalid username or password format\"}");
            return;
        }
        String key = getClientIP(ctx) + "\0" + username.toLowerCase(java.util.Locale.ROOT);
        if (loginLimiter.isBlocked(key)) {
            ctx.header("Retry-After", String.valueOf(loginLimiter.retryAfterSeconds(key)));
            ctx.status(429).result("{\"error\":\"Too many login attempts, try again later\"}");
            return;
        }
        var repo = plugin.getDatabaseManager().getUserRepository();
        var userOpt = repo.findByUsername(username);
        User user = userOpt.orElse(null);
        // Toujours vérifier un hash (même factice) : temps de réponse uniforme,
        // un seul message d'échec pour inconnu, désactivé ou mot de passe faux.
        String hash = (user != null) ? user.getPasswordHash() : PasswordHasher.DUMMY_HASH;
        if (user == null || !user.isEnabled() || !PasswordHasher.verify(password, hash)) {
            loginLimiter.recordFailure(key);
            ctx.status(401).result("{\"error\":\"Invalid credentials\"}");
            return;
        }
        loginLimiter.reset(key);
        if (repo.isPasswordChangeRequired(user.getId())) {
            ctx.status(403).result(gson().toJson(Map.of(
                    "error", "Password change required", "mustChangePassword", true)));
            return;
        }
        repo.updateLastLogin(user.getUsername());
        Set<String> perms = plugin.getDatabaseManager().getPermissionRepository().getPermissions(user.getId());
        String token = sessionManager.createSession(user.getUsername(), user.getId(), user.getRole(), perms,
                user.isEnabled());
        ctx.header("Set-Cookie", SessionManager.sessionCookieHeader(token));
        ctx.result(gson().toJson(Map.of(FIELD_USERNAME, user.getUsername(), "role", user.getRole())));
    }

    private void handleLogout(Context ctx) throws Exception {
        String token = SessionManager.extractToken(ctx);
        if (token != null) {
            sessionManager.removeSession(token);
        }
        ctx.header("Set-Cookie", SessionManager.clearSessionCookieHeader());
        ctx.result("{\"ok\":true}");
    }

    /** Identité de la session courante (posée par handle(), snapshot au login). */
    private void handleSession(Context ctx) throws Exception {
        Session session = (Session) ctx.attribute("session");
        if (session == null || !session.enabled()) {
            ctx.status(401).result("{\"error\":\"Unauthorized\"}");
            return;
        }
        ctx.result(gson().toJson(Map.of(FIELD_USERNAME, session.username(), "role", session.role())));
    }

    /**
     * Changement de mot de passe sans session (premier login forcé) : prouve la
     * connaissance du mot de passe actuel. Soumis au même limiteur que le login.
     */
    private void handlePasswordChange(Context ctx) throws SQLException {
        Map<String, Object> body = jsonBody(ctx);
        String username = str(body, FIELD_USERNAME);
        String currentPassword = str(body, "currentPassword");
        String newPassword = str(body, "newPassword");
        if (username == null || username.isBlank() || currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            ctx.status(400).result("{\"error\":\"Username, currentPassword and newPassword required\"}");
            return;
        }
        if (username.length() > MAX_LOGIN_NAME_LEN || newPassword.length() > MAX_PASSWORD_LEN) {
            ctx.status(400).result("{\"error\":\"Invalid username or password format\"}");
            return;
        }
        if (newPassword.equals(currentPassword)) {
            ctx.status(400).result("{\"error\":\"New password must differ from the current one\"}");
            return;
        }
        String key = getClientIP(ctx) + "\0" + username.toLowerCase(java.util.Locale.ROOT);
        if (loginLimiter.isBlocked(key)) {
            ctx.header("Retry-After", String.valueOf(loginLimiter.retryAfterSeconds(key)));
            ctx.status(429).result("{\"error\":\"Too many login attempts, try again later\"}");
            return;
        }
        var repo = plugin.getDatabaseManager().getUserRepository();
        User user = repo.findByUsername(username).orElse(null);
        String hash = (user != null) ? user.getPasswordHash() : PasswordHasher.DUMMY_HASH;
        if (user == null || !user.isEnabled() || !PasswordHasher.verify(currentPassword, hash)) {
            loginLimiter.recordFailure(key);
            ctx.status(401).result("{\"error\":\"Invalid credentials\"}");
            return;
        }
        loginLimiter.reset(key);
        repo.update(new User(user.getId(), user.getUsername(), PasswordHasher.hash(newPassword),
                user.getRole(), user.isEnabled(), user.getCreatedAt(), user.getLastLogin()));
        repo.clearPasswordChangeRequired(user.getId());
        // Le mot de passe a changé : les autres sessions ne sont plus dignes de confiance.
        sessionManager.removeSessionsForUser(user.getUsername());
        ctx.result("{\"ok\":true}");
    }

    private void handleStats(Context ctx) throws Exception {
        ServerStats stats = callSync(() -> new ServerStats(
                plugin.getServer().getOnlinePlayers().size(),
                plugin.getServer().getMaxPlayers(),
                getTPS(),
                plugin.getServer().getVersion(),
                plugin.getServer().getName()));
        ctx.result(gson().toJson(stats));
    }

    private void handlePlayers(Context ctx) throws Exception {
        List<Map<String, Object>> players = callSync(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                list.add(PlayerData.summary(player, logger::processMessage));
            }
            return list;
        });
        ctx.result(gson().toJson(players));
    }

    private void handlePlayerDetail(Context ctx) throws Exception {
        String uuidStr = ctx.queryParam("uuid");
        if (uuidStr == null || uuidStr.isBlank()) {
            ctx.status(400).result("{\"error\":\"uuid query param required\"}");
            return;
        }
        UUID uuid = tryParseUuid(uuidStr);
        if (uuid == null) {
            ctx.status(400).result("{\"error\":\"Invalid UUID\"}");
            return;
        }
        Map<String, Object> detail = callSync(() -> {
            Player player = plugin.getServer().getPlayer(uuid);
            return player == null ? null : PlayerData.detail(player, logger::processMessage);
        });
        if (detail == null) {
            ctx.status(404).result("{\"error\":\"Player not found or offline\"}");
            return;
        }
        ctx.result(gson().toJson(detail));
    }

    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void handleUsers(Context ctx) throws Exception {
        List<User> users = plugin.getDatabaseManager().getUserRepository().list();
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users) {
            out.add(Map.of(
                    "id", u.getId(), FIELD_USERNAME, u.getUsername(),
                    "role", u.getRole(), "enabled", u.isEnabled(),
                    "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "",
                    "lastLogin", u.getLastLogin() != null ? u.getLastLogin().toString() : ""));
        }
        ctx.result(gson().toJson(out));
    }

    private void handleUserCreate(Context ctx) throws Exception {
        Map<String, Object> body = jsonBody(ctx);
        Object rawUsername = body.get(FIELD_USERNAME);
        Object rawPassword = body.get(FIELD_PASSWORD);
        String username = rawUsername == null ? null : rawUsername.toString();
        String password = rawPassword == null ? null : rawPassword.toString();
        String role = str(body, "role");
        if (role == null)
            role = "moderator";
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            ctx.status(400).result("{\"error\":\"Invalid username (3-50 chars: letters, digits, _ . -)\"}");
            return;
        }
        if (password == null || password.isBlank() || password.length() > MAX_PASSWORD_LEN) {
            ctx.status(400).result("{\"error\":\"Invalid password (1-128 chars)\"}");
            return;
        }
        if (!ROLE_PATTERN.matcher(role).matches()) {
            ctx.status(400).result("{\"error\":\"Invalid role\"}");
            return;
        }
        var users = plugin.getDatabaseManager().getUserRepository();
        if (users.findByUsername(username).isPresent()) {
            ctx.status(409).result("{\"error\":\"Username already taken\"}");
            return;
        }
        User user = new User(0, username, PasswordHasher.hash(password), role, true, LocalDateTime.now(), null);
        int userId;
        try {
            userId = users.create(user);
        } catch (java.sql.SQLException e) {
            if (com.xl1te.modpanel.database.DatabaseManager.isDuplicateKey(e)) {
                ctx.status(409).result("{\"error\":\"Username already taken\"}");
                return;
            }
            throw e;
        }
        var perms = plugin.getDatabaseManager().getPermissionRepository();
        perms.grant(userId, "modpanel.stats");
        perms.grant(userId, "modpanel.players");
        ctx.result(gson().toJson(Map.of("ok", true, FIELD_USERNAME, username)));
    }

    private void handleUserUpdate(Context ctx) throws Exception {
        Map<String, Object> body = jsonBody(ctx);
        int id;
        try {
            id = Integer.parseInt(String.valueOf(body.get("id")));
        } catch (NumberFormatException e) {
            ctx.status(400).result("{\"error\":\"Invalid user id\"}");
            return;
        }
        var repo = plugin.getDatabaseManager().getUserRepository();
        User user = repo.findById(id).orElse(null);
        if (user == null) {
            ctx.status(404).result("{\"error\":\"User not found\"}");
            return;
        }
        String role = str(body, "role");
        if (role == null)
            role = user.getRole();
        if (!ROLE_PATTERN.matcher(role).matches()) {
            ctx.status(400).result("{\"error\":\"Invalid role\"}");
            return;
        }
        boolean enabled;
        Optional<Boolean> parsedEnabled = parseEnabledFlag(body.getOrDefault("enabled", String.valueOf(user.isEnabled())), ctx);
        if (parsedEnabled.isEmpty())
            return;
        enabled = parsedEnabled.get();
        String passwordHash = user.getPasswordHash();
        String newPassword = str(body, FIELD_PASSWORD);
        if (newPassword != null) {
            if (newPassword.isBlank() || newPassword.length() > MAX_PASSWORD_LEN) {
                ctx.status(400).result("{\"error\":\"Invalid password (1-128 chars)\"}");
                return;
            }
            passwordHash = PasswordHasher.hash(newPassword);
        }
        repo.update(new User(id, user.getUsername(), passwordHash, role, enabled, user.getCreatedAt(),
                user.getLastLogin()));
        if (newPassword != null) {
            // Mot de passe imposé par un tiers : à changer au prochain login.
            repo.setPasswordChangeRequired(id);
        }
        if (newPassword != null || !enabled) {
            // Sessions existantes révoquées : mot de passe changé ou compte désactivé.
            sessionManager.removeSessionsForUser(user.getUsername());
        }
        // Rôle/permissions modifiés sans déconnexion : recharge le snapshot (no-op si révoqué).
        sessionManager.refreshUserSessions(user.getUsername(), id, role,
                plugin.getDatabaseManager().getPermissionRepository().getPermissions(id), enabled);
        ctx.result("{\"ok\":true}");
    }

    private Optional<Boolean> parseEnabledFlag(Object rawEnabled, Context ctx) {
        if (rawEnabled instanceof Boolean b) {
            return Optional.of(b);
        }
        if (rawEnabled != null
                && (rawEnabled.toString().equalsIgnoreCase("true")
                    || rawEnabled.toString().equalsIgnoreCase("false"))) {
            return Optional.of(Boolean.parseBoolean(rawEnabled.toString()));
        }
        ctx.status(400).result("{\"error\":\"Invalid enabled flag (true/false)\"}");
        return Optional.empty();
    }

    private void handleUserDelete(Context ctx) throws Exception {
        Map<String, Object> body = jsonBody(ctx);
        String username = str(body, FIELD_USERNAME);
        if (username == null || username.isBlank()) {
            ctx.status(400).result("{\"error\":\"Username required\"}");
            return;
        }
        plugin.getDatabaseManager().getUserRepository().delete(username);
        sessionManager.removeSessionsForUser(username);
        ctx.result("{\"ok\":true}");
    }

    private void handlePermissions(Context ctx) throws Exception {
        Map<String, Object> body = jsonBody(ctx);
        int userId;
        try {
            userId = Integer.parseInt(String.valueOf(body.get("userId")));
        } catch (NumberFormatException e) {
            ctx.status(400).result("{\"error\":\"Invalid userId\"}");
            return;
        }
        String action = str(body, "action");
        if (!"grant".equals(action) && !"revoke".equals(action)) {
            ctx.status(400).result("{\"error\":\"Invalid action (grant/revoke)\"}");
            return;
        }
        String permission = str(body, "permission");
        if (permission == null || !PERMISSION_PATTERN.matcher(permission).matches()) {
            ctx.status(400).result("{\"error\":\"Invalid permission (1-100 chars: letters, digits, . _ * -)\"}");
            return;
        }
        User target = plugin.getDatabaseManager().getUserRepository().findById(userId).orElse(null);
        if (target == null) {
            ctx.status(404).result("{\"error\":\"User not found\"}");
            return;
        }
        var perms = plugin.getDatabaseManager().getPermissionRepository();
        switch (action) {
            case "grant" -> perms.grant(userId, permission);
            case "revoke" -> perms.revoke(userId, permission);
        }
        // Permissions modifiées : recharge le snapshot des sessions actives.
        sessionManager.refreshUserSessions(target.getUsername(), target.getId(), target.getRole(),
                perms.getPermissions(target.getId()), target.isEnabled());
        ctx.result("{\"ok\":true}");
    }

    private void handleWhitelist(Context ctx) throws Exception {
        var repo = plugin.getDatabaseManager().getWebWhitelistRepository();
        ctx.result(gson().toJson(repo.list()));
    }

    private Session getAuthenticatedSession(Context ctx) {
        String token = SessionManager.extractToken(ctx);
        if (token == null) return null;
        return sessionManager.getSession(token);
    }

    private String getClientIP(Context ctx) {
        return ClientIp.extract(ctx, proxyMode);
    }

    /** Contrôle sur le snapshot de session (rôle, permissions) : 0 requête DB. */
    private boolean hasPermission(Session session, String permission) {
        if (!session.enabled())
            return false;
        if (session.role().equals("admin") || session.role().equals("owner"))
            return true;
        return session.permissions().contains(permission);
    }

    /**
     * Exécute la collecte sur le thread principal Bukkit et attend le résultat.
     * L'API Bukkit n'est pas thread-safe : les handlers Javalin tournent sur des
     * threads Jetty et ne doivent jamais toucher joueurs, mondes ou inventaires.
     */
    private <T> T callSync(Callable<T> task) throws Exception {
        Future<T> future = plugin.getServer().getScheduler().callSyncMethod(plugin, task);
        try {
            return future.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private double getTPS() {
        try {
            Object provider = plugin.getServer().getClass().getMethod("getTPS").invoke(plugin.getServer());
            if (provider instanceof double[] arr)
                return arr[0];
            if (provider instanceof List<?> list && !list.isEmpty()) {
                Object val = list.get(0);
                if (val instanceof Double)
                    return (Double) val;
            }
            return 20.0;
        } catch (Exception e) {
            return 20.0;
        }
    }

    private Map<String, Object> jsonBody(Context ctx) {
        // Corps absent ou JSON invalide : map vide pour des 400 explicites, jamais 500.
        try {
            Map<String, Object> body = ctx.bodyAsClass(BODY_TYPE);
            return body != null ? body : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Valeur string d'un champ JSON, ou null si absent/nul. */
    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private com.google.gson.Gson gson() {
        return GsonProvider.get();
    }
}
