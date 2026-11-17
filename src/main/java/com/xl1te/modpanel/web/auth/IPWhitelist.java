package com.xl1te.modpanel.web.auth;

import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.utils.ExpiringCache;
import com.xl1te.modpanel.web.ClientIp;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;

import java.util.List;

public class IPWhitelist implements Handler {

    private static final long CACHE_TTL_MS = 30_000;

    private final ConnectionLimiter connectionLimiter;
    private final DatabaseManager databaseManager;
    private final BestLogger logger;
    private final boolean proxyMode;
    private final boolean enabled;
    private final List<String> configWhitelist;
    private final ExpiringCache<String, Boolean> whitelistCache;

    public IPWhitelist(ConnectionLimiter connectionLimiter, DatabaseManager databaseManager, BestLogger logger, boolean proxyMode,
            boolean enabled, List<String> configWhitelist) {
        this.connectionLimiter = connectionLimiter;
        this.databaseManager = databaseManager;
        this.logger = logger;
        this.proxyMode = proxyMode;
        this.enabled = enabled;
        this.configWhitelist = configWhitelist;
        this.whitelistCache = new ExpiringCache<>(CACHE_TTL_MS);
    }

    @Override
    public void handle(Context ctx) {
        if (!enabled) {
            return;
        }

        String clientIP = getClientIP(ctx);
        logger.info("Incoming connection attempt from " + clientIP);

        if (configWhitelist.contains(clientIP)) {
            logger.info("IP " + clientIP + " allowed by config whitelist.");
            return;
        }

        Boolean cached = whitelistCache.get(clientIP);
        if (Boolean.TRUE.equals(cached)) {
            return;
        }
        if (cached == null) {
            try {
                boolean allowed = databaseManager.getWebWhitelistRepository().exists(clientIP);
                whitelistCache.put(clientIP, allowed);
                if (allowed) {
                    logger.info("IP " + clientIP + " allowed by whitelist.");
                    return;
                }
            } catch (Exception e) {
                logger.warning("Database error checking whitelist for " + clientIP + ": " + e.getMessage());
            }
        }

        try {
            connectionLimiter.checkConnection(clientIP);
        } catch (IPBanException e) {
            logger.warning("IP " + clientIP + " blocked: " + e.getMessage());
            throw new ForbiddenResponse(e.getMessage());
        }

        logger.warning("Access denied for non-whitelisted IP: " + clientIP);
        throw new ForbiddenResponse("Access denied. Your IP is not whitelisted.");
    }

    public void invalidateCache(String ip) {
        whitelistCache.invalidate(ip);
    }

    private String getClientIP(Context ctx) {
        return ClientIp.extract(ctx, proxyMode);
    }
}
