package com.xl1te.modpanel.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class VersionCheck {

    public boolean isUpdateAvailable() {
        String current = getCurrentVersion();
        String latest = getLatestVersion();
        if (current == null || latest == null)
            return false;
        return !current.equals(latest);
    }

    private String getCurrentVersion() {
        try (InputStream is = getClass().getResourceAsStream("/plugin.yml")) {
            if (is == null)
                return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("version:")) {
                    return line.substring(line.indexOf(":") + 1).trim();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getLatestVersion() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/lighscent/ModPanel/tags"))
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Gson gson = new Gson();
                JsonArray tags = gson.fromJson(response.body(), JsonArray.class);
                if (tags.size() > 0) {
                    JsonObject latest = tags.get(0).getAsJsonObject();
                    return latest.get("name").getAsString();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
