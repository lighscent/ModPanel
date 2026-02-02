package com.xl1te.modpanel.database;

import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.xl1te.modpanel.utils.ColoredLogger;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class DatabaseManager {
    private Connection connection;
    private ColoredLogger logger;
    private DatabaseSchema schema;

    public DatabaseManager(ColoredLogger logger, String dbPath) {
        this.logger = logger;
        this.schema = new DatabaseSchema(logger);
        try {
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(
                    "jdbc:h2:" + dbPath + ";CACHE_SIZE=65536;DB_CLOSE_DELAY=-1;AUTO_RECONNECT=TRUE", "sa", "");
            schema.createTables(connection);
        } catch (ClassNotFoundException | SQLException e) {
            logger.severe("Failed to connect to database: " + e.getMessage());
        }
    }

    public synchronized void logAccess(String ip, boolean allowed) {
        String sql = "INSERT INTO access_logs (ip, allowed) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ip);
            pstmt.setBoolean(2, allowed);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to log access: " + e.getMessage());
        }
    }

    public synchronized void storePlayer(String uuid, String name) {
        String sql = "MERGE INTO players (uuid, name) KEY(uuid) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to store player: " + e.getMessage());
        }
    }

    public synchronized String getPlayerName(String uuid) {
        String sql = "SELECT name FROM players WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            logger.warning("Failed to get player name: " + e.getMessage());
        }
        return null;
    }

    public synchronized String getPlayerUUID(String name) {
        String sql = "SELECT uuid FROM players WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("uuid");
            }
        } catch (SQLException e) {
            logger.warning("Failed to get player UUID: " + e.getMessage());
        }
        return null;
    }

    public synchronized void updateLastSeen(String uuid) {
        String sql = "UPDATE players SET last_seen = CURRENT_TIMESTAMP WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to update last seen: " + e.getMessage());
        }
    }

    public synchronized void savePlayerInventory(String uuid, ItemStack[] mainInventory, ItemStack[] armor,
            ItemStack offhand) {
        try {
            // Serialize inventory data
            String mainData = serializeInventory(mainInventory);
            String armorData = serializeInventory(armor);
            String offhandData = serializeItem(offhand);

            String sql = "MERGE INTO player_inventories (uuid, main_inventory, armor_inventory, offhand_item, last_updated) KEY(uuid) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid);
                pstmt.setString(2, mainData);
                pstmt.setString(3, armorData);
                pstmt.setString(4, offhandData);
                pstmt.executeUpdate();
            }
        } catch (SQLException | IOException e) {
            logger.warning("Failed to save player inventory: " + e.getMessage());
        }
    }

    public synchronized ItemStack[] loadPlayerMainInventory(String uuid) {
        String sql = "SELECT main_inventory FROM player_inventories WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                String data = rs.getString("main_inventory");
                return data != null ? deserializeInventory(data) : new ItemStack[36];
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            logger.warning("Failed to load player main inventory: " + e.getMessage());
        }
        return null;
    }

    public synchronized ItemStack[] loadPlayerArmorInventory(String uuid) {
        String sql = "SELECT armor_inventory FROM player_inventories WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                String data = rs.getString("armor_inventory");
                return data != null ? deserializeInventory(data) : new ItemStack[4];
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            logger.warning("Failed to load player armor inventory: " + e.getMessage());
        }
        return null;
    }

    public synchronized ItemStack loadPlayerOffhandItem(String uuid) {
        String sql = "SELECT offhand_item FROM player_inventories WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                String data = rs.getString("offhand_item");
                return data != null ? deserializeItem(data) : null;
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            logger.warning("Failed to load player offhand item: " + e.getMessage());
        }
        return null;
    }

    private String serializeInventory(ItemStack[] items) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeInt(items.length);
            for (ItemStack item : items) {
                boos.writeObject(item);
            }
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private ItemStack[] deserializeInventory(String data) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(data);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            int length = bois.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) bois.readObject();
            }
            return items;
        }
    }

    private String serializeItem(ItemStack item) throws IOException {
        if (item == null)
            return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private ItemStack deserializeItem(String data) throws IOException, ClassNotFoundException {
        if (data == null)
            return null;
        byte[] bytes = Base64.getDecoder().decode(data);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) bois.readObject();
        }
    }

    public synchronized List<String[]> getAllPlayers() {
        List<String[]> players = new ArrayList<>();
        String sql = "SELECT uuid, name, first_seen, last_seen FROM players ORDER BY last_seen DESC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            var rs = pstmt.executeQuery();
            while (rs.next()) {
                players.add(new String[] {
                        rs.getString("uuid"),
                        rs.getString("name"),
                        rs.getString("first_seen"),
                        rs.getString("last_seen")
                });
            }
        } catch (SQLException e) {
            logger.warning("Failed to get all players: " + e.getMessage());
        }
        return players;
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed.");
            } catch (SQLException e) {
                logger.warning("Failed to close database: " + e.getMessage());
            }
        }
    }
}