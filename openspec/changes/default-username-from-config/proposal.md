## Why

`--username` must be passed on every invocation even though it is effectively always the same value (`estatio-admin`), adding avoidable friction. It is not a secret, so it can default from configuration like the other non-secret settings.

## What Changes

- **`--username` defaults from configuration**: `--username` becomes optional, falling back to a new configured value (`aregress.username`, default `estatio-admin`) when not supplied on the CLI — matching the existing precedence pattern used for the URL options. The passwords remain CLI/prompt-only.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `cli-entrypoint`: `--username` becomes optional (falls back to configuration).
- `app-configuration`: add `aregress.username` (default `estatio-admin`) to the externalized, CLI-overridable settings.

## Impact

- `ReplayCommand.java` — make `--username` non-required and resolve it via config fallback.
- `AregressProperties.java` — add `username` property (default `estatio-admin`).
- `application.yml` — add `aregress.username: estatio-admin`.
- `README.adoc` — document that `--username` is now optional and defaults to `estatio-admin`.
