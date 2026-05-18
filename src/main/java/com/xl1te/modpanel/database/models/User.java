package com.xl1te.modpanel.database.models;

import java.time.LocalDateTime;
import java.util.Objects;

public final class User {
    private final int id;
    private final String username;
    private final String passwordHash;
    private final String role;
    private final boolean enabled;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastLogin;

    public User(int id, String username, String passwordHash, String role, boolean enabled,
                LocalDateTime createdAt, LocalDateTime lastLogin) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.lastLogin = lastLogin;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLogin() { return lastLogin; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User u)) return false;
        return id == u.id && Objects.equals(username, u.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }
}
