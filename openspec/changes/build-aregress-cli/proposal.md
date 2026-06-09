## Why

Regression testing the JDO→JPA refactoring of our in-house Causeway app is currently a slow, error-prone manual process: a developer must click "replay next" on two running app instances, trigger a database comparison, check for differences, and repeat for every recorded command. `aregress` automates this loop so the same regression suite can be run reliably and eventually in CI.

## What Changes

- **New tool**: `aregress` — a Java CLI that drives two Causeway app instances and a `cfct` comparison tool via Playwright browser automation
- Accepts a `--timestamp` argument to identify the command replay baseline, plus optional overrides for app and cfct URLs
- Runs headed by default (for failure diagnosis) with a `--headless` flag for CI
- Exits non-zero on the first detected difference; exits 0 when all commands pass

## Capabilities

### New Capabilities

- `cli-entrypoint`: Picocli-based CLI entry point — argument parsing, browser lifecycle, exit codes
- `replay-orchestration`: The core loop — replay next on app-a and app-b in lockstep, refresh cfct, check for differences, log results

### Modified Capabilities

<!-- none — this is a greenfield tool -->

## Impact

- New Maven project (`pom.xml`) in the repo root
- Dependencies: Playwright Java, Picocli, maven-shade-plugin (fat JAR)
- Requires two running Causeway app instances and a running `cfct` instance at the configured URLs
