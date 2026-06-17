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

### Requirement: Rebuild the target metamodel before replaying a "navigate to one of" command
"Navigate to one of" actions resolve their choices against the target object's metamodel, so a stale metamodel can cause a spurious replay failure or false divergence. When the rebuild behaviour is enabled (see the `app-configuration` capability), for each step the tool SHALL read the oldest pending command's **logical member identifier** and its **target bookmark**. If the logical member identifier contains the prefix `__causeway_navigate_to_one_of_` or `__isis_navigate_to_one_of_`, the tool SHALL, before replaying that command on each app, force a metamodel rebuild of that command's target bookmark on that app via `POST {app-base}/api/automation/rebuild/{target}` (see the `metamodel-rebuild` capability). The rebuild SHALL be performed on **both** apps: app-a's rebuild before app-a's replay, and app-b's rebuild before app-b's replay. Commands whose identifier does not carry either prefix SHALL be replayed without any rebuild. When the rebuild behaviour is disabled, the tool SHALL replay every command without any rebuild call (the behaviour prior to this change).

#### Scenario: Rebuild disabled — no rebuild for any command
- **WHEN** the rebuild toggle is disabled and the oldest pending command is a "navigate to one of" command
- **THEN** the tool replays the command on both apps without reading its target bookmark or calling the rebuild endpoint

#### Scenario: Navigate-to-one-of command — rebuild both apps first
- **WHEN** the rebuild toggle is enabled and the oldest pending command's logical member identifier contains `__causeway_navigate_to_one_of_` (or `__isis_navigate_to_one_of_`) and its target bookmark is `T`
- **THEN** the tool calls `POST {app-a}/api/automation/rebuild/{T}` before clicking "replay next" on app-a, and `POST {app-b}/api/automation/rebuild/{T}` before clicking "replay next" on app-b

#### Scenario: Ordinary command — no rebuild
- **WHEN** the oldest pending command's logical member identifier carries neither the `__causeway_navigate_to_one_of_` nor the `__isis_navigate_to_one_of_` prefix
- **THEN** the tool replays the command on both apps without calling the rebuild endpoint

#### Scenario: Rebuild fails — abort before replay
- **WHEN** a required rebuild call returns a non-`200` response or fails to send
- **THEN** the tool aborts with a clear error naming the app and target and exits with code 2, without replaying that command

### Requirement: Replay next on both apps per step
Each loop iteration SHALL click "replay next" on app-a, wait for completion, then click "replay next" on app-b and wait for completion. When the oldest command is a "navigate to one of" command (its logical member identifier carries the `__causeway_navigate_to_one_of_` or `__isis_navigate_to_one_of_` prefix), the tool SHALL first rebuild that command's target metamodel on the relevant app — app-a's rebuild immediately before app-a's "replay next", and app-b's rebuild immediately before app-b's "replay next".

#### Scenario: Replay in sequence
- **WHEN** an iteration begins
- **THEN** app-a's "replay next" is clicked and the tool waits for the action to complete before clicking app-b's "replay next"

#### Scenario: Replay in sequence with rebuild
- **WHEN** an iteration begins for a "navigate to one of" command with target bookmark `T`
- **THEN** the tool rebuilds `T` on app-a, clicks app-a's "replay next" and waits for completion, then rebuilds `T` on app-b, clicks app-b's "replay next" and waits for completion

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

### Requirement: Confirm the comparison matches the replayed command
Before using a cfct comparison to decide pass/fail, the tool SHALL confirm the comparison is for the command just replayed on app-a and app-b. The tool SHALL compare the just-replayed command's interaction id against the `command.interactionId` in the cfct response, case-insensitively.

On mismatch — which indicates cfct was queried before both apps had finished committing the command — the tool SHALL re-query cfct (each query re-refreshes server-side) up to a configured number of attempts, waiting a configured delay between attempts. The attempt count and delay SHALL be configurable (`aregress.compare.max-attempts`, `aregress.compare.retry-delay`) via the standard configuration sources.

If confirmed, the existing divergence check (`hasDifferences`) applies as before. If the comparison is never confirmed within the configured attempts, the tool SHALL abort the step with a clear error and exit non-zero — an unconfirmed comparison SHALL NOT be treated as a pass.

#### Scenario: Confirmed on the first attempt
- **WHEN** the cfct response's `command.interactionId` equals (case-insensitively) the interaction id of the command just replayed
- **THEN** the tool proceeds to evaluate `hasDifferences` for that result

#### Scenario: cfct reports an older command, then catches up
- **WHEN** the first response's `command.interactionId` does not match the just-replayed command (cfct had not yet caught up) and a subsequent re-query within the configured attempts does match
- **THEN** the tool proceeds with the matching result and evaluates `hasDifferences`

#### Scenario: Comparison never confirmed — abort
- **WHEN** no response matches the just-replayed command's interaction id within the configured attempts
- **THEN** the tool prints a clear error naming the expected command and exits non-zero, and does not report the step as passed

### Requirement: Pause when background commands are pending
A command may spawn background commands that execute asynchronously (e.g. an `InvoiceRun`). The tool SHALL NOT judge such a step while that work is outstanding. After a step's comparison has been confirmed (matching the replayed command), and before the divergence check, the tool SHALL inspect `backgroundCommands.pending` in the cfct response. If it is non-zero, the tool SHALL stop the run with a clear "paused — resume later" message and a distinct non-zero exit status (separate from a regression or an automation error), instructing the operator to re-run aregress once the background commands have completed. This is not a failure verdict.

#### Scenario: Background commands pending — pause
- **WHEN** the confirmed comparison reports `backgroundCommands.pending` greater than zero
- **THEN** the tool prints a paused message naming the pending count, exits with the distinct "paused" status, and does not evaluate the divergence for that step

#### Scenario: No background commands — continue
- **WHEN** the confirmed comparison reports `backgroundCommands.pending` of zero (or omits it)
- **THEN** the tool proceeds to the divergence check as normal

#### Scenario: Resume by re-running
- **WHEN** the background commands have since completed and the operator re-runs aregress
- **THEN** the run continues from the still-pending commands (the previously-spawned background commands now among them) without any saved state

### Requirement: Log progress to stdout
The tool SHALL log each step result and a final summary to stdout.

#### Scenario: Per-step logging
- **WHEN** a step completes (pass or fail)
- **THEN** the tool prints the step number and command name, e.g. `[step N] <command> replayed... OK`, or the corresponding `FAIL` / `replay FAILED` message on failure

#### Scenario: Final summary on success
- **WHEN** all steps complete without failure
- **THEN** the tool prints `All N steps passed.`
