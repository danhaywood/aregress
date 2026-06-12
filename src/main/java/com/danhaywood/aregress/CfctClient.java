package com.danhaywood.aregress;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * HTTP client for cfct's automation REST API.
 *
 * A single GET to {@code /api/automation/comparison.json} triggers a server-side refresh
 * (footprint comparison of the newest successful command) and returns the result as JSON,
 * in the same format produced by cfct's UI "Download" action. Secured with HTTP Basic Auth
 * (realm "CFCT Automation").
 */
public class CfctClient {

    private final String comparisonUrl;
    private final String basicAuth;
    private final HttpClient http;

    public CfctClient(String baseUrl, String username, String password) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.comparisonUrl = base + "/api/automation/comparison.json";
        this.basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * GET the latest comparison — the endpoint refreshes first, then returns the result.
     *
     * @throws CfctApiException on any non-200 response or transport error (distinct from a
     *         detected database divergence, which is signalled by {@code hasDifferences}).
     */
    public ComparisonResult latestComparison() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(comparisonUrl))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", basicAuth)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new CfctApiException("cfct automation request failed: " + comparisonUrl, e);
        }
        if (response.statusCode() != 200) {
            throw new CfctApiException("cfct automation returned HTTP " + response.statusCode()
                    + " for " + comparisonUrl + ": " + truncate(response.body()));
        }
        return ComparisonResult.parse(response.body());
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /** Signals an automation-API failure, distinct from a detected database divergence. */
    public static class CfctApiException extends RuntimeException {
        public CfctApiException(String message) {
            super(message);
        }

        public CfctApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
