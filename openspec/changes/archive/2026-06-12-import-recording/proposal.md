## Why

Today a regression run requires the apps to be **primed out-of-band** — the command recording imported into both `CommandReplayManager`s — and a `--timestamp` to locate that batch. That manual priming is the biggest friction in actually running aregress (and the source of most of the "apps out of sync" trouble). If aregress can import the recording itself by posting a file to each app, a run becomes self-contained: point it at a recording file and go.

## What Changes

- **New `--file <path>` option**, an alternative to `--timestamp`. Exactly one of the two SHALL be provided (mutually exclusive).
- When `--file` is given, aregress **POSTs the recording to an import endpoint on app-a and on app-b**. Each app imports the commands into its `CommandReplayManager` and returns the **baseline timestamp** of the imported batch; aregress then proceeds exactly as the `--timestamp` flow, using each app's returned timestamp to build its replay URL.
- aregress **writes the HTTP client** for the import POST (this change).
- This change **provides a copy/paste Spring Boot 2.7.x endpoint stub + contract** as documentation. Wiring the endpoint to actually import into the `CommandReplayManager` is the app owner's task (out of scope here).

## Capabilities

### New Capabilities

- `command-import`: post a recording file to each app's import endpoint and obtain the replay baseline timestamp used to drive the run.

### Modified Capabilities

- `cli-entrypoint`: accept `--file` as an alternative to `--timestamp`; exactly one is required.

## Impact

- **aregress code**: a new import client (JDK `HttpClient`), `Main` wiring (import before the replay loop when `--file` is set), and the `--file` CLI option. `--timestamp` becomes optional-but-one-of.
- **External dependency**: an import endpoint in app-a/app-b. A documented stub + contract is delivered; the owner completes the import logic and the Spring Security config to permit the endpoint.
- **Auth**: the import endpoint is secured with HTTP Basic Auth, reusing the Causeway `--username`/`--password` (same as the UI login).
- **Out of scope**: the actual import-into-CommandReplayManager implementation; changes to the replay loop or the cfct comparison.
