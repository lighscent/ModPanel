package com.xl1te.modpanel.web.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class PasswordHasher {

    private static final int COST = 12;

    public static String hash(String password) {
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray());
    }

    public static boolean verify(String password, String hash) {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
    }

    /** Hash factice : vérifié pour les pseudos inconnus afin d'uniformiser le temps de réponse. */
    public static final String DUMMY_HASH = hash("modpanel.invalid.login.dummy");
}