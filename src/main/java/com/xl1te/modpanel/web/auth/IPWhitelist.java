package com.xl1te.modpanel.web.auth;

import com.xl1te.modpanel.database.DatabaseManager;
import com.xl1te.modpanel.utils.BestLogger;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;

public class IPWhitelist implements Handler {

    private final DatabaseManager databaseManager;
    private final BestLogger logger;

    public IPWhitelist(DatabaseManager databaseManager, BestLogger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        String clientIP = getClientIP(ctx);

        boolean allowed = databaseManager.getWebWhitelistRepository().exists(clientIP);
        if (!allowed) {
            logger.warning("Access denied for non-whitelisted IP: " + clientIP);
            throw new ForbiddenResponse("Access denied. Your IP is not whitelisted.");
        }
    }

    private String getClientIP(Context ctx) {
        String xForwardedFor = ctx.header("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return ctx.req().getRemoteAddr();
    }
}
