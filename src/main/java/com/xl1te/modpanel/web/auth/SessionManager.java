package com.xl1te.modpanel.web.auth;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.time.Instant;

public class SessionManager {

    private static final long SESSION_TTL_MS = 86_400_000L;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public String createSession(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(username, Instant.now().toEpochMilli()));
        return token;
    }

    public Session getSession(String token) {
        Session s = sessions.get(token);
        if (s == null) return null;
        if (System.currentTimeMillis() - s.createdAt > SESSION_TTL_MS) {
            sessions.remove(token);
            return null;
        }
        return s;
    }

    public void removeSession(String token) {
        sessions.remove(token);
    }

    public boolean isValidSession(String token) {
        return getSession(token) != null;
    }

    public record Session(String username, long createdAt) {}
}