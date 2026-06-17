package com.danhaywood.aregress;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * HTTP client for a Causeway app's automation metamodel-rebuild endpoint.
 *
 * Issues {@code POST {base}/api/automation/rebuild/{target}} (HTTP Basic Auth, no body) to force the
 * app to rebuild the metamodel of the given target object. Used immediately before replaying a
 * "navigate to one of" command, whose choices resolve against that metamodel — a stale metamodel
 * can otherwise cause a spurious replay failure or false database divergence.
 */
public class AppRebuildClient {

    private final String rebuildBaseUrl;
    private final String label;
    private final String basicAuth;
    private final HttpClient http;

    public AppRebuildClient(String baseUrl, String username, String password) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.rebuildBaseUrl = base + "/api/automation/rebuild/";
        this.label = base;
        this.basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * POST to rebuild the metamodel of {@code targetBookmark}. The bookmark is URL-path-encoded so
     * characters such as {@code :} (e.g. {@code party.Organisation:123}) travel as a single path
     * segment value.
     *
     * @throws RebuildException if the request fails or the response is non-200.
     */
    public void rebuild(String targetBookmark) {
        String url = rebuildBaseUrl + encodePathSegment(targetBookmark);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", basicAuth)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RebuildException("rebuild request to " + label + " for target " + targetBookmark
                    + " failed: " + e.getMessage(), e);
        }
        if (response.statusCode() != 200) {
            throw new RebuildException("rebuild on " + label + " for target " + targetBookmark
                    + " returned HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
    }

    /** Percent-encode for use as a single URL path segment ({@code +}→{@code %20} for spaces). */
    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /** Signals a failure to rebuild a target's metamodel, distinct from a replay failure or divergence. */
    public static class RebuildException extends RuntimeException {
        public RebuildException(String message) {
            super(message);
        }

        public RebuildException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
