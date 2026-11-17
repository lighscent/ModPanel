package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.database.models.ServerStats;
import io.javalin.http.Context;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Endpoints de lecture du serveur et des joueurs (collecte sur le thread principal). */
public class GameApi {

    private final Main plugin;
    private final ApiSupport support;

    public GameApi(Main plugin, ApiSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void handleStats(Context ctx) throws Exception {
        ServerStats stats = support.callSync(() -> new ServerStats(
                plugin.getServer().getOnlinePlayers().size(),
                plugin.getServer().getMaxPlayers(),
                support.getTPS(),
                plugin.getServer().getVersion(),
                plugin.getServer().getName()));
        ctx.result(support.gson().toJson(stats));
    }

    public void handlePlayers(Context ctx) throws Exception {
        List<Map<String, Object>> players = support.callSync(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                list.add(PlayerData.summary(player, support.logger()::processMessage));
            }
            return list;
        });
        ctx.result(support.gson().toJson(players));
    }

    public void handlePlayerDetail(Context ctx) throws Exception {
        String uuidStr = ctx.queryParam("uuid");
        if (uuidStr == null || uuidStr.isBlank()) {
            ApiSupport.error(ctx, 400, "uuid query param required");
            return;
        }
        UUID uuid = ApiSupport.tryParseUuid(uuidStr);
        if (uuid == null) {
            ApiSupport.error(ctx, 400, "Invalid UUID");
            return;
        }

        Map<String, Object> detail = support.callSync(() -> {
            Player player = plugin.getServer().getPlayer(uuid);
            return player == null ? null : PlayerData.detail(player, support.logger()::processMessage);
        });
        if (detail == null) {
            ApiSupport.error(ctx, 404, "Player not found or offline");
            return;
        }
        ctx.result(support.gson().toJson(detail));
    }
}
