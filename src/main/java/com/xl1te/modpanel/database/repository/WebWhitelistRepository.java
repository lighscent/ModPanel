package com.xl1te.modpanel.database.repository;

import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.database.models.WebWhitelistEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WebWhitelistRepository {

    private final DatabaseManager databaseManager;

    public WebWhitelistRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean exists(String ip) throws SQLException {
        String sql = "SELECT COUNT(*) FROM web_whitelist WHERE ip = ?";
        try (Connection conn = databaseManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ip);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        }
    }

    public void add(String ip) throws SQLException {
        String sql = "MERGE INTO web_whitelist (ip, added_at) KEY(ip) VALUES (?, CURRENT_TIMESTAMP)";
        try (Connection conn = databaseManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ip);
            stmt.executeUpdate();
        }
    }

    public void remove(String ip) throws SQLException {
        String sql = "DELETE FROM web_whitelist WHERE ip = ?";
        try (Connection conn = databaseManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ip);
            stmt.executeUpdate();
        }
    }

    public List<WebWhitelistEntry> list() throws SQLException {
        String sql = "SELECT id, ip, added_at FROM web_whitelist ORDER BY added_at DESC";
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            List<WebWhitelistEntry> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        }
    }

    private WebWhitelistEntry mapRow(ResultSet rs) throws SQLException {
        return new WebWhitelistEntry(
                rs.getInt("id"),
                rs.getString("ip"),
                rs.getTimestamp("added_at").toLocalDateTime());
    }
}
