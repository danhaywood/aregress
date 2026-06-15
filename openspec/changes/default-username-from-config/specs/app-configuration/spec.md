## MODIFIED Requirements

### Requirement: Load settings from externalized configuration
The tool SHALL read its non-secret settings (app-a / app-b / cfct base URLs, cfct username, the Causeway login username, timeouts) from Spring Boot externalized configuration — a bundled `application.yml`, environment variables, and system properties — applying Spring's standard precedence. Built-in defaults SHALL be provided so the tool runs with no external configuration.

#### Scenario: Defaults from bundled configuration
- **WHEN** the tool runs with no configuration overrides
- **THEN** it uses the bundled defaults (app-a `http://localhost:8080`, app-b `http://localhost:9090`, cfct `http://localhost:10010`, cfct username `robot`, login username `estatio-admin`)

#### Scenario: Environment / system-property override
- **WHEN** a setting is provided via an environment variable or system property (e.g. the cfct base URL)
- **THEN** that value overrides the bundled default for the run

### Requirement: CLI options override configuration
When a setting is supplied both as a CLI option and via configuration, the CLI option SHALL take precedence. This applies to the Causeway login username (`--username` over `aregress.username`) as well as the URL options. Secret values (the Causeway and cfct passwords) SHALL be accepted only via CLI option or interactive prompt and SHALL NOT be bound from `application.yml`.

#### Scenario: CLI option wins over configuration
- **WHEN** `--app-a http://host:9999` is passed and configuration also specifies an app-a base URL
- **THEN** the tool uses the CLI value

#### Scenario: Configuration used when option omitted
- **WHEN** an option (e.g. `--cfct` or `--username`) is not passed
- **THEN** the tool uses the configured value, or the bundled default if none is configured

#### Scenario: Secrets not read from application.yml
- **WHEN** the run needs a password
- **THEN** it is taken only from the CLI option (or interactive prompt), never from `application.yml`
