## 1. Preconditions

- [ ] 1.1 Confirm the cfct automation API is reachable at the target host: `curl -i -u <user>:<pass> -X POST {cfct}/api/automation/refresh` returns `200` with `status: completed`, and `GET {cfct}/api/automation/comparison.json` returns the comparison JSON.
- [ ] 1.2 Confirm the JSON matches the existing `ComparisonResult` fields (`hasDifferences`, `tables[].summary`, `differingRows`).

## 2. HTTP Client

- [ ] 2.1 Add `ComparisonResult.parse(String json)` overload; have the existing file-based `parse(Path)` delegate to it.
- [ ] 2.2 Create `CfctClient` (JDK `java.net.http.HttpClient`) constructed with the cfct base URL + Basic-Auth username/password; set `Authorization: Basic …` on every request; use generous timeouts.
- [ ] 2.3 Implement `CfctClient.refresh()` → `POST /api/automation/refresh`; require `200` and `status == "completed"`; throw a clear error otherwise (optionally log `tableCount`).
- [ ] 2.4 Implement `CfctClient.latestComparison()` → `GET /api/automation/comparison.json`; require `200`; parse body into `ComparisonResult`; treat non-`200` (incl. `404 not_found`) as an automation error.

## 3. CLI

- [ ] 3.1 Add `--cfct-username` option (Basic-Auth user); keep `--cfct-password` (now the Basic-Auth secret, still interactive-capable). Decide default for `--cfct-username` (e.g. `robot`).
- [ ] 3.2 Update `--cfct` help text to "base URL of the cfct automation REST API".

## 4. Orchestration

- [ ] 4.1 In `Main`, replace the cfct Playwright page with a `CfctClient`; stop opening a third browser page.
- [ ] 4.2 Per step, after both replays succeed: `cfctClient.refresh()` then `cfctClient.latestComparison()`; keep the existing `hasDifferences` → FAIL (with `describeDifferences()`) / OK logic and logging.
- [ ] 4.3 Surface automation-API errors as a distinct non-zero exit (separate from a data divergence).
- [ ] 4.4 Delete `CfctPage` and its Playwright selectors.

## 5. Docs & Verify

- [ ] 5.1 Update `README.md`: cfct is now a REST endpoint (`--cfct` base URL, `--cfct-username`/`--cfct-password` Basic Auth); note cfct no longer requires a browser.
- [ ] 5.2 `mvn package` and verify the fat JAR builds.
- [ ] 5.3 Happy-path smoke test against the live endpoint: full suite replays, `All N steps passed.`, exit 0 — and confirm each step is fast (footprint, not full-DB).
- [ ] 5.4 Failure-case smoke test: induce a divergence (or stop the fixture per the cfct README) and verify exit 1 with the differing table reported.
