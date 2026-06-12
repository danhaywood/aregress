## Context

aregress drives app-a and app-b via Playwright and compares via cfct's REST API. The replay target is located by `--timestamp`, which assumes both apps were already primed (recording imported into each `CommandReplayManager`) out-of-band. This change lets aregress do the priming itself: given `--file <recording>`, it POSTs the file to an import endpoint on each app, which imports it and returns the baseline timestamp to drive the run.

The import endpoint is implemented by the app owner (Causeway / Spring Boot 2.7.x); this change delivers the **client** plus a **copy/paste endpoint stub + contract**.

## Goals / Non-Goals

**Goals:**
- `--file` as an alternative to `--timestamp` (exactly one required).
- Post the recording to app-a and app-b, obtain each app's baseline timestamp, and drive the run from it.
- Deliver a clear endpoint contract + a Spring Boot 2.7.x controller/security stub the owner can paste in.

**Non-Goals:**
- Implementing the import-into-`CommandReplayManager` logic (owner's task).
- Any change to the replay loop or the cfct comparison.
- Resetting/cleaning databases before import (assumed handled by the import or out-of-band).

## Decisions

### CLI: `--file` xor `--timestamp` (exactly one)
Use a Picocli exclusive `@ArgGroup` with `multiplicity = "1"` containing `--timestamp` and `--file`, so the framework enforces "exactly one" and produces a usage error otherwise.

### Import client over HTTP (JDK `HttpClient`)
A small `AppImportClient` (one per app, base URL + Basic-Auth credentials) with `importRecording(Path) -> String timestamp`. Reuses the same `HttpClient`/Basic-Auth approach as `CfctClient`. No new dependency (Gson parses the response).

### Contract: `POST {app}/api/automation/import`
- **Auth**: HTTP Basic, validated against the app's existing user store — aregress sends the Causeway `--username`/`--password`.
- **Request body**: the raw recording file bytes (`BodyPublishers.ofFile`). `Content-Type: application/xml` (Causeway `CommandsDto` is XML; the endpoint may accept any content type).
- **Response**: `200 OK`, `application/json`, `{ "timestamp": "<URL-ready baseline timestamp>" }` — the exact string to substitute into `…/CommandReplayManager:{timestamp}` (e.g. `2026-06-11T12-23-56.614Z`).
- **Errors**: `401` (auth), `4xx` (bad recording), `5xx` (import failed) — any non-`200` is a fatal import error for aregress.

### Per-app timestamp
aregress posts to **both** apps and uses **each app's own returned timestamp** for that app's replay URL (they should be identical for the same recording, but decoupling avoids assuming so).

### Flow & exit codes
- `--file`: `tsA = appAImport.importRecording(file)`, `tsB = appBImport.importRecording(file)`, then navigate app-a to `appA + path(tsA)`, app-b to `appB + path(tsB)`, then run the existing loop.
- `--timestamp`: `tsA = tsB = timestamp` (unchanged behaviour).
- An import failure aborts before the loop with a clear message and a distinct non-zero exit (`2`, as for other automation/infra errors).

### Endpoint stub (Spring Boot 2.7.x) — copy/paste

> The app owner pastes these into app-a and app-b, then fills the two `TODO`s. Spring Boot 2.7.x ⇒ `javax.*` (not `jakarta.*`) and Spring Security 5.7.

**Controller**

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

**Security (a dedicated Basic-Auth chain for the automation path)**

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

Notes for the owner: ensure the existing security chain doesn't already match `/api/**`; the Basic-Auth user is the same one aregress logs in with (`--username`/`--password`). If you prefer a multipart upload, change the controller to `@RequestParam("file") MultipartFile file` and aregress can switch to a multipart body — but the raw-body form above is what the client sends by default.

## Risks / Trade-offs

- **Contract drift** (path, field name, timestamp format) between this stub and the wired endpoint → import fails or the replay URL is wrong. Mitigation: the contract above is the single source of truth; client and stub are written to it.
- **CSRF / security misconfig** → the POST is rejected (e.g. 403/redirect to login). Mitigation: the dedicated `@Order(1)` Basic-Auth chain with CSRF disabled for `/api/automation/**`.
- **Timestamp format mismatch** (colons vs dashes) → broken `CommandReplayManager` URL. Mitigation: the endpoint returns the exact URL-ready string; aregress substitutes it verbatim.
- **Large recording / slow import** → set a generous client timeout.

## Migration Plan

1. Add the `--file`/`--timestamp` `@ArgGroup`; add `AppImportClient`.
2. Wire `Main`: when `--file`, import on both apps → per-app timestamps → navigate; else unchanged.
3. Add the standalone copy/paste doc (`docs/app-import-endpoint.md`) from the stub above.
4. Owner pastes the stub into app-a/app-b and implements the two TODOs; then smoke-test `--file`.
No rollback concern — `--timestamp` behaviour is unchanged.

## Open Questions

- Raw body vs multipart upload for the recording — default to raw body; switch if the owner prefers multipart.
- Should aregress verify app-a's and app-b's returned timestamps match and warn if not?
- Recording content type — `application/xml` assumed (Causeway `CommandsDto`); confirm.
