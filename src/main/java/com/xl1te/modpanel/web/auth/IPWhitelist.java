package com.xl1te.modpanel.web.auth;

import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.utils.ExpiringCache;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public class IPWhitelist implements Handler {

    private static final long CACHE_TTL_MS = 30_000;

    private final IPBanManager banManager;
    private final DatabaseManager databaseManager;
    private final BestLogger logger;
    private final boolean proxyMode;
    private final ExpiringCache<String, Boolean> whitelistCache;

    public IPWhitelist(IPBanManager banManager, DatabaseManager databaseManager, BestLogger logger, boolean proxyMode) {
        this.banManager = banManager;
        this.databaseManager = databaseManager;
        this.logger = logger;
        this.proxyMode = proxyMode;
        this.whitelistCache = new ExpiringCache<>(CACHE_TTL_MS);
    }

    @Override
    public void handle(Context ctx) {
        String clientIP = getClientIP(ctx);
        logger.info("Incoming connection attempt from " + clientIP);

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
            banManager.checkConnection(clientIP);
        } catch (IPBanException e) {
            logger.warning("IP " + clientIP + " blocked: " + e.getMessage());
            ctx.status(403).result(e.getMessage());
            return;
        }

        logger.warning("Access denied for non-whitelisted IP: " + clientIP);
        ctx.status(403).result("Access denied. Your IP is not whitelisted.");
    }

    public void invalidateCache(String ip) {
        whitelistCache.invalidate(ip);
    }

    private String getClientIP(Context ctx) {
        if (proxyMode) {
            String xForwardedFor = ctx.header("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }
        return ctx.req().getRemoteAddr();
    }
}
