package com.xl1te.modpanel.web.auth;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Limiteur de connexions par IP, entièrement en mémoire : aucune lecture ni
 * écriture en base sur le chemin des requêtes. Fenêtre fixe, bannissement
 * temporaire puis permanent au-delà du seuil.
 */
public class ConnectionLimiter {

    private static final int MAX_ENTRIES = 10_000;

    private final boolean enabled;
    private final int maxAttempts;
    private final long windowMillis;
    private final long banDurationSeconds;
    private final long banDurationMillis;
    private final int permanentBanThreshold;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public ConnectionLimiter(boolean enabled, int maxAttempts, long windowSeconds,
            long banDurationSeconds, int permanentBanThreshold) {
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
        this.banDurationSeconds = banDurationSeconds;
        this.banDurationMillis = banDurationSeconds * 1000L;
        this.permanentBanThreshold = permanentBanThreshold;
    }

    public void checkConnection(String clientIP) {
        if (!enabled)
            return;
        long now = System.currentTimeMillis();
        State s = states.computeIfAbsent(clientIP, k -> new State());
        synchronized (s) {
            if (s.permanent) {
                throw new IPBanException("Your IP has been permanently banned.", true);
            }
            if (s.bannedUntil > now) {
                long remaining = (s.bannedUntil - now) / 1000L;
                throw new IPBanException("Too many requests; retry after " + remaining + " seconds.", false);
            }
            if (s.bannedUntil != 0) {
                s.bannedUntil = 0;
                s.count = 0;
                s.windowStart = now;
            } else if (now - s.windowStart >= windowMillis) {
                s.count = 0;
                s.windowStart = now;
            }
            s.count++;
            if (s.count >= maxAttempts) {
                s.banCount++;
                s.count = 0;
                s.windowStart = now;
                if (s.banCount >= permanentBanThreshold) {
                    s.permanent = true;
                    throw new IPBanException("Your IP has been permanently banned.", true);
                }
                s.bannedUntil = now + banDurationMillis;
                throw new IPBanException(
                        "Too many requests; your IP is temporarily banned for " + banDurationSeconds + " seconds.",
                        false);
            }
        }
        if (states.size() > MAX_ENTRIES)
            purge(now);
    }

    private void purge(long now) {
        states.entrySet().removeIf(e -> {
            State s = e.getValue();
            synchronized (s) {
                return !s.permanent && s.banCount == 0 && s.bannedUntil <= now
                        && now - s.windowStart >= windowMillis;
            }
        });
    }

    private static class State {
        int count;
        long windowStart;
        long bannedUntil;
        int banCount;
        boolean permanent;
    }
}
