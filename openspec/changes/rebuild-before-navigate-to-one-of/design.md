## Context

aregress replays recorded Causeway commands on app-a and app-b in lockstep (see `replay-orchestration`), comparing databases via cfct after each command. The replay loop lives in `ReplayCommand.call()`; each app's CommandReplayManager page is wrapped by `CausewayReplayPage`, and app/cfct HTTP calls go through small dedicated clients (`AppImportClient`, `CfctClient`) that all share the same shape: a base URL normalised once, a pre-computed Basic-Auth header, a `java.net.http.HttpClient`, and a typed `*Exception`.

"Navigate to one of" actions resolve their choices against the target object's metamodel. When that metamodel is stale on an app, the action can pick the wrong choice (or none), surfacing as a replay failure or a false database divergence — noise unrelated to the JDO→JPA refactoring under test. The app already exposes the `/api/automation/**` automation surface (the import endpoint), so adding a sibling `rebuild` endpoint and calling it just-in-time before the affected command is a natural fit.

## Goals / Non-Goals

**Goals:**
- Before replaying a "navigate to one of" command, force a metamodel rebuild of its target object on both apps, so the action resolves against a current metamodel.
- Detect such commands purely from data already on the page (the command's logical member identifier prefix) plus its target bookmark.
- Fail loudly and distinctly when a rebuild can't be performed, rather than silently proceeding into a misleading divergence.
- Provide a copy/paste server-side stub (`AregressRebuildController`) mirroring the existing import-endpoint doc.
- Make the whole behaviour switchable off via configuration, so it can be retired without code changes if Causeway is fixed.

**Non-Goals:**
- Implementing the actual app-side rebuild logic (that is the consuming app's responsibility; we document a stub).
- Rebuilding on every command, or any general metamodel-management strategy beyond the navigate-to-one-of case.
- New CLI options or configuration keys.

## Decisions

### Detection key: logical member identifier prefix
The trigger is the command's **logical member identifier** carrying `__causeway_navigate_to_one_of_` or `__isis_navigate_to_one_of_` (the two prefixes cover the Causeway rename from the legacy `isis` namespace). `CausewayReplayPage` already reads the oldest command's member name (`oldestCommandMember`, cells.nth(3)) and its interaction id from the `ReplayableCommand:` href. We add a reader for the logical member identifier; if the member-name column is the friendly name rather than the logical id, the identifier is read from the command row's link/title attribute the same way the interaction id is (a selector to be confirmed against the live page during implementation, consistent with how existing selectors were discovered).

*Alternative considered:* match on the friendly member name. Rejected — fragile and locale/labelling dependent; the prefix is a stable contract on the logical identifier.

### Target obtained from the command's target bookmark
The rebuild endpoint is keyed by `{target}` = the command's target bookmark string (e.g. `party.Organisation:123`). We add `oldestCommandTargetBookmark()` to `CausewayReplayPage`, reading the target column/link on the command row. The bookmark is URL-path-encoded before being appended to the endpoint path so that `:` and similar characters travel as a single path segment.

### A new `AppRebuildClient`, modelled on `AppImportClient`
Add `AppRebuildClient(baseUrl, username, password)` exposing `rebuild(String targetBookmark)`. It mirrors `AppImportClient`: normalise base URL, build `Basic` header, `POST {base}/api/automation/rebuild/{encodedTarget}` with no body, treat any non-200 or transport error as a `RebuildException`. One client per app is constructed alongside the existing per-app objects in `ReplayCommand.call()`.

*Alternative considered:* fold rebuild into `AppImportClient`. Rejected — keeps each client single-purpose and matches the existing one-client-per-endpoint convention.

### POST, no body; both apps
Rebuild mutates server-side state (it rebuilds the metamodel), so POST is the correct verb — and it stays consistent with the import endpoint. No body is needed; the target is in the path. The rebuild is performed on **both** apps because the command is replayed on both and both metamodels must be fresh; each app's rebuild fires immediately before that app's `replayNext()`.

### Failure handling: exit code 2, like import
A rebuild failure is a precondition failure, not a verdict. It is surfaced with a clear message naming the app and target and exits with code 2 — the same status `ReplayCommand` already uses for import failures and automation errors — keeping the divergence (1) and paused (3) codes meaningful.

### Toggle via `aregress.rebuild.enabled` (config-only, default true)
The whole behaviour is gated by a new boolean setting `aregress.rebuild.enabled`, default `true`. It is added to `AregressProperties` as a nested `Rebuild` block (`getRebuild().isEnabled()`), mirroring the existing `Compare` tuning block, with a default in the bundled `application.yml`. It is config-only (no CLI flag) — matching the `compare` precedent — because it is an operational kill-switch rather than a per-run choice: it exists so the rebuild can be turned off wholesale if/when Causeway is fixed to make it unnecessary. When `false`, the loop skips the detection/rebuild entirely and behaves exactly as today.

*Alternative considered:* a `--rebuild` / `--no-rebuild` CLI flag. Rejected for now — a negatable boolean with a config fallback needs awkward three-state handling in picocli, and the toggle is a deployment-level switch, not something to vary run-to-run. Can be added later if needed.

### Loop integration
Inside the existing `while (appAPage.hasPendingCommands())` body, when `props.getRebuild().isEnabled()`: before `appAPage.replayNext()`, read the oldest command's logical member identifier and (if it matches a prefix) its target bookmark; on match, call `rebuildA.rebuild(target)`. Symmetrically before `appBPage.replayNext()`, call `rebuildB.rebuild(target)`. The identifier/target are read once at the top of the iteration (app-a's page is the authoritative driver, as it already is for `oldestCommandMember`/`oldestCommandInteractionId`). When the toggle is `false`, none of this runs.

## Risks / Trade-offs

- **Selector for the logical member identifier / target bookmark may differ from assumptions** → the design defers the exact selector to implementation-time inspection of the live CommandReplayManager page, exactly as the existing `REPLAYABLE_COMMAND_ID` / state-badge selectors were discovered; the prefix-match and bookmark-encoding logic are independent of the selector chosen.
- **Endpoint not yet implemented in the target app** → documented as a stub in `docs/app-rebuild-endpoint.md`; until the app ships it, navigate-to-one-of steps will abort with a clear `404`-based message (exit 2) rather than silently misbehaving — an acceptable, visible failure mode.
- **Rebuild latency adds per-affected-command overhead** → only fires for navigate-to-one-of commands, so the cost is bounded to the cases that need it.
- **Both prefixes maintained** → matching both `__causeway_` and `__isis_` keeps the tool working across app versions during the namespace transition; low cost, future prefixes would need adding.

## Migration Plan

Purely additive to aregress: new client + loop branch + page readers, no config or CLI change, so no rollback concerns for existing `--timestamp` / `--file` runs (non-navigate commands are unaffected). The app side must deploy the `AregressRebuildController` (per the new doc) for navigate-to-one-of steps to succeed; until then those steps abort visibly.

## Open Questions

- Exact DOM selector for the command row's logical member identifier and target bookmark — to be confirmed against the live page during implementation.
- Whether the app's rebuild should be synchronous (block until the metamodel is rebuilt) — assumed yes; the `200` response is taken to mean the rebuild is complete before replay proceeds.
