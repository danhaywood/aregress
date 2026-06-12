## 1. Maven Project Setup

- [x] 1.1 Create `pom.xml` with groupId `com.danhaywood`, artifactId `aregress`, packaging `jar`
- [x] 1.2 Add Playwright Java dependency (`com.microsoft.playwright:playwright`)
- [x] 1.3 Add Picocli dependency (`info.picocli:picocli`)
- [x] 1.4 Configure `maven-shade-plugin` to produce a fat JAR with manifest `Main-Class`
- [x] 1.5 Create `src/main/java/com/danhaywood/aregress/` package structure

## 2. CLI Entry Point

- [x] 2.1 Create `Main.java` as a Picocli `@Command` class with `--timestamp` (required), `--app-a`, `--app-b`, `--cfct`, and `--headless` options with documented defaults
- [x] 2.2 Implement browser launch: headed by default, headless when `--headless` is set
- [x] 2.3 Implement URL construction: `{app-base}/wicket/entity/isis.ext.commandLog.CommandReplayManager:{timestamp}`
- [x] 2.4 Wire up exit codes: 0 on success, 1 on mismatch, non-zero on argument error

## 3. Page Objects

- [x] 3.1 Create `CausewayReplayPage.java` — wraps a Playwright `Page`, exposes `navigateTo(url)`, `isReplayNextEnabled()`, `clickReplayNext()`
- [x] 3.2 Inspect the running Causeway app to discover the Playwright selector for the "replay next" button — DONE: button is `a:has-text("Replay Or Retry Next")`; it is an AJAX `<a>` with no disabled state. See design.md "Findings".
- [x] 3.2a Reworked `CausewayReplayPage`: `navigateTo()` now does Spring-Security form login; `hasPendingCommands()` + `oldestReplayState()` (reads the row-0 `.fragment-compact-badge`) replace `isReplayNextEnabled()`; `oldestCommandMember()` for logging; replay selector fixed to "Replay Or Retry Next".
- [x] 3.3 Implement `waitForCompletion()` in `CausewayReplayPage` — `waitForLoadState("networkidle")` confirmed adequate for Causeway.
- [x] 3.4 Create `CfctPage.java` — wraps a Playwright `Page`, exposes `navigateTo(url)`, `clickRefresh()`, `hasDifferenceTabs()`
- [x] 3.5 Inspect the running cfct app to discover selectors — DONE: cfct exposes stable `data-testid`s. See design.md "Findings".
- [x] 3.5a Reworked `CfctPage` to the real flow: `login()` (`login-password`/`login-submit`, waits for Refresh enabled); `refresh()` (`command-filter-refresh`); `selectAllTables()` (`table-select-all-checkbox`, full-DB compare — footprint doesn't auto-load under automation); `compare()` (`compare-button` + polls `comparison-progress-summary` for "complete"); `downloadComparison()` → parses the exported JSON into `ComparisonResult`. Difference detection is via JSON `hasDifferences` (not tab-scraping). Added `ComparisonResult` + Gson dependency.
- [ ] 3.5b (future optimisation) Drive the per-command footprint compare instead of full-DB, once the footprint-load event is known (see design.md OPEN BLOCKER).

## 4. Orchestration Loop

- [x] 4.1 Implement the main replay loop in `Main.java` (or a dedicated `ReplayOrchestrator` class): while `appA.isReplayNextEnabled()`, replay both apps, refresh cfct, check for tabs
- [x] 4.1a Reworked the loop: `cfct.login()` once before the loop; loop while `appA.hasPendingCommands()`; per step replay both apps, then stop on (a) `oldestReplayState()=="Failed"` on either app, or (b) `ComparisonResult.hasDifferences`. Added required `--username`/`--password`/`--cfct-password` options (apps need auth).
- [x] 4.2 Implement per-step stdout logging: `[step N] replayed... OK` / `[step N] replayed... FAIL`
- [x] 4.2a Distinguished the two failure modes in logging: "replay FAILED on app-a/-b" vs. "FAIL — database divergence: <tables>" (the latter from `ComparisonResult.describeDifferences()`).
- [x] 4.3 Implement final summary logging: `All N steps passed.`
- [x] 4.4 Ensure loop exits immediately on mismatch with code 1

## 5. Build and Smoke Test

- [x] 5.1 Run `mvn package` and verify fat JAR is produced
- [x] 5.2 Run `playwright install` (or document as a prerequisite) to download browser binaries
- [~] 5.3 Happy-path smoke test (clean lockstep run → "All N steps passed.", exit 0): NOT YET — needs both apps re-primed to a clean *in-sync* state (they are currently deliberately desynced). Pipeline plumbing is otherwise validated by 5.4.
- [x] 5.4 Failure-case smoke test: ran the JAR headless against the live (diverged) apps — output `[step 1] lock replayed... FAIL — database divergence: isisExtSecman.ApplicationUser (1 differing row(s))`. Validates end-to-end: dual Causeway login, cfct DB login, lockstep replay, full-DB compare, Download→JSON parse, divergence detection + diagnostics, exit 1. (The "Failed replay" stop-condition path is coded but not yet exercised against a real replay failure.)

## 6. Documentation

- [x] 6.1 Add a `README.md` with prerequisites (Java, Maven, `playwright install`), build instructions (`mvn package`), and example invocation
- [x] 6.2 Add `aregress` shell script launcher — checks Java 17+ on `PATH`, errors with helpful message if not met, forwards all args to the fat JAR
