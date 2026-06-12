package com.danhaywood.aregress;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

/**
 * HTTP client for a Causeway app's automation import endpoint.
 *
 * Posts a command recording to {@code POST {base}/api/automation/import} (HTTP Basic Auth) and
 * returns the baseline timestamp the app assigns to the imported batch — the value to substitute
 * into {@code …/CommandReplayManager:{timestamp}}.
 */
public class AppImportClient {

    private static final Gson GSON = new Gson();

    private final String importUrl;
    private final String label;
    private final String basicAuth;
    private final HttpClient http;

    public AppImportClient(String baseUrl, String username, String password) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.importUrl = base + "/api/automation/import";
        this.label = base;
        this.basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * POST the recording and return the baseline timestamp from the JSON response.
     *
     * @throws ImportException if the file can't be read, the request fails, the response is
     *         non-200, or it carries no usable {@code timestamp}.
     */
    public String importRecording(Path file) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(importUrl))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", basicAuth)
                    .header("Content-Type", "application/xml")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofFile(file))
                    .build();
        } catch (Exception e) {
            throw new ImportException("cannot read recording file " + file + ": " + e.getMessage(), e);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ImportException("import request to " + importUrl + " failed: " + e.getMessage(), e);
        }
        if (response.statusCode() != 200) {
            throw new ImportException("import to " + label + " returned HTTP " + response.statusCode()
                    + ": " + truncate(response.body()));
        }

        String timestamp;
        try {
            JsonObject body = GSON.fromJson(response.body(), JsonObject.class);
            timestamp = body.get("timestamp").getAsString();
        } catch (Exception e) {
            throw new ImportException("import to " + label + " returned an unparseable response: "
                    + truncate(response.body()), e);
        }
        if (timestamp == null || timestamp.isBlank()) {
            throw new ImportException("import to " + label + " returned no timestamp: " + truncate(response.body()));
        }
        return timestamp;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /** Signals a failure to import a recording, distinct from a replay failure or divergence. */
    public static class ImportException extends RuntimeException {
        public ImportException(String message) {
            super(message);
        }

        public ImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
