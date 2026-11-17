package com.xl1te.modpanel.web;

import io.javalin.http.Context;

/** Extraction unique de l'IP cliente : X-Forwarded-For seulement en proxy-mode. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String extract(Context ctx, boolean proxyMode) {
        if (proxyMode) {
            String xForwardedFor = ctx.header("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }
        return ctx.req().getRemoteAddr();
    }
}
