## MODIFIED Requirements

### Requirement: Accept required credentials
The CLI SHALL accept the credentials needed to authenticate against the apps: `--username` and `--password` for the Causeway form login (used for both app-a and app-b), and `--cfct-username` and `--cfct-password` for HTTP Basic Auth against cfct's automation REST API. The `--cfct` option SHALL be the base URL of that REST API. The password options MAY be supplied inline or prompted for interactively when given without a value.

#### Scenario: Credentials supplied inline
- **WHEN** the user passes `--username`, `--password <value>`, `--cfct-username <value>` and `--cfct-password <value>`
- **THEN** the tool logs in to app-a and app-b with the username/password and authenticates cfct automation requests with HTTP Basic Auth using the cfct username/password, without prompting

#### Scenario: Password prompted interactively
- **WHEN** the user passes `--password` (or `--cfct-password`) with no value
- **THEN** the tool prompts for the password on the console without echoing it

#### Scenario: Required credential omitted
- **WHEN** a required credential option is omitted entirely
- **THEN** the tool SHALL exit with a non-zero code and print a usage error
