## 1. Preconditions

- [x] 1.1 Confirm the cfct automation API is reachable: `curl -i -u <user>:<pass> {cfct}/api/automation/comparison.json` returns `200` with the comparison JSON (the endpoint refreshes server-side first).
- [x] 1.2 Confirm the JSON parses into `ComparisonResult`. (The endpoint is live at `robot:secret`; fields will become `hasDifferences` / `differingTables` / `comparedTables` per the agreed contract — see 2.4.)

## 2. HTTP Client

- [x] 2.1 Add `ComparisonResult.parse(String json)` overload; have the existing file-based `parse(Path)` delegate to it.
- [x] 2.2 Create `CfctClient` (JDK `java.net.http.HttpClient`) constructed with the cfct base URL + Basic-Auth username/password; set `Authorization: Basic …` on every request; use a generous timeout (the GET refreshes server-side).
- [x] 2.3 Implement `CfctClient.latestComparison()` → `GET /api/automation/comparison.json` (refreshes then returns); require `200`; parse body into `ComparisonResult`; treat non-`200` as an automation error.
- [x] 2.4 Adopt the agreed JSON contract: rename `tables` → `differingTables`, add `comparedTables` + `comparedTableCount()`. A no-op (no-footprint) command returns `200 hasDifferences:false` (empty `comparedTables`) → logged `... OK (no footprint)`; no aregress error-special-casing needed.

## 3. CLI

- [x] 3.1 Add `--cfct-username` option (Basic-Auth user); keep `--cfct-password` (now the Basic-Auth secret, still interactive-capable). Decide default for `--cfct-username` (e.g. `robot`).
- [x] 3.2 Update `--cfct` help text to "base URL of the cfct automation REST API".

## 4. Orchestration

- [x] 4.1 In `Main`, replace the cfct Playwright page with a `CfctClient`; stop opening a third browser page.
- [x] 4.2 Per step, after both replays succeed: `cfctClient.latestComparison()`; keep the existing `hasDifferences` → FAIL (with `describeDifferences()`) / OK logic and logging.
- [x] 4.3 Surface automation-API errors as a distinct non-zero exit (separate from a data divergence).
- [x] 4.4 Delete `CfctPage` and its Playwright selectors.

## 5. Docs & Verify

- [x] 5.1 Update `README.md`: cfct is now a REST endpoint (`--cfct` base URL, `--cfct-username`/`--cfct-password` Basic Auth); note cfct no longer requires a browser.
- [x] 5.2 `mvn package` and verify the fat JAR builds.
- [ ] 5.3 Happy-path smoke test against the live endpoint: full suite replays, `All N steps passed.`, exit 0 — and confirm each step is fast (footprint, not full-DB). BLOCKED: awaiting cfct shipping the agreed contract (no-op→`200`; `differingTables`/`comparedTables`). Until then a navigation command returns `500` and aregress exits 2.
- [ ] 5.4 Failure-case smoke test: induce a divergence (or stop the fixture per the cfct README) and verify exit 1 with the differing table reported. BLOCKED (same as 5.3).
