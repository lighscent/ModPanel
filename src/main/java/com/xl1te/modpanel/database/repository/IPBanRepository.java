package com.xl1te.modpanel.database.repository;

import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.database.models.IPBanEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class IPBanRepository {
    private final DatabaseManager databaseManager;

    public IPBanRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<IPBanEntry> findByIp(String ip) throws SQLException {
        String sql = "SELECT failure_count, ban_count, banned_until, permanent FROM web_bans WHERE ip = ?";
        try (Connection conn = databaseManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ip);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Integer banCount = rs.getInt("ban_count");
                    Integer failureCount = rs.getInt("failure_count");
                    Timestamp bannedUntilTs = rs.getTimestamp("banned_until");
                    Instant bannedUntil = bannedUntilTs != null ? bannedUntilTs.toInstant() : null;
                    boolean permanent = rs.getBoolean("permanent");
                    return Optional.of(new IPBanEntry(ip, failureCount, banCount, bannedUntil, permanent));
                }
            }
        }
        return Optional.empty();
    }

    public void save(IPBanEntry entry) throws SQLException {
        String sql = "MERGE INTO web_bans (ip, failure_count, ban_count, banned_until, permanent) KEY(ip) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entry.getIp());
            stmt.setInt(2, entry.getFailureCount());
            stmt.setInt(3, entry.getBanCount());
            if (entry.getBannedUntil() != null) {
                stmt.setTimestamp(4, Timestamp.from(entry.getBannedUntil()));
            } else {
                stmt.setTimestamp(4, null);
            }
            stmt.setBoolean(5, entry.isPermanent());
            stmt.executeUpdate();
        }
    }

    public void delete(String ip) throws SQLException {
        String sql = "DELETE FROM web_bans WHERE ip = ?";
        try (Connection conn = databaseManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ip);
            stmt.executeUpdate();
        }
    }
}
