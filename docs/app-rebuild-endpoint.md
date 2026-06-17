# App rebuild endpoint (for "navigate to one of" replay)

"Navigate to one of" actions resolve their choices against the **target object's metamodel**. If that
metamodel is stale on an app instance, the action can replay against the wrong choice (or none),
producing a spurious replay failure or a false database divergence. So before replaying any command
whose logical member identifier carries the prefix `__causeway_navigate_to_one_of_` or (legacy)
`__isis_navigate_to_one_of_`, `aregress` forces a metamodel rebuild of that command's **target
bookmark** on **both** apps (app-a's rebuild before app-a's replay, app-b's before app-b's).

This behaviour is controlled by `aregress.rebuild.enabled` (default `true`); set it to `false` to
disable the rebuild entirely (e.g. once Causeway is fixed to make it unnecessary).

This document is the contract plus a copy/paste Spring Boot 2.7.x stub for that endpoint. Wiring the
actual metamodel rebuild is yours (the one `TODO`).

## Contract

- **Method / path**: `POST {app-base}/api/automation/rebuild/{target}`
- **`{target}`**: the command's target **bookmark** string (e.g. `party.Organisation:123`),
  URL-path-encoded by `aregress` so characters such as `:` travel as a single path segment.
- **Auth**: HTTP Basic, validated against the app's existing user store. `aregress` sends the
  Causeway `--username` / `--password` (the same credentials it logs in / imports with).
- **Request body**: none.
- **Response**: `200 OK` once the target's metamodel has been rebuilt. `aregress` treats `200` as
  "rebuild complete" and then proceeds to replay; the rebuild SHOULD therefore be synchronous.
  The response body is ignored (`application/json` is accepted but not required).
- **Errors**: `401` (auth), `404`/`4xx` (unknown or un-resolvable target), `5xx` (rebuild failed).
  `aregress` treats any non-`200` as a fatal rebuild error and exits `2` **before** replaying the
  command (distinct from a replay failure `1` or a database divergence `1`).

## Stub (Spring Boot 2.7.x — `javax.*`, Spring Security 5.7)

### Controller

```java
package com.example.estatio.automation; // adjust to your package

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation")
public class AregressRebuildController {

    // TODO (1): inject whatever service can rebuild the metamodel for a single target object,
    //           e.g. the Causeway MetaModelService / SpecificationLoader, or a bespoke service.
    // private final MetaModelRebuildService rebuildService;

    /**
     * Rebuild the metamodel of the given target object so that a subsequent "navigate to one of"
     * command replays against an up-to-date metamodel.
     *
     * @param target the target object's bookmark, e.g. "party.Organisation:123"
     *               (aregress URL-path-encodes it; Spring decodes it back to the raw bookmark).
     */
    @PostMapping(value = "/rebuild/{target}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public void rebuild(@PathVariable String target) {
        // TODO (2): resolve `target` (a Bookmark string "logicalTypeName:identifier") and rebuild
        //           the metamodel for that object's domain type, synchronously. On success, return
        //           200 (a void body is fine). Throw / return a non-200 if the target can't be
        //           resolved or the rebuild fails.
        // rebuildService.rebuild(Bookmark.parse(target).orElseThrow());
    }
}
```

### Security

The existing automation Basic-Auth chain (see `app-import-endpoint.md`,
`AregressAutomationSecurityConfig`) already matches `/api/automation/**`, so it covers
`/api/automation/rebuild/**` too — **no new security configuration is needed**. The Basic-Auth user
is the same one `aregress` logs in with (`--username` / `--password`).

Notes:
- `{target}` is a single path segment. Spring decodes the percent-encoding `aregress` applies, so the
  `@PathVariable` is the raw bookmark string (e.g. `party.Organisation:123`). If your Spring/Tomcat
  config rejects encoded characters in the path, prefer accepting the bookmark as a request param
  instead (`/rebuild?target=...`) and have `aregress` send it accordingly.
- Keep the rebuild **synchronous**: `aregress` replays as soon as it sees `200`.
