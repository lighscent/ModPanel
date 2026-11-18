package com.xl1te.modpanel.listeners;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.web.api.LiveHub;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Pousse la liste des joueurs aux sockets live à chaque arrivée/départ. */
public class PlayerLiveListener implements Listener {

    private final Main plugin;
    private final LiveHub hub;

    public PlayerLiveListener(Main plugin, LiveHub hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        hub.broadcastPlayers();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Le joueur est encore listé pendant l'event : diffusion une tick plus tard.
        plugin.getServer().getScheduler().runTaskLater(plugin, hub::broadcastPlayers, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, hub::broadcastPlayers, 1L);
    }
}
