package com.danhaywood.aregress;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
