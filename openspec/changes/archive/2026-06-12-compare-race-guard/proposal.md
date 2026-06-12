## Why

Each step, aregress replays a command on app-a and app-b and then asks cfct for the comparison. But cfct's `GET /api/automation/comparison.json` refreshes for the *newest successfully completed* command — and there's a race: if aregress queries before both apps have finished committing the command, cfct may still report the **previous** command, so aregress would compare the wrong footprint and either miss a real divergence or report a stale one. cfct now returns the identity of the command it compared, so aregress can detect this and wait.

cfct's response now includes:
```json
{ "command": { "interactionId": "31E86289-80DA-49CF-AC25-BEFE14319757", "timestamp": "2026-06-11T13:29:38.4370000" } }
```

## What Changes

- **Verify command identity**: aregress reads the interaction id of the command it just replayed and checks it equals `command.interactionId` in the cfct response (case-insensitively) before trusting the comparison.
- **Retry on mismatch**: if cfct reports a different (older) command — i.e. it was queried before the apps finished — aregress re-queries cfct (which re-refreshes server-side), up to a bounded number of attempts with a delay between them, until the identity matches.
- **Tunable via configuration**: the attempt count and delay are Spring Boot config properties (`aregress.compare.*`) with sensible defaults, overridable per the existing precedence (CLI/env/`application.yml`).
- **Fail closed**: if the identity never matches within the configured attempts, aregress aborts the step with a clear error and a distinct non-zero exit — an unverifiable comparison is never treated as a pass.
- `ComparisonResult` gains the `command` (`interactionId`, `timestamp`) field.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities

- `replay-orchestration`: the per-step comparison must confirm cfct's reported command matches the just-replayed command, retrying on mismatch and aborting if it can't be confirmed.

## Impact

- **Code**: `CausewayReplayPage` exposes the oldest command's interaction id (read from its row); `ComparisonResult` adds the `command` object; the loop wraps the cfct query in a bounded retry that matches `command.interactionId`; new `aregress.compare.*` properties on `AregressProperties`.
- **External dependency**: relies on cfct including the `command` block in its JSON (already shipped).
- **Behaviour**: more robust under timing; a confirmed comparison per step. No change to the pass/fail semantics once the right command is confirmed.
- **Key unknown** (resolved at implementation): how aregress obtains the replayed command's interaction id from the Causeway `CommandReplayManager` row (see design); a timestamp-based or "advanced since last step" fallback is noted.
