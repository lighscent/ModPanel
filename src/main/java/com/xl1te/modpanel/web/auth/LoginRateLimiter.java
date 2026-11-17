package com.xl1te.modpanel.web.auth;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Limiteur de tentatives de connexion, en mémoire, par IP + pseudo.
 * Fenêtre fixe : au-delà de maxAttempts échecs, 429 jusqu'à expiration.
 */
public class LoginRateLimiter {

    private static final int MAX_ENTRIES = 10_000;

    private final boolean enabled;
    private final int maxAttempts;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(boolean enabled, int maxAttempts, long windowSeconds) {
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
    }

    public boolean isBlocked(String key) {
        if (!enabled) return false;
        Attempt a = attempts.get(key);
        if (a == null) return false;
        long now = System.currentTimeMillis();
        if (now - a.windowStart >= windowMillis) {
            attempts.remove(key, a);
            return false;
        }
        return a.count >= maxAttempts;
    }

    public void recordFailure(String key) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        attempts.compute(key, (k, a) -> {
            if (a == null || now - a.windowStart >= windowMillis) return new Attempt(1, now);
            return new Attempt(a.count + 1, a.windowStart);
        });
        if (attempts.size() > MAX_ENTRIES) purge(now);
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    public long retryAfterSeconds(String key) {
        Attempt a = attempts.get(key);
        if (a == null) return 0;
        return Math.max((a.windowStart + windowMillis - System.currentTimeMillis()) / 1000L, 1);
    }

    private void purge(long now) {
        attempts.entrySet().removeIf(e -> now - e.getValue().windowStart >= windowMillis);
    }

    private record Attempt(int count, long windowStart) {}
}
