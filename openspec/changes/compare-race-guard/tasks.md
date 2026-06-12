## 1. cfct response model

- [x] 1.1 Add a nested `Command { interactionId, timestamp }` to `ComparisonResult` (Gson-bound, tolerant of absence — null when cfct omits it).

## 2. Replayed-command identity

- [x] 2.1 DONE: the interaction id is on each replay row's command entity link `href="./isis.ext.commandLog.ReplayableCommand:<interactionId>"` (lowercase; cfct returns upper-case, hence the case-insensitive match).
- [x] 2.2 Add `CausewayReplayPage.oldestCommandInteractionId()` returning the oldest pending command's interaction id. If it can't be read cleanly, fall back per design (timestamp match, or advanced-since-last-step) and note the choice.

## 3. Configuration

- [x] 3.1 Add `aregress.compare.max-attempts` (default 5) and `aregress.compare.retry-delay` (Spring `Duration`, default `1s`) to `AregressProperties` (nested `compare`); ship defaults in `application.yml`.

## 4. Race-guarded compare

- [x] 4.1 In `ReplayCommand`: capture `expectedId = appA.oldestCommandInteractionId()` before replaying.
- [x] 4.2 Wrap `cfct.latestComparison()` in a bounded retry: repeat up to `max-attempts`, sleeping `retry-delay` between, until `command.interactionId` matches `expectedId` (case-insensitive); then run the existing `hasDifferences` check.
- [x] 4.3 On exhaustion, print a clear error naming the expected command (and the last reported one) and return a distinct non-zero exit (`2`); never report the step passed.

## 5. Build & verify

- [x] 5.1 `mvn package`; confirm the jar builds and `--help` is unchanged.
- [ ] 5.2/5.3/5.4 BLOCKED (live): need app-a + app-b up and re-primed in sync. Build + --help + config binding verified; the happy-path confirmation, the retry-on-lag, and the exhaustion-aborts-exit-2 paths await a clean in-sync run.

## 6. Docs

- [x] 6.1 README: document `aregress.compare.max-attempts` / `aregress.compare.retry-delay` (with defaults) in the configuration section.
