package com.xl1te.modpanel.web.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import com.xl1te.modpanel.utils.GsonProvider;
import io.javalin.http.Context;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Dépendances et helpers partagés par les handlers de l'API web : parsing JSON,
 * validation, pont vers le thread principal Bukkit, réponses JSON.
 */
public class ApiSupport {

    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_PASSWORD = "password";
    public static final String SESSION_ATTRIBUTE = "session";

    // Bornes alignées sur le schéma (users.username VARCHAR(50), role VARCHAR(20),
    // user_permissions.permission VARCHAR(100)) + garde-fou mot de passe.
    public static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{3,50}");
    public static final Pattern ROLE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,20}");
    public static final Pattern PERMISSION_PATTERN = Pattern.compile("[A-Za-z0-9._*-]{1,100}");
    public static final int MAX_PASSWORD_LEN = 128;
    public static final int MAX_LOGIN_NAME_LEN = 50;

    /** Délai max d'attente du thread principal (collecte Bukkit). */
    private static final long SYNC_TIMEOUT_SECONDS = 5;

    private static final Type BODY_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final Main plugin;
    private final BestLogger logger;

    public ApiSupport(Main plugin, BestLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public Main plugin() {
        return plugin;
    }

    public BestLogger logger() {
        return logger;
    }

    public Gson gson() {
        return GsonProvider.get();
    }

    /**
     * Corps JSON de la requête. Corps absent ou invalide → map vide, pour que les
     * handlers répondent 400 au lieu de 500.
     */
    public Map<String, Object> jsonBody(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(BODY_TYPE);
            return body != null ? body : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Valeur string d'un champ JSON, ou null si absent/nul. */
    public static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    public static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Exécute la collecte sur le thread principal Bukkit et attend le résultat.
     * L'API Bukkit n'est pas thread-safe : les handlers Javalin tournent sur des
     * threads Jetty et ne doivent jamais toucher joueurs, mondes ou inventaires.
     */
    public <T> T callSync(Callable<T> task) throws Exception {
        Future<T> future = plugin.getServer().getScheduler().callSyncMethod(plugin, task);
        try {
            return future.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    public double getTPS() {
        try {
            Object provider = plugin.getServer().getClass().getMethod("getTPS").invoke(plugin.getServer());
            if (provider instanceof double[] arr)
                return arr[0];
            if (provider instanceof List<?> list && !list.isEmpty()) {
                Object val = list.get(0);
                if (val instanceof Double)
                    return (Double) val;
            }
            return 20.0;
        } catch (Exception e) {
            return 20.0;
        }
    }

    public static void error(Context ctx, int status, String message) {
        ctx.status(status).result(GsonProvider.get().toJson(
                Map.of("error", message == null ? "Error" : message)));
    }

    public static void ok(Context ctx) {
        ctx.result("{\"ok\":true}");
    }
}
