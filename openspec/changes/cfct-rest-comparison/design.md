## Context

`aregress` drives two Causeway apps (replay) and cfct (compare) via Playwright. The cfct half is the problem: its per-command footprint auto-selection doesn't fire under headless automation, so the tool falls back to a full-database compare (~337 tables) every step — correct but slow, and it depends on scraping a Vaadin UI.

cfct now provides an **automation REST API** (per its README), secured with HTTP Basic Auth (realm `CFCT Automation`):

- `POST /api/automation/refresh` → recomputes the footprint comparison for the **newest successful command**; `200` with `{ "status": "completed", "completedAt": ..., "tableCount": N }` (N = eligible business tables touched by that command).
- `GET /api/automation/comparison.json` → the latest comparison in the **same deterministic JSON format** as the UI download (`hasDifferences`, `tables[].summary`, `differingRows`); `404` with `{ "status": "not_found" }` if no refresh has run.

This lets `aregress` obtain a fast, correctly-scoped result over HTTP and drop cfct UI automation entirely.

## Goals / Non-Goals

**Goals:**
- Replace the cfct Playwright flow with HTTP calls: per step, `POST refresh` then `GET comparison.json`, and decide on `hasDifferences`.
- Use the per-command footprint compare (the endpoint already scopes to the newest command) — fast.
- Reuse the existing `ComparisonResult` model and failure diagnostics unchanged.
- Keep Causeway replay exactly as-is (still Playwright).

**Non-Goals:**
- Implementing the cfct endpoint (lives in the cfct project).
- Changing the replay loop's structure, stop conditions, or logging (other than where the comparison is obtained).
- Retaining the full-DB UI compare as a fallback (removed; it remains in git history / the archived change).

## Decisions

### Use the JDK `HttpClient` (no new HTTP dependency)
`java.net.http.HttpClient` (Java 11+, we target 17) covers Basic-Auth requests, timeouts, and response handling. Avoids adding OkHttp/Apache HttpClient. Gson (already a dependency) parses the body.

- Alternative: OkHttp — richer, but an unnecessary dependency for two calls.

### `CfctClient` replaces `CfctPage`
A plain HTTP client constructed with the base URL + Basic-Auth credentials. Methods:
- `refresh()` → `POST /api/automation/refresh`; expects `200` and `status == "completed"`; may surface `tableCount` for logging.
- `latestComparison()` → `GET /api/automation/comparison.json`; expects `200`, parses the body into `ComparisonResult`.

`Main` no longer opens a third Playwright page for cfct; it constructs a `CfctClient`. `ComparisonResult.parse` gains a `parse(String json)` overload (the file-based one can delegate).

### Per-step flow: refresh, then get
After both apps replay (and neither is `Failed`):
1. `cfctClient.refresh()` — synchronous; the `200/status:completed` response means the footprint compare for the newest successful command is done.
2. `cfctClient.latestComparison()` — fetch + parse.
3. `result.hasDifferences` → FAIL (with `describeDifferences()`), else OK.

The "newest successful command" is exactly the command just replayed in lockstep, so no interaction-id mapping is needed.

### Basic-Auth credentials via CLI
Add `--cfct-username` (e.g. `robot`) and reuse `--cfct-password` as the Basic-Auth secret. The `Authorization: Basic base64(user:pass)` header is set on every automation request. The previous cfct **UI-login** handling is removed (the endpoint owns the DB connection server-side).

### Generous HTTP timeouts; fail loudly on unexpected responses
`refresh` may take a little while server-side; set request timeouts generously (e.g. 120s). Any non-`200` from `refresh`, or a non-`200` (incl. `404 not_found`) from `comparison.json` *after* a successful refresh, aborts the run with a clear error (distinct from a data divergence).

## Risks / Trade-offs

- **Endpoint contract drift** (cfct changes the JSON or paths) → `ComparisonResult` parse fails or fields go missing. Mitigation: the README documents a curl contract check; add a startup smoke `GET`/`POST` and fail fast with a clear message.
- **Replay not yet committed when `refresh` runs** → footprint computed against stale data. Mitigation: the existing post-replay `waitForCompletion()` on both Causeway pages already gates this; `refresh` is called only after both replays settle.
- **`refresh` is actually asynchronous** (returns before completion) → we'd read a stale/empty comparison. Mitigation: rely on the README's `status: "completed"` contract; if it proves async, poll `comparison.json`'s `completedAt` until it advances. (Open question.)
- **Basic-Auth secret on the command line** (process listing) → same exposure as today's passwords; allow interactive prompt / env-var sourcing as already done for `--password`.

## Migration Plan

1. Add `CfctClient` + `ComparisonResult.parse(String)`; add `--cfct-username`.
2. Rewire `Main`: drop the cfct Playwright page; call `CfctClient.refresh()` + `latestComparison()` per step.
3. Remove `CfctPage`.
4. Smoke-test against the live cfct endpoint (happy path exit 0; divergence exit 1) and compare timing vs. the old full-DB path.
No rollback concern — the prior path is preserved in git history / the archived change.

## Open Questions

- Is `POST /api/automation/refresh` fully synchronous (safe to `GET` immediately), or should we poll `comparison.json`'s `completedAt`?
- Default for `--cfct-username` — hardcode `robot` as default, or require it?
- Should `aregress` cross-check the `refresh` response (e.g. expected `tableCount`/command identity) against the command it just replayed, to detect endpoint/loop drift? The README's `refresh` response doesn't include the command identity — would need the endpoint to return it.
