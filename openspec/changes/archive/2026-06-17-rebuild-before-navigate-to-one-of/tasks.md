## 1. Rebuild HTTP client

- [x] 1.1 Add `AppRebuildClient.java` modelled on `AppImportClient` (normalise base URL, build `Basic` auth header, `java.net.http.HttpClient`).
- [x] 1.2 Implement `rebuild(String targetBookmark)`: `POST {base}/api/automation/rebuild/{encodedTarget}` with no body; URL-path-encode the target bookmark so `:` etc. travel as one path segment.
- [x] 1.3 Treat any non-`200` response or transport error as a `RebuildException` (inner static class), with a message naming the app/target and HTTP status — mirroring `ImportException`.

## 2. Read the command's identifier and target from the page

- [x] 2.1 Add `oldestCommandLogicalMemberIdentifier()` to `CausewayReplayPage`, reading the oldest command row's logical member identifier (confirm the exact selector against the live CommandReplayManager page, as the existing selectors were).
- [x] 2.2 Add `oldestCommandTargetBookmark()` to `CausewayReplayPage`, reading the oldest command row's target bookmark string.
- [x] 2.3 Add a small helper to test the identifier for the `__causeway_navigate_to_one_of_` / `__isis_navigate_to_one_of_` prefixes.

## 3. Config toggle

- [x] 3.1 Add a nested `Rebuild` block to `AregressProperties` with `boolean enabled = true` and getter/setter, mirroring the existing `Compare` block; expose `getRebuild()`.
- [x] 3.2 Add `aregress.rebuild.enabled: true` to the bundled `application.yml`.

## 4. Wire rebuild into the replay loop

- [x] 4.1 In `ReplayCommand.call()`, construct an `AppRebuildClient` for app-a and one for app-b alongside the existing per-app objects (only needed when the toggle is enabled).
- [x] 4.2 Gate the rebuild logic on `props.getRebuild().isEnabled()`; when disabled, the loop behaves exactly as before.
- [x] 4.3 When enabled, at the top of each loop iteration read the oldest command's logical member identifier and, when it matches a prefix, its target bookmark.
- [x] 4.4 When matched, call the app-a rebuild client before `appAPage.replayNext()`, and the app-b rebuild client before `appBPage.replayNext()`.
- [x] 4.5 On `RebuildException`, print a clear error naming the app and target and return exit code 2 (no replay of that command); leave non-matching commands unchanged.

## 5. Documentation

- [x] 5.1 Add `docs/app-rebuild-endpoint.md` (contract: `POST /api/automation/rebuild/{target}`, Basic Auth, no body, `200` on success, error codes), mirroring `docs/app-import-endpoint.md`.
- [x] 5.2 Include a copy/paste `AregressRebuildController` Spring Boot 2.7.x stub with a `@PathVariable String target` and a `TODO` for the actual metamodel-rebuild call.
- [x] 5.3 Note that the existing `/api/automation/**` Basic-Auth security chain already covers the new path (no new security config needed).

## 6. Verify

- [x] 6.1 Build with `mvn package` and confirm it compiles.
- [x] 6.2 Sanity-check prefix detection and bookmark path-encoding (e.g. `party.Organisation:123`) against the new spec scenarios.
- [x] 6.3 Confirm `aregress.rebuild.enabled: false` fully disables the behaviour (no rebuild call for a navigate-to-one-of command).
