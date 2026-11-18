package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.Main;
import io.javalin.websocket.WsCloseStatus;
import io.javalin.websocket.WsContext;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registre des sockets live + diffusion de la liste des joueurs connectés. */
public class LiveHub {

    private final Main plugin;
    private final ApiSupport support;
    private final ConcurrentHashMap<String, WsContext> sockets = new ConcurrentHashMap<>();
    private volatile boolean closing = false;
    private BukkitTask heartbeat;

    public LiveHub(Main plugin, ApiSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void add(String id, WsContext ctx) {
        sockets.put(id, ctx);
    }

    public void remove(String id) {
        sockets.remove(id);
    }

    public boolean isClosing() {
        return closing;
    }

    /** Remet le hub en service (après un /mp reload, le hub survit au restart). */
    public synchronized void reset() {
        closing = false;
        sockets.clear();
    }

    /**
     * Ping périodique via le scheduler Bukkit (30 s) : garde les connexions
     * idle ouvertes derrière les proxies, sans thread dédié. Annulé
     * automatiquement au disable du plugin, donc pas de fuite de classloader.
     */
    public synchronized void startHeartbeat() {
        if (heartbeat != null) {
            return;
        }
        heartbeat = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            sockets.forEach((id, ctx) -> {
                try {
                    ctx.sendPing();
                } catch (Exception e) {
                    sockets.remove(id);
                }
            });
        }, 20L * 30, 20L * 30);
    }

    /** Ferme proprement tous les sockets avant l'arrêt de Jetty. */
    public void closeAll() {
        closing = true;
        sockets.forEach((id, ctx) -> {
            try {
                ctx.closeSession(WsCloseStatus.SERVICE_RESTART, "Server stopping");
            } catch (Exception ignored) {
            }
        });
        sockets.clear();
    }

    public int size() {
        return sockets.size();
    }

    /** Collecte la liste des joueurs, sur le thread principal si nécessaire. */
    public List<Map<String, Object>> collectPlayers() throws Exception {
        if (plugin.getServer().isPrimaryThread()) {
            return collectNow();
        }
        return support.callSync(this::collectNow);
    }

    private List<Map<String, Object>> collectNow() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            list.add(PlayerData.summary(player, support.logger()::processMessage));
        }
        return list;
    }

    /** Snapshot complet : liste des joueurs + slots max. */
    public Map<String, Object> snapshot() throws Exception {
        List<Map<String, Object>> players = collectPlayers();
        int maxPlayers = plugin.getServer().getMaxPlayers();
        return Map.of("type", "players", "players", players, "maxPlayers", maxPlayers);
    }

    /** Envoie la liste complète à un seul socket (snapshot à la connexion). */
    public void sendSnapshot(WsContext ctx) {
        try {
            ctx.send(support.gson().toJson(snapshot()));
        } catch (Exception e) {
            support.logger().warning("Failed to send live snapshot: " + e.getMessage());
        }
    }

    /** Pousse la liste complète à tous les sockets (join/quit/kick). */
    public void broadcastPlayers() {
        if (sockets.isEmpty()) {
            return;
        }
        final String payload;
        try {
            payload = support.gson().toJson(snapshot());
        } catch (Exception e) {
            support.logger().warning("Failed to collect players for live broadcast: " + e.getMessage());
            return;
        }
        sockets.forEach((id, ctx) -> {
            try {
                ctx.send(payload);
            } catch (Exception e) {
                sockets.remove(id);
            }
        });
    }
}
