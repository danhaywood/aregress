# aregress

Automated regression testing CLI for the JDO→JPA Causeway refactoring.

Drives two Causeway app instances via Playwright, replaying recorded commands step-by-step,
and after each command queries the `cfct` database-comparison tool's automation REST API —
stopping on the first detected divergence.

Built as a Spring Boot 4.x CLI (no web server): Picocli still provides the command-line, and
configuration can come from `application.yml` / environment variables / system properties as well
as CLI options.

## Prerequisites

- Java 17+
- Maven 3.x
- Playwright browser binaries (one-time setup):

```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

## Build

```bash
mvn package
```

Produces `target/aregress-1.0-SNAPSHOT.jar` (a Spring Boot executable jar, ~200MB).

## Usage

The `aregress` shell script at the repo root wraps the JAR and checks
that Java 17+ is on `PATH` before running:

```bash
./aregress --timestamp <timestamp> [options]
```

Or invoke the JAR directly:

```bash
java -jar target/aregress-1.0-SNAPSHOT.jar --timestamp <timestamp> [options]
```

If your shell's `java` is older than 17, set `JAVA_HOME` first:

```bash
# SDKMAN example
sdk use java 17.0.18-tem
./aregress --timestamp <timestamp>
```

### Required

Exactly one of `--timestamp` / `--file` is required:

| Option            | Description |
|-------------------|-------------|
| `--timestamp`     | Baseline timestamp of an already-imported batch, e.g. `2026-04-23T08-32-03.309Z` |
| `--file`          | A recording file to import into both apps (instead of `--timestamp`); each app's import endpoint returns the baseline timestamp to replay. See [docs/app-import-endpoint.md](docs/app-import-endpoint.md) |

Always required:

| Option            | Description |
|-------------------|-------------|
| `--username`      | Username for the Causeway app login (used for both app-a and app-b; also the Basic-Auth user for the `--file` import endpoint) |
| `--password`      | Password for the Causeway app login. Pass `--password` alone to be prompted interactively |
| `--cfct-password` | HTTP Basic-Auth secret for the cfct automation API. Pass `--cfct-password` alone to be prompted interactively |

### Optional

| Option            | Default                    | Description |
|-------------------|----------------------------|-------------|
| `--app-a`         | `http://localhost:8080`    | Base URL for app-a |
| `--app-b`         | `http://localhost:9090`    | Base URL for app-b |
| `--cfct`          | `http://localhost:10010`   | Base URL of the cfct automation REST API |
| `--cfct-username` | `robot`                    | Username for HTTP Basic Auth against the cfct automation API |
| `--headless`      | false (headed)             | Run browser headless (for CI) |

### Example

```bash
# Headed (default) — browser windows visible for failure diagnosis.
# Prompts for the two passwords (not echoed):
java -jar target/aregress-1.0-SNAPSHOT.jar \
  --timestamp 2026-04-23T08-32-03.309Z \
  --username estatio-admin --password --cfct-password

# Headless — for CI (pass secrets as args / from a secret store):
java -jar target/aregress-1.0-SNAPSHOT.jar \
  --timestamp 2026-04-23T08-32-03.309Z \
  --username "$AREGRESS_USER" --password "$AREGRESS_PASS" --cfct-password "$CFCT_PASS" \
  --headless

# Import a recording first (no out-of-band priming): aregress POSTs the file to
# each app's import endpoint and replays from the timestamp each returns.
java -jar target/aregress-1.0-SNAPSHOT.jar \
  --file recording.xml \
  --username estatio-admin --password --cfct-password
```

### Output

```
[step 1] newLocalUser replayed... OK
[step 2] updateUsername replayed... OK
[step 3] lock replayed... FAIL — database divergence: isisExtSecman.ApplicationUser (1 differing row(s))
```

Each step is reported with the command's member name. Two failure modes are distinguished:

- `replay FAILED on app-a/-b` — the command failed to execute on one of the Causeway instances.
- `FAIL — database divergence: <tables>` — the databases diverged after replay (the differing table(s) are named).

Exits `0` when all commands replay and compare cleanly, `1` on the first regression (replay failure or database divergence), `2` on a cfct automation-API error (unreachable endpoint, auth failure, etc.), and `3` when a command has spawned **background commands** that are still pending — a pause, not a failure: wait for them to complete, then re-run aregress and it resumes from the still-pending commands.

## Configuration

Non-secret settings have built-in defaults (a bundled `application.yml`) and can be overridden the
Spring Boot way — by environment variables, system properties, or an external `application.yml` —
with CLI options taking highest precedence:

**CLI option → system property → environment variable → bundled `application.yml` → built-in default**

| Config key (`aregress.*`)   | CLI option        | Default |
|-----------------------------|-------------------|---------|
| `aregress.app-a`            | `--app-a`         | `http://localhost:8080` |
| `aregress.app-b`            | `--app-b`         | `http://localhost:9090` |
| `aregress.cfct`             | `--cfct`          | `http://localhost:10010` |
| `aregress.cfct-username`    | `--cfct-username` | `robot` |
| `aregress.compare.max-attempts` | _(config only)_ | `5` |
| `aregress.compare.retry-delay`  | _(config only)_ | `1s` |

`aregress.compare.*` tune the per-step race guard: after replaying a command, aregress re-queries
cfct until the comparison it returns is for that command (matching `command.interactionId`), up to
`max-attempts` times with `retry-delay` between attempts. This absorbs the lag between a replay
finishing and cfct seeing it; if the command is never confirmed within the attempts, the run aborts
with exit `2` rather than trusting a stale comparison.

Examples (all set the cfct base URL): `--cfct https://host:10010` · `-Daregress.cfct=https://host:10010` · `AREGRESS_CFCT=https://host:10010`.

Secrets (`--password`, `--cfct-password`) are **only** accepted via the CLI option or interactive
prompt — they are never read from `application.yml`.

## Before running

1. Get the recording into both apps — either import it out-of-band and pass `--timestamp`, or pass `--file <recording>` and let aregress import it via each app's import endpoint (see [docs/app-import-endpoint.md](docs/app-import-endpoint.md))
2. Ensure `cfct` is running with its automation REST API enabled and connected to both databases

## CI setup

```yaml
- name: Install Playwright browsers
  run: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"

- name: Run regression
  run: ./aregress --timestamp $TIMESTAMP --headless
```
