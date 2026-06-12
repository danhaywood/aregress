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

## Findings from live inspection (2026-06-11)

Inspected the three running apps with Playwright. Several original assumptions were wrong; the corrected facts below supersede the earlier "button disabled state" loop-control and "refresh + check tabs" cfct decisions.

### Causeway replay page (8080 / 9090)

- **Replay button**: the action is labelled **"Replay Or Retry Next"** (not "Replay Next"). It renders as a Wicket AJAX anchor: `<a class="btn ... isis-isis-ext-commandLog-CommandReplayManager btn-primary prototype" id="actionLink397">…Replay Or Retry Next</a>`, tooltip *"Executes the oldest command."* Stable selector: `a:has-text("Replay Or Retry Next")` (the `id` is dynamic — do not use it).
- **No disabled state**: it is an `<a>`, never HTML-`disabled`. `isReplayNextEnabled()` via `Locator.isEnabled()` would always be true → infinite loop. **Replaced by the pending-collection signal below.**
- **Termination signal**: the "pending/not-yet-OK" commands collection. Its paginator reads `Showing 1 to 15 of N` (locator: `text=/Showing .* of /`). The loop continues while that collection is non-empty; "all passed" when it empties.
- **Per-command replay state**: the "Replay State" column (5th `<td>`) holds `<div class="badge ...fragment-compact-badge"><span>Pending|Failed|Ok</span></div>`. Read the oldest row's state after a replay to detect failure.
- **"Replay Or Retry Next"** executes the *oldest* command and **retries** a failed one — so a Failed command stays at the head and re-clicking just re-fails it (this is why a naive loop never advances). Hence the fail-stop requirement below.

### cfct (10010)

