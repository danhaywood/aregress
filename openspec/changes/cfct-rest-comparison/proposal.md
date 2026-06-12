## Why

`aregress` currently detects database divergence by driving cfct's Vaadin UI: refresh → select **all** tables → compare → download JSON. This is both **slow** (a full-database compare of ~337 tables every step, several minutes per regression run) and **brittle** (cfct's per-command *footprint* auto-selection does not trigger under headless automation, which is why we fell back to the whole-database compare). cfct can instead expose a REST endpoint that returns the per-command footprint comparison directly — letting `aregress` get a fast, scoped result over HTTP and stop driving cfct's UI altogether.

## What Changes

- **cfct exposes an automation REST API** (implemented in the cfct project — an external dependency of this change), secured with HTTP Basic Auth (realm `CFCT Automation`):
  - `POST /api/automation/refresh` → recomputes the footprint comparison for the **newest successful command** server-side; `200` with `{status: "completed", completedAt, tableCount}`.
  - `GET /api/automation/comparison.json` → the latest comparison in the **same JSON format** as the UI download (`hasDifferences`, `tables[].summary`, `differingRows`); `404 {status: "not_found"}` if no refresh has run yet.
- **aregress replaces cfct-UI driving with an HTTP client.** Per step, after both replays: `POST refresh`, then `GET comparison.json`, then check `hasDifferences`. The `ComparisonResult` model and the decision logic are reused unchanged.
- **Per-command footprint compare** (the endpoint scopes to the newest command's impacted tables) replaces the full-database compare — dramatically faster, and no interaction-id mapping is needed (the endpoint tracks the newest successful command).
- **cfct no longer needs Playwright at all.** The cfct browser page, the DB-connection UI login, and the table-selection/compare/download UI flow are removed. (Causeway replay is still driven via Playwright.)
- **CLI**: `--cfct` becomes the REST base URL; cfct credentials become **Basic-Auth** (`--cfct-username` / `--cfct-password`, e.g. `robot`/secret) instead of a UI-login password.

## Capabilities

### New Capabilities
<!-- none — this reworks how an existing capability obtains its comparison -->

### Modified Capabilities

- `replay-orchestration`: the "Compare in cfct and check for differences" requirement changes from a UI-driven full-database compare to an **HTTP request for the per-command footprint comparison**; the "Connect cfct to the databases on startup" requirement is **removed** (no cfct UI login).
- `cli-entrypoint`: the cfct-related options change — `--cfct` is the REST endpoint base URL, and cfct credentials become Basic-Auth (`--cfct-username` / `--cfct-password`); the endpoint owns the database connection.

## Impact

- **aregress code**: `CfctPage` (Playwright) is replaced by a small `CfctClient` (HTTP + existing `ComparisonResult` JSON parse). `Main` calls the client per step. Possible new HTTP-client dependency (or the JDK `HttpClient`).
- **External dependency**: requires the cfct automation API (`/api/automation/refresh`, `/api/automation/comparison.json`) to be available and return the agreed JSON contract. Per the cfct README this endpoint exists.
- **Behaviour**: faster, more reliable regression runs; removes the headless-footprint blocker entirely.
- **Out of scope**: implementing the cfct endpoint itself (separate cfct project); changing the Causeway replay driving.
