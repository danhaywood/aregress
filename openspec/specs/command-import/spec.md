# command-import Specification

## Purpose

Prime the apps from a recording file instead of requiring out-of-band import: when `--file` is given, aregress posts the recording to each app's import endpoint and obtains the baseline timestamp used to drive the run.

## Requirements

### Requirement: Import a recording into both apps when a file is given
When `--file <path>` is provided, the tool SHALL POST the recording to each app's import endpoint — `POST {app}/api/automation/import` with HTTP Basic Auth using the Causeway `--username`/`--password` — and SHALL use the baseline timestamp returned by each app to build that app's `CommandReplayManager` URL. The import SHALL happen once per app, before the replay loop.

#### Scenario: File imported into both apps
- **WHEN** the tool is run with `--file recording.xml`
- **THEN** it POSTs the file to `app-a` and to `app-b`, each app returns `{ "timestamp": "<baseline>" }`, and the tool navigates each app to `{app-base}/wicket/entity/isis.ext.commandLog.CommandReplayManager:{that app's returned timestamp}`

#### Scenario: Timestamp mode performs no import
- **WHEN** the tool is run with `--timestamp` (not `--file`)
- **THEN** no import request is made and both apps use the supplied timestamp

### Requirement: Import failure aborts before replay
If an import request returns a non-`200` response (or fails to connect), the tool SHALL abort before the replay loop with a clear error and exit non-zero, distinct from a replay failure or database divergence.

#### Scenario: Import request fails
- **WHEN** an `import` request to app-a or app-b returns a non-`200` response
- **THEN** the tool prints a clear import-error message naming the app and exits non-zero (and does not begin replaying)
