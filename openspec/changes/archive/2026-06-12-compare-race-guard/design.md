## Context

Per step the loop does: replay command X on app-a, replay X on app-b, then `cfct.latestComparison()` (a `GET` that refreshes server-side for the *newest successfully completed* command and returns the footprint comparison). The race: if the `GET` runs before both apps have committed X, cfct's newest-completed command is still X-1, so the comparison is for the wrong command. cfct now returns `command: { interactionId, timestamp }` so aregress can detect and wait this out.

## Goals / Non-Goals

**Goals:**
- Confirm cfct's `command.interactionId` equals the command aregress just replayed before trusting a comparison.
- Re-query cfct (bounded attempts + delay) when it reports an older command; fail closed if never confirmed.
- Make attempts/delay tunable via Spring Boot config.

**Non-Goals:**
- Changing pass/fail semantics once the correct command is confirmed.
- Changing the cfct contract (the `command` block already ships).

## Decisions

### Match on `interactionId` (case-insensitive)
The identity key is `command.interactionId` (the user added it for exactly this). UUID casing differs between surfaces (cfct returns upper-case, Causeway may differ), so compare case-insensitively. `command.timestamp` is carried too but not used for matching (cross-surface format differences make it a poor key).

### Source of the just-replayed command's interaction id: the Causeway replay row
"Replay Or Retry Next" executes the **oldest** pending command. Before replaying, aregress reads that row's interaction id from app-a's `CommandReplayManager` — the row is a `CommandLogEntry` whose object identifier *is* the interaction id, so it is recoverable from the row's entity-link href / bookmark. New `CausewayReplayPage.oldestCommandInteractionId()`. (app-a and app-b are in lockstep on the same recording, so the id is the same on both; reading app-a suffices.)

The exact selector is discovered at implementation (as with the other Causeway selectors). **Fallbacks** if the id isn't cleanly exposed on the row: (a) match on `command.timestamp` parsed from the row, or (b) require `command.interactionId` to have **advanced since the previous step** (in lockstep, a newly-completed command ⇒ the right one). Primary remains the interaction-id match.

### Bounded retry that re-queries cfct
`cfct.latestComparison()` re-refreshes on each call, so retrying *is* the wait-for-catch-up mechanism:

```
expectedId = appA.oldestCommandInteractionId()   // before replaying
appA.replayNext(); appB.replayNext()              // (existing Failed checks unchanged)
for attempt in 1..maxAttempts:
    result = cfct.latestComparison()
    if result.command != null && expectedId.equalsIgnoreCase(result.command.interactionId):
        break  // confirmed → proceed to hasDifferences check
    if attempt < maxAttempts: sleep(retryDelay)
else:
    print "[step N] <member> — could not confirm cfct comparison for <expectedId> after K attempts (last: <reported>)"
    return 2
```

Once confirmed, the existing `hasDifferences` → FAIL(1) / OK logic is unchanged (including the `(no footprint)` no-op case, whose command id also matches).

### Tunable via `aregress.compare.*`
Add to `AregressProperties`: `compare.maxAttempts` (default `5`) and `compare.retryDelay` (Spring `Duration`, default `1s`). Bound via the existing config precedence (CLI/env/`application.yml`). (Whether to expose these as CLI options too, or config-only, is an open question — leaning config-only since they're tuning knobs, not per-run choices.)

### Fail closed, distinct exit code
Exhausting attempts without a match is an automation/timing error, not a data verdict → exit `2` (as for other cfct-automation errors), never `0`. An unverifiable comparison is never a pass.

## Risks / Trade-offs

- **Interaction id not exposed on the Causeway row** → primary match impossible. Mitigation: the timestamp / advanced-since-last-step fallbacks above; decide at implementation.
- **Legitimately-slow commits** → spurious exhaustion. Mitigation: tunable attempts/delay; pick safe defaults (5 × 1s) and document.
- **cfct omits `command`** (old build) → every match fails → exhaustion. Mitigation: clear error message naming the cause; the contract is already shipped.
- **Per-step latency** added by retries only when there's an actual lag; the common case matches on the first attempt.

## Migration Plan

1. `ComparisonResult`: add `Command { interactionId, timestamp }` (nested), tolerant of absence.
2. `CausewayReplayPage.oldestCommandInteractionId()` — discover the row selector for the interaction id.
3. `AregressProperties`: add `compare.maxAttempts` + `compare.retryDelay`; defaults in `application.yml`.
4. Loop: capture `expectedId` before replay; wrap `latestComparison()` in the bounded, identity-matching retry; exit `2` on exhaustion.
5. Verify: a normal run still passes (matches first attempt); simulate/observe a lag and confirm it retries then proceeds; confirm exhaustion exits `2`.

## Open Questions

- How is the interaction id exposed on the Causeway `CommandReplayManager` row (entity-link bookmark vs a column)? Else which fallback.
- Expose `compare.maxAttempts`/`retryDelay` as CLI options too, or config-only?
- Default values — `5` attempts, `1s` delay proposed; confirm.
