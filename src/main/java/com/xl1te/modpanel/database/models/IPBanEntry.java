package com.xl1te.modpanel.database.models;

import java.time.Instant;
import java.util.Objects;

public final class IPBanEntry {
    private final String ip;
    private final int failureCount;
    private final int banCount;
    private final Instant bannedUntil;
    private final boolean permanent;

    public IPBanEntry(String ip, int failureCount, int banCount, Instant bannedUntil, boolean permanent) {
        this.ip = ip;
        this.failureCount = failureCount;
        this.banCount = banCount;
        this.bannedUntil = bannedUntil;
        this.permanent = permanent;
    }

    public String getIp() {
        return ip;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public int getBanCount() {
        return banCount;
    }

    public Instant getBannedUntil() {
        return bannedUntil;
    }

    public boolean isPermanent() {
        return permanent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof IPBanEntry))
            return false;
        IPBanEntry that = (IPBanEntry) o;
        return failureCount == that.failureCount && banCount == that.banCount && permanent == that.permanent
                && Objects.equals(ip, that.ip) && Objects.equals(bannedUntil, that.bannedUntil);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, failureCount, banCount, bannedUntil, permanent);
    }
}
