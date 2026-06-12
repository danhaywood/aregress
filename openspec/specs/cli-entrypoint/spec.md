# cli-entrypoint Specification

## Purpose

The Picocli-based command-line entry point for `aregress`: argument parsing, browser lifecycle, and exit codes. It turns a single invocation into a configured run against two Causeway app instances and a `cfct` comparison tool.

## Requirements

### Requirement: Accept required timestamp argument
The CLI SHALL accept a `--timestamp` argument (required) representing the ISO-format baseline timestamp used to construct the CommandReplayManager URL for both app instances.

#### Scenario: Valid timestamp provided
- **WHEN** the user runs `aregress --timestamp 2026-04-23T08-32-03.309Z`
- **THEN** the tool constructs URLs of the form `{app-base}/wicket/entity/isis.ext.commandLog.CommandReplayManager:{timestamp}` for both app-a and app-b and navigates to them

#### Scenario: Timestamp omitted
- **WHEN** the user runs `aregress` without `--timestamp`
- **THEN** the tool SHALL exit with a non-zero code and print a usage error

### Requirement: Accept required credentials
The CLI SHALL accept the credentials needed to authenticate against the apps: `--username` and `--password` for the Causeway form login (used for both app-a and app-b), and `--cfct-password` for the cfct database connection. The password options MAY be supplied inline or prompted for interactively when given without a value.

#### Scenario: Credentials supplied inline
- **WHEN** the user passes `--username`, `--password <value>` and `--cfct-password <value>`
- **THEN** the tool logs in to app-a and app-b with the username/password and connects cfct with the cfct password, without prompting

#### Scenario: Password prompted interactively
- **WHEN** the user passes `--password` (or `--cfct-password`) with no value
- **THEN** the tool prompts for the password on the console without echoing it

#### Scenario: Required credential omitted
- **WHEN** a required credential option is omitted entirely
- **THEN** the tool SHALL exit with a non-zero code and print a usage error

### Requirement: Accept optional URL overrides
The CLI SHALL accept optional arguments `--app-a`, `--app-b`, and `--cfct` to override default base URLs.

#### Scenario: Defaults used
- **WHEN** no URL overrides are provided
- **THEN** app-a defaults to `http://localhost:8080`, app-b to `http://localhost:9090`, cfct to `http://localhost:10010`

#### Scenario: URL override provided
- **WHEN** the user passes `--app-a http://myhost:8888`
- **THEN** that URL is used as the base for app-a instead of the default

### Requirement: Headed mode by default with headless flag
The CLI SHALL launch the browser in headed (visible) mode by default. A `--headless` flag SHALL switch to headless mode.

#### Scenario: Default headed mode
- **WHEN** `--headless` is not passed
- **THEN** a visible browser window opens

#### Scenario: Headless mode for CI
- **WHEN** `--headless` is passed
- **THEN** the browser runs without a visible window

### Requirement: Exit codes reflect outcome
The CLI SHALL exit 0 on full success and non-zero on any failure or argument error.

#### Scenario: All steps pass
- **WHEN** all commands replay without differences
- **THEN** the tool exits with code 0

#### Scenario: Mismatch detected
- **WHEN** cfct shows differences after any step
- **THEN** the tool exits with code 1
