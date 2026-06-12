# replay-orchestration Specification

## Purpose

The core loop: replay recorded commands on app-a and app-b in lockstep, compare their databases via `cfct` after each command, and stop on the first detected divergence (or replay failure). This is what automates the previously-manual regression check of the JDO→JPA refactoring.

## Requirements

### Requirement: Open the app pages and cfct client on startup
The tool SHALL open browser pages for app-a and app-b at their CommandReplayManager URLs, and SHALL configure an HTTP client for the cfct automation REST API, before beginning the replay loop. (cfct is accessed over HTTP, not driven via a browser page.)

#### Scenario: Startup
- **WHEN** the tool starts
- **THEN** browser pages are opened for app-a (`{app-a-base}/wicket/entity/isis.ext.commandLog.CommandReplayManager:{timestamp}`) and app-b (the equivalent URL with `{app-b-base}`), and a cfct REST client is configured for `{cfct-base}`

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
After both replays succeed, the tool SHALL obtain the comparison from cfct's automation REST API and determine whether the databases have diverged. The tool SHALL `GET {cfct}/api/automation/comparison.json` (HTTP Basic Auth); the endpoint refreshes the footprint comparison for the newest successful command server-side before returning it. The tool SHALL parse the returned JSON, whose top-level `hasDifferences` flag is the divergence signal (with `differingTables` and `comparedTables` providing detail).

#### Scenario: No differences — pass
- **WHEN** the comparison request returns `200` and the JSON reports `hasDifferences: false`
- **THEN** the tool logs `[step N] <command> replayed... OK` and continues to the next iteration

#### Scenario: No-op command with no footprint
- **WHEN** the just-replayed command touched no business tables and the comparison returns `200` with `hasDifferences: false` and an empty `comparedTables`
- **THEN** the tool logs `[step N] <command> replayed... OK (no footprint)` and continues to the next iteration

#### Scenario: Differences detected — fail
- **WHEN** the comparison JSON reports `hasDifferences: true`
- **THEN** the tool logs `[step N] <command> replayed... FAIL — database divergence: <differing tables>` and exits with code 1

#### Scenario: Automation API error
- **WHEN** the comparison request returns a non-`200` response
- **THEN** the tool SHALL abort with a clear error distinct from a data divergence and exit non-zero

### Requirement: Log progress to stdout
The tool SHALL log each step result and a final summary to stdout.

#### Scenario: Per-step logging
- **WHEN** a step completes (pass or fail)
- **THEN** the tool prints the step number and command name, e.g. `[step N] <command> replayed... OK`, or the corresponding `FAIL` / `replay FAILED` message on failure

#### Scenario: Final summary on success
- **WHEN** all steps complete without failure
- **THEN** the tool prints `All N steps passed.`
