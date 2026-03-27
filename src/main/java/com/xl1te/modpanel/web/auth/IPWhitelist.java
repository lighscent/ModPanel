package com.xl1te.modpanel.web.auth;

import com.xl1te.modpanel.utils.BestLogger;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.util.List;

public class IPWhitelist implements Handler {

    private final List<String> allowedIPs;
    private final BestLogger logger;

    public IPWhitelist(List<String> allowedIPs, BestLogger logger) {
        this.allowedIPs = allowedIPs;
        this.logger = logger;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        String clientIP = getClientIP(ctx);
        if (!allowedIPs.contains(clientIP)) {
            logger.warning("Access denied for IP: " + clientIP);
            throw new io.javalin.http.ForbiddenResponse("Access denied. Your IP is not whitelisted.");
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
