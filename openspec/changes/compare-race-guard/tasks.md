## 1. cfct response model

- [ ] 1.1 Add a nested `Command { interactionId, timestamp }` to `ComparisonResult` (Gson-bound, tolerant of absence — null when cfct omits it).

## 2. Replayed-command identity

- [ ] 2.1 Discover how the interaction id is exposed on the Causeway `CommandReplayManager` row (entity-link bookmark vs a column) by inspecting the running app.
- [ ] 2.2 Add `CausewayReplayPage.oldestCommandInteractionId()` returning the oldest pending command's interaction id. If it can't be read cleanly, fall back per design (timestamp match, or advanced-since-last-step) and note the choice.

## 3. Configuration

- [ ] 3.1 Add `aregress.compare.max-attempts` (default 5) and `aregress.compare.retry-delay` (Spring `Duration`, default `1s`) to `AregressProperties` (nested `compare`); ship defaults in `application.yml`.

## 4. Race-guarded compare

- [ ] 4.1 In `ReplayCommand`: capture `expectedId = appA.oldestCommandInteractionId()` before replaying.
- [ ] 4.2 Wrap `cfct.latestComparison()` in a bounded retry: repeat up to `max-attempts`, sleeping `retry-delay` between, until `command.interactionId` matches `expectedId` (case-insensitive); then run the existing `hasDifferences` check.
- [ ] 4.3 On exhaustion, print a clear error naming the expected command (and the last reported one) and return a distinct non-zero exit (`2`); never report the step passed.

## 5. Build & verify

- [ ] 5.1 `mvn package`; confirm the jar builds and `--help` is unchanged.
- [ ] 5.2 Normal run: each step confirms on the first attempt (no behavioural change vs today).
- [ ] 5.3 Race case: observe (or induce) cfct lagging behind a replay and confirm the tool retries and then proceeds once the ids match.
- [ ] 5.4 Exhaustion: confirm that when the id never matches within the attempts, the tool exits non-zero with the clear error (and config overrides for attempts/delay take effect).

## 6. Docs

- [ ] 6.1 README: document `aregress.compare.max-attempts` / `aregress.compare.retry-delay` (with defaults) in the configuration section.
