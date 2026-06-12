## ADDED Requirements

### Requirement: Navigate all three pages on startup
The tool SHALL navigate app-a, app-b, and cfct to their respective URLs before beginning the replay loop.

#### Scenario: Startup navigation
- **WHEN** the tool starts
- **THEN** three browser pages are opened: app-a at `{app-a-base}/wicket/entity/isis.ext.commandLog.CommandReplayManager:{timestamp}`, app-b at the equivalent URL with `{app-b-base}`, and cfct at `{cfct-base}`

### Requirement: Connect cfct to the databases on startup
Because the cfct refresh and compare controls are disabled until a database connection is established, the tool SHALL perform the cfct connection step once before the replay loop.

#### Scenario: cfct connection
- **WHEN** the tool starts and cfct shows its connection dialog
- **THEN** the tool submits the connection credentials (`login-password` / `login-submit`) and proceeds only once `command-filter-refresh` is enabled

### Requirement: Loop while pending commands remain
The tool SHALL repeat the replay-compare cycle while app-a's pending-commands collection is non-empty. (The "Replay Or Retry Next" control is a Wicket AJAX anchor with no disabled state, so the pending collection — not button state — is the authoritative signal.)

#### Scenario: Pending commands remain — continue loop
- **WHEN** app-a's pending-commands collection is non-empty at the start of an iteration
- **THEN** the tool performs the replay-compare cycle for that iteration

#### Scenario: No pending commands — exit loop
- **WHEN** app-a's pending-commands collection is empty
- **THEN** the tool exits the loop and reports overall success

### Requirement: Replay next on both apps per step
Each loop iteration SHALL click "replay next" on app-a, wait for completion, then click "replay next" on app-b and wait for completion.

#### Scenario: Replay in sequence
- **WHEN** an iteration begins
- **THEN** app-a's "replay next" is clicked and the tool waits for the action to complete before clicking app-b's "replay next"

### Requirement: Stop if a command fails to replay
After replaying on each app, the tool SHALL check the replayed command's Causeway replay state. If the command's state is `Failed` on either app, the tool SHALL stop and exit non-zero (this is a replay-execution failure, distinct from a database divergence).

#### Scenario: Replay execution failure
- **WHEN** the just-replayed command's Replay State reads `Failed` on app-a or app-b
- **THEN** the tool logs `[step N] <command> — replay FAILED on app-a` (or `app-b`) and exits with code 1

### Requirement: Compare in cfct and check for differences
After both replays succeed, the tool SHALL drive cfct to compare the databases and determine whether they have diverged: refresh, select all tables, click compare, wait for completion, then click Download and parse the exported JSON. The top-level `hasDifferences` flag of that JSON is the divergence signal.

> NOTE (delivered scope): the comparison is a **full-database** compare (cfct's per-command *footprint* auto-selection does not trigger under headless automation). This is correct but slow; the per-command footprint compare is deferred to a separate change that adds a cfct REST endpoint (see design.md). Detection is via the downloaded JSON rather than scraping the Vaadin results tabs, which is more robust.

#### Scenario: No differences — pass
- **WHEN** the downloaded comparison JSON reports `hasDifferences: false`
- **THEN** the tool logs `[step N] <command> replayed... OK` and continues to the next iteration

#### Scenario: Differences detected — fail
- **WHEN** the downloaded comparison JSON reports `hasDifferences: true`
- **THEN** the tool logs `[step N] <command> replayed... FAIL — database divergence: <differing tables>` and exits with code 1

### Requirement: Log progress to stdout
The tool SHALL log each step result and a final summary to stdout.

#### Scenario: Per-step logging
- **WHEN** a step completes (pass or fail)
- **THEN** the tool prints the step number and command name, e.g. `[step N] <command> replayed... OK`, or the corresponding `FAIL` / `replay FAILED` message on failure

#### Scenario: Final summary on success
- **WHEN** all steps complete without failure
- **THEN** the tool prints `All N steps passed.`
