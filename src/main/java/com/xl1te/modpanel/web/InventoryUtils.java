package com.xl1te.modpanel.web;

import com.xl1te.modpanel.utils.ColoredLogger;
import com.xl1te.modpanel.database.DatabaseManager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.enchantments.Enchantment;
import java.util.Map;

public class InventoryUtils {
    private ColoredLogger logger;
    private DatabaseManager databaseManager;

    public InventoryUtils(ColoredLogger logger, DatabaseManager databaseManager) {
        this.logger = logger;
        this.databaseManager = databaseManager;
    }

    public String itemToJson(ItemStack item) {
        if (item == null || item.getType().name().equals("AIR")) {
            return "null";
        }

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"").append(item.getType().name().toLowerCase()).append("\",");
        json.append("\"count\":").append(item.getAmount());

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            json.append(",\"displayName\":\"").append(item.getItemMeta().getDisplayName().replace("\"", "\\\""))
                    .append("\"");
        }

        // Add enchantments
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            json.append(",\"enchantments\":[");
            Map<Enchantment, Integer> enchants = item.getEnchantments();
            boolean first = true;
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                if (!first)
                    json.append(",");
                json.append("{");
                json.append("\"name\":\"").append(entry.getKey().getKey().getKey()).append("\",");
                json.append("\"level\":").append(entry.getValue());
                json.append("}");
                first = false;
            }
            json.append("]");
        }

        json.append("}");
        return json.toString();
    }

    public String serializeInventoryJson(PlayerInventory inventory) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        // Main inventory (0-35)
        json.append("\"main\":[");
        for (int i = 0; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (i > 0)
                json.append(",");
            json.append(itemToJson(item));
        }
        json.append("],");

        // Armor (boots, leggings, chestplate, helmet)
        json.append("\"armor\":[");
        ItemStack[] armor = { inventory.getBoots(), inventory.getLeggings(),
                inventory.getChestplate(), inventory.getHelmet() };
        for (int i = 0; i < armor.length; i++) {
            if (i > 0)
                json.append(",");
            json.append(itemToJson(armor[i]));
        }
        json.append("],");

        // Off hand
        json.append("\"offhand\":[");
        json.append(itemToJson(inventory.getItemInOffHand()));
        json.append("]");

        json.append("}");
        return json.toString();
    }

    public boolean moveItemInInventory(String playerUUID, int fromSlot, int toSlot, Player onlinePlayer) {
        try {
            ItemStack[] mainInventory;
            ItemStack[] armorInventory;
            ItemStack offhandItem;

            if (onlinePlayer != null) {
                // Player is online - get current inventory
                PlayerInventory inventory = onlinePlayer.getInventory();
                mainInventory = new ItemStack[36];
                for (int i = 0; i < 36; i++) {
                    mainInventory[i] = inventory.getItem(i);
                }
                armorInventory = new ItemStack[] {
                        inventory.getBoots(),
                        inventory.getLeggings(),
                        inventory.getChestplate(),
                        inventory.getHelmet()
                };
                offhandItem = inventory.getItemInOffHand();
            } else {
                // Player is offline - load from database
                mainInventory = databaseManager.loadPlayerMainInventory(playerUUID);
                armorInventory = databaseManager.loadPlayerArmorInventory(playerUUID);
                offhandItem = databaseManager.loadPlayerOffhandItem(playerUUID);
            }

            // Validate slots and perform the move
            ItemStack itemToMove = null;

            // Get item from source slot
            if (fromSlot >= 0 && fromSlot < 36) {
                // Main inventory slot
                if (mainInventory != null && fromSlot < mainInventory.length) {
                    itemToMove = mainInventory[fromSlot];
                }
            } else if (fromSlot >= 36 && fromSlot <= 39) {
                // Armor slot (36=boots, 37=leggings, 38=chestplate, 39=helmet)
                int armorIndex = fromSlot - 36;
                if (armorInventory != null && armorIndex < armorInventory.length) {
                    itemToMove = armorInventory[armorIndex];
                }
            } else if (fromSlot == 40) {
                // Offhand slot
                itemToMove = offhandItem;
            }

            if (itemToMove == null || itemToMove.getType().name().equals("AIR")) {
                return false; // No item to move
            }

            // Get item from destination slot (for swapping)
            ItemStack destItem = null;
            if (toSlot >= 0 && toSlot < 36) {
                // Main inventory slot
                if (mainInventory != null && toSlot < mainInventory.length) {
                    destItem = mainInventory[toSlot];
                }
            } else if (toSlot >= 36 && toSlot <= 39) {
                // Armor slot
                int armorIndex = toSlot - 36;
                if (armorInventory != null && armorIndex < armorInventory.length) {
                    destItem = armorInventory[armorIndex];
                }
            } else if (toSlot == 40) {
                // Offhand slot
                destItem = offhandItem;
            }

            // Perform the move
            if (fromSlot >= 0 && fromSlot < 36) {
                mainInventory[fromSlot] = destItem;
            } else if (fromSlot >= 36 && fromSlot <= 39) {
                armorInventory[fromSlot - 36] = destItem;
            } else if (fromSlot == 40) {
                offhandItem = destItem;
            }

            if (toSlot >= 0 && toSlot < 36) {
                mainInventory[toSlot] = itemToMove;
            } else if (toSlot >= 36 && toSlot <= 39) {
                armorInventory[toSlot - 36] = itemToMove;
            } else if (toSlot == 40) {
                offhandItem = itemToMove;
            }

            // Save back to database and/or apply to online player
            if (onlinePlayer != null) {
                // Apply changes to online player's inventory
                PlayerInventory inventory = onlinePlayer.getInventory();
                for (int i = 0; i < 36; i++) {
                    inventory.setItem(i, mainInventory[i]);
                }
                inventory.setBoots(armorInventory[0]);
                inventory.setLeggings(armorInventory[1]);
                inventory.setChestplate(armorInventory[2]);
                inventory.setHelmet(armorInventory[3]);
                inventory.setItemInOffHand(offhandItem);
            }

            // Always save to database for persistence
            databaseManager.savePlayerInventory(playerUUID, mainInventory, armorInventory, offhandItem);

            return true;
        } catch (Exception e) {
            logger.warning("Failed to move item for player " + playerUUID + ": " + e.getMessage());
            return false;
        }
    }

    public boolean removeItemFromSlot(String playerUUID, int slot, Player onlinePlayer) {
        try {
            ItemStack[] mainInventory;
            ItemStack[] armorInventory;
            ItemStack offhandItem;

            if (onlinePlayer != null) {
                // Player is online - get current inventory
                PlayerInventory inventory = onlinePlayer.getInventory();
                mainInventory = new ItemStack[36];
                for (int i = 0; i < 36; i++) {
                    mainInventory[i] = inventory.getItem(i);
                }
                armorInventory = new ItemStack[] {
                        inventory.getBoots(),
                        inventory.getLeggings(),
                        inventory.getChestplate(),
                        inventory.getHelmet()
                };
                offhandItem = inventory.getItemInOffHand();
            } else {
                // Player is offline - load from database
                mainInventory = databaseManager.loadPlayerMainInventory(playerUUID);
                armorInventory = databaseManager.loadPlayerArmorInventory(playerUUID);
                offhandItem = databaseManager.loadPlayerOffhandItem(playerUUID);
            }

            // Perform removal
            if (slot >= 0 && slot < 36) {
                // Main inventory slot
                if (mainInventory != null && slot < mainInventory.length) {
                    mainInventory[slot] = null;
                }
            } else if (slot >= 36 && slot <= 39) {
                // Armor slot
                int armorIndex = slot - 36;
                if (armorInventory != null && armorIndex < armorInventory.length) {
                    armorInventory[armorIndex] = null;
                }
            } else if (slot == 40) {
                // Offhand slot
                offhandItem = null;
            } else {
                return false; // Invalid slot
            }

            // Save back to database and/or apply to online player
            if (onlinePlayer != null) {
                // Apply changes to online player's inventory
                PlayerInventory inventory = onlinePlayer.getInventory();
                if (slot >= 0 && slot < 36) {
                    inventory.setItem(slot, null);
                } else if (slot >= 36 && slot <= 39) {
                    if (slot == 36)
                        inventory.setBoots(null);
                    else if (slot == 37)
                        inventory.setLeggings(null);
                    else if (slot == 38)
                        inventory.setChestplate(null);
                    else if (slot == 39)
                        inventory.setHelmet(null);
                } else if (slot == 40) {
                    inventory.setItemInOffHand(null);
                }
            }

            // Always save to database for persistence
            databaseManager.savePlayerInventory(playerUUID, mainInventory, armorInventory, offhandItem);

            return true;
        } catch (Exception e) {
            logger.warning("Failed to remove item for player " + playerUUID + ": " + e.getMessage());
            return false;
        }
    }
}