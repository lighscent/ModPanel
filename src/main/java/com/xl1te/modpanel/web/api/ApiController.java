package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.database.models.ServerStats;
import com.xl1te.modpanel.database.models.User;
import com.xl1te.modpanel.web.auth.PasswordHasher;
import com.xl1te.modpanel.web.auth.SessionManager;
import com.xl1te.modpanel.web.auth.SessionManager.Session;
import com.xl1te.modpanel.web.auth.IPBanException;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.utils.GsonProvider;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.time.LocalDateTime;
import java.util.*;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ApiController implements Handler {

    private final Main plugin;
    private final SessionManager sessionManager;
    private final BestLogger logger;

    public ApiController(Main plugin, SessionManager sessionManager, BestLogger logger) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.logger = logger;
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
        } catch (Exception e) {
            logger.severe("API error on " + path + ": " + e.getMessage());
            ctx.status(500).result("{\"error\":\"Internal server error\"}");
        }
    }

    private void handleLogin(Context ctx) throws Exception {
        Map body = ctx.bodyAsClass(Map.class);
        String username = body.get("username").toString();
        String password = body.get("password").toString();
        if (username.isBlank() || password.isBlank()) {
            ctx.status(400).result("{\"error\":\"Username and password required\"}");
            return;
        }
        var repo = plugin.getDatabaseManager().getUserRepository();
        var userOpt = repo.findByUsername(username);
        if (userOpt.isEmpty()) {
            ctx.status(401).result("{\"error\":\"Invalid credentials\"}");
            return;
        }
        User user = userOpt.get();
        if (!user.isEnabled()) {
            ctx.status(403).result("{\"error\":\"Account disabled\"}");
            return;
        }
        if (!PasswordHasher.verify(password, user.getPasswordHash())) {
            ctx.status(401).result("{\"error\":\"Invalid credentials\"}");
            return;
        }
        repo.updateLastLogin(user.getUsername());
        String token = sessionManager.createSession(user.getUsername());
        ctx.result("{\"token\":\"" + token + "\",\"username\":\"" + user.getUsername() + "\",\"role\":\"" + user.getRole() + "\"}");
    }

    private void handleLogout(Context ctx) throws Exception {
        String auth = ctx.header("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            sessionManager.removeSession(auth.substring(7));
        }
        ctx.result("{\"ok\":true}");
    }

    private void handleStats(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.stats")) return;
        double tps = getTPS();
        ServerStats stats = new ServerStats(
            plugin.getServer().getOnlinePlayers().size(),
            plugin.getServer().getMaxPlayers(),
            tps,
            plugin.getServer().getVersion(),
            plugin.getServer().getName()
        );
        ctx.result(gson().toJson(stats));
    }

    private void handlePlayers(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.players")) return;
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", player.getName());
            p.put("uuid", player.getUniqueId().toString());
            p.put("ip", player.getAddress().getAddress().getHostAddress());
            p.put("world", player.getWorld().getName());
            p.put("health", (int) player.getHealth());
            p.put("maxHealth", (int) player.getMaxHealth());
            p.put("gamemode", player.getGameMode().name());
            p.put("ping", getPlayerPing(player));
            p.put("locale", player.getLocale());
            p.put("displayName", player.getDisplayName());
            p.put("x", (int) player.getLocation().getX());
            p.put("y", (int) player.getLocation().getY());
            p.put("z", (int) player.getLocation().getZ());
            players.add(p);
        }
        ctx.result(gson().toJson(players));
    }

    private void handlePlayerDetail(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.player.detail")) return;
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
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            ctx.status(404).result("{\"error\":\"Player not found or offline\"}");
            return;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", player.getName());
        detail.put("uuid", player.getUniqueId().toString());
        detail.put("displayName", player.getDisplayName());
        detail.put("ip", player.getAddress().getAddress().getHostAddress());
        detail.put("world", player.getWorld().getName());
        detail.put("x", player.getLocation().getX());
        detail.put("y", player.getLocation().getY());
        detail.put("z", player.getLocation().getZ());
        detail.put("yaw", (double) player.getLocation().getYaw());
        detail.put("pitch", (double) player.getLocation().getPitch());
        detail.put("health", player.getHealth());
        detail.put("maxHealth", player.getMaxHealth());
        detail.put("food", player.getFoodLevel());
        detail.put("saturation", (double) player.getSaturation());
        detail.put("gamemode", player.getGameMode().name());
        detail.put("ping", getPlayerPing(player));
        detail.put("locale", player.getLocale());
        detail.put("level", player.getLevel());
        detail.put("exp", player.getExp());
        detail.put("totalExperience", player.getTotalExperience());
        detail.put("walkSpeed", player.getWalkSpeed());
        detail.put("flySpeed", player.getFlySpeed());
        detail.put("isFlying", player.isFlying());
        detail.put("isOp", player.isOp());

        // Potion effects
        List<Map<String, Object>> effects = new ArrayList<>();
        player.getActivePotionEffects().forEach(effect -> {
            Map<String, Object> eff = new LinkedHashMap<>();
            eff.put("type", effect.getType().getName());
            eff.put("amplifier", effect.getAmplifier());
            eff.put("duration", effect.getDuration() / 20); // ticks to seconds
            effects.add(eff);
        });
        detail.put("potionEffects", effects);

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
        detail.put("inventory", inventory);

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
        detail.put("armor", armor);

        // Offhand
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            detail.put("offhand", offhand.getType().name());
        }

        ctx.result(gson().toJson(detail));
    }

    private void handleUsers(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.view")) return;
        List<User> users = plugin.getDatabaseManager().getUserRepository().list();
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users) {
            out.add(Map.of(
                "id", u.getId(), "username", u.getUsername(),
                "role", u.getRole(), "enabled", u.isEnabled(),
                "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "",
                "lastLogin", u.getLastLogin() != null ? u.getLastLogin().toString() : ""
            ));
        }
        ctx.result(gson().toJson(out));
    }

    private void handleUserCreate(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.manage")) return;
        Map body = ctx.bodyAsClass(Map.class);
        String username = body.get("username").toString();
        String password = body.get("password").toString();
        String role = body.getOrDefault("role", "moderator").toString();
        if (username.isBlank() || password.isBlank()) {
            ctx.status(400).result("{\"error\":\"Username and password required\"}");
            return;
        }
        User user = new User(0, username, PasswordHasher.hash(password), role, true, LocalDateTime.now(), null);
        plugin.getDatabaseManager().getUserRepository().create(user);
        var perms = plugin.getDatabaseManager().getPermissionRepository();
        perms.grant(user.getId(), "modpanel.stats");
        perms.grant(user.getId(), "modpanel.players");
        ctx.result("{\"ok\":true,\"username\":\"" + username + "\"}");
    }

    private void handleUserUpdate(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.manage")) return;
        Map body = ctx.bodyAsClass(Map.class);
        int id = Integer.parseInt(body.get("id").toString());
        var repo = plugin.getDatabaseManager().getUserRepository();
        User user = repo.findById(id).orElse(null);
        if (user == null) { ctx.status(404).result("{\"error\":\"User not found\"}"); return; }
        String role = body.getOrDefault("role", user.getRole()).toString();
        boolean enabled = Boolean.parseBoolean(body.getOrDefault("enabled", String.valueOf(user.isEnabled())).toString());
        String passwordHash = user.getPasswordHash();
        if (body.containsKey("password") && !body.get("password").toString().isBlank()) {
            passwordHash = PasswordHasher.hash(body.get("password").toString());
        }
        repo.update(new User(id, user.getUsername(), passwordHash, role, enabled, user.getCreatedAt(), user.getLastLogin()));
        ctx.result("{\"ok\":true}");
    }

    private void handleUserDelete(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.manage")) return;
        Map body = ctx.bodyAsClass(Map.class);
        String username = body.get("username").toString();
        plugin.getDatabaseManager().getUserRepository().delete(username);
        ctx.result("{\"ok\":true}");
    }

    private void handlePermissions(Context ctx) throws Exception {
        if (!requirePermission(ctx, "modpanel.users.manage")) return;
        Map body = ctx.bodyAsClass(Map.class);
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
        if (!requirePermission(ctx, "modpanel.whitelist")) return;
        var repo = plugin.getDatabaseManager().getWebWhitelistRepository();
        ctx.result(gson().toJson(repo.list()));
    }

    private boolean requirePermission(Context ctx, String permission) throws Exception {
        Session session = getAuthenticatedSession(ctx);
        if (session == null) { ctx.status(401).result("{\"error\":\"Unauthorized\"}"); return false; }
        if (!hasPermission(session.username(), permission)) {
            ctx.status(403).result("{\"error\":\"Permission denied\"}");
            return false;
        }
        ctx.attribute("session", session);
        return true;
    }

    private Session getAuthenticatedSession(Context ctx) {
        String auth = ctx.header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        return sessionManager.getSession(auth.substring(7));
    }

    private boolean hasPermission(String username, String permission) {
        try {
            var repo = plugin.getDatabaseManager().getUserRepository();
            var perms = plugin.getDatabaseManager().getPermissionRepository();
            User user = repo.findByUsername(username).orElse(null);
            if (user == null) return false;
            Set<String> userPerms = perms.getPermissions(user.getId());
            if (user.getRole().equals("admin") || user.getRole().equals("owner")) return true;
            return userPerms.contains(permission);
        } catch (Exception e) { return false; }
    }

    private double getTPS() {
        try {
            Object provider = plugin.getServer().getClass().getMethod("getTPS").invoke(plugin.getServer());
            if (provider instanceof double[] arr) return arr[0];
            if (provider instanceof List<?> list && !list.isEmpty()) {
                Object val = list.get(0);
                if (val instanceof Double) return (Double) val;
            }
            return 20.0;
        } catch (Exception e) { return 20.0; }
    }

    private int getPlayerPing(Player player) {
        try {
            return player.getPing();
        } catch (Exception e) { return -1; }
    }

    private com.google.gson.Gson gson() { return GsonProvider.get(); }
}
