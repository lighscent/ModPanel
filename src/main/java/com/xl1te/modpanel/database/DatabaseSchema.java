package com.xl1te.modpanel.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import com.xl1te.modpanel.utils.ColoredLogger;

public class DatabaseSchema {
    private ColoredLogger logger;

    public DatabaseSchema(ColoredLogger logger) {
        this.logger = logger;
    }

    public void createTables(Connection connection) throws SQLException {
        String accessLogsSql = "CREATE TABLE IF NOT EXISTS access_logs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "ip VARCHAR(45)," +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "allowed BOOLEAN)";

        String playersSql = "CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(16) NOT NULL," +
                "first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        String playerInventoriesSql = "CREATE TABLE IF NOT EXISTS player_inventories (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "main_inventory TEXT," +
                "armor_inventory TEXT," +
                "offhand_item TEXT," +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(accessLogsSql);
            stmt.execute(playersSql);
            stmt.execute(playerInventoriesSql);

            // Add performance indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_last_seen ON players(last_seen DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_name ON players(name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_access_logs_timestamp ON access_logs(timestamp)");
        } catch (SQLException e) {
            logger.severe("Error creating database tables: " + e.getMessage());
            throw e;
        }
    }
}