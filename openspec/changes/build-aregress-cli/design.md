## Context

`aregress` is a new greenfield CLI tool in this repo. The in-house Causeway app is being refactored from JDO to JPA; the two ORM variants run side-by-side on different ports. Both expose a `CommandReplayManager` UI (Apache Causeway / Wicket) that can replay a pre-loaded sequence of recorded commands one step at a time. A separate Vaadin web app (`cfct`) compares the underlying databases after each step and surfaces any differences as tabs in a fixed panel.

The automation goal is straightforward: drive three browser windows in a deterministic loop and exit on the first detected divergence.

## Goals / Non-Goals

**Goals:**
- Automate the replay loop: click "replay next" on both apps, refresh cfct, check for differences
- Exit non-zero on first mismatch; exit 0 when all commands pass
- Support headed and headless modes
- Be usable as a CI step (fat JAR, single command invocation, meaningful exit code)

**Non-Goals:**
- Importing/loading commands into the app instances (done out-of-band by the user)
- Enriching logs with per-command details from the Causeway UI (v2)
- Handling async background commands surfaced by cfct (v3)
- Supporting non-Causeway apps or non-Vaadin comparison tools

## Decisions

### Browser automation: Playwright Java

**Decision**: Use the official Playwright Java API (`com.microsoft.playwright:playwright`).

Alternatives: Selenium (more boilerplate, older API), REST calls to Causeway Restful Objects (simpler but loses the visual context needed for failure diagnosis).

Playwright is chosen because: it drives a real browser (valuable when diagnosing failures visually), has a clean synchronous Java API, and handles dynamic Wicket and Vaadin UIs well.

### CLI framework: Picocli

**Decision**: Use Picocli for argument parsing.

It produces clean `--flag` style CLI args, generates help text automatically, and is lightweight. No alternatives seriously considered — it's the standard choice for modern Java CLIs.

### Packaging: Fat JAR via maven-shade-plugin

**Decision**: Package as a fat JAR. No native binary (GraalVM) for now.

A fat JAR is sufficient for local use and CI. Playwright downloads its own browser binaries on first run via `playwright install`; these live outside the JAR. Native image compilation adds complexity with no immediate benefit.

### Page Object structure

**Decision**: Two page-object classes — `CausewayReplayPage` and `CfctPage`.

Each wraps a Playwright `Page` and exposes only the operations needed (`clickReplayNext`, `isReplayNextEnabled`, `clickRefresh`, `hasDifferenceTabs`). Selectors are encapsulated in these classes, making them easy to update when the UI changes. Selectors will be discovered by inspecting the running apps during implementation.

### Loop control: button disabled state

**Decision**: Loop while `CausewayReplayPage.isReplayNextEnabled()` returns true (checked on app-a).

Both apps have the same command list, so app-a's button state is the authoritative signal for "more work to do". If app-a's button is disabled, the sequence is exhausted.

### Step sequencing: app-a first, then app-b

**Decision**: Click app-a, wait for completion, then click app-b, wait for completion, then refresh cfct.

This keeps both instances in lockstep before comparison. "Wait for completion" means `waitForLoadState("networkidle")` or an equivalent Playwright wait after the action returns, to be refined during implementation.

### URL construction from timestamp

**Decision**: Accept `--timestamp` as the sole required argument. Construct the full path:
```
{app-base}/wicket/entity/isis.ext.commandLog.CommandReplayManager:{timestamp}
```
The same path is used for both app-a and app-b (different base URLs, same path).

### Shell launcher script

**Decision**: Provide an `aregress` shell script at the repo root that wraps `java -jar`.

The repo uses SDKMAN; the shell's default Java may be older than 17. The script checks the major version and exits with a helpful error if it's too old, so users aren't confronted with a cryptic JVM error. All arguments are forwarded verbatim to the JAR.

## Risks / Trade-offs

- **Selector fragility** → Causeway Wicket and Vaadin generate dynamic HTML; selectors may break on app upgrades. Mitigation: encapsulate selectors in page objects so updates are localised.
- **Timing sensitivity** → `networkidle` may not always be a reliable signal for action completion in Causeway. Mitigation: add explicit waits for specific UI elements if needed during implementation.
- **cfct tab detection** → Vaadin uses web components; standard CSS selectors may need `pierce` or shadow-DOM handling in Playwright. Mitigation: inspect the running app during implementation to find stable selectors.
- **Browser binary installation** → First run requires `playwright install` to download browser binaries. Mitigation: document in README; add to CI setup steps.

## Open Questions

- Exact Playwright selector for the "replay next" button in the Causeway Wicket UI — to be discovered by inspecting the running app.
- Exact Playwright selector for the cfct difference-tabs panel — to be discovered by inspecting the running cfct instance.
- Whether `waitForLoadState("networkidle")` is reliable enough for Causeway, or whether a more specific wait is needed.
