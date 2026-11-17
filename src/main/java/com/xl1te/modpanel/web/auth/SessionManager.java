package com.xl1te.modpanel.web.auth;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SessionManager {

    private static final long SESSION_TTL_MS = 86_400_000L;
    private static final long PURGE_INTERVAL_MIN = 30L;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> tokensByUser = new ConcurrentHashMap<>();
    private ScheduledExecutorService purger;

    /** Démarre le balayage périodique des tokens expirés. Idempotent (reload). */
    public synchronized void start() {
        if (purger != null) return;
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

    public String createSession(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(username, System.currentTimeMillis()));
        tokensByUser.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(token);
        return token;
    }

    public Session getSession(String token) {
        Session s = sessions.get(token);
        if (s == null) return null;
        if (System.currentTimeMillis() - s.createdAt > SESSION_TTL_MS) {
            sessions.remove(token, s);
            removeTokenFromIndex(s.username(), token);
            return null;
        }
        return s;
    }

    public void removeSession(String token) {
        Session s = sessions.remove(token);
        if (s != null) removeTokenFromIndex(s.username(), token);
    }

    /** Révoque toutes les sessions d'un utilisateur (mot de passe, désactivation, suppression). */
    public void removeSessionsForUser(String username) {
        Set<String> tokens = tokensByUser.remove(username);
        if (tokens != null) tokens.forEach(sessions::remove);
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

    public record Session(String username, long createdAt) {}
}
