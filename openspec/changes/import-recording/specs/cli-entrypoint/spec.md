## RENAMED Requirements

- FROM: `### Requirement: Accept required timestamp argument`
- TO: `### Requirement: Accept a replay target (timestamp or file)`

## MODIFIED Requirements

### Requirement: Accept a replay target (timestamp or file)
The CLI SHALL accept exactly one of `--timestamp <ts>` or `--file <path>`. `--timestamp` names an already-imported baseline; `--file` is a recording the tool imports into both apps (see the `command-import` capability), using each app's returned baseline timestamp. Providing neither, or both, SHALL be a usage error.

#### Scenario: Timestamp provided
- **WHEN** the user runs `aregress --timestamp 2026-04-23T08-32-03.309Z`
- **THEN** the tool constructs `{app-base}/wicket/entity/isis.ext.commandLog.CommandReplayManager:{timestamp}` for both app-a and app-b and navigates to them

#### Scenario: File provided
- **WHEN** the user runs `aregress --file recording.xml`
- **THEN** the tool imports the recording into app-a and app-b and uses each app's returned baseline timestamp to build its `CommandReplayManager` URL

#### Scenario: Neither provided
- **WHEN** neither `--timestamp` nor `--file` is given
- **THEN** the tool SHALL exit with a non-zero code and print a usage error

#### Scenario: Both provided
- **WHEN** both `--timestamp` and `--file` are given
- **THEN** the tool SHALL exit with a non-zero code and print a usage error
