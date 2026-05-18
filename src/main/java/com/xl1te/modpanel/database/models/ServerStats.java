package com.xl1te.modpanel.database.models;

import java.time.Instant;

public final class ServerStats {
    private final int playerCount;
    private final int maxPlayers;
    private final double tps;
    private final String mcVersion;
    private final String serverName;
    private final Instant timestamp;

    public ServerStats(int playerCount, int maxPlayers, double tps, String mcVersion, String serverName) {
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.tps = tps;
        this.mcVersion = mcVersion;
        this.serverName = serverName;
        this.timestamp = Instant.now();
    }

    public int getPlayerCount() { return playerCount; }
    public int getMaxPlayers() { return maxPlayers; }
    public double getTps() { return tps; }
    public String getMcVersion() { return mcVersion; }
    public String getServerName() { return serverName; }
    public Instant getTimestamp() { return timestamp; }
}
