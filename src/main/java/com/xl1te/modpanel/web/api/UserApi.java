package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.database.models.User;
import com.xl1te.modpanel.database.repository.PermissionRepository;
import com.xl1te.modpanel.database.repository.UserRepository;
import com.xl1te.modpanel.web.auth.PasswordHasher;
import com.xl1te.modpanel.web.auth.SessionManager;
import io.javalin.http.Context;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Endpoints d'administration : comptes du panneau et permissions. */
public class UserApi {

    private final UserRepository users;
    private final PermissionRepository permissions;
    private final SessionManager sessionManager;
    private final ApiSupport support;

    public UserApi(Main plugin, SessionManager sessionManager, ApiSupport support) {
        this.users = plugin.getDatabaseManager().getUserRepository();
        this.permissions = plugin.getDatabaseManager().getPermissionRepository();
        this.sessionManager = sessionManager;
        this.support = support;
    }

    public void handleUsers(Context ctx) throws Exception {
        List<User> userList = users.list();
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : userList) {
            out.add(Map.of(
                    "id", u.getId(), ApiSupport.FIELD_USERNAME, u.getUsername(),
                    "role", u.getRole(), "enabled", u.isEnabled(),
                    "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "",
                    "lastLogin", u.getLastLogin() != null ? u.getLastLogin().toString() : ""));
        }
        ctx.result(support.gson().toJson(out));
    }

    public void handleUserCreate(Context ctx) throws Exception {
        Map<String, Object> body = support.jsonBody(ctx);
        String username = ApiSupport.str(body, ApiSupport.FIELD_USERNAME);
        String password = ApiSupport.str(body, ApiSupport.FIELD_PASSWORD);
        String role = ApiSupport.str(body, "role");
        if (role == null)
            role = "moderator";
        if (username == null || !ApiSupport.USERNAME_PATTERN.matcher(username).matches()) {
            ApiSupport.error(ctx, 400, "Invalid username (3-50 chars: letters, digits, _ . -)");
            return;
        }
        if (password == null || password.isBlank() || password.length() > ApiSupport.MAX_PASSWORD_LEN) {
            ApiSupport.error(ctx, 400, "Invalid password (1-128 chars)");
            return;
        }
        if (!ApiSupport.ROLE_PATTERN.matcher(role).matches()) {
            ApiSupport.error(ctx, 400, "Invalid role");
            return;
        }
        if (users.findByUsername(username).isPresent()) {
            ApiSupport.error(ctx, 409, "Username already taken");
            return;
        }

        User user = new User(0, username, PasswordHasher.hash(password), role, true, LocalDateTime.now(), null);
        int userId;
        try {
            userId = users.create(user);
        } catch (SQLException e) {
            if (DatabaseManager.isDuplicateKey(e)) {
                ApiSupport.error(ctx, 409, "Username already taken");
                return;
            }
            throw e;
        }

        permissions.grant(userId, "modpanel.stats");
        permissions.grant(userId, "modpanel.players");
        ctx.result(support.gson().toJson(Map.of(
                "ok", true, ApiSupport.FIELD_USERNAME, username)));
    }

    public void handleUserUpdate(Context ctx) throws Exception {
        Map<String, Object> body = support.jsonBody(ctx);
        int id;
        try {
            id = Integer.parseInt(String.valueOf(body.get("id")));
        } catch (NumberFormatException e) {
            ApiSupport.error(ctx, 400, "Invalid user id");
            return;
        }
        User user = users.findById(id).orElse(null);
        if (user == null) {
            ApiSupport.error(ctx, 404, "User not found");
            return;
        }

        String role = ApiSupport.str(body, "role");
        if (role == null)
            role = user.getRole();
        if (!ApiSupport.ROLE_PATTERN.matcher(role).matches()) {
            ApiSupport.error(ctx, 400, "Invalid role");
            return;
        }

        Optional<Boolean> parsedEnabled = parseEnabledFlag(
                body.getOrDefault("enabled", String.valueOf(user.isEnabled())), ctx);
        if (parsedEnabled.isEmpty())
            return;
        boolean enabled = parsedEnabled.get();

        String passwordHash = user.getPasswordHash();
        String newPassword = ApiSupport.str(body, ApiSupport.FIELD_PASSWORD);
        if (newPassword != null) {
            if (newPassword.isBlank() || newPassword.length() > ApiSupport.MAX_PASSWORD_LEN) {
                ApiSupport.error(ctx, 400, "Invalid password (1-128 chars)");
                return;
            }
            passwordHash = PasswordHasher.hash(newPassword);
        }

        users.update(new User(id, user.getUsername(), passwordHash, role, enabled, user.getCreatedAt(),
                user.getLastLogin()));
        if (newPassword != null) {
            // Mot de passe imposé par un tiers : à changer au prochain login.
            users.setPasswordChangeRequired(id);
        }
        if (newPassword != null || !enabled) {
            // Sessions existantes révoquées : mot de passe changé ou compte désactivé.
            sessionManager.removeSessionsForUser(user.getUsername());
        }
        // Rôle/permissions modifiés sans déconnexion : recharge le snapshot (no-op si révoqué).
        sessionManager.refreshUserSessions(user.getUsername(), id, role,
                permissions.getPermissions(id), enabled);
        ApiSupport.ok(ctx);
    }

    public void handleUserDelete(Context ctx) throws Exception {
        Map<String, Object> body = support.jsonBody(ctx);
        String username = ApiSupport.str(body, ApiSupport.FIELD_USERNAME);
        if (username == null || username.isBlank()) {
            ApiSupport.error(ctx, 400, "Username required");
            return;
        }
        users.delete(username);
        sessionManager.removeSessionsForUser(username);
        ApiSupport.ok(ctx);
    }

    public void handlePermissions(Context ctx) throws Exception {
        Map<String, Object> body = support.jsonBody(ctx);
        int userId;
        try {
            userId = Integer.parseInt(String.valueOf(body.get("userId")));
        } catch (NumberFormatException e) {
            ApiSupport.error(ctx, 400, "Invalid userId");
            return;
        }

        String action = ApiSupport.str(body, "action");
        if (!"grant".equals(action) && !"revoke".equals(action)) {
            ApiSupport.error(ctx, 400, "Invalid action (grant/revoke)");
            return;
        }

        String permission = ApiSupport.str(body, "permission");
        if (permission == null || !ApiSupport.PERMISSION_PATTERN.matcher(permission).matches()) {
            ApiSupport.error(ctx, 400, "Invalid permission (1-100 chars: letters, digits, . _ * -)");
            return;
        }

        User target = users.findById(userId).orElse(null);
        if (target == null) {
            ApiSupport.error(ctx, 404, "User not found");
            return;
        }

        switch (action) {
            case "grant" -> permissions.grant(userId, permission);
            case "revoke" -> permissions.revoke(userId, permission);
        }
        // Permissions modifiées : recharge le snapshot des sessions actives.
        sessionManager.refreshUserSessions(target.getUsername(), target.getId(), target.getRole(),
                permissions.getPermissions(target.getId()), target.isEnabled());
        ApiSupport.ok(ctx);
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
        ApiSupport.error(ctx, 400, "Invalid enabled flag (true/false)");
        return Optional.empty();
    }
}
