package com.xl1te.modpanel.web.auth;

public class IPBanException extends RuntimeException {
    private final boolean permanent;

    public IPBanException(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
