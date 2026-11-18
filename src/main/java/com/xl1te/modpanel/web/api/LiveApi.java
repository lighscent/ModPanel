package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.web.auth.SessionManager;
import com.xl1te.modpanel.web.auth.SessionManager.Session;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsCloseStatus;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsErrorContext;

/**
 * Cycle de vie du socket live (/api/live). Auth via le cookie de session
 * HttpOnly (envoyé automatiquement lors du handshake) + permission
 * {@code modpanel.players}, comme l'endpoint REST équivalent.
 */
public class LiveApi {

    private final SessionManager sessionManager;
    private final LiveHub hub;
    private final BestLogger logger;

    public LiveApi(SessionManager sessionManager, LiveHub hub, BestLogger logger) {
        this.sessionManager = sessionManager;
        this.hub = hub;
        this.logger = logger;
    }

    public void handleConnect(WsConnectContext ctx) {
        String token = ctx.cookie(SessionManager.SESSION_COOKIE);
        Session session = token == null ? null : sessionManager.getSession(token);
        if (session == null || !session.enabled()) {
            ctx.closeSession(WsCloseStatus.POLICY_VIOLATION, "Unauthorized");
            return;
        }
        if (!ApiSupport.hasPermission(session, "modpanel.players")) {
            ctx.closeSession(WsCloseStatus.POLICY_VIOLATION, "Forbidden");
            return;
        }
        hub.add(ctx.sessionId(), ctx);
        hub.sendSnapshot(ctx);
    }

    public void handleClose(WsCloseContext ctx) {
        hub.remove(ctx.sessionId());
    }

    public void handleError(WsErrorContext ctx) {
        hub.remove(ctx.sessionId());
        // Pendant l'arrêt, Jetty coupe les sockets : bruit normal, pas une erreur.
        if (!hub.isClosing() && ctx.error() != null && ctx.error().getMessage() != null) {
            logger.warning("Live socket error: " + ctx.error().getMessage());
        }
    }
}
