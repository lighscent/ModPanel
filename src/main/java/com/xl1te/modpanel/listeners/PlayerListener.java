package com.xl1te.modpanel.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.utils.ColoredLogger;

public class PlayerListener implements Listener {
    private ColoredLogger logger;
    private DatabaseManager databaseManager;

    public PlayerListener(ColoredLogger logger, DatabaseManager databaseManager) {
        this.logger = logger;
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        String name = event.getPlayer().getName();

        databaseManager.storePlayer(uuid, name);
        databaseManager.updateLastSeen(uuid);

        // Load saved inventory if it exists (for offline modifications)
        try {
            var mainInventory = databaseManager.loadPlayerMainInventory(uuid);
            var armorInventory = databaseManager.loadPlayerArmorInventory(uuid);
            var offhandItem = databaseManager.loadPlayerOffhandItem(uuid);

            // Check if we have saved inventory data
            boolean hasSavedData = false;
            if (mainInventory != null) {
                for (ItemStack item : mainInventory) {
                    if (item != null && !item.getType().name().equals("AIR")) {
                        hasSavedData = true;
                        break;
                    }
                }
            }

            if (hasSavedData) {
                // Apply saved inventory to player
                var inventory = event.getPlayer().getInventory();

                // Set main inventory
                for (int i = 0; i < Math.min(mainInventory.length, 36); i++) {
                    if (mainInventory[i] != null) {
                        inventory.setItem(i, mainInventory[i]);
                    }
                }

                // Set armor
                if (armorInventory != null && armorInventory.length >= 4) {
                    inventory.setBoots(armorInventory[0]);
                    inventory.setLeggings(armorInventory[1]);
                    inventory.setChestplate(armorInventory[2]);
                    inventory.setHelmet(armorInventory[3]);
                }

                // Set offhand
                if (offhandItem != null) {
                    inventory.setItemInOffHand(offhandItem);
                }

                logger.info("Loaded saved inventory for player " + name);
            }
        } catch (Exception e) {
            logger.warning("Failed to load saved inventory for player " + name + ": " + e.getMessage());
        }

        logger.info("Player " + name + " joined and data stored.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        String name = event.getPlayer().getName();

        // Save inventory data before player quits
        var inventory = event.getPlayer().getInventory();

        // Main inventory (0-35)
        var mainInventory = new org.bukkit.inventory.ItemStack[36];
        for (int i = 0; i < 36; i++) {
            mainInventory[i] = inventory.getItem(i);
        }

        // Armor inventory (boots, leggings, chestplate, helmet)
        var armorInventory = new org.bukkit.inventory.ItemStack[] {
                inventory.getBoots(),
                inventory.getLeggings(),
                inventory.getChestplate(),
                inventory.getHelmet()
        };

        // Offhand item
        var offhandItem = inventory.getItemInOffHand();

        databaseManager.savePlayerInventory(uuid, mainInventory, armorInventory, offhandItem);
        databaseManager.updateLastSeen(uuid);

        logger.info("Player " + name + " quit, inventory saved and last seen updated.");
    }
}