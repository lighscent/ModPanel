package com.xl1te.modpanel.database.repository;

import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.database.models.User;

import java.sql.*;
import java.util.List;
import java.util.Optional;

public class UserRepository {
    private final DatabaseManager db;

    public UserRepository(DatabaseManager db) {
        this.db = db;
    }

    private static final String SELECT_COLS = "id, username, password_hash, role, enabled, created_at, last_login";
    private static final String FROM_USERS = " FROM users";

    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT " + SELECT_COLS + FROM_USERS + " WHERE username = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<User> findById(int id) throws SQLException {
        String sql = "SELECT " + SELECT_COLS + FROM_USERS + " WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public int create(User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role, enabled) VALUES (?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getRole());
            stmt.setBoolean(4, user.isEnabled());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        // Fallback if driver returns no generated key: re-read the row just inserted.
        return findByUsername(user.getUsername()).map(User::getId)
                .orElseThrow(() -> new SQLException("Failed to retrieve generated user id"));
    }

    public void update(User user) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, role = ?, enabled = ? WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getPasswordHash());
            stmt.setString(2, user.getRole());
            stmt.setBoolean(3, user.isEnabled());
            stmt.setInt(4, user.getId());
            stmt.executeUpdate();
        }
    }

    public void delete(String username) throws SQLException {
        String sql = "DELETE FROM users WHERE username = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }
    }

    public List<User> list() throws SQLException {
        String sql = "SELECT " + SELECT_COLS + FROM_USERS + " ORDER BY created_at DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            java.util.ArrayList<User> list = new java.util.ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

    public void updateLastLogin(String username) throws SQLException {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }
    }

    public boolean isPasswordChangeRequired(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM password_change_required WHERE user_id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public void setPasswordChangeRequired(int userId) throws SQLException {
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO password_change_required (user_id) VALUES (?)")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (DatabaseManager.isDuplicateKey(e)) return;
            throw e;
        }
    }

    public void clearPasswordChangeRequired(int userId) throws SQLException {
        String sql = "DELETE FROM password_change_required WHERE user_id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"),
            rs.getString("role"), rs.getBoolean("enabled"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
            rs.getTimestamp("last_login") != null ? rs.getTimestamp("last_login").toLocalDateTime() : null
        );
    }
}
