## Why

"Navigate to one of" actions resolve their choices against the target object's metamodel. If that metamodel is stale on an app instance, the action can replay against the wrong (or no) choice, producing spurious replay failures or false database divergences that have nothing to do with the JDO→JPA refactoring under test. Forcing a metamodel rebuild of the target object immediately before replaying such a command removes this source of flakiness.

## What Changes

- Add a new REST client (`AppRebuildClient`) that calls `POST {app-base}/api/automation/rebuild/{target}` (HTTP Basic Auth), where `{target}` is the command's target bookmark string.
- Teach the replay loop to read, for the oldest pending command, its **logical member identifier** and its **target bookmark**.
- When the command's member identifier carries the prefix `__causeway_navigate_to_one_of_` or `__isis_navigate_to_one_of_`, call the rebuild endpoint on **both** apps for that command's target bookmark *before* replaying it (app-a's endpoint before app-a's replay, app-b's before app-b's). Non-matching commands are unaffected.
- A rebuild failure on either app aborts the run with a clear, distinct error (treated like an import failure, not a divergence).
- Add a config property `aregress.rebuild.enabled` (default `true`) to toggle the whole behaviour off — so it can be disabled once/if Causeway is fixed to make the rebuild unnecessary. When disabled, navigate-to-one-of commands replay exactly as before, with no rebuild call.
- Add `docs/app-rebuild-endpoint.md` documenting the endpoint contract and a copy/paste `AregressRebuildController` Spring Boot stub (mirroring the existing `docs/app-import-endpoint.md` / `AregressImportController`).

## Capabilities

### New Capabilities
- `metamodel-rebuild`: The contract for the app-side `/api/automation/rebuild/{target}` endpoint and the aregress client that calls it to refresh a target object's metamodel before replay.

### Modified Capabilities
- `replay-orchestration`: The per-step loop gains a pre-replay rebuild step that fires only for "navigate to one of" commands (and only when the rebuild toggle is enabled), keyed off the command's logical member identifier and target bookmark.
- `app-configuration`: A new `aregress.rebuild.enabled` boolean setting (default `true`) is read from externalized configuration to toggle the rebuild behaviour.

## Impact

- New code: `AppRebuildClient.java` (HTTP client + `RebuildException`).
- Modified code: `ReplayCommand.java` (invoke rebuild before each replay when the prefix matches and the toggle is enabled), `CausewayReplayPage.java` (expose the oldest command's logical member identifier and target bookmark), `AregressProperties.java` + bundled `application.yml` (the new `aregress.rebuild.enabled` setting).
- New doc: `docs/app-rebuild-endpoint.md`.
- App-side (out of scope for this repo, documented as a stub): a new `AregressRebuildController` plus reuse of the existing `/api/automation/**` Basic-Auth security chain.
- No new dependencies and no new CLI option; one new config property (`aregress.rebuild.enabled`). Reuses the existing Causeway `--username` / `--password` and app base URLs.
