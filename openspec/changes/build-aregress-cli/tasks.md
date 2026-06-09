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
- [ ] 3.2 Inspect the running Causeway app to discover the Playwright selector for the "replay next" button and its disabled state
- [x] 3.3 Implement `waitForCompletion()` in `CausewayReplayPage` — use `waitForLoadState("networkidle")` initially; refine if needed
- [x] 3.4 Create `CfctPage.java` — wraps a Playwright `Page`, exposes `navigateTo(url)`, `clickRefresh()`, `hasDifferenceTabs()`
- [ ] 3.5 Inspect the running cfct app to discover the Playwright selector for the "refresh" button and the difference-tabs panel (accounting for Vaadin shadow DOM if needed)

## 4. Orchestration Loop

- [x] 4.1 Implement the main replay loop in `Main.java` (or a dedicated `ReplayOrchestrator` class): while `appA.isReplayNextEnabled()`, replay both apps, refresh cfct, check for tabs
- [x] 4.2 Implement per-step stdout logging: `[step N] replayed... OK` / `[step N] replayed... FAIL`
- [x] 4.3 Implement final summary logging: `All N steps passed.`
- [x] 4.4 Ensure loop exits immediately on mismatch with code 1

## 5. Build and Smoke Test

- [x] 5.1 Run `mvn package` and verify fat JAR is produced
- [x] 5.2 Run `playwright install` (or document as a prerequisite) to download browser binaries
- [ ] 5.3 Manually smoke-test against the running apps: start a short replay sequence and verify the tool logs correctly and exits 0
- [ ] 5.4 Smoke-test failure case: introduce a deliberate divergence and verify the tool exits 1 at the correct step

## 6. Documentation

- [x] 6.1 Add a `README.md` with prerequisites (Java, Maven, `playwright install`), build instructions (`mvn package`), and example invocation
- [x] 6.2 Add `aregress` shell script launcher — checks Java 17+ on `PATH`, errors with helpful message if not met, forwards all args to the fat JAR
