package com.xl1te.modpanel.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.xl1te.modpanel.utils.GsonProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class VersionCheck {

    private final BestLogger logger;
    private final String currentVersion;
    private final ExecutorService executor;
    private static final String GITHUB_API_URL = "https://api.github.com/repos/lighscent/ModPanel/tags";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson GSON = GsonProvider.get();

    public VersionCheck(BestLogger logger, String currentVersion, ExecutorService executor) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.executor = executor;
    }

    public void checkForUpdates() {
        CompletableFuture.runAsync(() -> {
            String latest = getLatestVersion();

            if (latest == null)
                return;

            if (isNewer(currentVersion, latest)) {
                logger.severe(" A new update for ModPanel is available!");
                logger.severe(" Current version is: " + currentVersion + " and latest is: " + latest);
                logger.severe(" Download: https://modrinth.com/plugin/modpanel");
            }
        }, executor);
    }

    private boolean isNewer(String local, String remote) {
        try {
            String cleanLocal = local.replaceAll("[^\\d.]", "");
            String cleanRemote = remote.replaceAll("[^\\d.]", "");

            String[] localParts = cleanLocal.split("\\.");
            String[] remoteParts = cleanRemote.split("\\.");

            int length = Math.max(localParts.length, remoteParts.length);
            for (int i = 0; i < length; i++) {
                int localVal = i < localParts.length ? Integer.parseInt(localParts[i]) : 0;
                int remoteVal = i < remoteParts.length ? Integer.parseInt(remoteParts[i]) : 0;

                if (remoteVal > localVal)
                    return true;
                if (remoteVal < localVal)
                    return false;
            }
        } catch (NumberFormatException e) {
            return !local.equalsIgnoreCase(remote);
        }
        return false;
    }

    private String getLatestVersion() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "ModPanel-UpdateChecker")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonArray tags = GSON.fromJson(response.body(), JsonArray.class);
                if (tags != null && tags.size() > 0) {
                    return tags.get(0).getAsJsonObject().get("name").getAsString();
                }
            }
        } catch (Exception e) {
            logger.warning("Could not check for updates: " + e.getMessage());
        }
        return null;
    }
}