- **Stable `data-testid` selectors throughout** — use these, not Vaadin CSS. Key ids: `login-password`, `login-submit`, `command-filter-refresh`, `command-selection-grid`, `command-checkbox-<interactionId>`, `command-filter-replay-state-ok|pending|failed` (the K/P/F filters), `command-filter-member-id`, `command-filter-interaction-id`, `table-selection-grid`, `table-select-all-checkbox`, `selected-only-checkbox`, `compare-button`, `comparison-results-container`, `comparison-stage-empty` (holds "No comparison results yet."), `comparison-stage-error`, `comparison-progress-summary`.
- **DB-connection login required**: on load a `login-modal` dialog is shown; `command-filter-refresh` and `compare-button` are **disabled until connected**. Connect by filling `login-password` (=`pass`; username `estatio` is pre-filled) and clicking `login-submit`. Connects `estatio ↔ estatio_alt` (app-a's DB ↔ app-b's DB).
- **Difference rendering**: differences appear as **tabs** in `comparison-results-container` after a Compare (confirmed by the project owner). When there is no divergence the stage shows `comparison-stage-empty`.
- **Compare flow per step** (decision: *latest command only*): refresh → select the just-replayed command's `command-checkbox-<interactionId>` → select its table(s) (`table-select-all-checkbox`) → click `compare-button` → check `comparison-results-container` for tabs. Matching the just-replayed command to its `interactionId` is the open mechanic to validate on resume.
- **cfct command grid columns** (validated): `Replay state` (`OK` / `PENDING` / `FAILED`), `Member` (full logical name, e.g. `isis.ext.secman.ApplicationUserManager#newLocalUser`), `Timestamp` (matches the Causeway command timestamp — a viable join key), `Completed at`, `Interaction` (UUID = the `interactionId` used in the checkbox testid). After a command is replayed on at least one app it shows `OK` here.
- **Command selection mechanic** (validated): target the checkbox **directly by interaction id** — `getByTestId('command-checkbox-<lowercased-uuid>')`. The grid **virtualizes rows**, so do NOT iterate `command-checkbox-*` by index (off-screen rows aren't clickable); select by id (and `scrollIntoViewIfNeeded`) instead.
- **Difference rendering** (validated 2026-06-12 against a live divergence): after Compare, results render in `vaadin-tabs[data-testid="comparison-results-tabs"]` as **one `vaadin-tab` per *compared* table** — testid `comparison-result-tab-<schema>-<table>` (lowercased), label `schema.Table`. A tab is shown for **every** compared table, NOT only differing ones. Within a tab, the results grid has a **`Status` column**; divergent rows read `Status = DIFFERENT` (a differing table's tab is also styled red). Toolbar toggles: **"Diff tables only"** hides same tables (leaving only tabs that genuinely differ) and **"Diff columns only"** (available once "Diff tables only" is on) hides same columns. Also a format dropdown (json) + Download.
- **Difference detection (DECIDED — JSON download)**: after Compare completes, click **Download** (exports the current comparison as JSON) and parse it. Top-level **`hasDifferences`** (boolean) is the pass/fail signal. The JSON also carries `tables[]` with per-table `summary` (`differingRowCount`, `rowsOnlyInLeftCount`, `rowsOnlyInRightCount`, `hasDifferences`) and `differingRows[].differences[]` (`column`, `left`, `right`) — enabling rich failure logging (which table/column/row diverged) almost for free. **Validated end-to-end in headless automation 2026-06-12**: full-DB compare → Download captured (`download.saveAs`) → parsed `hasDifferences:true` and identified `isisExtSecman.ApplicationUser`. This supersedes DOM tab-scraping.
  - Playwright Java: `Download dl = page.waitForDownload(() -> downloadTrigger.click()); dl.saveAs(tmp);` then JSON-parse. Download trigger is reachable by text "Download" (also has a `data-testid`; two download-related testids exist — confirm which on implementation).
  - `comparison-progress-summary` reads "Comparison complete." when done — poll it for completion before downloading.
  - DOM-scraping alternative (fallback, not chosen): enable **"Diff tables only"** then count remaining `vaadin-tab`s under `comparison-results-tabs` (>0 ⇒ divergence). "Diff columns only" further narrows to differing columns.
- **Full-DB vs footprint**: selecting **`table-select-all-checkbox`** compares the whole schema (~337 tables — slow but works in automation and still surfaces the divergent table's tab). The intended efficient path is the per-command **footprint** (just the impacted tables).
- **OPEN BLOCKER — footprint auto-selection not reproducible in automation**: in the owner's interactive session, clicking Refresh auto-selects the most-recent command AND auto-selects its footprint tables (e.g. `isisExtSecman.ApplicationUser`), populating the bottom grid and enabling Compare. In headless Playwright the command auto-checks but the **footprint tables never load/select** (`table-selection-grid` stays empty, `compare-button` disabled). Tried: waiting 18s; toggling the command checkbox off/on; clicking the command row's Member and Timestamp cells. None trigger the footprint load. `selected-only-checkbox` is checked by default (so the empty grid = nothing auto-selected, not merely hidden). Needs the cfct author to confirm what event drives footprint selection. Workaround available: full-DB `select-all` + "Diff tables only".

### Timing

- `waitForLoadState("networkidle")` works for the Causeway page navigation and the AJAX replay click (confirmed the replay executed and the state badge updated). cfct (Vaadin) needs a short explicit settle (`waitForTimeout`) plus waiting on the relevant `data-testid` to appear/enable rather than relying on `networkidle` alone.

## Revised decisions (supersede earlier ones)

- **Loop control**: drive by the Causeway pending-commands collection (non-empty ⇒ more work), NOT button-disabled state.
- **Stop conditions** (exit non-zero), checked per step:
  1. The replayed command's Causeway replay state becomes **Failed** on either app → stop (replay execution failure).
  2. cfct shows **difference tabs** after Compare → stop (database divergence).
  Otherwise continue; "All N steps passed." when the pending collection empties.
- **cfct startup**: perform the DB-connection login once before the loop.
- **cfct compare granularity**: latest command only (see above).

## Open Questions (remaining, to validate on resume)

- Map a just-replayed Causeway command to its cfct `interactionId`: the cfct grid's `Timestamp`/`Interaction` columns match the Causeway command row, so join on timestamp (or interaction id if surfaced in the Causeway row). To confirm once a usable recording exists.
- Confirm the difference-tab DOM shape (tab count / per-table tabs) against a *real* divergence — **blocked**: see environment note.
- Whether `table-select-all` is required, or selecting the command alone enables `compare-button`.

> **Environment note (updated 2026-06-12):** the original env replay failures were fixed (apps restarted; `userManager` then replayed `OK` on both). However the owner determined that **this recording's commands produce no database footprint divergence** — so the difference-tab path cannot be exercised with it, and *no* command in the suite is usable for that validation (reason not disclosed). **Action: owner is creating a new recording that does produce a divergence.** On resume, re-prime both apps from the new recording, then validate the cfct difference path (and run smoke tests 5.3/5.4).
>
> Transient state when paused: app-a had replayed through command 2 (`newLocalUser`, count 16), app-b through command 1 (count 17) — i.e. deliberately desynced for the (aborted) difference experiment. Moot once re-primed from the new recording.
