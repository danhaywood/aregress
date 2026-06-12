# aregress

Automated regression testing CLI for the JDO→JPA Causeway refactoring.

Drives two Causeway app instances and a `cfct` database-comparison tool via Playwright,
replaying recorded commands step-by-step and stopping on the first detected divergence.

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

Produces `target/aregress-1.0-SNAPSHOT.jar` (fat JAR, ~190MB).

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

| Option            | Description |
|-------------------|-------------|
| `--timestamp`     | Baseline timestamp for the `CommandReplayManager` URL, e.g. `2026-04-23T08-32-03.309Z` |
| `--username`      | Username for the Causeway app login (used for both app-a and app-b) |
| `--password`      | Password for the Causeway app login. Pass `--password` alone to be prompted interactively |
| `--cfct-password` | Password for the cfct database connection. Pass `--cfct-password` alone to be prompted interactively |

### Optional

| Option      | Default                    | Description |
|-------------|----------------------------|-------------|
| `--app-a`   | `http://localhost:8080`    | Base URL for app-a |
| `--app-b`   | `http://localhost:9090`    | Base URL for app-b |
| `--cfct`    | `http://localhost:10010`   | Base URL for cfct |
| `--headless` | false (headed)            | Run browser headless (for CI) |

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

Exits `0` when all commands replay and compare cleanly, `1` on the first failure of either kind.

## Before running

1. Import the same command recording into both app instances (out-of-band)
2. Navigate both apps to the `CommandReplayManager` page (or let aregress do it via `--timestamp`)
3. Ensure `cfct` is running and connected to both databases

## CI setup

```yaml
- name: Install Playwright browsers
  run: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"

- name: Run regression
  run: ./aregress --timestamp $TIMESTAMP --headless
```
