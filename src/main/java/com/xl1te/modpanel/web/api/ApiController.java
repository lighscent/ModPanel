package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.database.models.ServerStats;
import com.xl1te.modpanel.database.models.User;
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

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ApiController implements Handler {

    private static final Type BODY_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    /** Délai max d'attente du thread principal (collecte Bukkit). */
    private static final long SYNC_TIMEOUT_SECONDS = 5;

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
    }

    @Override
    public void handle(Context ctx) {
        String path = ctx.path();
        try {
            switch (path) {
                case "/api/auth/login" -> handleLogin(ctx);
                case "/api/auth/logout" -> handleLogout(ctx);
                case "/api/stats" -> handleStats(ctx);
                case "/api/players" -> handlePlayers(ctx);
                case "/api/players/detail" -> handlePlayerDetail(ctx);
                case "/api/users" -> handleUsers(ctx);
                case "/api/users/create" -> handleUserCreate(ctx);
                case "/api/users/update" -> handleUserUpdate(ctx);
                case "/api/users/delete" -> handleUserDelete(ctx);
                case "/api/permissions" -> handlePermissions(ctx);
                case "/api/whitelist" -> handleWhitelist(ctx);
                default -> ctx.status(404).result("{\"error\":\"Not found\"}");
            }
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
        Object rawUsername = body.get("username");
        Object rawPassword = body.get("password");
        String username = rawUsername == null ? null : rawUsername.toString();
        String password = rawPassword == null ? null : rawPassword.toString();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            ctx.status(400).result("{\"error\":\"Username and password required\"}");
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
        repo.updateLastLogin(user.getUsername());
        String token = sessionManager.createSession(user.getUsername());
        ctx.result("{\"token\":\"" + token + "\",\"username\":\"" + user.getUsername() + "\",\"role\":\""
                + user.getRole() + "\"}");
    }

    private void handleLogout(Context ctx) throws Exception {
        String auth = ctx.header("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            sessionManager.removeSession(auth.substring(7));
        }
        ctx.result("{\"ok\":true}");
    }

    private void handleStats(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.stats"))
            return;
        ServerStats stats = callSync(() -> new ServerStats(
                plugin.getServer().getOnlinePlayers().size(),
                plugin.getServer().getMaxPlayers(),
                getTPS(),
                plugin.getServer().getVersion(),
                plugin.getServer().getName()));
        ctx.result(gson().toJson(stats));
    }

    private void handlePlayers(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.players"))
            return;
        List<Map<String, Object>> players = callSync(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", player.getName());
                p.put("uuid", player.getUniqueId().toString());
                p.put("ip", player.getAddress().getAddress().getHostAddress());
                p.put("world", player.getWorld().getName());
                p.put("health", (int) player.getHealth());
                p.put("maxHealth", (int) getMaxHealth(player));
                p.put("gamemode", player.getGameMode().name());
                p.put("ping", getPlayerPing(player));
                p.put("locale", player.getLocale());
                p.put("displayName", player.getDisplayName());
                p.put("x", (int) player.getLocation().getX());
                p.put("y", (int) player.getLocation().getY());
                p.put("z", (int) player.getLocation().getZ());
                list.add(p);
            }
            return list;
        });
        ctx.result(gson().toJson(players));
    }

    private void handlePlayerDetail(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.player.detail"))
            return;
        String uuidStr = ctx.queryParam("uuid");
        if (uuidStr == null || uuidStr.isBlank()) {
            ctx.status(400).result("{\"error\":\"uuid query param required\"}");
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            ctx.status(400).result("{\"error\":\"Invalid UUID\"}");
            return;
        }
        Map<String, Object> detail = callSync(() -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null)
                return null;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", player.getName());
            d.put("uuid", player.getUniqueId().toString());
            d.put("displayName", player.getDisplayName());
            d.put("ip", player.getAddress().getAddress().getHostAddress());
            d.put("world", player.getWorld().getName());
            d.put("x", player.getLocation().getX());
            d.put("y", player.getLocation().getY());
            d.put("z", player.getLocation().getZ());
            d.put("yaw", (double) player.getLocation().getYaw());
            d.put("pitch", (double) player.getLocation().getPitch());
            d.put("health", player.getHealth());
            d.put("maxHealth", getMaxHealth(player));
            d.put("food", player.getFoodLevel());
            d.put("saturation", (double) player.getSaturation());
            d.put("gamemode", player.getGameMode().name());
            d.put("ping", getPlayerPing(player));
            d.put("locale", player.getLocale());
            d.put("level", player.getLevel());
            d.put("exp", player.getExp());
            d.put("totalExperience", player.getTotalExperience());
            d.put("walkSpeed", player.getWalkSpeed());
            d.put("flySpeed", player.getFlySpeed());
            d.put("isFlying", player.isFlying());
            d.put("isOp", player.isOp());

            // Potion effects
            List<Map<String, Object>> effects = new ArrayList<>();
            player.getActivePotionEffects().forEach(effect -> {
                Map<String, Object> eff = new LinkedHashMap<>();
                eff.put("type", effect.getType().getName());
                eff.put("amplifier", effect.getAmplifier());
                eff.put("duration", effect.getDuration() / 20); // ticks to seconds
                effects.add(eff);
            });
            d.put("potionEffects", effects);

            // Inventory
            PlayerInventory inv = player.getInventory();
            List<Map<String, Object>> inventory = new ArrayList<>();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    Map<String, Object> slot = new LinkedHashMap<>();
                    slot.put("slot", i);
                    slot.put("type", item.getType().name());
                    slot.put("amount", item.getAmount());
                    inventory.add(slot);
                }
            }
            d.put("inventory", inventory);

            // Armor
            List<Map<String, Object>> armor = new ArrayList<>();
            for (ItemStack a : inv.getArmorContents()) {
                if (a != null && !a.getType().isAir()) {
                    Map<String, Object> piece = new LinkedHashMap<>();
                    piece.put("type", a.getType().name());
                    piece.put("amount", a.getAmount());
                    armor.add(piece);
                }
            }
            d.put("armor", armor);

            // Offhand
            ItemStack offhand = inv.getItemInOffHand();
            if (offhand != null && !offhand.getType().isAir()) {
                d.put("offhand", offhand.getType().name());
            }
            return d;
        });
        if (detail == null) {
            ctx.status(404).result("{\"error\":\"Player not found or offline\"}");
            return;
        }
        ctx.result(gson().toJson(detail));
    }

    private void handleUsers(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.view"))
            return;
        List<User> users = plugin.getDatabaseManager().getUserRepository().list();
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users) {
            out.add(Map.of(
                    "id", u.getId(), "username", u.getUsername(),
                    "role", u.getRole(), "enabled", u.isEnabled(),
                    "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "",
                    "lastLogin", u.getLastLogin() != null ? u.getLastLogin().toString() : ""));
        }
        ctx.result(gson().toJson(out));
    }

    private void handleUserCreate(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.manage"))
            return;
        Map<String, Object> body = jsonBody(ctx);
        Object rawUsername = body.get("username");
        Object rawPassword = body.get("password");
        String username = rawUsername == null ? null : rawUsername.toString();
        String password = rawPassword == null ? null : rawPassword.toString();
        String role = body.getOrDefault("role", "moderator").toString();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            ctx.status(400).result("{\"error\":\"Username and password required\"}");
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
        ctx.result("{\"ok\":true,\"username\":\"" + username + "\"}");
    }

    private void handleUserUpdate(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.manage"))
            return;
        Map<String, Object> body = jsonBody(ctx);
        int id = Integer.parseInt(body.get("id").toString());
        var repo = plugin.getDatabaseManager().getUserRepository();
        User user = repo.findById(id).orElse(null);
        if (user == null) {
            ctx.status(404).result("{\"error\":\"User not found\"}");
            return;
        }
        String role = body.getOrDefault("role", user.getRole()).toString();
        boolean enabled = Boolean
                .parseBoolean(body.getOrDefault("enabled", String.valueOf(user.isEnabled())).toString());
        String passwordHash = user.getPasswordHash();
        if (body.containsKey("password") && !body.get("password").toString().isBlank()) {
            passwordHash = PasswordHasher.hash(body.get("password").toString());
        }
        repo.update(new User(id, user.getUsername(), passwordHash, role, enabled, user.getCreatedAt(),
                user.getLastLogin()));
        ctx.result("{\"ok\":true}");
    }

    private void handleUserDelete(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.manage"))
            return;
        Map<String, Object> body = jsonBody(ctx);
        String username = body.get("username").toString();
        plugin.getDatabaseManager().getUserRepository().delete(username);
        ctx.result("{\"ok\":true}");
    }

    private void handlePermissions(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.manage"))
            return;
        Map<String, Object> body = jsonBody(ctx);
        int userId = Integer.parseInt(body.get("userId").toString());
        String action = body.get("action").toString();
        String permission = body.get("permission").toString();
        var perms = plugin.getDatabaseManager().getPermissionRepository();
        switch (action) {
            case "grant" -> perms.grant(userId, permission);
            case "revoke" -> perms.revoke(userId, permission);
        }
        ctx.result("{\"ok\":true}");
    }

    private void handleWhitelist(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.whitelist"))
            return;
        var repo = plugin.getDatabaseManager().getWebWhitelistRepository();
        ctx.result(gson().toJson(repo.list()));
    }

    private boolean requirePermission(Context ctx, String permission) throws Exception {
        Session session = getAuthenticatedSession(ctx);
        if (session == null) {
            ctx.status(401).result("{\"error\":\"Unauthorized\"}");
            return false;
        }
        if (!hasPermission(session.username(), permission)) {
            ctx.status(403).result("{\"error\":\"Permission denied\"}");
            return false;
        }
        ctx.attribute("session", session);
        return true;
    }

    private Session getAuthenticatedSession(Context ctx) {
        String auth = ctx.header("Authorization");
        if (auth == null || !auth.startsWith("Bearer "))
            return null;
        return sessionManager.getSession(auth.substring(7));
    }

    private String getClientIP(Context ctx) {
        if (proxyMode) {
            String xForwardedFor = ctx.header("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }
        return ctx.req().getRemoteAddr();
    }

    private boolean hasPermission(String username, String permission) {
        try {
            var repo = plugin.getDatabaseManager().getUserRepository();
            var perms = plugin.getDatabaseManager().getPermissionRepository();
            User user = repo.findByUsername(username).orElse(null);
            if (user == null)
                return false;
            Set<String> userPerms = perms.getPermissions(user.getId());
            if (user.getRole().equals("admin") || user.getRole().equals("owner"))
                return true;
            return userPerms.contains(permission);
        } catch (Exception e) {
            return false;
        }
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

    private double getMaxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }

    private int getPlayerPing(Player player) {
        try {
            return player.getPing();
        } catch (Exception e) {
            return -1;
        }
    }

    private Map<String, Object> jsonBody(Context ctx) {
        return ctx.bodyAsClass(BODY_TYPE);
    }

    private com.google.gson.Gson gson() {
        return GsonProvider.get();
    }
}
