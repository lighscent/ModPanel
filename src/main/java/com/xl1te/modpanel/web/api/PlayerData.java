package com.xl1te.modpanel.web.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Collecte des données joueur pour l'API. Appeler uniquement depuis le thread
 * principal Bukkit (via callSync) : l'API Bukkit n'est pas thread-safe.
 */
public final class PlayerData {

    private PlayerData() {
    }

    /** Tuile joueur pour GET /api/players. */
    public static Map<String, Object> summary(Player player, Function<String, String> maskIp) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", player.getName());
        p.put("uuid", player.getUniqueId().toString());
        p.put("ip", maskIp.apply(player.getAddress().getAddress().getHostAddress()));
        p.put("world", player.getWorld().getName());
        p.put("health", (int) player.getHealth());
        p.put("maxHealth", (int) maxHealth(player));
        p.put("gamemode", player.getGameMode().name());
        p.put("ping", ping(player));
        p.put("locale", player.getLocale());
        p.put("displayName", player.getDisplayName());
        p.put("x", (int) player.getLocation().getX());
        p.put("y", (int) player.getLocation().getY());
        p.put("z", (int) player.getLocation().getZ());
        return p;
    }

    /** Fiche complète pour GET /api/players/detail. */
    public static Map<String, Object> detail(Player player, Function<String, String> maskIp) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", player.getName());
        d.put("uuid", player.getUniqueId().toString());
        d.put("displayName", player.getDisplayName());
        d.put("ip", maskIp.apply(player.getAddress().getAddress().getHostAddress()));
        d.put("world", player.getWorld().getName());
        d.put("x", player.getLocation().getX());
        d.put("y", player.getLocation().getY());
        d.put("z", player.getLocation().getZ());
        d.put("yaw", (double) player.getLocation().getYaw());
        d.put("pitch", (double) player.getLocation().getPitch());
        d.put("health", player.getHealth());
        d.put("maxHealth", maxHealth(player));
        d.put("food", player.getFoodLevel());
        d.put("saturation", (double) player.getSaturation());
        d.put("gamemode", player.getGameMode().name());
        d.put("ping", ping(player));
        d.put("locale", player.getLocale());
        d.put("level", player.getLevel());
        d.put("exp", player.getExp());
        d.put("totalExperience", player.getTotalExperience());
        d.put("walkSpeed", player.getWalkSpeed());
        d.put("flySpeed", player.getFlySpeed());
        d.put("isFlying", player.isFlying());
        d.put("isOp", player.isOp());
        d.put("potionEffects", effects(player));
        PlayerInventory inv = player.getInventory();
        d.put("inventory", inventory(inv));
        d.put("armor", armor(inv));
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            d.put("offhand", offhand.getType().name());
        }
        return d;
    }

    private static List<Map<String, Object>> effects(Player player) {
        List<Map<String, Object>> effects = new ArrayList<>();
        player.getActivePotionEffects().forEach(effect -> {
            Map<String, Object> eff = new LinkedHashMap<>();
            eff.put("type", effect.getType().getName());
            eff.put("amplifier", effect.getAmplifier());
            eff.put("duration", effect.getDuration() / 20); // ticks to seconds
            effects.add(eff);
        });
        return effects;
    }

    private static List<Map<String, Object>> inventory(PlayerInventory inv) {
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
        return inventory;
    }

    private static List<Map<String, Object>> armor(PlayerInventory inv) {
        List<Map<String, Object>> armor = new ArrayList<>();
        for (ItemStack a : inv.getArmorContents()) {
            if (a != null && !a.getType().isAir()) {
                Map<String, Object> piece = new LinkedHashMap<>();
                piece.put("type", a.getType().name());
                piece.put("amount", a.getAmount());
                armor.add(piece);
            }
        }
        return armor;
    }

    private static double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }

    private static int ping(Player player) {
        try {
            return player.getPing();
        } catch (Exception e) {
            return -1;
        }
    }
}
