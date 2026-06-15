package com.danhaywood.aregress;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalized, non-secret settings, bound from Spring Boot configuration under the {@code aregress}
 * prefix ({@code application.yml}, environment variables, system properties). Defaults ship in the
 * bundled {@code application.yml}; CLI options (see {@link ReplayCommand}) override these when supplied.
 *
 * Secrets (the Causeway and cfct passwords) are intentionally NOT here — they come only from the CLI
 * option or an interactive prompt.
 */
@ConfigurationProperties(prefix = "aregress")
public class AregressProperties {

    /** Base URL for app-a. */
    private String appA = "http://localhost:8080";
    /** Base URL for app-b. */
    private String appB = "http://localhost:9090";
    /** Base URL of the cfct automation REST API. */
    private String cfct = "http://localhost:10010";
    /** Username for HTTP Basic Auth against the cfct automation API. */
    private String cfctUsername = "robot";
    /** Username for the Causeway app login (used for both apps) and for the import Basic-Auth. */
    private String username = "estatio-admin";
    /** Tuning for the per-step comparison race-guard. */
    private Compare compare = new Compare();

    /** Race-guard tuning: how hard to retry until cfct's reported command matches the replayed one. */
    public static class Compare {
        /** Maximum number of cfct comparison queries per step while waiting for the ids to match. */
        private int maxAttempts = 5;
        /** Delay between attempts. */
        private Duration retryDelay = Duration.ofSeconds(1);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getRetryDelay() {
            return retryDelay;
        }

        public void setRetryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay;
        }
    }

    public Compare getCompare() {
        return compare;
    }

    public void setCompare(Compare compare) {
        this.compare = compare;
    }

    public String getAppA() {
        return appA;
    }

    public void setAppA(String appA) {
        this.appA = appA;
    }

    public String getAppB() {
        return appB;
    }

    public void setAppB(String appB) {
        this.appB = appB;
    }

    public String getCfct() {
        return cfct;
    }

    public void setCfct(String cfct) {
        this.cfct = cfct;
    }

    public String getCfctUsername() {
        return cfctUsername;
    }

    public void setCfctUsername(String cfctUsername) {
        this.cfctUsername = cfctUsername;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
