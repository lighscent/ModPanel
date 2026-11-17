package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.database.models.User;
import com.xl1te.modpanel.database.repository.PermissionRepository;
import com.xl1te.modpanel.database.repository.UserRepository;
import com.xl1te.modpanel.web.ClientIp;
import com.xl1te.modpanel.web.auth.LoginRateLimiter;
import com.xl1te.modpanel.web.auth.PasswordHasher;
import com.xl1te.modpanel.web.auth.SessionManager;
import com.xl1te.modpanel.web.auth.SessionManager.Session;
import io.javalin.http.Context;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Endpoints d'authentification : login, logout, session courante, mot de passe. */
public class AuthApi {

    private final UserRepository users;
    private final PermissionRepository permissions;
    private final SessionManager sessionManager;
    private final ApiSupport support;
    private final LoginRateLimiter loginLimiter;
    private final boolean proxyMode;

    public AuthApi(Main plugin, SessionManager sessionManager, ApiSupport support,
            LoginRateLimiter loginLimiter, boolean proxyMode) {
        this.users = plugin.getDatabaseManager().getUserRepository();
        this.permissions = plugin.getDatabaseManager().getPermissionRepository();
        this.sessionManager = sessionManager;
        this.support = support;
        this.loginLimiter = loginLimiter;
        this.proxyMode = proxyMode;
    }

    public void handleLogin(Context ctx) throws Exception {
        Map<String, Object> body = support.jsonBody(ctx);
        String username = ApiSupport.str(body, ApiSupport.FIELD_USERNAME);
        String password = ApiSupport.str(body, ApiSupport.FIELD_PASSWORD);
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            ApiSupport.error(ctx, 400, "Username and password required");
            return;
        }
        if (username.length() > ApiSupport.MAX_LOGIN_NAME_LEN
                || password.length() > ApiSupport.MAX_PASSWORD_LEN) {
            ApiSupport.error(ctx, 400, "Invalid username or password format");
            return;
        }

        String key = getClientIP(ctx) + "\0" + username.toLowerCase(Locale.ROOT);
        if (loginLimiter.isBlocked(key)) {
            ctx.header("Retry-After", String.valueOf(loginLimiter.retryAfterSeconds(key)));
            ApiSupport.error(ctx, 429, "Too many login attempts, try again later");
            return;
        }

        User user = users.findByUsername(username).orElse(null);
        String hash = (user != null) ? user.getPasswordHash() : PasswordHasher.DUMMY_HASH;
        if (user == null || !user.isEnabled() || !PasswordHasher.verify(password, hash)) {
            loginLimiter.recordFailure(key);
            ApiSupport.error(ctx, 401, "Invalid credentials");
            return;
        }
        loginLimiter.reset(key);

        if (users.isPasswordChangeRequired(user.getId())) {
            ctx.status(403).result(support.gson().toJson(Map.of(
                    "error", "Password change required", "mustChangePassword", true)));
            return;
        }

        users.updateLastLogin(user.getUsername());
        Set<String> perms = permissions.getPermissions(user.getId());
        String token = sessionManager.createSession(user.getUsername(), user.getId(), user.getRole(), perms,
                user.isEnabled());
        ctx.header("Set-Cookie", SessionManager.sessionCookieHeader(token));
        ctx.result(support.gson().toJson(Map.of(
                ApiSupport.FIELD_USERNAME, user.getUsername(), "role", user.getRole())));
    }

    public void handleLogout(Context ctx) throws Exception {
        String token = SessionManager.extractToken(ctx);
        if (token != null) {
            sessionManager.removeSession(token);
        }
        ctx.header("Set-Cookie", SessionManager.clearSessionCookieHeader());
        ApiSupport.ok(ctx);
    }

    /** Identité de la session courante (posée par ApiController#handle). */
    public void handleSession(Context ctx) throws Exception {
        Session session = (Session) ctx.attribute(ApiSupport.SESSION_ATTRIBUTE);
        if (session == null || !session.enabled()) {
            ApiSupport.error(ctx, 401, "Unauthorized");
            return;
        }
        ctx.result(support.gson().toJson(Map.of(
                ApiSupport.FIELD_USERNAME, session.username(), "role", session.role())));
    }

    /**
     * Changement de mot de passe sans session (premier login forcé) : prouve la
     * connaissance du mot de passe actuel. Soumis au même limiteur que le login.
     */
    public void handlePasswordChange(Context ctx) throws SQLException {
        Map<String, Object> body = support.jsonBody(ctx);
        String username = ApiSupport.str(body, ApiSupport.FIELD_USERNAME);
        String currentPassword = ApiSupport.str(body, "currentPassword");
        String newPassword = ApiSupport.str(body, "newPassword");
        if (username == null || username.isBlank() || currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            ApiSupport.error(ctx, 400, "Username, currentPassword and newPassword required");
            return;
        }
        if (username.length() > ApiSupport.MAX_LOGIN_NAME_LEN
                || newPassword.length() > ApiSupport.MAX_PASSWORD_LEN) {
            ApiSupport.error(ctx, 400, "Invalid username or password format");
            return;
        }
        if (newPassword.equals(currentPassword)) {
            ApiSupport.error(ctx, 400, "New password must differ from the current one");
            return;
        }

        String key = getClientIP(ctx) + "\0" + username.toLowerCase(Locale.ROOT);
        if (loginLimiter.isBlocked(key)) {
            ctx.header("Retry-After", String.valueOf(loginLimiter.retryAfterSeconds(key)));
            ApiSupport.error(ctx, 429, "Too many login attempts, try again later");
            return;
        }

        User user = users.findByUsername(username).orElse(null);
        String hash = (user != null) ? user.getPasswordHash() : PasswordHasher.DUMMY_HASH;
        if (user == null || !user.isEnabled() || !PasswordHasher.verify(currentPassword, hash)) {
            loginLimiter.recordFailure(key);
            ApiSupport.error(ctx, 401, "Invalid credentials");
            return;
        }
        loginLimiter.reset(key);

        users.update(new User(user.getId(), user.getUsername(), PasswordHasher.hash(newPassword),
                user.getRole(), user.isEnabled(), user.getCreatedAt(), user.getLastLogin()));
        users.clearPasswordChangeRequired(user.getId());
        // Le mot de passe a changé : les autres sessions ne sont plus dignes de confiance.
        sessionManager.removeSessionsForUser(user.getUsername());
        ApiSupport.ok(ctx);
    }

    private String getClientIP(Context ctx) {
        return ClientIp.extract(ctx, proxyMode);
    }
}
