package com.xl1te.modpanel.database.models;

import java.time.LocalDateTime;
import java.util.Objects;

public final class WebWhitelistEntry {
    private final int id;
    private final String ip;
    private final LocalDateTime addedAt;

    public WebWhitelistEntry(int id, String ip, LocalDateTime addedAt) {
        this.id = id;
        this.ip = ip;
        this.addedAt = addedAt;
    }

    public int getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof WebWhitelistEntry))
            return false;
        WebWhitelistEntry that = (WebWhitelistEntry) o;
        return id == that.id && Objects.equals(ip, that.ip) && Objects.equals(addedAt, that.addedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ip, addedAt);
    }

    @Override
    public String toString() {
        return "WebWhitelistEntry{" +
                "id=" + id +
                ", ip='" + ip + '\'' +
                ", addedAt=" + addedAt +
                '}';
    }
}
