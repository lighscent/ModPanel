package com.xl1te.modpanel.database.repository;

import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.database.models.Permission;

import java.sql.*;
import java.util.*;

public class PermissionRepository {
    private final DatabaseManager db;

    public PermissionRepository(DatabaseManager db) {
        this.db = db;
    }

    public Set<String> getPermissions(int userId) throws SQLException {
        String sql = "SELECT permission FROM user_permissions WHERE user_id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                Set<String> perms = new HashSet<>();
                while (rs.next()) perms.add(rs.getString("permission"));
                return perms;
            }
        }
    }

    public void grant(int userId, String permission) throws SQLException {
        String sql = "MERGE INTO user_permissions (user_id, permission) KEY(user_id, permission) VALUES (?, ?)";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, permission);
            stmt.executeUpdate();
        }
    }

    public void revoke(int userId, String permission) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM user_permissions WHERE user_id = ? AND permission = ?")) {
            stmt.setInt(1, userId);
            stmt.setString(2, permission);
            stmt.executeUpdate();
        }
    }

    public void clearAll(int userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM user_permissions WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }
}