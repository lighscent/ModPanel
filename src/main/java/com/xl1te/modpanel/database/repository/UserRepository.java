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

    public void create(User user) throws SQLException {
        String sql;
        if (db.getDialect() == com.xl1te.modpanel.database.DatabaseManager.Dialect.MYSQL) {
            sql = "INSERT INTO users (username, password_hash, role, enabled) VALUES (?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE username=username";
        } else {
            sql = "MERGE INTO users (username, password_hash, role, enabled) KEY(username) VALUES (?, ?, ?, ?)";
        }
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getRole());
            stmt.setBoolean(4, user.isEnabled());
            stmt.executeUpdate();
        }
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

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"),
            rs.getString("role"), rs.getBoolean("enabled"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
            rs.getTimestamp("last_login") != null ? rs.getTimestamp("last_login").toLocalDateTime() : null
        );
    }
}
