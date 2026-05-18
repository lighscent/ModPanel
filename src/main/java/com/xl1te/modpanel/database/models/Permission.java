package com.xl1te.modpanel.database.models;

import java.util.Objects;

public final class Permission {
    private final int id;
    private final int userId;
    private final String permission;

    public Permission(int id, int userId, String permission) {
        this.id = id;
        this.userId = userId;
        this.permission = permission;
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getPermission() { return permission; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission p)) return false;
        return id == p.id && userId == p.userId && Objects.equals(permission, p.permission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, permission);
    }
}