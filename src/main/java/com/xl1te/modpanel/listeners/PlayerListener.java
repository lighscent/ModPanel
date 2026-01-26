package com.xl1te.modpanel.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.entity.Player;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.web.EventsHandler;

public class PlayerListener implements Listener {
    private Main plugin;
    private ColoredLogger logger;
    private DatabaseManager databaseManager;

    public PlayerListener(Main plugin, ColoredLogger logger, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String name = player.getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.storePlayer(uuid, name);
            databaseManager.updateLastSeen(uuid);

            try {
                var mainInventory = databaseManager.loadPlayerMainInventory(uuid);
                var armorInventory = databaseManager.loadPlayerArmorInventory(uuid);
                var offhandItem = databaseManager.loadPlayerOffhandItem(uuid);

                if (mainInventory != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            var inventory = player.getInventory();
                            for (int i = 0; i < Math.min(mainInventory.length, 36); i++) {
                                inventory.setItem(i, mainInventory[i]);
                            }

                            if (armorInventory != null && armorInventory.length >= 4) {
                                inventory.setBoots(armorInventory[0]);
                                inventory.setLeggings(armorInventory[1]);
                                inventory.setChestplate(armorInventory[2]);
                                inventory.setHelmet(armorInventory[3]);
                            }

                            if (offhandItem != null) {
                                inventory.setItemInOffHand(offhandItem);
                            }
                            logger.info("Loaded saved inventory from database for player " + name);
                        }
                    });
                }
            } catch (Exception e) {
                logger.warning("Failed to load saved inventory for player " + name + ": " + e.getMessage());
            }

            logger.info("Player " + name + " joined and data stored.");
            EventsHandler.broadcast("refresh");
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> EventsHandler.broadcast("refresh"));
        }
    }

    @EventHandler
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> EventsHandler.broadcast("refresh"));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> EventsHandler.broadcast("refresh"));
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> EventsHandler.broadcast("refresh"));
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> EventsHandler.broadcast("refresh"));
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> EventsHandler.broadcast("refresh"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> EventsHandler.broadcast("refresh"));
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> EventsHandler.broadcast("refresh"));
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> EventsHandler.broadcast("refresh"));
    }

    /**
     * Saves the inventory of a specific player to the database.
     * This is called on player quit and also during server shutdown.
     */
    public void savePlayerInventory(Player player) {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();

        var inventory = player.getInventory();

        var mainInventory = new org.bukkit.inventory.ItemStack[36];
        for (int i = 0; i < 36; i++) {
            mainInventory[i] = inventory.getItem(i);
        }

        var armorInventory = new org.bukkit.inventory.ItemStack[] {
                inventory.getBoots(),
                inventory.getLeggings(),
                inventory.getChestplate(),
                inventory.getHelmet()
        };

        var offhandItem = inventory.getItemInOffHand();

        databaseManager.savePlayerInventory(uuid, mainInventory, armorInventory, offhandItem);
        databaseManager.updateLastSeen(uuid);
        logger.info("Saved inventory for player " + name);
    }

    /**
     * Saves all online players' inventories to the database.
     * This should be called during server shutdown to prevent data loss.
     */
    public void saveAllOnlinePlayersInventory() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            savePlayerInventory(player);
        }
        logger.info("Saved all online players' inventories.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        String name = event.getPlayer().getName();

        var inventory = event.getPlayer().getInventory();

        var mainInventory = new org.bukkit.inventory.ItemStack[36];
        for (int i = 0; i < 36; i++) {
            mainInventory[i] = inventory.getItem(i);
        }

        var armorInventory = new org.bukkit.inventory.ItemStack[] {
                inventory.getBoots(),
                inventory.getLeggings(),
                inventory.getChestplate(),
                inventory.getHelmet()
        };

        var offhandItem = inventory.getItemInOffHand();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.savePlayerInventory(uuid, mainInventory, armorInventory, offhandItem);
            databaseManager.updateLastSeen(uuid);
            logger.info("Player " + name + " quit, inventory saved asynchronously.");
            EventsHandler.broadcast("refresh");
        });
    }
}