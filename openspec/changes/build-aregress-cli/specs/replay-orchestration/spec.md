## ADDED Requirements

### Requirement: Navigate all three pages on startup
The tool SHALL navigate app-a, app-b, and cfct to their respective URLs before beginning the replay loop.

#### Scenario: Startup navigation
- **WHEN** the tool starts
- **THEN** three browser pages are opened: app-a at `{app-a-base}/wicket/entity/isis.ext.commandLog.CommandReplayManager:{timestamp}`, app-b at the equivalent URL with `{app-b-base}`, and cfct at `{cfct-base}`

### Requirement: Loop while replay is available
The tool SHALL repeat the replay-compare cycle while the "replay next" button on app-a is enabled.

#### Scenario: Button enabled — continue loop
- **WHEN** the "replay next" button on app-a is enabled at the start of an iteration
- **THEN** the tool performs the replay-compare cycle for that iteration

#### Scenario: Button disabled — exit loop
- **WHEN** the "replay next" button on app-a is disabled
- **THEN** the tool exits the loop and reports overall success

### Requirement: Replay next on both apps per step
Each loop iteration SHALL click "replay next" on app-a, wait for completion, then click "replay next" on app-b and wait for completion.

#### Scenario: Replay in sequence
- **WHEN** an iteration begins
- **THEN** app-a's "replay next" is clicked and the tool waits for the action to complete before clicking app-b's "replay next"

### Requirement: Refresh cfct and check for differences
After both replays, the tool SHALL click "refresh" on cfct, wait for it to load, then inspect whether the difference panel contains any tabs.

#### Scenario: No differences — pass
- **WHEN** after refresh the cfct difference panel contains no tabs
- **THEN** the tool logs `[step N] replayed... OK` and continues to the next iteration

#### Scenario: Differences detected — fail
- **WHEN** after refresh the cfct difference panel contains one or more tabs
- **THEN** the tool logs `[step N] replayed... FAIL` and exits with code 1

### Requirement: Log progress to stdout
The tool SHALL log each step result and a final summary to stdout.

#### Scenario: Per-step logging
- **WHEN** a step completes (pass or fail)
- **THEN** the tool prints `[step N] replayed... OK` or `[step N] replayed... FAIL`

#### Scenario: Final summary on success
- **WHEN** all steps complete without failure
- **THEN** the tool prints `All N steps passed.`
