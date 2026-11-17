package com.xl1te.modpanel.web.auth;

import io.javalin.http.Context;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SessionManager {

    private static final long SESSION_TTL_MS = 86_400_000L;
    private static final long PURGE_INTERVAL_MIN = 30L;

    public static final String SESSION_COOKIE = "modpanel_session";

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> tokensByUser = new ConcurrentHashMap<>();
    private ScheduledExecutorService purger;

    public synchronized void start() {
        if (purger != null)
            return;
        purger = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ModPanel-SessionPurge");
            t.setDaemon(true);
            return t;
        });
        purger.scheduleAtFixedRate(this::purgeExpired, PURGE_INTERVAL_MIN, PURGE_INTERVAL_MIN, TimeUnit.MINUTES);
    }

    public synchronized void stop() {
        if (purger != null) {
            purger.shutdownNow();
            purger = null;
        }
    }

    public String createSession(String username, int userId, String role, Set<String> permissions,
            boolean enabled) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(username, System.currentTimeMillis(), userId, role,
                Set.copyOf(permissions), enabled));
        tokensByUser.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(token);
        return token;
    }

    public Session getSession(String token) {
        Session s = sessions.get(token);
        if (s == null)
            return null;
        if (System.currentTimeMillis() - s.createdAt > SESSION_TTL_MS) {
            sessions.remove(token, s);
            removeTokenFromIndex(s.username(), token);
            return null;
        }
        return s;
    }

    public void removeSession(String token) {
        Session s = sessions.remove(token);
        if (s != null)
            removeTokenFromIndex(s.username(), token);
    }

    public static String sessionCookieHeader(String token) {
        return SESSION_COOKIE + "=" + token + "; Path=/; Max-Age=" + (SESSION_TTL_MS / 1000)
                + "; HttpOnly; SameSite=Strict";
    }

    public static String clearSessionCookieHeader() {
        return SESSION_COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict";
    }

    public static String extractToken(Context ctx) {
        String cookie = ctx.cookie(SESSION_COOKIE);
        if (cookie != null && !cookie.isBlank())
            return cookie;
        String auth = ctx.header("Authorization");
        if (auth != null && auth.startsWith("Bearer "))
            return auth.substring(7);
        return null;
    }

    public void removeSessionsForUser(String username) {
        Set<String> tokens = tokensByUser.remove(username);
        if (tokens != null)
            tokens.forEach(sessions::remove);
    }

    public void refreshUserSessions(String username, int userId, String role, Set<String> permissions,
            boolean enabled) {
        Set<String> tokens = tokensByUser.get(username);
        if (tokens == null)
            return;
        Set<String> frozen = Set.copyOf(permissions);
        tokens.forEach(token -> sessions.computeIfPresent(token,
                (t, s) -> new Session(username, s.createdAt(), userId, role, frozen, enabled)));
    }

    public boolean isValidSession(String token) {
        return getSession(token) != null;
    }

    void purgeExpired() {
        long now = System.currentTimeMillis();
        sessions.forEach((token, s) -> {
            if (now - s.createdAt > SESSION_TTL_MS) {
                sessions.remove(token, s);
                removeTokenFromIndex(s.username(), token);
            }
        });
    }

    private void removeTokenFromIndex(String username, String token) {
        tokensByUser.computeIfPresent(username, (k, set) -> {
            set.remove(token);
            return set.isEmpty() ? null : set;
        });
    }

    public record Session(String username, long createdAt, int userId, String role, Set<String> permissions,
            boolean enabled) {
    }
}
