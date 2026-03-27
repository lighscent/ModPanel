package com.xl1te.modpanel.web.auth;

import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.database.repository.IPBanRepository;
import com.xl1te.modpanel.database.models.IPBanEntry;

import java.sql.SQLException;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;

public class IPBanManager {
    private final boolean enabled;
    private final int banAfter;
    private final int banDurationSeconds;
    private final int permanentBanThreshold;
    private final BestLogger logger;
    private final IPBanRepository banRepository;

    public IPBanManager(JavaPlugin plugin, BestLogger logger, IPBanRepository banRepository) {
        this.logger = logger;
        this.banRepository = banRepository;
        this.enabled = plugin.getConfig().getBoolean("web-server.connection-limit.enabled", true);
        this.banAfter = plugin.getConfig().getInt("web-server.connection-limit.ban-after", 10);
        this.banDurationSeconds = plugin.getConfig().getInt("web-server.connection-limit.ban-duration", 600);
        this.permanentBanThreshold = plugin.getConfig().getInt("web-server.connection-limit.permanent-ban-threshold", 2);
    }

    public void checkConnection(String clientIP) {
        if (!enabled) {
            logger.info("IP ban system disabled; allowing connection for " + clientIP);
            return;
        }

        Instant now = Instant.now();
        IPBanEntry entry;
        try {
            entry = banRepository.findByIp(clientIP).orElse(new IPBanEntry(clientIP, 0, 0, null, false));
        } catch (SQLException e) {
            logger.warning("Failed to load ban entry for " + clientIP + "; allowing connection, but this may be insecure: " + e.getMessage());
            return;
        }

        if (entry.isPermanent()) {
            logger.warning("Permanently banned IP tried connection: " + clientIP);
            throw new IPBanException("Your IP has been permanently banned.", true);
        }

        if (entry.getBannedUntil() != null && entry.getBannedUntil().isAfter(now)) {
            long remaining = Duration.between(now, entry.getBannedUntil()).getSeconds();
            logger.warning("Temporarily banned IP tried connection: " + clientIP + " (" + remaining + "s left)");
            throw new IPBanException("Too many requests; retry after " + remaining + " seconds.", false);
        }

        if (entry.getBannedUntil() != null && !entry.getBannedUntil().isAfter(now)) {
            logger.info("Ban expired for IP " + clientIP + "; clearing ban state.");
            entry = new IPBanEntry(clientIP, 0, entry.getBanCount(), null, false);
        }

        int failures = entry.getFailureCount() + 1;

        if (failures >= banAfter) {
            int currentBanCount = entry.getBanCount() + 1;
            boolean isPermanent = currentBanCount >= permanentBanThreshold;
            Instant bannedUntil = isPermanent ? null : now.plusSeconds(banDurationSeconds);

            if (isPermanent) {
                logger.warning("IP " + clientIP + " reached permanent ban threshold (" + currentBanCount + " ban(s)).");
            } else {
                logger.warning("IP " + clientIP + " banned temporarily for " + banDurationSeconds + " seconds (ban count=" + currentBanCount + ").");
            }

            IPBanEntry updated = new IPBanEntry(clientIP, 0, currentBanCount, bannedUntil, isPermanent);
            try {
                banRepository.save(updated);
            } catch (SQLException e) {
                logger.severe("Failed to update ban state for " + clientIP + ": " + e.getMessage());
            }

            if (isPermanent) {
                throw new IPBanException("Your IP has been permanently banned.", true);
            } else {
                throw new IPBanException("Too many requests; your IP is temporarily banned for " + banDurationSeconds + " seconds.", false);
            }
        }

        IPBanEntry updated = new IPBanEntry(clientIP, failures, entry.getBanCount(), null, false);
        try {
            banRepository.save(updated);
        } catch (SQLException e) {
            logger.warning("Failed to persist failure count for " + clientIP + ": " + e.getMessage());
        }
    }

    public boolean isBanned(String clientIP) {
        try {
            IPBanEntry entry = banRepository.findByIp(clientIP).orElse(null);
            if (entry == null) {
                return false;
            }
            if (entry.isPermanent()) {
                return true;
            }
            if (entry.getBannedUntil() != null && entry.getBannedUntil().isAfter(Instant.now())) {
                return true;
            }
            if (entry.getBannedUntil() != null && !entry.getBannedUntil().isAfter(Instant.now())) {
                banRepository.save(new IPBanEntry(clientIP, 0, entry.getBanCount(), null, false));
            }
            return false;
        } catch (SQLException e) {
            logger.warning("Failed to evaluate ban status for " + clientIP + ": " + e.getMessage());
            return false;
        }
    }
}
