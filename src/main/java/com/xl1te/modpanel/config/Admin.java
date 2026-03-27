package com.xl1te.modpanel.config;

public class Admin {
    private final String ip;
    private final String username;
    private final String discordId;

    public Admin(String ip, String username, String discordId) {
        this.ip = ip;
        this.username = username;
        this.discordId = discordId;
    }

    public String getIp() {
        return ip;
    }

    public String getUsername() {
        return username;
    }

    public String getDiscordId() {
        return discordId;
    }

    @Override
    public String toString() {
        return "Admin{ip='" + ip + "', username='" + username + "', discordId='" + discordId + "'}";
    }
}
