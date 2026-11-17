package com.xl1te.modpanel.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migrations de schéma idempotentes, dialect-agnostic (H2 et MariaDB/MySQL).
 * {@code CREATE TABLE IF NOT EXISTS} ignore les changements de colonnes sur les bases
 * existantes : toute évolution de schéma passe donc par une migration versionnée ici,
 * enregistrée dans la table {@code schema_version}. Les migrations déjà appliquées ne
 * sont jamais modifiées.
 */
public class SchemaMigrator {

    public static final int SCHEMA_VERSION = 1;

    public void migrate(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "version INT PRIMARY KEY, "
                    + "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
        int current = readVersion(conn);
        for (int v = current + 1; v <= SCHEMA_VERSION; v++) {
            applyMigration(conn, v);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO schema_version (version) VALUES (?)")) {
                stmt.setInt(1, v);
                stmt.executeUpdate();
            }
        }
    }

    private int readVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void applyMigration(Connection conn, int version) throws SQLException {
        switch (version) {
            // Figée : miroir de createTables() à la version 1. Ne jamais modifier.
            case 1 -> exec(conn,
                    "CREATE TABLE IF NOT EXISTS password_change_required ("
                    + "user_id INT PRIMARY KEY, "
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            default -> throw new SQLException("Unknown schema version: " + version);
        }
    }

    private void exec(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }
}
