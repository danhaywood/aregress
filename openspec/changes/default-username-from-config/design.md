## Context

`aregress` resolves its non-secret settings (app-a / app-b / cfct base URLs, cfct username) from `AregressProperties` (bound from `application.yml` under the `aregress` prefix), with CLI options overriding via `cliValue != null ? cliValue : props.getX()`. The Causeway login `--username` is the exception: it is `required = true` and has no configured fallback, so it must be passed every run even though it is effectively constant (`estatio-admin`). It is not a secret — it is used for the form login on both apps and as the import Basic-Auth user.

## Goals / Non-Goals

**Goals:**
- Make `--username` optional, defaulting to a configured value (`aregress.username`, default `estatio-admin`) via the existing CLI-overrides-config precedence.

**Non-Goals:**
- Defaulting the passwords from configuration (secrets remain CLI/prompt only).
- Any change to the import flow, replay loop, or exit codes.

## Decisions

### `--username` optional with config fallback
Drop `required = true` from `--username`; resolve it as `username != null ? username : props.getUsername()`, mirroring `--app-a` / `--cfct-username`. Add a `username` property to `AregressProperties` (default `estatio-admin`) and `aregress.username: estatio-admin` to `application.yml`. The resolved value continues to be used for both app logins and the import Basic-Auth.

- Rationale: username is not a secret, so binding it from configuration is consistent with the other non-secret settings.
- Safety net: if neither CLI nor config yields a username (e.g. config edited to blank), fail fast with a clear usage-style error rather than attempting a login with a null/blank username.

## Risks / Trade-offs

- [Username silently wrong because someone set an unexpected `aregress.username`] → It is visible via the standard config; an incorrect username fails the login fast and visibly. Acceptable.
