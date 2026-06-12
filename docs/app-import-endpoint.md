# App import endpoint (for `aregress --file`)

When `aregress` is run with `--file <recording>`, it POSTs the recording to **each** app
(app-a and app-b) and uses the baseline timestamp each returns to drive replay. This document
is the contract plus a copy/paste Spring Boot 2.7.x stub for that endpoint. Wiring the actual
import into the `CommandReplayManager` is yours (the two `TODO`s).

## Contract

- **Method / path**: `POST {app-base}/api/automation/import`
- **Auth**: HTTP Basic, validated against the app's existing user store. `aregress` sends the
  Causeway `--username` / `--password`.
- **Request body**: the raw recording file bytes. `Content-Type: application/xml` (Causeway
  `CommandsDto`); the endpoint may accept any content type.
- **Response**: `200 OK`, `application/json`:
  ```json
  { "timestamp": "2026-06-11T12-23-56.614Z" }
  ```
  `timestamp` MUST be the exact string to substitute into
  `/wicket/entity/isis.ext.commandLog.CommandReplayManager:{timestamp}` (note the dashes in the
  time portion, not colons).
- **Errors**: `401` (auth), `4xx` (bad recording), `5xx` (import failed). `aregress` treats any
  non-`200` as a fatal import error and exits `2` before replaying.

## Stub (Spring Boot 2.7.x — `javax.*`, Spring Security 5.7)

### Controller

```java
package com.example.estatio.automation; // adjust to your package

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation")
public class AregressImportController {

    // TODO (1): inject whatever service can import a recording into the CommandReplayManager,
    //           e.g. the same path the "Import Commands…" UI action uses.
    // private final CommandReplayImportService importService;

    /**
     * Import a command recording and return the baseline timestamp used to drive replay.
     * Body = the raw recording file bytes posted by aregress.
     */
    @PostMapping(value = "/import",
            consumes = MediaType.ALL_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ImportResponse importRecording(@RequestBody byte[] recording) {
        // TODO (2): import `recording` into the CommandReplayManager, then return the baseline
        //           timestamp of the imported batch — formatted EXACTLY as it must appear in
        //           /wicket/entity/isis.ext.commandLog.CommandReplayManager:{timestamp}
        //           e.g. "2026-06-11T12-23-56.614Z"
        String timestamp = /* importService.importReturningBaselineTimestamp(recording) */ null;
        return new ImportResponse(timestamp);
    }

    // Java 8/11-safe DTO (use a record if the app is on Java 16+).
    public static class ImportResponse {
        private final String timestamp;
        public ImportResponse(String timestamp) { this.timestamp = timestamp; }
        public String getTimestamp() { return timestamp; }
    }
}
```

### Security (a dedicated Basic-Auth chain for the automation path)

```java
package com.example.estatio.automation; // adjust to your package

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class AregressAutomationSecurityConfig {

    // Ordered ahead of the app's existing (form-login) chain so it owns /api/automation/**.
    @Bean
    @Order(1)
    public SecurityFilterChain automationApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .antMatcher("/api/automation/**")                 // this chain handles only the automation API
            .csrf(csrf -> csrf.disable())                     // stateless API: aregress posts without a CSRF token
            .httpBasic(withDefaults())                        // HTTP Basic Auth (validated against the existing user store)
            .authorizeRequests(auth -> auth.anyRequest().authenticated())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
```

Notes:
- Ensure the existing security chain doesn't already match `/api/**`.
- The Basic-Auth user is the same one `aregress` logs in with (`--username` / `--password`).
- Prefer a multipart upload? Change the controller to `@RequestParam("file") MultipartFile file`;
  `aregress` would then need to switch to a multipart body (it sends a raw body by default).
