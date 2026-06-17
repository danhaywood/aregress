## ADDED Requirements

### Requirement: Toggle the metamodel-rebuild behaviour via configuration
The tool SHALL read a boolean setting `aregress.rebuild.enabled` from externalized configuration (bundled `application.yml`, environment variables, system properties, per Spring's standard precedence), defaulting to `true`. When `true`, the pre-replay metamodel rebuild for "navigate to one of" commands (see `replay-orchestration`) is performed. When `false`, the tool SHALL skip that behaviour entirely and replay all commands as it did before the behaviour was introduced. This setting is config-only (no CLI option) — an operational kill-switch so the rebuild can be retired without code changes if Causeway is fixed to make it unnecessary.

#### Scenario: Enabled by default
- **WHEN** the tool runs with no configuration override for `aregress.rebuild.enabled`
- **THEN** the rebuild behaviour is enabled (the bundled default is `true`)

#### Scenario: Disabled via configuration
- **WHEN** `aregress.rebuild.enabled` is set to `false` (via `application.yml`, an environment variable, or a system property)
- **THEN** the tool performs no metamodel rebuild for any command during the run
